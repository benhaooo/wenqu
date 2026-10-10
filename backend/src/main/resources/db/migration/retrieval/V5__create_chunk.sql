create extension if not exists vector with schema public;

-- 文档切分后的文本块与向量。document_id、workspace_id 冗余自 document 模块，见 docs/_decisions/0005
create table retrieval.chunk
(
    id               bigint generated always as identity primary key,
    workspace_id     bigint      not null,
    document_id      bigint      not null,
    -- 生成文本块时的正文版本，旧版本的索引结果不能覆盖新版本
    document_version integer     not null,
    chunk_index      integer     not null,
    -- 文本块所在的标题路径，如「部署手册 > 回滚」
    heading_path     text        not null,
    content          text        not null,
    token_count      integer     not null,
    -- 对应正文顶层块的范围（含两端），用于跳转原文并高亮
    block_from       integer     not null,
    block_to         integer     not null,
    -- 生成向量的模型，检索时只使用当前模型生成的向量
    embedding_model  text        not null,
    embedding        public.vector(${embedding_dimensions}) not null,
    created_at       timestamptz not null default now(),
    constraint uk_chunk_document_id_chunk_index unique (document_id, chunk_index),
    constraint ck_chunk_block_range check (block_from <= block_to)
);

-- 向量近邻检索（余弦距离）
create index idx_chunk_embedding on retrieval.chunk using hnsw (embedding public.vector_cosine_ops);
