.DEFAULT_GOAL := help
MVN := cd backend && ./mvnw -B

.PHONY: help setup deps deps-down dev-backend dev-frontend check check-backend check-frontend fmt api db-reset

help: ## 列出所有命令
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  make %-15s %s\n", $$1, $$2}'

setup: ## 安装依赖与 Git 钩子，生成 .env
	@test -f .env || cp .env.example .env
	pnpm install

deps: ## 启动 Docker 依赖
	docker compose up -d --wait

deps-down: ## 停止 Docker 依赖
	docker compose down

dev-backend: ## 启动后端
	$(MVN) spring-boot:run

dev-frontend: ## 启动前端
	pnpm --filter frontend dev

check: check-backend check-frontend ## 全部检查：格式、lint、类型、架构测试、单元与集成测试

check-backend:
	$(MVN) verify

check-frontend:
	pnpm --filter frontend check

fmt: ## 自动格式化
	$(MVN) -q spotless:apply
	pnpm --filter frontend exec prettier --write .
	pnpm --filter frontend exec eslint --fix .

api: ## 导出 openapi.json 并生成前端接口代码
	$(MVN) test -Dtest=OpenApiContractTest -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true
	pnpm --filter frontend gen:api

db-reset: ## 删除本地数据库数据并重新启动（后端启动时 Flyway 重新迁移）
	docker compose rm -sfv postgres && docker volume rm -f wenqu_pgdata
	docker compose up -d --wait postgres
