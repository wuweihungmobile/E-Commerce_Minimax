# 環境變數說明 / Environment Variables

> **文件類型**: 部署指南（環境變數）
> **版本**: v1.1
> **建立日期**: 2026-10-03（Sprint 244，文件對齊 3/3；v1.1 為收尾追加）
> **依據**: `backend/src/main/resources/application.yml`、後端 `@Value` 與條件式 bean、`docker-compose.yml`、`docker-compose.override.yml`、`.env.example`、`frontend/src`（`NEXT_PUBLIC_*`）、`scripts/validate-e2e.sh`、`Makefile`
> **讀者**: 部署與維運人員、後端與前端開發者
> **說明**: 本文件依程式碼核對，**不改變任何預設值**。與實作不符或尚待決定的項目見 §7 與 [DEFERRED_ITEMS_TRACKER.md](../04_planning/DEFERRED_ITEMS_TRACKER.md)（DEF-322、DEF-345～DEF-349）。

---

## 1. 怎麼讀這份文件

- **預設值**：未設定該變數時，應用程式實際使用的值（來自 `application.yml` 的 `${變數:預設值}`，或程式內的 `@Value` 預設）。「—」表示沒有預設值，未設定時啟動或功能會失敗。
- **compose 傳遞**：`docker-compose.yml` 的 backend `environment` 有沒有傳遞該變數。沒有傳遞時，容器使用預設值。
- **正式必填**：正式環境必須明確設定，不可依賴預設值。
- **功能開關不是環境變數**：例如 `STRIPE_PAYMENT_ENABLED` 是租戶功能開關，見 §5。
- **Spring 鬆綁規則**：`jwt.access-token-expiration` 可以用環境變數 `JWT_ACCESS_TOKEN_EXPIRATION` 覆寫。

---

## 2. 後端（Spring Boot）

### 2.1 Spring 與資料庫

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `SPRING_PROFILES_ACTIVE` | 未設定（compose 預設 `prod`） | 啟用的 profile。`LoggingEmailSender` 在 `prod` 不記錄信件內容 | ✅ | ✅ |
| `SPRING_DATASOURCE_URL` | — | PostgreSQL JDBC URL（compose 為 `jdbc:postgresql://postgres:5432/${POSTGRES_DB}`） | ✅ | ✅ |
| `SPRING_DATASOURCE_USERNAME` | — | 資料庫帳號（compose 來自 `POSTGRES_USER`，預設 `koala`） | ✅ | ✅ |
| `SPRING_DATASOURCE_PASSWORD` | — | 資料庫密碼（compose 來自 `POSTGRES_PASSWORD`，預設 `koala5`，**開發用**） | ✅ | ✅，必須覆寫 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | — | schema 管理方式；compose 預設 `validate` | ✅ | ✅（`validate`） |
| `SPRING_FLYWAY_ENABLED` | — | 是否執行 Flyway 遷移；compose 預設 `true` | ✅ | ✅（`true`） |
| `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT` | compose 固定 `redis`、`6379` | Redis 位址 | ✅ | ✅ |
| `SPRING_DATA_REDIS_PASSWORD` | compose 來自 `REDIS_PASSWORD`，預設 `redis-dev-password`（**開發用**） | Redis 密碼 | ✅ | ✅，必須覆寫 |

### 2.2 認證與 JWT

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `JWT_SECRET` | yml：`your-256-bit-secret-key-change-in-production-min-32-chars`；compose：`your-256-bit-secret-key-please-change-in-production` | HS256 簽章密鑰（至少 256 bits） | ✅ | ✅。**含 `change-in-production` 的值會被後端拒絕啟動**（DEF-320）。`make setup` 會產生隨機值 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | yml 寫死 `900000`（15 分鐘） | Access Token 效期（毫秒） | ✅（`900000`） | — |
| `JWT_REFRESH_TOKEN_EXPIRATION` | yml 寫死 `604800000`（**7 天**）；compose 寫死 `2592000000`（**30 天**） | Refresh Token 效期（毫秒）。**compose 與 yml 的預設不同** | ✅（`2592000000`） | 依產品決定天數 |
| `JWT_ISSUER_URI` | `https://placeholder.local` | Spring OAuth2 resource server 的 issuer（佔位值，對應 `spring.security.oauth2.resourceserver.jwt.issuer-uri`） | ❌ | 視是否使用該設定 |

### 2.3 OAuth 登入

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `OAUTH_GOOGLE_CLIENT_ID`、`OAUTH_GOOGLE_CLIENT_SECRET` | 空 | Google 登入 | ❌ | 啟用 Google 登入時必填 |
| `OAUTH_GITHUB_CLIENT_ID`、`OAUTH_GITHUB_CLIENT_SECRET` | 空 | GitHub 登入 | ❌ | 啟用 GitHub 登入時必填 |
| `OAUTH_ALLOWED_REDIRECT_ORIGINS` | `http://localhost:3000`（逗號分隔） | OAuth 回跳允許的前端來源 | ❌ | ✅（改為正式前端網域） |

### 2.4 寄信（SMTP）

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `SMTP_USERNAME` | 空 | Google Workspace 帳號。**有值時啟用 `SmtpEmailSender`**（`@Primary`，取代日誌型） | ✅ | 要寄真實信時必填 |
| `SMTP_PASSWORD` | 空 | 應用程式密碼（App Password），不是登入密碼；只由 `.env` 注入 | ✅ | 要寄真實信時必填 |
| `SMTP_HOST` | `smtp.gmail.com` | SMTP 主機 | ✅ | 視需要 |
| `SMTP_PORT` | `587`（STARTTLS 必須） | SMTP 埠 | ✅ | 視需要 |
| `SMTP_FROM_ADDRESS` | 空（沿用 `SMTP_USERNAME`） | 寄件位址。改成其他位址前，須先在 Google 帳戶設定為寄件別名並完成驗證 | ✅ | 視需要 |

**`SMTP_USERNAME` 為空時**（目前的預設）使用 `LoggingEmailSender`：非 `prod` 把信件全文寫入日誌；`prod` 只記錄「有一封信未寄出」，`canDeliver()` 為 `false`，因此**開店申請的 Email 驗證前置條件不生效**（SRD §5.5）。

**注意**：`SmtpEmailSender` 尚未對真實 Google Workspace 伺服器實測（Sprint 209 紀錄），且目前是同步寄送，啟用前須先處理 DEF-347。

### 2.5 前端網址與 CORS

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `APP_FRONTEND_BASE_URL` | `http://localhost:3000`（`@Value` 預設） | 重設密碼與 Email 驗證連結的網域；Stripe 回跳網址也用此設定 | ✅ | ✅（非 localhost 部署必填，見 SRD §5.5） |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | 允許的前端來源（逗號分隔）。不可用 `*`，因為 `allowCredentials=true` | ✅ | ✅ |

### 2.6 排程與逾時

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `APP_SCHEDULING_ENABLED` | `true` | 排程總開關（`app.scheduling.enabled`）。設為 `false` 會**同時停止**自動退款與逾時取消 | ✅（預設 `true`） | 視需要；緊急時使用 |
| `APP_SCHEDULING_POOL_SIZE` | `4` | 排程執行緒池大小 | ❌ | — |
| `ORDER_UNPAID_TIMEOUT_HOURS` | `24` | 未付款訂單的逾時自動取消（小時） | ❌ | — |
| `ORDER_TIMEOUT_CHECK_INTERVAL_MS`、`ORDER_TIMEOUT_INITIAL_DELAY_MS`、`ORDER_TIMEOUT_BATCH_SIZE` | `300000`、`60000`、`100` | 掃描間隔、啟動延遲、每批數量 | ❌ | — |
| `BOOKING_PAYMENT_TIMEOUT_HOURS` | `24` | 訂房付款期限（建立後的小時數） | ❌ | — |
| `BOOKING_STRIPE_SESSION_HOURS` | `24` | Stripe Checkout 工作階段的有效時間；期間內開始結帳的訂房不會被逾時取消 | ❌ | — |
| `BOOKING_TIMEOUT_CHECK_INTERVAL_MS`、`BOOKING_TIMEOUT_INITIAL_DELAY_MS`、`BOOKING_TIMEOUT_BATCH_SIZE` | `300000`、`60000`、`100` | 訂房逾時掃描（同上） | ❌ | — |
| `APP_REFUND_CHECK_INTERVAL_MS`、`APP_REFUND_INITIAL_DELAY_MS`、`APP_REFUND_BATCH_SIZE` | `60000`、`45000`、`50` | 自動退款掃描 | ❌ | — |
| `APP_REFUND_RETRY_BASE_MINUTES`、`APP_REFUND_RETRY_MAX_MINUTES` | `5`、`360` | 退款失敗的指數退避起點與上限（分鐘） | ❌ | — |

### 2.7 金流（Stripe）

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `STRIPE_SECRET_KEY` | `sk_test_placeholder`（佔位值） | Stripe 密鑰。**金鑰存在不代表 Stripe 已啟用**，啟用由租戶功能開關決定（§5） | ✅（同預設） | 啟用 Stripe 時必填 |
| `STRIPE_WEBHOOK_SECRET` | 空 | Webhook 簽章密鑰。compose 曾因未傳遞而使 prod 容器無法啟動（Sprint 188，DEF-262） | ✅（`${STRIPE_WEBHOOK_SECRET:-}`） | 啟用 Stripe 時必填 |

### 2.8 物件儲存（MinIO／S3 相容）

| 變數 | 預設值 | 用途 | compose 傳遞 | 正式必填 |
|------|--------|------|:------------:|:--------:|
| `STORAGE_ENDPOINT` | `http://localhost:9000` | 儲存服務位址 | ❌ | ✅ |
| `STORAGE_REGION` | `us-east-1` | 區域 | ❌ | 視服務而定 |
| `STORAGE_BUCKET` | `media` | 儲存桶名稱 | ❌ | ✅ |
| `STORAGE_ACCESS_KEY` | `minioadmin`（**開發預設**） | 存取金鑰。`docker-compose.override.yml`（開發）會設為 `minioadmin` | ❌ | ✅，**必須覆寫**（DEF-348） |
| `STORAGE_SECRET_KEY` | `minioadmin123`（**開發預設**） | 私密金鑰。同上 | ❌ | ✅，**必須覆寫**（DEF-348） |

`storage.presigned-expiry` 寫死為 `3600` 秒，不是環境變數。

---

## 3. 前端（Next.js）

| 變數 | 預設值 | 用途 | 生效時機 | 說明 |
|------|--------|------|----------|------|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080/api/v2`（compose 的 build arg 與 `.env.example`） | 前端呼叫的 API 基底網址 | **建置時**（內嵌於 bundle） | 改變數後必須重新建置映像檔 |
| `NEXT_PUBLIC_OAUTH_GOOGLE_CLIENT_ID` | 空 | 前端的 Google 登入按鈕 | 建置時 | 與後端 `OAUTH_GOOGLE_CLIENT_ID` 對應；compose 未傳遞 |
| `NEXT_PUBLIC_OAUTH_GITHUB_CLIENT_ID` | 空 | 前端的 GitHub 登入按鈕 | 建置時 | 與後端 `OAUTH_GITHUB_CLIENT_ID` 對應；compose 未傳遞 |

---

## 4. Docker Compose 與 `.env`

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `DOCKER_PLATFORM` | `linux/amd64` | 容器平台。M1／M2 Mac 可設為 `linux/arm64` 以原生執行 |
| `SPRING_PROFILES_ACTIVE` | `prod` | 見 §2.1 |
| `POSTGRES_USER`、`POSTGRES_PASSWORD`、`POSTGRES_DB` | `koala`、`koala5`、`nextkeytest` | **開發用預設值**，正式必須覆寫 |
| `REDIS_PASSWORD` | `redis-dev-password` | **開發用預設值**，正式必須覆寫 |
| `JWT_SECRET` | `your-256-bit-secret-key-please-change-in-production` | 含 `change-in-production`，後端拒絕啟動，正式必須覆寫 |
| `STRIPE_SECRET_KEY`、`STRIPE_WEBHOOK_SECRET` | `sk_test_placeholder`、空 | 見 §2.7 |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080/api/v2` | 見 §3 |

`.env.example` 列出 `DOCKER_PLATFORM`、`SPRING_PROFILES_ACTIVE`、`NEXT_PUBLIC_API_URL`、`JWT_SECRET`、`POSTGRES_*`、`REDIS_PASSWORD`、`SMTP_*`、`APP_FRONTEND_BASE_URL`、`APP_CORS_ALLOWED_ORIGINS`、`APP_SCHEDULING_ENABLED`，以及 CI 用的註解行。**沒有列出** Stripe、儲存、OAuth 與逾時變數（見 §7）。

`docker-compose.override.yml` 是本機開發用：會設定 `STORAGE_*` 為 `minioadmin`，並定義 `minio` 服務（profile `storage`，見 DOCKER_POLICY.md）。

---

## 5. 功能開關（不是環境變數）

| 開關 | 預設 | 作用 | 注意 |
|------|------|------|------|
| `STRIPE_PAYMENT_ENABLED` | **關閉**（沒有資料列即為關閉；V79 註解） | 開啟後，Mock 付款回 `E-6004`，付款改走 Stripe Checkout；Stripe 發起在未開啟時回 `E-6002` | 這是**租戶級**開關（`TenantFeatureToggle`），以**目前請求的租戶**查詢。買家的請求在其所屬租戶下執行（一般消費者屬系統租戶），因此開關要設在買家所屬的租戶上（依此推論；實際操作方式未在本文件實測） |

**上線風險（DEF-346）**：全新部署時此開關關閉，所有買家都能以 Mock 付款把訂單改為已付款，**不經過金流**。在正式上線前必須確認開關已開啟，且 `STRIPE_SECRET_KEY`、`STRIPE_WEBHOOK_SECRET` 為正式值。

---

## 6. 上線前檢查（以本文件為準）

| # | 項目 | 依據 |
|---|------|------|
| 1 | `JWT_SECRET` 設為隨機值，且不含 `change-in-production` | §2.2、DEF-320 |
| 2 | `POSTGRES_PASSWORD`、`REDIS_PASSWORD` 改為正式密碼 | §2.1、§4 |
| 3 | `STORAGE_ACCESS_KEY`、`STORAGE_SECRET_KEY` 改為正式金鑰 | §2.8、DEF-348 |
| 4 | `STRIPE_PAYMENT_ENABLED` 已在買家所屬租戶開啟；`STRIPE_SECRET_KEY`、`STRIPE_WEBHOOK_SECRET` 為正式值 | §5、DEF-346 |
| 5 | 要寄真實信時：設定 `SMTP_USERNAME`、`SMTP_PASSWORD`，**並先完成 DEF-347**（非同步寄送）與真實寄信實測 | §2.4、DEF-347 |
| 6 | `APP_FRONTEND_BASE_URL`、`APP_CORS_ALLOWED_ORIGINS`、`OAUTH_ALLOWED_REDIRECT_ORIGINS` 改為正式網域 | §2.3、§2.5 |
| 7 | `APP_SCHEDULING_ENABLED` 為 `true`（預設值） | §2.6 |
| 8 | 決定 Refresh Token 天數（yml 7 天、compose 30 天，兩者不同） | §2.2 |
| 9 | 其他部署前置（非環境變數）：執行 `V90__Unpublish_Orphaned_System_Tenant_Content.sql` 前，先跑檔頭的三個預覽 `SELECT` 看筆數 | Sprint 242 計畫書 |

---

## 7. 已知落差（待決定，見 DEF-322）

| 落差 | 影響 | 狀態 |
|------|------|------|
| `docker-compose.yml` 仍未傳遞 `OAUTH_*`、`STORAGE_*`（`SMTP_*`、`APP_FRONTEND_BASE_URL`、`APP_CORS_ALLOWED_ORIGINS`、`APP_SCHEDULING_ENABLED` 已於 Sprint 244 收尾時補傳，經使用者授權） | OAuth 與儲存設定在 compose 部署使用預設值（儲存為 `minioadmin`，見 DEF-348） | 🟡 OAuth 與儲存是否補傳待決定（需使用者明確指示） |
| `.env.example` 未列出 Stripe、儲存、OAuth 與逾時變數 | 依 `.env.example` 設定的人不知道有這些設定 | 🟡 同上（SMTP 與 APP 網址、CORS、排程已補列） |
| compose 內建的 `koala5`、`redis-dev-password` 為開發預設值 | 直接用於正式環境時，預設密碼可被猜到 | 🟡 見 §6 第 2 項 |
| `JWT_REFRESH_TOKEN_EXPIRATION`：yml 7 天、compose 30 天 | 同一份程式在不同部署的 Refresh Token 效期不同 | 已記載（§2.2） |

---

## 8. 開發、測試與 CI 腳本使用的變數（非應用程式設定）

| 變數 | 預設 | 用途 | 位置 |
|------|------|------|------|
| `E2E_BACKEND_LOG` | 由 `make validate-e2e` 設定 | 後端日誌檔路徑。Playwright 從日誌取得信件中的連結（刻意不提供信箱查詢端點） | `frontend/e2e/helpers/mailbox.ts`、`scripts/validate-e2e.sh` |
| `E2E_GATE_SKIP_BUILD` | `0` | `1` 時重用既有的 JAR 與 `.next`（較快，但會測到舊的前端；改了前端要先手動建置） | `scripts/validate-e2e.sh` |
| `E2E_GATE_STRICT` | `1` | `0` 為 advisory 模式：spec 失敗只報告、不阻擋。僅供環境異常時臨時使用 | `scripts/validate-e2e.sh` |
| `E2E_GATE_WAIT` | `180`（秒） | 等待後端就緒的秒數 | `scripts/validate-e2e.sh` |
| `E2E_GATE_BACKEND_PORT`、`E2E_GATE_FRONTEND_PORT` | `8080`、`3000` | 本機啟動後端與前端的埠 | `scripts/validate-e2e.sh` |
| `E2E_GATE_PG_PORT`、`E2E_GATE_RD_PORT` | `55432`、`56379` | 驗證用 PostgreSQL 與 Redis 的埠 | `scripts/validate-e2e.sh` |
| `CI_DATA_DIR`、`CI_RECORD` | `.ci-validation-data`、`$CI_DATA_DIR/commits` | 本機守門的驗證記錄位置（Makefile 內部使用，不需手動設定） | `Makefile` |

---

## 9. 修訂紀錄

| 版本 | 日期 | 內容 | 作者 |
|------|------|------|------|
| v1.0 | 2026-10-03 | 首版（Sprint 244）：依 `application.yml`、`@Value`、`docker-compose.yml`、`.env.example` 與前端程式核對；新增 §5 功能開關與 §6 上線前檢查 | Claude Code（Sprint 244） |
| v1.1 | 2026-10-03 | Sprint 244 收尾追加（使用者授權）：compose 補傳 `SMTP_*`、`APP_FRONTEND_BASE_URL`、`APP_CORS_ALLOWED_ORIGINS`、`APP_SCHEDULING_ENABLED`；`.env.example` 同步列出；§2、§4、§7 更新 | Claude Code（Sprint 244） |

---

**文件結束**
