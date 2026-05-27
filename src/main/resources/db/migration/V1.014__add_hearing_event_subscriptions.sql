CREATE TABLE hearing_event_subscriptions (
    id                uuid        PRIMARY KEY NOT NULL,
    subscription_id   uuid        NOT NULL REFERENCES client(subscription_id),
    hearing_event_id  uuid        NOT NULL REFERENCES hearing_event_payload(hearing_event_id),
    created_at        timestamptz NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_ns_sub_event ON hearing_event_subscriptions (subscription_id, hearing_event_id);
CREATE INDEX idx_ns_hearing_event_id ON hearing_event_subscriptions (hearing_event_id);
