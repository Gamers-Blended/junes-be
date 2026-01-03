-- Database: junes
-- Schema: junes_rel

CREATE TABLE IF NOT EXISTS junes_rel.payment_methods
(
    payment_method_id  UUID PRIMARY KEY,

    -- Display only
    card_type          VARCHAR(20)  NOT NULL,
    card_last_four     VARCHAR(4)   NOT NULL,
    card_holder_name   VARCHAR(100) NOT NULL,
    expiration_month   VARCHAR(2)   NOT NULL,
    expiration_year    VARCHAR(4)   NOT NULL,

    -- Foreign keys
    billing_address_id UUID         NOT NULL REFERENCES junes_rel.addresses (address_id),
    user_id            UUID         NOT NULL REFERENCES junes_rel.users (user_id) ON DELETE CASCADE,

    -- Status and metadata
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    status             VARCHAR(20)  NOT NULL DEFAULT 'active',

    -- Audit
    created_on         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_on         TIMESTAMP

    -- Constraint
    CONSTRAINT valid_expiration CHECK (
        expiration_year::integer >= EXTRACT(YEAR FROM NOW())::integer
            OR (expiration_year::integer = EXTRACT(YEAR FROM NOW())::integer
            AND expiration_month::integer >= EXTRACT(MONTH FROM NOW())::integer)
        ),
    CONSTRAINT valid_month CHECK (expiration_month::integer BETWEEN 1 AND 12),
    CONSTRAINT valid_last_four CHECK (card_last_four ~ '^[0-9]{4}$')

);

CREATE INDEX idx_payment_method_user_id ON junes_rel.payment_methods (user_id);
CREATE UNIQUE INDEX idx_one_default_payment_per_user ON junes_rel.payment_methods (user_id) WHERE is_default = TRUE;