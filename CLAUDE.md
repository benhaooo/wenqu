# CLAUDE.md

问渠（wenqu）：面向 5～20 人技术团队的知识库。成员撰写和导入文档，AI 依据团队文档回答问题并标注来源。

## 开始任何工作前

1. 阅读 [docs/architecture.md](docs/architecture.md) 和要改动模块的文档 `docs/modules/<模块>.md`。
2. 阅读与本次改动相关的规范：[docs/conventions/](docs/conventions/)。
3. 新模块或较大改动：先输出设计（数据模型、对外接口、核心流程、依赖），经用户确认后再实现。

## 硬性规则

- **与架构冲突时停止实现**：说明冲突点，提出调整方案，确认后再改。禁止用变通写法绕开模块边界、分层或依赖方向。
- **不考虑向后兼容**（项目未上线）：不保留兼容代码、注释掉的代码、未使用的代码，不写临时实现、TODO 占位或桩代码。
- **一次改到位**：代码、测试、现状文档在同一次提交中修改。架构调整同时新增一条决策记录（`docs/_decisions/`）。
- **数据库**：表结构只通过 Flyway 迁移脚本变更。上线前允许合并、重写迁移脚本，使其反映干净的当前表结构。
- **接口类型**：前端不手写接口类型，一律由 `backend/openapi.json` 生成（`make api`）。
- **密钥**：只通过环境变量注入，新增变量同步写入 `.env.example`，真实值不进仓库。
- **文档**：现状文档只用陈述句描述当前实现，规则见 [docs/conventions/docs.md](docs/conventions/docs.md)。

## 自动检查

以下规则由工具强制，违反时测试失败或无法提交，不要通过修改检查配置来让代码通过：

| 规则 | 检查方式 |
|---|---|
| 后端模块边界、分层、无循环依赖、表归属 | ArchUnit：`backend/src/test/java/com/wenqu/architecture/` |
| 前端 feature 之间只能通过 `index.ts` 引用 | ESLint `boundaries` 规则 |
| Java 格式 | Spotless（palantir-java-format） |
| 前端格式与 lint | Prettier、ESLint |
| 接口契约与后端代码一致 | `OpenApiContractTest` |
| 提交信息 | commitlint（Conventional Commits） |

## 常用命令

```bash
make setup         # 安装依赖与 Git 钩子
make deps          # 启动 Docker 依赖
make dev-backend   # 启动后端（:8080）
make dev-frontend  # 启动前端（:5173）
make check         # 全部检查：格式、lint、类型、架构测试、单元与集成测试
make fmt           # 自动格式化
make api           # 导出 openapi.json 并生成前端接口代码
make db-reset      # 重置本地数据库
```

后端编译依赖 Docker（jOOQ 代码生成和集成测试使用 Testcontainers）。
