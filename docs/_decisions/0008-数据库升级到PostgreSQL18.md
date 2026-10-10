# 0008 数据库升级到 PostgreSQL 18

- 日期：2026-10-10
- 状态：已采纳

## 背景

需求文档指定 PostgreSQL 16。jOOQ 3.21 开源版的 `POSTGRES` 方言面向最新的 PostgreSQL 版本，代码生成时对 16 给出警告：数据库版本低于方言支持的版本。开源版不提供面向旧版本的方言，jOOQ 可能生成 16 不支持的 SQL。

## 备选方案

1. **保持 PostgreSQL 16**：符合需求文档；需要忍受版本警告，并在遇到方言差异时逐个规避。
2. **升级到 PostgreSQL 18**（`pgvector/pgvector:pg18`）：与 jOOQ 方言一致；pgvector 在 18 上同样可用。
3. **购买 jOOQ 商业版以获得旧版本方言**：为个人项目付费不划算。

## 决定

采用方案 2。本地依赖、jOOQ 代码生成、集成测试统一使用 `pgvector/pgvector:pg18` 镜像。

## 代价

- PostgreSQL 18 镜像的数据目录按主版本分子目录（`/var/lib/postgresql/18/docker`），compose 卷挂载到 `/var/lib/postgresql`。已有的 16 数据卷不兼容，需要 `make db-reset`。
- 部署时云数据库需要提供 18 版本和 pgvector 扩展。
- 需求文档的技术栈表需同步修改。
