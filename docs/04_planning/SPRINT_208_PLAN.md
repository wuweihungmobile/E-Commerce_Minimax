# Sprint 208 Plan — DEF-287／285／284 三項待辦（1 修復 + 1 決策落實 + 1 修復）

**Sprint**: Sprint 208
**日期**: 2026-09-27

## 1. 起點

使用者先回「我選 DEF-287」，接著貼上完整五項清單要求「請依序做完」：DEF-287、DEF-285、DEF-284、真實寄信服務選型、維運三件事＋Stripe 走查。本輪處理前三項；後兩項需使用者提供憑證或操作實際部署環境，AI 做不到，見 §5。

## 2. DEF-287：結算單手動觸發入口（已修）

### 2.1 設計決策

追蹤表原記錄需使用者決定「給誰用」「能否指定租戶與期間」。決策：

- **僅 SUPER_ADMIN**：結算單核准後會觸發真實 Transfer 撥款，比照 `settlement:reverse`（SUPER_ADMIN/CFO 雙重授權）的敏感度，但 generate 本身不需要雙重授權（沒有立即的資金流出，approve 才有）。新增 `Permission.SETTLEMENT_GENERATE`，**刻意不加入任何角色的 `EnumSet`**，只靠 `SUPER_ADMIN` 的 `EnumSet.allOf(Permission.class)` 取得——比照既有 `NOTIFICATION_CREATE` 收斂為 SUPER_ADMIN 專用的作法（DEF-238），不必在 controller 額外硬編碼角色檢查。
- **可指定租戶與期間**：`tenantId`（必填）、`periodStart`（必填）、`periodEnd`（選填，預設 `periodStart + 6` 天，比照週結算天數）。兩個實際用途都需要任意期間：(1) 排程當週某租戶失敗的補產，通常是上週；(2) 上線前 Stripe 測試模式走查撥款，需要涵蓋「現在」的期間，不是上週。

### 2.2 為什麼不重新實作防護

`SettlementGenerator.generateStatementForTenant`（既有方法，排程呼叫）已有：
- **冪等**：同租戶＋期間已有結算單，直接回傳既有的，不重複產生。
- **併發防護**：訂單以 `UPDATE ... WHERE settled_statement_id IS NULL` 原子認領，認領筆數不足即回滾整張結算單。

新增的 `generateStatementManually` 只加驗證（`periodEnd` 早於 `periodStart` → `E-9008`）與 DTO 轉換，直接委派 `generateStatementForTenant`——手動觸發與排程觸發共用同一套防護，不會因為多了一個入口而多一種重複結算的風險。

### 2.3 實作

- `Permission.SETTLEMENT_GENERATE`（新）。
- `SettlementGenerator.generateStatementManually(tenantId, periodStart, periodEnd)`（新方法）。
- `SettlementController`：`POST /v2/admin/settlements/generate`，`@PreAuthorize("hasAuthority('settlement:generate')")`。

### 2.4 測試

| 測試 | 守什麼 |
|------|--------|
| `SettlementGeneratorManualTriggerTest`（3） | `periodEnd` 早於 `periodStart` → `E-9008`、不查租戶；正常參數委派並回傳 mapper 轉換的 DTO；同期間已有結算單 → 直接回傳既有的，不重新認領 |
| `RolePermissionMappingTest` 新增 1 案例 | `settlement:generate` 僅 SUPER_ADMIN 持有，ADMIN／CFO／STORE_OWNER 皆無 |

**紅燈先行、突變驗證**（4 種，全被抓到）：拿掉日期驗證、繞過 `generateStatementForTenant` 直接組一張新結算單（跳過冪等）、意外把權限加進 CFO 的 `EnumSet`。

## 3. DEF-285：PRD 全域冪等規範的產品決策（已拍板並落實）

### 3.1 決策

**PRD §9.17 從「所有 POST/PUT/DELETE 必帶 `X-Idempotency-Key`」收斂為「涉及金流或庫存副作用的寫入端點」**。理由：Sprint 207 盤點的 174 個寫入端點中，多數（評價、媒體、地址簿等）重送的代價低且使用者可自行察覺重複，強制全部呼叫端都生成並保存冪等鍵的實作與維護成本，大於它防的風險。

標頭名維持實作現況 `Idempotency-Key`（無 `X-` 前綴）：`X-` 前綴的自訂標頭慣例已在 RFC 6648 被建議棄用，讓 PRD 對齊實作而非反過來改三個既有端點與其前端呼叫端。

### 3.2 補上最大缺口

Sprint 207 盤點發現 `POST /v2/orders`（PRODUCT 單一類型結帳）是金流關鍵路徑裡唯一完全沒有冪等保護的端點——`BookingController.createBooking`／`CheckoutController.checkoutMixedCart` 都已支援。本輪比照兩者既有邏輯（未變更既有程式碼，只是把同一段邏輯套用到新端點）：選帶 `Idempotency-Key`，帶了就走 Redis 檢查並快取回應，不帶則維持原行為（向後相容）。

**這個修法解決什麼、不解決什麼**：解決「同一個邏輯建單請求因網路逾時被重送」（PRD 原文動機）。**不解決**兩個沒有共用鍵的獨立請求對同一購物車的併發競態——那是 Sprint 207 §2.1 記錄的推論、未重現的問題，維持併入 DEF-285 的後續範圍，本輪未處理。

### 3.3 測試

`OrderControllerIdempotencyTest`（7）：純 mock 測控制器分支（沒帶 key／空白 key／格式錯誤／新 key 建單成功／重複 key 有快取／重複 key 仍處理中／建單拋例外時清 key），不啟動 Spring context 或 Redis——Redis 語意已由既有 `IdempotencyServiceTest` 覆蓋。

**紅燈先行、突變驗證**（3 種，全被抓到）：拿掉格式驗證、忽略快取直接回 409（第一次用 regex 寫的突變沒有實際命中檔案內容，測試「通過」是假訊號，改用精確字串比對後才真的產生突變並確認被抓到——這是本輪唯一的方法論教訓）、失敗時不清除 key。

## 4. DEF-284：`E_1005` 重複註冊的狀態碼（已修）

`GlobalExceptionHandler.mapErrorCodeToStatus` 把 `E_1005`「電子郵件已被註冊」歸在 `NOT_FOUND`（404），與其他「已存在」類（`E_2003`、`E_1008`、`E_1010`、`E_3005`、`E_4092` 等）的 409 不一致。查證：`frontend/src` grep `E-1005`／「已被註冊」皆無結果；另 grep `status === 404` 找到的兩處用法都在租戶相關元件，與註冊無關。改一行（移入 `CONFLICT` 群組），同步更新 `GlobalExceptionHandlerTest`（142 案例）與 `API_Error_Codes.md` §4 對應列（`ErrorCodeDocDriftTest` 已驗證同步）。

**風險本身不變**：狀態碼從來不是列舉 email 的防線（`LoginRateLimitFilter` 已處理該風險），本次只是把不一致的狀態碼改成與其他「已存在」類一致，不改變安全姿態。

**突變驗證**：把 `E_1005` 改回 404，`GlobalExceptionHandlerTest`／`ErrorCodeDocDriftTest` 皆變紅；`API_Error_Codes.md` 單獨改回 404（程式碼不變）也讓 `ErrorCodeDocDriftTest` 變紅。皆已還原並重跑確認全綠。

**§6 完整驗證才抓到的第二個被釕住位置**：`AuthControllerIntegrationTest.register_emailExists_returns409` 的方法名與 `@DisplayName` 從一開始就寫「returns409」，但斷言本體檢查 `status().isNotFound()`——名字與斷言互相矛盾，只是恰好與舊行為（404）一致而從未被發現，是修復前就存在的既有不一致，不是本次修復引入的。單獨跑 `GlobalExceptionHandlerTest`／`ErrorCodeDocDriftTest` 兩支目標測試時看不到，只有跑到這支 `@SpringBootTest` 整合測試才會失敗——這正是本輪一開始沒抓到它的原因（先前只針對性跑了目標測試，直到完整 `mvn clean verify` 才發現）。已改為 `isConflict()`，與名稱一致。全庫另 grep `E-1005`／`E_1005`：`AuthServiceRegisterTest`／`AuthServiceTest` 只斷言 `ErrorCode` 列舉值，不涉 HTTP 狀態，未受影響；前端無任何依賴。

## 5. 過程中的插曲：一次假的 `BUILD FAILURE`

背景執行 `mvn clean verify` 期間，前景又跑了多個 `mvn test -Dtest=...`（DEF-287 的突變驗證）——同一個 `target/` 目錄被兩個 Maven 生命週期同時寫入，背景那次以一堆不相關檔案的「cannot access ... Builder」失敗（Lombok 產生的巢狀類別在編譯中途被覆蓋）。這是已知教訓（見 `sprint136-52-concurrency-debt-fully-resolved-s137-144` 記憶）在本輪又踩了一次。發現後確認沒有殘留的 Maven 行程，之後改為前景動作全部做完才單獨重跑 `mvn clean verify`，見 §6。

## 6. 驗證結果

**過程中又踩了一次環境坑**：本輪早些時候 `git push` 時 pre-push 的 `make validate-push` 呼叫了 `test-db-down`，把整合測試需要的 `nk-test-pg`/`nk-test-redis` 停掉；下一次背景 `mvn clean verify`（已排除 §5 的並行污染後）因此以 11 個「Failed to load ApplicationContext」失敗，全部是與本輪改動無關的既有 `@SpringBootTest` 測試（`PaginationBoundaryValidationTest`、`SellerDashboardServiceCacheTest` 等）。`make test-db-up` 重啟後問題消失——純環境問題，不是程式碼缺陷。

**單獨執行、test DB 就位、過程中未與任何其他 `mvn` 呼叫並行**：`mvn -o clean verify`

- 單元 **1785**（+11：`SettlementGeneratorManualTriggerTest` 3、`OrderControllerIdempotencyTest` 7、`RolePermissionMappingTest` 新增 1 案例）
- 整合 **565**（不變；`AuthControllerIntegrationTest` 8 個含 §4 修好的那支）
- 0 failures／0 errors／0 skipped
- checkstyle（main＋test）0 violations
- `BUILD SUCCESS`，9:57 min

**未執行**：E2E（`make validate-e2e`）與 `make validate-release`（屬 push 流程）；本輪沒有 Flyway 遷移。

## 7. 範圍外（延後，AI 無法完成）

- **真實寄信服務選型（SMTP／SendGrid／SES）**：需使用者提供憑證，AI 無法代為決定或設定。
- **維運三件事**（前方代理 `X-Forwarded-Proto`、staging 首次上線看 CSP 違規、Sprint 198 若已部署則送 `max-age=0`）：需存取實際部署環境。
- **Stripe 測試模式端到端走查**（`STRIPE_PRODUCTION_CHECKLIST.md` §D）：需人工登入 Stripe Dashboard 操作，含驗證 DEF-288 修復的「連續退兩次」項目。
- **DEF-285 未逐一核對的約 160 個非金流端點**、**購物車併發競態**（Sprint 207 §2.1）：維持登記，未在本輪處理。

## 8. Push

**尚未 push。** 本輪含新功能（結算單手動觸發端點、訂單冪等鍵）與一個 API 契約變更（`E_1005` 狀態碼），不在 2026-07-08「金流／安全 commit 免確認 push」授權範圍內，等使用者決定。
