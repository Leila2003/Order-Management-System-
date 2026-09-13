# DESIGN.md

## Part 1 - Indexing strategy & denormalisation decisions

### Indexes (see `schema.sql`)

| Index | Columns | Why |
|---|---|---|
| `idx_orders_customer_created` | `orders(customer_id, created_at DESC)` | Serves both "orders for customer X" filters in `GET /api/orders` and Query 1 (top customers by revenue in a date window) - `customer_id` narrows first, `created_at` is already sorted for the range scan. |
| `idx_orders_status_created` | `orders(status, created_at DESC)` | Serves `GET /api/orders?status=` and any "recent orders in status X" scan without touching unrelated statuses. |
| `idx_orders_created_at` | `orders(created_at DESC)` | Plain date-range scans (`from`/`to` with no status/customer) and Query 3's 12-month trend. |
| `idx_order_items_order_id`, `idx_order_items_product_id` | FK columns on `order_items` | `order_items` is always joined back to its order or product; without these, every join is a sequential scan once the table reaches millions of rows. |
| `idx_products_low_stock` | `products(stock_quantity) WHERE stock_quantity < 20` | Query 2 only ever cares about products under the threshold. A **partial** index stays a few KB even with a 10M-row catalogue, instead of indexing every in-stock product that will never match. |
| `idx_customers_tier` | `customers(tier)` | Query 3 groups by tier. |

General rule I followed: index the columns that appear in a `WHERE`/`JOIN`/`ORDER BY` together, as one composite index, rather than one index per column - Postgres can only efficiently use a single index per table per query in most plans, so composite indexes that match the actual access pattern beat several narrow ones.

**Note on UUID primary keys.** All four tables use `UUID PRIMARY KEY DEFAULT gen_random_uuid()` (random v4) instead of `BIGSERIAL`, so that ids are non-guessable and safe to expose directly in the API and to generate client-side without a round-trip. The trade-off at the "tens of millions of rows" scale this exam targets: a v4 UUID is 16 bytes vs. 8 for `bigint`, and because the values are random rather than monotonically increasing, every insert lands at a random point in the primary-key B-tree instead of always appending at the right edge - this causes more page splits, worse buffer-cache locality, and a larger, more fragmented index than an equivalent `BIGSERIAL` table. All the composite indexes above are unaffected (they don't lead with `id`), and keyset pagination (Part 3) still works because it only needs `id` to be a stable tie-breaker, not chronological. If insert throughput on these tables became the bottleneck in practice, the standard fix is a time-ordered id (UUIDv7, or `ULID`) instead of v4, which preserves the non-guessable/client-generatable properties while keeping inserts sequential.

### Denormalisation decisions

1. **`order_items.unit_price`** - required by the spec: it's a snapshot of `products.unit_price` at the moment of purchase. If it just referenced `products`, a later price change would silently rewrite the revenue of every past order. This is the standard "price at time of sale" pattern for any order/invoice system.
2. **`orders.total_amount`** - a denormalised sum of `SUM(order_items.quantity * order_items.unit_price)`, computed once when the order is created and never recomputed. This is a system with **high read volume** (the spec says so explicitly), and `total_amount` is read constantly (order list, customer summary) but written exactly once per order. Paying a join + aggregation on every read to save one column write at creation time is the wrong trade-off here. The risk (total_amount drifting from the items) is contained because order items are immutable after creation in this API - there's no endpoint that edits a placed order's items.

---

## Part 2 - ORM choice & concurrency trade-off

### ORM choice: Spring Data JPA

I used **Spring Data JPA / Hibernate** rather than plain Spring JDBC:

- The domain is a classic object graph (Order → OrderItems → Product/Customer) with real relationships and cascades (deleting an order should delete its items) - JPA's entity mapping and cascades express that directly instead of hand-written join/insert logic.
- `JpaSpecificationExecutor` gives `GET /api/orders`'s dynamic, optional filters (status/customerId/from/to) + pagination "for free" as one generated, parameterised, indexed SQL query - see `OrderSpecifications` - without hand-building a WHERE clause string.
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` maps directly onto `SELECT ... FOR UPDATE`, which is exactly the primitive the concurrency requirement needs.
- The trade-off: JPA can hide expensive queries behind an innocuous-looking method call (N+1 selects, over-fetching). I mitigated this deliberately: `@ManyToOne` associations are `FetchType.LAZY` everywhere, `spring.jpa.open-in-view=false` (no queries leak into the view layer), `hibernate.default_batch_fetch_size=25` batches any lazy loads that do happen, and the list endpoint returns a lightweight `OrderSummaryResponse` that never touches the `order_items` collection at all. For the two spots doing real aggregation (`Query 1-3` in Part 1, and the customer summary), I still write the query explicitly (JPQL/native SQL) rather than trusting an ORM-generated one - Spring JDBC would have been an equally valid choice for those two reasons alone, and for a system expected to scale to tens of millions of rows I'd reach for it more as the read paths grow, keeping JPA only for the transactional write paths (order creation, status update).

### Concurrency & stock deduction: a hybrid of optimistic and pessimistic locking

`POST /api/orders` (`OrderServiceImpl#deductStockAndBuildItem`) picks a strategy **per product, based on how much stock is currently on hand**, rather than committing to one strategy for every row:

| Stock level | Strategy | Mechanism |
|---|---|---|
| `stock_quantity > 20` (well-stocked) | **Optimistic** (lock-free) | A single atomic `UPDATE products SET stock_quantity = stock_quantity - :qty WHERE id = :id AND stock_quantity >= :qty` (`ProductRepository.deductStockIfAvailable`) |
| `stock_quantity <= 20` (low stock) | **Pessimistic** | `SELECT ... FOR UPDATE` (`ProductRepository.findByIdForUpdate`), as before |

The `20` threshold is not a new magic number - it's the same "low stock" boundary already used by `idx_products_low_stock` in schema.sql and by Query 2, so "low stock" means one thing everywhere in this codebase.

**Why the optimistic path doesn't need a `@Version` column.** A conditional `UPDATE` is race-safe on its own: Postgres takes a row-level write lock for the duration of *that statement* no matter which "philosophy" issued it, so two concurrent attempts on the same row always serialise at the database level - the second one physically cannot proceed until the first commits or rolls back, and then re-evaluates its own `WHERE stock_quantity >= :qty` against the post-commit value. If it doesn't match, `0` rows are affected and the code fails fast with `InsufficientStockException` - there's no version conflict to retry, because a plain "not enough stock right now" is a legitimate business outcome here, not a transient one. This is simpler than classic `@Version` + `OptimisticLockException` + retry-loop, and just as correct for a single-counter deduction like this.

**Why the initial (unlocked) stock-level read is safe to be stale.** Before deducting, the code reads the current `stock_quantity` via `ProductRepository.findStockView` - a plain, unlocked projection query - purely to decide *which branch to run*. If that read is stale by the time the actual deduction happens (someone else bought stock in between), it doesn't matter: the optimistic branch's `UPDATE ... WHERE` guard still catches insufficient stock correctly, and the pessimistic branch re-reads fresh, locked data anyway. The read is a heuristic for routing, not a correctness mechanism, so it never needs its own lock.

**Deadlock avoidance still holds across both branches.** When an order has multiple line items, products are still processed in a fixed order (ascending `id`) before any are touched - this matters regardless of which branch each item takes, because both the atomic `UPDATE` and `SELECT ... FOR UPDATE` hold their row lock until the transaction commits. As long as every code path acquires locks in the same relative order, two orders sharing several products still can't deadlock against each other.

**Scope note:** the cancellation path (`updateStatus` restocking items when an order is cancelled) intentionally stays pessimistic-only. Cancellations are comparatively rare (an operator action, not a customer-facing hot path), so there's no throughput reason to add the extra branching there, and restocking is a good place to stay conservative.

**Trade-off vs. picking a single strategy for everything:**

| | Pure pessimistic (`SELECT FOR UPDATE` everywhere) | Pure optimistic (`@Version` + retry) | Hybrid (chosen) |
|---|---|---|---|
| Throughput on well-stocked products | Pays lock overhead even though nobody is contending | No overhead | No overhead (same as pure optimistic) |
| Correctness under contention on a hot, low-stock SKU | Guaranteed, no retry needed | Guaranteed only if the caller retries; failures pile up exactly where contention is worst | Guaranteed, no retry needed (falls back to pessimistic exactly where it matters) |
| Code complexity | One code path | One code path, but needs a retry loop to be usable | Two code paths, but neither needs a retry loop |

The hybrid costs one extra unlocked read per line item (to pick a branch) and a second repository method, in exchange for avoiding any locking cost on the common case (orders touching well-stocked products) while keeping the strong, no-retry-needed guarantee exactly where contention actually concentrates - the last few units of a popular SKU.

---

## Part 3 - Scaling `GET /api/orders` to 50,000 req/min without touching the DB server

### 1. Diagnosis first

I would not guess - I'd look in this order, cheapest/most informative first:

1. **Application-side first, because it's free and instant**: HikariCP metrics (`HikariPoolMXBean` / Micrometer `hikaricp.connections.*`). If `active` is pinned at `maximum-pool-size` and `pending` (waiting threads) is climbing, requests are queueing for a DB connection before a query even runs - that alone can explain "slow at peak" with a perfectly fast query. Check `hikaricp.connections.usage` (time a connection is checked out) too - if it's high, connections are being held too long (e.g. `open-in-view` leaking a connection for the whole request, or a slow query holding it).
2. **Slow query log** (`log_min_duration_statement` on Postgres, or `pg_stat_statements` if enabled) - identifies which specific query is actually slow versus just frequent. At 50k req/min, even a 20ms query run 800×/sec adds up; the log tells me whether it's one pathological query or broad load.
3. **`EXPLAIN (ANALYZE, BUFFERS)`** on the `GET /api/orders` query with realistic filters/page depth - confirms whether it's using the intended index, doing a sequential scan, or (very likely at scale) doing a `Sort` for `ORDER BY` that spills to disk. I'd check this against the current filter combinations actually used in production, since a filter combo without a matching composite index degrades silently.
4. **Missing/unused indexes** - `pg_stat_user_indexes` shows index scans vs. sequential scans on `orders`; if `idx_scan` is near zero on an index I expect to be used, either the query doesn't match it (e.g. filtering on the second column of a composite index without the first) or it genuinely doesn't exist.
5. **Table/index bloat & vacuum** - `pg_stat_user_tables` (`n_dead_tup`, last autovacuum) - a heavily updated `orders` table (status changes) that isn't vacuumed enough bloats both the table and its indexes, silently making every scan slower over time even with the "right" index.

In short: pool saturation first (rules out "it's not even the query"), then the slow query log + `EXPLAIN ANALYZE` (finds *which* query and *why*), then index/vacuum stats (explains *why the index isn't helping*).

### 2. Caching strategy

What I'd cache, in priority order:

- **`GET /api/customers/{id}/summary`** - an aggregate that changes only when that customer's orders change; cheap to cache, high value since it's likely hit repeatedly for the same customers (support/ops staff looking up the same accounts).
- **`GET /api/orders/{id}`** - individual orders are immutable in every field except `status`, so they're very cache-friendly.
- **`GET /api/orders` list pages** - the hardest to cache well because the key space is huge (every filter/page combination is a different cache entry) and results change whenever any matching order's status changes. I'd cache this only for the *first page of the default/no-filter view* (what most dashboards load on open), not the long tail of filter combinations.

**Approach:** cache-aside with Redis (not the in-process JVM heap - the app should be able to scale to multiple instances behind a load balancer, and an in-process cache would be inconsistent across them and lost on every deploy).

- **TTL:** short, 30-60 seconds, for order detail and list pages - it's a safety net against stampedes, not the primary consistency mechanism (invalidation is). Customer summaries can take a slightly longer TTL (2-5 minutes) since they're read far more often than any single customer's orders change.
- **Invalidation on status update:** `PUT /api/orders/{id}/status` explicitly evicts `order:{id}` and `customer-summary:{customerId}` for that order's customer *in the same transaction/request* that performs the update (write-through invalidation), rather than waiting for the TTL. This guarantees ops staff never see a stale status right after they changed it, while the short TTL still protects against any cache entry that gets missed (e.g. a bulk status update path added later that forgets to invalidate). List pages are **not** individually invalidated (too many possible cache keys to track) - they rely purely on the short TTL, which is acceptable since a list view is inherently a "moment in time" snapshot to begin with.

### 3. Why OFFSET pagination degrades at 50M rows, and the alternative

`LIMIT 20 OFFSET 100000` still requires Postgres to *walk and discard* the first 100,000 matching rows in index order before it can return the next 20 - the cost of a page grows linearly with how deep into the result set it is. At 50M rows, page 5,000 can mean scanning millions of index entries just to throw them away, and this gets paid on every request, not just once. It also produces incorrect results under concurrent writes: if a row is inserted/deleted ahead of the current offset while a user is paging through, they see a duplicate or miss a row entirely.

**Alternative: keyset (cursor) pagination.** Instead of `OFFSET n`, the client passes the last row it saw, and the query asks for "the next N rows after that point" using an indexed column (or tuple) as the cursor:

```sql
SELECT * FROM orders
WHERE (created_at, id) < (:last_created_at, :last_id)   -- descending example
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

`(created_at, id)` (a tuple, to break ties when two orders share a timestamp) matches `idx_orders_created_at`/`idx_orders_customer_created` directly, so Postgres seeks straight to the cursor position via the index and reads exactly 20 rows - constant time regardless of how deep the page is. The trade-off is losing "jump to page 500" random access and needing to encode/decode an opaque cursor in the API (e.g. a base64 token of `created_at,id`) instead of a page number - acceptable for an operations UI that mostly scrolls forward/backward, not one that needs arbitrary page-number jumps.

### 4. Constrained optimisation: expensive JOIN, no new indexes allowed on `orders`

If I can't add an index to `orders` itself, my options move the work elsewhere:

1. **Index the *other* side of the join instead.** If the expensive join is `orders ⋈ order_items` or `orders ⋈ customers`, an index on `order_items.order_id` / `customers.id` (already the PK) may let Postgres flip the join order or switch to a nested-loop/index lookup instead of hashing/sorting the full `orders` table - the restriction was "no new indexes on `orders`," not on every table.
2. **Denormalise the join away.** Precompute and store the joined data redundantly - e.g. add `customer_name`/`customer_tier` columns directly onto a read-optimised copy of `orders`, or maintain a summary table (`order_summary`) refreshed on write, so the list endpoint reads one table instead of joining two. This is exactly the same reasoning as `orders.total_amount` in Part 1: pay the cost once on write, not on every read.
3. **Materialized view.** `CREATE MATERIALIZED VIEW order_listing_view AS SELECT ... FROM orders JOIN customers ...`, refreshed on a schedule (`REFRESH MATERIALIZED VIEW CONCURRENTLY`) or triggered after writes. `GET /api/orders` reads the view (which *can* have its own indexes, since it's not the `orders` table) instead of joining live. Trade-off: the view's data is only as fresh as the last refresh - acceptable for an operations list view, not for the single-order detail endpoint.
4. **Restructure into two simpler queries, joined in the app.** Fetch the page of `orders` rows alone (cheap, no join), collect the distinct `customer_id`s, then fetch those specific customers with a `WHERE id IN (...)` (a PK lookup, always fast) and join them in application code. Two fast, indexable queries can beat one query planner struggling to optimise a join it has no good index for.
5. **Read replica.** Point `GET /api/orders` at a read replica so the expensive join's I/O and CPU cost don't contend with the primary's write traffic (stock deduction, status updates) - doesn't make the query itself faster, but stops it from starving other requests. Combine with the caching strategy in (2) above so the replica absorbs far fewer of these queries to begin with.

In practice I'd reach for (2) or (3) first: they eliminate the expensive join outright rather than just relocating or hiding its cost, and they compose well with the caching and keyset-pagination changes above.
