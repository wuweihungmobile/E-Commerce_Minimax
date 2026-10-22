# Sprint 300 Plan — 以真實資料掃描訂單／退貨／工單頁面：修復 DEF-404（無 SKU 品項退貨 500）、DEF-405（UTC 當「今天」）、DEF-406（預設縮圖破圖），登記 DEF-407～411

## 1. 起點與範圍

### 1.1 起點（對「繼續」的解讀是推論，不是授權）

使用者貼回 [Sprint 299](SPRINT_299_PLAN.md) 的收尾總結，只說「請繼續完成任務！」，**沒有任何 `==>` 逐項回覆**。依既有慣例（記憶 `inline-decision-replies-mean-proceed` 的「無 `==>`」變體，Sprint 223／229／254／298 同款）：

- 這**不是**對「待你決定」項目的授權——總結裡 `DEF-401` 的子問題（要不要加 ESLint 規則禁止空字串 `SelectItem`）使用者沒有回「依照建議」，所以**沒有做**。
- 做的是總結自己列為「下一輪我能獨立做」、不涉產品決策的事：以真實資料補掃總結說「還沒走到」的路由（候選 1），並核對總結對 `at-m15`（候選 2）與 `getCurrentTenant()`（候選 4）的說法；候選 4 只做了**機械分類與一次針對「系統租戶佔位值」模式的靜態掃描**（§6.1），不是完整的語意稽核。
- 這個解讀本身是我的推論；使用者若本意是「全部照建議做」，只要回一句，我就補做 `DEF-401` 子問題與下面 `DEF-407`～`409` 的建議。

### 1.2 成功標準（開工時定義）

1. 以真實後端、真實資料（訂單、訂房、退貨、客服工單）走過原本「沒走到」的頁面，機械判定崩潰／空白／卡載入／5xx／列表漏列／不該看到的資料外洩。
2. 掃描發現的缺陷，**屬於不需產品決策的才修**；需要授權規則、定義、導向等決定的只登記並附建議，不擅自做。
3. 每個修復的新測試必須能因為正確的原因轉紅：修復前的紅燈＋修復的突變；多個缺陷各有**獨立**的紅燈（serial 下第一個失敗會讓後面不執行）。
4. 全量後端回歸（改了生產程式碼）與完整 `make validate-e2e` 通過。

### 1.3 開工先核對總結的說法（用精確工具，不憑印象）

| 總結的說法 | 核對結果 |
|---|---|
| 雲端 CI：`a57cd1a2` 與 `48775385` 都全綠 | **屬實**：`gh run list` 兩個 run（39806469533、39808694462）皆 `completed／success`，`headSha` 與 commit 一致 |
| 「剩下 14 個沒走到的路由」 | **是探針視角的數字**。以瀏覽器層級的既有 E2E 來看，`/orders/[id]`、`/bookings/[id]`、`/dashboard/bookings/[id]`、付款成功／取消頁（訂房）、OAuth 回呼其實都有案例開過。**真正零瀏覽器覆蓋**的是：`/returns/[id]`、`/dashboard/returns/[id]`、`/support/tickets/[id]`、`/dashboard/support/tickets/[id]`、`/admin/support/tickets/[id]`、`/orders/[id]/payment/cancel`，以及 `/dashboard/orders`（列表與詳情；`frontend/e2e` 對 `dashboard/orders` 零引用）。我第一次用 `grep` 的正規表示式 `\$\{` 被 `ugrep` 拒絕，改用 `-F` 固定字串重查才得到可信的結果 |
| 「`at-m15-e2e.spec.ts` 三個沒有任何斷言的案例」 | **低估**：逐案例計數（腳本統計每個 `test(` 區塊內的 `expect(`），12 個案例有 **7 個**零斷言（見 `DEF-410`）；Sprint 298 的記憶也寫「三個」，一併更正 |
| 「150 多處 `getCurrentTenant()`」 | **屬實**：`backend/src/main` 共 172 處、35 個檔；本輪做了機械分類與針對性靜態掃描（§6.1），完整語意稽核仍未做 |

## 2. 掃描：帶真資料的頁面

### 2.1 做法

沿用 Sprint 298 的做法（記憶 `frontend-differential-probe-and-route-crawl-recipe`），但**資料改成真的**。全棧自己起（Postgres 55432／Redis 56379、打包 JAR、`next start` 3000），**先核對產物新舊**：JAR（20:11）比最後一個後端 commit（14:12）新可用；前端 `.next/BUILD_ID`（22:46）比最後一個前端 commit（`a57cd1a2`，22:54）還舊，不能當 HEAD，重建。播種全走真實流程：店主註冊→驗證 Email→申請開店（HYBRID）→管理員核准→開 `BOOKING_ENABLED`→店主重新登入→建房源與商品；買家註冊後訂房並付款、下三張訂單（已送達、已付款、未付款）、對已送達訂單申請三張退貨（待審核、核准、駁回）、開兩張客服工單（一張掛訂單並雙方各回覆一次、一張只有開單）。**第一次播種時，我用沒有 SKU 的商品申請退貨就撞到 500（§3）；為了讓掃描能繼續，退貨改用另建的有 SKU＋庫存的商品，其餘訂單維持無 SKU 商品。**

依角色走訪 **48 頁**：買家 22、店主 18、管理員 8，每頁判定 Next 錯誤畫面、`pageerror`、HTTP 5xx，並記錄「列表是否列出播種的那幾筆」「不該看到的是否出現」「是否卡在載入中」，另對每頁截圖供人工檢視。

### 2.2 機械判定結果：全部為 0

崩潰 0、空白 0、卡載入 0、列表漏列 0、外洩 0、`pageerror` 0、5xx 0、導航錯誤 0。店主的客服工單列表沒有出現「沒有訂單、屬於系統租戶」的那張，直接開它回 404「找不到客服工單」（隔離正確）；管理員兩張都看得到。

### 2.3 機械判定之外，人工比對發現的事

機械旗標全是 0，**不代表正確**。逐頁看 4xx、console 錯誤、畫面文字與截圖，並用已知的播種真值對數字，發現下列各項（各自的證據與處理見 §3～§6）：

| # | 觀察 | 處理 |
|---|---|---|
| 1 | 播種階段：`POST /v2/returns` 對沒有 SKU 的商品回 **500** | `DEF-404`，已修 |
| 2 | 店主營收報表顯示「總營收 $0／0 筆」，API 其實有 2600／3 筆 | `DEF-405`，已修 |
| 3 | 店主首頁「今日營收 3100」與營收報表「總營收 2600」對不上（差一張未付款訂單） | `DEF-407`，待裁決 |
| 4 | 四個頁面的商品縮圖是破圖（`404 /placeholder.png`） | `DEF-406`，已修 |
| 5 | 買家開自己的訂單詳情，`GET /v2/logistics/order/{id}` 回 403；畫面對已送達訂單寫「尚未出貨」 | `DEF-408`，待裁決 |
| 6 | 買家用真實登入表單登入，落在賣家後台並顯示「無法載入儀表板資料」 | `DEF-409`，待裁決 |
| 7 | （核對總結說法時）`at-m15` 七個案例零斷言 | `DEF-410`，登記 |
| — | 訂單狀態紀錄的原因欄位是英文（「Order created from cart」「Mock payment success」），其餘介面是中文 | 純文案，**未登記** |

## 3. DEF-404：沒有 SKU 的品項申請退貨回 500（已修）

### 3.1 證據

後端日誌：`ERROR: null value in column "sku_id" of relation "return_request_items" violates not-null constraint`。根因是三層不一致：服務層（`createReturnRequest` 的 `orderItem.getSku() != null ? … : null`、`receiveReturn` 的 `if (item.getSkuId() != null)`、`toResponse` 容忍 null）與前端（`skuId`／`skuCode`／`productName` 型別為 `string | null`、`品項 #…` 後備顯示）都預期 `sku_id` 可以是 null，只有 `V74` 的 `NOT NULL` 與實體的 `@Column(nullable = false)` 擋住。商品建立後預設沒有 SKU（SKU 要由店主在商品**編輯**頁的 SKU 管理另外建，`SkuManager`；`frontend/src/services/sku.ts` 的註解寫明 Sprint 178 才有建立入口，此前沒有任何程式碼會建立 `ProductSku`），所以沒另外建 SKU 的店鋪，買家永遠退不成貨。`API_M05_Return.md` §7 早就記過「`nullable=false` 與可能寫入 `null` 的落差，無測試驗證，建議後續確認」——當時只歸因於「SKU 被刪除」，沒有用真實 DB 驗證，也沒登記成缺陷。

### 3.2 改了什麼

- `V93__Return_Request_Items_Sku_Nullable.sql`：`ALTER COLUMN sku_id DROP NOT NULL`（保留外鍵）。
- `ReturnRequestItem.skuId`：`@Column(name = "sku_id")`（拿掉 `nullable = false`）。
- 沒有 SKU＝沒有庫存可回補：收貨確認只更新退貨單狀態與收到的數量，不寫庫存異動（服務層本來就是這樣，沒改）。

### 3.3 驗證

- **後端整合測試**：`M05ReturnRequestIntegrationTest` 新增 `IT-M05-RETURN-013`（無 SKU 品項申請→核准→收貨，且該訂單零庫存異動、另一個 SKU 的庫存不變）。先對未修實體跑：14 個案例 1 個 error，正是上述 not-null 違反；修復後 14／14。**陷阱**：修復後第一次重跑仍然紅——`mvn` 說 `Nothing to compile`（量到的是 class 的修改時間 00:55:47 只比原始碼 00:55:46 晚 1 秒，不是我的 `mvn` 編的；**推論**是 IDE 的 Java 服務已先重編，所以那句話是對的），真正原因是整合測試 profile 的 `ddl-auto: update` **不會放寬既有表的 NOT NULL**，測試 DB 容器裡的表是第一輪用舊實體建好的。用 `stat` 對照 class 與原始碼時間、用 `psql` 查 `information_schema`（`is_nullable = NO`）確認後，換全新的測試 DB 才綠。`make test-db-up` 每次都先 `docker rm -f` 再重建容器（`Makefile` 第 89 行），雲端 CI 的服務容器也是每次全新，所以只要在 commit／verify 前執行過 `make test-db-up` 就不受影響；**若沿用修改前就啟動的舊容器**，`IT-M05-RETURN-013` 會因舊表的 NOT NULL 而失敗，換一個容器即可。
- **真實 Flyway 全棧**：整合測試用 Hibernate 推導的 schema，**驗不到 migration 本身**。以重建的 JAR 起全棧：`flyway_schema_history` 的 V93 `success = t`、`information_schema` 顯示 `sku_id` 的 `is_nullable = YES`、`return_request_items` 的三個外鍵仍在。
- **新 E2E**：`at-return-flow-real.spec.ts` 的 `E2E-RETURN-02`（買家在退貨申請頁送出，商品沒有 SKU）：對修復前的 JAR **紅**（停在 `/returns/new`，後端日誌有 not-null 違反），修復後綠；`-03`（店主核准並確認收貨→「已完成」）、`-04`（買家的列表與詳情看到「已完成」）修復後綠。
- `ReturnRequestServiceTest` 13／13。
- `make validate-schema-doc` 通過——SRD 資料庫文件完全沒有 `return_request_items`（守門也不比對可否為 NULL），所以 V93 不會造成漂移，不需同步。

## 4. DEF-405：用 UTC 日期當「今天」（已修）

### 4.1 證據

營收報表 `frontend/src/app/dashboard/revenue/page.tsx` 以 `date.toISOString().slice(0, 10)` 算預設起訖日。實測：本地時間 2026-10-23 00:48（UTC 2026-10-22 16:48）時，頁面送出 `startDate=2026-09-22&endDate=2026-10-22`，畫面顯示「總營收 $0」「訂單總數 0 筆」；同一個店鋪、後端預設區間回 `totalRevenue: 2600`、`totalOrders: 3`。同根因的第二處是 `ListingDetail.tsx` 的「入住日期不可早於今日」驗證（前端放行前一天；後端對那個日期的回應**未驗證**）。與 `DEF-362`（Sprint 254）同一個根因，當時只修了 `PricingCalendarPreview`。

**精確範圍**：`frontend/src` 的 `toISOString` 共 5 行（`app/health/route.ts` 時間戳記無關、營收頁與 `ListingDetail` 本項修復、`PricingCalendarPreview` 一行註解、`(auth)/account/page.tsx` 的匯出檔名）；`.split('T')[0]` 只有 `PurchaseOrderForm` 解析伺服器日期字串，不是由「現在」推算。匯出檔名只影響檔名，**沒有動**。

### 4.2 改了什麼

新增 `frontend/src/lib/date.ts` 的 `toLocalDateString`（把 `DEF-362` 當時私有在 `PricingCalendarPreview` 的函式搬出來，連同理由）；營收頁與 `ListingDetail` 改用它，`PricingCalendarPreview` 也改為引用它（避免第三份複製；這是我替使用者做的一個小重構，見 §10）。

### 4.3 驗證

這個缺陷**只在台灣時間 00:00～07:59 現形**，一般白天或 CI 的 UTC 時區根本測不出來，所以不能靠真實時鐘：固定瀏覽器時間 `2030-03-10T17:30:00Z`（＝台灣 2030-03-11 01:30）、指定 `timezoneId: 'Asia/Taipei'`。

| 案例 | 修復前 | 修復後 |
|---|---|---|
| `at-dashboard-revenue.spec.ts` `E2E-REVENUE-01`（預設結束日是台灣的今天、起日 30 天前，畫面日期欄位一致） | **紅**：`Expected "2030-03-11"、Received "2030-03-10"` | 綠 |
| `at-room-booking.spec.ts` `E2E-ROOM-13`（台灣凌晨選「昨天」入住→顯示「入住日期不可早於今日」，且不發出可用性查詢） | **紅**（找不到錯誤訊息，驗證放行了） | 綠 |
| `E2E-ROOM-14`（對照：同一時刻選台灣「今天」→通過驗證並查詢） | 綠 | 綠（防止矯枉過正；前後都綠是設計，不是遺漏） |

這兩條驗證訊息（「入住日期不可早於今日」「退房日期需晚於入住日期」）在這之前**整個 E2E 完全沒有被斷言過**（精確搜尋只命中原始碼）。

## 5. DEF-406：預設縮圖不存在（已修）

四個頁面（`(auth)/orders/[id]`、`(auth)/returns/new`、`(auth)/returns/[id]`、`dashboard/orders/[id]`）的 `<img src={item.coverImageUrl || '/placeholder.png'}>`，但 `frontend/public` 只有 Next 預設的 5 個 SVG，沒有 `placeholder.png`：Playwright 擷取到 `404 image http://localhost:3000/placeholder.png`，畫面是只剩 alt 文字的破圖。購物車頁用的是「灰底＋『無圖片』」的條件式區塊，沒有這個問題。

**改了什麼**：新增 `frontend/public/placeholder.svg`（64×64，灰底＋灰色圖示，比照購物車的 gray-200／gray-400；明寫 `width`／`height` 讓 `naturalWidth` 在各瀏覽器都大於 0），四處引用改為 `.svg`。

**驗證**：`at-return-flow-real.spec.ts` 的 `E2E-RETURN-01`（買家訂單詳情、退貨申請頁）、`-02`（退貨詳情）、`-03`（店主訂單詳情）斷言縮圖 `complete && naturalWidth > 0`，01 另斷言沒有失敗的圖片請求。修復前 01 紅（紅在第一個縮圖斷言）。**但那次只證明了第一個使用點**——後面三個使用點的斷言從沒被「紅」過（S299 記下的「斷言順序讓後面沒牙齒」）。所以做一次**逐點突變**：只把後三處改回 `.png`、第一處保持正確，重建後三個案例各自單跑：

| 案例 | 紅在 | 對應的突變使用點 |
|---|---|---|
| `E2E-RETURN-01` | 第 121 行（第二個縮圖斷言；第 117 行的買家訂單頁通過） | `returns/new` |
| `E2E-RETURN-02` | 第 139 行（申請退貨成功、跳轉後的退貨詳情） | `returns/[id]` |
| `E2E-RETURN-03` | 第 147 行 | `dashboard/orders/[id]` |

我先用失敗行號對照，確認不是「同一段訊息掩蓋了紅在哪裡」。突變已還原（`placeholder.png` 0 處、`.svg` 4 處）。

**測試設計的教訓**：第一版把「縮圖檢查」與「申請退貨」放在同一個案例（縮圖檢查在前），修復前只看到縮圖那條紅，**把 `DEF-404` 的 500 遮住了**，後面的案例還因 serial 而不執行。重排成各自獨立的案例（01 縮圖、02 申請退貨），兩個缺陷才各有獨立的紅燈。

## 6. 登記、待使用者裁決（沒有擅自做）

`DEF-407`／`408`／`409` 需要使用者決定；`DEF-410` 不需產品決策，但它的「正確斷言」取決於 `DEF-409`，所以先登記。**只登記並附建議**；使用者回「依照建議」我就做。

| 登記 | 一句話 | 為什麼不擅自做 | 我的建議 |
|---|---|---|---|
| `DEF-407` | 「營收」有三種定義，首頁（3100）與報表（2600）對不上，未付款訂單被算成營收 | 「營收」的定義是產品決策；M13 API 文件早記過這個不一致，但從沒人拍板 | 營收＝已付款且未取消／未退款的訂單；首頁與每日趨勢對齊 `totalRevenue` 那一組；平均客單價分母同組；訂房是否納入另案 |
| `DEF-408` | 買家看不到自己訂單的物流（PRD 路由表寫「含物流追蹤」，後端 403） | 授權規則變更，這一區有過多次跨租戶 IDOR 加固 | 只開放讀取類端點給該訂單的買家本人，寫入端點維持店家／admin，另一位買家仍須 403 |
| `DEF-409` | 所有角色登入後一律導向 `/dashboard`，買家落在賣家後台的錯誤畫面；沒有「登入後回原頁」 | 登入後去哪裡是產品決策 | 依角色分流（BUYER→`/`、店家類→`/dashboard`、管理員→`/admin`）＋同源 `?redirect=`（防開放轉址） |
| `DEF-410` | `at-m15-e2e.spec.ts` 7／12 案例零斷言 | 「正確的斷言」取決於 `DEF-409` 與角色權限的決定，現在補只會把有疑義的現況寫死 | 用 `seedStore` 真實店主改寫，並刪掉這 7 個 |

### 6.1 候選 4（`getCurrentTenant()`）的第一步：機械分類與針對性靜態掃描；登記 `DEF-411`（不需決策）

- **機械分類**（唯讀腳本，每個檔案的 `getCurrentTenant()` 次數與 `isStoreTenant` 排除次數）：172 處／35 檔，其中只有 10 檔（24 處）在同檔有 `isStoreTenant` 排除，25 檔（148 處）沒有。**「同檔沒守門」本身不是缺陷指標**（多半是只有店主角色進得去的端點），所以只當作範圍，不當結論。
- **針對歷史上真正出事的模式**（店家層端點只用 BUYER 也持有的權限守門——工單、退貨、訂單、訂房都這樣出過事）：靜態掃描 `api/controller`，列出路徑含 `dashboard`／`tenant`／`seller`、卻只要求 BUYER 持有的權限的端點，共 **9 個**：`orders/tenant`、`dashboard/returns`、三個 `dashboard/support/tickets` 端點先前已修並有真實測試（E2E-SDASH-06、E2E-STI-02 等）；`dashboard/bookings/{id}/state-log` 讀碼確認用 `checkStoreBookingAccess`（已排除佔位值），安全；三個 `dashboard/pricing/*` 與日曆預覽的擁有權檢查沒有排除系統租戶佔位值 → **`DEF-411`**（🟢，**讀碼推論、沒有實測**，影響限於系統租戶名下的資源且唯讀）。
- `DEF-411` 不需產品決策，建議修法比照 Sprint 234／235；**我沒有在本輪修**：發現它時完整後端回歸正在跑、原始碼不能動；回歸跑完後我也沒有追加——這項要先實測再改，而且追加後端改動會讓已完成的全量回歸與 gate 作廢、得重跑（約 25 分鐘）。下一輪我可以獨立做。
- **順帶更正一份錯誤的文件**：讀 `PricingController` 時發現 `API_M12_Dynamic_Pricing.md`（v2.0）把 `POST /calendar/price` 記成「無租戶擁有權檢查、可能是跨租戶寫入漏洞，建議另案登記調查」，連 §4.8 的權限說明與權限矩陣都這樣寫；實際程式碼 `PricingService.setCalendarPrice` 第一步就是 `checkRuleTenantOwnership`（DEF-242），也有單元測試 `PricingServiceTest.setCalendarPrice_roomBelongsToDifferentTenant_throwsE1007`。三處已更正為 v2.1（**只讀碼與單元測試，沒有實測 HTTP 層**）。這個「疑似漏洞」自文件 v2.0（Sprint 266）起就寫在那裡，本輪才拿程式碼對過，發現不成立。
- 這個掃描只涵蓋「只要求 BUYER 也持有的權限」的端點；其他持有更高權限但落在系統租戶的角色（例如管理員）與服務層內部呼叫，沒有檢視。

## 7. 驗證總表

| 檢查 | 結果 |
|---|---|
| `npx tsc --noEmit`、`npm run lint`（`--max-warnings 0`） | 皆退出碼 0 |
| `npm run build` | 退出碼 0，70／70 頁 |
| `M05ReturnRequestIntegrationTest`（真實 PostgreSQL，全新 DB） | 14／14；新增的 `IT-M05-RETURN-013` 修復前紅 |
| `ReturnRequestServiceTest` | 13／13 |
| 真實 Flyway 全棧 | V93 `success = t`、`sku_id` 可為 NULL、外鍵仍在 |
| 新 E2E（`at-return-flow-real` 4＋`at-dashboard-revenue` 1＋`at-room-booking` 2，共 7 個） | 修復後 7／7 綠（單獨跑 26.6 秒）；修復前各自紅燈見 §3～§5 |
| 突變驗證 | 縮圖三個使用點各被自己的案例抓到（§5）；其餘缺陷以「對修復前的建置跑」作為反向驗證 |
| `make validate-schema-doc` | 通過（SRD 無 `return_request_items`，V93 不造成漂移） |
| 全量後端 `mvn -o clean verify`（單元＋整合；Checkstyle 另由 pre-commit 跑） | `BUILD SUCCESS`（18 分 31 秒）；**單元 2309、整合 886**，皆 0 失敗／0 錯誤／0 跳過，日誌中 `<<< FAILURE`／`<<< ERROR` 標記 0 個；`ReturnRequestServiceTest` 13／13、`M05ReturnRequestIntegrationTest` 14／14（含新增的 `IT-M05-RETURN-013`）。從日誌核對，沒有只信背景任務的退出碼 |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **第一次執行即全綠：195 案例，191 通過／4 跳過／0 失敗／0 不穩定（6.3 分鐘）**，閘門顯示「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」；backend 啟動檢查「entity 與 Flyway schema 對齊，無漂移」（含 V93 與改過的實體）。案例數由 Sprint 299 的 188 增為 195＝新增的 7 個，都在整套裡執行並通過（`E2E-REVENUE-01`、`E2E-RETURN-01`～`04`、`E2E-ROOM-13`／`14`）；4 個跳過與 Sprint 299 相同 |
| pre-commit、pre-push、雲端 CI | 皆通過，詳見 §11：雲端 run 39828790750 三個 job 皆 success（`headSha` 一致）；雲端日誌確認 `M05ReturnRequestIntegrationTest` 14／14、整合測試 886／0 失敗 |

## 8. 過程中的失誤與修正

1. **第一版 `E2E-RETURN-01` 把兩個缺陷塞在同一個案例**（§5）：修復前只看到縮圖那條紅，遮住 `DEF-404`；對修復前的建置跑「紅燈驗證」才發現，重排成獨立案例。
2. **修復後第一次重跑整合測試仍然紅**（§3.3）：`mvn` 的 `Nothing to compile` 與舊表殘留的 NOT NULL 讓人誤以為修法沒生效。沒有憑猜測繼續改，而是用 class／原始碼的修改時間與 `psql` 的 `information_schema` 查證，確認是測試 DB 殘留，換全新容器後綠。
3. **縮圖斷言一開始只對得到第一個使用點**（§5）：靠逐點突變補上，並以失敗行號確認紅在預期的那一行。
4. **一個正規表示式被 `ugrep` 拒絕**（§1.3）：我先看到的是空輸出與錯誤訊息，改用 `-F` 固定字串重查，沒有拿錯誤輸出當「零命中」。
5. **Bash 的工作目錄多次因 `cd` 殘留在子目錄**（記憶已有記載）：之後的指令一律用絕對路徑；沒有造成錯誤結果。

## 9. 限制（誠實揭露）

1. **掃描只涵蓋「載入」層**：沒有走互動（回覆工單、指派、駁回退貨、取消訂單、改訂單狀態等）；工單的三組頁面（買家、店主、管理員）只看了載入，沒有新增永久測試，所以工單頁面**仍然沒有瀏覽器 E2E 覆蓋**。
2. **播種的訂單由店主以 `PATCH /v2/orders/{id}/status` 推進到已送達，沒有建立物流單**：M11 文件記載「建立物流單即出貨、同一交易扣庫存」，這條真實出貨路徑我沒走。所以 `DEF-408` 只觀察到 403，沒有觀察到「物流單存在卻被藏起來」。
3. **只在一個時段掃描**（台灣凌晨 00:4x）：`DEF-405` 正是因為時段才現形；其他時段才會現形的缺陷可能仍在。
4. **修復後沒有重跑整個 48 頁的掃描**：驗證靠新增的 7 個案例、突變與完整 gate。
5. `DEF-405` 的修法假設瀏覽器時區＝營運時區（台灣），與 `DEF-362` 的慣例一致；非台灣時區的瀏覽器不在範圍。`(auth)/account/page.tsx` 匯出檔名的 UTC 日期沒有動（只影響檔名）。
6. `DEF-404`：無 SKU 品項的退貨在店主審核台顯示 `品項 #<訂單品項 id 前 8 碼>`，不是商品名（前端既有的後備顯示；品名是從 SKU join 來的）。
7. `getCurrentTenant()` 172 處的**完整語意稽核仍未做**（只做了 §6.1 的機械分類與針對性靜態掃描）；`DEF-401` 的子問題（ESLint 規則防空字串 `SelectItem`）仍待使用者裁決。
8. gate 的冷啟動註冊逾時（Sprint 299 未查明）本輪沒有主動處理，也沒有改 `scripts/validate-e2e.sh`。gate 期間我另起背景迴圈每 6 秒複製一次後端日誌（腳本收尾會刪），以便再現時有資料可查；但這次 gate **第一次執行就全綠**（整套最前面幾個案例都通過），冷啟動逾時沒有再現，所以沒有日誌可診斷。成因仍未查明——只能說這一次沒發生，不能說已解決。那個迴圈後來被我自己的 `pkill` 結束（背景任務通知顯示 exit 144 是這個原因，與 gate 無關）。

## 10. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | `DEF-404` 採「允許無 SKU 品項退貨」（V93 讓欄位可空），而不是拒絕 | 服務層（三處）、DTO 與前端型別都預期 null，只有 DB 欄位與實體擋住；沒建 SKU 是預設狀態，拒絕等於這種店鋪不能退貨 | 撤銷 V93，改成服務層對無 SKU 品項回 4xx |
| 2 | 預設縮圖用 `placeholder.svg`（並把四處引用改 `.svg`），而不是補一個 `.png` | 文字檔可審閱、可縮放，且比照購物車的灰色慣例 | 改回 `.png` 並放一張圖 |
| 3 | `toLocalDateString` 抽到 `frontend/src/lib/date.ts`，`PricingCalendarPreview` 一併改為引用 | 避免第三份複製；同一個根因（`DEF-362`） | 還原 `PricingCalendarPreview` 的私有函式 |
| 4 | 日期以瀏覽器本地時區判斷 | 與 `DEF-362` 的既有慣例一致（Rule 11） | 改為固定用 `Asia/Taipei` 計算 |
| 5 | 新增 7 個永久 E2E 案例（含一支 4 案例的真實後端規格） | 這些行為沒有任何現有測試守住；退貨頁面原本零瀏覽器覆蓋 | 刪檔 |
| 6 | PRD 升 v1.0.9（只補 §8.2.13 `sku_id` 可空一句）；`API_M05_Return` v1.1、`API_M13` v1.1、`API_M11` v1.2、`API_M12` v2.1（更正誤記的 `calendar/price` 無檢查） | 文件與實作要一致；這幾份文件原本就記著這些落差或寫錯 | 還原那幾處 |
| 7 | `at-m15-e2e.spec.ts` 本輪**不動**（登記 `DEF-410`） | 補斷言會把有疑義的現況寫死 | 要我現在就補或刪，回一句即可 |
| 8 | `DEF-407`／`408`／`409` **不擅自決定** | 營收定義、授權規則、登入導向都是產品／安全決策 | — |

## 11. 雲端 CI 與狀態

- commit `7357ce34`，於 2026-10-23 push（`48775385..7357ce34 main -> main`）。以推送日誌，以及 `git fetch` 後本機 HEAD 與 `origin/main` 同為 `7357ce34`、ahead／behind 為 0／0 核實；沒有採信背景任務通知的退出碼。
- pre-commit 一次通過：Checkstyle、Maven compile、核心層快速測試（`Maven quick test passed (core layer)`）、ESLint、`tsc --noEmit`、secret 掃描，以及 V93／PRD 觸發的 schema 文件守門（`SRD_Database_Schema.md` 的 DDL、PRD §8.2 的欄位清單與 Flyway 實際 schema 一致）。commit 前測試 DB 已換成全新容器。
- pre-push 輕量守門通過：本機 act 的 Backend Unit Tests（Run Unit Tests 2 分 50 秒）與 Frontend Lint & Build（ESLint、Type Check、Build）皆 `Job succeeded`，schema 漂移守門「backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移」。push 之後 `docker ps -a` 為空（pre-push 清掉了 `nk-test-*`）。
- 雲端 CI run [39828790750](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39828790750)：`completed／success`，三個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 success，約 18 分鐘（18:08:46Z～18:26:41Z）。`gh run watch` 結束後另以 `gh run view --json status,conclusion,jobs,headSha` 交叉確認，`headSha` 與本機 commit 一致。
- 下載整合測試 job 的雲端日誌（195,210 行）確認：`M05ReturnRequestIntegrationTest` **14／14**（含新增的 `IT-M05-RETURN-013`，在雲端全新的 Postgres 上）、整合測試總計 **886／0 失敗／0 錯誤／0 跳過**（與本機相同）、`BUILD SUCCESS`。第一次用 `gh api` 抓日誌時，因回應含終端機跳脫序列被拒絕，只拿到一行錯誤訊息；加 `--allow-escape-sequences` 後重抓才得到完整日誌（先檢查檔案大小才沒有把它當成「沒有命中」）。
- 雲端三個 job 不含 Playwright E2E；E2E 以本機 `make validate-e2e` 為準（§7）。
- 之後的純文件回填 commit 不在本文件記錄自己的 hash 與雲端結果（commit 無法包含自己的 hash）。

## 12. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/resources/db/migration/V93__Return_Request_Items_Sku_Nullable.sql` | 新增（`DEF-404`） |
| `backend/src/main/java/com/nextkey/ecommerce/domain/model/returns/ReturnRequestItem.java` | `skuId` 可為 null（`DEF-404`） |
| `backend/src/test/java/com/nextkey/ecommerce/integration/M05ReturnRequestIntegrationTest.java` | 新增 `IT-M05-RETURN-013` 與 `seedOrder(status, itemSkuId)` 重載（`DEF-404`） |
| `frontend/src/lib/date.ts` | 新增 `toLocalDateString`（`DEF-405`） |
| `frontend/src/app/dashboard/revenue/page.tsx`、`components/storefront/ListingDetail.tsx`、`components/pricing/PricingCalendarPreview.tsx` | 改用共用的 `toLocalDateString`（`DEF-405`） |
| `frontend/public/placeholder.svg`、四個頁面（`(auth)/orders/[id]`、`(auth)/returns/new`、`(auth)/returns/[id]`、`dashboard/orders/[id]`） | 新增預設縮圖、引用改 `.svg`（`DEF-406`） |
| `frontend/e2e/at-return-flow-real.spec.ts`、`at-dashboard-revenue.spec.ts` | 新增 |
| `frontend/e2e/at-room-booking.spec.ts` | 新增 `E2E-ROOM-13`／`14`、檔頭說明（`DEF-405`） |
| `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` | v1.0.8 → v1.0.9（§8.2.13 `sku_id` 可空） |
| `docs/02_architecture/api/API_M05_Return.md`（v1.1）、`API_M13_Seller_Dashboard.md`（v1.1）、`API_M11_Logistics.md`（v1.2）、`API_M12_Dynamic_Pricing.md`（v2.1） | 同步 `DEF-404`／`407`／`408`／`411`；M12 更正誤記的「`calendar/price` 無租戶擁有權檢查」 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | 登記 `DEF-404`～`411`（`404`／`405`／`406` 結案；`407`／`408`／`409` 待裁決；`410`／`411` 登記，不需決策） |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 300 row |
| `docs/04_planning/SPRINT_300_PLAN.md` | 新增（本文件） |
