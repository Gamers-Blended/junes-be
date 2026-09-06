-- Database: junes
-- Schema: junes_rel

-- For registered users only
CREATE TABLE IF NOT EXISTS junes_rel.wishlists
(
    wishlist_id UUID PRIMARY KEY,
    user_id     UUID      NOT NULL,
    created_on  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_on  TIMESTAMP,
    version     INTEGER   NOT NULL DEFAULT 0,

    CONSTRAINT uk_wishlists_user_id UNIQUE (user_id)
);

CREATE INDEX idx_wishlists_updated_on ON junes_rel.wishlists (updated_on);
