# Sprint 304 Plan — DEF-415（沒有店鋪的 ADMIN 不得管理系統租戶〔平台自營〕的房源、商品、SKU、定價規則與日曆）

## 1. 起點與範圍

### 1.1 起點（對「繼續」的解讀是推論，但 `==>` 回覆是使用者本人的真實輸入）

使用者貼回 [Sprint 303](SPRINT_303_PLAN.md) 的總結，在總結末段「我需要你回答兩件事，才能做 `DEF-418`」旁用 `==>` 回覆：

1. 是否已有任何正式或預備環境用這些遷移建過資料庫？==> **還沒有正式或測試環境**
2. 採做法 A（新增 `V94` 前進式遷移刪除）還是做法 B？==> **A**

結尾「請繼續依序完成該完成任務！」。這是**有 `==>` 回覆**的變體：兩個回答都是使用者本人的決定。Sprint 303 總結自己寫明排程：「你回『請繼續』，我就先做 `DEF-415`（你已決定）；`DEF-418` 等你回答上面兩題」。所以：

| Sprint | 內容 | 為何排這個位置（順序是我依相依性與風險排的，不是使用者指定的） |
|---|---|---|
| **304（本輪）** | `DEF-415`：沒有店鋪的 `ADMIN` 不得管理平台自營資料（使用者於 Sprint 303 收尾已決定「比照 Sprint 234 全部擋下」） | 使用者已決定、不需再問；只動後端程式與測試 |
| 305 | `DEF-418`：做法 A（`V94` 前進式遷移） | 改遷移會牽動 E2E gate（對 `admin@nextkey.local` 有 6 個規格依賴）與 schema 文件守門，需自成一個 Sprint 驗證；不與本輪混在一起，才分得出紅燈來源 |
| 之後 | `DEF-417`（商品定價規則的上限／重疊檢查疑似跨租戶，讀碼推論、需先實測） | 不需產品決策，但仍是推論 |

### 1.2 成功標準（開工時定義）

1. **先實測再改**：用真實 PostgreSQL＋完整 HTTP／JWT 鏈，對**未修改的程式碼**量出「沒有店鋪的 `ADMIN` 現在能做什麼」（追蹤表原本只是讀碼推論）。
2. 每個案例固定三件事：沒有店鋪的 `ADMIN` 被擋；被擋之後資料庫的資料沒有被改；對照組照常運作（`SUPER_ADMIN`、有店鋪的 `ADMIN`；店主碰別人的資料仍是 403）。
3. 每個守門都經過突變驗證（拿掉、過度擋、順序錯、傳錯參數），逐條讀失敗名稱確認原因。
4. 全量後端回歸與完整 `make validate-e2e` 通過。
5. 範圍判斷明文化：使用者的決定寫的是「一次改齊五個服務的**擁有權檢查**」，建立端不是擁有權檢查而是「蓋章」——要寫明我怎麼解讀、為什麼，並可一句話推翻（§9 第 1 點）。

### 1.3 動手前後用工具核對的事實（不憑印象）

| 說法 | 核對結果 |
|---|---|
| 「沒有店鋪的 `ADMIN` 現況可以管理平台自營資料」（追蹤表 `DEF-415` 登記時是讀碼推論） | **實測屬實**：修復前 18 個案例全紅，沒有店鋪的 `ADMIN` 對五個服務的 18 個寫入操作全部成功（回 200／201），含建立房源、商品、統一建立、不綁房源的定價規則（§2） |
| 「`ADMIN` 持有這些端點要求的權限」 | 讀 `RolePermissionMapping` 的 `ADMIN` 區塊：持有 `PRODUCT_READ`／`CREATE`／`UPDATE`／`DELETE` 與 `ROOM_READ`／`CREATE`／`UPDATE`／`DELETE`。修復前同一批請求回 200／201 也證實權限註解對它放行 |
| 「403 的回應訊息可以分辨是權限註解還是擁有權檢查擋下的」（**我的測試設計前提**） | **不成立**：`BusinessException` 的 `details` 依 AI-2418 設計不外洩，`getUserMessage()` 固定回 `ErrorCode` 的預設訊息，`E_1007` 就是「權限不足」，與 `AccessDeniedException` 處理器的回應文字相同。第一版測試的訊息斷言因此在綠燈階段 18 個案例全紅（狀態碼其實都是 403，§7 第 1 點）。改用「同角色、有店鋪的 `ADMIN` 對自己店鋪做同一個操作成功」的對照組證明擋下的是擁有權檢查 |
| 三個建立端點有沒有帶呼叫者身分 | 沒有：`RoomController.createRoom`、`ProductController.createProduct`、`DashboardListingController.createListing` 原本都沒有 `@AuthenticationPrincipal`，也沒有 `isSuperAdmin`，所以要新增參數（其餘寫入端點本來就有） |
| `getCurrentTenant()` 呼叫處基線會不會變 | 只有 `PricingService` 5→4（`checkListingReadAccess` 併入 `checkListingTenantOwnership`，少一處）；其餘四個服務的呼叫處數量不變（改的是既有的呼叫處或沿用已取得的 `tenantId`），逐一用 `grep` 計數確認 |
| 平台自營資料的 `owner`／租戶從哪來 | 沿用 Sprint 303 的實測：真實登入的 `SUPER_ADMIN` 簽發的租戶是系統租戶佔位值，建立的資料蓋成系統租戶 |

## 2. 修復前實測（`PlatformSelfRunWriteIsolationE2ETest`，未修改的主程式）

**第一次跑**：18 個案例 4 失敗＋14 錯誤。4 個失敗是建立類案例（`POST /v2/rooms`、`/v2/products`、`/v2/dashboard/listings`、不綁房源的 `POST /rules`），失敗在「預期 403」——實際回 201／201／200／201。另外 14 個**錯誤與被測程式無關**：我的固件把 `Listing` 與 `Room`／`Product` 分開兩個交易存，`@MapsId` 對 `Listing` 串聯 persist 時它已是 detached（`detached entity passed to persist: Listing`）。修固件（`TransactionTemplate` 包同一個交易）後重跑。

**第二次跑（固件修好）：18 個案例全紅，每個都卡在第一個「沒有店鋪的 `ADMIN` 被擋」斷言**，實際狀態如下：

| 服務 | 沒有店鋪的 `ADMIN` 做的事 | 修復前實測 |
|---|---|---|
| `RoomService` | 更新房源／刪除房源／清除開放窗／建立房源 | 200／200／200／**201** |
| `ProductService` | 更新商品／刪除商品／建立商品 | 200／200／**201** |
| 統一建立 | `POST /v2/dashboard/listings`（ROOM 分支先被打到） | 200 |
| `ProductSkuService` | 新增 SKU／更新 SKU | **201**／200 |
| `RoomCalendarService` | 標記維護／解除維護 | 200／200 |
| `PricingService` | 建立規則（綁系統租戶房源）／建立規則（不綁房源）／更新規則／停用規則／覆蓋價格／日曆單日改價 | **201**／**201**／200／200／200／200 |

**誠實揭露這個基準的邊界**：每個案例在第一個斷言就中止，所以對照組（`SUPER_ADMIN`、有店鋪的 `ADMIN`、店主碰系統租戶資料）與「資料庫沒被改」的斷言**在修復前的程式碼上沒有被執行到**；它們由綠燈階段與突變驗證（§5）證實。

## 3. 修法

### 3.1 一個共同判斷

`TenantContext.isStoreTenant(callerTenantId)`（Sprint 234 的述詞：非 `null` 且不是系統租戶佔位值）。沒有店鋪的呼叫者——含沒有店鋪的 `ADMIN`（租戶內角色，持有 `room:*`／`product:*` 寫入權限）——租戶是系統租戶佔位值、全體共用，平台自營資料也蓋成同一個值，單看「租戶相等」會把他們當成擁有者。`SUPER_ADMIN`（租戶同為系統租戶）維持原本直接放行。

### 3.2 各處改動

| 位置 | 改法 |
|---|---|
| `PricingService.checkRuleTenantOwnership`（更新／刪除／覆蓋／日曆改價共用） | 在租戶相等比對前加 `isStoreTenant`；`resourceTenantId` 與呼叫者租戶都是 `null` 的舊相等情形也因此被擋 |
| `PricingService.checkListingTenantOwnership`（建立規則；**同時**服務讀取端點） | `isStoreTenant` 判斷放在**查詢房源之前**，也放在「`targetListingId` 為 null 就略過」**之前**——不綁房源的規則一樣蓋成呼叫者的租戶，沒有店鋪者建出來的就是系統租戶的規則。Sprint 303 的讀取專用 `checkListingReadAccess` 與它重複，**併入同一個方法**並刪除；錯誤訊息統一為 `Not authorized to access pricing for this listing`（原本讀寫兩種文案；測試與文件都沒有引用這兩句） |
| `RoomService`、`ProductService`、`ProductSkuService` 的 `checkListingTenantOwnership(Listing, isSuperAdmin)` | 條件改成 `!isStoreTenant(caller) \|\| !listing.getTenantId().equals(caller)`（資源已被呼叫端載入，所以判斷與比對併在同一個 `if`，比照 Sprint 303 `BookingService` 的寫法） |
| `RoomCalendarService.checkListingTenantOwnership(UUID, isSuperAdmin)` | 這個 helper 自己查房源，所以先 `isStoreTenant`、後查詢（先判斷再查詢，省一次查詢，也不讓沒有店鋪者經由 404／403 探測房源是否存在） |
| `RoomService.createRoom`／`createRoomFromDashboard`、`ProductService.createProduct`／`createProductFromDashboard` | 新增 `isSuperAdmin` 參數與私有 `checkCanCreateListing`，**放在功能開關與配額檢查之前**（兩者都要查資料庫）。`createRoom`／`createProduct` 原本先檢查功能開關，改為先取 `tenantId` 與守門、再檢查開關（只調換既有兩行的順序） |
| `RoomController.createRoom`、`ProductController.createProduct`、`DashboardListingController.createListing` | 新增 `@AuthenticationPrincipal UserPrincipal`，以 `SUPER_ADMIN_ROLE.equals(principal.getRole())` 算出 `isSuperAdmin` 傳入（與同檔其他寫入端點完全相同的慣例；`DashboardListingController` 補上常數與兩個 import） |

### 3.3 刻意沒做的事

- **讀取端點不動**：`GET /v2/rooms`、`GET /v2/products` 等（讀碼：以呼叫者的租戶篩選，且只回 `ACTIVE` 的項目，所以沒有店鋪的呼叫者會列出系統租戶名下 `ACTIVE` 的項目；這一點我沒有實測，也沒有核對它與公開瀏覽入口看得到的內容是否完全相同）。使用者的決定針對的是「管理」，不含讀取。
- **有 id 的更新／刪除（房源、商品、SKU）維持「先載入再檢查」的既有順序**：沒有店鋪的 `ADMIN` 對不存在的 id 得 404、對存在的得 403，與其他呼叫者既有行為相同（`GET /v2/rooms/{id}` 本來就可由任何持有 `room:read` 的人讀取）。要改成先判斷必須在每個公開寫入方法開頭再加一道呼叫，而使用者決定的是「比照 Sprint 234、先 `isStoreTenant` 再比對」，所以我只在 helper 自己查詢的兩處（定價、日曆）與建立端做「先判斷再查詢」。
- **前端沒動**：沒有店鋪的 `ADMIN` 在介面建立商品／房源會看到「權限不足」。

## 4. 測試

| 層 | 內容 |
|---|---|
| 真實 PostgreSQL＋完整 HTTP／JWT 鏈（新增 18 個案例） | `PlatformSelfRunWriteIsolationE2ETest`：真實登入的沒有店鋪 `ADMIN` 與 `SUPER_ADMIN`、一間真實店鋪的店主與同店鋪內角色為 `ADMIN` 的成員。涵蓋更新／刪除／清除開放窗／建立房源、更新／刪除／建立商品、統一建立（ROOM 與 PRODUCT）、新增與更新 SKU、標記與解除維護、建立規則（綁房源、不綁房源）、更新／停用／覆蓋規則、日曆單日改價。每個案例：沒有店鋪的 `ADMIN` 得 403＋`E-1007`、**直接查資料庫**確認資料沒被改（標題、狀態、開放窗、規則內容與啟用狀態、SKU、日曆列、新建資料列數）、`SUPER_ADMIN` 照常且建立的資料蓋成系統租戶、**有店鋪的 `ADMIN` 對自己店鋪的同一個操作成功**（同角色對照，證明擋下的是擁有權檢查而不是權限註解）、店主碰系統租戶資料仍是 403 |
| 單元（新增 28） | `PricingServiceTest.StorelessCallerWriteTests` 9（建立綁房源且先判斷不查詢、建立不綁房源、`SUPER_ADMIN` 不綁房源仍可且蓋成系統租戶、更新、`SUPER_ADMIN` 更新、停用且規則仍啟用、覆蓋、日曆改價、店主管自己店鋪仍可）；`RoomServiceTest` +7；`ProductServiceTest` +6；`ProductSkuServiceTest` +3；`RoomCalendarServiceTest` +3 |
| 既有測試因簽名變更而調整 | `RoomServiceTest`、`ProductServiceTest` 各 4 處建立方法呼叫補 `false`；`DashboardListingControllerAuthorizationIntegrationTest` 的兩個 mock 補 `anyBoolean()`；`TenantContextCallSiteDriftTest` 的 `PricingService` 基線 5→4（含註解） |
| 守門測試的寫法 | `lenient()` stub 備妥系統租戶名下的資源＋`verify(never())`：守門被拿掉時舊的租戶相等檢查會放行、操作會繼續執行，測試才會紅，而不是被 Mockito 對 `Optional`／`List` 的預設回傳掩蓋成別的例外 |

## 5. 突變驗證

驅動（Python，放暫存目錄，不進 repo）：每個突變備份→套用（`old` 必須在檔案中恰好出現一次，事先對全部 38 個樣式檢查過）→等 class 比原始碼新→`mvn -o test-compile surefire:test … failsafe:integration-test …`（單元 6 個類別〔`PricingServiceTest` 含巢狀類別、`RoomServiceTest`、`ProductServiceTest`、`ProductSkuServiceTest`、`RoomCalendarServiceTest`、`BookingServiceOwnerCalendarTest`〕加 2 個整合類別〔`PlatformSelfRunWriteIsolationE2ETest`、`PricingSystemTenantIsolationE2ETest`〕，共 238 個案例＝單元 214＋整合 24）→還原→SHA-256 比對。失敗集合取自 surefire／failsafe 的 XML。**下面的「擊殺者」只統計這 8 個類別**，其他測試類別（例如 controller 層的測試）沒有納入這次突變驗證。

**38 個突變全數擊殺（0 倖存、0 無效）**，每個都由預期的案例擊殺（逐條讀失敗案例名稱）：

| 守門點 | 突變 | 擊殺者 |
|---|---|---|
| `PricingService` 規則 helper（P1、P2、P7） | 拿掉 `isStoreTenant`／過度擋 `SUPER_ADMIN`／拿掉租戶相等 | P1：新單元 4＋E2E 4；P2：新單元 `…superAdmin_systemTenantRule_stillAllowed`＋E2E 4；P7：既有跨租戶單元 4＋E2E 4（證明原有的相等比對沒被放寬） |
| `PricingService` 房源 helper（P3～P6、P8） | 拿掉守門／不綁房源的略過放在守門前／先查房源再守門／拿掉相等／過度擋 `SUPER_ADMIN` 不綁房源 | P3：建立（綁／不綁）與 Sprint 303 讀取案例；**P4 只被不綁房源的案例擊殺**（單元＋E2E 各 1）；**P5 只有單元擊殺**（`verify(never()).findById`，HTTP 看不出查詢先後）；P6：既有跨租戶單元＋E2E；P8：`SUPER_ADMIN` 不綁房源的對照 |
| `RoomCalendarService`（C1～C4） | 拿掉守門／守門放到查詢後／過度擋 `SUPER_ADMIN`／拿掉相等 | C1：單元 2＋E2E 2；**C2 只有單元**；C3：既有與新增共 11 個單元＋E2E 2；C4：既有跨租戶單元＋E2E |
| `ProductSkuService`（K1～K3） | 拿掉 `isStoreTenant`／過度擋／拿掉相等 | 各由新增與既有單元＋E2E 擊殺 |
| `RoomService`（R1～R8）、`ProductService`（D1～D8） | helper 拿掉守門、過度擋、拿掉相等；建立守門的條件改成永不成立、過度擋 `SUPER_ADMIN`；**四個建立方法各自拿掉守門呼叫**；`createRoom`／`createProduct` 守門放到功能開關之後 | **四個建立方法各被自己的單元案例與自己的 E2E 案例分別擊殺**（證明四處是獨立覆蓋）；**守門放到開關之後只有單元擊殺**（`verify(featureToggleService, never())`） |
| 三個 controller（T1～T7） | `isSuperAdmin` 傳 `true`（沒有店鋪的 `ADMIN` 因此成功）、傳 `false`／固定 `false`（`SUPER_ADMIN` 因此被擋） | **在選用的 8 個測試類別裡只有 E2E 擊殺**，各被對應的建立案例擊殺（既有的 `DashboardListingControllerAuthorizationIntegrationTest` 以 `anyBoolean()` mock 服務、不驗證傳入的值，所以不會擊殺；其他 controller 層測試沒有納入這次突變驗證，我沒有核對它們是否涵蓋這個參數傳遞） |

原始檔還原後 8 個檔案的 SHA-256 與事先記錄逐一相同，沒有 `.mut415.orig` 殘留，`git status` 只有預期的變更。

## 6. 驗證總表

| 檢查 | 結果 |
|---|---|
| 修復前 | `PlatformSelfRunWriteIsolationE2ETest` 18／18 紅（§2） |
| 修復後（真實 PostgreSQL） | `PlatformSelfRunWriteIsolationE2ETest` 18／18 綠（§7 第 1 點改成同角色對照組之後的那一輪）；修復後的主程式另跑 `PricingSystemTenantIsolationE2ETest` 6／6、`PricingRulePersistenceE2ETest` 3／3、`DashboardListingControllerAuthorizationIntegrationTest` 4／4（較早的另一輪，當時新 E2E 只因訊息斷言而紅） |
| 單元針對性 | 7 個類別共 216／216（`PricingServiceTest` 101〔含 `StorelessCallerWriteTests` 9〕、`RoomServiceTest` 30、`ProductServiceTest` 27、`ProductSkuServiceTest` 14、`RoomCalendarServiceTest` 36、`BookingServiceOwnerCalendarTest` 6、`TenantContextCallSiteDriftTest` 2） |
| 突變 | 38／38 擊殺（§5） |
| 全量後端 `mvn -o clean verify`（全新測試 DB） | `BUILD SUCCESS`（18 分 06 秒）；Checkstyle main／test 皆 0 違規；**單元 2361、整合 915**，皆 0 失敗／0 錯誤／0 跳過，日誌中 `<<< FAILURE`／`<<< ERROR` 標記 0 個。與 Sprint 303（2333／897）相比單元 +28（`PricingServiceTest` +9、`RoomServiceTest` +7、`ProductServiceTest` +6、`ProductSkuServiceTest` +3、`RoomCalendarServiceTest` +3）、整合 +18（`PlatformSelfRunWriteIsolationE2ETest`），正好等於本輪新增的測試數；`ApiRouteDocDriftTest` 2／2、`TenantContextCallSiteDriftTest` 2／2。**第一次全量即通過**：動手前我先 `grep` 了四個建立方法在 main 與 test 的所有呼叫者（找到 `RoomServiceTest`、`ProductServiceTest`、`DashboardListingControllerAuthorizationIntegrationTest`）並核對 `getCurrentTenant()` 呼叫處基線；Sprint 303 前兩次全量失敗的那兩種原因（漏掉全域守門與 mock 測試）本輪沒有重現 |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **預設並行度（2 個 worker）連跑兩次都失敗**：第一次 278 案例 270 通過／4 跳過／**2 失敗**／2 未執行（7.0 分鐘），第二次 269 通過／4 跳過／**2 失敗**／3 未執行（7.0 分鐘）；兩次失敗的都是 `at-store-less-seller-real`（第一次 `E2E-SLS-03`、第二次 `E2E-SLS-02`）與 `at-store-suspended-payment-real` 的 `E2E-SSP-01`，錯誤一律是**登入 API 回 429 `E-9904`**（`loginViaApi`、`adminAccessToken`，兩個 API 層登入輔助函式對 429 沒有重試）。第二次我保留了後端日誌：**21 筆 `LoginRateLimitFilter` 的 `exceeded rate limit on /v2/auth/login`，全部落在最後約 50 秒（台灣時間 02:39:02～02:39:51），與兩個失敗的時間戳（UTC 18:39:03、18:39:38）吻合**，第一筆出現在整套 7.0 分鐘的最後約一分鐘內（推論：尾端幾支登入最密的規格擠在一起，把每來源 IP 每路徑 30 次／分的桶用完）。後端日誌裡我新加的守門例外在整個守門過程 **0 次被觸發**（E2E 規格都用真實店主）；唯一一筆 `Not authorized to access pricing for this listing` 來自 `at-pricing-rules-real` 的『買家以別店房源 id 查詢得 403』（預期，該案例通過）。**第三次 `workers=1`（雲端 CI 的並行度；用我複製的腳本，見 §7 第 6 點，重用同一份 JAR 與前端）：278 案例，274 通過／4 跳過／0 失敗（9.2 分鐘），閘門顯示『本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）』，後端日誌 0 筆登入限流警告。** 這是既有脆弱點，已登記 `DEF-420`；Sprint 303 的整套 11.0 分鐘（當時機器負載偏高）所以沒撞到 |
| pre-commit、pre-push、雲端 CI | 皆通過，細節與證據見 §10（commit 當下尚未 push，由回填 commit 補上，因為 commit 無法包含自己的 hash） |

## 7. 過程中的失誤與修正

1. **測試設計前提錯誤（§1.3 第 3 列）**：我假設「擁有權檢查擋下的 403」與「權限註解擋下的 403」回應訊息不同，並據此在 E2E 的 `forbidden` 輔助函式裡斷言訊息；綠燈階段 18 個案例全紅（狀態碼其實全是 403，主程式修法有效，錯的是測試）。讀 `GlobalExceptionHandler` 與 `BusinessException.getUserMessage()` 後才確認細節不外洩。改成同角色對照組，多花一輪 E2E。**教訓**：寫「區分兩種 403」的斷言前，先確認回應裡真的有可區分的資訊。
2. **固件跨交易存 `Room`／`Product`**（§2）：第一次紅燈基準有 14 個案例因此是錯誤而不是失敗，白跑一輪。
3. **把中文長行用 heredoc 傳給 python**：又踩到記憶裡早有的坑（`SyntaxError: Non-UTF-8 code`），解析階段就失敗、沒有改到任何檔案，改用 Write 工具落檔再執行。
4. **紅燈基準的邊界**（§2 末段）：每個案例在第一個斷言就中止，修復前沒有執行到對照組與資料庫斷言；這不是失誤，但要誠實寫明，避免被讀成「修復前整個案例的行為都量過」。
5. **E2E 守門預設並行度連兩次失敗**（§6）：第一次失敗後，我讀日誌確認是登入 API 回 429，但那次的後端日誌已被守門刪掉，所以重跑第二次時**同時開了背景迴圈每 6 秒複製後端日誌**（守門結束會刪）；第二次又失敗在同兩支規格，複製下來的日誌量到 21 筆登入限流警告集中在最後約 50 秒、與兩個失敗的時間戳吻合，才確認原因是登入密度；第三次改用 `workers=1`。**我沒有在未修改的 HEAD 上重現這個失敗**，「與本輪改動無關」的判斷依據是失敗訊息、日誌時間吻合與 diff 範圍（不碰登入、限流與 E2E 規格），不是對照實驗。
6. **第三次守門用的是我複製的腳本，不是 `make validate-e2e` 本身**：守門腳本不接受 `--workers` 參數，repo 裡的腳本我沒動。複製版與原腳本只差兩行（固定 `REPO_ROOT`、Playwright 多一個 `--workers` 參數，`diff` 確認），並以 `E2E_GATE_SKIP_BUILD=1` 重用第二次守門剛建好的 JAR 與前端（建置之後我只改過文件）。所以「全綠」是對這個設定而言，預設設定下 `make validate-e2e` 目前會因 `DEF-420` 偶發失敗。

## 8. 限制（誠實揭露）與同型缺口

1. **E2E 跑在 Hibernate 產生的測試庫（`integration-test`），不是 Flyway 建出的資料庫**。`DEF-415` 的守門只依賴呼叫者的租戶與資源的租戶，讀碼推論不涉及兩種資料庫的已知差異（列舉 CHECK 約束、`V5` 種子列）；但我沒有針對它新增 Flyway 全棧（Playwright）規格，所以這一點沒有實測。
2. **讀取端點不動**（§3.3），有 id 的更新／刪除維持先載入再檢查（§3.3）。
3. **同型缺口已登記為 `DEF-419`（未修、未實測）**：`ShippingTemplateController` 的建立／更新／刪除用 `product:create`／`update`／`delete` 守門，`ShippingTemplateService` 以「呼叫者租戶 == 模板租戶」過濾；沒有店鋪的 `ADMIN` 持有這三個權限，所以讀碼推論它能新增、改寫、刪除系統租戶（平台自營）的運費模板，而結帳時 `calculateFeeForTenant(storeId, …)` 以該店鋪的模板算運費。這與 `DEF-415` 同一個原則，但不在使用者點名的五個服務內，我不替使用者決定。
4. **`DEF-417` 仍是讀碼推論**，沒有實測。
5. **`DEF-420`（既有的測試基礎設施脆弱點，本輪登記、不修）**：`make validate-e2e` 在預設並行度（這台是 2 個 worker，約 7 分鐘）下，尾端登入最密的規格會用完登入端點 IP 限流的桶（容量 30、補充 30 次／分），API 層登入輔助函式（`loginViaApi`、`adminAccessToken`）沒有 429 重試而硬失敗。本輪連兩次踩到。建議：輔助函式讀 `Retry-After` 退避重試、admin token 以 worker 為單位快取、再降 `seedStore` 的登入次數；不要調高正式環境的登入限流（Sprint 168 的決定）。這會讓 `make validate-e2e` 在預設設定下重新穩定，值得下一輪處理，但不屬於 `DEF-415`。
6. 使用者的決定涵蓋的是「管理」。依 `RolePermissionMapping` 的註解，`TenantContextFilter`（`DEF-038`）不讓 `ADMIN` 以 `X-Tenant-ID` 切換租戶；這只是讀註解，本輪沒有重審或實測這一層。

## 9. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | **建立端一併納入**（沒有店鋪的 `ADMIN` 也不能建立房源、商品） | 使用者的決定原文是「一次改齊五個服務的擁有權檢查」，建立不是擁有權檢查而是「蓋章」；但「全部擋下、平台自營資料只有 `SUPER_ADMIN` 能管」若漏掉建立，會出現「能建、不能改」的怪狀態（它建的平台自營房源只有 `SUPER_ADMIN` 能清理） | 要求建立端維持原狀 |
| 2 | 不綁房源的定價規則也擋 | 它會蓋成呼叫者的租戶，沒有店鋪者建出來的就是系統租戶的規則；DTO 本來就沒強制至少一個 id | 只擋綁房源的 |
| 3 | 規則讀寫合併成同一個擁有權檢查，錯誤訊息統一 | 兩個檢查在加入這個判斷後完全重複；訊息本來就不外洩給使用者 | 保留兩個方法與兩句文案 |
| 4 | 定價與日曆的 helper、建立端「先判斷再查詢」；房源／商品／SKU 的有 id 更新與刪除維持先載入再檢查 | 見 §3.3 | 全部改成先判斷 |
| 5 | 讀取端點不動 | 使用者的決定針對「管理」；讀取端點只回 `ACTIVE` 的項目（§3.3，沒有核對過是否與公開瀏覽入口完全相同） | 讀取也擋 |
| 6 | `DEF-419` 登記而不修 | 不在點名的五個服務內；權限模型決策 | 一併納入 |
| 7 | `DEF-420` 登記而不修（不在本輪動 E2E 輔助函式） | 它是既有的測試基礎設施脆弱點，與 `DEF-415` 無關；改輔助函式會牽動多支規格，需要自己的驗證 | 本輪一併處理 |

## 10. 雲端 CI 與狀態

commit `a9f74554` 已 push（`2091519c..a9f74554  main -> main`）；`git fetch` 後本機 `HEAD` 與 `origin/main` 同為 `a9f745541450a5f24c08c6b142ded4d16f36b976`，ahead／behind 為 0／0。

- **pre-commit** 一次通過：Checkstyle、Maven compile、核心層快速測試、secret 掃描。
- **pre-push 輕量守門**通過：本機 act 的 Backend Unit Tests（日誌印出兩輪，皆 `Job succeeded`）、Frontend Lint & Build `Job succeeded`；schema 漂移守門「backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移」。
- **雲端 CI** run [40009945926](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/40009945926)：`completed／success`，三個 job 皆 success，約 11 分鐘（19:15:19Z～19:26:18Z）；`gh run view --json status,conclusion,jobs,headSha` 的 `headSha` 與本機 commit 一致。
  - 我下載了「Backend Integration Tests & Package」job 的**完整**雲端日誌（202,250 行）核對：該 job 執行 `mvn verify -DskipUnitTests=true -Dspring.profiles.active=integration-test`，failsafe 彙總 **`Tests run: 915, Failures: 0, Errors: 0, Skipped: 0`**（與本機整合測試 915 一致），`BUILD SUCCESS`，`<<< FAILURE`／`<<< ERROR` 標記 0 個；`PlatformSelfRunWriteIsolationE2ETest` 18／18、`PricingRulePersistenceE2ETest` 3／3、`PricingSystemTenantIsolationE2ETest` 6／6、`DashboardListingControllerAuthorizationIntegrationTest` 4／4（雲端全新 Postgres）。
  - 「Backend Unit Tests」job 的雲端日誌我**沒有**下載，只確認該 job 結論為 success；單元測試 2361 以本機全量為準。
  - 雲端三個 job 不含 Playwright E2E，E2E 以本機 `make validate-e2e` 為準（見 §6，含 `DEF-420` 的並行度問題）。

## 11. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/java/.../core/pricing/PricingService.java` | 兩個擁有權檢查加 `isStoreTenant`；`checkListingReadAccess` 併入 |
| `backend/src/main/java/.../core/room/RoomService.java`、`core/product/ProductService.java` | helper 加 `isStoreTenant`；四個建立方法加 `isSuperAdmin` 與 `checkCanCreateListing` |
| `backend/src/main/java/.../core/product/ProductSkuService.java`、`core/booking/RoomCalendarService.java` | helper 加 `isStoreTenant`（日曆先判斷再查詢） |
| `backend/src/main/java/.../api/controller/RoomController.java`、`ProductController.java`、`DashboardListingController.java` | 建立端點傳入 `isSuperAdmin` |
| `backend/src/test/java/.../api/controller/PlatformSelfRunWriteIsolationE2ETest.java` | 新增（18 個真實 PostgreSQL 案例） |
| `backend/src/test/java/.../core/pricing/PricingServiceTest.java`、`core/room/RoomServiceTest.java`、`core/product/ProductServiceTest.java`、`core/product/ProductSkuServiceTest.java`、`core/booking/RoomCalendarServiceTest.java` | 新增共 28 個單元案例；`RoomServiceTest`／`ProductServiceTest` 既有建立呼叫補參數 |
| `backend/src/test/java/.../integration/DashboardListingControllerAuthorizationIntegrationTest.java` | 兩個 mock 補 `anyBoolean()` |
| `backend/src/test/java/.../shared/tenant/TenantContextCallSiteDriftTest.java` | `PricingService` 基線 5→4 與註解 |
| `docs/02_architecture/api/API_M01_Product_Center.md`、`API_M02_Listing_Center.md`、`API_M12_Dynamic_Pricing.md`、`API_Listing_Unified_Create.md` | v2.1／v2.1／v2.3／v2.1：寫入端點要求呼叫者有真正的店鋪、權限矩陣、錯誤碼、已知限制；`API_M02` 另更正維護端點 `403` 的回應訊息描述（回應一律是「權限不足」，英文句子只在伺服器日誌） |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | `DEF-415` 結案；`DEF-411` 一句話更正；`DEF-418` 更正與使用者回答；新增 `DEF-419`、`DEF-420`；頁尾最後更新 |
| `docs/04_planning/RELEASE_TRACKER.md` | Sprint 304 row |
| `docs/04_planning/SPRINT_304_PLAN.md` | 新增（本文件） |

## 12. 規劃中發現、與本輪順帶更正的 `DEF-418` 事實

Sprint 303 總結把「`V1`／`V5`／`V7`／`V15`／`V16` 都把帶已知密碼的帳號放進每個 Flyway 資料庫」當成事實，其中只有 `admin@nextkey.local` 實測過。本輪用 Spring Security 6.2.4 的 `BCryptPasswordEncoder` 離線核對各遷移的雜湊（對 5 個候選密碼：`admin123`、`Test123!`、`test123`、`password`、`SecurePass123!`）：

| 雜湊 | 結果 |
|---|---|
| `V7` 覆蓋後的 `admin@nextkey.local` | **符合 `Test123!`**（與 E2E gate 登入成功一致） |
| `V1` 原始 admin；`V7` 的 `pending-owner`／`active-owner`／`buyer-test`（同一個雜湊） | **全部不符**（SQL 註解寫的 `admin123` 不成立） |
| `V16` `m15-api-owner`（註解稱 `test123`）、`V5` `test-store-owner` | **全部不符** |
| `V15` `m15-owner` | `$2a$10$dummy_hash_for_testing`，**不是合法 bcrypt**，不可能登入 |

所以**可證實為已知密碼的只有 `admin@nextkey.local`／`Test123!`**；其他帳號是 SQL 註解的宣稱（我只測了 5 個候選，不能保證它們不可被破解）。`DEF-418` 仍要處理（含已知密碼的 `SUPER_ADMIN` 在每個 Flyway 資料庫，包含日後的正式環境），但嚴重度描述要更正，追蹤表已同步。使用者的兩個回答（沒有正式或測試環境、選做法 A）不受影響；做法 A 的設計重點記在追蹤表（`E2E gate` 依賴 admin 的已知密碼、指向 `users`／`tenants` 的外鍵有數十個、舊 dev 資料庫可能有活動紀錄）。
