# retrieval 模块

## 职责

- 文本块的向量化与存储：整体替换一篇文档的文本块。
- 向量检索：在调用方给定的文档范围内，返回与问题最相关的文本块。

retrieval 不判断文档是否可访问，范围（未删除、在提问范围内的文档 id）由调用方通过 document 模块确定。

## 数据模型

`retrieval.chunk`：

| 列 | 说明 |
|---|---|
| `workspace_id`、`document_id` | 冗余自 document，见 [0005](../_decisions/0005-每个模块独立schema.md) |
| `document_version` | 生成文本块时的正文版本 |
| `chunk_index` | 文档内序号，`(document_id, chunk_index)` 唯一 |
| `heading_path`、`content`、`token_count` | 标题路径、正文、token 数 |
| `block_from`、`block_to` | 对应正文顶层块范围（含两端） |
| `embedding_model` | 生成向量的模型 |
| `embedding` | `vector(N)`，N 由迁移脚本占位符 `embedding_dimensions` 决定 |

| 索引 | 服务的查询 |
|---|---|
| `idx_chunk_embedding`（HNSW，`vector_cosine_ops`） | 余弦距离近邻检索 |
| `uk_chunk_document_id_chunk_index` | 按文档替换、删除，查询已索引版本 |

向量列维度在首次建表时由 `AI_EMBEDDING_DIMENSIONS` 决定（Flyway 占位符），pgvector 的 HNSW 索引支持不超过 2000 维。启动时 `EmbeddingColumnCheck` 比较列维度与配置，不一致时启动失败并提示重建数据库。jOOQ 通过 `com.wenqu.jooq.binding.PgVectorBinding` 把 `vector` 列映射为 `float[]`。

## 对外接口

### HTTP 接口

无。

### 模块 API（`com.wenqu.retrieval.api`）

| 方法 | 说明 |
|---|---|
| `indexedVersion(documentId)` | 文档当前索引对应的正文版本，没有文本块时为空 |
| `replaceChunks(workspaceId, documentId, version, drafts, onEmbedded)` | 向量化并整体替换文本块 |
| `search(workspaceId, documentIds, query, limit)` | 检索，按相似度从高到低 |

## 核心流程

### 替换文本块

1. 按 32 条一批调用 Embedding 模型（`TextEmbedder`），每批完成后回调进度。
2. 全部向量化完成后，在一个短事务中：获取文档级事务锁（`pg_advisory_xact_lock(hashtextextended('retrieval.chunk:' || documentId, 0))`），已有索引的版本比本次新时放弃写入，否则删除旧文本块、插入新文本块。

模型调用失败时已有文本块保持不变。并发的两个任务（如重复投递）由文档锁串行化，旧版本不会覆盖新版本。

### 检索

1. 问题向量化。
2. 在事务中执行 `set local hnsw.iterative_scan = relaxed_order`，再按余弦距离取最近的 `limit` 条，条件为 `workspace_id`、`document_id = any(范围)`、`embedding_model = 当前模型`。迭代扫描保证范围过滤严格时仍能取满，见 [0005](../_decisions/0005-每个模块独立schema.md)；`relaxed_order` 下结果可能略微乱序，取回后按相似度（`1 - 距离`）重新排序。

只比较当前模型生成的向量：更换 Embedding 模型后，旧文本块不参与检索，直到重新索引。

### 删除

文档移入回收站时文本块保留，检索范围由调用方按未删除文档确定，因此立即不会被检索到；从回收站恢复后无需重新索引。文档彻底删除后，收到 `document.purged` 事件删除其文本块，见 [0010](../_decisions/0010-文本块在文档彻底删除时清理.md)。

## 依赖

| 依赖 | 用途 |
|---|---|
| infra `TextEmbedder` | 向量化 |
| infra `EventQueue` | 订阅事件 |
| 订阅 `document.purged` | 删除文本块 |
| PostgreSQL `vector` 扩展（pgvector） | 向量存储与 HNSW 索引 |
