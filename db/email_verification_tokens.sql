-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.email_verification_tokens
(
    email_verification_id BIGSERIAL PRIMARY KEY,
    user_id               UUID         NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    token_hash            VARCHAR(255) NOT NULL UNIQUE,
    purpose               VARCHAR(50)  NOT NULL,
    expiry_date           TIMESTAMP    NOT NULL,
    used                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_on            TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Fast token lookup (most common query)
CREATE INDEX idx_token_hash ON junes_rel.email_verification_tokens (token_hash);

-- Fast user lookup (invalidate old token(s))
CREATE INDEX idx_user_id_purpose_used ON junes_rel.email_verification_tokens (user_id, purpose, used);

ALTER TABLE junes_rel.email_verification_tokens
    ADD CONSTRAINT chk_email_verification_tokens_purpose
        CHECK (purpose IN ('SIGNUP_EMAIL', 'CHANGE_EMAIL'));