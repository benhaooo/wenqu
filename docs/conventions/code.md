# 代码规范

## 通用

- 不保留注释掉的代码、未使用的代码、兼容代码、临时实现。
- 注释解释「为什么」，不复述代码在做什么。
- 命名使用完整英文单词，不使用拼音。

## 后端

### 包结构与分层

结构和依赖方向见 [architecture.md](../architecture.md#模块内部分层)。补充约定：

| 包 | 放什么 | 约定 |
|---|---|---|
| `api` | `XxxApi` 接口、跨模块 DTO（`XxxDto` 或语义化 record）、领域事件 `XxxEvent` | 只依赖 `common`、JDK、校验与 OpenAPI 注解；不引用本模块其他包 |
| `controller` | `@RestController`、`XxxRequest`、`XxxResponse` | 只做参数校验、类型转换、调用 service；`domain` 对象不直接作为接口响应 |
| `service` | `@Service` 业务实现，实现本模块 `api` 接口；事件监听器（`@RabbitListener`）与定时任务（`@Scheduled`） | 事务边界（`@Transactional`）只放在 service；监听器只做参数转换并调用业务方法，处理必须幂等 |
| `repository` | 基于 jOOQ `DSLContext` 的数据访问类 | 返回 `domain` 对象，jOOQ `Record` 不离开本包 |
| `domain` | 领域对象（record / class / enum） | 不依赖 Spring、jOOQ |

`infra` 按能力划分子包（如 `infra.system`、`infra.outbox`），子包内部同样遵循上述分层。

### 编码

- Java 21：DTO 和不可变值对象使用 `record`；优先使用 `switch` 模式匹配、`var`（类型显而易见时）。
- 依赖注入使用构造器注入，字段为 `private final`，不使用 `@Autowired` 字段注入。
- 业务错误抛出 `BusinessException(ErrorCode, message)`，由 `GlobalExceptionHandler` 统一转换，不在 controller 中捕获再包装。
- 不使用 `Optional` 作为字段或参数，只作为查询方法的返回值。
- 时间统一使用 `Instant`（数据库 `timestamptz`），只在展示层转换时区。
- 日志使用 SLF4J，参数用占位符 `{}`；不记录密码、Token、API Key 等敏感信息。

### 格式

Spotless + palantir-java-format，`make fmt` 自动格式化。提交前钩子和 CI 执行 `spotless:check`。

### 测试

| 类型 | 命名 | 说明 |
|---|---|---|
| 单元测试 | `XxxTest` | 不启动 Spring，覆盖核心业务逻辑（权限计算、切块、限流、状态流转） |
| 集成测试 | `XxxTest` | 标注 `@IntegrationTest`，用 Testcontainers 启动真实依赖，所有集成测试共享一个 Spring 上下文和容器 |
| 架构测试 | `com.wenqu.architecture` | ArchUnit 规则及其自检 |

## 前端

### 目录与边界

结构见 [architecture.md](../architecture.md#前端结构)。每个 feature 内部组织：

```
features/<feature>/
├── index.ts        对外入口：只导出其他模块需要的组件、hooks、类型
├── components/
├── hooks/
└── store.ts        仅在该 feature 需要客户端状态时存在（Zustand）
```

### 编码

- 组件使用函数组件和 hooks，文件名 `PascalCase.tsx`，其他文件 `camelCase.ts`。
- 接口请求只使用 `shared/api/generated` 中生成的 hooks 和类型，不手写接口类型，不直接调用 `fetch`。
- 服务端数据不复制到 Zustand；Zustand 只保存客户端状态（如当前空间、Access Token、UI 状态）。
- 渲染用户内容或 AI 回答时不使用 `dangerouslySetInnerHTML`，确需渲染 HTML 时先经过净化。
- 样式使用 Tailwind 工具类，通用组件放 `shared/ui`（shadcn/ui）。
- 颜色、圆角、字体只使用 `src/index.css` 中定义的设计令牌（如 `bg-primary`、`text-muted-foreground`、`border-border-subtle`），令牌取值来自 UI 原型；不在组件中新增零散的颜色值。
- 中文使用系统字体，西文与数字使用 IBM Plex Sans，代码使用 JetBrains Mono（均为本地依赖，不请求外部字体服务）。

### 格式与检查

Prettier 负责格式，ESLint 负责代码质量与模块边界，`tsc` 负责类型检查，Vitest 负责测试。
