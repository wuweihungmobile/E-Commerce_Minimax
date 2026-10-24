# Sprint 306 Plan — DEF-421／DEF-420／DEF-356 與手動腳本 M15（使用者全權委託：依最合理、最佳、最理想、最適當決策，不顧慮成本）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 305](SPRINT_305_PLAN.md) 總結，在四項「我需要你回答」（`DEF-421`、`DEF-420`、手動腳本 `M15_API_Automation_Test.sh`、`DEF-356`）旁都用 `==>` 回覆**同一句**：「請依照最合理化，最佳化，最理想，最適當的解決方幫我決策，若真需要我決定，請以最簡單的解釋，讓我互動選項決策！」，結尾再寫一次「以上全部」適用。執行中使用者又傳來兩則訊息：同一句加上「(不需要顧慮成本)」，以及單獨一句「不需要顧慮成本，請注意！」。

我的解讀（寫下來讓使用者能糾正）：

1. **全權委託**：四項由我定案，不逐項反問。
2. **「不顧慮成本」＝選最理想的做法，不是最省事的**。例如：`DEF-420` 原本只打算做「遇 429 重試」，改成「重試＋降低登入密度」兩層；M15 腳本在刪除前先實跑證明、逐案例對照、補齊覆蓋缺口；`DEF-356` 用真實 `docker compose` 堆疊（含建置開發版後端映像）驗證；`DEF-420` 先量改動前的基準再量改動後。
3. **「真需要我決定」＝只問被明文規定「AI 不得自行決定」的項目**：只有 `DEF-356`——`docker-compose.yml` 該掛載旁的註解（「AI 不得移除此掛載」）與 [DOCKER_POLICY.md](../08_deployment/DOCKER_POLICY.md)（「AI 禁止自行移除或保留此掛載，需人工確認後決策」）。我用**一題**互動選項問（建議項放第一、三個選項各一句白話說明），使用者本人選了「**改成只用 Flyway（建議）**」。其餘三項沒有這類限制，自己定案。

### 1.2 成功標準（開工時定義）

1. `DEF-421`：在 Flyway 實跑的資料庫裡由測試**自己種**一列列舉外的 `rule_type`，房源、商品、店鋪三種列表查詢都不得被拖垮；固件「會咬人」有前置斷言；六個分支各自退回 `findAll()` 的突變全擊殺；固件失效時有測試會紅。
2. `DEF-420`：直接打認證 API 的輔助函式遇 429 依 `Retry-After` 退避，並對**真實**的 429 實測自癒；降低登入密度；預設並行度的守門連續多次通過；守門自己印出限流次數、失敗時保留後端日誌。
3. M15 腳本：先用證據決定（實跑、查歷史、逐案例對照），不憑印象刪除；刪除前補齊對照不到的 HTTP 層缺口。
4. `DEF-356`：使用者拍板後，在真實 compose 堆疊（全新 volume）上驗證；舊資料庫的失敗行為實測並寫進文件。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「V94 讓 M15 腳本壞掉」（Sprint 305 計畫書 §8 第 3 點） | **不完整**。把腳本複本（報告與暫存檔導向暫存目錄）對 V94 乾淨資料庫的真實後端實跑：**第 1 步註冊就被拒**（`userType must be BUYER, SELLER, or HOST`，腳本送的是 `STORE_OWNER`），印出「無法取得 Token，測試終止」，exit 1，一個案例都沒跑到。`git log -S` 查 `RegisterRequest.userType` 的白名單：Sprint 10（`8f69f7c0`，2026-05-09）曾加入 `STORE_OWNER`，**Sprint 182（`cecfdad2`，2026-09-22，DEF-244～247 註冊租戶奪取修復）收窄回 `BUYER｜SELLER｜HOST`**——腳本自該日起就無法執行，比 V94 早一個多月。V94 刪掉 V16 的店鋪與房源只是它「就算註冊過了也會失敗」的另一個原因 |
| 「腳本的 24 個案例由自動化測試承接」 | 逐案例對照（§2.3，名稱層級）：**16 個**原本就有 HTTP 或真實後端層級的對應測試；**6 個**（TC-002 的空白標題、TC-007、TC-011、TC-015、TC-016、TC-024）原本只有單元層或完全沒有 HTTP 層斷言——其中 TC-002 只有「缺少 title」的測試，是我第一次對照時漏看、做第二遍逐案例讀碼才發現的——刪除前補了 **5 個** HTTP 層案例（IT-M15-023～027，§4）；**1 個**（TC-018 不支援的檔案類型）只有服務層單元測試，HTTP 層補不進 `PostControllerE2ETest`（§7 第 3 點）；**1 個**（TC-023）與 TC-021 的請求與斷言完全相同、沒有獨立內容 |
| 「dev 的 `make up` 全新 volume：initdb 第一支就失敗，之後呢？」（Sprint 305 只測到第一支失敗） | 用一次性容器、與 compose 相同的 `restart: unless-stopped` 實測：`V10__create_posts_table.sql` 因 `relation "tenants" does not exist` 失敗 → 容器退出 → Docker 重啟 → 日誌印 `PostgreSQL Database directory appears to contain a database; Skipping initialization` → 資料庫 `nextkeytest` 存在但 **0 張表** |
| 「Sprint 248 記載的『全新 volume 建了 59／~91 張表』與實測不一致，原因未查明」（`DEF-356` 原記載） | **部分解開**：用舊設定（Flyway 關閉、`ddl-auto=update`）起後端，空資料庫被 Hibernate 建出**恰好 59 張表**、沒有 `flyway_schema_history`——Sprint 248 看到的 59 很可能就是這個，不是 initdb 的成果。Sprint 248 當時記載的『多數測試資料也建立成功』這部分，這個解釋**涵蓋不了**（Hibernate 不會建資料），仍未查明 |
| 「新設定遇到用舊設定建的開發資料庫會怎樣？」（`baseline-on-migrate: true`、`baseline-version: '0'`） | 實測：Flyway 建立歷史表並寫入 `<< Flyway Baseline >>`（版本 0），隨後 `Migration of schema "public" to version "1 - Initial Schema" failed! … ERROR: relation "tenants" already exists`，約 18 秒後程序結束——**大聲失敗**，不會悄悄套用。失敗的啟動會留下只有基準列的歷史表（`make down-clean` 會一併清掉） |
| 「登入 429 的真實回應長什麼樣？」 | 對真實後端並行連打 45 次登入：5 個 200、**15 個 429**、25 個 401（日誌是 `E-1004 帳號已被鎖定: Too many failed login attempts`，同一帳號並行連打觸發的 per-account 鎖定，與 429 重試無關）。429 帶 `Retry-After: 2`、`Content-Type: application/json;charset=UTF-8`、本文 `{"success":false,"code":"E-9904","message":"已超過速率限制",…}` |
| 「`findAll()` 全表載入只剩 `DEF-416` 那一處嗎？」 | 掃描主程式碼（排除 Repository 介面）：還有 **3 處**——`NotificationService.broadcastNotification`、`NotificationProducerService.broadcastToQueue`（不帶租戶時對全部使用者廣播）、`AdminService.getPlatformStats`（`tenantRepository.findAll().stream().filter(ACTIVE).count()`）。已登記 `DEF-422`，沒有分析影響、沒有修 |

## 2. 決策與設計

### 2.1 `DEF-421` → 選項 (3)：Flyway 型後端整合測試

`PricingRulesCorruptRowFlywayIntegrationTest`（`backend/src/test/java/.../integration/`）：

- **資料庫**：每次執行用 `@DynamicPropertySource` 先 `DROP … WITH (FORCE)` 再 `CREATE DATABASE s306_pricing_corrupt_row`，讓 Spring 啟動時由 Flyway 從 V1 實跑到最新版（本次 94 支）、`ddl-auto=validate`；之後完全走真實 HTTP／JWT／權限鏈（`MockMvc`＋真實 `JwtTokenService`）。資料庫執行完留在測試用 PostgreSQL（Spring 的 context 快取仍持有連線，無法在類別結束時刪除）；容器停止時一併消失，下次執行會重建。
- **固件**：一間店鋪（店主、一間房源、一個商品、各一條合法規則）＋另一間店鋪名下**直接以 SQL 寫入**的一列 `rule_type = 'WEEKEND_SURCHARGE'`（不在 `PricingRuleType` 內；Flyway 建的 `pricing_rules.rule_type` 只是 `VARCHAR(20)`，沒有 CHECK）。
- **前置斷言**：`fixture_corruptRowMakesFullTableScanFail`——`findAll()` 在這個資料庫必須丟出含 `WEEKEND_SURCHARGE` 的例外。這是 `DEF-421` 的教訓（護欄失去觸發條件卻仍然是綠的）：若將來列舉映射改成能容忍未知值，這支測試會在這裡紅，提醒護欄失效，而不是靜靜變成永遠通過。
- **三種列表**（每種都測預設與 `activeOnly=true`）：依房源、依商品、店鋪層級。範圍比原本的 `DEF-416`（只有房源分支）寬，因為三個分支都有同樣的「改成 `findAll()` 就被一列壞資料拖垮」的暴露面，成本只是多幾次 HTTP 呼叫。
- 為什麼不選 (1)（E2E 自己種壞資料列）：規格沒有 DB 連線，gate 與雲端的連線方式不同（本機對映的 55432／服務的 5432），要新增一層 E2E 專用的 SQL 通道；而後端整合測試同一個模式（`TestSeedRemovalMigrationIntegrationTest`）已在本機與雲端 CI 實證可跑。為什麼不選 (2)（只留單元測試）：單元測試用 mock 的 `findAll()` 回空 List 擋「不得全表掃描」的意圖，但擋不住其他寫法的同型回歸，也沒有證明「壞資料列真的會咬人」。

### 2.2 `DEF-420` → 三層，不改 gate 的並行度、不動正式環境限流

| 層 | 做法 | 為什麼 |
|---|---|---|
| (1) 遇 429 退避重試 | `helpers/auth.ts` 新增 `postAuthApi(request, url, data)`：回 429 就依 `Retry-After`（缺漏或無法解析用 2.5 秒）等待後重試，最多 8 次，用盡就丟明確的錯誤；其他狀態碼（含 401、409）原樣回傳。**所有**直接打登入／註冊 API 的 7 個呼叫點（5 個檔案）都改走它 | 根因是「可恢復的 429 變成硬失敗」（Sprint 304、305 預設並行度失敗的就是這個）。UI 層的 `submitLogin`／`submitRegister` 早就有重試，API 層沒有 |
| (2) 管理員 token 快取 | `adminAccessToken` 在每個 worker 內快取 10 分鐘（access token 15 分鐘，來自 `application.yml`；登出與換發偵測只撤銷 refresh token，不會讓已簽發的 access token 失效）。`seedStore`（全套件 26 次呼叫）與 `at-booking-payment-real` 自己那份播種流程改用它，**省下每次一次管理員 UI 登入** | 重試只是「撞到才補救」；降低需求才是讓套件離限流容量遠一點。UI 登入還要走頁面、雜湊成本 12 |
| (3) 守門自己看得見 | `scripts/validate-e2e.sh`：Playwright 結束後（刪除後端日誌之前）印出「認證端點被限流 N 次」；E2E 失敗時把後端日誌另存 `frontend/test-results/e2e-gate-backend.log`（已被 git 忽略） | Sprint 289 就記載「守門不會告訴你重試路徑有沒有被走到」；Sprint 299、305 都因為沒搶救到日誌而量不到數字（Sprint 300 靠另起的複製迴圈保住了日誌） |

**刻意不做**：把 gate 預設並行度改成 1（多約 2 分鐘，且只是迴避問題，雲端 CI 的並行度本來就是 1）；調高正式環境登入限流（Sprint 168 的決定）。

重試輔助函式自己也有守門：`at-auth-api-retry.spec.ts` 用本機假伺服器確定地走過每條路徑（見 §4）。理由是歷史教訓：UI 層的重試曾從 Sprint 234 起對真實 429 悄悄不生效，到 Sprint 288 才被量到（DEF-396）。

### 2.3 M15 腳本 → 刪除（證據鏈）

1. **它已經跑不起來**，而且與 V94 無關（§1.3 第一列）。
2. **改成「自己建資料」不可行**：要讓腳本取得有店鋪的店主 token，得在 bash 裡重做「店主註冊→讀後端日誌驗證 Email→申請開店→管理員核准→重新登入」整套流程——那就是 Playwright 的 `seedStore` 在做的事，而且那一套已經有真實後端規格（`AT-M15-REAL` 等）每次守門都在跑。
3. **它還有其他缺陷**：`TC-M15-012` 標題寫「跨 Tenant 權限驗證」，實際只是送一個假 token 看 401／403；`TC-M15-023` 與 `TC-M15-021` 的請求與斷言完全相同；檔頭寫 `set -e` 卻註解「允許任何錯誤時繼續執行」；使用說明寫 `./run_api_tests.sh`（檔名不符）；執行時會改寫被追蹤的 `docs/03_testing/TR_M15_API_Results.md` 並在 `/tmp` 留檔。
4. **沒有任何 CI、Makefile 或文件把它當作流程引用**（只有追蹤表與計畫書提到它）。

逐案例對照（**名稱層級的對照，不是執行等價的證明**；粗體是本輪新增的案例）：

| 腳本案例 | 對應的自動化測試 |
|---|---|
| TC-001 建立草稿貼文 | `PostControllerE2ETest` IT-M15-001；`PostServiceTest.createPost_success` |
| TC-002 空白標題 → 400 | IT-M15-002 只涵蓋**缺少** title；空字串與只有空白由 **IT-M15-027** 涵蓋（服務層條件是 `null \|\| trim().isEmpty()`，後半原本沒有測試） |
| TC-003 有效 Embed／TC-004 重複 Embed | IT-M15-003／004／005（400 `E-4104`）；`PostServiceTest.createPost_duplicateEmbed_throwsE4104` |
| TC-005 更新貼文 | `PostServiceTest.updatePost_*`；`AT-M15-REAL` 的 E2E-M15-002（真實後端編輯並儲存） |
| TC-006 發布 | IT-M15-007 |
| TC-007 下架 | `PostServiceTest.unpublishPost_*`（單元）；HTTP 路由與回到草稿由 **IT-M15-026** 涵蓋 |
| TC-008 刪草稿／TC-009 刪已發布 | `PostServiceTest.deletePost_draftSuccess`；IT-M15-008 |
| TC-010 取得詳情／TC-011 無效貼文 → 404 | `PostServiceTest.getPost_success`；`AT-M15-REAL` E2E-M15-002（從列表進入編輯頁讀取詳情）；404 `E-4100` 由 **IT-M15-023** 涵蓋 |
| TC-012 「跨 Tenant」（實為假 token） | IT-M15-016（他店貼文 403 `E-4031`）；`Bearer invalid.jwt.token` → 401 見 Product／Order／Booking／Cart／Auth 的 `*ControllerE2ETest` |
| TC-013 已發布列表／TC-014 依 slug | IT-M15-013／014 |
| TC-015 無效 slug → 404／TC-016 草稿 slug → 404 | `PostServiceTest.getPublishedPostBySlug_draftPost_throwsE4101`（單元）；HTTP 的 404 `E-4100`／`E-4101` 由 **IT-M15-024** 涵蓋 |
| TC-017 媒體上傳 | IT-M15-010／010b；`M18MediaIntegrationTest` IT-M18-006／014 |
| TC-018 檔案類型驗證 → 400 | `MediaUploadServiceTest.uploadMedia_pathBased_unsupportedType_throwsException`（路徑版上傳的服務層）、`MediaValidationServiceTest.validateMimeType_unsupported_throwsException` 等（單元）。**沒有 HTTP 層案例**：`IntegrationTestConfiguration` 把 `MediaService` 換成回傳罐頭資料的 mock，在 `PostControllerE2ETest` 打 `upload-by-path` 看不到真實驗證（§7 第 3 點） |
| TC-019 列出媒體／TC-020 刪除媒體 | IT-M15-011／012；IT-M18-005／009 |
| TC-021 商品卡片／TC-022 房型卡片 | IT-M15-019／020；`ListingCardServiceTest` |
| TC-023 「卡片可用性驗證」 | 腳本內與 TC-021 完全相同，沒有獨立內容；可用性（下架、維護中）由 IT-M15-017／020 與 `ListingCardServiceTest` 涵蓋 |
| TC-024 無效 Listing → 404 | 原本沒有；404 `E-3000` 由 **IT-M15-025** 涵蓋 |

### 2.4 `DEF-356` → 使用者選「改成只用 Flyway（建議）」

- `docker-compose.yml`：移除 postgres 的 `docker-entrypoint-initdb.d` 掛載，註解改為記錄原因與決定。
- `docker-compose.override.yml`：開發後端 `SPRING_FLYWAY_ENABLED=true`、`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`（原本是關閉 Flyway＋`update`）。兩個值現在與 base compose 的預設相同。
- [DOCKER_POLICY.md](../08_deployment/DOCKER_POLICY.md)：「已知風險」小節改寫為「已處理」，記錄實測、決定、後果與「不得再把遷移目錄掛進 initdb.d」。
- `README.md`：帳號段落改寫；明確寫出「舊的開發資料庫要先 `make down-clean`」與實測的失敗訊息。`scripts/sql/seed-e2e-admin.sql`：註解更新。
- **不改**：`application.yml` 的 `baseline-on-migrate: true`／`baseline-version: '0'`（它現在只會在「有表但沒有歷史表」的資料庫發揮作用，實測的結果是大聲失敗，見 §1.3；是否改成 `false` 是正式環境行為，不在本題範圍）；`V94` 標頭註解中「dev 的 initdb 路徑」的敘述（改動已套用過的遷移檔會改變 checksum，該句現在只是歷史說明）。
- **驗證**（在隔離的暫存複本上跑實際的 compose 檔，專案名稱 `probe306`，不碰使用者的 `e-commerce_minimax_*` 卷與 `backend/target`）：postgres 15 秒內 healthy、**0 次重啟**、0 張表、日誌印 `ignoring /docker-entrypoint-initdb.d/*`；建置開發版後端映像並啟動（`SPRING_PROFILES_ACTIVE=dev`）：`Successfully applied 94 migrations to schema "public", now at version v94`、`Started EcommerceApplication in 42.7 seconds`、healthy；資料庫 `flyway_schema_history` 94 筆全成功、使用者只有 `admin@nextkey.local`、租戶只有系統租戶、65 張業務表；`Test123!` 登入 admin **401**，執行 `make dev-admin-password` 後 **200**。

## 3. 刻意沒做的事

1. **不改 gate 的預設並行度**、**不動正式環境登入限流**（§2.2）。
2. **不改 `application.yml` 的 `baseline-on-migrate`**、**不改 `V94` 檔案**（§2.4）。
3. **不處理 `DEF-422`（另外 3 處 `findAll()`）**：只掃描、登記；沒有重現、沒有量測影響。
4. **不替使用者刪除他現有的開發資料庫**：`make down-clean` 由使用者自己執行（會刪除 `e-commerce_minimax_postgres_data`）。

## 4. 測試

| 新增的測試 | 案例數 | 它守住什麼 |
|---|---|---|
| `PricingRulesCorruptRowFlywayIntegrationTest`（`backend/src/test/java/.../integration/`，Flyway 實跑 V1～V94 的獨立資料庫，單跑 49 秒） | 4 | `fixture_corruptRowMakesFullTableScanFail`：固件咬人（`findAll()` 讀這個資料庫會丟含 `WEEKEND_SURCHARGE` 的例外）；`listByRoomListing_…`、`listByListing_…`、`listForTenant_…`：店主走真實 HTTP 列規則，各自（預設與 `activeOnly=true`）都回 200 且只含自己的規則，不被另一間店鋪的壞資料列拖垮 |
| `frontend/e2e/at-auth-api-retry.spec.ts`（本機假伺服器，不需要後端，單跑 13.5 秒） | 4 | `E2E-RETRY-01`：429 帶 `Retry-After: 3` → 兩次請求間隔 ≥ 2.9 秒（分辨「依標頭」與「預設 2.5 秒」）；`02`：沒有標頭 → ≥ 2.4 秒；`03`：持續 429 → 共 9 次請求後丟出明確錯誤；`04`：200 與 401 只送一次、原樣回傳。間隔只斷言下限（負載再高等待只會更久，不會更短） |
| `PostControllerE2ETest` 新增 IT-M15-023～027（24 → 29 案例） | 5 | 023：不存在的貼文 → 404 `E-4100`；024：不存在的 slug → 404 `E-4100`、草稿的 slug → 404 `E-4101`；025：不存在的卡片 → 404 `E-3000`；026：下架路由 → 200 並回到草稿，前台以 slug 取得變 404 `E-4101`；027：空字串與只有空白的 title → 400 `E-9005` |

既有測試只改註解（`PricingServiceTest.RuleListingQueryTests` 的護欄敘述、`at-pricing-rules-real.spec.ts` 的兩處 V94 說明）。E2E 輔助函式與 5 個規格檔改走 `postAuthApi`、快取的 `adminAccessToken`，行為不變、只少了登入。

## 5. 突變驗證

共 **23 個突變，全數擊殺**，每個都還原並核對（Java 檔 `sha256` 與 `git diff -- backend/src/main` 為空；前端檔 `sha256`）。每一組都先跑一個**不突變的基準（Z0）**，證明新案例在正式程式碼上本來就綠。驅動沿用 Sprint 242/291 的做法（備份 → 套用，`old` 必須恰好出現一次 → 只跑目標類別 → 還原）。

### 5.1 `PricingRulesCorruptRowFlywayIntegrationTest`（8 個）

| 突變 | 擊殺者（失敗訊息） |
|---|---|
| `M1` 房源分支退回 `findAll()` 全表掃描再過濾（`DEF-416` 的原始寫法） | `listByRoomListing_…`（`Status expected:<200> but was:<500>`） |
| `M2` 房源分支 `activeOnly=true` 改成 `findAll()` 過濾 | 同上（同一個案例的第二個斷言） |
| `M3`／`M4` 商品分支（預設／`activeOnly`）改成 `findAll()` 過濾 | `listByListing_…`（500） |
| `M5`／`M6` 店鋪層級（預設／`activeOnly`）改成 `findAll()` 過濾 | `listForTenant_…`（500） |
| `F1` 固件：壞資料列改成合法的 `rule_type`（觸發條件消失） | **只有** `fixture_…`（`Expecting code to raise a throwable`） |
| `F2` = `F1` ＋ `M1`（觸發條件消失**且**真的退回 `findAll()`） | **只有** `fixture_…`；三個列表案例仍然全綠 |

`F2` 是這支測試存在的理由：它重現 `DEF-421` 的失效模式——回歸已經發生，三個列表案例卻因為沒有壞資料列而繼續通過；只有前置斷言會紅。

### 5.2 `postAuthApi`（`at-auth-api-retry.spec.ts`，8 個）

| 突變 | 擊殺者 |
|---|---|
| `H1` 拿掉重試（任何回應直接回傳） | `01`、`02`、`03` |
| `H2` 忽略 `Retry-After`（一律用預設 2.5 秒） | `01` |
| `H3` 沒有標頭時不等待 | `02` |
| `H4a`／`H4b` 重試次數多一次／少一次 | `03`（9 次請求變 10／8 次） |
| `H5` 重試用盡後吞掉錯誤、回傳 429 | `03` |
| `H6` 對任何 ≥ 400 都重試（連 401 也重試） | `04` |
| `H7` 等待改成 0 毫秒 | `01`、`02` |

### 5.3 `PostControllerE2ETest` 新增案例（7 個）

| 突變 | 擊殺者 |
|---|---|
| `N1` 下架路由改名（`…/publish-disabled`） | IT-M15-026 |
| `N2` 貼文找不到改丟 `E_4106`（會變 409） | IT-M15-023 |
| `N3` 依 slug 找不到改丟 `E_4101` | IT-M15-024 |
| `N4` 草稿對前台可見（拿掉未發布檢查） | IT-M15-024、IT-M15-026 |
| `N5` 卡片找不到改丟 `E_3001`（會變 422） | IT-M15-025 |
| `N6` 下架不改狀態 | IT-M15-026 |
| `N7` 標題檢查拿掉 `trim().isEmpty()`（只剩 `null`） | IT-M15-027 |

### 5.4 誠實註記

- 各組失敗集合**與事先登記的預期完全一致**（多一個或少一個都會判 `MISMATCH`），所以每個突變歸因到單一的新案例，不是「紅了就算」。唯一一次出現 `MISMATCH` 是基準（Z0）就紅了——那是無效的 `IT-M15-028`（§7 第 3 點），移除後重跑一致。
- 突變只對**主程式碼或輔助函式**做，不對新測試本身做突變；測試強度靠上述突變與「基準先綠」間接驗證。`N1`～`N7` 只跑目標整合類別，沒有跑單元測試層，所以「擊殺」指的是新的 HTTP 案例；其中有些（例如 `N6`）既有單元測試也會擊殺。
- `adminAccessToken` 的快取沒有被突變驗證（沒有直接測它的案例）；它的效果以後端日誌的請求數量測（§6）。
- 第一次驅動每步都等 IDE 的 Java 語言服務預編 class 至 60 秒，而它這次根本沒在預編，每個突變白等約 2 分鐘；之後的驅動把等待上限降為 12 秒（Maven 自己編）。

## 6. 驗證總表

| 項目 | 結果 |
|---|---|
| 新 Flyway 整合測試 | 4／4 通過；後端日誌 `Successfully applied 94 migrations to schema "public", now at version v94`；事後以 SQL 確認資料庫有 2 列合法規則、1 列 `WEEKEND_SURCHARGE` |
| `at-auth-api-retry.spec.ts` | 4／4 通過（13.5 秒）；已在 4 次改動後的守門中隨套件執行，皆通過 |
| **真實 429 探針**（臨時全棧，探針規格未進 repo） | 並行連打 45 次登入：5 個 200、15 個 429、25 個 401（`E-1004` 帳號鎖定，同帳號並行連打所致）。429 帶 `Retry-After: 2`、`application/json;charset=UTF-8`、本文 `E-9904`。**原始 `request.post` 在桶耗盡後立刻再打一次 → 429（舊寫法的硬失敗）；`postAuthApi` 在同樣狀態下重試 1 次（等 1000 毫秒），共 1.4 秒後拿到 200**。後端日誌 17 筆 `exceeded rate limit` 與預期吻合（15 ＋ 原始 1 ＋ `postAuthApi` 第一次 1） |
| `PostControllerE2ETest` | 29／29 通過（24 ＋ 新增 5） |
| M15 腳本實跑（暫存複本） | 第 1 步註冊被拒，exit 1（§1.3） |
| `DEF-356` 真實 compose 驗證 | postgres 15 秒內 healthy、0 次重啟、0 張表；開發版後端 Flyway 套用 94 支遷移、`Started … in 42.7 seconds`、healthy；`flyway_schema_history` 94 筆全成功；`Test123!` 登入 admin 401 → `make dev-admin-password` 後 200。舊資料庫模擬：59 張表、沒有歷史表 → 新設定啟動在 V1 失敗（`relation "tenants" already exists`） |
| 全量後端 `mvn -o clean verify`（`make test-db-down`＋`up` 重建的全新測試 DB） | `BUILD SUCCESS`（19 分 28 秒）；Checkstyle main／test 皆 0 違規；**單元 2361、整合 933**，皆 0 失敗／0 錯誤／0 跳過，日誌中 `<<< FAILURE`／`<<< ERROR` 標記 0 個。與 Sprint 305（2361／924）相比單元不變、整合 +9，正好等於本輪新增的測試數（Flyway 整合測試 4 ＋ `PostControllerE2ETest` 新增 5）；兩者在全量中分別是 4／4（12.3 秒）與 29／29（33.5 秒） |
| `make validate-schema-doc` | 通過：套用 94 個 Flyway 遷移後，SRD 的 DDL 與 PRD §8.2 欄位清單均與實際 schema 一致（含 §2.6 涵蓋率守門） |
| 標準指令 `make validate-e2e`（完整建置：JAR＋`npm ci`＋`next build`，預設並行度 2 個 worker） | **282 案例，278 通過／4 跳過／0 失敗**（6.7 分鐘），閘門『本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）』；守門新增的一行印出『後端記錄到認證端點被限流 0 次』；後端日誌登入請求 199、註冊 91。連同 §6.1 的另外 3 次改動後守門（第 2、3、4 列），改動後共 4 次、全數 0 失敗 |
| pre-commit、pre-push、雲端 CI | 皆通過：pre-commit 一次通過、pre-push 輕量守門通過、雲端 run 40062709075 全綠（整合測試 933／0 失敗，下載完整日誌確認），細節與證據見 §10 |

### 6.1 `DEF-420` 的守門對照（同一台機器，同一份 JAR 與前端建置，`E2E_GATE_SKIP_BUILD=1`）

「改動前」＝把 `frontend/e2e` 暫時換成 HEAD 版本（備份、`sha256` 清單逐檔比對還原）；「預先打空」＝Playwright 開跑前先對不存在的帳號並行登入 45 次，把登入限流桶打空（衍生腳本，與 `scripts/validate-e2e.sh` 只差三處）。後端日誌的請求數來自守門執行期間複製出的日誌。

| # | 程式碼 | 條件 | 結果 | 登入請求 | 註冊請求 | 登入 429 |
|---|---|---|---|---|---|---|
| 1 | 改動前 | 預設並行度 | 274 通過／4 跳過／**0 失敗**（8.9 分鐘） | 228 | 91 | 0 |
| 2 | 改動後 | 預設並行度 | 278 通過／4 跳過／**0 失敗**（7.0 分鐘） | 201 | 91 | 0 |
| 3 | 改動後 | 4 個 worker | 278／4／0（6.8 分鐘） | 203 | 91 | 0 |
| 4 | 改動後 | 預設並行度＋預先打空 | 278／4／**0 失敗**（6.6 分鐘） | 230（含預先打空 30 次進入控制器） | 91 | 20（**15 次是預先打空本身，其餘 5 次發生在最後一分鐘、全部自癒**） |
| 5 | **改動前** | 預設並行度＋預先打空 | 268 通過／**2 失敗**／4 跳過／4 未執行（7.4 分鐘） | 252（含預先打空 30 次） | 89 | 63（15 次是預先打空本身） |

- 第 5 列的 2 個失敗是 `at-store-less-seller-real` 的 `E2E-SLS-01`（`loginViaApi`，第 36 行）與 `at-store-suspended-payment-real` 的 `E2E-SSP-01`（`adminAccessToken`，第 71 行），錯誤都是登入 API 回 **429 `E-9904`**、`expect(status).toBe(200)` 收到 429——與 Sprint 304、305 反覆出現的是**同兩支規格、同一個症狀**。後端日誌被守門另存到 `frontend/test-results/e2e-gate-backend.log`（新增的功能，實際生效）。
- 第 4 列最後一分鐘有 40 次登入，超過每分鐘 30 次的補充速率——這是 Sprint 304 記載的『尾端擠在一起』；改動後撞到 5 次、全部由重試自癒，沒有任何失敗。
- 套件案例數：改動前 278、改動後 282（+4 ＝ `at-auth-api-retry.spec.ts`）。
- **登入密度**：同一份套件的登入請求 **228 → 201（−27，−11.8%）**，註冊 91 不變。差 27 與預期吻合：`seedStore`（含 `at-booking-payment-real` 自己那份播種流程）共 26 次管理員 UI 登入被拿掉；原本另有 3 個規格各自以 API 登入管理員一次，現在每個 worker 只登入一次並共用。（這個拆分是依程式推算，沒有逐筆核對日誌。）
- 第 3 列用 4 個 worker 只比 2 個 worker 快 0.2 分鐘（這台只有 4 個核心），並沒有比較大的登入壓力——它不是有效的加壓，這也是我另外設計「預先打空」的原因。

## 7. 過程中的失誤與修正

1. **第一次對照 M15 腳本時漏掉兩個缺口**：TC-002 的空白標題（既有測試只涵蓋『缺少 title』，服務層條件是 `null || trim().isEmpty()`，後半沒有測試）、TC-018 的不支援類型。第二遍逐案例讀碼才發現（§1.3）。
2. **算術**：第一版計畫書寫「19＋5＋2」，重算後是 16＋6＋1＋1＝24（§1.3）。
3. **`IT-M15-028` 是無效測試**：我為 TC-018 寫了 HTTP 層案例，**基準（不突變）就失敗**——`upload-by-path` 回 200、回應本文 `"id":null`。原因是 `IntegrationTestConfiguration` 把 `MediaService` 換成回傳罐頭資料的 mock，這個 E2E 類別看不到真實的驗證。我第一次跑的是「基準＋突變」同一批，`N8`（把驗證拿掉）顯示『擊殺』，但那只是因為這個案例在任何情況下都紅——**先看基準那一列才識破**。已移除該案例，TC-018 改以 `MediaUploadServiceTest.uploadMedia_pathBased_unsupportedType_throwsException` 為準，並更正計畫書與測試檔內的敘述。
4. **開發版後端第一次啟動失敗，且那次什麼也沒證明**：隔離複本沒有 `.env`，`JWT_SECRET` 是 compose 的預設佔位值，後端依既有設計 fail-fast（`JwtTokenService` 建構子丟例外），容器反覆重啟（我檢查時已 31 次以上）。這發生在 Tomcat 啟動階段，**Flyway 根本沒跑到**。我讀容器日誌才發現，補上假的 `JWT_SECRET`（只存在於暫存目錄的 `.env`，沒有複製使用者的 `.env`）後重建，第二次才是有效的驗證。
5. **我在包裝腳本還在執行時修改了它**（為了加 `GATE_CMD`）：bash 邊讀邊執行，檔案位移讓守門結束後的統計段落出現語法錯誤、複製日誌的背景迴圈沒被收掉。守門本身不受影響；我手動從已複製的日誌重算數字（與腳本邏輯相同的 `grep`），並收掉殘留的迴圈。往後改腳本一律等執行結束。
6. **預先打空的衍生腳本第一版用了不帶參數的 `wait`**：它會等所有背景工作，包括 backend 與 frontend 這兩個長駐程序，守門會永遠卡住。執行前讀自己的腳本時發現，改成只等 `curl` 的 PID。
7. **突變驅動白等**：見 §5.4。
8. **`RATE_LIMIT_MAX_WAIT_SECONDS`（等待上限）寫了又拿掉**：測試本身的逾時已經限制了等待，而這個分支沒有測試（Rule 2）。
9. **`PASSWORD` 常數**：改成快取的管理員 token 後，`at-booking-payment-real.spec.ts` 的 `PASSWORD` 沒人用了，ESLint（`--max-warnings 0`）擋下，已移除。
10. **zsh 小坑**：`echo =====` 被當成指令替換、未加引號的 `$變數 "字串"` 不會拆字，各造成一次『找不到指令』的假輸出；因為輸出可見，當場識破並重跑。
11. **我先前在 Sprint 305 計畫書寫的『V94 讓 M15 腳本壞掉』不完整**：腳本在 Sprint 182 就已經因註冊白名單收窄而無法執行（§1.3）。Sprint 305 計畫書屬歷史紀錄，未改寫，以本計畫書更正。

## 8. 限制（誠實揭露）與同型缺口

1. **雲端 e2e job（只能手動觸發）沒有實跑**：本輪沒有改 `ci.yml`；E2E 輔助函式的變更只在本機守門驗證過。雲端 CI 的三個 job 不含 Playwright。
2. **`DEF-420` 的守門證據是小樣本**：改動前『未加壓』的基準守門這次也通過（8.9 分鐘、0 次限流），所以 Sprint 304／305 記載的『近 4 次失敗 3 次』是間歇性的，我**沒有**在未加壓的條件下重現它；對照實驗（第 4、5 列）是我人為把限流桶打空，各只有 1 次，不是統計結論。它能說的是：機制（真實 429 探針）、症狀（同兩支規格同一個錯誤）與修復後的行為（尾端 5 次限流全部自癒）三者一致。
3. **登入請求數是『抵達控制器』的請求**：被限流過濾器擋下的 429 不會進控制器，不計入；複製日誌的迴圈每 5 秒複製一次，最後不到 5 秒的內容可能缺漏。同一種量法用在改動前後，比較仍然公平，但絕對值略低估。
4. **`adminAccessToken` 快取沒有直接測試也沒有突變驗證**（§5.4）。
5. **開發堆疊只驗證了 postgres、redis、後端**：前端開發容器（`node` 映像、熱更新）沒有建置或啟動；驗證用的是隔離複本、假的 `JWT_SECRET`；『舊資料庫』的模擬是用主機上的 JAR 套用與 override 相同的環境值，不是在容器裡。
6. **使用者現有的開發資料庫沒有被碰**：我沒有執行 `make down-clean`（它會刪除 `e-commerce_minimax_postgres_data`）。使用者拉到這次改動後，第一次 `make up` 會失敗，直到執行一次 `make down-clean`；README 與 compose 註解已寫明，失敗訊息是實測的。
7. **TC-M15-018 沒有 HTTP 層案例**（§7 第 3 點）：路徑版上傳的驗證由服務層單元測試守住，HTTP 狀態碼的對應（`E_9000` → 400）靠共用的對應表；沒有一個真實服務的端到端案例。
8. **`DEF-422`（另外 3 處 `findAll()`）只掃描、未分析**（§1.3）。
9. **`application.yml` 的 `baseline-on-migrate: true`／`baseline-version: '0'` 沒有改**：現在它對『有表但沒有歷史表』的資料庫會先建立基準列才失敗（實測），失敗後歷史表留下一列基準；正式環境是否該改成 `false` 是另一個決定。
10. **`PricingRulesCorruptRowFlywayIntegrationTest` 的資料庫留在測試用 PostgreSQL**（下次執行重建，容器停止時消失）。
11. **同型風險**：任何『靠遷移留下的壞資料列當觸發條件』的測試，現在都應該改成自己種固件並有前置斷言；我用關鍵字掃描只找到 `DEF-416` 這一個（Sprint 305），沒有逐支閱讀所有規格。

## 9. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | `DEF-421` 選 (3)，且範圍涵蓋房源、商品、店鋪三種列表 | 與 V94 測試同一模式；三個分支暴露面相同，多測成本很低 | 告訴我只留房源分支，或改選 (1)／(2) |
| 2 | `DEF-420` 做重試＋降低密度，**不**改 gate 預設並行度、**不**動正式環境限流；註冊 API 呼叫也一併改走 `postAuthApi` | 治本又不拖慢守門；註冊端點有同樣的獨立限流桶、同樣沒有重試 | 告訴我改成 `workers=1`，或只改登入呼叫點 |
| 3 | 管理員 token 快取 10 分鐘 | access token 15 分鐘，留 5 分鐘餘裕；登出不會讓它失效 | 把 `ADMIN_TOKEN_MAX_AGE_MS` 改小，或移除快取 |
| 4 | 刪除 M15 腳本（不是改成自己建資料） | §2.3：已無法執行、自建資料等於重寫 Playwright 的 `seedStore`、案例由自動化測試承接 | 從 git 歷史還原該檔案（`git checkout <本輪 commit>^ -- backend/src/test/java/com/nextkey/ecommerce/api/M15_API_Automation_Test.sh`） |
| 5 | 刪除前補了 5 個 HTTP 層案例（IT-M15-023～027） | 『不憑印象刪除』：刪除要求每個案例都對得到會在 CI 跑的測試 | 刪掉這些案例 |
| 6 | `DEF-356` 選項由使用者選；我另外決定**不改** `baseline-on-migrate`、**不改** `V94` 的標頭註解 | 前者是正式環境行為、後者會改變已套用遷移的 checksum | 告訴我要改 |
| 7 | 修改 `scripts/validate-e2e.sh`（印出限流次數、失敗時保留日誌） | 補上 Sprint 289 記載的盲點；只新增輸出與一個檔案，不改通過／失敗判定 | 刪掉該段落 |
| 8 | 登記 `DEF-422` | 掃描結果不該只留在計畫書 | 刪掉該列 |

## 10. 雲端 CI 與狀態

commit `01edcea1` 已 push（`e21a06bb..01edcea1  main -> main`；同一次 push 也帶出 Sprint 305 的回填 commit `bb206f7f`）；`git fetch` 後本機 `HEAD` 與 `origin/main` 同為 `01edcea122cf6740aaed20b1f4ac41d704385498`，ahead／behind 為 0／0。

- **pre-commit**：一次通過——Checkstyle、Maven compile、核心層快速測試、ESLint、TypeScript 型別檢查、secret 掃描（commit 前以同一個指令序列 `docker ps | grep -c nk-test && git commit` 確認測試 DB 容器在；沒有繞過 hook）。本輪沒有動到遷移或 SRD／PRD，所以 schema 文件漂移檢查沒有被觸發。
- **pre-push 輕量守門**通過：本機 act 的 Backend Unit Tests（日誌印出兩輪，皆 `Job succeeded`）、Frontend Lint & Build `Job succeeded`；schema 漂移守門「backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移」；日誌中失敗標記 0 個；push 之後 `docker ps -a` 為空。
- **雲端 CI** run [40062709075](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40062709075)：`completed／success`，三個 job 皆 success，約 12 分 33 秒（04:31:57Z～04:44:30Z）；`gh run view --json` 的 `headSha` 為 `01edcea1`。
  - 下載了「Backend Integration Tests & Package」job 的**完整**雲端日誌（53,601,280 位元組、205,742 行；下載結束、stderr 為空、檔尾是 job 收尾才統計。第一次下載失敗是因為 `gh api` 拒絕輸出含終端跳脫序列的內容，加 `--allow-escape-sequences` 後重下）：failsafe 彙總 **`Tests run: 933, Failures: 0, Errors: 0, Skipped: 0`**（與本機整合測試 933 一致），`BUILD SUCCESS`，`<<< FAILURE`／`<<< ERROR` 標記 0 個；**`PricingRulesCorruptRowFlywayIntegrationTest` 4／4**（雲端 GitHub service 的全新 Postgres 上建立 `s306_pricing_corrupt_row` 並由 Flyway 實跑）、**`PostControllerE2ETest` 29／29**、`TestSeedRemovalMigrationIntegrationTest` 9／9。
  - 「Backend Unit Tests」job 的雲端日誌我**沒有**下載，只確認該 job 結論為 success；單元測試 2361 以本機全量為準。
  - 雲端三個 job 不含 Playwright E2E，E2E 以本機 `make validate-e2e` 為準（見 §6：標準指令完整建置 278 通過／4 跳過／0 失敗）；雲端 e2e job 只能手動觸發，沒有實跑。
- **Docker 清理**（所有 commit、push、本機守門都結束之後）：容器 0；19 個匿名卷逐一刪除（0 失敗），卷 1.664GB → 928MB；5 個具名卷（`act-toolcache`、`e-commerce_minimax_maven_cache`、`_minio_data`、`_postgres_data`、`_redis_data`）全部保留；映像只留 3 個核准的基底映像（`postgres:18-alpine`、`redis:7-alpine`、`catthehacker/ubuntu:act-latest`），懸空映像 0B。探針用的 `probe306-backend` 映像與 `probe306_*` 卷、網路都已在驗證結束時移除。**建置快取還有 1.68GB**（17 筆，是我建置開發版後端映像留下的，不在「卷與映像」的清理範圍內，沒有刪；要清可執行 `docker builder prune -af`）。

## 11. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/test/java/.../integration/PricingRulesCorruptRowFlywayIntegrationTest.java` | 新增（4 個 Flyway 案例） |
| `backend/src/test/java/.../api/controller/PostControllerE2ETest.java` | 新增 IT-M15-023～027（24 → 29） |
| `backend/src/test/java/.../core/pricing/PricingServiceTest.java` | 只改 `RuleListingQueryTests` 的類別註解（新增第二道護欄的說明） |
| `backend/src/test/java/.../api/M15_API_Automation_Test.sh` | **刪除**（772 行；`git rm`） |
| `frontend/e2e/helpers/auth.ts` | 新增 `postAuthApi`、`retryAfterMs` |
| `frontend/e2e/helpers/store.ts` | `adminAccessToken` 改走 `postAuthApi` 並每 worker 快取 10 分鐘；`seedStore` 用快取的管理員 token，不再 UI 登入管理員 |
| `frontend/e2e/at-auth-api-retry.spec.ts` | 新增（4 個本機假伺服器案例） |
| `frontend/e2e/at-store-less-seller-real.spec.ts`、`at-auth-refresh-real.spec.ts`、`at-login-landing-real.spec.ts`、`at-buyer-auth-wall.spec.ts` | 直接打登入／註冊 API 的呼叫點改走 `postAuthApi`（共 6 處） |
| `frontend/e2e/at-booking-payment-real.spec.ts` | 自己的播種流程改用快取的管理員 token；移除不再使用的 `PASSWORD` |
| `frontend/e2e/at-pricing-rules-real.spec.ts` | 只改兩處註解：護欄改由後端 Flyway 整合測試承接（`DEF-421`） |
| `scripts/validate-e2e.sh` | 結束前印出認證端點被限流次數；E2E 失敗時另存後端日誌 |
| `scripts/sql/seed-e2e-admin.sql` | 只改註解 |
| `docker-compose.yml` | 移除 postgres 的 `docker-entrypoint-initdb.d` 掛載，註解改為記錄原因與決定 |
| `docker-compose.override.yml` | 開發後端 `SPRING_FLYWAY_ENABLED=true`、`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`（含註解） |
| `docs/08_deployment/DOCKER_POLICY.md` | 『已知風險』小節改為『已處理』 |
| `README.md` | 帳號段落：開發堆疊現在走 Flyway、舊資料庫要先 `make down-clean`（含實測失敗訊息） |
| `docs/02_architecture/api/API_M12_Dynamic_Pricing.md` | v2.4：DEF-416 列的測試敘述與契約測試連結 |
| `docs/03_testing/TR_M15_API_Results.md` | 開頭加註記：產生本報告的腳本已刪除 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-356`／`DEF-420`／`DEF-421` 結案；新增 `DEF-422`；`DEF-418` 補註記；頁尾 v3.79 |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 306 row、統計區、版本歷史 v4.34 |
| `docs/04_planning/SPRINT_306_PLAN.md` | 新增（本文件） |

## 12. 後續

- `DEF-417`（商品定價規則的上限／重疊檢查疑似跨租戶）、`DEF-419`（運費模板同型缺口）、`DEF-422`（另外 3 處 `findAll()`）：本輪沒有動，皆為讀碼推論或掃描結果，需先實測。
- **使用者要做的一件事**：拉到這次改動後執行一次 `make down-clean`，再 `make up`（開發資料庫會由 Flyway 重建；之後要已知密碼的 admin 請 `make dev-admin-password`）。
- 是否把 `application.yml` 的 `baseline-on-migrate` 改成 `false`：目前實測是『大聲失敗』，不急；若要處理，需先決定正式環境對『有表沒有歷史表』的資料庫該怎麼辦。
- 雲端 e2e job 的 `postgresql-client` 安裝與 admin seed 步驟（Sprint 305）仍未實跑過。
- `DEF-356` 原記載的 Sprint 248『多數測試資料也建立成功』這一點，本輪的 59 張表解釋涵蓋不了，仍未查明（不影響結論：該路徑已移除）。
