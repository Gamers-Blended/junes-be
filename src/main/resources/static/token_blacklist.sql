-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.token_blacklist
(
    token_id       UUID PRIMARY KEY,
    token          VARCHAR(255) NOT NULL UNIQUE,
    blacklisted_at TIMESTAMP    NOT NULL,
    expiry_date    TIMESTAMP    NOT NULL
);