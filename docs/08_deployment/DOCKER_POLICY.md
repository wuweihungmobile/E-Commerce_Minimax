# Docker 管理政策 / Docker Management Policy

**建立日期**: 2026-06-24
**適用範圍**: 所有 Docker 相關檔案（Dockerfile、docker-compose*.yml）
**維護者**: 人工確認（AI 禁止自行修改此文件中的核准清單）

---

## 🔴 核心原則

1. **Image 最小化原則**：每個 build stage 只使用完成工作所需的最小 image
2. **版本釘定原則**：核心服務（prod/CI）必須釘定到主版本（如 `postgres:18-alpine`），不得使用 `latest`
3. **快取優先原則**：相同 base image 在 Dockerfile 多 stage 間共用，避免重複拉取
4. **Volume 最小授權原則**：只掛載必要的目錄，生產環境嚴禁 bind mount 源碼

---

## ✅ 核准 Image 清單（Approved Image List）

> 🔴 **AI 禁止新增此清單以外的 image，新增需人工審核並更新此文件**

| Image | 允許版本 | 用途 | 允許環境 | 釘定狀態 |
|-------|---------|------|---------|---------|
| `postgres` | `18-alpine` | 主資料庫 | prod / CI / dev | ✅ 已釘定 |
| `redis` | `7-alpine` | 快取 / Session | prod / CI / dev | ✅ 已釘定 |
| `node` | `20-alpine` | Frontend 建置 & 執行 | prod / CI / dev | ✅ 已釘定 |
| `maven` | `3.9-eclipse-temurin-21` | Backend Maven 建置 | 建置 only | ✅ 已釘定 |
| `eclipse-temurin` | `21-jre-alpine` | Backend 執行時 & layer 提取 | 建置 & prod | ✅ 已釘定 |
| `catthehacker/ubuntu` | `act-latest` | 本機 act CI 模擬 | 本機 CI only | ✅ 已釘定 |
| `minio/minio` | `RELEASE.2025-09-07T16-13-09Z` | 物件儲存 | 開發 only | ✅ 已釘定（2026-06-24） |
| `mockoon/cli` | `9.7.0` | API Mock | 開發 only | ✅ 已釘定（2026-06-24） |

> ℹ️ **local-llm (ghcr.io/ggerganov/llama.cpp) 已於 2026-06-24 移除**
> 原因：代碼庫（backend/frontend）中無任何 LLM 呼叫引用。
> 若未來需要本地 LLM 功能，需先確認使用場景，並將 image 加回此核准清單後由人工確認。

---

## 📁 Volume 掛載規則（Volume Mount Rules）

> 🔴 **AI 禁止新增或移除 volume 掛載，所有掛載變更需人工確認**

### 核准的 Volume 配置

| Volume 名稱 | 容器掛載路徑 | 目的 | 是否持久化 | 允許環境 |
|------------|------------|------|----------|---------|
| `postgres_data` | `/var/lib/postgresql` | PG 資料持久化 | ✅ 是 | prod |
| `postgres_ci_data` | `/var/lib/postgresql` | CI 測試（測後清除） | ⏱️ 暫存 | CI |
| `redis_data` | `/data` | Redis AOF 持久化 | ✅ 是 | prod |
| `redis_ci_data` | `/data` | CI 測試（redis 為 ephemeral） | ⏱️ 暫存 | CI |
| `maven_cache` | `/root/.m2` | Maven 套件快取（加速建置） | ✅ 是 | dev |
| `minio_data` | `/data` | MinIO 物件儲存 | ✅ 是 | dev |
| ~~`llm_models`~~ | ~~`/models`~~ | ~~GGUF 模型快取~~ | ~~✅ 是~~ | ❌ 已移除（2026-06-24，local-llm 服務移除） |
| `./frontend:/app` | `/app` | 前端 hot-reload bind mount | ❌ 暫存 | dev only |
| `./backend:/app` | `/app` | 後端 hot-reload bind mount | ❌ 暫存 | dev only |
| `/app/node_modules` | `/app/node_modules` | 匿名 volume 保護 node_modules | ❌ 暫存 | dev only |
| `/app/.next` | `/app/.next` | 匿名 volume 保護 .next 快取 | ❌ 暫存 | dev only |

### ⚠️ 已知風險：initdb.d 掛載

**位置**：`docker-compose.yml` postgres service
```yaml
- ./backend/src/main/resources/db/migration:/docker-entrypoint-initdb.d:ro
```

**風險說明**：
- PostgreSQL 容器在 data volume 為空時（首次啟動），會自動執行 `initdb.d/` 中所有 `.sql` 檔案
- 同時，Spring Boot 啟動時 Flyway 也會執行相同的 migration SQL
- **潛在衝突**：若 SQL 未包含 `IF NOT EXISTS` 保護，可能造成 table 已存在但 Flyway schema_history 不存在的衝突

**建議**：
- 若 Flyway 已設定 `spring.flyway.baseline-on-migrate=true`，則可安全共存
- 若未設定，建議移除此 initdb.d 掛載，讓 Flyway 全權管理 schema
- **🔴 AI 禁止自行移除或保留此掛載，需人工確認後決策**

---

## 🚫 禁止行為（Forbidden Actions）

### AI (Claude) 禁止行為

1. **❌ 禁止新增此清單以外的 Docker image**
   - 新增新 service 必須先更新此文件，再由人工確認

2. **❌ 禁止將任何服務的 image tag 從釘定版本改為 `latest`**
   - 例如：不得將 `postgres:18-alpine` 改為 `postgres:latest`

3. **❌ 禁止在生產 docker-compose.yml 中新增 bind mount（源碼掛載）**
   - 生產環境只允許 named volumes

4. **❌ 禁止移除 healthcheck 設定**
   - 每個 service 的 healthcheck 是服務啟動順序的關鍵依賴

5. **❌ 禁止移除 deploy.resources 資源限制**
   - 資源限制防止單一容器耗盡主機資源

6. **❌ 禁止修改 redis 密碼設定（requirepass）**
   - 需同步更新 Spring Boot 環境變數

7. ~~禁止在未啟用 `profiles: ["with-llm"]` 的情況下讓 local-llm 自動啟動~~（條款已廢止，local-llm 服務已於 2026-06-24 移除）

### 人工操作禁止行為

1. **❌ 禁止直接 `docker pull` 更新釘定版本 image 而不更新此文件**
2. **❌ 禁止在 CI 驗證前修改 docker-compose.yml 並 push**

---

## 🔄 Image 更新流程（Change Process）

當需要更新 image 版本時：

```
1. 人工確認新版本的 breaking changes（如 postgres 18 → 19）
2. 更新此文件的核准 image 清單
3. 更新對應的 Dockerfile / docker-compose*.yml
4. 在本機執行 docker compose pull + up 驗證
5. 執行完整 CI 測試
6. 確認通過後才能 push 到 main
```

---

## 📊 服務架構與 Image 關係圖

```
本機開發（docker compose up）
├── frontend  ← node:20-alpine (builder target)
├── backend   ← maven:3.9-eclipse-temurin-21 (builder target)
├── postgres  ← postgres:18-alpine
├── redis     ← redis:7-alpine
└── minio     ← minio/minio:RELEASE.2025-09-07T16-13-09Z ✅（dev only，已釘定 2026-06-24）

生產環境（docker compose -f docker-compose.yml up）
├── frontend  ← node:20-alpine (runner target，standalone build)
├── backend   ← eclipse-temurin:21-jre-alpine (runner target，layered JAR)
│              建置過程使用：
│              ├── maven:3.9-eclipse-temurin-21  (stage 1: builder)
│              └── eclipse-temurin:21-jre-alpine (stage 2: extractor + stage 3: runner)
├── postgres  ← postgres:18-alpine
└── redis     ← redis:7-alpine

Mock 環境（加 -f docker-compose.mock.yml）
└── mock-server  ← mockoon/cli:9.7.0 ✅（已釘定 2026-06-24）
    （local-llm 已移除，代碼庫中無 LLM 呼叫）

本機 CI（act）
└── runner    ← catthehacker/ubuntu:act-latest
```

---

## Backend Dockerfile Multi-stage 建置說明

```
Stage 1: builder  (maven:3.9-eclipse-temurin-21)
  → mvn dependency:go-offline  ← 套件快取層（pom.xml 不變則快取命中）
  → mvn package -DskipTests

Stage 2: extractor  (eclipse-temurin:21-jre-alpine)  ← 🔴 與 runner 相同 image！
  → java -Djarmode=layertools -jar app.jar extract
  → Docker 快取此 image，不重複拉取

Stage 3: runner  (eclipse-temurin:21-jre-alpine)
  → 只複製 layer extracted 的 classes
  → 最小化最終 image 大小
```

**重要**：Stage 2 和 Stage 3 使用相同 base image（`eclipse-temurin:21-jre-alpine`），
Docker build cache 會命中，**不會重複拉取**。
禁止將 Stage 2 改回 `eclipse-temurin:21-jdk-alpine`（已浪費 400MB 額外 pull）。

---

*此文件由 AISDLC v0.09 管理，任何修改需記錄在 build/logs/ 或 Sprint Release Notes*
