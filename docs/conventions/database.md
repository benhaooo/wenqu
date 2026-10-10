# 数据库规范

## Schema 划分

每个模块一个 schema，名称与模块名相同：`auth`、`workspace`、`document`、`ingestion`、`retrieval`、`chat`、`usage`、`infra`。原因见 [0005](../_decisions/0005-每个模块独立schema.md)。

- 模块只能读写自己 schema 中的表。
- 跨 schema 不建外键、不做 join；需要的关联字段冗余存储（如 `workspace_id`、`document_id`）。
- `public` schema 只存放 Flyway 历史表和扩展（如 `vector`）。

## 迁移脚本

- 由一个 Flyway 实例管理全部 schema，应用启动时自动执行。schema 由 Flyway 按配置创建。
- 脚本位置：`backend/src/main/resources/db/migration/<模块>/V<版本>__<描述>.sql`。
- 版本号全局唯一、递增，使用整数（`V1`、`V2`…）。描述使用小写英文和下划线：`V3__create_document_node.sql`。
- 表名写全限定名：`create table document.node (...)`。
- 上线前允许合并、重写迁移脚本，使其反映干净的当前表结构。重写后执行 `make db-reset`。
- 禁止手动修改数据库结构。

## 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 表 | 单数名词、snake_case | `document.node` |
| 列 | snake_case | `created_at` |
| 主键 | `id` | |
| 外键列 | `<表>_id` | `workspace_id` |
| 索引 | `idx_<表>_<列>` | `idx_node_parent_id` |
| 唯一约束 | `uk_<表>_<列>` | `uk_user_email` |
| 外键约束 | `fk_<表>_<被引用表>` | `fk_node_workspace` |

## 类型

| 用途 | 类型 |
|---|---|
| 主键 | `bigint generated always as identity` |
| 时间 | `timestamptz` |
| 枚举 | `text` + `check` 约束（不使用 PostgreSQL enum 类型，便于迁移） |
| JSON | `jsonb` |
| 向量 | `vector(<维度>)` |

- 每张业务表包含 `created_at timestamptz not null default now()`；会被修改的表包含 `updated_at`。
- 软删除使用 `deleted_at timestamptz`，不使用布尔标记。

## 索引

- 每个索引在迁移脚本中用注释说明服务于哪个查询。
- 外键列、常用过滤列建索引；新增查询时用 `explain analyze` 确认走索引。

## 访问方式

- 只通过 jOOQ 访问数据库，所有 SQL 参数化，不拼接字符串。
- jOOQ 代码在编译时由迁移后的表结构生成（`com.wenqu.jooq.<schema>`），生成代码不入库。
