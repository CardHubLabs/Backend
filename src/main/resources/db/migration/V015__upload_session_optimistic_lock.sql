alter table upload_sessions
    add column version bigint not null default 0;
