# infra 模块

## 职责

提供与业务无关的技术能力，供业务模块使用：

- 系统健康检查：汇总外部依赖的连接状态。
- 对象存储：基于 S3 协议访问 MinIO，启动时确保存储桶存在。
- OpenAPI 文档配置。

`infra` 只依赖 `common`，不依赖任何业务模块。

## 数据模型

无。

## 对外接口

### HTTP 接口

| 方法 | 路径 | 说明 | 所需角色 |
|---|---|---|---|
| GET | `/api/system/health` | 系统整体状态及每个依赖的连接状态 | 无需登录 |

响应 `data`：

```json
{
  "status": "UP",
  "components": [
    { "name": "db", "status": "UP" },
    { "name": "objectStorage", "status": "UP" },
    { "name": "rabbit", "status": "UP" },
    { "name": "redis", "status": "UP" }
  ]
}
```

- `status` 取值 `UP`、`DOWN`、`OUT_OF_SERVICE`、`UNKNOWN`；任一依赖不可用时整体为 `DOWN`。
- 依赖不可用不影响接口本身的 HTTP 状态，接口始终返回 200，由 `status` 表达结果。

## 核心流程

### 健康检查

`SystemHealthService` 调用 Actuator 的 `HealthEndpoint`，把各健康指标转换为领域对象 `SystemHealth`。参与汇总的指标只有外部依赖：

| 组件 | 来源 |
|---|---|
| `db` | Spring Boot 内置（数据源） |
| `redis` | Spring Boot 内置（Spring Data Redis） |
| `rabbit` | Spring Boot 内置（Spring AMQP） |
| `objectStorage` | `ObjectStorageHealthIndicator`：对存储桶执行 `HeadBucket` |

磁盘空间、ping、SSL、存活与就绪探针等进程自身的指标在配置中关闭。

### 对象存储

- `StorageConfiguration` 创建 `S3Client`：path-style 访问，单次调用超时 5 秒。
- `BucketInitializer` 在应用启动时检查存储桶，不存在则创建。

## 依赖

| 依赖 | 配置 |
|---|---|
| PostgreSQL | `DB_*` |
| Redis | `REDIS_*` |
| RabbitMQ | `RABBITMQ_*` |
| MinIO | `MINIO_*`（`wenqu.storage.*`） |
