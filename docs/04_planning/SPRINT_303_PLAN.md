# Sprint 303 Plan — DEF-410（`at-m15` 零斷言案例改寫）與 DEF-411（定價規則／日曆預覽排除系統租戶佔位值）；途中發現並修復 DEF-414、DEF-416，登記 DEF-415

## 1. 起點與範圍

### 1.1 起點（對「繼續」的解讀是推論，不是使用者的明確指示）

使用者貼回 [Sprint 302](SPRINT_302_PLAN.md) 的總結，回覆「請依序完成所有任務！」，**沒有任何 `==>` 回覆**。總結自己寫明：「我下一輪可獨立做、不需產品決策：`DEF-410`……與 `DEF-411`……你回『請繼續』，我就做」，並列出「追蹤表上標明要你確認或決定的」項目（`DEF-346`、`DEF-355`／`356`、`DEF-297`、`DEF-337`）。

我把「請依序完成所有任務」解讀為「做總結列為可獨立做的那兩項」，**不替使用者決定待決項目**。這個解讀是推論；若使用者的意思是連待決項目也一併授權，需要明說。

### 1.2 成功標準（開工時定義）

1. `DEF-411`：**先實測再改**（登記時的要求）；修復前測試對的是真實 PostgreSQL，且每個案例附「真正的店主、平台管理員照常運作」的對照。
2. `DEF-410`：7 個零斷言案例改成真實店主視角、每個案例都有能失敗的斷言；用前端突變證明它們真的會因對的原因轉紅。
3. 每個守門／斷言都經過突變驗證，並逐條讀失敗訊息確認原因。
4. 全量後端回歸（改了生產程式碼）與完整 `make validate-e2e` 通過。
5. 登記時寫的推論要在動手前先用工具核對；發現的新缺陷屬缺陷就修並說明、屬權限決策就登記不擅自做。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「管理員的租戶是不是系統租戶」（`DEF-411` 登記時留作先實測的前提） | **是**。讀碼：`AuthService.resolveTenantForUser` 對沒有 `tenantId`、沒有有效 `tenant_members` 的使用者退回系統租戶（註解寫「如平台管理員無租戶歸屬」）。**實測**（真實 PostgreSQL，`PricingSystemTenantIsolationE2ETest`）：真實登入的 `SUPER_ADMIN`，登入回應的租戶＝系統租戶；他經 HTTP 建立的規則，資料庫那一列的租戶＝系統租戶 |
| 「`/calculate` 的程式本輪沒有讀完」（登記時） | 讀完：無擁有權檢查；但買家端的 `GET /v2/listings/{id}/price` 同樣只要求 `room:read`／`product:read` 並呼叫同一個 `PricingService.calculatePrice`，所以**不是外洩**。日曆預覽（`getCalendarForOwner`）的內容與買家端 `GET /v2/bookings/calendar` 相同，修的是擁有權檢查的一致性。真正會外洩的是規則列表（折扣百分比、有效期等商業資訊） |
| 「寫入端點需要 BUYER 沒有的 `room:create`／`update`，不受影響」 | 對 BUYER 成立。但**沒有店鋪的 `ADMIN` 持有寫入權限**，且租戶也是系統租戶——我的第一版修法把寫入檢查一併收緊，等於替使用者決定它；讀完 `RoomService`／`ProductService`／`ProductSkuService`／`RoomCalendarService` 的同型檢查後確認會不一致，收回（§3.3） |
| `POST /rules` 能不能在真實資料庫建立規則 | **不能**（`DEF-414`，§4）。固件用 `.tenantId(...)` 存檔就撞 NOT NULL，順著 HTTP 路徑確認服務層同樣有這個缺陷 |
| 「真實 PostgreSQL 整合測試涵蓋了這個端點」 | 只涵蓋 Hibernate 產生的 schema。Flyway 建出的資料庫有兩處不同：`pricing_rules.rule_type` 沒有 CHECK 約束，且 `V5` 測試資料列永遠在（`DEF-416`，§5） |
| `at-m15` 7／12 零斷言 | 屬實，並核對了每個案例的實際行為（§2）；『商品頁面』已被 `at-seller-product-list-real` 的 `E2E-PRODLIST-01` 涵蓋，不重複保留 |

## 2. DEF-410：`at-m15` 的 7 個零斷言案例改寫（已完成）

### 2.1 設計

`frontend/e2e/at-m15-e2e.spec.ts` 最下方新增 `AT-M15-REAL` 區塊（serial；`beforeAll` 以 `seedStore` 開 HYBRID 店鋪＋商品＋房型，再由 API 建一篇草稿與一篇已發布且嵌入商品卡的貼文），取代原本 7 個案例：

| 案例 | 斷言重點 |
|---|---|
| `E2E-M15-001` 新建貼文並發布 | 等「這一次 POST」的回應（200、狀態 `PUBLISHED`）與 alert『貼文已發布』、導向 `/cms/posts/<id>/edit`、前台 `/blog` 列得出標題 |
| `E2E-M15-002` 從列表進入編輯頁、改標題並儲存 | 列表有播種草稿且是『草稿』；編輯頁標題欄帶入原標題；等 PUT 回應（200）與 alert『貼文已儲存』；列表顯示新標題且仍是草稿 |
| `E2E-M15-004` 前台文章詳情 | 已發布 slug 顯示文章；不存在的 slug 後端回 404、畫面顯示『找不到文章』且不顯示他文 |
| `E2E-M15-005` 部落格列表→詳情→嵌入商品卡 | 點進文章後顯示商品名、`TWD 450`、『查看詳情』，原始 `{{embed:listing:` 語法不外露 |
| Dashboard 首頁 | 帳號與角色（`STORE_OWNER`）、『營運總覽』、快速入口、無『無法載入儀表板資料』、『上架中商品 / 房型』＝`1 / 1`（由播種的 1 商品＋1 房型推得，不是看輸出後才寫） |
| 房型頁面 | 『房源管理』標題、看到播種的房型、無 `This page couldn’t load`、無 `pageerror` |

同步訊號一律用網路回應與 alert 訊息，不用畫面文字（按鈕名稱含『發布』『已發布』，文字比對會誤判）。保留的 5 個案例（CMS 列表、篩選、媒體庫兩個、部落格首頁）原本就有斷言，沒動；檔案從 12 個案例變 11 個。

### 2.2 一個被看穿的假象

原『訪問前台文章詳情（無效 slug）』用**匿名頁面**開 `/blog/non-existent-slug-12345`。詳情頁的 `tenantId` 取自 `localStorage`，匿名時 `loadPost` 直接 `return`、`loading` 永遠是 `true`，畫面永遠是『載入中』——這個案例**不可能**看到『找不到文章』，就算它有斷言也會永遠失敗或（像原本那樣）根本沒斷言。新版先登入讓 `tenantId` 就位，並用已發布 slug 做對照。

### 2.3 驗證

| 檢查 | 結果 |
|---|---|
| 第一次對真實全棧跑 | 6 個新案例 1 通過、1 失敗（`E2E-M15-002`）、4 個沒執行（serial）；失敗原因是**我的定位器**：`row.getByText('草稿')` 與種子貼文標題（含『草稿』二字）撞名，strict mode violation。改標題用詞、狀態徽章與連結用 `exact: true` |
| 修正後 | `AT-M15-REAL` 區塊 6／6（24 秒）；整份規格再連跑 2 次皆 11／11（54 秒、60 秒）；突變驗證的基準（無突變）也是 6／6 |
| 前端突變 6 組（每組重建前端、重啟，只跑預期擊殺的案例） | 基準 Z（無突變）全綠；F1『發布』按鈕改成只存草稿、F2 編輯頁不帶入標題、F3『找不到文章』改字、F4 嵌入語法解析被改掉、F5 儀表板商品數固定 0、F6 房型列表永遠空——**各被目標案例擊殺**。逐條讀失敗訊息，皆是預期的斷言（F1：`點「發布」建立的貼文應直接是已發布` Expected `PUBLISHED`／Received `DRAFT`；F2：標題欄 Expected 原標題／Received 空字串；F3／F4／F5／F6：對應元素找不到）。還原後守門檔與備份逐位元相同，`git status` 無前端原始碼殘留 |

## 3. DEF-411：定價規則與日曆預覽的擁有權檢查（已完成，讀取端點）

### 3.1 實測（修復前）

`PricingSystemTenantIsolationE2ETest`（真實 PostgreSQL＋完整 HTTP／JWT 鏈；真實登入的無店鋪買家、無店鋪 `ADMIN`、`SUPER_ADMIN`；一間真實店鋪與店主做對照）**修復前 7 個案例 6 紅 1 綠**，紅燈原因逐一讀過：

| 案例 | 修復前 | 原因 |
|---|---|---|
| 買家列規則（含 `activeOnly`） | 紅 | 列得出系統租戶的規則 |
| 無店鋪 `ADMIN` 列規則 | 紅 | 同上 |
| 買家以系統租戶房源 id 查詢（`roomListingId`／`listingId`） | 紅 | 回 200，應 403 |
| 買家預覽系統租戶房源日曆 | 紅 | 回 200，應 403 |
| 量測：管理員經 HTTP 建規則 | 紅 | `POST /rules` 回 **500**——這是 `DEF-414`（§4），不是 `DEF-411` |
| 無店鋪 `ADMIN` 改規則（第一版的案例，§3.3 收回） | 紅 | 回 200（修改成功） |
| 店主以別人房源 id 查詢 | **綠** | 對照組：跨租戶本來就擋 |

### 3.2 修法

- `PricingService.getRules`：無參數分支對「沒有店鋪的非管理員」回空 List（不查資料庫）；`roomListingId`／`listingId` 兩個分支改用新的 `checkListingReadAccess`——先要求呼叫者有真正的店鋪（`TenantContext.isStoreTenant`，先判斷、再查詢），再沿用既有 `checkListingTenantOwnership`。
- `BookingService.checkListingTenantOwnership`（只有 `getCalendarForOwner` 用，純讀取）：同樣排除。
- `SUPER_ADMIN` 照常：其租戶同為系統租戶，仍列得出系統租戶名下的規則。

### 3.3 範圍收斂（我中途改變了做法）

第一版把**所有**擁有權檢查一併改成「`isStoreTenant` 且相等」，包含純寫入端的 `checkRuleTenantOwnership`（更新／刪除／覆蓋／日曆改價）。13 個突變跑到一半，我讀其他服務的同型檢查時發現：`RoomService`、`ProductService`、`ProductSkuService`、`RoomCalendarService` 都是同一個「租戶相等」寫法，而沒有店鋪的 `ADMIN` 持有它們的寫入權限。這代表第一版**等於替使用者決定**「沒有店鋪的 `ADMIN` 不能改系統租戶的定價規則」，卻不動商品／房源——不一致，也超出登記時「只修唯讀端點」的範圍。

我用 `SIGINT` 中止驅動（讓它的 `finally` 還原；第一次誤打到外層 shell，第二次才打到 Python），確認原始檔 SHA-256 與事先記錄一致，把寫入端點的兩個 helper 還原為原樣，只保留讀取路徑的收緊，移除固化舊決定的兩個測試（E2E 的『無店鋪 `ADMIN` 不得更新或停用』與單元的『更新／刪除系統租戶規則 → E_1007』），並把問題登記為 `DEF-415`。

### 3.4 驗證

| 檢查 | 結果 |
|---|---|
| 修復後真實 PostgreSQL | `PricingSystemTenantIsolationE2ETest` 6／6、`PricingRulePersistenceE2ETest` 3／3 |
| 單元 | `PricingServiceTest` 92／92（新增 7：`StorelessCallerTests` 6、`RuleListingQueryTests` 1）、`BookingServiceOwnerCalendarTest` 6／6（新增 2） |
| 守門測試的寫法 | 用 `lenient()` stub 加 `verify(never())`：只斷言「回空」或「拋例外」會被 Mockito 對 Repository 的預設回傳（空 List）掩蓋，守門被拿掉測試照樣綠 |
| **後端突變 13 個（最終設計）** | **全數擊殺**：P1 getRules 無參數守門拿掉、P2 過度擋平台管理員、R1 讀取 helper 的『先擋沒有店鋪者』拿掉、R2 過度擋平台管理員、R3 不再沿用租戶相等檢查、R4／R5 兩個分支各自退回舊檢查、P8／P9／P10 `DEF-414` 三處各拿掉 `.tenant(...)`、B1 日曆預覽退回只比相等、B2 拿掉租戶相等、B3 平台管理員放行拿掉。每個都由預期的案例擊殺（逐條讀失敗名稱：例如 R2 同時被 `getRules_withListingId_superAdmin_stillAllowed` 與 E2E 擊殺，證明過度擋也抓得到）；還原後兩個原始檔 SHA-256 與事先記錄一致，且沒有 `.orig` 殘留 |
| 第一次（舊設計）突變 | 只跑到 P1、P2 就中止（皆擊殺），不計入最終的 13 個 |

## 4. DEF-414：定價規則的三個寫入端點一律 500（途中發現，已修復）

`PricingRule.tenantId` 是 `insertable = false` 的影子欄位，INSERT 依據的是 `tenant` 關聯物件（程式庫其他地方早有同型說明：`TenantService` `DEF-143`、`ProductService`／`RoomService` 都先查 `Tenant` 再 `.tenant(tenant)`）。`createRule`、`setCalendarPrice`、`overridePrice` 只設了 `.tenantId(...)`，`pricing_rules.tenant_id`（`V1` 與測試庫皆 `NOT NULL`）收到 NULL → `DataIntegrityViolationException` → HTTP 500。

**怎麼發現**：為 `DEF-411` 寫真實 PostgreSQL 固件時，`pricingRuleRepository.save(PricingRule.builder().tenantId(...))` 先撞到 NOT NULL。我沒有直接把固件改成 `.tenant(...)` 就算了，而是先確認服務層是不是同樣的寫法——是（並讓「管理員經 HTTP 建規則」成為量測案例，修復前回 500）。

**為什麼一直沒被看到**：`M12PricingIntegrationTest`、`PricingServiceTest` 全部把 Repository 換成 mock，INSERT 從未真的執行過；前端沒有真實建立過定價規則的 E2E（又一例 `mock-repository-hides-never-executed-queries`）。

**修復**：三處改設 `.tenant(...)`（`createRule` 先以 `TenantRepository` 查出 `Tenant`，找不到回 `E-2000`；其餘取關聯物件）。**驗證**：修復前 `PricingRulePersistenceE2ETest` 3／3 紅（皆 500；4 個失敗請求在後端日誌各印出 NOT NULL 違反）、修復後 3／3 綠；單元以 `ArgumentCaptor` 斷言存檔的規則帶 `Tenant`；三處突變各被擊殺；Flyway 全棧上 `at-pricing-rules-real` 的 `E2E-PRICING-01／02` 驗證三個端點回 201／200／200（§6）。

**修復前的行為在 Flyway 層也有證據**：JAR 層突變 J2／J3／J4（把 `.tenant(...)` 拿掉＝回到修復前的寫法）在 Flyway 建出的資料庫上三個端點確實回 500（§6）。**誠實揭露**：這是等價的突變重現，不是真正的修復前 JAR（`git stash` 後重打包）；`createRule` 的突變另外多了一個沒用到的 `Tenant` 查詢。

## 5. DEF-416：帶房源 id 的『全部規則』列表在 Flyway 資料庫一律 500（途中發現，已修復）

在 Flyway 全棧上對 `DEF-414` 做一次性驗證時，建立規則成功、`GET /rules?roomListingId=…`（預設 `activeOnly=false`）卻回 500：`No enum constant ...PricingRuleType.WEEKEND_SURCHARGE`。原因是該分支 `findAll()` 把**全表所有租戶**的規則載入記憶體再過濾，而 `V5__Test_Data_Init.sql` 永遠在每個 Flyway 建出的資料庫裡放一列 `rule_type = 'WEEKEND_SURCHARGE'`（不在 Java 列舉；`application.yml` 的 Flyway 沒有環境過濾，也沒有後續遷移刪除它）。

**為什麼真實 PostgreSQL 整合測試重現不出來**：Hibernate 產生的測試庫對 `rule_type` 有 CHECK 約束，放不進這種值。只有 Flyway 建出的資料庫能。這也是 API_M12 §7 原本把它記成「效能疑慮」的原因——沒人知道它有功能性後果。

**修復**：新增衍生查詢 `PricingRuleRepository.findByRoomListingId`，`getRules` 改用，只載入這間房源的規則。**驗證**：修復前在真實全棧（Flyway）列表回 500（後端日誌堆疊指向 `getRules` 的 `findAll()`；日誌在暫存目錄，沒有進 repo）；單元 `RuleListingQueryTests` 以 `verify(never()).findAll()` 守住意圖（`findAll()` 被 stub 成空 List，舊實作得 0 筆而紅）；正式規格 `at-pricing-rules-real`（§6）；JAR 層突變（§6）。

## 6. 真實全棧（Flyway）規格 `at-pricing-rules-real`（新增）

| 案例 | 內容 |
|---|---|
| `E2E-PRICING-01` | 店主建立規則 → 201，規則租戶是自己的店鋪（`DEF-414`） |
| `E2E-PRICING-02` | 日曆改價 200、區間覆蓋 200；帶房源 id 的規則列表（預設 `activeOnly=false`）列得出三條且都屬於自己的店鋪（`DEF-414`／`416`） |
| `E2E-PRICING-03` | `beforeAll` 先由平台管理員建一條不綁房源的『平台自營規則』（蓋成系統租戶）；平台管理員列得出它（對照），無店鋪的新買家列規則得空陣列、帶別店房源 id 得 403（`DEF-411` 冒煙；完整矩陣在後端真實 PostgreSQL 測試） |

**JAR 層突變 5 個**（每個：改原始碼→`mvn clean package -DskipTests`→重啟全棧→跑整份規格）：

| 突變 | 內容 | 結果 |
|---|---|---|
| J1 | `DEF-416` 退回：列表走 `findAll()` 全表掃描 | 擊殺：`E2E-PRICING-02` 紅（列表回 500） |
| J2 | `DEF-414`：`createRule` 不設 `.tenant(...)` | 擊殺：`E2E-PRICING-01` 紅（`POST /rules` 回 500，其後案例沒執行） |
| J3 | `DEF-414`：`setCalendarPrice` 不設 `.tenant(...)` | 擊殺：`E2E-PRICING-02` 紅（日曆改價回 500） |
| J4 | `DEF-414`：`overridePrice` 不設 `.tenant(...)` | 擊殺：`E2E-PRICING-02` 紅（區間覆蓋回 500） |
| J5 | `DEF-411`：`getRules` 無參數分支的守門拿掉 | **第一次倖存**（3 個案例全綠）：Flyway 資料庫的系統租戶底下沒有任何規則，『買家得空陣列』是空洞斷言（後端真實 PostgreSQL 層的 P1 擊殺了同一個突變，所以守門本身有被守住，只是這一層沒牙齒）。加強規格——`beforeAll` 先由平台管理員建一條系統租戶名下的規則、並用平台管理員列表當對照——後再跑：擊殺，`E2E-PRICING-03` 紅，訊息『沒有店鋪的買家不得列出系統租戶名下的任何規則』 |

J2／J4 第一次因 `stack.sh down` 只送 SIGTERM 就返回、下一步 `up` 撞上尚未釋放的 8080 而「啟動失敗」，結果是 `START-FAIL`（不是擊殺）；修 `down` 等埠釋放後補跑。J1／J3 跑的是加強前的規格版本（它們的目標案例 `E2E-PRICING-01／02` 沒改）。每個突變還原後原始檔 SHA-256 與事先記錄一致。

## 7. 驗證總表

| 檢查 | 結果 |
|---|---|
| `tsc --noEmit`、ESLint（`--max-warnings 0`）對新增／改寫的規格 | 皆零輸出 |
| 後端單元針對性 | `PricingServiceTest` 92／92、`BookingServiceOwnerCalendarTest` 6／6 |
| 後端真實 PostgreSQL 針對性 | `PricingSystemTenantIsolationE2ETest` 6／6、`PricingRulePersistenceE2ETest` 3／3 |
| 後端突變 | 13／13 擊殺 |
| 前端突變 | 6／6 擊殺（基準全綠） |
| JAR 層突變（Flyway） | 5／5 擊殺（J5 第一次倖存、加強規格後擊殺；J2／J4 第一次未跑到、補跑擊殺） |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`（28 分 51 秒，第三次全量；機器負載偏高）；Checkstyle main／test 皆 0 違規；**單元 2333、整合 897**，皆 0 失敗／0 錯誤／0 跳過，日誌中 `<<< FAILURE`／`<<< ERROR` 標記 0 個。與 Sprint 302（2324／888）相比單元 +9（`PricingServiceTest` +7、`BookingServiceOwnerCalendarTest` +2）、整合 +9（`PricingSystemTenantIsolationE2ETest` 6、`PricingRulePersistenceE2ETest` 3），正好等於本輪新增的測試數；`ApiRouteDocDriftTest` 2／2、`TenantContextCallSiteDriftTest` 2／2、`M12PricingIntegrationTest` 12／12、`M12PricingProductIntegrationTest` 3／3。**前兩次全量都失敗**（§8 第 9 點）：第一次單元階段 1 個失敗（呼叫處基線），第二次整合階段 3 個失敗（兩個 mock Repository 整合測試） |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **第一次執行即全綠：278 案例，274 通過／4 跳過／0 失敗（11.0 分鐘）**，閘門顯示「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」。案例數由 Sprint 302 的 276 變 278＝`at-m15` 12→11（−1）加 `at-pricing-rules-real` 3（+3）；`AT-M15-REAL` 的 6 個與 `at-pricing-rules-real` 的 3 個都在整套裡執行並通過；Playwright 彙總沒有 flaky 行 |
| pre-commit、pre-push、雲端 CI | 見 §10 |

## 8. 過程中的失誤與修正

1. **第一版把寫入端的擁有權檢查一併收緊**（等於替使用者決定 `DEF-415`）：突變驗證跑到一半才發現，中止、收回（§3.3）。
2. **中止驅動時 `kill -INT` 先打到外層 zsh**（`pgrep -f` 回兩個 pid，Python 是第二個）：驅動多跑了一個突變才停；第二次打到 Python 才中止，`finally` 還原、SHA-256 核對一致。
3. **並行的兩個 Bash 呼叫共用工作目錄**，一個的 `cd` 蓋掉另一個的：編輯沒套用、報「路徑不存在」。立刻發現，單獨重跑（記憶裡早有這個坑）。
4. **把中文長行用 heredoc 傳給 python 被截壞**（`SyntaxError: Non-UTF-8 code`）：改用 Write 工具落檔再執行。
5. **`DEF-410` 第一版規格的定位器歧義**（§2.3）：種子貼文標題含『草稿』，與狀態徽章撞名。
6. **寫計畫書時把日誌 `grep -c` 的 16 寫成「16 次違反」**：自查時抓到，實際是 4 個失敗請求各印多行（16 是比對到的行數）。
7. **`stack.sh down` 不等埠釋放**：JAR 層突變 J2／J4 因 8080 仍被占用「啟動失敗」，白跑約 8 分鐘；結果是 `START-FAIL` 而非擊殺，修 `down` 後補跑。
8. **Flyway 層規格的『買家得空陣列』一度是空洞斷言**（§6，J5 倖存）：靠突變抓到，加強後擊殺。
9. **針對性測試漏掉兩種全域守門，全量回歸前兩次失敗**：(a) `TenantContextCallSiteDriftTest`——`PricingService` 的 `getCurrentTenant()` 呼叫處 4→5（新增的第 5 處是 `checkListingReadAccess`，本身就是 `isStoreTenant` 守門），評估後更新基線；(b) `M12PricingIntegrationTest`、`M12PricingProductIntegrationTest` 共 3 個案例——`createRule` 新增 `TenantRepository` 依賴後，它們寫死的租戶 id 查不到 `Tenant` → `E-2000` → 404（這兩個類別把 Repository 全換成 mock，也是 `DEF-414` 一直沒被看到的原因）。我的針對性測試只跑了自己寫的類別與 `PricingServiceTest`，沒有先 `grep` 出所有碰 `PricingService`／`createRule` 的測試與全域漂移守門。全量回歸本來就是為此，但「改了依賴或呼叫處後先找出所有受影響的測試」這一步我沒做。

## 9. 限制（誠實揭露）

1. **修復前的 `DEF-414` 狀態不是用真正的修復前 JAR 對 Flyway 資料庫跑的**，而是 J2／J3／J4 突變的等價重現（§4、§6）。
2. **`DEF-411` 只收緊讀取路徑**；寫入端點對沒有店鋪的 `ADMIN` 的語意沒動（`DEF-415`）。
3. **`V5` 的測試資料（`nextkey-test-tenant`、其房源與兩列定價規則）仍在每個 Flyway 建出的資料庫裡，包含正式環境**——是否有意、要不要用新遷移清掉，我沒有決定。`DEF-416` 只是讓程式碼不再被它絆倒。
4. `DEF-410` 保留的 5 個案例用沒有店鋪的新帳號、斷言偏弱，本輪沒動。
5. 前端突變只涵蓋我改寫的 6 個案例各一個代表性突變，不是窮舉。
6. **讀碼時看到、尚未實測的疑點已登記為 `DEF-417`**：商品定價規則（只帶 `listingId`）的『50 條上限』與『同類型時間重疊』檢查，查詢用 `findByRoomListingIdAndIsActiveTrue(null)`，若 Spring Data 把 null 轉成 `IS NULL`，查的就是全平台所有租戶的無房源規則；帶 `confirmOverride=true` 時甚至會軟刪除其他租戶的規則。這是推論（連『Spring Data 對 null 參數產生 `IS NULL`』這一點也沒實測），本輪不修。
7. 儀表板『`1 / 1`』斷言依賴「`activeListings` 計的是 `ACTIVE` 商品數」這個實作細節（`AnalyticsService` 的 `countByListingTenantIdAndListingStatus`）；若日後改定義，這個斷言要跟著改。

## 10. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 對『請依序完成所有任務！』的解讀是只做 `DEF-410`／`DEF-411`，不碰待決項目 | 總結自己列的就是這兩項；待決項目標明要使用者決定 | 明說連待決項目也授權 |
| 2 | `DEF-411` 只收緊讀取路徑，寫入端點維持原樣 | 避免替使用者決定『沒有店鋪的 `ADMIN` 能否管理平台自營資源』，且與其他服務不一致（§3.3） | 要求一次改齊五個服務（`DEF-415`） |
| 3 | 對沒有店鋪的呼叫者，`GET /rules` 無參數分支回**空陣列**而不是 403 | 比照 Sprint 234 對客服工單、退貨、結算的列表端點既有做法；前端列表頁不會因此顯示錯誤 | 改回 403 |
| 4 | 帶房源 id 時，沒有店鋪的呼叫者連『房源是否存在』都不透露（一律 403，不先查房源） | 先判斷、再查詢；省一次查詢 | 先查房源（不存在回 404） |
| 5 | 順手修復 `DEF-414`、`DEF-416`（不另開 Sprint） | 都是真實缺陷、修法小、是我正在改的函式／固件；`DEF-414` 是 `DEF-411` 測試的前提 | 改為只登記不修 |
| 6 | 『商品頁面』案例刪除而不是改寫 | `E2E-PRODLIST-01` 已完整涵蓋同一路由，重複保留徒增維護 | 恢復一個案例 |
| 7 | 新增正式規格 `at-pricing-rules-real`（Flyway 資料庫） | 這是唯一能重現 `DEF-416` 的層級，也是最接近正式環境的 `DEF-414` 守門；約 25 秒 | 刪掉，只留後端測試 |

## 11. 雲端 CI 與狀態

狀態：本文件隨主 commit 一起提交時**尚未 push**。commit hash、pre-commit／pre-push 結果與雲端 CI 結果，於 push 後以純文件 commit 回填（本文件與 `RELEASE_TRACKER.md` 的 Sprint 303 row；回填 commit 無法記錄自己的 hash）。

## 12. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/java/com/nextkey/ecommerce/core/pricing/PricingService.java` | `DEF-414`：三處 `.tenant(...)`、注入 `TenantRepository`；`DEF-411`：`getRules` 無參數分支、`checkListingReadAccess`；`DEF-416`：改用 `findByRoomListingId` |
| `backend/src/main/java/com/nextkey/ecommerce/domain/repository/PricingRuleRepository.java` | 新增 `findByRoomListingId`（`DEF-416`） |
| `backend/src/main/java/com/nextkey/ecommerce/core/booking/BookingService.java` | `checkListingTenantOwnership` 排除系統租戶佔位值（`DEF-411`） |
| `backend/src/test/java/.../api/controller/PricingSystemTenantIsolationE2ETest.java` | 新增（6 個真實 PostgreSQL 案例，`DEF-411`） |
| `backend/src/test/java/.../api/controller/PricingRulePersistenceE2ETest.java` | 新增（3 個真實 PostgreSQL 案例，`DEF-414`） |
| `backend/src/test/java/.../core/pricing/PricingServiceTest.java` | 新增 `TenantRepository` mock 與 7 個案例（`StorelessCallerTests`、`RuleListingQueryTests`） |
| `backend/src/test/java/.../core/booking/BookingServiceOwnerCalendarTest.java` | 新增 2 個案例（`DEF-411`） |
| `backend/src/test/java/.../integration/M12PricingIntegrationTest.java`、`M12PricingProductIntegrationTest.java` | 新增 `@MockBean TenantRepository` 與 `stubTenant()`，3 個 `createRule` 案例呼叫它（`DEF-414` 讓 `createRule` 需要 `Tenant` 實體；這兩個類別把 Repository 全換成 mock，這也是 `DEF-414` 一直沒被看到的原因） |
| `backend/src/test/java/.../shared/tenant/TenantContextCallSiteDriftTest.java` | `PricingService` 的 `getCurrentTenant()` 呼叫處基線 4→5（新增的第 5 處是 `checkListingReadAccess`，本身就是 `isStoreTenant` 守門），並更正舊註解『系統租戶目前無資料』（`DEF-411`） |
| `frontend/e2e/at-m15-e2e.spec.ts` | 7 個零斷言案例改寫為 `AT-M15-REAL`（6 個案例）（`DEF-410`） |
| `frontend/e2e/at-pricing-rules-real.spec.ts` | 新增 3 個案例（Flyway 資料庫，`DEF-414`／`416`／`411`） |
| `docs/02_architecture/api/API_M12_Dynamic_Pricing.md` | v2.1 → v2.2（§1.1、§4.3、§4.7、§5、§7：`DEF-411`／`414`／`416` 已修復、`DEF-415` 登記） |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-410`／`411` 結案；新增 `DEF-414`／`415`／`416` |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 303 row |
| `docs/04_planning/SPRINT_303_PLAN.md` | 新增（本文件） |
