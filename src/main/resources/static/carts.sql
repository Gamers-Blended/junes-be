-- Database: junes
-- Schema: junes_rel

-- For registered users only
CREATE TABLE IF NOT EXISTS junes_rel.carts
(
    cart_id    UUID PRIMARY KEY,
    user_id    UUID      NOT NULL,
    session_id UUID      NOT NULL,
    created_on TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMP,
    version    INTEGER   NOT NULL DEFAULT 0,

    CONSTRAINT uk_carts_user_id UNIQUE (user_id)
);

CREATE INDEX idx_carts_updated_on ON carts (updated_on);
