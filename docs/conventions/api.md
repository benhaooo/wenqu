# 接口规范

## 路径与方法

- 所有接口以 `/api` 开头，资源名使用复数名词、kebab-case：`/api/workspaces/{workspaceId}/documents`。
- 属于空间的资源嵌套在空间路径下：`/api/workspaces/{workspaceId}/...`，路径变量名固定为 `workspaceId`。这类接口必须标注 `@RequireWorkspaceRole`，访问控制见 [workspace 模块](../modules/workspace.md#空间内接口的访问控制)。
- 方法语义：`GET` 查询、`POST` 创建或执行动作、`PUT` 整体替换、`PATCH` 部分更新、`DELETE` 删除。
- 不适合用 CRUD 表达的动作使用子路径动词：`POST /api/.../documents/{id}/restore`。

## 认证

- 除注册、登录、刷新、退出、健康检查和接口文档外，所有接口需要在请求头携带 `Authorization: Bearer <accessToken>`。
- 令牌机制见 [auth 模块](../modules/auth.md)。

## 统一响应格式

所有接口（包括错误）返回：

```json
{ "code": 0, "message": "成功", "data": { } }
```

| 字段 | 说明 |
|---|---|
| `code` | `0` 表示成功，其他值为 `ErrorCode` 中定义的错误码 |
| `message` | 面向用户的提示信息 |
| `data` | 业务数据；出错时为 `null` |

- HTTP 状态码与错误语义一致（400、401、403、404、409、429、500 等），`code` 提供更细的区分。
- 流式接口（SSE）是唯一例外，事件格式在对应模块文档中定义。

后端实现：`com.wenqu.common.web.ApiResult`。

## 错误码

- 全部错误码定义在 `com.wenqu.common.error.ErrorCode` 一个枚举中，每项包含错误码、HTTP 状态、默认提示。
- 号段按模块划分：

| 号段 | 模块 |
|---|---|
| 10000–10999 | 通用 |
| 11000–11999 | auth |
| 12000–12999 | workspace |
| 13000–13999 | document |
| 14000–14999 | ingestion |
| 15000–15999 | retrieval |
| 16000–16999 | chat |
| 17000–17999 | usage |

- 前端根据 `code` 做特殊处理（如刷新 Token），提示信息直接展示 `message`。

## 参数与数据格式

- JSON 字段 camelCase。
- 时间使用 ISO 8601 UTC 字符串（`2026-10-10T08:00:00Z`）。
- ID 使用 `long`（JSON number）。
- 请求参数使用 Jakarta Validation 注解校验，校验失败返回 `VALIDATION_FAILED`。
- 列表分页参数 `page`（从 1 开始）、`size`，响应 `{ items, total, page, size }`。

## 接口文档与契约

- 接口文档由 springdoc 自动生成：启动后访问 `/swagger-ui.html`。
- 每个 controller 使用 `@Tag`，每个接口使用 `@Operation(summary = ...)`。
- `backend/openapi.json` 是接口契约，接口变化后执行 `make api` 更新并提交，见 [architecture.md](../architecture.md#接口契约)。
