-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.users
(
    user_id                      UUID PRIMARY KEY, -- Generate in Java
    password_hash                TEXT         NOT NULL,
    email                        VARCHAR(255) NOT NULL,
    is_active                    BOOLEAN      NOT NULL,
    is_email_verified            BOOLEAN      NOT NULL,
    verification_token_hash      TEXT,
    verification_token_issued_at BIGINT,
    last_login_at                TIMESTAMP,
    role                         VARCHAR(255) NOT NULL,
    history_list                 JSONB        NOT NULL DEFAULT '[]',
    address_list                 JSONB        NOT NULL DEFAULT '[]',
    payment_info_list            JSONB        NOT NULL DEFAULT '[]',
    created_on                   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_on                   TIMESTAMP
);

-- Uniqueness only for verified emails
CREATE UNIQUE INDEX unique_verified_email
    ON junes_rel.users (email)
    WHERE is_email_verified = true;

-- Faster email lookups
CREATE INDEX idx_users_email ON junes_rel.users (email);