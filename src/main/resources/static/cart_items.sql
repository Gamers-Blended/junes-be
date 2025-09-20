-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.cart_items (
    cart_item_id BIGSERIAL PRIMARY KEY,
    -- user_id in cart_items must contain only values inside users.user_id (parent)
    -- when users.user_id is deleted, delete all rows in cart_items with same user_id
    user_id BIGINT NOT NULL REFERENCES junes_rel.users(user_id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ,
    -- Prevent duplicate products for same user
    UNIQUE(user_id, product_id)
);

INSERT INTO junes_rel.cart_items (user_id, product_id, quantity, created_on)
VALUES(1, '681a55f2cb20535492b5e691', 2, '2025-05-31 23:30:00');

INSERT INTO junes_rel.cart_items (user_id, product_id, quantity, created_on)
VALUES(1, '681a55f2cb20535492b5e68d', 3, '2025-05-31 23:40:00');