-- Database: junes
-- Schema: junes_rel

CREATE TABLE idempotency_keys
(
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    key_value        VARCHAR(255) NOT NULL,
    status           VARCHAR(100) NOT NULL,
    response_payload TEXT,
    created_on       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_on       TIMESTAMP,
    CONSTRAINT uq_user_event_key_val UNIQUE (user_id, event_type, key_value)
);