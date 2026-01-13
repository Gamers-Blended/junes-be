-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.transactions
(
    transaction_id      UUID PRIMARY KEY,
    order_number        VARCHAR(100) UNIQUE NOT NULL,
    order_date          TIMESTAMP           NOT NULL,
    status              VARCHAR(50)         NOT NULL,
    total_amount        NUMERIC(10, 2)      NOT NULL CHECK (total_amount >= 0),
    shipping_cost       NUMERIC(10, 2)      NOT NULL CHECK (shipping_cost >= 0),
    shipped_date        TIMESTAMP,
    shipping_weight     NUMERIC(10, 2)      NULL,
    tracking_number     VARCHAR(255),
    shipping_address_id UUID                NOT NULL REFERENCES junes_rel.addresses (address_id),
    user_id             UUID              NOT NULL REFERENCES junes_rel.users (user_id),
    created_on          TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_on          TIMESTAMP
);