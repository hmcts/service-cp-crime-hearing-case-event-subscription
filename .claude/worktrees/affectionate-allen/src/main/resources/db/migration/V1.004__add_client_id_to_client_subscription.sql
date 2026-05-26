DROP TABLE IF EXISTS client_subscription;

CREATE TABLE client_subscription (
    id                      uuid PRIMARY KEY NOT NULL,
    client_id               uuid UNIQUE NOT NULL,
    notification_endpoint   varchar(2048) NOT NULL,
    event_types             varchar(128)[] NOT NULL,
    created_at              timestamp NOT NULL,
    updated_at              timestamp NOT NULL
);

CREATE UNIQUE INDEX idx_client_subscription_client_id_endpoint
    ON client_subscription (client_id, notification_endpoint);

CREATE INDEX idx_client_subscription_client_id
    ON client_subscription (client_id);
