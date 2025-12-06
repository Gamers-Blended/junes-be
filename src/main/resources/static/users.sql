-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.users (
    user_id UUID PRIMARY KEY, -- Generate in Java
    password_hash TEXT NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    is_active BOOLEAN NOT NULL,
    is_email_verified BOOLEAN NOT NULL,
    history_list JSONB NOT NULL DEFAULT '[]',
    address_list JSONB NOT NULL DEFAULT '[]',
    payment_info_list JSONB NOT NULL DEFAULT '[]',
    created_on TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_on TIMESTAMPTZ
);

-- Faster email lookups
CREATE INDEX idx_users_email ON junes_rel.users(email);