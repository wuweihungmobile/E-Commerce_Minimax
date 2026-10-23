# Sprint 301 Plan — 落地使用者在 Sprint 300 收尾的四個回答：DEF-407（營收口徑統一）、DEF-408（買家讀自己訂單的物流）、DEF-409（登入後依角色分流＋回原頁）、DEF-401 子問題（ESLint 禁空字串 SelectItem），登記 DEF-412、DEF-413

## 1. 起點與範圍

### 1.1 起點（這次是使用者本人的回答，不是我的推論）

[Sprint 300](SPRINT_300_PLAN.md) 收尾時我用 `AskUserQuestion` 問了四題；使用者**本人**全部選了建議選項（真實的互動輸入，可在對話紀錄中查到）：

| 題目 | 使用者的回答 |
|---|---|
| `DEF-407` 「營收」的定義 | 已付款且未取消（建議） |
| `DEF-408` 買家看自己訂單的物流 | 只開放讀取給買家本人（建議） |
| `DEF-409` 登入後去哪裡 | 依角色分流＋回原頁（建議） |
| `DEF-401` 子問題：要不要加 ESLint 規則禁止空字串 `SelectItem` | 新增 error 級規則（建議） |

這四個回答就是授權。本輪做的就是照這四個決定落地，**沒有**替使用者決定其他待決項目。

### 1.2 成功標準（開工時定義）

1. 四項依使用者的決定落地，文件（PRD／API 規格／指南）同步。
2. 每個新測試必須能因為正確的原因轉紅：對修復前的建置跑，加上針對實作的突變；多個缺陷各有**獨立**的紅燈（serial 下第一個失敗會讓後面不執行）。
3. 前端的整合（登入頁真的用了新邏輯）用**真實後端、真實登入表單**驗證，不只測純函式。
4. 全量後端回歸（改了生產程式碼）與完整 `make validate-e2e` 通過。
5. 順著量到的相鄰缺陷要**實測**再登記，不拿讀碼推論當事實寫。

### 1.3 實作前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「`revenue30d` 要在 UI／文件標成『已實現營收』」 | 前端 `frontend/src` 對 `revenue30d` **零命中**——沒有任何 UI 顯示它，所以「標成不同名稱」只能落在 DTO Javadoc、服務註解與 API 文件；欄位名不改，以免破壞既有契約 |
| 「PRD 對營收的定義」 | PRD 只有「營收」作為功能名稱，**沒有定義**；所以使用者的裁決是**新增**一條規格（§9.16），不是改寫舊條文 |
| 「各頁遇 401 是 `router.push('/login')`」 | 改了 50 個頁面／元件（`router.push(loginHref())`）；剩下 30 處 `router.push('/login')` 用腳本逐處檢查，前 8 行內**全部**出現 `logout`，沒有例外，皆為登出處理函式 |
| `loginHref()` 呼叫處 | 51 處 ＝ 50 個頁面／元件 ＋ axios 攔截器 1 處；含 `loginHref` 字樣的檔案 52 個（再加定義檔 `auth-redirect.ts`） |

## 2. DEF-407：營收口徑統一（已完成）

**改了什麼**：`AnalyticsService` 新增 `REVENUE_STATUSES`（`PAID`／`CONFIRMED`／`SHIPPING`／`DELIVERED`／`COMPLETED`）、`countsAsRevenue`、`sumRevenue`；首頁今日／昨日／本月／本年、每日趨勢（`buildRevenueBuckets`）、報表總營收全部改走同一個加總函式，不可能再各自分歧。平均客單價的分母改為入帳訂單數（`totalOrders` 仍是區間內全部訂單數）。`revenue30d`（僅 `COMPLETED`）保留，在 `SellerDashboardDto` Javadoc 與 `SellerDashboardService` 註解標成「已實現營收」。訂房維持不含（PRD 未規定，另案）。

**驗證**：

- **實作前先看紅燈**：三個新／更新的單元測試轉紅，實際值是首頁 1300（預期 200）、平均客單價 75（預期 150）、趨勢 420（預期 300），都是新口徑造成，不是別的原因。
- **實作後**：`AnalyticsServiceTest` 14／14（12→14：新增 2 個，另更新 1 個既有案例的平均客單價預期值）；`AnalyticsRealDbIntegrationTest` 3／3（2→3：新增 1 個真實 PostgreSQL 案例，同樣的狀態走真實 SQL 路徑）。
- **突變**：M1 把判定退回舊的「排除 `CANCELLED`／`REFUNDED`」→ 被 3 個單元案例與真實資料庫案例擊殺；M2 把平均客單價分母改回全部訂單 → 被 2 個單元案例與真實資料庫案例擊殺。還原後以 `git diff` 核對只剩預期的實作變更。
- **真實全棧 E2E**（`at-dashboard-revenue.spec.ts`，已付款 500＋未付款 500）：`E2E-REVENUE-02`（首頁今日／本月／本年營收）對修復前的建置紅（今日營收 `$1,000`，預期 `$500`），`E2E-REVENUE-03`（報表平均客單價）紅（`$250`，預期 `$500`）；修復後綠。同檔 `E2E-REVENUE-01`（Sprint 300 的 `DEF-405`）本來就綠，是未受影響的對照。

## 3. DEF-408：買家讀自己訂單的物流（已完成）

**改了什麼**：`LogisticsService` 新增 `checkOrderReadAccess`／`checkLogisticsReadAccess`。四個讀取方法（`getLogistics`／`getLogisticsByOrderId`／`trackLogistics`／`getTrackingDetail`）在原本的「店鋪租戶或 admin」之外，另放行 `order.userId == 目前使用者`。**比對使用者 id 而不是租戶**：沒有店鋪的買家共用系統租戶佔位值（DEF-190），比對租戶會讓任兩個買家互通；目前使用者或訂單的買家欄位為 `null` 時不放行（兩邊都是 `null` 不算相等）。建立／更新狀態／取消仍只走 `checkOrderTenant`／`checkLogisticsTenant`，買家本人仍是 403。

**驗證**：

- `LogisticsServiceTenantAccessTest` 新增 9 個單元測試（8→17）；`LogisticsService*Test` 四個類別合計 38／38。
- **突變**（三個，各被預期的測試抓到）：M1 拿掉買家本人放行 → 被恰好 4 個「買家本人可讀」案例擊殺；M2 放寬成任何已登入使用者 → 被 4 個「另一位買家仍須 `E-1007`」案例擊殺；M3 讓讀取放寬外溢到寫入路徑 → 被恰好 3 個寫入案例（建立／更新／取消）擊殺。還原後以 `git diff` 核對。
- **真實全棧 E2E**（`at-logistics-buyer-real.spec.ts`）：**物流單走真實出貨路徑建立**（店主確認訂單後 `POST /v2/logistics`）。Sprint 300 的播種是直接 `PATCH` 訂單狀態、沒有物流單，所以當時只觀察到 403、沒觀察到「物流單存在卻被藏起來」——這是 Sprint 300 的已知限制，這次補上。對修復前的建置：`E2E-LOGISTICS-01`（找不到「物流商：黑貓宅急便」）與 `02`（讀取回 403 `E-1007`）紅；`03`（另一位買家四個端點仍 403）與 `04`（買家不得更新／取消／建立）本來就綠，是守住「放寬不外溢」的對照案例。

## 4. DEF-409：登入後依角色分流＋回原頁（已完成）

### 4.1 設計與改了什麼

新增 `frontend/src/lib/auth-redirect.ts`：

- `landingPathForRole`：`BUYER`→`/`、`STORE_OWNER`／`STORE_STAFF`／`SELLER`／`HOST`→`/dashboard`、`ADMIN`／`SUPER_ADMIN`→`/admin`，其餘→`/`。
- `safeRedirectTarget`：只收以 `/` 開頭、同源、正規化後不以 `//` 開頭、且不是登入／註冊／忘記密碼／重設密碼／Email 驗證／OAuth 流程頁面的站內路徑，其餘一律回 `null`（改用角色預設落地頁）。`router.push` 不可傳入未驗證的網址（Next 文件：XSS 風險）。
- `loginHref`：要求先登入時的導向網址，帶 `?redirect=`；已在流程頁面或伺服器端渲染時退回 `/login`。

套用範圍：登入頁（`?redirect=` 驗證後優先，否則角色落地）；OAuth 回呼頁（只做角色分流）；axios 401 刷新失敗的整頁導向改用 `loginHref()`；**50 個頁面／元件**的 `router.push('/login')`（未登入牆）改成 `router.push(loginHref())`；**30 處登出維持不帶 `?redirect=`**（登出是離開，下一個登入的人不該被送回前一個人的頁面）。

### 4.2 驗證

- **純函式規格** `at-auth-redirect.spec.ts`：50 個案例——角色表 11；接受 6／拒絕 22 的 `?redirect=` 表（含 `//host`、`/\host`、夾帶定位字元、`/a/..//host`、`javascript:`，以及會讓 `new URL` 丟例外的 `//`、`/\`、`//host:99999`）；`loginHref` 的往返與防繞圈 11 個。
- **真實全棧、走真實登入表單**的 `at-login-landing-real.spec.ts`（`E2E-LOGIN-01`～`05`）與 `at-oauth-callback.spec.ts` 新增的 3 個角色案例（以 `page.route` 假造後端的 OAuth 登入回應，不碰真實 provider）。
- **對修復前（基準）建置的紅燈**：`01`（買家落在 `/dashboard`）、`03`（管理員落在 `/dashboard`）、`04`（第一步就失敗：期望 `/login?redirect=%2Fcart`、實得 `/login`）紅；`02`（店主進 `/dashboard`）本來就綠。`05` 與 OAuth 案例是設計時發現原本沒有案例守住這兩條路徑後補的，沒有對基準建置跑過，改用突變等價地還原舊行為取得紅燈（見下）。

### 4.3 突變驗證

- **純函式層 22 個突變，全數擊殺**（驅動腳本：還原→套用→跑規格→還原，舊字串須恰好出現一次，最後逐位元比對）：拿掉同源檢查／正規化後的 `//` 檢查／登入流程頁面檢查／開頭斜線檢查／`try/catch`；流程頁面改前綴比對、清單各少一項（`/oauth`、`/register`、`/reset-password`、`/verify-email`、`/forgot-password`）；目標漏掉 `search` 或 `hash`；角色表各種改壞；`loginHref` 拿掉防繞圈／不編碼／漏 `search`／伺服器端不退回。
  - **第一輪有倖存的風險**：原本的規格沒有任何案例走到 `catch`、沒有 `/reset-password`／`/verify-email`、`loginHref` 的搜尋參數沒有含 `&` 的案例。先補了案例才跑完整批，不是靠運氣全殺。
- **整合層 6 個突變，各由預期的案例、因預期的原因擊殺**（每個案例單獨一輪，避免 serial 遮蔽；先跑一組無突變的基準：`LOGIN-01`～`05` 與 `OAUTH` 三個角色，共 8 個測試全綠）：

| 突變 | 擊殺的案例 | 失敗訊息（節錄） |
|---|---|---|
| 登入頁不驗證 `redirect` | `LOGIN-01` | 期望 `/`，實得 `chrome-error://chromewebdata/`——瀏覽器真的跟著 `//evil.example.com` 走了 |
| 攔截器導去不帶參數的 `/login` | `LOGIN-05` | 期望 `/login?redirect=%2Forders`，實得 `/login` |
| OAuth 回呼一律 `/dashboard` | `OAUTH`（BUYER、ADMIN） | 期望 `/`、`/admin`，實得 `/dashboard`；STORE_OWNER 維持綠 |
| 登入頁忽略 `?redirect=` | `LOGIN-04` | 第 94 行（回原頁那步）期望 `/cart`，實得 `/` |
| 登入頁不分角色一律 `/dashboard` | `LOGIN-01`、`03` | 期望 `/`、`/admin`，實得 `/dashboard`；`02` 維持綠 |
| 登出時帶上 `?redirect=` | `LOGIN-04` | 第 98 行（登出那步）期望 `/login`，實得 `/login?redirect=%2Fcart`；第 94 行那步有通過，所以歸因到這個突變 |

## 5. DEF-401 子問題：ESLint 禁空字串 SelectItem（已完成）

`frontend/eslint.config.mjs` 新增 `no-restricted-syntax`（error）三個選擇器：`<SelectItem value="">`、`value={''}`、空的樣板字串。**驗證**：用 `eslint --stdin` 餵三種禁止寫法與三種合法寫法（`"ALL"`、`{'x'}`、變數）：前者各報一個 error（第 6／7／8 行，訊息各自對應）、後者零報錯；`npm run lint`（`--max-warnings 0`）現況 0 個違規。這是**error** 不是 warning。`FRONTEND_PRECOMMIT_GUIDE.md` 已補說明。

## 6. 登記、待使用者裁決（實測後登記，沒有擅自做）

兩個都是順著做 `DEF-407`／`DEF-409` 時量到的相鄰缺陷。

| 登記 | 一句話 | 為什麼不擅自做 | 我的建議 |
|---|---|---|---|
| `DEF-412` | 報表 `netRevenue` 扣退款的算法有兩處錯：全額退款被扣兩次（實測 `-500`）、部分退款完全不被扣（實測退 200 後仍是 450） | 「退款怎麼算進營收」是產品決策 | `totalRefunds`＝所有付款 `refundedAmount` 加總；`netRevenue`＝計入營收的訂單金額 − 這些訂單上的部分退款；補真實 PostgreSQL 整合測試 |
| `DEF-413` | 買家頁面幾乎都沒有登入牆：9 個頁面實測只有 `/cart` 會導去登入；其餘停在原頁顯示「無法載入…」，`/returns` 還露出後端原文 `Authentication required`，`/checkout`、`/account` 整頁照常渲染 | 要不要加牆、加在哪、`/checkout` 是否允許訪客，都是產品／UX 決策；PRD 沒有逐路由的存取規則 | 在買家頁面共用的版面層加登入守門（`router.push(loginHref())`，與 `/cart` 一致；公開頁面排除）；`/checkout` 另問 |

**`DEF-412` 的證據鏈**：(1) 讀碼確認 `PaymentStateService` 的退款處理——達全額才把付款與訂單都轉 `REFUNDED`，否則付款轉 `PARTIALLY_REFUNDED`、訂單狀態不變（Stripe webhook 路徑也會把兩者設成 `REFUNDED`）；(2) 用真實端點＋真實店主登入實測：基準（店 A：500／0／500，店 B：450／0／450）→ 以 SQL 寫入退款終態 → 再呼叫（A：`totalRevenue 0`、`totalRefunds 500`、**`netRevenue -500`**；B：`totalRefunds 0`、**`netRevenue 450`**）→ 還原後數字回到基準；(3) **限制**：退款終態是用 SQL 依應用程式的寫法寫入的，**沒有走完真實的退款流程**。

**`DEF-413` 的證據**：真實瀏覽器、乾淨 context、完全沒有 token，逐頁量「最後停在哪、主要內容區寫什麼」。第一版探針用「畫面含『登入』」當判斷，被頁首的登入連結誤導（任何訪客頁面都有），改成只取 `<main>` 的文字才得到可信的結果。

另有兩項不需產品決策、仍未處理：`DEF-410`（`at-m15-e2e.spec.ts` 7／12 案例零斷言；它的正確斷言取決於本輪已落地的 `DEF-409`，現在可以動了）與 `DEF-411`（定價／日曆預覽端點的擁有權檢查沒排除系統租戶佔位值，讀碼推論、沒有實測）。

## 7. 驗證總表

| 檢查 | 結果 |
|---|---|
| `npx tsc --noEmit`、`npm run lint`（`--max-warnings 0`） | 皆退出碼 0、無輸出 |
| `ApiRouteDocDriftTest`（改完 API_M11／M13 後單獨跑） | 2／2 |
| 純函式規格 `at-auth-redirect.spec.ts` | 50／50（3.0 秒）；22 個突變全數擊殺 |
| 真實全棧新規格（無突變基準，最終程式碼的正式建置） | `LOGIN-01`～`05` 與 `OAUTH` 三個角色全綠 |
| 整合層突變 | 6 個各由預期案例擊殺（§4.3） |
| 後端突變 | DEF-408 三個、DEF-407 兩個，皆由預期的測試擊殺（§2、§3） |
| 全量後端 `mvn -o clean verify`（全新測試 DB；含 Checkstyle、單元、整合） | `BUILD SUCCESS`（19 分 47 秒）；Checkstyle main／test 皆 0 違規；**單元 2320、整合 887**，皆 0 失敗／0 錯誤／0 跳過，日誌中 `<<< FAILURE`／`<<< ERROR` 標記 0 個。與 Sprint 300（2309／886）相比單元 +11（`AnalyticsServiceTest` +2、`LogisticsServiceTenantAccessTest` +9）、整合 +1（`AnalyticsRealDbIntegrationTest` +1），正好等於本輪新增的測試數；`ApiRouteDocDriftTest` 2／2、`LogisticsServiceTenantAccessTest` 17／17、`AnalyticsServiceTest` 14／14、`AnalyticsRealDbIntegrationTest` 3／3 都有實際跑到。從日誌核對，沒有只信背景任務的退出碼 |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **第一次執行：259 案例，253 通過／2 失敗／4 跳過（10.0 分鐘）**——2 個失敗是既有規格 `at-account-data-rights.spec.ts` 的兩個案例，也是整套的**第 1、2 個**，訊息相同：`submitRegister: 註冊失敗，仍停在 /register（status=undefined, rateLimited=false）`；失敗當下的頁面快照裡送出按鈕是 `Creating account...`〔disabled〕，即註冊請求還在途中就超過輔助函式的 6 秒等待上限。我沒有只重跑那兩支，而是**完整重跑整套**：**第二次執行 259 案例，255 通過／4 跳過／0 失敗（8.6 分鐘），沒有 flaky 項目**，閘門顯示「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」，backend 啟動檢查「entity 與 Flyway schema 對齊，無漂移」；上次失敗的那兩個案例這次仍是第 1、2 個，通過。案例數由 Sprint 300 的 195 增為 259＝新增的 64 個（`at-auth-redirect` 50、`LOGIN-01`～`05`、`LOGISTICS-01`～`04`、`REVENUE-02`／`03`、`OAUTH-LANDING` 三個角色），逐一從日誌確認都在整套裡執行並通過。跳過數 4 與 Sprint 300 相同（Playwright 摘要只列數量，本輪沒有逐一核對是哪 4 個）。第一次失敗的分析見 §8 第 7 點與 §9 第 11 點 |
| pre-commit、pre-push、雲端 CI | 皆通過，詳見 §11：雲端 run 39909836033 三個 job 皆 success（`headSha` 一致）；雲端日誌確認整合測試 887／0 失敗、`AnalyticsRealDbIntegrationTest` 3／3 |

## 8. 過程中的失誤與修正

1. **前端整合層突變驗證的驅動腳本連續出了四次錯，每次都是我自己的腳本，不是測試或程式碼**：
   - 第一次：`stack.sh` 沒有執行權限，Z 組建置完就崩，**沒有任何突變結果**。
   - 第二次：驅動用管線接 `stack.sh` 的輸出，而 `stack.sh` 啟動前端時留下的背景子殼層一直握著那條管線，驅動永遠等不到結束，**卡死了將近 3 小時**。沒有發完成通知，是使用者問「還有任務在進行嗎」我才去看輸出檔發現的。修法：輸出導到檔案並加逾時；現在每個子行程都有逾時。
   - 第三次：`stack.sh` 的 `echo "…（$DIR）"` 在 Python 子行程下報 `DIR�: unbound variable`。我一開始猜是語系問題，**方向猜反了**（以為 C 語系會壞），用實測才修正：Python 3.9 偵測到 C 語系會自動把 `LC_CTYPE` 設成 `C.UTF-8` 傳給子行程（PEP 538），macOS 內建 bash 3.2 在 UTF-8 語系下會把變數後緊接的全形括號吃進變數名。改成 `${DIR}`。
   - 第四次：上一輪結束後留下的備份目錄 `febak`，讓我自己寫在啟動指令裡的防呆短路成 `echo`，背景任務通知顯示「完成、退出碼 0」但驅動根本沒執行。**我當時還對使用者說「已啟動」，嚴格說它啟動後立刻結束了**；是讀了輸出檔才發現。之後驅動改成會自己處理殘留（工作樹與舊備份逐位元相同才刪，不同就中止）。
   - 每次重試前，我都用事先記錄的 SHA-256 獨立核對四個來源檔仍是原版，才清備份、才重跑。
2. **第一版純函式規格太鬆**（§4.3）：突變驗證前先檢視，發現 `catch` 分支、兩個流程頁面、搜尋參數的 `&` 都沒有案例，補了再跑；否則「拿掉 `try/catch`」「不做編碼」這類突變會倖存。
3. **設計 `E2E-LOGIN-05` 時才發現 axios 攔截器與 OAuth 落地兩條改過的路徑沒有任何案例**：補了 `E2E-LOGIN-05` 與 `E2E-OAUTH-LANDING`，並用突變等價地還原舊行為取得紅燈。
4. **探針的判斷依據錯過一次**（§6）：用「畫面含『登入』」判斷是否被導去登入，被頁首的登入連結誤導；改取 `<main>` 文字。
5. **並行的 Bash 呼叫共用工作目錄**：其中一個 `cd docs` 讓另一個呼叫對 API 文件的 `grep` 回報「No such file」，我差點據此認為檔案不存在；改用絕對路徑重查才確認檔案存在。
6. **實作階段**（續自上一個對話視窗的紀錄）：測試類別被 IDE 編成含錯誤的 class（`Unresolved compilation problem`），刪掉重編；測試裡用錯了列舉型別（模型的 vs. DTO 的 `LogisticsProvider`）；axios 改完後多了一行用不到的 `eslint-disable`，被 `--max-warnings 0` 擋下；回復 M3 突變時 Edit 的錨點不符，重讀實際行後才改對。
7. **第一次完整 gate 有 2 個失敗，是已知型態的「冷啟動註冊逾時」再現，成因仍未證實**（§7）。兩個失敗都是整套的第 1、2 個案例（兩個 worker 同時註冊）。我先排除與本輪改動的關係：註冊流程相關檔案（註冊頁、`services/auth.ts`、`helpers/auth.ts`）本輪皆未改動（`git status` 確認），其餘上百個同樣呼叫 `registerAndLogin` 的案例都通過；失敗當下的頁面快照顯示請求還在途中。再量環境：這台機器只有 **4 個核心**，gate 期間 1／5／15 分鐘負載平均是 `18／47／43`（約 10 倍超載；之後我什麼都沒跑，1 分鐘負載也在 14～15）；在負載 17 的機器上量全新後端，第一次註冊 1.46 秒、之後約 0.45～0.5 秒，所以**單靠 JVM 冷啟動解釋不了**，需要大量 CPU 競爭。**第一次沒有留後端日誌**（gate 腳本收尾會刪），所以無法確認當時後端到底卡在哪。完整重跑前我加了一個會自己結束的日誌複製迴圈；第二次全綠，留下的後端日誌顯示同樣是整套最前面的兩個同時註冊，後端處理時間 **1.7 秒**（13:16:25.504 → 13:16:27.215），第三、四個註冊分別 0.76、0.58 秒。結論只能是：第一次那 2 個案例遇到的是某個暫時性的拖慢（很可能是機器爭用，但沒有證據證明）。本輪沒有改 `scripts/validate-e2e.sh`，也沒有改輔助函式的 6 秒上限。

## 9. 限制（誠實揭露）

1. **`DEF-412` 的退款終態是 SQL 寫入的**，沒有走完真實的退款流程（Stripe 與 mock 付款的退款管線都沒走）；已讀碼確認應用程式會寫出這兩種終態。
2. **`DEF-413` 的探針只涵蓋「載入」層、只用 Chromium、只看完全沒有 token 的訪客**；沒有看「token 過期但有 refresh token」的情況（那條由 `E2E-LOGIN-05` 守住）。`/checkout` 對訪客是不是刻意設計，PRD 沒有明說，我沒有猜。
3. **`loginHref` 只記住 pathname 加 search，不含 `#片段`**。
4. **OAuth 登入不支援 `?redirect=`**（它不經過本站登入頁）；OAuth 的測試用 `page.route` 假造後端回應，沒有碰真實 provider。
5. **「已實現營收」的名稱只在 Javadoc／文件**，前端沒有顯示該欄位，沒有 UI 可標。
6. **儀表板營收仍不含訂房**（PRD 未規定）。
7. **前端 `(auth)/orders/[id]/page.tsx` 仍以 `.catch(() => [])` 吞掉讀取物流的失敗**，日後若又出現 403，畫面仍只會顯示「尚未出貨」。
8. **ESLint 規則只認名稱剛好是 `SelectItem` 的元件**；改名匯入或自己包一層的寫法不在範圍。
9. **後端突變驗證用的是針對性測試（單一測試類別），不是每個突變都跑全量**；全量回歸只跑一次（§7）。
10. `E2E-LOGIN-05` 依賴「後端對不認得的 token 回 401」；已用 `curl` 確認（`/v2/orders` 與 `/v2/auth/refresh` 皆 401），但這是現況的假設。
11. **gate 的冷啟動註冊逾時仍未查明**（Sprint 299 首次記錄、本輪第二次出現）：本輪第一次執行再現、重跑未再現；只能說兩次執行的結果不同，不能說已解決或已找到成因（§8 第 7 點）。輔助函式 `REGISTER_RESULT_TIMEOUT_MS = 6_000` 在這台 4 核心、閒置負載 14～15 的機器上偏緊。可能的處置（調高上限、在 gate 腳本加一次預熱註冊、降低 worker 數）都是改共用基礎設施，我沒有擅自做，列在最後報告給使用者決定。

## 10. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | OAuth 登入成功只做角色分流、不支援 `?redirect=` | OAuth 回呼不經過本站登入頁，沒有 `?redirect=` 可讀 | 要我讓 OAuth 也回原頁，需另設計把原頁帶過 provider 的來回 |
| 2 | 登出不帶 `?redirect=` | 下一個登入的人不該被送回前一個人的頁面 | 登出也帶，改 30 處 |
| 3 | `loginHref` 不保留 `#片段` | `window.location.search` 與 pathname 足夠回到原頁；片段通常是頁內錨點 | 加上 `hash` |
| 4 | 平均客單價的分母＝入帳訂單數；`totalOrders` 維持區間內全部訂單數 | 使用者說「分母同一組」；`totalOrders` 是另一個欄位、有自己的語意 | 讓 `totalOrders` 也只算入帳訂單 |
| 5 | 寫入端點不放寬；另一位買家仍是 403 | 使用者說「只開放讀取」 | — |
| 6 | 新增 `E2E-LOGIN-05`、`E2E-OAUTH-LANDING` 與 `loginHref` 的純函式測試（超出四個回答字面範圍） | 這幾行是本輪改的，原本沒有任何案例守住；突變驗證證明不補就會倖存 | 刪掉這幾個案例 |
| 7 | `DEF-412`、`DEF-413` 只登記不修 | 退款怎麼算進營收、要不要加登入牆都是產品決策 | 回「依照建議」我就做 |
| 8 | PRD 升 v1.0.10；`API_M11` v1.3、`API_M13` v1.2 | 文件與實作要一致 | 還原那幾處 |

## 11. 雲端 CI 與狀態

- commit `4b493543`，於 2026-10-23 push（`11aa8817..4b493543 main -> main`）。以推送日誌，以及 `git fetch` 後本機 HEAD 與 `origin/main` 同為 `4b493543`、ahead／behind 為 0／0 核實；沒有採信背景任務通知的退出碼。
- pre-commit 一次通過：Checkstyle、Maven compile、核心層快速測試（`Maven quick test passed (core layer)`）、ESLint、`tsc --noEmit`、secret 掃描，以及 PRD 觸發的 schema 文件守門（`SRD_Database_Schema.md` 的 DDL、PRD §8.2 的欄位清單與 93 個 Flyway 遷移的實際 schema 一致）。commit 前測試 DB 已換成全新容器。
- pre-push 輕量守門通過：本機 act 跑了**兩輪** Backend Unit Tests（Run Unit Tests 5 分 42 秒與 7 分 3 秒，皆 `Job succeeded`；日誌顯示 act 被啟動兩次，我沒有查為什麼是兩輪）、Frontend Lint & Build（Type Check 1 分 15 秒、Build Frontend 3 分 3 秒，`Job succeeded`），schema 漂移守門「backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移」。push 之後 `docker ps -a` 為空（pre-push 清掉了 `nk-test-*`）。
- 雲端 CI run [39909836033](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39909836033)：`completed／success`，三個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 success，約 11 分 47 秒（06:09:46Z～06:21:33Z）。`gh run watch` 結束後另以 `gh run view --json status,conclusion,jobs,headSha` 交叉確認，`headSha` 與本機 commit 一致。
- 下載整合測試 job 的雲端日誌（195,857 行、約 51 MB）確認：整合測試總計 **887／0 失敗／0 錯誤／0 跳過**（與本機相同）、`AnalyticsRealDbIntegrationTest` **3／3**（含新增案例，在雲端全新的 Postgres 上）、`BUILD SUCCESS`。下載超過 120 秒的前景上限被轉到背景；日誌帶顏色跳脫碼，第一次的彙總行正規式沒有命中，去除跳脫碼後重查才得到數字（沒有把「沒命中」當成「沒有」）。
- 雲端三個 job 不含 Playwright E2E；E2E 以本機 `make validate-e2e` 為準（§7）。
- 之後的純文件回填 commit 不在本文件記錄自己的 hash 與雲端結果（commit 無法包含自己的 hash）。

## 12. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/java/com/nextkey/ecommerce/core/analytics/AnalyticsService.java` | `REVENUE_STATUSES`／`countsAsRevenue`／`sumRevenue`，所有營收加總與平均客單價分母改用（`DEF-407`） |
| `backend/src/main/java/com/nextkey/ecommerce/api/dto/SellerDashboardDto.java`、`core/seller/SellerDashboardService.java` | `revenue30d` 標為「已實現營收」（Javadoc／註解，`DEF-407`） |
| `backend/src/main/java/com/nextkey/ecommerce/core/logistics/LogisticsService.java` | `checkOrderReadAccess`／`checkLogisticsReadAccess`，四個讀取方法改用（`DEF-408`） |
| `backend/src/test/java/.../core/analytics/AnalyticsServiceTest.java`、`AnalyticsRealDbIntegrationTest.java` | 新增 2＋1 個案例並更新平均客單價預期值（`DEF-407`） |
| `backend/src/test/java/.../core/logistics/LogisticsServiceTenantAccessTest.java` | 新增 9 個案例（`DEF-408`） |
| `frontend/src/lib/auth-redirect.ts` | 新增（`DEF-409`） |
| `frontend/src/app/(auth)/login/page.tsx`、`src/app/oauth/callback/[provider]/page.tsx`、`src/lib/axios.ts` | 套用角色分流／`?redirect=`／`loginHref()`（`DEF-409`） |
| 50 個頁面與元件（`src/app/**`、`src/components/**`，含 `(auth)/cart`、`dashboard/**`、`admin/**`、`tenant/apply`） | `router.push('/login')` → `router.push(loginHref())`（`DEF-409`） |
| `frontend/eslint.config.mjs` | `no-restricted-syntax`：禁空字串 `SelectItem`（`DEF-401` 子問題） |
| `frontend/e2e/at-auth-redirect.spec.ts`、`at-login-landing-real.spec.ts`、`at-logistics-buyer-real.spec.ts` | 新增（`DEF-409`／`408`） |
| `frontend/e2e/at-dashboard-revenue.spec.ts`、`at-oauth-callback.spec.ts` | 新增 `E2E-REVENUE-02`／`03`（`DEF-407`）、`E2E-OAUTH-LANDING`（`DEF-409`） |
| `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` | v1.0.9 → v1.0.10（§9.16 營收口徑、§10.1.1 `/login`、`/orders/[id]`） |
| `docs/02_architecture/api/API_M13_Seller_Dashboard.md`（v1.2）、`API_M11_Logistics.md`（v1.3） | 同步 `DEF-407`／`412`／`408` |
| `docs/06_quality/FRONTEND_PRECOMMIT_GUIDE.md` | 新增「禁止 `<SelectItem value="">`」說明 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-407`／`408`／`409` 結案、`DEF-401` 備註補完、登記 `DEF-412`／`413` |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 301 row |
| `docs/04_planning/SPRINT_301_PLAN.md` | 新增（本文件） |
