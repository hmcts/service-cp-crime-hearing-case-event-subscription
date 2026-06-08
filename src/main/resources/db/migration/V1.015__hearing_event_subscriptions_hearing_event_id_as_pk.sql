ALTER TABLE hearing_event_subscriptions DROP CONSTRAINT hearing_event_subscriptions_pkey;
ALTER TABLE hearing_event_subscriptions DROP COLUMN id;
DROP INDEX IF EXISTS idx_ns_sub_event;
DROP INDEX IF EXISTS idx_ns_hearing_event_id;
ALTER TABLE hearing_event_subscriptions ADD PRIMARY KEY (hearing_event_id);