-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.transaction_items (
    transaction_item_id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES junes_rel.transactions(transaction_id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    price_at_time_of_sale NUMERIC(10, 2) NOT NULL,
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ
);