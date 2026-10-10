# Git 提交规范

## 提交信息格式

提交信息遵循 Conventional Commits：`<类型>(<范围>): <描述>`，例如 `feat(auth): 支持刷新令牌轮换`。描述使用中文，不超过 50 个字。

常用类型：feat 新功能，fix 修复缺陷，refactor 重构，docs 文档，test 测试，chore 构建与工具。提交前的钩子会用 commitlint 检查格式，不符合规范的提交会被拒绝。

## 分支

从 main 拉出功能分支，命名为 `feat/简短描述` 或 `fix/简短描述`。一个分支只做一件事，完成后通过 Pull Request 合并，合并方式使用 merge commit。

## 禁止事项

禁止直接向 main 分支推送，禁止 force push 到共享分支。
