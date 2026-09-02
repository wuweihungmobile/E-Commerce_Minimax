# =============================================
# E-Commerce Platform - Makefile
# AISDLC v0.09 - 統一管理所有本機驗證命令
# 使用方式：make <command>
# 範例：make validate-all
# =============================================

# ---- 變數設定 ----
SHELL := /bin/bash
.DEFAULT_GOAL := help
.PHONY: help

# ---- 顏色輸出 ----
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

# =============================================
# 說明
# =============================================
help: ## 顯示所有可用命令
	@echo "$(GREEN)E-Commerce Platform - 本機驗證工具$(NC)"
	@echo "$(GREEN)================================$(NC)"
	@echo ""
	@echo "$(YELLOW)🚀 快速開始：$(NC)"
	@echo "  make setup          # 一次性設定本機環境"
	@echo "  make validate-all   # 完整驗證（CI 模擬）"
	@echo ""
	@echo "$(YELLOW)📋 個別命令：$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

# =============================================
# 環境設定
# =============================================
setup: ## 一次性設定本機環境（安裝 act + 安裝 hooks + 複製 .env）
	@echo "$(YELLOW)🔧 設定本機環境...$(NC)"
	@command -v act >/dev/null 2>&1 || (echo "📦 安裝 act..." && brew install act)
	@if [ ! -f .env ]; then cp .env.example .env && echo "✅ 建立 .env"; fi
	@./scripts/install-hooks.sh
	@echo "$(GREEN)✅ 設定完成！$(NC)"

# =============================================
# Docker Compose
# =============================================
up: ## 啟動所有服務（含 dev override，不含 MinIO）
	@echo "$(YELLOW)🚀 啟動 Docker Compose...$(NC)"
	docker compose up -d
	@echo "$(GREEN)✅ 服務已啟動$(NC)"
	@echo "Frontend: http://localhost:3000"
	@echo "Backend:  http://localhost:8080"
	@echo "Postgres: localhost:5432"
	@echo "Redis:    localhost:6379"
	@echo "$(YELLOW)ℹ️  需媒體上傳（MinIO）請改用：make up-storage$(NC)"

up-storage: ## 啟動所有服務 + MinIO 物件儲存（媒體上傳功能才需要）
	@echo "$(YELLOW)🚀 啟動 Docker Compose（含 MinIO）...$(NC)"
	docker compose --profile storage up -d
	@echo "$(GREEN)✅ 服務已啟動（含 MinIO）$(NC)"
	@echo "Frontend:      http://localhost:3000"
	@echo "Backend:       http://localhost:8080"
	@echo "MinIO API:     http://localhost:9000"
	@echo "MinIO Console: http://localhost:9001"

down: ## 停止所有服務
	@echo "$(YELLOW)🛑 停止 Docker Compose...$(NC)"
	docker compose down
	@echo "$(GREEN)✅ 已停止$(NC)"

down-clean: ## 停止所有服務並刪除 volumes
	@echo "$(YELLOW)🛑 停止並清理 volumes...$(NC)"
	docker compose down -v
	@echo "$(GREEN)✅ 已清理$(NC)"

up-ci: ## 啟動 CI 模擬環境（postgres + redis + backend + frontend）
	@echo "$(YELLOW)🚀 啟動 CI 模擬環境...$(NC)"
	docker compose -f docker-compose.yml -f docker-compose.test.yml up -d
	@echo "$(GREEN)✅ CI 模擬環境已啟動$(NC)"

down-ci: ## 停止 CI 模擬環境
	@echo "$(YELLOW)🛑 停止 CI 模擬環境...$(NC)"
	docker compose -f docker-compose.yml -f docker-compose.test.yml down -v
	@echo "$(GREEN)✅ 已停止$(NC)"

test-db-up: ## 啟動「整合測試 / pre-commit 核心測試」所需 DB（postgres:5432 + redis:6379，對齊 integration-test profile）
	@echo "$(YELLOW)🗄️  啟動測試 DB（對齊 application-integration-test.yml：postgres :5432、redis :6379）...$(NC)"
	@docker rm -f nk-test-pg nk-test-redis >/dev/null 2>&1 || true
	@docker run -d --rm --name nk-test-pg \
	  -e POSTGRES_USER=koala -e POSTGRES_PASSWORD=koala5 -e POSTGRES_DB=nextkeytest \
	  -p 5432:5432 postgres:18-alpine >/dev/null
	@docker run -d --rm --name nk-test-redis \
	  -p 6379:6379 redis:7-alpine redis-server --requirepass redis-dev-password >/dev/null
	@printf "$(YELLOW)   等待 postgres 就緒...$(NC)\n"
	@for i in $$(seq 1 30); do \
	  docker exec nk-test-pg pg_isready -U koala -d nextkeytest >/dev/null 2>&1 && break; \
	  sleep 1; \
	  [ $$i -eq 30 ] && { echo "$(RED)❌ postgres 30s 未就緒$(NC)"; exit 1; }; \
	done
	@echo "$(GREEN)✅ 測試 DB 就緒（nk-test-pg :5432 / nk-test-redis :6379）$(NC)"
	@echo "   用途：cd backend && mvn verify -Pintegration-test ；或 git commit（pre-commit 核心測試含 @ActiveProfiles integration-test 者需真實 postgres）"
	@echo "   $(YELLOW)注意$(NC)：佔用 :5432/:6379，若本機有 dev DB 在跑請先停。完成後執行 make test-db-down 清理。"

test-db-down: ## 停止測試 DB（test-db-up 啟動的 postgres+redis）
	@echo "$(YELLOW)🛑 停止測試 DB...$(NC)"
	@docker rm -f nk-test-pg nk-test-redis >/dev/null 2>&1 || true
	@echo "$(GREEN)✅ 測試 DB 已停止$(NC)"

up-mock: ## 啟動 Mock API 服務（Mockoon）
	@echo "$(YELLOW)🚀 啟動 Mock 服務（僅 API Mock）...$(NC)"
	docker compose -f docker-compose.yml -f docker-compose.mock.yml up -d
	@echo "$(GREEN)✅ Mock API 服務已啟動$(NC)"
	@echo "Mock API: http://localhost:3001"

down-mock: ## 停止 Mock 服務
	@echo "$(YELLOW)🛑 停止 Mock 服務...$(NC)"
	docker compose -f docker-compose.yml -f docker-compose.mock.yml down
	@echo "$(GREEN)✅ 已停止$(NC)"

test-mock: ## 測試 Mock API 所有端點
	@echo "$(YELLOW)🧪 測試 Mock API...$(NC)"
	@echo "  - 健康檢查"
	@curl -fsS http://localhost:3001/health || (echo "❌ Mock API 沒回應" && exit 1)
	@echo "  - 信用卡付款"
	@curl -fsS -X POST http://localhost:3001/api/payment/credit-card \
		-H "Content-Type: application/json" \
		-d '{"amount": 1000}' >/dev/null && echo "    ✅ OK" || echo "    ❌ FAIL"
	@echo "  - 物流查詢"
	@curl -fsS http://localhost:3001/api/logistics/track/TEST-001 >/dev/null && echo "    ✅ OK" || echo "    ❌ FAIL"
	@echo "  - 簡訊發送"
	@curl -fsS -X POST http://localhost:3001/api/sms/send \
		-H "Content-Type: application/json" \
		-d '{"phone": "+886912345678"}' >/dev/null && echo "    ✅ OK" || echo "    ❌ FAIL"
	@echo "  - Google OAuth"
	@curl -fsS http://localhost:3001/api/auth/google/callback >/dev/null && echo "    ✅ OK" || echo "    ❌ FAIL"
	@echo "$(GREEN)✅ Mock API 測試完成$(NC)"

logs: ## 查看所有服務 logs
	docker compose logs -f

logs-backend: ## 查看 backend logs
	docker compose logs -f backend

logs-frontend: ## 查看 frontend logs
	docker compose logs -f frontend

# =============================================
# 本機驗證
# =============================================
validate-all: ## 完整 CI 模擬驗證（act） - 本機最完整檢查
	@echo "$(YELLOW)🧪 執行 act CI 模擬驗證...$(NC)"
	@cd /Users/wuweihong/Cursor_Project/E-Commerce_Minimax && \
	if act -W .github/workflows/act-compat.yml; then \
		CURRENT_COMMIT=$$(git rev-parse HEAD); \
		VALIDATED_TIME=$$(date '+%Y-%m-%d %H:%M:%S'); \
		TREE_HASH=$$(git ls-tree -r HEAD 2>/dev/null | sha256sum | cut -d' ' -f1); \
		if [ -z "$$TREE_HASH" ]; then TREE_HASH="empty"; fi; \
		CI_DATA_DIR=".ci-validation-data"; \
		CI_RECORD="$$CI_DATA_DIR/commits"; \
		mkdir -p "$$CI_DATA_DIR"; \
		sed -i.bak "/^$$CURRENT_COMMIT|/d" "$$CI_RECORD" 2>/dev/null || \
		  (grep -v "^$$CURRENT_COMMIT|" "$$CI_RECORD" > "$$CI_RECORD.tmp" 2>/dev/null && mv "$$CI_RECORD.tmp" "$$CI_RECORD" 2>/dev/null) || true; \
		rm -f "$$CI_RECORD.bak" "$$CI_RECORD.tmp"; \
		echo "$$CURRENT_COMMIT|$$VALIDATED_TIME|$$TREE_HASH|SUCCESS" >> "$$CI_RECORD"; \
		echo "$(GREEN)✅ 完整驗證通過！已寫入驗證記錄 $$CI_RECORD$(NC)"; \
	else \
		echo "$(RED)❌ 驗證失敗，請修復問題後重新執行$(NC)"; \
		exit 1; \
	fi

validate-fast: ## 快速 CI 模擬（僅 backend-unit + frontend，不含 backend-integration）
	@echo "$(YELLOW)⚡ 快速 CI 模擬...$(NC)"
	@cd /Users/wuweihong/Cursor_Project/E-Commerce_Minimax && \
	if act -W .github/workflows/act-compat.yml -j backend-unit && \
	   act -W .github/workflows/act-compat.yml -j frontend; then \
		CURRENT_COMMIT=$$(git rev-parse HEAD); \
		VALIDATED_TIME=$$(date '+%Y-%m-%d %H:%M:%S'); \
		TREE_HASH=$$(git ls-tree -r HEAD 2>/dev/null | sha256sum | cut -d' ' -f1); \
		if [ -z "$$TREE_HASH" ]; then TREE_HASH="empty"; fi; \
		CI_DATA_DIR=".ci-validation-data"; \
		CI_RECORD="$$CI_DATA_DIR/commits"; \
		mkdir -p "$$CI_DATA_DIR"; \
		sed -i.bak "/^$$CURRENT_COMMIT|/d" "$$CI_RECORD" 2>/dev/null || \
		  (grep -v "^$$CURRENT_COMMIT|" "$$CI_RECORD" > "$$CI_RECORD.tmp" 2>/dev/null && mv "$$CI_RECORD.tmp" "$$CI_RECORD" 2>/dev/null) || true; \
		rm -f "$$CI_RECORD.bak" "$$CI_RECORD.tmp"; \
		echo "$$CURRENT_COMMIT|$$VALIDATED_TIME|$$TREE_HASH|SUCCESS" >> "$$CI_RECORD"; \
		echo "$(GREEN)✅ 快速驗證通過！已寫入驗證記錄 $$CI_RECORD$(NC)"; \
	else \
		echo "$(RED)❌ 驗證失敗，請修復問題後重新執行$(NC)"; \
		exit 1; \
	fi

validate-backend: ## 僅驗證 backend（act，unit + integration 兩個 job）
	@echo "$(YELLOW)🔨 驗證 backend...$(NC)"
	act -W .github/workflows/act-compat.yml -j backend-unit
	act -W .github/workflows/act-compat.yml -j backend-integration

validate-frontend: ## 僅驗證 frontend（act）
	@echo "$(YELLOW)🎨 驗證 frontend...$(NC)"
	act -W .github/workflows/act-compat.yml -j frontend

validate-list: ## 列出 act 將執行的所有 jobs
	act -W .github/workflows/act-compat.yml -l

validate-schema: ## schema 漂移守門：以 ddl-auto=validate + Flyway 對乾淨 DB 啟動 backend，攔截 entity↔migration 漂移
	@echo "$(YELLOW)🔍 schema 漂移守門關卡（複製 GitHub E2E 啟動條件，本地 act 抓不到的漏洞）...$(NC)"
	@./scripts/validate-schema.sh

validate-schema-doc: ## 文件漂移守門：比對 SRD_Database_Schema.md 的 DDL 與 Flyway 實際 schema
	@echo "$(YELLOW)📘 SRD 資料庫文件漂移守門（migration ↔ 文件；validate-schema 管的是 entity ↔ migration）...$(NC)"
	@./scripts/validate-schema-doc.sh

sync-schema-doc: ## 依 Flyway 實際 schema 重新產生 SRD_Database_Schema.md 的資料表 DDL
	@echo "$(YELLOW)📘 依實際 schema 重新產生 SRD 資料表 DDL...$(NC)"
	@./scripts/validate-schema-doc.sh --write

validate-e2e: ## 本地 E2E 守門：乾淨 DB → Flyway 重建 → 全棧(host JAR+npm start, ddl-auto=validate) → Playwright（複製雲端 e2e job）
	@echo "$(YELLOW)🎭 本地 E2E 守門（複製雲端 e2e job：host 程序 + 乾淨 DB + Playwright）...$(NC)"
	@./scripts/validate-e2e.sh

validate-push: ## pre-push 輕量守門（2026-07-04 起）：backend-unit（act）+ schema 漂移 + frontend 快檢，約 8-12 分鐘；完整 backend-integration/E2E 交給雲端 push 觸發
	@echo "$(YELLOW)🚦 pre-push 輕量守門（完整整合測試已改由雲端 push 觸發，見 act-compat.yml on: push 說明）...$(NC)"
	@$(MAKE) test-db-down
	@$(MAKE) validate-fast
	@$(MAKE) validate-schema
	@echo "$(GREEN)✅ 輕量守門通過！push 後雲端會自動執行完整 backend-integration + frontend job（act-compat.yml）$(NC)"

validate-release: ## 完整測試程序（雲端等價，供手動全量確認用）：自動 test-db-down → act（backend-unit+backend-integration+frontend）+ schema 漂移 + E2E；寫 FULL 記錄
	@echo "$(YELLOW)🚦 完整測試程序（等價雲端 ci.yml；pre-push 守門與部署前共用）...$(NC)"
	@echo "$(YELLOW)   先自動停用 test DB（釋放 :5432/:6379 給 act 服務容器，制度化消除 AI-2301 port 衝突）...$(NC)"
	@$(MAKE) test-db-down
	@$(MAKE) validate-all
	@$(MAKE) validate-schema
	@$(MAKE) validate-e2e
	@CURRENT_COMMIT=$$(git rev-parse HEAD); \
	  VALIDATED_TIME=$$(date '+%Y-%m-%d %H:%M:%S'); \
	  TREE_HASH=$$(git ls-tree -r HEAD 2>/dev/null | { command -v sha256sum >/dev/null 2>&1 && sha256sum || shasum -a 256; } | cut -d' ' -f1); \
	  [ -z "$$TREE_HASH" ] && TREE_HASH="empty"; \
	  mkdir -p .ci-validation-data; \
	  grep -v "^$$CURRENT_COMMIT|" .ci-validation-data/commits > .ci-validation-data/commits.tmp 2>/dev/null && mv .ci-validation-data/commits.tmp .ci-validation-data/commits || true; \
	  echo "$$CURRENT_COMMIT|$$VALIDATED_TIME|$$TREE_HASH|SUCCESS|FULL" >> .ci-validation-data/commits; \
	  echo "$(GREEN)✅ 完整測試程序全數通過（act + schema + E2E）；已寫 FULL 記錄，30 分內 git push 直接放行$(NC)"

# =============================================
# 本機快速檢查（無需 act）
# =============================================
check-backend: ## 本機快速檢查 backend（checkstyle + compile + test）
	@echo "$(YELLOW)🔨 Backend 快速檢查...$(NC)"
	cd backend && mvn checkstyle:check compile -DskipTests
	cd backend && mvn test -Dtest="com.nextkey.ecommerce.core.**" -DexcludedGroups=slow -Dspring.profiles.active=test
	@echo "$(GREEN)✅ Backend 檢查通過（-DexcludedGroups=slow：排除需 DB 的 @SpringBootTest，見 AI-2302）$(NC)"

check-frontend: ## 本機快速檢查 frontend（lint + type-check + build）
	@echo "$(YELLOW)🎨 Frontend 快速檢查...$(NC)"
	cd frontend && npm run lint
	cd frontend && npm run type-check
	@echo "$(GREEN)✅ Frontend 檢查通過$(NC)"

check-secret: ## 掃描潛在 secrets（使用 gitleaks）
	@command -v gitleaks >/dev/null 2>&1 || (echo "❌ gitleaks 未安裝，請執行：brew install gitleaks" && exit 1)
	gitleaks detect --source . --config .gitleaks.toml --no-banner

# =============================================
# Pre-commit Hooks
# =============================================
hooks-install: ## 安裝所有 Git Hooks
	./scripts/install-hooks.sh

hooks-test: ## 測試 pre-commit hook（不實際 commit）
	@echo "$(YELLOW)🧪 測試 pre-commit hook...$(NC)"
	@git diff --cached --name-only | head -5 || echo "  (no staged files)"

# =============================================
# Docker Image 清理
# =============================================
docker-clean: ## 清理未使用的 Docker 資源
	@echo "$(YELLOW)🧹 清理 Docker 資源...$(NC)"
	docker system prune -f
	@echo "$(GREEN)✅ 已清理$(NC)"

docker-clean-all: ## 完全清理 Docker（images + volumes + networks）
	@echo "$(YELLOW)🧹 完全清理 Docker...$(NC)"
	docker system prune -a --volumes -f
	@echo "$(GREEN)✅ 已完全清理$(NC)"

# =============================================
# 開發輔助
# =============================================
ps: ## 列出所有執行中的容器
	docker compose ps

shell-backend: ## 進入 backend 容器 shell
	docker compose exec backend sh

shell-postgres: ## 進入 postgres 容器 shell
	docker compose exec postgres sh

shell-redis: ## 進入 redis 容器 shell
	docker compose exec redis sh

# =============================================
# 預設目標
# =============================================
.DEFAULT:
	@echo "$(RED)❌ 未知命令：$@$(NC)"
	@echo "執行 'make help' 查看所有可用命令"
