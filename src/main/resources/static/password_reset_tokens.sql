-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.password_reset_tokens (
    password_reset_token_id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES junes_rel.users(user_id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_on TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Fast token lookup (most common query)
CREATE INDEX idx_token ON junes_rel.password_reset_tokens(token);

-- Fast user lookup (deleting old token)
CREATE INDEX idx_user_id ON junes_rel.password_reset_tokens(user_id);

-- Partial index for cleaning up of expired tokens
CREATE INDEX idx_expiry_date ON junes_rel.password_reset_tokens(expiry_date) WHERE used = FALSE;