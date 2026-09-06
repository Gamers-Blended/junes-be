-- Database: junes
-- Schema: junes_rel

CREATE TABLE dead_letter_events
(
    id                UUID PRIMARY KEY,
    original_topic    VARCHAR(100) NOT NULL,
    event_id          VARCHAR(100),
    event_type        VARCHAR(100),
    payload           JSONB        NOT NULL,
    exception_message TEXT,
    status            VARCHAR(100) NOT NULL,
    failed_on         TIMESTAMP    NOT NULL DEFAULT now(),
    resolved_on       TIMESTAMP
);

-- Powers the admin "list unresolved dead letters" query
CREATE INDEX idx_dead_letter_status
    ON dead_letter_events (status, failed_on);