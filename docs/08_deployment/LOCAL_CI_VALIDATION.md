# 本地 CI 驗證流程完整指南
# AISDLC v0.09 - 開發專注版
# 目標：所有 Push 到 GitHub 前，都應在本機完成完整 CI 模擬

> **🔴 核心原則：Push 之前，必須先在本機通過所有 CI 檢查！**

> **⚠️ 注意：`make validate-all`（act）走 `ddl-auto=update`，抓不到 entity↔migration 的 schema 漂移。**
> 修改 `@Entity` 欄位或 Flyway migration 後，請另外執行 **`make validate-schema`**（schema 漂移守門關卡）。
> 詳見 [SCHEMA_DRIFT_GATE.md](./SCHEMA_DRIFT_GATE.md)。

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

---

> **🔴 記住：Push 之前，務必先執行 `make validate-all`！**
