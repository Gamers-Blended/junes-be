-- Database: junes
-- Schema: junes_rel

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL, -- e.g. 'Order'
    aggregate_id   VARCHAR(100) NOT NULL, -- e.g. orderNumber, also used as Kafka partition key
    event_type     VARCHAR(100) NOT NULL, -- e.g. 'OrderCreated', 'PaymentSucceeded'
    topic          VARCHAR(100) NOT NULL DEFAULT 'order-events',
    payload        JSONB        NOT NULL,
    created_on     TIMESTAMP    NOT NULL DEFAULT now(),
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    published_on   TIMESTAMP,
    retry_count    INTEGER      NOT NULL DEFAULT 0
);

-- Speeds up relay's polling query: "get the oldest N unpublished events
-- Partial index only covers unpublished rows
-- so that it stays small as table grows
CREATE INDEX idx_outbox_unpublished
    ON outbox_events (created_on)
    WHERE published = FALSE;

-- Debugging/sporrt: "get every event for order X"
-- Since aggregate_id = Kafka partition key and natural trace-through field
CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_on);