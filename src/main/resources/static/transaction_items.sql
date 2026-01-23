-- Database: junes
-- Schema: junes_rel

-- One-to-many
CREATE TABLE IF NOT EXISTS junes_rel.transaction_items
(
    transaction_item_id UUID PRIMARY KEY,
    transaction_id      UUID         NOT NULL REFERENCES junes_rel.transactions (transaction_id) ON DELETE CASCADE,
    product_id          VARCHAR(255) NOT NULL,
    quantity            INTEGER      NOT NULL CHECK (quantity > 0),
    created_on          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_on          TIMESTAMP
);

CREATE INDEX idx_transaction_items_transaction_id ON junes_rel.transaction_items (transaction_id);