-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.addresses
(
    address_id   UUID PRIMARY KEY,
    full_name    VARCHAR(100) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    unit_number  VARCHAR(50),
    country      VARCHAR(50)  NOT NULL,
    zip_code     VARCHAR(20)  NOT NULL,
    phone_number VARCHAR(20)  NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id      UUID         NOT NULL REFERENCES junes_rel.users (user_id) ON DELETE CASCADE,
    created_on   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_on   TIMESTAMP,
    deleted_on   TIMESTAMP    NULL

);

CREATE INDEX idx_addresses_user_id ON junes_rel.addresses (user_id);
CREATE UNIQUE INDEX idx_one_default_per_user ON junes_rel.addresses (user_id) WHERE is_default = TRUE AND deleted_on IS NULL;