create table users (
    id uuid primary key,
    email varchar(320),
    nickname varchar(30) not null,
    birth_date date,
    terms_agreed_at timestamptz,
    role varchar(20) not null,
    status varchar(20) not null,
    anonymized_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint ck_users_role check (role in ('USER', 'ADMIN')),
    constraint ck_users_status check (status in ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
);

create index ix_users_status_created_at on users (status, created_at desc);

create table social_accounts (
    id uuid primary key,
    user_id uuid not null references users(id),
    provider varchar(20) not null,
    provider_subject varchar(255) not null,
    created_at timestamptz not null,
    constraint ck_social_accounts_provider check (provider in ('GOOGLE', 'KAKAO', 'NAVER')),
    constraint uk_social_accounts_provider_subject unique (provider, provider_subject)
);

create index ix_social_accounts_user_id on social_accounts (user_id);

create table auth_sessions (
    id uuid primary key,
    user_id uuid not null references users(id),
    refresh_token_hash varchar(64) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    replaced_by_session_id uuid,
    user_agent varchar(512),
    ip_hash varchar(64),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uk_auth_sessions_refresh_token_hash unique (refresh_token_hash),
    constraint fk_auth_sessions_replacement foreign key (replaced_by_session_id) references auth_sessions(id)
);

create index ix_auth_sessions_user_active on auth_sessions (user_id, expires_at) where revoked_at is null;
