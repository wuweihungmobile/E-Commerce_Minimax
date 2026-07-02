# 本地 CI 驗證流程完整指南
# AISDLC v0.09 - 開發專注版
# 目標：所有 Push 到 GitHub 前，都應在本機完成完整 CI 模擬

> **🔴 核心原則：Push 之前，必須先在本機通過所有 CI 檢查！**

> **⚠️ 注意：`make validate-all`（act）走 `ddl-auto=update`，抓不到 entity↔migration 的 schema 漂移。**
> 修改 `@Entity` 欄位或 Flyway migration 後，請另外執行 **`make validate-schema`**（schema 漂移守門關卡）。
> 詳見 [SCHEMA_DRIFT_GATE.md](./SCHEMA_DRIFT_GATE.md)。

---

## 🔴 本地優先策略（2026-06-30 更新 — 取代雲端日常 CI）

### 背景
本 repo 為 **private**，GitHub Actions 有計費額度；且帳號層級帳單反覆封鎖，導致雲端 CI 無法穩定使用。
策略改為 **「本地守門為主、雲端僅手動觸發」**：日常驗證完全在本地完成，不再為每次 push 燒雲端 Actions 額度。

### 變更一：三個 workflow 改為「僅手動觸發」
`ci.yml`、`act-compat.yml`、`technical-debt-review.yml` 的 `on:` 全部改為 **只剩 `workflow_dispatch`**
（移除 `push` / `pull_request` / `schedule` 自動觸發）。→ **push 不再觸發任何雲端 run**。

> 還原方式：各檔 `on:` 區塊內把被註解的 `push:` / `pull_request:` / `schedule:` 取消註解即可。
> 本地 `act` 不受影響——所有 `make validate-*` 與 pre-push hook 都以 `act -W <指定檔>` 執行，會無視 `on:` 過濾（已實測）。

### 變更二：本地分層守門（2026-06-30 pre-push v5 — 上 GIT = 完整測試程序）

> **使用者要求「批次 push 可以，但上 GIT 必須做完整測試程序」**。故 pre-push = 完整測試程序 `make validate-release`（act backend+frontend + schema 漂移 + e2e，等價雲端 ci.yml，~30-45 分）。
> 因為 push 已改為**低頻批次**（commit 勤、push 少），完整成本只在檢查點付一次、整體等待反而比「每次 push 都跑」更低。
>
> （v4 曾把整合測試+e2e 移出 pre-push 以提速到 ~5-8 分；v5 依使用者要求改回「上 GIT 必跑完整」，但靠降頻 + FULL 記錄快取避免重複等待。）

| 層級 | 觸發 | 內容 | 耗時 |
|------|------|------|------|
| **Tier 0** pre-commit | 每次 commit | lint + compile + 核心測試 + gitleaks | ~2 min |
| **Tier 1** 開發快檢（可選） | 手動，開發中 | `make check-backend` / `make check-frontend`（host 快速 compile/lint/type-check） | ~3-5 min |
| **Tier 2** pre-push＝完整測試程序 | 每次 push（低頻批次） | `make validate-release` = `validate-all`（act 完整含整合）+ `validate-schema`（ddl-auto=validate + Flyway 對乾淨 DB）+ `validate-e2e`（Playwright + 乾淨 DB） | **~30-45 min** |

> 純文件/設定（無 `backend/`、`frontend/` 程式碼變動）→ pre-push 自動略過完整測試，直接放行。

**降低 push 頻率（必要做法）**：完整測試程序有成本，請**commit 勤、push 少**——
- `git commit` 隨時做（pre-commit 快）；只在「一個 US/功能完整、收尾、或要備份」時 `git push`。
- 多個 commit **一次 push**：pre-push 只對最終 working tree 跑一次完整測試，批次 push 攤平等待。
- 想把等待挪到自己方便時：先手動 `make validate-release`（寫 FULL 記錄）→ **30 分鐘內** 對同一 tree `git push` 直接放行（不重跑）。
- FULL 記錄只有 `make validate-release` 寫得出；`check-backend`/`check-frontend` 等 host 快檢**不會**放行 push（避免「只跑快檢就上庫」）。

### 變更三：新增 make target

| 命令 | 用途 |
|------|------|
| `make validate-schema` | schema 漂移守門（已串進 pre-push，改 entity/migration 時自動跑） |
| `make validate-e2e` | 乾淨 DB → Flyway 重建 → 全棧(ddl-auto=validate) → Playwright，複製雲端 e2e job。**預設 strict**（spec 失敗即阻擋；基準 27 passed/5 skip/0 fail）；環境異常臨時放行 `E2E_GATE_STRICT=0` |
| `make validate-release` | **完整測試程序＝ pre-push 守門內容**：**自動 `test-db-down`**（AI-2301）→ `validate-all` + `validate-schema` + `validate-e2e` = 雲端 `ci.yml` 等價。手動先跑一次會寫 FULL 記錄，30 分內對同 tree push 直接放行 |
| `make test-db-up` / `test-db-down` | 啟動/停止「整合測試 + pre-commit 核心測試」所需 DB（postgres:5432 + redis:6379，對齊 integration-test profile）。改 backend `.java/.yml/.sql` 後 commit 前先 `make test-db-up`（pre-commit 的 `@ActiveProfiles("integration-test")` 核心測試需真實 postgres）。**完成後可不必手動 down——`validate-release` 已自動 down（AI-2301）**；平時清理仍可 `make test-db-down` |

### 變更四：test DB ↔ act port 衝突制度化（2026-07-02，AI-2301）

**問題**：`make test-db-up` 啟動的 `nk-test-pg`/`nk-test-redis` 佔用標準 port **5432/6379**（pre-commit 的 `@ActiveProfiles("integration-test")` 核心測試需要）；但 `make validate-release` 內的 `validate-all`（act）其服務容器**也要 host-bind 5432/6379** → `Bind for 0.0.0.0:6379 failed: port is already allocated`。兩者互斥，S40 push 曾因此卡關（依賴人工記憶「validate-release 前手動 test-db-down」）。

**制度化解法（單一自動化點）**：`make validate-release` 在跑 `validate-all` 前**自動執行 `test-db-down`**（冪等 `docker rm -f ... || true`）。因 pre-push hook 也是呼叫 `make validate-release`，故**直接執行**與 **push 守門**兩條路徑一次涵蓋，開發者不再需要記得手動 down。

**開發者心智模型（記住這一條即可）**：

| 情境 | 該做的事 | 為什麼 |
|------|----------|--------|
| 要 `git commit`（含 backend 變動） | 先 `make test-db-up` | pre-commit 核心 `@SpringBootTest`（integration-test profile）需真實 postgres:5432 / redis:6379 |
| 要 `make validate-release` 或 `git push` | **什麼都不用做** | validate-release 已自動 `test-db-down` 釋放 port 給 act |

**不動項**：`validate-schema` / `validate-e2e` 用非標準 port（**55432 / 56379**）本就不衝突；`.github/workflows/act-compat.yml` 的 5432/6379 是鏡像雲端 CI（GitHub runner 服務隔離），**保持不變**。

### 本地 vs 雲端覆蓋對照（哪些已被本地取代）

| 雲端 ci.yml job | 本地對應 | 狀態 |
|-----------------|----------|------|
| backend / frontend | `make validate-all`（act）+ pre-push | ✅ 已覆蓋 |
| e2e（Playwright + schema 重建） | `make validate-e2e` + pre-push 的 `validate-schema` | ✅ 已覆蓋 |
| secret-detection | pre-commit gitleaks / `make check-secret` | ✅ 已覆蓋 |
| dependency-scan / sast / license-compliance | 低頻安全掃描，需要時手動跑 | 🟡 不進每次 push 關卡 |

### 何時 / 如何手動觸發雲端
帳單恢復後若要做雲端驗證（例如 release 雲端 E2E、或安全掃描）：
```bash
gh workflow run "CI/CD Pipeline (v0.09 Enhanced)" --ref main
gh workflow run "技術債檢視 / Technical Debt Review" --ref main
gh run watch   # 觀察執行
```

### 日常正確流程（本地優先，commit 勤、push 少）
```bash
# 1. 開發 → commit（pre-commit 快檢把關）；累積多個 commit，先別急著 push
git commit -m "feat: ..."
git commit -m "feat: ..."
# 2.（可選）想把等待挪到方便時：先手動跑完整測試程序，會寫 FULL 記錄
make validate-release
# 3. 到檢查點才 push：pre-push = 完整測試程序（act + schema + e2e）
#    若步驟 2 剛跑過且 tree 未變 → 30 分內直接放行；否則 pre-push 自動跑一次（~30-45 分）
git push origin main
```

> 🔴 嚴禁 `--no-verify`：會繞過 pre-push 唯一守門員，且雲端已停用自動 CI，等於完全無驗證上庫。

---

## 📋 目錄

1. [問題背景](#問題背景)
2. [整體架構](#整體架構)
3. [快速開始](#快速開始)
4. [四大支柱詳解](#四大支柱詳解)
5. [日常開發流程](#日常開發流程)
6. [故障排除](#故障排除)
7. [參考資源](#參考資源)

---

## 問題背景

### 為什麼需要本地 CI 驗證？

過去 20+ 次 CI 失敗的教訓：

1. **盲目猜測錯誤原因**：看到 CI 失敗 → 猜測是某個設定問題 → 修復 → push → 仍然失敗
2. **環境差異**：本機 (macOS) 與 CI (Ubuntu) 環境不一致，導致「在我電腦可以跑」問題
3. **浪費 GitHub Actions 額度**：每次失敗都會消耗免費額度（每月 2,000 分鐘）
4. **修復週期長**：push → 等 CI 跑 → 看 log → 修復 → 再 push，最快也要 20 分鐘

### 解決方案

打造「**Dev → CI → Prod 一致性**」的完整工具鏈：

| 工具 | 用途 | 效益 |
|------|------|------|
| **Docker Compose** | 本機運行 PostgreSQL + Redis + Backend + Frontend | 本機 = 正式環境 |
| **act** | 在本機模擬 GitHub Actions | 不用 push 就能驗證 CI |
| **Pre-commit / Pre-push Hooks** | Commit/Push 前自動檢查 | 阻擋低級錯誤 |
| **Mock 服務 + Local LLM** | 模擬外部 API 與 AI 模型 | 測試穩定，無外部依賴 |

---

## 整體架構

```
┌─────────────────────────────────────────────────────┐
│  本機開發環境 (macOS / Linux)                          │
├─────────────────────────────────────────────────────┤
│                                                       │
│  ┌──────────────┐    ┌──────────────┐               │
│  │   VSCode     │    │  Terminal    │               │
│  └──────┬───────┘    └──────┬───────┘               │
│         │                    │                       │
│  ┌──────▼────────────────────▼───────────────────┐  │
│  │  Pre-commit Hook (commit 前)                    │  │
│  │  ├─ Checkstyle + Compile + Test                 │  │
│  │  └─ ESLint + TypeScript                         │  │
│  └──────────────────────┬──────────────────────────┘  │
│                         │                              │
│  ┌──────────────────────▼──────────────────────────┐  │
│  │  Pre-push Hook (push 前)                         │  │
│  │  └─ act CI 模擬                                  │  │
│  └──────────────────────┬──────────────────────────┘  │
│                         │                              │
│  ┌──────────────────────▼──────────────────────────┐  │
│  │  Docker Compose                                  │  │
│  │  ├─ PostgreSQL 18    (port 5432)                │  │
│  │  ├─ Redis 7         (port 6379)                │  │
│  │  ├─ Backend Spring  (port 8080)                │  │
│  │  ├─ Frontend Next   (port 3000)                │  │
│  │  ├─ Mock API        (port 3001, optional)      │  │
│  │  └─ Local LLM       (port 8081, optional)      │  │
│  └──────────────────────┬──────────────────────────┘  │
│                         │                              │
└─────────────────────────┼──────────────────────────────┘
                          │  git push (通過所有檢查後)
                          ▼
┌─────────────────────────────────────────────────────┐
│  GitHub Actions (CI/CD)                              │
├─────────────────────────────────────────────────────┤
│  ├─ secret-detection (Gitleaks)                      │
│  ├─ dependency-scan                                  │
│  ├─ license-compliance                               │
│  ├─ backend (build + test)                           │
│  ├─ frontend (lint + build)                          │
│  ├─ sast                                             │
│  └─ e2e (Playwright)                                 │
└─────────────────────────────────────────────────────┘
```

---

## 快速開始

### 一次性設定（5 分鐘）

```bash
# 1. 確認 Docker 已啟動
docker --version

# 2. 確認 Homebrew 已安裝（macOS）
brew --version

# 3. 執行 setup 命令（會自動安裝 act + 建立 .env + 安裝 hooks）
make setup

# 4. 驗證所有工具都已就緒
make help
```

### 日常開發流程（每次 commit/push）

```bash
# 1. 開發完成後，先做本機驗證
make check-backend  # 或 make check-frontend

# 2. 完整 CI 模擬（推薦 - 在 push 前必做）
make validate-all

# 3. 確認通過後才 commit
git add .
git commit -m "feat: 新功能"
# ↑ pre-commit hook 會自動執行檢查

# 4. Push（會自動觸發 pre-push hook）
git push origin develop
# ↑ pre-push hook 會要求執行 act 模擬
```

---

## 四大支柱詳解

### 一、打造「迷你正式環境」(Docker Compose)

**目標**：本機開發環境 = CI 環境 = 正式環境

#### 設定檔結構

| 檔案 | 用途 |
|------|------|
| `docker-compose.yml` | 正式環境配置（生產設定） |
| `docker-compose.override.yml` | 開發環境覆寫（Hot Reload） |
| `docker-compose.test.yml` | CI 模擬環境（一次性 volumes） |
| `docker-compose.mock.yml` | Mock 服務（API + LLM） |
| `.env.example` | 環境變數模板 |

#### 啟動方式

```bash
# 開發環境（含 hot reload）
make up

# CI 模擬環境（與 GitHub Actions 一致）
make up-ci

# Mock 服務（API Mock）
make up-mock

# 完整環境（dev + mock）
docker compose \
  -f docker-compose.yml \
  -f docker-compose.override.yml \
  -f docker-compose.mock.yml \
  up -d
```

#### 關鍵修正

🔴 **Redis 密碼設定修正**

**修正前（CI 失敗）**：
```yaml
redis:
  image: redis:7
  command: redis-server --requirepass redis-dev-password  # ❌ 錯誤
```

**修正後（正確）**：
```yaml
redis:
  image: redis:7-alpine
  env:
    REDIS_PASSWORD: redis-dev-password
  command:
    - "redis-server"
    - "--requirepass"
    - "redis-dev-password"
```

**為什麼這樣改？**
- `--requirepass` 是 redis-server 參數，必須以陣列方式傳遞給 container
- `env` 會自動注入 container 環境變數
- `redis:7-alpine` 與本機環境一致

---

### 二、在地端直接運行 GitHub Actions (act 工具)

**目標**：不 push 就能知道 CI 是否會通過

#### 安裝 act

```bash
# macOS (Homebrew)
brew install act

# Linux
curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash

# 驗證
act --version
```

#### 設定檔

`.actrc`（已建立）：
```ini
-P ubuntu-latest=catthehacker/ubuntu:act-latest
--container-architecture linux/amd64
```

#### 使用方式

```bash
# 列出所有 jobs
make validate-list

# 完整 CI 模擬
make validate-all

# 僅跑 backend
make validate-backend

# Dry run（不實際執行）
act -W .github/workflows/act-compat.yml -n
```

#### act 已知限制

| 步驟 | act 支援 | 解法 |
|------|---------|------|
| `gitleaks-action` | ⚠️ 部分 | 本機用 `gitleaks detect` |
| `dependency-review-action` | ❌ 不支援 | 跳過 |
| `setup-java` cache | ⚠️ 不持久 | 可忽略 |
| `services: postgres/redis` | ✅ 支援 | 與 GitHub 一致 |
| `actions/upload-artifact` | ⚠️ 部分 | 跨 run 不保留 |

#### act 與 GitHub Actions 對照

| 功能 | GitHub Actions | act |
|------|----------------|-----|
| 執行環境 | Ubuntu runner | Docker container |
| Secrets | GitHub Secrets | 本機環境變數 |
| 服務容器 | Docker service | Docker service |
| Matrix | ✅ | ✅ |
| 快取 | ✅ 跨 run | ❌ 僅當次 |
| Artifact 上傳 | ✅ | ⚠️ 部分 |

---

### 三、設立自動攔截點 (Pre-commit Hooks)

**目標**：自動把關，避免低級錯誤 commit 進來

#### 三層 Hook 架構

```
┌─────────────────────────────────────┐
│  Root Pre-commit (scripts/hooks/)   │
│  ├─ 偵測 staged 檔案                 │
│  ├─ 呼叫 backend hook（如有）        │
│  ├─ 呼叫 frontend hook（如有）       │
│  └─ 秘密掃描                        │
└─────────────────────────────────────┘
         │
         ├──> backend/hooks/pre-commit
         │    ├─ Checkstyle
         │    ├─ Maven Compile
         │    └─ Maven Test (core)
         │
         └──> frontend/.husky/pre-commit
              ├─ ESLint --fix
              ├─ TypeScript --noEmit
              └─ (Build 跳過，改在 pre-push)
```

#### 安裝

```bash
make hooks-install
```

#### 跳過方式

```bash
# 緊急時跳過（不推薦）
git commit --no-verify
git push --no-verify
```

#### Pre-push Hook（最終把關）

會呼叫 `act` 模擬完整 CI 流程：

```bash
git push origin develop
# ↓
# 1. 偵測 act 是否安裝
# 2. 列出即將執行的 jobs
# 3. 詢問是否執行（避免意外卡住）
# 4. 執行 act CI 模擬
# 5. 通過才允許 push
```

---

### 四、建立高擬真的 API 與 AI 模型模擬

**目標**：不依賴外部 API，提升測試穩定性

#### 4.1 API Mock（Mockoon）

**用途**：模擬金流、物流、簡訊、OAuth 等外部 API

**啟動**：
```bash
make up-mock
# Mock API: http://localhost:3001
```

**已建立的 Mock 端點**（`mocks/mockoon-data.json`）：

| 端點 | 用途 |
|------|------|
| `POST /api/payment/credit-card` | Mock 信用卡付款 |
| `GET /api/logistics/track/:trackingId` | Mock 物流查詢 |
| `POST /api/sms/send` | Mock 簡訊發送 |
| `GET /api/auth/google/callback` | Mock Google OAuth |

**自訂 Mock**：
1. 編輯 `mocks/mockoon-data.json`
2. 重啟容器：`docker compose restart mock-server`

---

## 日常開發流程

### 情境 1：純前端變更

```bash
# 1. 開發
code frontend/src/...

# 2. 本機快速檢查
make check-frontend

# 3. Commit（pre-commit hook 自動跑 lint + tsc）
git add .
git commit -m "feat: 新增頁面"

# 4. Push（pre-push hook 跑 act）
git push origin develop
```

### 情境 2：純後端變更

```bash
# 1. 開發
code backend/src/main/java/...

# 2. 啟動 Docker Compose（需要 DB + Redis）
make up

# 3. 本機快速檢查
make check-backend

# 4. Commit + Push（同上）
```

### 情境 3：需要 Mock 外部 API

```bash
# 1. 啟動 Mock 服務
make up-mock

# 2. 在 application-dev.yml 設定 Mock URL
# MOCK_PAYMENT_URL=http://localhost:3001/api/payment
# MOCK_LOGISTICS_URL=http://localhost:3001/api/logistics

# 3. 開發 + 測試
```

### 情境 4：緊急修復（跳過所有檢查）

```bash
# 僅在緊急時使用
git commit --no-verify -m "hotfix: 緊急修復"
git push --no-verify origin develop
```

⚠️ **警告**：跳過檢查 = 高風險！請在 push 後立即到 GitHub Actions 確認 CI 通過。

---

## 故障排除

### Q1: `act` 顯示 Docker daemon 連線失敗

**原因**：Docker Desktop 未啟動
**解決**：
```bash
# macOS
open -a Docker

# 確認 Docker 運行
docker ps
```

### Q2: act 跑 CI 時 Redis 連線失敗

**原因**：Redis healthcheck 還沒通過
**解決**：
```bash
# 手動驗證 Redis
docker exec $(docker ps -qf "ancestor=redis:7-alpine") \
  redis-cli -a redis-dev-password --no-auth-warning ping
```

### Q3: Pre-commit hook 太慢（超過 30 秒）

**原因**：跑完整 Maven Test
**解決**：
- 僅跑核心層測試：`mvn test -Dtest="com.nextkey.ecommerce.core.**"`
- 或暫時跳過：`git commit --no-verify`

### Q4: Mock Server 啟動失敗

**原因**：`mockoon-data.json` 格式錯誤
**解決**：
```bash
# 驗證 JSON 格式
cat mocks/mockoon-data.json | python3 -m json.tool

# 或使用 Mockoon 桌面應用編輯
```

### Q5: Local LLM 啟動後立即退出

**原因**：模型檔案不存在
**解決**：
```bash
# 檢查模型
ls -lh ~/models/

# 重新下載
curl -L -o ~/models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf
```

### Q6: Push 一直被 pre-push hook 擋下

**原因**：act CI 模擬失敗
**解決**：
```bash
# 1. 手動執行 act 看錯誤
make validate-all

# 2. 查看詳細 log
act -W .github/workflows/act-compat.yml -v

# 3. 修復後重試
```

---

## 參考資源

### 工具文檔
- **act**: https://github.com/nektos/act
- **Docker Compose**: https://docs.docker.com/compose/
- **Husky**: https://typicode.github.io/husky/
- **Mockoon**: https://mockoon.com/docs/

### 專案內部文檔
- [DEPLOYMENT_CHECKLIST_SPRINT10.md](DEPLOYMENT_CHECKLIST_SPRINT10.md) - 部署檢查清單
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) - Release 流程

### 相關 .github/workflows
- `ci.yml` - 正式 GitHub Actions CI
- `act-compat.yml` - 本機 act 專用 workflow
- `technical-debt-review.yml` - 技術債檢視

---

## 維護記錄

| 日期 | 版本 | 變更 | 作者 |
|------|------|------|------|
| 2026-06-11 | 1.0 | 初版建立（完整本地 CI 工具鏈） | Claude Code |
| 2026-06-30 | 2.0 | 本地優先策略：三 workflow 改 workflow_dispatch only（停用雲端自動 CI）；新增 `make validate-e2e` / `validate-release`；`validate-schema` 串進 pre-push；修復 backend Dockerfile 死碼 `COPY .mvn .mvn` | Claude Code |
| 2026-06-30 | 3.0 | pre-push v5：依使用者要求「批次 push 但上 GIT 必須完整測試程序」，pre-push = `make validate-release`（act + schema + e2e）；FULL 記錄（僅 validate-release 寫得出）+ tree-hash 30 分快取避免重跑；純文件略過；移除已失效的 `make validate-push`（host 快檢不再放行 push） | Claude Code |
| 2026-07-02 | 3.1 | **AI-2301（S41 US-001）test DB↔act port 制度化**：`make validate-release` 在 `validate-all` 前自動 `test-db-down`（冪等），消除 `nk-test-redis`(6379) 與 act 服務容器的 port 衝突；直接執行與 pre-push 兩路徑一次涵蓋；新增「變更四」段落與開發者心智模型表 | Claude Code |

---

> **🔴 記住：push 即完整測試程序。想把等待挪到方便時，先手動 `make validate-release`！**
