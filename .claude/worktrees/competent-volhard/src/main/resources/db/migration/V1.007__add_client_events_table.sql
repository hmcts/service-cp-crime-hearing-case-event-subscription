CREATE TABLE client_events (
    id SERIAL PRIMARY KEY,
    subscription_id uuid NOT NULL,
    event_type_id integer NOT NULL,

    CONSTRAINT fk_client
    FOREIGN KEY (subscription_id)
    REFERENCES client(subscription_id),

    CONSTRAINT fk_event_type
    FOREIGN KEY (event_type_id)
    REFERENCES event_type(id)
);