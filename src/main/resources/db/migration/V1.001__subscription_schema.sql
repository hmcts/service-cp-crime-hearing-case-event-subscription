create table client_subscription (
    id                      uuid primary key not null,
    event_types             varchar(128) not null,
    notification_endpoint   varchar(2048) not null,
    created_at              timestamp not null,
    updated_at              timestamp not null
);
