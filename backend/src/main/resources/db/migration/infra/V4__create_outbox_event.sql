-- 事务性发件箱：业务模块在自己的事务中写入，OutboxRelay 投递到 RabbitMQ，见 docs/_decisions/0006
create table infra.outbox_event
(
    id           bigint generated always as identity primary key,
    -- RabbitMQ 路由键，如 document.content-changed
    routing_key  text        not null,
    payload      jsonb       not null,
    -- 未投递的事件中唯一：再次写入同一个键时合并为一条并推迟投递时间（防抖）
    dedup_key    text,
    available_at timestamptz not null default now(),
    created_at   timestamptz not null default now(),
    published_at timestamptz
);

-- OutboxRelay 读取到期未投递的事件
create index idx_outbox_event_available_at on infra.outbox_event (available_at) where published_at is null;
-- 防抖合并
create unique index uk_outbox_event_dedup_key on infra.outbox_event (dedup_key) where published_at is null;
-- 清理已投递的旧事件
create index idx_outbox_event_published_at on infra.outbox_event (published_at) where published_at is not null;
