# Sprint 310 Plan — DEF-427／DEF-425，以及讀 mock 時量到的 DEF-433（真實 Redis 下「處理中」的同鍵請求回 500，不是 409）

## 1. 起點與範圍

### 1.1 起點

使用者貼回 [Sprint 309](SPRINT_309_PLAN.md) 的總結，只說「請繼續完成任務！」——**沒有 `==>`，也沒有任何互動選項的答案**。我的解讀（推論，不是使用者說的）：與 Sprint 307／308 的無 `==>` 變體相同，做總結自己點名的「下次直接做」——`DEF-425`（`at-m17-003`／`at-m17-004` 四個永遠跳過的 E2E）與 `DEF-427`（IT-M06-103 斷言偏弱），兩者追蹤表都標「測試衛生，不需產品決策」。**不碰**總結「待你處理」裡需要使用者的項目：`DEF-426`（日曆手動改價，產品決策）、`DEF-419`（權限模型）、`make down-clean`→`make up`→`make dev-admin-password`、Docker 建置快取（Docker 政策，只回報）。

### 1.2 成功標準（開工時定義）

1. `DEF-427`：IT-M06-103 不靠執行緒與計時，契約各有確定性的案例；先量出舊版弱點；新版每條斷言都經突變驗證。
2. `DEF-425`：`at-m17-003`／`at-m17-004` 的 UI 層真的在跑（沒有 `test.skip()`），每個案例有鑑別力（突變驗證）；完整 E2E 套件 0 跳過。
3. 過程中發現的缺陷分三類處理：能獨立判定、有實測重現、修法極小的生產缺陷→紅燈先行並修；需要產品語意裁決或牽動多處 UI 的→只登記並附證據；測試衛生同類→登記，不擴大範圍。
4. 全量後端 `mvn -o clean verify`（全新測試 DB）與完整 E2E 套件通過；文件與追蹤表同步。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| `DEF-427`：「類別 Javadoc 寫真實 PostgreSQL 和 Redis，但匯入的 `IntegrationTestConfiguration` 把 `RedisTemplate` 換成 mock，兩者矛盾，我沒有查出是哪一個在作用」 | 讀碼查清：**只有 PostgreSQL 是真的**。`RedisConnectionFactory`、`RedisTemplate`、`RedisLockService`（永遠取得鎖）、`IdempotencyService` 都是 mock；後者以記憶體 `ConcurrentHashMap` 追蹤，**以原始鍵為索引，不分租戶／使用者**（真實服務的鍵是 `idempotency:{租戶}:{使用者}:{鍵}`）。所以預先標記不必設定 `TenantContext` |
| `DEF-427` 建議的 (2)「重送回快取 200 且沒有第二筆預訂」要新增 | **不用**。IT-M06-102 與真實 Redis 的 `BookingApiRealStackIntegrationTest.create_idempotencyKey`（Sprint 243）已確定性覆蓋，後者還斷言 `totalElements==1` |
| 「訂房的『處理中回 409』還有哪些測試碰到」 | 全測試樹 `grep E-6005`／`isStillProcessing`：訂房只有 IT-M06-103（且在 mock 下）；訂單有 `UT-ORDER-IDEM-006`（Mockito stub 讓 `getStoredResponse` 回 `null`）；**真實 Redis 下沒有任何案例**——這是 `DEF-433` 沒被發現的原因 |
| `DEF-425`「推論（沒有驗證）：選擇器也過期」 | **為真**，且有三處以上：開關是 `<button role="switch">`（`components/ui/switch.tsx`），不是 `input[type="checkbox"]`；列表卡片只有「查看詳情」、「編輯店鋪」在詳情頁（只對 `isOwner && ACTIVE`）；儲存成功是 `router.push` 回詳情頁，不顯示「成功」；編輯表單沒有店鋪描述欄位，所以規格「如果存在才填」的分支永遠沒走到 |
| 「Feature Toggle 數值更新」對應的產品功能 | **沒有**：功能頁的數量配額是唯讀用量條；`PUT /v2/dashboard/tenants/features/{數量配額}` 回 `E-9000`（`requireToggleableFeature`）；`PUT /v2/admin/tenants/{id}/features/{feature}` 只收 `enabled`；平台管理員的 `POST /v2/admin/feature-toggles` 收 `config`，但前端沒有任何呼叫（`lib/api.ts` 的對應常數已在 DEF-321(e) 改指別處）、也不是 StoreOwner 情境 |
| 「JAR 與 `.next` 是不是和 HEAD 一致」 | 開工時 JAR 比除 `BookingController.java` 以外的所有生產原始碼新（該檔的新 mtime 是我的突變驅動還原後 `utime` 造成，SHA-256 與 HEAD 相同）；為排除疑慮，第一次真實全棧跑之前仍重建 JAR 與前端 |

## 2. DEF-427 與 DEF-433

### 2.1 DEF-427：IT-M06-103 改成確定性案例

舊版：`CompletableFuture.runAsync` 送第一個請求、睡 100 毫秒、再送同鍵的第二個，之後 `409`（`E-6005`）與 `200`（回快取）兩種都算過，其他狀態碼只印警告。機器夠快時第一個請求先完成，只走 `200` 分支，「處理中回 `409`」這條契約根本沒被驗到。

**先量舊版的弱點**（驅動 `mutate_booking.py`，備份→套用→跑→還原→SHA-256 比對）：把 `BookingController` 的處理中分支改成回 HTTP 500，並在建立訂房前加 400 毫秒延遲（逼第一個請求仍在途中，讓第二個請求真的走到被改壞的分支）→ 舊版 **25 個案例仍全過**（25／0／0），日誌只印出 `⚠️ IT-M06-103: 收到狀態碼 500, Body: {"success":false,"code":"E-6005",…`。也就是「第二個請求確實走到了處理中的分支、而且分支被弄壞了」，這個測試仍是綠的。

新版：預先對該鍵呼叫 `IdempotencyService.checkAndMark`（標成處理中，等同另一個同鍵請求正在執行）→ 送**單一**請求 → 斷言 `409`、`success=false`、`E-6005`，並確認買家沒有任何預訂（409＝這個請求沒有被執行）。同時：

- 移除 Sprint 309 為修清理競態而加的 `inFlightRequest` 欄位與 `tearDown` 等待（已沒有背景請求，成為死碼）和 `TimeUnit` 匯入；
- 更正類別 Javadoc 的「真實 PostgreSQL 和 Redis」（只有 PostgreSQL 是真的）；
- IT-M06-103 的 Javadoc 說明記憶體 mock 與真實服務的差異，指向 `DEF-433`。

### 2.2 讀 mock 時量到的矛盾 → DEF-433

讀 `IntegrationTestConfiguration.idempotencyService()` 時注意到它的 `getStoredResponse` 在值是 `"PROCESSING"` 時**回 `null`**（註解：「重要：如果值是 "PROCESSING" 表示仍在處理中，返回 null」），而真實的 `IdempotencyService.getStoredResponse` 是 `return (T) redisTemplate.opsForValue().get(key)`——**直接回傳 Redis 的值**。處理中的鍵存的正是字串 `"PROCESSING"`（`checkAndMark`）。三個控制器的呼叫形狀相同：

```java
BookingDto.BookingResponse storedResponse = idempotencyService.getStoredResponse(idempotencyKey);
if (storedResponse != null) { return 200 快取 }
return 409 E-6005;   // 以為「處理中」會走到這裡
```

泛型 `T` 由指派對象推斷，編譯器在呼叫端插入 checkcast；字串轉成 `BookingResponse` 會失敗。**這是推論；所以先用真實棧量測**。

**一次性探針**（臨時的 `ZzIdempotencyInFlightProbeIntegrationTest`，放 `integration` 套件才吃得到 package-private 的 `ContractFixture`；真實 PostgreSQL＋真實 Redis＋真實 `IdempotencyService`＋完整過濾鏈，用完已刪）：先正常送一個帶 `Idempotency-Key` 的訂房（201），到真實 Redis 找出該鍵、把值改回字串 `PROCESSING`（等同第一個請求仍在途中），再送同鍵請求：

| 步驟 | 量到的 |
|---|---|
| 第一個請求 | 201；Redis 的值型別是 `BookingDto$BookingResponse` |
| 把值改回 `PROCESSING` | 值型別 `java.lang.String` |
| 第二個同鍵請求 | **HTTP 500，`E-9900`「發生未預期的錯誤」，`resolvedException = java.lang.ClassCastException: class java.lang.String cannot be cast to class …BookingDto$BookingResponse`** |

### 2.3 修復（紅燈先行）

先寫測試、對**未修復**的服務跑：

| 測試 | 未修復 | 修復後 |
|---|---|---|
| `IdempotencyServiceTest.getStoredResponse_whenStillProcessing_returnsNull`（新，單元） | 紅 | 綠 |
| `IdempotencyServiceTest.getStoredResponse_whenKeyMissing_returnsNull`（新，單元） | 綠（本來就對） | 綠 |
| `BookingApiRealStackIntegrationTest.create_idempotencyKeyStillProcessing_returns409`（新，真實 Redis） | 紅：`Status expected:<409> but was:<500>` | 綠 |
| `OrderApiRealStackIntegrationTest.createOrder_idempotencyKeyStillProcessing_returns409`（新） | 紅：`expected:<409> but was:<500>` | 綠 |
| `OrderApiRealStackIntegrationTest.mixedCheckout_idempotencyKeyStillProcessing_returns409`（新） | 紅：`expected:<409> but was:<500>` | 綠 |

三個端點的真實 500 都是實測、不是推論。修法：`IdempotencyService.getStoredResponse` 遇處理中標記回 `null`；標記字串抽成常數 `PROCESSING_MARKER`（取代 `checkAndMark`、`isStillProcessing` 的兩處字面值），三個控制器不用改。`ContractFixture` 新增 `markBuyerIdempotencyKeyProcessing`／`releaseBuyerIdempotencyKey`：鍵以租戶＋使用者範圍化（DEF-039），所以用買家的身分（系統租戶＋買家本人）標記；MockMvc 在測試執行緒內執行請求、過濾器結束時會清掉 `TenantContext`，所以每次操作自己設定、自己清。

## 3. DEF-425：`at-m17-003`／`at-m17-004`

### 3.1 設計

兩個規格都用 `seedStore`（真實流程：註冊→驗證 Email→申請開店→管理員核准→店主重新登入）保證「有店鋪的店主」，`serial` 模式共用一間店，**不再有任何 `test.skip()`**（比照 `at-m17-002`）。後端狀態都另外用 API 讀回驗證，不只相信畫面。

| 案例 | 內容 |
|---|---|
| E2E-M17-003-01 | 免審核的 `CMS_ENABLED`（預設開啟）：前提斷言 → 關閉 → 畫面開關與「已停用」徽章、API 讀回 `isEnabled:false／INACTIVE` → 重新整理後仍是關 → 再開啟 → 「已啟用」徽章、API 讀回 `isEnabled:true／ACTIVE`（AC-M17-006-2：免審核直接生效）。刻意**不斷言**3 秒後自動消失的成功訊息（不是規格訂的契約，且易受負載影響） |
| E2E-M17-003-02 | 需審核的 `PROMO_ENABLED`：點開 → 回應 `previousState:false／newState:false／PENDING_APPROVAL`（AC-M17-006-1）→ API 讀回 `isEnabled:false／PENDING` → 重新整理後開關仍是關 → 這是 `DEF-247`（店主自助繞過審核）的回歸防線 |
| E2E-M17-004-01 | 列表 →「查看詳情」→「編輯店鋪」→ 改名稱／網址代稱／信箱／電話 → 儲存 → 導回詳情頁，頁面顯示新名稱、信箱、電話；API 讀回與輸入一致，且表單沒有的**店鋪描述**與沒改的營業類型不被洗掉 |
| E2E-M17-004-02 | 只改店鋪名稱 → 儲存 → 其餘欄位（網址代稱、描述、營業類型、信箱、電話）逐一與儲存前相同 |

「Feature Toggle 數值更新」**移除**（§1.3 第五列：店主端沒有對應的產品功能）；以 AC-M17-006-1 的另一半（需審核功能）取代。

### 3.2 一次性探針（量測，不是測試）

`zz-probe-s310.spec.ts`（用完已刪，輸出 JSON 在 scratchpad）在真實全棧量了三件我事先不知道的事，成為 `DEF-428`～`DEF-430`：

1. **導覽**：收集 STORE_OWNER 在 `/dashboard`、`/dashboard/tenants`、店鋪詳情頁的 `a[href]`——儀表板首頁沒有通往店鋪管理的連結；詳情頁只有「返回列表」與「編輯店鋪」，沒有功能開關頁的入口（`DEF-430`）。
2. **新店鋪的初始狀態**：全新 `RETAIL_ONLY` 店鋪、零申請，`BOOKING_ENABLED`／`DYNAMIC_PRICING_ENABLED`／`PROMO_ENABLED` 的 API 讀回都是 `isEnabled:false／status:PENDING`，畫面在點擊前就顯示「待審核」（`DEF-429`）。
3. **需審核功能點開的當下**：`aria-checked="true"`、訊息「功能「PROMO_ENABLED」已啟用」，但 API 讀回 `isEnabled:false／PENDING`，重新整理後 `aria-checked="false"`（`DEF-428`）。

因此 E2E-M17-003-02 **不斷言**點擊當下的畫面，也不把「待審核」徽章當成點擊的結果（它在點擊前就存在）——只鎖回應契約與「不會直接生效」。

## 4. 測試

| 檔案 | 內容 |
|---|---|
| `api/controller/BookingControllerE2ETest`（修改） | IT-M06-103 改寫（§2.1）；移除 `inFlightRequest`、`tearDown` 等待、`TimeUnit` 匯入；Javadoc 更正；新增 `@Autowired IdempotencyService` |
| `core/idempotency/IdempotencyServiceTest`（新增 2 案例） | 處理中標記→`null`；鍵不存在→`null` |
| `integration/ContractFixture`（修改） | `markBuyerIdempotencyKeyProcessing`／`releaseBuyerIdempotencyKey` |
| `integration/BookingApiRealStackIntegrationTest`（新增 1 案例） | 真實 Redis：處理中的鍵 → 409 `E-6005`、買家沒有訂房 |
| `integration/OrderApiRealStackIntegrationTest`（新增 2 案例） | 真實 Redis：訂單與合併結帳各一——409 `E-6005`、沒有訂單／訂房、購物車保持原樣 |
| `frontend/e2e/at-m17-003.spec.ts`、`at-m17-004.spec.ts`（改寫） | §3.1 |

## 5. 突變驗證

### 5.1 IT-M06-103（驅動 `mutate_booking.py`，只改 `BookingController.java`，每次跑整個 `BookingControllerE2ETest`）

| 突變 | 對象 | 結果 |
|---|---|---|
| M1p：處理中分支回 500＋建立前延遲 400 毫秒 | **舊版** | 25／0／0，只印警告（弱點實證） |
| BASE：不突變 | 新版 | 25／0／0，印出新訊息（新案例確實執行） |
| M1：處理中分支回 500 | 新版 | 1 失敗＝IT-M06-103：`Expected status code <409> but was <500>` |
| M2：錯誤碼 E-6005→E-6004 | 新版 | 1 失敗＝IT-M06-103：`Expected: E-6005 / Actual: E-6004` |
| M3：回 409 但先偷偷建立預訂 | 新版 | 1 失敗＝IT-M06-103：狀態碼與錯誤碼斷言都過，**只有**「不可建立預訂」斷言叫：`Expecting empty but was: [Booking@…]`（證明第三條斷言有獨立的牙齒） |

### 5.2 `IdempotencyService`（驅動 `mutate_idem.py`，跑 `IdempotencyServiceTest`＋兩個真實棧類別共 44 案例）

| 突變 | 結果 |
|---|---|
| Z0：不突變 | 44 案例 0 失敗 |
| R1：標記比對錯誤（等同修復前行為） | 恰好 4 個新案例紅（單元 1、真實 Redis 3） |
| R2：`getStoredResponse` 一律回 `null` | 恰好 3 個「回放快取」既有案例紅（`IdempotencyServiceTest.getStoredResponse_returnsStoredValue`、`BookingApiRealStackIntegrationTest.create_idempotencyKey`、`OrderApiRealStackIntegrationTest.createOrder_idempotencyKey`），**4 個處理中案例維持綠**——兩種行為各有獨立守門，修復沒有過度擋 |

### 5.3 E2E 規格（驅動 `mutate_e2e.py`：套用→重建該端→單規格真實全棧→還原→**再重建乾淨**；先跑無突變基準 Z0：四個案例全綠，74 秒）

| 突變 | 殺手 | 紅燈原因 |
|---|---|---|
| FM1 前端送反向 `enabled` | 003-01 | 畫面開關顯示「關」（UI 斷言過），**API 讀回 `isEnabled` 沒變**——證明必須斷言後端 |
| FM2 前端不翻轉本地開關狀態 | 003-01 | `aria-checked` 預期 false、實際 true |
| BM1 後端不寫入停用 | 003-01 | API 讀回仍是啟用 |
| BM2 後端讓需審核功能被自助啟用（`pendingApproval=false`，`DEF-247` 的回歸） | 003-02（003-01 維持綠） | 回應 `newState` 預期 false |
| FM3 編輯表單沒送新信箱 | 004-01 | 詳情頁信箱預期 `updated-…`、實際 `store-…` |
| FM4 儲存後導回列表而不是詳情 | 004-01 | `toHaveURL` 預期詳情頁、實際 `/dashboard/tenants` |
| FM5 表單多送空的 `storeDescription` | 004-01 | 描述被洗掉（`toMatchObject`） |
| BM3 後端忽略 `contactPhone` | 004-01 | 詳情頁電話預期 `+886-988-888-888`、實際 `0912345678` |
| BM4 後端把沒送的描述清空 | 004-01 | 描述被洗掉 |
| BM5 後端把沒改的網址代稱誤判衝突（拿掉「同值就略過」） | **只有 004-02**（004-01 因為有改代稱而綠） | 儲存回 `409`（預期 200） |

BM5 是專門為了證明 004-02 的獨立價值設計的：原本 FM5／BM4 這類「洗掉沒送的欄位」缺陷會被先跑的 004-01 吃掉（serial 共用同一間店，後案例比對的「前一刻狀態」已被洗成 null，null 對 null 永遠相等）——所以我在 004-01 就鎖住「表單沒有的欄位不變」，並在兩個案例加前提斷言（描述必須有值）。

所有突變還原後逐檔 SHA-256 比對一致，並以 `git status`／`git diff -- backend/src/main frontend/src` 與突變殘留字串 `grep` 獨立核對。

## 6. 驗證總表

| 項目 | 結果 |
|---|---|
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`，**18 分 58 秒**；單元 **2380／0 失敗**（Sprint 309 的 2378＋本輪 2）；整合 **957／0 失敗**（954＋本輪 3）；Checkstyle main／test 皆 0 違規；日誌 `<<< FAILURE`／`<<< ERROR` 標記 0 個。本輪相關類別：`IdempotencyServiceTest` 14／14、`IdempotencyServiceTenantIsolationTest` 2／2、`OrderControllerIdempotencyTest` 7／7、`BookingControllerE2ETest` 25／25、`BookingApiRealStackIntegrationTest` 17／17、`OrderApiRealStackIntegrationTest` 13／13 |
| 完整 E2E 套件（Flyway 資料庫＋真實 Redis；`validate-e2e.sh` 副本、list 報表） | 第一次（IdempotencyService 修復**之前**，產物為突變後乾淨重建）：**286 通過／0 跳過／0 失敗**，6.7 分鐘；認證端點被限流 22 次（輔助函式重試自癒）。**修復後重跑**（JAR 是 verify 剛打包、含 `IdempotencyService` 修復；前端是突變驗證後從還原的原始碼乾淨重建，沒有重跑 `npm ci`）：**286 通過／0 跳過／0 失敗**，6.7 分鐘；認證端點被限流 13 次（重試自癒）；四個改寫的案例都在清單內且通過；清單中跳過、失敗、flaky 的標記皆 0 個 |
| 文件守門測試（`DocMarkdownFenceTest`、`ApiRouteDocDriftTest`、`ErrorCodeDocDriftTest`、`TechStackDocDriftTest`） | 它們屬單元階段，verify 跑到它們的時候我還在寫文件；所以在**所有文件定稿後**另行補跑：4 個類別共 **14 個案例，0 失敗**，`BUILD SUCCESS`（`ApiRouteDocDriftTest` 2、`DocMarkdownFenceTest` 9、`TechStackDocDriftTest` 2、`ErrorCodeDocDriftTest` 1） |
| ESLint（`--max-warnings 0`）／`tsc --noEmit`（兩個新規格） | 退出碼皆 0；ESLint 另用控制組（餵一個未使用變數）確認 `e2e/` 目錄確實有被檢查 |
| IDE Problems（`getDiagnostics`） | DEF-427 的測試改完後全專案 362 個檔案條目、診斷 0；收尾再查一次：**382 個檔案條目、診斷 0**，本輪改過的後端檔案都在條目內（IDE 有分析到）。兩個前端規格不在條目內（TypeScript 語言服務只分析開啟的檔案），以 ESLint＋`tsc --noEmit` 退出碼 0 為準 |

## 7. 過程中的失誤與修正

1. **zsh 未加引號的萬用字元讓指令根本沒跑**（兩次：`grep --include=*.md`、`--include=*.java`）：輸出是 `no matches found`，我差點讀成「沒有結果」。重跑時一律加引號。
2. **`cd` 讓並行呼叫的工作目錄漂移**（Bash 內 `cd frontend`／`cd backend` 之後，下一個呼叫的 cwd 變了）：之後一律絕對路徑。
3. **004-02 的原始設計有盲點**（§5.3 末段）：設計突變時才發現，serial 共用同一間店會讓「洗掉沒送的欄位」被先跑的案例吃掉；補了 004-01 的描述保留斷言與前提斷言，並新增 BM5 證明 004-02 的獨立價值。
4. **003-01 第一版有一條 3 秒後自動消失的成功訊息斷言**：移除（負載高時有時間炸彈的風險，且不是規格訂的契約）。
5. **規格標頭初稿寫「Admin 端同樣只收 `enabled`」不精確**：查到 `POST /v2/admin/feature-toggles` 收 `config`（SUPER_ADMIN、無畫面呼叫），已更正成精確說法。
6. **追蹤表把 `DEF-427` 當成純測試衛生**：實際讀 mock 才發現它背後是真實缺陷（`DEF-433`）。追蹤表原文明寫「做之前要先讀清楚」，這次讀清楚的代價是範圍擴大——但也是這輪最有價值的發現。
7. **我最初在 `DEF-429` 的描述裡把「管理員可能核准從沒申請過的功能」寫得像事實**：改成明標「推論」（我只量了店主端 API，沒有重新量 Admin 頁面）。

## 8. 限制（誠實揭露）

1. **`DEF-433` 沒有測「兩個請求真正同時在途」的時序**：用預先標記模擬「處理中」。真實 `setIfAbsent` 的原子性（兩個請求同時搶同一把鍵、只有一個成功）**沒有任何真實 Redis 的測試**——`IdempotencyServiceTest` 只用 Mockito 驗證呼叫形狀（鍵、標記值、TTL），不驗原子性；要補需要「雙執行緒打真實 Redis」的測試（比照 `AccountTokenServiceIntegrationTest` 直連 Lettuce 的範式）。本輪沒有做，也沒有登記成缺陷（沒有證據顯示它有問題，只是沒被驗證）。
2. 三個端點中，訂房／訂單／合併結帳各有一個真實 Redis 案例；**訂單與合併結帳的 500 是實測的**（不是讀碼推論）。
3. `at-m17-003-02` 的「待審核」徽章在點擊前就存在（`DEF-429`），所以它不鑑別「點擊產生了待審核」，只鎖回應契約與「不會直接生效」。
4. `DEF-428` 的畫面狀態**沒有**寫進規格（避免把錯誤行為寫成規格）；修復時要補。
5. `DEF-430` 的導覽結論只來自 `a[href]` 與 `grep`，沒有檢查以按鈕 `router.push` 做的導覽。
6. 「何時開始永遠跳過」的時間線（`DEF-425` 原文就說沒重建）仍未重建。
7. 沒有 push，**雲端 CI 沒有驗證這一輪**；`e2e` job 在雲端仍未實跑。
8. PRD／FRD 沒有改。

## 9. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 想推翻時 |
|---|---|---|---|
| 1 | **順手修了 `DEF-433`（生產程式碼）**，而不是只登記 | 有實測重現（三端點都是 500）、修法極小（一個方法 3 行）、不涉及產品決策、文件與控制器註解本來就寫 409、紅燈先行＋突變驗證齊全；放著等於留一個已知的生產 500 | 告訴我回退 `IdempotencyService` 的修改（三個真實 Redis 案例改標為已知失敗） |
| 2 | 003 的「數值更新」案例**移除**而不是改寫 | 店主端沒有對應的產品功能（§1.3） | 告訴我你要做那個功能 |
| 3 | 功能開關頁用網址直接前往 | 沒有導覽入口（`DEF-430`） | 補上入口後改成走導覽 |
| 4 | 不在 IT-M06-102 加「沒有第二筆預訂」斷言 | 已由真實 Redis 的 `create_idempotencyKey` 確定性覆蓋（Rule 3） | 告訴我加 |
| 5 | 移除 Sprint 309 的 `inFlightRequest` 腳手架 | 改寫後成為死碼（Rule 3：清理自己造成的孤兒） | — |
| 6 | `DEF-428`～`DEF-432` 只登記、不修 | 428／429 互相影響且 429 要你裁決；430／432 是 UI 增補；431 是另一個測試的重寫 | 告訴我先做哪個 |
| 7 | 補寫 `API_M05` v2.1、`API_M06` v2.5、`API_M17` v2.4 的現況 | 文件原本寫 409／未說明 `PENDING` 的實際判定，與實測不符 | 告訴我拿掉 |
| 8 | **不 push** | 這輪含生產程式碼，但那是我自己發現的、不是你選定的選項，也不在 2026-07-08 的明確 push 授權範圍內 | 告訴我 push |

## 10. 新登記的缺陷

| ID | 摘要 | 狀態 |
|---|---|---|
| `DEF-428` | 點開需審核功能後，畫面誤顯示開關開啟與「已啟用」（後端實際待審核） | 未處理，不需產品決策；建議與 429 一起修 |
| `DEF-429` | 核准開店建的預設資料列被判成 `PENDING`——沒人申請也顯示待審核 | 未處理，**待裁決**（「已申請」怎麼表示） |
| `DEF-430` | 店主端沒有導覽通往店鋪管理頁與功能開關頁 | 未處理，建議直接做 |
| `DEF-431` | IT-M06-104 沒有任何會失敗的斷言，且鎖是永遠成功的 mock | 未處理，測試衛生 |
| `DEF-432` | 編輯表單缺店鋪描述；編輯頁有永遠不成立的 `'StoreOwner'` 判斷；編輯鈕只對 ACTIVE 顯示 | 未處理，②需確認 |
| `DEF-433` | 真實 Redis 下同鍵請求處理中回 500（三個端點） | **已修復（本輪）** |

## 11. 狀態

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) 的 Sprint 310 列。**本輪的 commit 留在本機，沒有 push**（本機另有 Sprint 309 的純文件回填 commit `0532edc0`，同樣未 push）。

本輪的 commit 範圍：生產程式 `IdempotencyService`；測試 `BookingControllerE2ETest`、`IdempotencyServiceTest`、`ContractFixture`、`BookingApiRealStackIntegrationTest`、`OrderApiRealStackIntegrationTest`、Playwright `at-m17-003.spec.ts`／`at-m17-004.spec.ts`；文件 `API_M05_Order.md`（v2.1）、`API_M06_Booking.md`（v2.5）、`API_M17_Tenant.md`（v2.4）、`DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md`、`SPRINT_308_PLAN.md`／`SPRINT_309_PLAN.md`（各補一則後續指引）與本計畫書。沒有改遷移、PRD、前端應用程式碼。
