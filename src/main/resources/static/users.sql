-- Database: junes

CREATE TABLE IF NOT EXISTS junes_rel.users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    is_active BOOLEAN NOT NULL,
    history_list JSONB NOT NULL DEFAULT '[]',
    address_list JSONB NOT NULL DEFAULT '[]',
    payment_info_list JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);