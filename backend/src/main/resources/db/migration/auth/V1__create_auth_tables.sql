create table auth.user_account
(
    id            bigint generated always as identity primary key,
    email         text        not null,
    password_hash text        not null,
    display_name  text        not null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

-- 注册查重与登录按邮箱查找，邮箱不区分大小写
create unique index uk_user_account_email on auth.user_account (lower(email));

create table auth.refresh_token
(
    id         bigint generated always as identity primary key,
    user_id    bigint      not null,
    token_hash text        not null,
    family_id  uuid        not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    constraint fk_refresh_token_user_account foreign key (user_id) references auth.user_account (id) on delete cascade
);

-- 刷新时按 Token 哈希查找
create unique index uk_refresh_token_token_hash on auth.refresh_token (token_hash);
-- 退出登录、检测到 Token 重用时按 family 批量作废
create index idx_refresh_token_family_id on auth.refresh_token (family_id);
-- 外键列
create index idx_refresh_token_user_id on auth.refresh_token (user_id);
