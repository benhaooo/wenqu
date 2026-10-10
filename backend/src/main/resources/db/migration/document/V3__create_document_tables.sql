-- 标题与正文的模糊搜索（ILIKE）使用 trigram 索引
create extension if not exists pg_trgm with schema public;

create table document.node
(
    id               bigint generated always as identity primary key,
    workspace_id     bigint      not null,
    parent_id        bigint,
    type             text        not null check (type in ('FOLDER', 'DOCUMENT')),
    title            text        not null check (length(title) between 1 and 200),
    -- 分数索引排序键，按字节序比较
    position         text collate "C" not null,
    created_by       bigint      not null,
    updated_by       bigint      not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    deleted_at       timestamptz,
    deleted_by       bigint,
    -- 同一次删除操作进入回收站的节点共享，恢复时整批恢复
    deleted_batch_id uuid,
    -- 彻底删除文件夹时其下节点一并删除
    constraint fk_node_parent foreign key (parent_id) references document.node (id) on delete cascade,
    constraint ck_node_deleted check ((deleted_at is null) = (deleted_batch_id is null))
);

-- 加载空间目录树：只查未删除节点
create index idx_node_workspace_id on document.node (workspace_id) where deleted_at is null;
-- 外键列；查询子节点
create index idx_node_parent_id on document.node (parent_id);
-- 回收站列表、恢复、定期清理
create index idx_node_deleted_batch_id on document.node (workspace_id, deleted_batch_id) where deleted_at is not null;
-- 标题模糊搜索
create index idx_node_title_trgm on document.node using gin (title public.gin_trgm_ops);

create table document.content
(
    node_id      bigint primary key,
    -- ProseMirror 文档 JSON
    content_json jsonb       not null default '{"type": "doc", "content": []}',
    -- 纯文本，用于搜索和 AI 索引
    plain_text   text        not null default '',
    -- 乐观并发控制：保存时必须基于最新版本
    version      integer     not null default 0,
    updated_at   timestamptz not null default now(),
    constraint fk_content_node foreign key (node_id) references document.node (id) on delete cascade
);

-- 正文模糊搜索
create index idx_content_plain_text_trgm on document.content using gin (plain_text public.gin_trgm_ops);

create table document.image
(
    id           uuid primary key,
    workspace_id bigint      not null,
    node_id      bigint      not null,
    object_key   text        not null,
    content_type text        not null,
    size_bytes   bigint      not null,
    created_by   bigint      not null,
    created_at   timestamptz not null default now(),
    constraint fk_image_node foreign key (node_id) references document.node (id) on delete cascade
);

-- 外键列；清理回收站时查找待删除文档的图片
create index idx_image_node_id on document.image (node_id);
