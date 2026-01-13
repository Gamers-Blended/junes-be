-- Database: junes
-- Schema: junes_rel

-- One-to-many
CREATE TABLE IF NOT EXISTS junes_rel.transaction_items
(
    transaction_item_id UUID PRIMARY KEY,
    transaction_id      UUID           NOT NULL REFERENCES junes_rel.transactions (transaction_id) ON DELETE CASCADE,
    product_id          VARCHAR(255)   NOT NULL,
    name                VARCHAR(255)   NOT NULL,
    slug                VARCHAR(255)   NOT NULL,
    platform            VARCHAR(50)    NOT NULL,
    region              VARCHAR(50)    NOT NULL,
    edition             VARCHAR(50)    NOT NULL,
    price               NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    product_image_url   VARCHAR(500),
    quantity            INTEGER        NOT NULL CHECK (quantity > 0),
    created_on          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_on          TIMESTAMP
);

CREATE INDEX idx_transaction_items_transaction_id ON junes_rel.transaction_items (transaction_id);