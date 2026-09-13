
-- gen_random_uuid() lives in pgcrypto on Postgres 15 (it's only built into
-- core as of Postgres 16), so it must be enabled explicitly here.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- customers
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    region      VARCHAR(100),
    -- Enums are modelled as VARCHAR + CHECK rather than a native Postgres
    -- ENUM type: adding a new tier/status later is a one-line ALTER ... CHECK
    -- instead of ALTER TYPE, which can't run inside some transactions.
    tier        VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
                    CHECK (tier IN ('STANDARD', 'PREMIUM', 'ENTERPRISE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customers_email UNIQUE (email)
);

-- ----------------------------------------------------------------------------
-- products
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    sku             VARCHAR(50)  NOT NULL,
    category        VARCHAR(100),
    unit_price      INTEGER NOT NULL CHECK (unit_price >= 0),
    stock_quantity  INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku)
);

-- ----------------------------------------------------------------------------
-- orders
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID NOT NULL REFERENCES customers (id),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    
    total_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- order_items
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id  UUID NOT NULL REFERENCES products (id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    unit_price  INTEGER NOT NULL CHECK (unit_price >= 0)
);


CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders (customer_id, created_at DESC);

-- GET /api/orders filtered by status, and the general "recent orders" scans
-- used in Query 2/3.
CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders (status, created_at DESC);

-- Plain date-range scans (GET /api/orders?from=&to= with no status/customer)
-- and Query 3's 12-month trend.
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at DESC);

-- order_items is almost always joined back to its parent order or product.
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items (product_id);

-- Query 2: "stock below 20" is a highly selective filter on a low-cardinality
-- range - a partial index keeps it tiny even at tens of millions of rows,
-- since only genuinely low-stock products are ever indexed.
CREATE INDEX IF NOT EXISTS idx_products_low_stock ON products (stock_quantity) WHERE stock_quantity < 20;

-- Query 3 groups by customer tier.
CREATE INDEX IF NOT EXISTS idx_customers_tier ON customers (tier);
