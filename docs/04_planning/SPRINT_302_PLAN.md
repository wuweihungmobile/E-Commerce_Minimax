# Sprint 302 Plan — 落地使用者在 Sprint 301 收尾的四個回答：DEF-412（淨營收的退款算法）、DEF-413（買家頁面登入守門）、`/checkout` 需登入、gate 註冊等待上限 6→20 秒

## 1. 起點與範圍

### 1.1 起點（這次是使用者本人的回答，不是我的推論）

[Sprint 301](SPRINT_301_PLAN.md) 收尾時我用 `AskUserQuestion` 問了四題；使用者**本人**全部選了建議選項（真實的互動輸入，可在對話紀錄中查到）：

| 題目 | 使用者的回答 |
|---|---|
| `DEF-412` 報表「淨營收」扣退款的算法 | 依建議：營收 − 部分退款 |
| `DEF-413` 沒登入的人開買家頁面 | 共用版面層加登入守門 |
| `/checkout` 對沒登入的訪客 | 需登入 |
| gate 冷啟動註冊逾時的處置 | 調高等待上限 6 秒→20 秒 |

這四個回答就是授權。本輪做的就是照這四個決定落地，**沒有**替使用者決定其他待決項目。

### 1.2 成功標準（開工時定義）

1. 四項依使用者的決定落地，文件（PRD／API 規格／追蹤表）同步。
2. 每個新測試必須能因為正確的原因轉紅：對修復前的建置跑，加上針對實作的突變；多個缺陷各有**獨立**的紅燈。
3. 前端守門用**真實瀏覽器、真實後端**驗證，不只測純函式；並找出守門會波及的既有 E2E，一併處理。
4. 全量後端回歸（改了生產程式碼）與完整 `make validate-e2e` 通過。
5. 登記時寫的推論要在動手前先用工具核對。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 使用者要的「`totalRefunds`＝所有付款的 `refundedAmount` 加總」可以照字面實作 | **不行**。`PaymentRepository#markRefundedIfNotAlready`（Stripe webhook 路徑）只改 `status` 與 `stripeRefundId`、不寫 `refundedAmount`，照字面加總會把 webhook 退掉的全額退款算成 0。所以全額退款（付款 `REFUNDED`）以 `amount` 計，部分退款（`PARTIALLY_REFUNDED`）才用 `refundedAmount`（§2，我替使用者做的實作決定） |
| `(auth)` 路由群組裡哪些頁面要公開 | 五個認證流程頁面（`/login`、`/register`、`/forgot-password`、`/reset-password`、`/verify-email`，與 `AUTH_FLOW_PATHS` 吻合）；其餘全部需要登入。灰色地帶 `/reviews/product/[listingId]`：它只渲染 `ReviewList`，而後端 `GET /v2/reviews/listing/{id}` 是 `@PreAuthorize("isAuthenticated()")`，訪客今天本來就拿到 401——擋它不是回歸 |
| 守門會波及哪些既有 E2E | 第一版掃描條件太寬（`addInitScript`、任意 `localStorage.setItem` 都算有登入手段），只找出 4 支；收窄成「沒有真實登入手段」後多找出 `at-room-booking` 的 3 個 `/checkout` 案例，共 **5 支**（§3.2） |
| 「6→20 秒」是不是沒有副作用 | **有一個**：被限流（429）時頁面不會導回登入頁，輔助函式的 `waitForURL` 會等滿上限才去看狀態碼，每輪限流重試最多多等 20 秒（原本 6 秒）。查 Sprint 301 成功那次 gate 的後端日誌（搜 `429`、`too many requests`、`rate limit`），命中的 3 行都是啟動時印的設定訊息，**沒有任何限流拒絕的紀錄**；這是「日誌裡沒有」，不等於「一次 429 都沒發生」（限流器拒絕時是否一定印日誌我沒有逐一確認），所以只能說健康的 gate 很可能碰不到（§4） |

## 2. DEF-412：淨營收的退款算法（已完成）

**改了什麼**：`AnalyticsService.getRevenueStats` 分別查付款狀態 `REFUNDED` 與 `PARTIALLY_REFUNDED`；`refundedMoney` 取每筆實際退掉的金額（`REFUNDED` 用 `amount`，`PARTIALLY_REFUNDED` 用 `refundedAmount`，`null` 當 0）。`totalRefunds`＝兩種退款加總；`netRevenue`＝`totalRevenue` − **仍計入營收的訂單**上的退款。全額退款的訂單（狀態 `REFUNDED`）本來就不在營收裡，不再扣第二次；部分退款的訂單狀態不變、仍計入營收，所以它的退款要扣。

**驗證**：

- **實作前先看紅燈**：3 個單元案例轉紅，原因都是預期的——全額退款不重複扣（淨營收預期 500、實得 **0**）、部分退款計入（退款總額預期 200、實得 **0**）、更新後的既有案例（退款總額預期 50、實得 **0**）。webhook 路徑案例本來就綠，是守住「全額退款以 `amount` 計」這個實作選擇的案例，由突變驗證負責。
- **實作後**：`AnalyticsServiceTest` 18／18（14→18：新增 4 個並更新 1 個既有案例）、`AnalyticsRealDbIntegrationTest` 4／4（3→4：新增 1 個真實 PostgreSQL 案例，全額＋部分＋webhook 三種退款混在同一個區間：營收 1450、退款總額 1100、淨營收 950；修復前會是退款 900、淨營收 550）。
- **既有案例要有意識地改寫**：`getRevenueStats_filtersByStatus_computesAovAndNet` 原本用 `any()` 讓任何狀態的查詢都回同一個只 stub 了 `getAmount()` 的 mock，沒有指定它屬於哪張訂單；新實作會查兩種狀態，同一筆退款會被算兩次。改成 `CONFIRMED` 訂單上的 50 元部分退款，斷言數字不變。
- **突變 5 個全數擊殺**：M1 淨營收退回舊算法（扣全部退款）、M2 不查部分退款、M3 全額退款也用 `refundedAmount`、M4 拿掉 `null` 保護（以 NPE 擊殺）、M5 反過來只扣不在營收裡的訂單的退款。還原後 sha256 相同。
- **突變驗證抓到我自己的測試缺陷**：第一版整合測試的數字湊巧（全額退款 500 ＝ 部分 200 ＋ webhook 300），M5 在整合層倖存（只被單元測試擊殺），整合測試分不出「扣仍計入營收的訂單上的退款」與「扣不在營收裡的訂單的退款」。把全額退款改成 600 後重跑，M5 也被整合案例擊殺。

## 3. DEF-413：買家頁面登入守門（已完成）

### 3.1 設計

新增 `frontend/src/components/layout/AuthGuard.tsx`，接在 `(auth)/layout.tsx` 的內容區外。`isAuthFlowPath` 從 `auth-redirect.ts` 匯出共用。沒登入的人在受保護頁面 `router.replace(loginHref())` 導去 `/login?redirect=<原頁>`，登入後回原頁；沒登入時不渲染頁面內容（不會發出必然 401 的請求、不會閃出「無法載入…」）。

**兩個刻意的設計**（程式碼註解與 E2E 都守著）：

1. **只在掛載與路徑改變時檢查，不對登入狀態的變化做反應**。登出時 token 被清掉；守門若跟著導向，就會與登出自己的 `router.push('/login')` 搶，把網址變成 `/login?redirect=…`。
2. **檢查時直接讀當下的 token，不用 render 拿到的快照**。整頁重新載入時 hydration 當下的快照一律是「未登入」，拿它判斷會把已登入的人誤踢去登入頁。

### 3.2 波及的既有規格

5 支「用 `page.route` 假造 API、不走真實登入、直接開受保護頁」的規格，守門上線後會被導去登入頁：`at-order-refund`、`at-notification-inbox-links`、`at-booking-cancel-refund`、`at-booking-payment`，以及 `at-room-booking` 的 `E2E-ROOM-02`／`03`／`12` 三個 `/checkout` 案例。新增 `e2e/helpers/mockLogin.ts`（`asMockLoggedInBuyer`），沿用 `at-room-booking` 既有的慣例——**只種 `accessToken`，刻意不種 `refreshToken` 與 `user`**：沒有 `refreshToken`，沒被 mock 到的請求得到 401 時攔截器不會整頁導去登入頁（種了的話會把案例弄壞）；沒有 `user`，共用頁首不會多發請求。各規格檔頭「(auth) 頁沒有登入守門」的過期敘述一併更新。

### 3.3 驗證

- **新規格 `at-buyer-auth-wall.spec.ts`（17 個案例）對沒有守門的基準建置：12 紅 5 綠**。`WALL-01`×11（沒登入開 `/orders`、`/orders?tab=returns`、`/orders/{id}`、`/addresses`、`/notifications`、`/bookings`、`/returns`、`/account`、`/checkout`、`/support/tickets`、`/auth/profile`）全紅，訊息都是「期望 `/login?redirect=…`、實得停在原頁」；`WALL-02`×5（認證流程頁面不被擋）是對照，本來就綠；`WALL-03`（登入後回 `/orders` 並看到訂單頁、整頁重新載入仍留在 `/orders`）紅。
- **含守門的最終建置：17／17 全綠**；5 支被波及的 mock 規格 **36／36**（基準加假登入 36／36，最終建置 36／36）；與登入／登出／買家頁面直接相關的既有規格（`at-login-landing-real`、`at-buyer-pages`、`at-oauth-callback`）**16／16**。
- **突變 5 組全數擊殺**（每組各自建置、每個案例各自一輪；先跑一組無突變的基準，5 個代表案例全綠）：

| 突變 | 擊殺的案例 | 失敗訊息（節錄） |
|---|---|---|
| G1 守門從不導向 | `WALL-01`／`WALL-03` | 期望 `/login?redirect=%2Forders`、實得 `/orders` |
| G2 守門不排除認證流程頁面 | `WALL-01`、`WALL-02`（`/login`、`/register`）、`WALL-03` | 登入標題找不到；`/register` 被導去 `/login`；`?redirect=` 被守門自己洗掉（實得 `/login`） |
| G3 導去不帶原頁的 `/login` | `WALL-01`／`WALL-03` | 期望帶 `?redirect=%2Forders`、實得 `/login` |
| G4 用 hydration 快照判斷 | `WALL-03` | 精確失敗在「整頁重新載入」那步：期望 `/orders`、實得 `/login`；`WALL-01` 仍綠 |
| G5 對登入狀態變化做反應 | `LOGIN-04`（Sprint 301 的案例） | 精確失敗在「登出」那步：期望 `/login`、實得 `/login?redirect=%2Fcart` |

- **我對 G2 的預期寫錯**：我預測 `WALL-01:/orders` 是綠的對照，實際也紅——它的第二個斷言是導到登入頁後要看到登入標題，守門把登入頁藏起來時當然失敗。是擊殺它的測試比我預期的多，不是測試漏洞。

## 4. gate 註冊等待上限 6→20 秒（已完成）

`frontend/e2e/helpers/auth.ts` 的 `REGISTER_RESULT_TIMEOUT_MS` 6_000 → 20_000，註解寫明原因與副作用。這是「不再被偶發的拖慢判死」，**不是診斷成因**：Sprint 301 gate 第一次失敗時沒留後端日誌，成因仍是「很可能是機器爭用」而非已證實；要驗證這個調整有沒有用，得等下一次冷啟動拖慢再現。本輪 gate 第一次執行就全綠，且這次有留下後端日誌（自動結束的複製迴圈）：整套 94 筆註冊的後端處理時間最慢 1.56 秒（就是整套最前面、上次失敗的那兩個案例同時註冊）、平均 0.61 秒，與 Sprint 301 第二次的 1.7 秒一致——也就是這次**沒有再現拖慢**，所以仍然沒有失敗當下的診斷資料。副作用見 §1.3（限流重試每輪最多多等 20 秒；Sprint 301 健康那次 gate 的後端日誌沒有任何限流拒絕的紀錄）。

## 5. 驗證總表

| 檢查 | 結果 |
|---|---|
| `npm run lint`（`--max-warnings 0`）、`npx tsc --noEmit` | 皆退出碼 0（守門、版面、輔助函式與被改的規格） |
| 後端單元＋整合針對性 | `AnalyticsServiceTest` 18／18、`AnalyticsRealDbIntegrationTest` 4／4；後端突變 5 個全數擊殺 |
| 前端守門 | 基準 12 紅 5 綠 → 最終建置 17／17；mock 規格 36／36；相關既有規格 16／16；前端突變 5 組全數擊殺 |
| 全量後端 `mvn -o clean verify`（全新測試 DB；含 Checkstyle、單元、整合） | `BUILD SUCCESS`（17 分）；Checkstyle main／test 皆 0 違規；**單元 2324、整合 888**，皆 0 失敗／0 錯誤／0 跳過，日誌中 `<<< FAILURE`／`<<< ERROR` 標記 0 個。與 Sprint 301（2320／887）相比單元 +4（`AnalyticsServiceTest`）、整合 +1（`AnalyticsRealDbIntegrationTest`），正好等於本輪新增的測試數；`ApiRouteDocDriftTest` 2／2、`AnalyticsServiceTest` 18／18、`AnalyticsRealDbIntegrationTest` 4／4 都有實際跑到。從日誌核對，沒有只信背景任務的退出碼 |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **第一次執行即全綠：276 案例，272 通過／4 跳過／0 失敗（7.3 分鐘）**，閘門顯示「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」。案例數由 Sprint 301 的 259 增為 276＝新增的 17 個（`at-buyer-auth-wall`），都在整套裡執行並通過。被守門波及的 5 支 mock 規格（`at-order-refund` 3、`at-notification-inbox-links` 3、`at-booking-cancel-refund` 5、`at-booking-payment` 11、`at-room-booking` 14 個案例）與 `at-login-landing-real` 5 個也都在整套裡通過——**我的影響範圍掃描沒抓到的規格沒有出現失敗**（掃描只涵蓋字面路徑的 `page.goto`，所以這點是由完整 gate 證明的，不是由掃描證明）。4 個跳過與 Sprint 301 的跳過數相同（Playwright 摘要只列數量，沒有逐一核對是哪 4 個） |
| pre-commit、pre-push、雲端 CI | 皆通過，詳見 §9：雲端 run 39927617437 三個 job 皆 success（`headSha` 一致）；雲端日誌確認整合測試 888／0 失敗、`AnalyticsRealDbIntegrationTest` 4／4 |

## 6. 過程中的失誤與修正

1. **整合測試的數字湊巧，讓 M5 在整合層倖存**（§2）：靠突變驗證才發現，改數字後重驗。
2. **我又把 `stack.sh fe` 接上 `| tail` 卡住了**：這是 Sprint 301 剛寫進記憶的坑（`stack.sh fe` 留下的背景子殼層握著管線）。前一步 `stack.sh up | tail` 沒事，我誤以為 `fe` 也沒事。前端本身其實已經起來，只是 `tail` 等不到 EOF；只殺卡住的 `tail`（沒碰前端伺服器），再改用不接管線的方式。
3. **寫規格時發現 `loginOnly` 會先 `goto('/login')`、把 `?redirect=` 洗掉**：沒辦法驗證「從 redirect 網址登入後回原頁」，改用規格內自己的表單填寫函式（在跑之前就改了）。
4. **我對突變 G2 的預期寫錯**（§3.3）。
5. **影響範圍掃描的第一版條件太寬**（§1.3）：收窄後才找出 `at-room-booking` 的 3 個案例。
6. **既有單元案例的 `any()` stub 會在新實作下被算兩次**（§2）：先跑紅燈才看到，有意識地改寫而不是調到通過。

## 7. 限制（誠實揭露）

1. **守門只在前端**：後端各端點本來就各自要求登入，這次沒動；沒登入的人在導向前，頁首與頁尾之間會短暫是空白。
2. **`loginHref` 不保留 `#片段`**（沿用 Sprint 301 的決定）。
3. **登入後回原頁的完整流程只用 `/orders` 驗證**；其餘 10 個路徑只驗證「被導去 `/login?redirect=…`」。`(auth)` 下的子路由（例如 `/returns/new`、`/bookings/[id]/payment/success`）由同一個版面層守門涵蓋，但沒有逐一做 E2E。
4. **`DEF-412` 沒有走完真實的退款流程驗證**（Stripe 與 mock 付款的退款管線都沒走），退款終態以測試資料列模擬（Sprint 301 的實測也是用 SQL 寫入終態）。
5. **gate 註冊逾時的成因仍未證實**，6→20 秒只是不再被偶發拖慢判死（§4）。
6. 影響範圍掃描只涵蓋 `page.goto('字面路徑')` 的寫法；用變數組出路徑或靠點擊導覽進入受保護頁的規格不會被抓到，要靠完整 gate 揭露。
7. `DEF-410`（`at-m15` 7／12 零斷言，現在可動）與 `DEF-411`（定價／日曆預覽擁有權檢查沒排除系統租戶佔位值，讀碼推論）仍未處理，不需產品決策。

## 8. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 全額退款的付款以 `amount` 計，而不是照字面加總 `refundedAmount` | webhook 路徑只改狀態、不寫 `refundedAmount`，照字面做會把 webhook 退掉的全額退款算成 0 | 改成純加總 `refundedAmount`（並接受 webhook 路徑漏算，或另補寫入 `refundedAmount`） |
| 2 | 守門不對登入狀態的變化做反應 | 否則登出時與登出自己的導向互搶，網址帶 `?redirect=` | 讓守門也處理登出（需另設計登出的導向） |
| 3 | 沒登入時不渲染頁面內容（而不是先渲染再導向） | 避免發出必然 401 的請求與閃出「無法載入…」 | 改成先渲染骨架畫面 |
| 4 | `/reviews/product/[listingId]` 也納入守門 | 後端列表端點本來就要求登入，訪客用不了 | 把它從守門排除 |
| 5 | mock 型規格用只種 `accessToken` 的假登入 | 沿用 `at-room-booking` 既有慣例；不種 `refreshToken` 以免攔截器把案例導走 | 改為真實登入（成本高） |
| 6 | `asMockLoggedInBuyer` 做成共用輔助函式，但不重構 `at-room-booking` 既有的本地 `asLoggedInBuyer` | 只做必要的改動 | 順手統一 |
| 7 | 調高上限只改一個常數，不順手改成「redirect 或狀態碼誰先到就處理」 | 使用者選的是「調高等待上限」 | 改寫 `submitRegister` 的等待邏輯，消除限流重試的副作用 |

## 9. 雲端 CI 與狀態

- commit `759a9ebe`，於 2026-10-23 push（`fdd16bbd..759a9ebe main -> main`）。以推送日誌，以及 `git fetch` 後本機 HEAD 與 `origin/main` 同為 `759a9ebe`、ahead／behind 為 0／0 核實；沒有採信背景任務通知的退出碼。
- pre-commit 一次通過：Checkstyle、Maven compile、核心層快速測試（`Maven quick test passed (core layer)`）、ESLint、`tsc --noEmit`、secret 掃描，以及 PRD 觸發的 schema 文件守門（93 個 Flyway 遷移的實際 schema 與 SRD／PRD 一致）。commit 前測試 DB 已換成全新容器。
- pre-push 輕量守門通過：本機 act 跑了**兩輪** Backend Unit Tests（Run Unit Tests 3 分 3 秒與 2 分 52 秒，皆 `Job succeeded`；與 Sprint 301 相同，act 被啟動兩次，我沒有查為什麼是兩輪）、Frontend Lint & Build（Type Check 28 秒、Build Frontend 57 秒，`Job succeeded`），schema 漂移守門「backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移」。push 之後 `docker ps -a` 為空。
- 雲端 CI run [39927617437](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39927617437)：`completed／success`，三個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 success，約 13 分 12 秒（08:51:19Z～09:04:31Z）。`gh run watch` 結束後另以 `gh run view --json status,conclusion,jobs,headSha` 交叉確認，`headSha` 與本機 commit 一致。
- 下載整合測試 job 的雲端日誌（196,189 行、約 51 MB）確認：整合測試總計 **888／0 失敗／0 錯誤／0 跳過**（與本機相同）、`AnalyticsRealDbIntegrationTest` **4／4**（含新增案例，在雲端全新的 Postgres 上）、`BUILD SUCCESS`。這次一開始就把下載放背景並加 `--allow-escape-sequences`，去除跳脫碼後才統計，沒有重蹈 Sprint 301「第一次正規式沒命中」的覆轍。
- 雲端三個 job 不含 Playwright E2E；E2E 以本機 `make validate-e2e` 為準（§5）。
- 之後的純文件回填 commit 不在本文件記錄自己的 hash 與雲端結果（commit 無法包含自己的 hash）。

## 10. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/java/com/nextkey/ecommerce/core/analytics/AnalyticsService.java` | `refundedMoney`；退款分別查 `REFUNDED`／`PARTIALLY_REFUNDED`；`netRevenue` 只扣仍計入營收的訂單上的退款（`DEF-412`） |
| `backend/src/test/java/.../core/analytics/AnalyticsServiceTest.java` | 新增 4 個案例、改寫 1 個既有案例（`DEF-412`） |
| `backend/src/test/java/.../core/analytics/AnalyticsRealDbIntegrationTest.java` | `seedOrder` 回傳 id、新增 `seedPayment` 與一個真實 PostgreSQL 案例（`DEF-412`） |
| `frontend/src/components/layout/AuthGuard.tsx` | 新增（`DEF-413`） |
| `frontend/src/app/(auth)/layout.tsx` | 內容區外包 `AuthGuard`（`DEF-413`） |
| `frontend/src/lib/auth-redirect.ts` | 匯出 `isAuthFlowPath` |
| `frontend/e2e/at-buyer-auth-wall.spec.ts` | 新增 17 個案例（`DEF-413`） |
| `frontend/e2e/helpers/mockLogin.ts` | 新增 `asMockLoggedInBuyer` |
| `frontend/e2e/at-order-refund.spec.ts`、`at-notification-inbox-links.spec.ts`、`at-booking-cancel-refund.spec.ts`、`at-booking-payment.spec.ts`、`at-room-booking.spec.ts` | 補假登入、更新過期的檔頭敘述 |
| `frontend/e2e/helpers/auth.ts` | `REGISTER_RESULT_TIMEOUT_MS` 6→20 秒（gate 逾時對策） |
| `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` | v1.0.10 → v1.0.11（§9.16 退款算法、§10.1.1 C 端登入守門與 `/checkout` 需登入） |
| `docs/02_architecture/api/API_M13_Seller_Dashboard.md` | v1.2 → v1.3（§3.3、§7 `DEF-412` 已修復） |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-412`／`413` 結案 |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 302 row |
| `docs/04_planning/SPRINT_302_PLAN.md` | 新增（本文件） |
