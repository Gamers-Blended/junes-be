-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.wishlist_items
(
    wishlist_item_id UUID PRIMARY KEY,
    wishlist_id      UUID         NOT NULL,
    product_id       VARCHAR(255) NOT NULL,
    created_on       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_on       TIMESTAMP,

    CONSTRAINT fk_wishlist_items_wishlist FOREIGN KEY (wishlist_id)
        REFERENCES junes_rel.wishlists (wishlist_id) ON DELETE CASCADE,
    CONSTRAINT uk_wishlist_items_wishlist_product_id UNIQUE (wishlist_id, product_id)
);

CREATE INDEX idx_wishlist_items_wishlist_id ON junes_rel.wishlist_items (wishlist_id);
CREATE INDEX idx_wishlist_items_product_id ON junes_rel.wishlist_items (product_id);
