

-- ----------------------------------------------------------------------------
-- Query 1: Top 10 customers by total revenue in the last 90 days
--
-- Uses idx_orders_customer_created (customer_id, created_at) - Postgres can
-- range-scan the date filter per customer and avoid a full table scan.
-- Cancelled orders are excluded: they never generated real revenue.
-- ----------------------------------------------------------------------------
SELECT
    c.id                                AS customer_id,
    c.name                              AS customer_name,
    c.tier                              AS customer_tier,
    SUM(oi.quantity * oi.unit_price)    AS total_revenue
FROM customers c
JOIN orders o       ON o.customer_id = c.id
JOIN order_items oi ON oi.order_id = o.id
WHERE o.created_at >= now() - INTERVAL '90 days'
  AND o.status <> 'CANCELLED'
GROUP BY c.id, c.name, c.tier
ORDER BY total_revenue DESC
LIMIT 10;


-- ----------------------------------------------------------------------------
-- Query 2: Products with stock below 20 units that have had at least one
-- order in the last 30 days
--
-- The stock_quantity < 20 filter hits idx_products_low_stock (a partial
-- index, so it stays tiny regardless of catalogue size). EXISTS is used
-- instead of a JOIN so each qualifying product is returned once and the
-- planner can stop at the first matching order_item.
-- ----------------------------------------------------------------------------
SELECT
    p.id,
    p.name,
    p.sku,
    p.stock_quantity
FROM products p
WHERE p.stock_quantity < 20
  AND EXISTS (
        SELECT 1
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        WHERE oi.product_id = p.id
          AND o.created_at >= now() - INTERVAL '30 days'
  )
ORDER BY p.stock_quantity ASC;


-- ----------------------------------------------------------------------------
-- Query 3: Monthly revenue trend for the past 12 months, broken down by
-- customer tier
--
-- date_trunc('month', ...) buckets each order into a calendar month;
-- idx_orders_created_at (or idx_orders_status_created) bounds the scan to
-- the last 12 months before the aggregation runs.
-- ----------------------------------------------------------------------------
SELECT
    date_trunc('month', o.created_at)  AS revenue_month,
    c.tier                             AS customer_tier,
    SUM(oi.quantity * oi.unit_price)   AS revenue
FROM orders o
JOIN customers c    ON c.id = o.customer_id
JOIN order_items oi ON oi.order_id = o.id
WHERE o.created_at >= date_trunc('month', now()) - INTERVAL '11 months'
  AND o.status <> 'CANCELLED'
GROUP BY 1, 2
ORDER BY 1, 2;
