-- temp addition till we switch to client and client_hmac
alter table client_subscription add hmac_key_id varchar(64);


CREATE TABLE client_hmac (
    id serial primary key,
    subscription_id uuid not null,
    key_id varchar(64) not null,

    CONSTRAINT fk_client
    FOREIGN KEY (subscription_id)
    REFERENCES client(subscription_id)
);
