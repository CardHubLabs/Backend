create table payment_webhook_events (
    id uuid primary key,
    provider varchar(30) not null,
    event_key varchar(200) not null,
    event_type varchar(80) not null,
    payment_id uuid references payments(id),
    payment_key_hash varchar(64),
    payload_hash varchar(64) not null,
    status varchar(20) not null,
    received_at timestamptz not null,
    constraint uk_payment_webhook_provider_event unique (provider, event_key),
    constraint ck_payment_webhook_status check (status in ('ENQUEUED', 'IGNORED'))
);

create index ix_payment_webhook_payment_received
    on payment_webhook_events(payment_id, received_at desc);
