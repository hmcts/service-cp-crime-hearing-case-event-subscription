create table subscriber (
    id                  uuid primary key not null,
    name                varchar(128) not null
);
create unique index on subscriber (name);

create table subscription (
    id                  uuid primary key not null,
    subscriber_id       uuid  not null,
    event_type          varchar(32) not null,
    notify_url          text        not null
);

alter table subscription
    add constraint subscription_subscriber_id_fk foreign key (subscriber_id) references subscriber (id);