-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.cart_items (
    cart_item_id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMP,

    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id)
        REFERENCES carts(cart_id) ON DELETE CASCADE,
    CONSTRAINT uk_cart_items_cart_product_id UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart_id ON junes_rel.cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON junes_rel.cart_items(product_id);