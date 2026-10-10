# 问渠 wenqu

面向 5～20 人技术团队的知识库。成员在这里撰写和导入文档，AI 依据团队自己的文档回答问题，并标注每个结论的来源。

> 问渠那得清如许，为有源头活水来。

## 架构

```mermaid
flowchart LR
    Browser[浏览器<br/>React 19] -->|/api| Backend[后端<br/>Spring Boot 4]
    Backend --> PG[(PostgreSQL 16<br/>+ pgvector)]
    Backend --> Redis[(Redis)]
    Backend <--> MQ[[RabbitMQ]]
    Backend --> MinIO[(MinIO)]
    Backend --> LLM[模型供应商]
```

详见 [docs/architecture.md](docs/architecture.md)。

## 本地启动

### 环境要求

| 工具 | 版本 |
|---|---|
| JDK | 21 |
| Node.js | 22 |
| pnpm | 10（`corepack enable` 后自动使用 `package.json` 中声明的版本） |
| Docker | Docker Desktop 或兼容运行时，需处于运行状态 |
| make | macOS / Linux 自带 |

Maven 无需安装，使用仓库内的 `backend/mvnw`。

### 步骤

```bash
git clone <仓库地址> wenqu && cd wenqu
make setup          # 生成 .env、安装前端依赖与 Git 钩子
make deps           # 启动 PostgreSQL 等依赖
make dev-backend    # 终端 1：启动后端
make dev-frontend   # 终端 2：启动前端
```

- 前端：<http://localhost:5173>
- 接口文档（Swagger UI）：<http://localhost:8080/swagger-ui.html>

端口和数据库账号在 `.env` 中配置；端口被占用时修改 `BACKEND_PORT`、`FRONTEND_PORT`、`DB_PORT`。

首次编译后端会下载依赖并拉取 PostgreSQL 镜像（jOOQ 代码生成和测试使用 Testcontainers），耗时取决于网络。

### 常用命令

```bash
make help
```

| 命令 | 作用 |
|---|---|
| `make check` | 全部检查：格式、lint、类型检查、架构测试、单元与集成测试 |
| `make fmt` | 自动格式化 |
| `make api` | 后端接口变化后，导出 `backend/openapi.json` 并生成前端接口代码 |
| `make db-reset` | 重置本地数据库 |

## 文档

| 文档 | 内容 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | AI 开发规则入口 |
| [docs/architecture.md](docs/architecture.md) | 整体架构 |
| [docs/conventions/](docs/conventions/) | 开发规范：文档、代码、接口、数据库、Git |
| [docs/modules/](docs/modules/) | 各模块设计 |
| [docs/_decisions/](docs/_decisions/) | 技术决策记录 |
