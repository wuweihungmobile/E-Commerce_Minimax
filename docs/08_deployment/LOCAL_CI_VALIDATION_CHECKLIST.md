# 本機 CI 工具鏈驗證清單
# Local CI Toolchain Validation Checklist
# AISDLC v0.09 - 開發專注版
# 用途：完整驗證「四道關卡」都能在本機運作，push GitHub 前必查

> **🔴 核心原則**：所有勾選必須為 ✅，才能 push 任何 commit！

---

## 📋 驗證流程

### Phase 0: 環境準備（一次性）

- [ ] **0.1** Docker Desktop 已啟動
  ```bash
  docker ps  # 應顯示容器列表（可能為空）
  ```
- [ ] **0.2** act 已安裝
  ```bash
  act --version  # 應顯示版本（≥ 0.2.0）
  ```
- [ ] **0.3** .env 已建立
  ```bash
  test -f .env && echo "OK" || cp .env.example .env
  ```
- [ ] **0.4** Hooks 已安裝
  ```bash
  ./scripts/install-hooks.sh
  ls .git/hooks/pre-commit .git/hooks/pre-push  # 兩個檔案都應存在
  ```
- [ ] **0.5** Mock 目錄已就緒
  ```bash
  test -f mocks/mockoon-data.json && test -f mocks/README.md && echo "OK"
  ```
- [ ] **0.6** Local LLM 模型已下載（可選）
  ```bash
  test -f ~/models/qwen2.5-1.5b-instruct-q4_k_m.gguf && echo "OK" || make download-llm-model
  ```

### Phase 1: Docker Compose（第一道關卡：迷你正式環境）

- [ ] **1.1** 所有 compose 設定檔語法正確
  ```bash
  for f in docker-compose.yml docker-compose.override.yml docker-compose.test.yml docker-compose.mock.yml; do
    docker compose -f $f config --quiet && echo "✅ $f"
  done
  ```
- [ ] **1.2** 主環境啟動成功（postgres + redis + minio）
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.override.yml up -d postgres redis minio
  docker compose ps  # 全部應為 Up (healthy) 或 Up
  ```
- [ ] **1.3** PostgreSQL 連線正常
  ```bash
  PGPASSWORD=koala5 psql -h localhost -U koala -d nextkeytest -c "SELECT version();"
  ```
- [ ] **1.4** Redis 連線正常
  ```bash
  docker exec ecommerce-redis redis-cli -a redis-dev-password --no-auth-warning ping
  # 應回應 PONG
  ```
- [ ] **1.5** Mock 服務啟動成功
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.mock.yml up -d mock-server
  curl -sS -o /dev/null -w "HTTP %{http_code}\n" -X POST http://localhost:3001/api/payment/credit-card -H "Content-Type: application/json" -d '{"amount":1000}'
  # 應回應 200
  ```
- [ ] **1.6** Mock API 所有 4 個端點測試通過
  ```bash
  make test-mock  # 會測試所有 4 個端點
  ```
- [ ] **1.7** Docker 容器日誌無錯誤
  ```bash
  docker compose logs --tail 50 | grep -iE "error|exception" | grep -v "INFO\|DEBUG" | head
  # 應無嚴重錯誤
  ```

### Phase 2: act CI 模擬（第二道關卡：本機 GitHub Actions）

- [ ] **2.1** act 可列出 jobs
  ```bash
  act -W .github/workflows/act-compat.yml -l
  # 應顯示 backend + frontend
  ```
- [ ] **2.2** Backend job 完整跑通
  ```bash
  act -W .github/workflows/act-compat.yml -j backend
  # 應 Success - Main Checkstyle
  # 應 Success - Main Verify Service Connections
  # 應 Success - Main Compile Backend
  # 應 Success - Main Run Unit Tests
  # 應 Success - Main Run Integration Tests
  # 應 Success - Main Package Backend
  ```
- [ ] **2.3** Frontend job 完整跑通
  ```bash
  act -W .github/workflows/act-compat.yml -j frontend
  # 應 Success - Main ESLint
  # 應 Success - Main Type Check
  # 應 Success - Main Build Frontend
  ```
- [ ] **2.4** 完整 CI 模擬（推薦）
  ```bash
  make validate-all
  ```

### Phase 3: Pre-commit Hooks（第三道關卡：自動攔截）

- [ ] **3.1** Root pre-commit 觸發 backend 檢查
  ```bash
  touch backend/Test.java && git add backend/Test.java
  SKIP_ACT_PROMPT=1 scripts/hooks/pre-commit
  # 應自動跑 Checkstyle + Compile + Test
  git reset HEAD backend/Test.java && rm backend/Test.java
  ```
- [ ] **3.2** Root pre-commit 觸發 frontend 檢查
  ```bash
  cd frontend && touch Test.tsx && git add Test.tsx
  cd .. && SKIP_ACT_PROMPT=1 scripts/hooks/pre-commit
  # 應自動跑 ESLint + TSC
  git reset HEAD frontend/Test.tsx && rm frontend/Test.tsx
  ```
- [ ] **3.3** Backend hook 攔截 Checkstyle 違規
  ```bash
  # 故意建立有違規的檔案
  touch backend/src/main/java/Bad.java
  git add backend/src/main/java/Bad.java
  SKIP_ACT_PROMPT=1 scripts/hooks/pre-commit
  # 應回傳非 0 exit code
  git reset HEAD backend/src/main/java/Bad.java && rm backend/src/main/java/Bad.java
  ```
- [ ] **3.4** Pre-push hook 在 non-TTY 環境不卡住
  ```bash
  SKIP_ACT_PROMPT=1 git push --dry-run  # 不應卡在 read 詢問
  ```
- [ ] **3.5** Pre-push hook 跑 act 完整模擬
  ```bash
  AUTO_RUN_ACT=1 git push --dry-run
  ```

### Phase 4: Mock 服務（第四道關卡：API 與 AI 模擬）

- [ ] **4.1** Mockoon API Mock 啟動
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.mock.yml up -d mock-server
  docker logs ecommerce-mock-server | grep "Server started"
  ```
- [ ] **4.2** Mock 端點 1: 信用卡付款
  ```bash
  curl -sS http://localhost:3001/api/payment/credit-card -X POST \
    -H "Content-Type: application/json" -d '{"amount":1000}' | jq .
  # 應有 status: success, transactionId, amount: 1000
  ```
- [ ] **4.3** Mock 端點 2: 物流查詢
  ```bash
  curl -sS http://localhost:3001/api/logistics/track/TW-001 | jq .
  # 應有 trackingId, status, location, history
  ```
- [ ] **4.4** Mock 端點 3: 簡訊發送
  ```bash
  curl -sS http://localhost:3001/api/sms/send -X POST \
    -H "Content-Type: application/json" -d '{"phone":"+886912345678"}' | jq .
  # 應有 success: true, messageId
  ```
- [ ] **4.5** Mock 端點 4: Google OAuth
  ```bash
  curl -sS http://localhost:3001/api/auth/google/callback | jq .
  # 應有 access_token, token_type, expires_in
  ```
- [ ] **4.6** Local LLM 啟動（需模型已下載）
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.mock.yml --profile with-llm up -d local-llm
  curl -sS -X POST http://localhost:8081/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"messages":[{"role":"user","content":"你好"}]}' | jq .
  # 應有 choices[0].message.content
  ```
- [ ] **4.7** LLM 模型下載腳本冪等性
  ```bash
  bash scripts/download-llm-model.sh
  # 模型已存在時應自動跳過下載
  ```

### Phase 5: 整合驗證（最終把關）

- [ ] **5.1** 完整 clean 流程
  ```bash
  # 清理所有容器
  docker compose down -v

  # 完整重啟
  make up

  # 跑全部驗證
  make validate-all
  ```
- [ ] **5.2** push 到 GitHub 前最後確認
  ```bash
  git add .
  git status  # 確認 staged 內容正確
  git commit -m "..."  # pre-commit 應自動跑
  git push origin develop  # pre-push 應自動跑
  ```
- [ ] **5.3** GitHub Actions 通過
  ```bash
  gh run list --limit 5
  gh run watch  # 監看最新 run 狀態
  ```

---

## 🚨 常見失敗模式

| 症狀 | 根因 | 解法 |
|------|------|------|
| `mvn: command not found` | catthehacker image 沒 mvn | act-compat.yml 應有 `Install Build Tools` step |
| `psql: command not found` | catthehacker image 沒 psql | 同上，加上 `postgresql-client` |
| `redis-cli: command not found` | catthehacker image 沒 redis-cli | 同上，加上 `redis-tools` |
| Redis 容器一直 unhealthy | `--requirepass` flag 拼成字串 | 必須用 array 形式傳遞 |
| PostgreSQL 18 啟動失敗 | mount 路徑錯誤 | 改用 `/var/lib/postgresql` 而非 `/var/lib/postgresql/data` |
| Mockoon 卡在互動詢問 | 舊版 JSON 格式 | 啟動加 `-r` 自動修復 |
| Backend 啟動失敗 `redisTemplate` | Bean 衝突 | 需重構 RedisConfig/RedisStreamConfig |
| Pre-commit 沒觸發 | hook 沒安裝 | 執行 `./scripts/install-hooks.sh` |
| Pre-push 卡在 read 詢問 | non-TTY 環境 | 用 `AUTO_RUN_ACT=1` 或 `SKIP_ACT_PROMPT=1` |
| E2E 測試在 unit test 階段失敗 | 沒排除 E2ETest | 用 `-Dtest='*Test,!E2ETest'` |

---

## 📊 驗證狀態追蹤

| 階段 | 最後驗證日期 | 通過/失敗 | 備註 |
|------|-------------|----------|------|
| Phase 0: 環境準備 | 2026-06-11 | ✅ | Docker + act + hooks + .env 都就緒 |
| Phase 1: Docker Compose | 2026-06-11 | ✅ | postgres:18 + redis:7 + mock-server 正常 |
| Phase 2: act CI 模擬 | 2026-06-11 | ✅ | backend unit + integration + frontend lint+tsc+build |
| Phase 3: Pre-commit Hooks | 2026-06-11 | ✅ | 攔截 Checkstyle 違規 |
| Phase 4: Mock 服務 | 2026-06-11 | ✅ | 4 個端點 + LLM 腳本冪等性 |
| Phase 5: 整合驗證 | 2026-06-11 | ⏳ | 待 push 後驗證 GitHub Actions |

---

## 📝 維護記錄

| 日期 | 版本 | 變更 | 作者 |
|------|------|------|------|
| 2026-06-11 | 1.0 | 初版建立 | Claude Code |

---

> **🔴 記住：所有 ✅ 後才能 push！每一次！**
