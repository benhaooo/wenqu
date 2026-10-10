-- 索引任务：导入文件的解析与向量化，以及正文修改后的重新索引
create table ingestion.ingest_job
(
    id             bigint generated always as identity primary key,
    workspace_id   bigint      not null,
    document_id    bigint      not null,
    trigger        text        not null check (trigger in ('IMPORT', 'EDIT', 'REINDEX')),
    status         text        not null check (status in ('QUEUED', 'PARSING', 'EMBEDDING', 'SUCCEEDED', 'FAILED')),
    -- 导入的原文件，trigger = IMPORT 时存在
    file_name      text,
    file_size      bigint,
    media_type     text,
    object_key     text,
    -- 原文件已解析并写入文档正文，重试时不再重复解析
    parsed_at      timestamptz,
    chunk_count    integer,
    embedded_count integer     not null default 0,
    attempts       integer     not null default 0,
    error_message  text,
    created_by     bigint      not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    finished_at    timestamptz,
    constraint ck_ingest_job_import_file check ((trigger = 'IMPORT') = (object_key is not null))
);

-- 导入列表：空间内的导入任务按时间倒序
create index idx_ingest_job_workspace_id on ingestion.ingest_job (workspace_id, id desc) where trigger = 'IMPORT';
-- 空间内每篇文档最近一次任务（目录树索引状态）；查找文档排队中的任务、导入的原文件
create index idx_ingest_job_workspace_id_document_id on ingestion.ingest_job (workspace_id, document_id, id desc);
-- 排队位置：统计更早进入队列的任务
create index idx_ingest_job_queued on ingestion.ingest_job (id) where status = 'QUEUED';
