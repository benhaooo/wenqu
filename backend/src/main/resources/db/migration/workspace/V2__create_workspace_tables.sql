create table workspace.space
(
    id         bigint generated always as identity primary key,
    name       text        not null check (length(name) between 1 and 50),
    created_by bigint      not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table workspace.member
(
    workspace_id bigint      not null,
    user_id      bigint      not null,
    role         text        not null check (role in ('ADMIN', 'EDITOR', 'VIEWER')),
    joined_at    timestamptz not null default now(),
    primary key (workspace_id, user_id),
    constraint fk_member_space foreign key (workspace_id) references workspace.space (id) on delete cascade
);

-- 查询「我加入的空间」
create index idx_member_user_id on workspace.member (user_id);

create table workspace.invitation
(
    id           bigint generated always as identity primary key,
    workspace_id bigint      not null,
    token_hash   text        not null,
    role         text        not null check (role in ('ADMIN', 'EDITOR', 'VIEWER')),
    created_by   bigint      not null,
    created_at   timestamptz not null default now(),
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    constraint fk_invitation_space foreign key (workspace_id) references workspace.space (id) on delete cascade
);

-- 打开邀请链接时按 Token 哈希查找
create unique index uk_invitation_token_hash on workspace.invitation (token_hash);
-- 管理员查看空间的邀请列表；外键列
create index idx_invitation_workspace_id on workspace.invitation (workspace_id);
