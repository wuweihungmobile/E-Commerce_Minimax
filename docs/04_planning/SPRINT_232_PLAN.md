# Sprint 232 Plan — 文件一致性檢查（第二輪）與實測發現的資料外洩止血（DEF-319～DEF-324）

**Sprint**: Sprint 232
**日期**: 2026-10-02

## 1. 缺口盤點結果

### 1.1 起點與解讀（推論）

使用者貼回 Sprint 231 的總結（內容：沒有新的待決項、工作樹乾淨、雲端 CI 全綠），並說「請繼續完成任務，若不知下一步任務，請回歸AISDLC流程，下一步該進行什麼？」。**沒有針對任何項目的 `==>` 回覆**，所以「繼續」不授權替使用者決定「等你決定」的項目（DEF-306 其餘子項、DEF-317／318）。

我的解讀（**推論，不是使用者說的**）：AISDLC 在「實作變更」之後的下一步是 **document-consistency-check**（[consistency-check.md](../../AISDLC_v0.09/workflow/core/consistency-check.md)；CLAUDE.md 明列「實作變更後不得跳過」），再接 **Sprint 驗收**（[sprint-execution.md](../../AISDLC_v0.09/workflow/core/sprint-execution.md) 步驟 5：逐項驗收、失敗建立 Bug）。上一次檢查是 Sprint 203（2026-09-26），其後 Sprint 204～231 共 28 輪實作只有 5 個 commit 動過規格類文件。比照 Sprint 203 的做法：先唯讀盤點、能由 AI 獨立處理的缺陷就處理、需要決策的以互動選擇提出。

### 1.2 盤點方法

1. **三個唯讀稽核 agent 平行**（API 規格↔端點；業務規則／權限／維運設定↔PRD／FRD／SRD／部署文件；追蹤鏈與測試文件）。每個 prompt 都明文禁止寫檔與任何 git 指令（CLAUDE.md「Workflow 子 Agent 唯讀範圍」）；**回報後我先 `git status`／`git diff` 核對實際檔案系統**，確認只有我自己的改動、沒有任何 agent 寫入。agent 的結論附 file:line，但**多數是讀碼推論**，下表會標明哪些我已複核或實測。
2. **主控 session 自己做的機械比對**：PRD §10 前端路由表 vs `frontend/src/app` 實際頁面（腳本）；前端內部連結字面值 vs 實際頁面（腳本，只認字面值與簡單樣板）。
3. **真實全棧實測**（打包 JAR＋PostgreSQL 18＋Redis 7＋`npm run start`＋Playwright）：兩個真實角色——店主（真實開店流程）與消費者（不屬於任何店鋪）。這一步是被「兩個 agent 各自獨立推論出同一個洩漏疑慮、且都註明『只是讀碼推論，請實測』」觸發的。
4. 抽樣複核 agent 的三項斷言（M06 權限表的 `PUT` 列、token 效期 15 分鐘／7 天、bcrypt 文件要求 ≥12 而程式用預設）：**全部屬實**。

### 1.3 查出的缺口

**A. 實測確認的缺陷（真實後端＋真實角色；本輪核心）**

| # | 缺陷 | 證據 | 處理 |
|---|------|------|------|
| A1 | **`GET /v2/dashboard/bookings`（Sprint 231）對沒有店鋪的買家外洩他人訂房**：一個剛註冊的買家看得到另一位消費者的訂房（房源、**訂房人姓名**、金額、入住日） | 真實全棧：消費者訂房並付款 → 新註冊買家呼叫該端點，回 1 筆他人訂房 | 本輪修復（見 §3） |
| A2 | **`GET /v2/orders/tenant`（Sprint 151，DEF-188）對沒有店鋪的買家外洩他人訂單**（含**收件人姓名**） | 同上（商品訂單）；HTTP 層測試移除守衛後列表出現他人訂單 | 本輪修復 |
| A3 | **歸屬根因（DEF-319）：訂單／訂房蓋成「下單者的租戶」而不是「賣家的租戶」**。一般消費者的租戶是系統租戶佔位值，所以**店主的 `/v2/dashboard/bookings`、`/v2/orders/tenant` 對真實消費者的單永遠是 0 筆**——Sprint 151 的賣家訂單管理與 Sprint 231 的商家端訂房管理，對真實客人完全無效 | 真實全棧：同一批消費者的單，店主查詢 0 筆（兩個端點）。讀碼：`BookingService.createBooking`:417／`buildBookingCore`:520-529、`OrderService.createOrderFromCart`:98／195 皆取 `getCurrentTenant()` | **未修，待決定修法**（§6 Q1）。`at-seller-dashboard-real.spec.ts` 以 `test.fail()` 描述修好後的行為 |

A3 的後果：**已實測（`E2E-SDASH-08`）**店主讀取消費者買的訂單 → `403 E-1007 權限不足`（`checkOrderTenantAuthorization` 的同租戶檢查）；**讀碼推論、未實測**：狀態更新（PATCH）、取消、建立物流單同樣被擋（`checkBookingOwnership`、`LogisticsService.checkOrderTenant` 都要同租戶）；週結算（`SettlementGenerator.generateStatementForTenant` 以 `orders.tenant_id` 為每個 ACTIVE 租戶彙總）不會納入真實消費者的訂單，商家拿不到錢。

**為什麼 231 個 Sprint 沒發現**：後端測試一律把買家的 `tenantId` 設成房源／賣家的租戶（`BookingControllerE2ETest.setUp`:221-226 的註解甚至寫「註冊時不會設定 tenant」再手動塞進去；`BuyerOrderJourneyE2ETest.registerAndLogin` 同）或直接寫入固件；Sprint 231 的 `API-M06-018` 是「買家升級成 STORE_OWNER 自己訂自己看」；前端 E2E 全 mock，前端頁面從未被瀏覽器開過。這正是記憶中「所有相關測試都用固件繞過同一段邏輯」的第三次實例。**兩個列表方法的 Javadoc 都寫著「買家帳號呼叫時 tenantId 為 null，自然為空頁」——前提不成立**（買家是系統租戶佔位值，不是 null）；Sprint 231 把 Sprint 151 的同一個錯誤假設抄了一份。

**B. 前端與 PRD 的落差（主控 session 機械比對＋讀碼，已複核）**

| # | 落差 | 證據 |
|---|------|------|
| B1 | PRD §10 前端路由表 50 條，只有 19 條與實際頁面同名；其餘多為改名（`/auth/login`→`/login`、`/dashboard/purchase-orders`→`/dashboard/erp/purchase-orders`、`/products`／`/rooms`→`/listings/[id]`…），但也有**從未實作且沒有任何記錄的**：`/stores/[slug]` 店鋪前台 4 條路由（後端沒有以 slug 取店鋪的端點、前端無頁面、FRD 完全沒有）、`/blog/category/[id]`、`/admin` 首頁 | 腳本比對；`grep` 後端／前端／規劃文件 |
| B2 | **店鋪成員管理沒有任何前端**：PRD §10.1.2 `/dashboard/members`；後端 7 個端點（列表、邀請、我的邀請、接受、拒絕、改角色、移除）經 Sprint 98／99／210 多輪強化，前端零呼叫（不分大小寫搜尋 `member|invite` 無結果）。店主只能用 API 加店員 | `TenantController`:214-309；前端搜尋 |
| B3 | **失效連結**：頁尾「隱私權政策」`/privacy`、「服務條款」`/terms` 在所有店面頁面都會顯示，頁面不存在（PRD 也沒定義這兩頁）；M15 部落格內嵌商品／房型卡片的「查看詳情」指向 `/products/{id}`、`/rooms/{id}`（`ListingCardService`:95／144），前端實際詳情頁是 `/listings/[id]`，無任何 redirect | 腳本掃描 50 個內部路徑字面值，2 個失效；`next.config.ts` 的 `rewrites` 只轉 `/api`；後端輸出路徑是伺服端產生，腳本看不到，另以讀碼確認 |
| B4 | PRD §9 對嵌入卡片的規格沒照做：MAINTENANCE 應 `ctaUrl="/contact"`、`currentPrice=null`；INACTIVE／DELETED 應 `statusReason="listing_unavailable"`、`ctaUrl="/stores/{slug}"`；實作恆輸出 `/products/` 或 `/rooms/`、維護中仍給價格、`statusReason="listing_inactive"` | `ListingCardService`:60-150 對照 PRD:915-925 |

**C. 安全與非功能（主控 session 讀碼複核；未執行）**

| # | 項目 | 證據 |
|---|------|------|
| C1 | **JWT 預設密鑰防護（DEF-251）可被另外兩個出貨的預設值繞過**：建構子只拒絕 `application.yml` 的那一個預設字串；`docker-compose.yml`:73（`...-key-please-change-in-production`）與 `.env.example`:19（`...-key-please-change-in-production-min-32-chars`）是**不同字串**、長度都 ≥ 32，會被接受，等於用公開字串簽所有 token。`docker compose up` 沒設 `JWT_SECRET` 就是預設路徑 | `JwtTokenService`:25-48；三處預設值 |
| C2 | 密碼雜湊成本：FRD NFR-SEC-002（**P0**）與 SRD 要求 bcrypt ≥ 12，程式是 `new BCryptPasswordEncoder()`（Spring 預設強度 10；未量測雜湊前綴） | `SecurityConfig`:185；FRD:3797、SRD-A:362 |
| C3 | Token 儲存：PRD／SRD／API 文件寫 Refresh Token 為 HttpOnly Cookie、Access 30 分鐘、Refresh 30 天；實際後端**沒有任何 Cookie 處理**，前端存 `localStorage`，Access 15 分鐘、Refresh 預設 7 天（compose 設 30 天），`expiresIn` 回傳毫秒 | grep；`application.yml`:79-80；`axios.ts` |

**D. 文件與實作矛盾（稽核 agent 讀碼回報，附 file:line；除標「已複核」者外，主控 session 未逐項重驗）**

四類分類沿用 consistency-check 的 §2.3（🔴 遺漏／🟡 待補充／🟢 有意省略／🟠 時序不匹配）。**高＝照文件做會出錯**。

| 領域 | 高誤導項目 | 分類 |
|------|-----------|------|
| 維運 | `SETTLEMENT_JOB_RUNBOOK.md` §6 緊急回滾要人設 `ENABLE_SCHEDULED_JOBS=false` 並 grep "Scheduling disabled"——**全庫沒有這兩者**（**已複核**）；真正的開關是 `APP_SCHEDULING_ENABLED`，且關掉會連**自動退款**一起停。緊急時照做會以為排程已停、實際照跑 | 🟠 |
| 維運 | `docker-compose.yml` 的 backend `environment` 是明確清單，沒有 `SMTP_*`、`APP_CORS_ALLOWED_ORIGINS`、`APP_FRONTEND_BASE_URL`、`APP_SCHEDULING_ENABLED`，也沒有 `env_file`——在 `.env` 設了不會進容器；這些變數在部署文件、README、`.env.example` 都是 0 筆 | 🔴 |
| API 規格 | `API_M06_Booking.md`：角色表只列 BUYER 且 `PUT` 寫 BUYER 持有 `booking:update`（**已複核：BUYER 沒有**）；`availability` 寫 GET＋Body（實作是 query 參數，照文件送得 400）；E-4001 標 409（實作 400）、E-3001／3002 標 400（實作 422）；訂房付款 4 個端點（Sprint 221）與 `GET /v2/dashboard/bookings`（Sprint 231）完全沒記載；狀態表有不存在的 `NO_SHOW`、缺 `PAID`／`COMPLETED` | 🔴＋🟠 |
| API 規格 | `API_M05_Order.md`：賣家列表／狀態更新的端點與請求形狀都不是實作的（`GET /v2/orders/tenant`、`PATCH /v2/orders/{id}/status {targetStatus,reason}`）；建單請求形狀不同；付款／退款端點（`OrderPaymentController` 6 個）未記載；狀態表缺 `PAID`／`CONFIRMED`；`X-Idempotency-Key` 實際是 `Idempotency-Key` | 🔴＋🟡 |
| API 規格 | `API_M03_Auth.md`：Access 30 分鐘／Refresh 30 天／HttpOnly Cookie／`expiresIn` 秒（**已複核與實作不符**，見 C3）；停用帳號寫 403（實作 401 E-1001）；未記載 6 次失敗鎖定（Sprint 214）、Refresh Token 輪替與重放偵測（Sprint 213）、登出撤銷全部 | 🔴 |
| 規格 | PRD §15.3／§9.14 等仍寫「Phase 1 為 Payment Mock、`CREATED(=PAID)`、不支援退款（E-4013）」，實作早已是真實金流、`CREATED` 為未付款（24 小時逾時取消）、自動退款；FRD BR-M05-001 狀態轉換矩陣與 SRD 的訂單狀態圖都是舊行為（沒有 `CONFIRMED`、沒有 `CANCELLED→REFUNDING`） | 🟠 |
| 規格 | **FRD 沒有 M06／M07／M09 章**（37 則 US 只涵蓋 M01／M02／M03／M05／M12／M17）：訂房、付款、通知沒有 US／AC；24 小時逾時規則 PRD／FRD／SRD 皆無 | 🔴 |
| 規格 | SRD §5.5 與 FRD 仍寫「日誌型 Mock 是唯一實作、接上真實寄信前不可上線」（Sprint 209 起已接 SMTP，只更新了 PRD） | 🟠 |
| 測試文件 | `docs/03_testing` 的 TC 文件停在 4～6 月：`TC_Index` 宣稱 190（金字塔／優先級合計 141，實際約 280）；`TC_M06` 25 個「待實現」0 個完成；M09／M10／M11／M13／M14／M18 無 TC 文件；後端測試編號只有 28% 出現在 `docs/03_testing`，Sprint 204～231 新增 41 個測試檔中 34 個沒有任何編號；`BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST` 勾選 0／42 | 🟡＋🔴 |
| 其他 | `STRIPE_PRODUCTION_CHECKLIST` 與 RUNBOOK 稱「沒有手動結算入口」（Sprint 208 已有 `POST /v2/admin/settlements/generate`，RUNBOOK 自相矛盾）；Webhook 路徑漏 `/api` 前綴；PRD 內嵌的 `TC-M05-016/017/018` 在 `docs/03_testing` 與測試碼各 0 筆 | 🟠 |

**反向缺口**（PRD 驗收標準 vs 實作，稽核 agent 回報；已登記者標出）：US-001「預訂成功後即時收到確認通知」、US-014「支付失敗／金流阻斷」通知與重試連結（DEF-318）、`GET /api/v2/bookings/:id/state-log` 與 US-006 狀態日誌（**追蹤表未登記**）、`X-Mock-Fail`（全庫零出現，**未登記**）、取消原因 ≤ 500 字驗證（輕微）。

### 1.4 更正與偽陽性（含我自己的錯誤）

- **「SRD schema 文件的守門沒擋住 V84～V86」是錯的**：我一度以為 `payment_due_at`／`refund_status` 沒進 `SRD_Database_Schema.md` 代表守門失靈。實際執行 `make validate-schema-doc` 通過；`bookings`、`payments`、`notifications` 等 39 張表本來就列在 §2.6「已知範圍外資料表」，是使用者在 Sprint 123 拍板的取捨（DEF-068）。**不是缺口**。
- **「商家取消面板顯示買家的取消政策，會誤導商家」是錯的**：`CANCELLATION_POLICY_SUMMARY` 已含「商家取消一律全額退款」。已撤回。
- **我的前幾次 `grep` 全部回 0 筆是工具誤用，不是「文件沒寫」**：zsh 把沒有引號的 `--include=*.md` 當萬用字元展開並中止指令、未加引號的變數不會斷詞。我靠「先對一個已知存在的字串驗證指令本身有效」才發現（`REFUNDING` 明明在 `API_M05_Order.md` 出現 3 次卻回 0）。結論中凡「文件沒寫」都已在指令驗證有效後才下。
- 稽核 agent 1 的任務前提「Sprint 216 起付款端點權限為 `order:update`」與程式不符，它自己更正了（Mock 付款是 `order:create or order:update`）。
- 稽核 agent 2 的「商家看不到訂房」「一般買家可列出」兩個高優先疑點，agent 1 的 D1 也獨立提出——**三方獨立推論一致**，我才花力氣實測；實測證實。

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

**尚無**。本輪結尾以互動選擇提出下列決定，答覆後記入下一個 Sprint 的計畫書（見 §6）。**（後記：使用者已在本輪結尾全部答覆，拍板紀錄見 [SPRINT_233_PLAN.md](SPRINT_233_PLAN.md) §2。）**

## 3. 實作內容

**止血（A1／A2，AI 可獨立處理、無產品決策）**：`OrderService.getTenantOrders`、`BookingService.getTenantBookings` 在呼叫者租戶為 `null` **或系統租戶佔位值**時回空頁、不查詢、不拋錯（比照這兩個類別既有的 `checkOrderTenantAuthorization`／`checkBookingOwnership` 已排除 `SYSTEM_TENANT_UUID` 的作法），並更正兩處 Javadoc 的錯誤前提。其他部分不動。

**已複核且風險高的文件問題，本輪直接修正（文件對齊實作，不需決策）**：`SETTLEMENT_JOB_RUNBOOK.md` §6 緊急回滾改為真正的開關 `APP_SCHEDULING_ENABLED`（並明寫關掉會連自動退款一起停、後端沒有「排程已停用」日誌行所以改用 `kubectl exec ... printenv` 確認、目前沒有「只停結算」的開關）；RUNBOOK 檔頭表格與 `STRIPE_PRODUCTION_CHECKLIST.md` 兩處「沒有手動結算入口」改為 Sprint 208 起有 `POST /v2/admin/settlements/generate`；Stripe Webhook 路徑補上 `/api` 前綴（`context-path`）。其餘文件矛盾（§1.3 D）等 Q4 答覆後處理。

**驗收測試（Sprint 驗收）**：新增 `frontend/e2e/at-seller-dashboard-real.spec.ts`（真實後端，不 mock）：
- `E2E-SDASH-02／06`（**正常斷言**）：沒有店鋪的買家呼叫商家端訂房列表／賣家訂單列表 → 空頁，看不到別人的單。守住本輪修復的洩漏。
- `E2E-SDASH-03／04／07／08`（`test.fail()`）：店主看得到並能處理消費者的單（含畫面上的取消流程、全額退款、取消方為商家）——**DEF-319 的行為規格**。目前應該失敗；修好後會「意外通過」而報錯，提醒移除標記。比 `test.skip` 好：每次都真的執行、不會悄悄爛掉。
- `E2E-SDASH-01／05`：建立前提（消費者訂房＋付款、買商品＋付款）。
- `E2E-SDASH-09`（正常斷言）：店主**自己訂自己的房**（同租戶的特例，也是 Sprint 231 當時唯一驗證過的情境）→ 商家端訂房管理畫面：列表、詳情、取消面板、退款說明、退款完成後重新載入。**這是 Sprint 231 的前端畫面第一次被真實瀏覽器開啟並截圖目視確認**；與 DEF-319 無關，修好後仍應通過。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元（Mockito） | `OrderServiceTest`：舊案例 `getTenantOrders_nullTenant_passesNullThrough`（**編碼的是實作細節「把 null 傳給 repository」，而非意圖「沒有店鋪就沒有店鋪訂單」**，且與修復衝突）改寫為「null 租戶回空頁、完全不查詢」，另加「系統租戶（買家）回空頁、完全不查詢」（含有／無 status 篩選）。`BookingServiceRoomTitleTest`：同樣的兩個案例。 |
| HTTP＋真實 PostgreSQL | `BookingControllerE2ETest` `API-M06-019`：兩個**沒有店鋪**的買家（本類別其他案例的 `setUp` 一律把買家放進房源租戶），A 訂房、B 呼叫 `GET /v2/dashboard/bookings` → 空；訂房人自己呼叫也是空，而 `GET /v2/bookings` 仍看得到自己的訂房。`BuyerOrderJourneyE2ETest` 新增案例：兩個沒有店鋪的買家，A 下單、B 呼叫 `GET /v2/orders/tenant` → 空。 |
| 真實全棧（Playwright） | 見 §3 的 `at-seller-dashboard-real.spec.ts`，9 個案例：5 個正常斷言＋4 個 `test.fail()`。**用 JSON 報告逐一確認 4 個預期失敗案例都在「正確的斷言」失敗**——列表 0 筆、畫面找不到該筆訂房、訂單列表 0 筆、店主讀取消費者訂單 403 `E-1007`——而不是測試本身寫錯。修復前（舊 JAR）以 `curl` 與一次性探測 spec 重現過兩個洩漏；修復後（新 JAR）`E2E-SDASH-02／06` 通過。 |

**突變驗證（Rule 9：無法失敗的測試是錯的）**——暫時把守衛條件改成 `if (false)`，從備份還原，並以 `git diff` 與 `grep` 確認無殘留：
- 單元：4 個新／改案例全部轉紅（服務層確實去查了 repository）。
- HTTP 層：`API-M06-019` 與訂單端新案例各轉紅，**失敗訊息直接列出被外洩的資料**（房源名稱、日期、金額、他人訂單）。

## 5. 驗證結果

- **`mvn -o clean verify`**（含 checkstyle main＋test、PMD、JAR 打包）：**BUILD SUCCESS，17 分 24 秒**；單元 **1977**（+2）／整合 **686**（+2）／**0 失敗**；checkstyle 0 違規；PMD 通過。
- **定點執行**：`OrderServiceTest`＋`BookingServiceRoomTitleTest` 83 個全過；`BookingControllerE2ETest` 23 個、`BuyerOrderJourneyE2ETest` 7 個全過；各自經突變驗證（見 §4）。
- **真實全棧（新 JAR＋PostgreSQL 18＋Redis 7＋`npm run start`）**：`at-seller-dashboard-real.spec.ts` **9 個案例全部符合預期（預期通過 5、預期失敗 4，非預期 0、略過 0、不穩定 0）**，約 1.2～1.7 分鐘。
- **畫面目視（Sprint 231 前端的首次驗收）**：`SDASH-09` 的截圖逐張讀過——列表（房源名稱、訂房人姓名與住宿日期、金額、狀態徽章）、詳情、取消面板（含政策說明「商家取消一律全額退款」）、取消後的提示與「退款處理中」卡片、退款完成後重新載入顯示「已退款」，皆正確。
- **版面觀察**：這些頁面沒有後台導覽列、內容貼齊視窗邊緣——查證後這是**既有的普遍現象**，不是 Sprint 231 獨有：後台 46 個頁面中 20 個沒有自己的置中容器（含 `/dashboard/returns`、客服工單、ERP 清單、FAQ…），且**沒有共用的 `dashboard/layout.tsx`**，使用者只能靠 `/dashboard` 首頁的快速連結與瀏覽器返回鍵（已登記 DEF-324，低優先級）。
- **前端**：`tsc --noEmit`、`eslint`（新 spec）皆通過。
- **push 與雲端 CI**：commit `2d5bd7a` 於 2026-10-02 push（`ff89824..2d5bd7a main -> main`）。pre-commit 通過（checkstyle／compile／核心測試／ESLint／TypeScript）；pre-push 輕量守門通過（後端單元、前端 Lint＆Build、schema 漂移）；**雲端 CI 全綠**（run 36953536720，三個 job 皆 success，共 6 分 42 秒）。全程未使用 `--no-verify`。
- **`make validate-e2e`**（乾淨 PostgreSQL＋Flyway、`ddl-auto=validate`、打包 JAR＋`npm start`，沿用剛打包的產物）：**121 個測試：117 通過／4 略過（與 Sprint 231 基準相同的 4 個）／0 失敗，7.2 分鐘**（121＝Sprint 231 的 112＋本輪新增 9；4 個 `test.fail()` 案例依 Playwright 規則計入「通過」）；schema 對齊（後端以 `ddl-auto=validate` 對 Flyway 從零重建的資料庫啟動成功）。耗時比 Sprint 231 的 4.7 分鐘長，主因是新 spec 的真實開店流程與兩個角色走訪（序列執行約 1.2～1.7 分鐘）。

## 6. 範圍外（延後）與待使用者決定

**待決（本輪結尾以 AskUserQuestion 提出）**

- **Q1（DEF-319 根因怎麼修）**：PRD §7.3／§8.2／2369 要求「店鋪訂單以 JWT 的 tenant_id 查詢」，即訂單／訂房應歸屬賣家。選項：(1) 訂房蓋房源所屬店鋪；訂單限同一店鋪結帳（跨店購物車請分開結帳）；Flyway 回填歷史資料——**最小，貼近 PRD Phase 1「單一訂單」假設**；(2) 訂房同上，訂單依店鋪自動拆單——體驗最好，但付款／退款／物流／通知都要支援「一次結帳多張訂單」，且 PRD 把拆單列為 Phase 2；(3) 只修訂房，訂單維持現狀——賣家訂單管理與週結算仍失效。**影響範圍**（讀碼）：優惠券以哪個租戶解析、`CombinedCheckoutService`、結算、物流、稽核紀錄的租戶、歷史資料回填。
- **Q2（安全 NFR）**：bcrypt 成本（PRD P0 ≥ 12）與 Token 儲存（PRD／SRD 寫 HttpOnly Cookie，實作 localStorage）：改程式對齊 PRD，還是改文件反映現況？
- **Q3（PRD 載明而缺席的前端）**：店鋪成員管理 UI（後端齊備）、店鋪前台 `/stores/[slug]`（需新後端端點＋4 頁）、修失效連結（CMS 卡片→`/listings/`；`/privacy`、`/terms` 需要法務文字，我不能替你寫）、DEF-318 的通知事件。
- **Q4（文件對齊策略）**：比照 Sprint 203（文件對齊實作、PRD 內文不動只加修訂註記、補 FRD 的 M06／M07／M09、TC 文件加現況聲明不重寫）；或另補 TC／AT 文件；或只修高誤導項目。

**已登記、不在本輪處理**：DEF-317／318（通知）、DEF-306 其餘 RBAC 子項、DEF-286（API 索引不完整）、Stripe 真實路徑未驗證。

**我替使用者做的假設**：沒有店鋪的呼叫者呼叫商家端列表回**空頁**而不是 403（比照原註解的意圖與 `/v2/orders/tenant` 既有行為，前端不需要為此處理新錯誤）。

**已知取捨（行為改變，已查證）**：系統租戶脈絡的管理員（含 SUPER_ADMIN 未帶 `X-Tenant-ID`）原本打開賣家訂單頁會看到**所有一般消費者的訂單**（等於意外的平台總覽），修復後是空頁。前端只有 `/dashboard/orders` 一處呼叫 `/v2/orders/tenant`；後端**沒有任何平台層的訂單列表端點**（`AdminController` 只有採購單），所以這個意外的能力沒有替代品——若平台管理確實需要總覽，那是新功能（要有自己的權限與端點），不該靠這個洩漏。SUPER_ADMIN 仍可帶 `X-Tenant-ID` 指定某個真實租戶查看。

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 使用者回答 §6 的 Q1～Q4 後：依答覆排 Sprint（建議順序：DEF-319 根因 → 安全 NFR／JWT 預設值（C1，不需決定，可先做）→ 文件對齊 → 缺席的前端）。
2. **C1（JWT 預設密鑰防護被繞過）不需要使用者決定**，可獨立處理（把三個出貨預設值都列入拒絕清單，或改成「含 `change-in-production` 即拒絕」），下一輪優先做。
3. ~~RUNBOOK §6 的錯誤緊急開關~~：**已於本輪修正**（見 §3）。
4. 本輪新增的 `test.fail()` 案例：DEF-319 修復時必須同時移除標記（否則會因「意外通過」而失敗，這是設計）。
5. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新（[[deferred-items-tracker-drift]]：這是最常被跳過的一步）。
