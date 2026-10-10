-- 会话只属于创建者本人
create table chat.conversation
(
    id           bigint generated always as identity primary key,
    workspace_id bigint      not null,
    user_id      bigint      not null,
    title        text        not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- 用户在空间中的会话列表，最近活动的在前
create index idx_conversation_workspace_id_user_id on chat.conversation (workspace_id, user_id, updated_at desc);

create table chat.message
(
    id              bigint generated always as identity primary key,
    conversation_id bigint      not null,
    role            text        not null check (role in ('USER', 'ASSISTANT')),
    content         text        not null,
    -- 回答状态：完成、用户停止、模型调用失败；提问固定为 COMPLETED
    status          text        not null check (status in ('COMPLETED', 'STOPPED', 'FAILED')),
    -- 提问的范围：整个空间、文件夹或单篇文档
    scope_type      text check (scope_type in ('WORKSPACE', 'FOLDER', 'DOCUMENT')),
    scope_node_id   bigint,
    created_at      timestamptz not null default now(),
    constraint fk_message_conversation foreign key (conversation_id) references chat.conversation (id) on delete cascade,
    constraint ck_message_scope check ((role = 'USER') = (scope_type is not null))
);

-- 外键列；按时间读取会话中的消息
create index idx_message_conversation_id on chat.message (conversation_id, id);

-- 回答引用的文本块。原文片段在回答时保存，文档后续修改或重新索引不影响历史回答的展示
create table chat.citation
(
    id           bigint generated always as identity primary key,
    message_id   bigint  not null,
    -- 回答正文中的角标序号 [n]
    marker       integer not null,
    document_id  bigint  not null,
    -- 回答时的文档标题
    document_title text  not null,
    chunk_id     bigint  not null,
    heading_path text    not null,
    snippet      text    not null,
    block_from   integer not null,
    block_to     integer not null,
    similarity   real    not null,
    constraint fk_citation_message foreign key (message_id) references chat.message (id) on delete cascade,
    constraint uk_citation_message_id_marker unique (message_id, marker)
);

create table chat.feedback
(
    message_id bigint primary key,
    rating     text        not null check (rating in ('UP', 'DOWN')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_feedback_message foreign key (message_id) references chat.message (id) on delete cascade
);
