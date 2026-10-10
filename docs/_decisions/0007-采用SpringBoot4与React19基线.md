# 0007 采用 Spring Boot 4 与 React 19 基线

- 日期：2026-10-10
- 状态：已采纳

## 背景

需求文档的技术栈写的是 Spring Boot 3 和 React 18。项目于 2026 年 10 月从零开始：

- Spring AI 2.0 依赖 Spring Boot 4.1；Spring AI 1.1 依赖 Spring Boot 3.5，3.5 是 3.x 的最后一个版本线。
- React 19 已发布多个小版本；shadcn/ui 当前版本围绕 React 19 和 Tailwind CSS 4 设计；Tiptap 3 同时兼容 React 18 和 19。

## 备选方案

1. **严格按需求文档**：Spring Boot 3.5 + Spring AI 1.1，React 18。符合原文，但起步就在旧版本线上，上线前大概率要做一次大版本升级。
2. **升级到当前主版本**：Spring Boot 4.1 + Spring AI 2.0 + springdoc 3 + Testcontainers 2，React 19 + Tailwind 4。

## 决定

采用方案 2。

| 项 | 版本 |
|---|---|
| Spring Boot | 4.1 |
| Spring AI | 2.0 |
| jOOQ | 3.21（由 Spring Boot 管理） |
| springdoc-openapi | 3.1 |
| Testcontainers | 2.0 |
| React | 19 |
| Tailwind CSS | 4 |
| TypeScript | 6.0（typescript-eslint 尚不支持 7.x） |

## 代价

- Spring Boot 4 的模块化 starter（如 `spring-boot-starter-webmvc`）、Jackson 3 等变化使部分网上资料不再适用。
- 新版本线的第三方库兼容性需要逐个确认，例如 Testcontainers 官方的 jOOQ 代码生成插件已停止维护，改用 gmavenplus + Flyway 插件 + jOOQ 插件组合。
- 需求文档的技术栈表需同步修改。
