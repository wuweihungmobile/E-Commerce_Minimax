# Sprint 244 Plan — 文件對齊（3/3）：SRD 改寫、FRD 補 M06／M07／M09 章、PRD 修訂註記、TC 現況聲明與環境變數文件；核對出五個新缺口（DEF-345～DEF-349）

**Sprint**: Sprint 244
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（依 SPRINT_243_PLAN §8.2）

本輪範圍是 [SPRINT_243_PLAN.md](SPRINT_243_PLAN.md) §8.2 排定的 SRD（訂單狀態圖、token 效期與 Cookie、寄信 Mock 的過時說法）、FRD 補 M06／M07／M09 章、PRD 修訂註記、TC 現況聲明、環境變數文件。依 CLAUDE.md，compose 傳遞環境變數屬 Docker 設定，須使用者明確指示，**本輪不改 `docker-compose.yml`**。

依據是 DEF-322（Sprint 232 文件一致性檢查的分類）與 DEF-323 (b)。排程順延：原 Sprint 242 → M05／M06 規格（實際為 Sprint 243），原 Sprint 243 → 本輪。

### 1.2 方法

- **逐條核對**：文件裡每個與實作有關的宣稱，都以程式碼、設定檔、遷移檔或 git 歷史確認（grep、讀碼、讀設定）。不採用記憶與 agent 報告作為結論；記憶裡的事實（例如 Refresh 效期、SMTP 的實測狀態）只有在程式碼中找得到對應時才寫進文件。
- **不寫探針**：本輪不改程式、不跑真實服務。需要行為層級的事實（例如訂房狀態能不能到 `CONFIRMED`）改以**寫入路徑**確認：搜尋 `setStatus`、`.status(...)`、列舉使用處，以及端點清單。
- **每個新宣稱附來源**：類別、設定鍵或端點，方便之後的人核對。

### 1.3 查出的差異（文件 vs 實作）

| 文件 | 原寫法 | 實作（來源） | 本輪處理 |
|------|--------|--------------|----------|
| SRD §5.1 | Access 30 分鐘、Refresh 30 天、HttpOnly Cookie、Payload 含 `roles` | Access 15 分鐘、Refresh 預設 7 天（compose 30 天）、無 Cookie、前端 `localStorage`、claim 為單一 `role`（`application.yml`、`JwtTokenService`、`frontend/src/lib/axios.ts`） | 改寫，加修訂註記 |
| SRD §5.5 | `LoggingEmailSender` 是 Phase 1 唯一實作 | 另有 `SmtpEmailSender`（Sprint 209，`@Primary`，`SMTP_USERNAME` 有值時啟用）；未對真實伺服器實測；同步寄送 | 補 `SmtpEmailSender`，寫明未實測與 DEF-347 |
| SRD §6.3.1 | `CREATED(=PAID)`、`DELIVERED → REFUNDING`、Phase 1 Mock 立即完成 | `OrderStateMachine.canTransition`：`CREATED → PAID`（付款）、`CANCELLED → REFUNDING → REFUNDED`；沒有 `DELIVERED → REFUNDING` | 依狀態機重繪，列出觸發與實作位置 |
| SRD §6.3（訂房） | 無訂房狀態圖 | 列舉 7 值；實際可達只有 `CREATED`、`PAID`、`CANCELLED`（DEF-345） | 新增 §6.3.4 |
| SRD_Module §1.2 | `TokenService`、`ACCESS_TOKEN_VALIDITY = 30 * 60` | 實際類別 `JwtTokenService`，效期由設定注入 | 常數改 15 分鐘，加註記 |
| SRD_Module §4 | 建單時即 `transition(PAID)` | 建單後為 `CREATED`，付款後才 `PAID` | 移除草稿中的錯誤轉換，序列圖標示改為「付款成功」 |
| FRD | 無 M06／M07／M09 章 | 已有實作（DEF-322） | 新增 §6A／§6B／§6C，附與 PRD 的差異 |
| PRD §3、§9.6、§9.7、Mock 表、M05 排除表 | `CREATED(=PAID)`、`X-Mock-Fail`、`/state-log`、「Phase 1 不支援退款」、「Phase 1 僅查詢與取消」 | 狀態機與付款見上；失敗模擬是 `POST …/pay/fail`；日誌路徑是 `/v2/orders/{orderId}/logs`；退款已實作（管理員） | 只加修訂註記，內文不動 |
| TC（12 份） | 停在 2026 年上半年 | 後端測試與 E2E 是現行來源（Sprint 243 數字） | 索引寫完整現況聲明，各檔加一行 |
| 環境變數（無文件） | 只有 `.env.example`（不完整） | `application.yml` 約 40 個環境變數佔位符，另有 compose 與前端的設定；compose 傳遞與預設值各不相同 | 新增 `docs/08_deployment/ENVIRONMENT_VARIABLES.md` |
| API_M06（S243 我寫的） | 「`CONFIRMED` 可更新」「由店家操作」 | 沒有任何寫入路徑（DEF-345） | 更正三處，加註記 |

### 1.4 新登記的缺口（寫文件時以程式碼核對發現，未修）

| 編號 | 嚴重度 | 內容 | 本輪是否修 |
|------|--------|------|------------|
| DEF-345 | 🟡 | 訂房的 `CONFIRMED`、`CHECKED_IN`、`CHECKED_OUT`、`COMPLETED` 沒有任何寫入路徑；店家沒有確認或入住的端點；PRD `GET /api/v2/bookings/:id/state-log` 未實作 | 否（新功能與權限決策，見 §6.2） |
| DEF-346 | 🟠 | `STRIPE_PAYMENT_ENABLED` 是租戶級功能開關，**沒有資料列即為關閉**。全新部署時所有買家都能以 Mock 付款把訂單改為已付款，不經過金流 | 否（上線程序決策，見 §6.2） |
| DEF-347 | 🟡（潛伏） | `SmtpEmailSender` 同步寄送；忘記密碼與 Email 驗證在請求內呼叫 `send`，存在的帳號回應較慢，形成帳號列舉的時序側通道。目前 compose 不傳遞 `SMTP_*`，尚未出現 | 否（啟用 SMTP 前必修） |
| DEF-348 | 🟡 | `application.yml` 的 MinIO 預設憑證 `minioadmin`／`minioadmin123`，正式 compose 未覆寫 | 否（需要同步調整 compose 與開發流程） |
| DEF-349 | 🟢 | LINE Pay 閘道是 stub（不呼叫 LINE Pay API，`confirmPayment` 無條件成功）；舊版 `POST /v2/payments` 在 Mock 模式會把 `paymentMethod` 原樣寫入 `SUCCESS`。目前無法產生真實入帳，屬標籤問題 | 否（接 LINE Pay 前必修） |

### 1.5 更正與偽陽性（我自己的錯與核對過程中的修正）

- **S243 M06 規格錯誤**：我把 `CONFIRMED` 寫成「實測可更新、由店家操作」。實際上那只能由直接寫資料庫的測試產生，程式沒有任何寫入路徑。已在 `API_M06_Booking.md` 三處更正，並在 SRD §6.3.4 與 DEF-345 記錄。
- **DEF-323 (b) 的 PRD 部分不成立**：DEF-323 寫「PRD 也有 HttpOnly Cookie、Access 30 分鐘、Refresh 30 天」。核對後 PRD 沒有 token 效期或 Cookie 的具體數值（grep `HttpOnly`、`30 天`、`Cookie` 皆無），因此 PRD 不需要 token 註記。這些數值只出現在 SRD 與 API_M03，已改寫。
- **DEF-322 與 DEF-323 (b) 的衝突**：DEF-323 (b) 寫「PRD／SRD 改成現況」，DEF-322 寫「比照 Sprint 203：PRD 內文不動、加修訂註記」。依 Rule 7，採用較新且由使用者拍板的決定（Sprint 232 的「比照 Sprint 203」）：PRD 只加註記，SRD 改寫。DEF-323 (b) 的原計畫視為被取代。
- **Stripe 開關的疑慮（已排除）**：我一度懷疑 Mock 守門與 Stripe 入口讀取的租戶不同，可能讓同一買家兩條路徑互相繞過。核對 `PaymentStateService`：Mock 守門（`requireMockPaymentAllowed`）、Stripe Checkout 發起、付款方式標籤（`"stripe" : "mock"`）四處都呼叫同一個 `featureToggleService.isFeatureEnabled(...)`，查的是**同一個當前租戶**，因此同一請求內兩條路徑互斥，沒有繞過。真正的風險是預設值（DEF-346）。
- **SMTP bean 衝突（已排除）**：`LoggingEmailSender` 無條件建立，`SmtpEmailSender` 在 `SMTP_USERNAME` 有值時建立並標 `@Primary`；注入點只有 `AccountSecurityService`。沒有衝突。
- **application.yml 過時註解**：第 56–57 行寫 `@ConditionalOnProperty`，實際是 `@ConditionalOnExpression`（`SmtpEmailSender` 的註解解釋了原因）。註解過時、行為正確，本輪未改（Rule 3）。

### 1.6 既有問題（核對時發現，HEAD 即有，不在本輪範圍）

- FRD 各模組「API 規格概要」段（§3.7、§4.7、§5.7、§6.5、§7.7、§8.6）的連結指向 `../02_architecture/API_M0x_*.md`，但檔案實際在 `../02_architecture/api/` 下，且 M17 的檔名是 `API_M17_Tenant.md`。共 6 條，未修。
- DEFERRED_ITEMS_TRACKER 的 `frontend/src/app/(auth)/…` 連結，因路徑含括號，在 Markdown 中無法解析。未修。
- FRD §11.3 變更記錄只有 v1.0（之後的版本未同步；§文件修訂紀錄是正本）。未修。

## 2. 使用者決策與行為變更

### 2.1 本輪做出的決策

使用者授權「若需要我決策，請先以最佳理想化進行決策」。本輪的決策如下，若要改變請告知：

| 題目 | 決策 | 理由 |
|------|------|------|
| PRD 內文要不要改寫（DEF-322 與 DEF-323 (b) 衝突） | 內文不動，只加修訂註記 | 使用者在 Sprint 232 拍板「比照 Sprint 203」，較新；Rule 7 |
| FRD 新章節編號 | 採 6A／6B／6C，不重排既有章節 | 避免破壞既有的交叉參照（§7、§8 等） |
| 訂房 `CONFIRMED` 之後的狀態 | 文件只寫實際可達的狀態；不在本輪實作 | 這是新功能與權限決策（誰能確認、入住由誰驅動），依 playbook 登記、不擅自做，見 DEF-345 |
| compose 與 `.env.example` | 收尾時經使用者授權，補傳 `SMTP_*`、`APP_FRONTEND_BASE_URL`、`APP_CORS_ALLOWED_ORIGINS`、`APP_SCHEDULING_ENABLED`（§2.3）；`OAUTH_*`、`STORAGE_*` 未授權，維持現狀 | CLAUDE.md：Docker 設定與環境變數值須使用者明確指示 |
| Stripe 開關的預設值 | 不改預設；寫入上線檢查（環境變數文件 §5、§6），登記 DEF-346 | 改預設會影響開發與 E2E 流程；上線程序需要使用者決定 |
| SMTP 同步寄送 | 不在本輪修；登記 DEF-347 為啟用 SMTP 的前置條件 | 屬程式變更；目前 compose 不啟用 SMTP，風險潛伏 |
| MinIO 預設憑證 | 不在本輪修；登記 DEF-348 | 需同步調整 compose 與開發流程 |

### 2.2 程式行為變更

**無程式行為變更**。主要變更為 `docs/`（21 個檔案，含本計畫書）；收尾追加 `docker-compose.yml` 與 `.env.example`，只新增 `${VAR:-預設}` 引用，預設值與程式內的預設相同，未設定時行為不變。沒有任何 `backend/`、`frontend/` 的變更。

### 2.3 收尾時的使用者回覆與追加變更

本輪收尾時，兩個需要使用者拍板的問題以互動選項提出（推薦選項為第一個），使用者的回覆如下：

| 題目 | 使用者選擇 | 本輪處理 |
|------|------------|----------|
| `docker-compose.yml` 補傳環境變數（DEF-322） | 授權補傳（推薦）：非機密設定補傳，密碼只由 `.env` 注入，`.env.example` 同步 | 已補傳 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USERNAME`、`SMTP_PASSWORD`、`SMTP_FROM_ADDRESS`、`APP_FRONTEND_BASE_URL`、`APP_CORS_ALLOWED_ORIGINS`、`APP_SCHEDULING_ENABLED`；預設值與程式內預設相同；`OAUTH_*`、`STORAGE_*` 未授權，維持現狀 |
| 訂房入住流程（DEF-345） | 依 PRD Phase 1（推薦）：付款成功即等同確認，不另設 `CONFIRMED`；店家標記入住與退房，完成由退房後自動處理 | 已登記為拍板決定，**排入 Sprint 245 實作**；本輪不實作（新增端點、權限與測試，需要獨立計畫） |

其餘的決策（§2.1）由我依「最佳理想化」原則決定，未再詢問。

## 3. 實作內容（文件清單）

| 檔案 | 版本 | 變更 |
|------|------|------|
| [SRD_System_Architecture.md](../02_architecture/SRD_System_Architecture.md) | v1.4 | §5.1 Token 效期、無 Cookie、Payload；§5.5 寄信通道（`SmtpEmailSender`、部署狀態、DEF-347）；§6.3.1 訂單狀態圖重繪；§6.3.4 訂房狀態（新增）；修訂歷史 |
| [SRD_Module_Technical_Design.md](../02_architecture/SRD_Module_Technical_Design.md) | v1.2 | §1.2 Token 常數與註記；§4 M05 建單草稿與 `ALLOWED_TRANSITIONS` 註記；序列圖標示；修訂歷史 |
| [API_M06_Booking.md](../02_architecture/API_M06_Booking.md) | 修訂註記 | 更正三處 `CONFIRMED` 敘述（S243 錯誤，DEF-345） |
| [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md) | v1.3 | 頂部修訂註記；§6A M06、§6B M07、§6C M09（新增，各含功能總覽、主要用戶、資料模型、業務規則、API、與 PRD 的差異）；文件修訂紀錄 |
| [E-Commerce_PRD_v1.0_Final.md](../01_requirements/E-Commerce_PRD_v1.0_Final.md) | v1.0.3 | 版本列與修訂註記 4 段（§3 狀態機、§9.6、§9.7、Mock 付款表、M05 排除範圍表）；**內文未改** |
| [TC_Index.md](../03_testing/TC_Index.md) | — | 完整現況聲明（停在哪、現行測試來源、已知差異） |
| `TC_E2E`、`TC_M01`～`TC_M08`、`TC_M12`、`TC_M17`（11 份） | — | 各加一行現況聲明，指向索引；內容未重寫 |
| [ENVIRONMENT_VARIABLES.md](../08_deployment/ENVIRONMENT_VARIABLES.md) | v1.0（新） | 後端、前端、compose、功能開關、上線前檢查、已知落差、開發與 CI 變數 |
| [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) | v3.33 | DEF-322 部分完成、DEF-323 結案；DEF-345～349 新增；歷史版本 v3.32 補記（版本鏈缺口） |
| [RELEASE_TRACKER.md](RELEASE_TRACKER.md) | — | Sprint 244 列（⏳ 待 push）與統計區 |
| `docker-compose.yml`、`.env.example` | — | 收尾追加：補傳 `SMTP_*`、APP 網址與 CORS、排程開關（§2.3） |
| 本檔 | — | 計畫書 |

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 文件守門（後端單元） | `ApiRouteDocDriftTest`（API 規格路由雙向比對；M06 規格有改動，必須重跑）、`ErrorCodeDocDriftTest`（錯誤碼文件） | 見 §5 |
| 連結檢查 | 變更與新增的 21 個 Markdown 檔（含本檔），全部相對連結（含錨點前的路徑）是否解析 | 見 §5 |
| Secret 掃描模擬 | 取自 `scripts/hooks/pre-commit` 第 81～89 行的規則，對 staged 的新增行逐檔掃描 | 見 §5 |
| 整合測試、E2E、`validate-release` | **不跑**：本輪沒有 `backend/`、`frontend/` 的變更；pre-push 對純文件放行（見 `validate-release-push-preflight` 記憶） | — |

## 5. 驗證結果

- **文件守門**：`mvn -o test -Dtest=ApiRouteDocDriftTest,ErrorCodeDocDriftTest`，退出碼 **0**，`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（`ApiRouteDocDriftTest` 2 個、`ErrorCodeDocDriftTest` 1 個）。
- **連結檢查**：變更與新增的 21 個 `.md`（含本檔）共 679 個相對連結，剩 7 個無法解析，全部是 HEAD 即存在的既有問題（§1.6：FRD 的 6 條 API 連結、追蹤表的 1 條括號路徑）。本輪新增的連結全部解析，包括追蹤表與 RELEASE_TRACKER 指向本檔的連結。
- **Secret 掃描模擬**：20 個 staged 檔案（不含本檔）新增行命中 **0** 筆，與 pre-commit 相同的 pattern 與排除規則；本檔另行掃描（見 commit 前的核對）。
- **程式碼**：沒有 `backend/`、`frontend/` 的變更；compose 與 `.env.example` 的變更見 §2.3。
- **compose 語法**：`docker compose -f docker-compose.yml config` 退出碼 **0**；以 JSON 渲染後檢查 backend 的環境變數：`SMTP_USERNAME` 為空、`SMTP_PORT` 為 `587`、`APP_FRONTEND_BASE_URL` 為 `http://localhost:3000`、`APP_SCHEDULING_ENABLED` 為 `true`、`JWT_REFRESH_TOKEN_EXPIRATION` 維持 `2592000000`（與補傳前一致）。**未實際 `docker compose up` 啟動容器**（未驗證）。
- **a6cd311 的雲端 CI**：run `37126315542`，`Local CI (act-compatible)` **success**，8 分 24 秒（2026-10-03 13:28:46 → 13:37:10 UTC）。
- **未驗證的項目（明示）**：文件的 SQL 與 DDL 沒有重新核對（`SRD_Database_Schema.md` 未改，受 `make validate-schema-doc` 守門）；M07 與通知的端點清單只核對到基底路徑與主要端點，未逐一實測。

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **文件沒有機械守門**：`ApiRouteDocDriftTest` 只守 API 路由；SRD、FRD、PRD、TC 的敘述與實作一致性靠人工核對（與 DEF-286 相關）。
2. **真實服務未驗證**：Stripe（Checkout、Webhook、退款）只有 Mock 與 WireMock 驗證；SMTP 未對真實 Google Workspace 伺服器實測；LINE Pay 是 stub。
3. **API 規格缺口**：M07（金流與結算）的 API 規格文件未建立（API_Index 標註「連結待建立」）；通知 API 未收錄於 API_Index（DEF-286 範圍）。
4. **結算狀態圖未文件化**：`SettlementStatement` 的狀態（8 值）流轉細節以程式為準，本輪只列出列舉。
5. **未逐項核對**：`SRD_Database_Schema.md` 的枚舉註解與 M07／通知的欄位細節。

### 6.2 待使用者決定（累積）

| 項目 | 內容 | 我的建議 | 對應 |
|------|------|----------|------|
| ✅ 訂房入住流程（已拍板，§2.3） | 依 PRD Phase 1：付款成功即等同確認；店家標記入住與退房；完成由退房後自動。排入 Sprint 245 實作 | — | DEF-345 |
| 上線時的 Stripe 開關 | 開關預設關閉，全新部署的 Mock 付款對所有買家開放 | 上線 runbook 明確開啟並驗證；另將正式環境的預設改為 fail-closed（未明確開啟時拒絕 Mock 付款）。需要使用者確認上線程序 | DEF-346、環境變數文件 §5、§6 |
| ✅ 已處理：compose 補傳環境變數 | 使用者授權補傳非機密設定（§2.3）；`OAUTH_*`、`STORAGE_*` 未授權 | — | DEF-322 |
| SMTP 非同步寄送 | 啟用 SMTP 前必須修 | 由 AI 獨立實作（專用執行緒池，寄送與回應脫鉤）並寫測試；需要使用者指示時機 | DEF-347 |
| MinIO 預設憑證 | `application.yml` 的 `minioadmin`／`minioadmin123` 在正式環境沒有覆寫 | 移除 yml 的預設值，缺少時啟動失敗（與 DEF-320 一致）；需要同步調整開發流程，所以需要使用者決定 | DEF-348 |
| LINE Pay | stub 與舊版端點的標籤 | 接 LINE Pay 之前處理；本輪不動 | DEF-349 |

### 6.3 本輪未做（有意的範圍外）

- 不改程式（§2.2）；訂房入住流程不在本輪實作（§2.3，排入 Sprint 245）。
- 不建立 M07 與通知的 API 規格（需要另排 Sprint；端點清單已列於 FRD §6B.6、§6C.6）。
- 不修 §1.6 的既有壞連結與 `application.yml` 過時註解（Rule 3）。

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. **commit 與 push**（已完成）：文件 commit `a6cd311` 已 push。收尾追加（compose、`.env.example`、本計畫書與追蹤表的回填）另成一個 commit，push 後其雲端 CI 結果於 Sprint 245 收尾回填。
2. **雲端 CI 回填**（`a6cd311`）：已回填（§5、RELEASE_TRACKER）。
3. **Sprint 245（已排定）**：DEF-345 訂房入住流程，依 PRD Phase 1（§2.3）。實作範圍：店家端的入住與退房端點與權限、`PAID → CHECKED_IN → CHECKED_OUT → COMPLETED` 的轉換規則與完成條件、M06 規格與 FRD §6A、SRD §6.3.4 的同步更新、單元與真實資料庫的整合測試、突變驗證。
4. **Sprint 246 候選**：DEF-347（寄信改非同步；在任何人設定 `SMTP_USERNAME` 之前完成）、DEF-346（上線 runbook 的開關步驟與驗證）。
5. **等使用者決定**：DEF-348（MinIO 預設憑證；移除 yml 預設值會影響開發流程）、OAuth 與儲存變數是否補傳 compose、DEF-349（LINE Pay，接 LINE Pay 前處理）。
6. **既有排程（不變）**：店鋪成員管理前端（DEF-321 (a)）→ CMS 卡片連結（DEF-321 (c)）→ 通知事件（DEF-318）→ 店鋪前台 `/stores`（DEF-321 (b)）；M01／M02 API 文件對齊（含 DEF-344、DEF-342）。
