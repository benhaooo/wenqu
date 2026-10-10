# Git 规范

## 分支

- 主分支 `main`，始终可构建、检查全部通过。
- 每个模块或任务一个分支：`feat/<模块>-<简述>`、`fix/<简述>`、`docs/<简述>`、`chore/<简述>`。
- 合并到 `main` 前 CI 必须通过。

## 提交信息

遵循 [Conventional Commits](https://www.conventionalcommits.org/)，由 commitlint 在 `commit-msg` 钩子中检查：

```
<type>(<scope>): <说明>

<正文：做了什么、为什么>
```

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 不改变行为的重构 |
| `perf` | 性能优化 |
| `test` | 测试 |
| `docs` | 文档 |
| `build` | 构建、依赖 |
| `ci` | CI 配置 |
| `chore` | 其他杂项 |

scope 可选，取值：`auth`、`workspace`、`document`、`ingestion`、`retrieval`、`chat`、`usage`、`infra`、`common`、`backend`、`frontend`、`docs`、`deps`。

- 说明使用中文或英文均可，一句话说清做了什么，不以句号结尾。
- 一次提交包含一件事的完整改动：代码、测试、文档一起提交。

## 提交前钩子

由 lefthook 管理，`make setup` 时安装：

| 钩子 | 检查 |
|---|---|
| `pre-commit` | 暂存的 Java 文件：Spotless 格式检查；暂存的前端文件：Prettier、ESLint |
| `commit-msg` | commitlint |

不使用 `--no-verify` 跳过钩子。

## 不进仓库的内容

`.env`、构建产物、生成代码（jOOQ、前端接口代码）、IDE 配置、本地数据卷。
