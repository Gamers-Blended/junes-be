-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.archived_carts
(
    archived_cart_id UUID PRIMARY KEY,
    order_number     VARCHAR(100)   NOT NULL,
    user_id          UUID           NOT NULL,
    cart_data        JSONB          NOT NULL, -- Store entire cart as JSON
    total_amount     NUMERIC(10, 2) NOT NULL,
    total_items      INTEGER        NOT NULL,
    archived_on      TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_archived_carts_order_number UNIQUE (order_number)
);

CREATE INDEX idx_archived_carts_order_number ON archived_carts(order_number);
CREATE INDEX idx_archived_carts_user_id ON archived_carts(user_id);
CREATE INDEX idx_archived_carts_archived_on ON archived_carts(archived_on);