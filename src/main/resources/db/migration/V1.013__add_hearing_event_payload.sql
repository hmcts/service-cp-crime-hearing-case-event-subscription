CREATE TABLE hearing_event_payload (
    hearing_event_id  uuid        PRIMARY KEY NOT NULL,
    event_type_id     integer     NOT NULL REFERENCES event_type(id),
    raw_payload       jsonb       NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hearing_event_payload_event_type ON hearing_event_payload (event_type_id);
