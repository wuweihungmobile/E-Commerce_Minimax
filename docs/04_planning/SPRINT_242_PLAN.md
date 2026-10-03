# Sprint 242 Plan — 停權店鋪的未付款單不能再付款＋系統租戶孤兒內容下架（使用者授權「依最佳狀態決策」；DEF-333）

**Sprint**: Sprint 242
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

使用者貼回 Sprint 239～241 的總結，在「需要你決定」那一節寫：「以下請依照最佳且理想化幫我進行決策，不用考量成本！評估最理想狀態！」。依記憶 `inline-decision-replies-mean-proceed`，這是**明確授權我選最佳做法並交代理由、照做**。兩件待決事項：

| 待決 | 來源 | 我原本的建議 | 本輪決策（最理想狀態） |
|------|------|--------------|------------------------|
| 停權店鋪停權前已成立、尚未付款的訂單／訂房，是否也擋付款 | [SPRINT_239_PLAN.md](SPRINT_239_PLAN.md) §6.1 第 1 點 | 擋 | **擋，而且擋在所有「會收錢」的入口**（見 §1.2），並讓付款狀態回應帶 `storeOpen`、前端顯示「店鋪暫停營業」而不是讓使用者按了才失敗 |
| `DEF-333`：系統租戶下既有的商品／房源／貼文（擁有者不能再編輯） | [SPRINT_240_PLAN.md](SPRINT_240_PLAN.md) §6.2 | 部署前先跑預覽 `SELECT`，有資料由管理員下架（不刪除） | **做成 V90 遷移**：自動下架（不刪除）並逐筆寫稽核紀錄以便還原；預覽 `SELECT` 附在遷移檔頭，部署前仍可先看筆數 |

Sprint 234 的限流單位與 V87／V88 的資料遷移，使用者沒有異議，照現狀進行（不需動作）。

**排程調整**：使用者原先排的 Sprint 242（M05／M06 API 規格）會描述付款端點，所以先落實本輪的付款守門，文件才不必寫完又改。**M05／M06 API 規格順延為 Sprint 243，SRD／FRD／PRD／TC／環境變數順延為 Sprint 244。**

### 1.2 決策一的設計：付款守門（讀碼）

**所有會收錢的入口**（逐一讀 `OrderPaymentController`、`BookingPaymentController`、`PaymentController`、`PaymentStateService`、`PaymentService` 確認）：

| 入口 | 位置 | 是否擋 |
|------|------|--------|
| 訂單 Mock 付款 `POST /v2/orders/{id}/pay` | `PaymentStateService.mockPaymentSuccess` | ✅ 擋 |
| 訂單 Stripe Checkout 發起 `POST /v2/orders/{id}/pay/checkout` | `initiateStripeCheckout` | ✅ 擋（**在呼叫 Stripe 之前**，不替停權店鋪建 session） |
| 訂房 Mock 付款 `POST /v2/bookings/{id}/pay` | `mockBookingPaymentSuccess` | ✅ 擋 |
| 訂房 Stripe Checkout 發起 `POST /v2/bookings/{id}/pay/checkout` | `initiateStripeBookingCheckout` | ✅ 擋 |
| 舊版 `POST /v2/payments`（訂單與訂房各一） | `PaymentService.processOrderPayment`／`processBookingPayment` | ✅ 擋（只擋新端點，買家改打舊端點就繞過了；Sprint 216 就是同型的「第二條路徑」） |
| Stripe 回跳確認、webhook（`markStripePaymentSucceeded`） | 共用核心 | ❌ **刻意不擋**：錢已經在 Stripe 那端收了，擋掉就是收了錢卻不記帳（Sprint 226 的 DEF-308 就是這個教訓）。停權前已建立的 Checkout Session 若在停權後才付完，仍入帳為已付款，取消與退款路徑照常可用 |
| 取消、退款 | — | ❌ 不擋：停權店鋪的消費者仍要能取消與退款（這正是「提示消費者取消」的前提） |
| Mock 付款失敗 `POST …/pay/fail` | `mockPaymentFailure` | ❌ 不擋：不收錢 |

| 面向 | 做法 |
|------|------|
| 判斷 | 沿用 Sprint 239 的 `StoreCheckoutGuard`（只有 `ACTIVE` 算營業，其餘四個狀態與「租戶不存在」一律拒絕）；新增 Spring 元件 `PaymentStoreGuard`（`requireStoreOpen`／`isStoreOpen`）取訂單／訂房自己的租戶（Sprint 236～237 起是商品／房源所屬店鋪）查店鋪。**租戶 ID 優先讀 `tenant` 關聯、不讀 `tenantId` 影子欄位**（`PaymentStoreGuard.storeIdOf`，見 §1.4） |
| 錯誤碼 | 沿用 `E-2010`（422）。訊息由「此店鋪目前暫停營業，無法下單」改為「…無法下單或付款」（`ErrorCode`、`API_Error_Codes.md`、前端三處對照同步） |
| **錯誤先後不變** | 守門放在**既有檢查之後**、任何寫入或呼叫金流之前（Sprint 239 的教訓：放到前面會改變既有錯誤的先後）：已取消的單回 `E-5011`、已付款的回 `E-6003`，不會先被說成「店鋪停業」；店鋪不營業才回 `E-2010` |
| `storeOpen` | `OrderPaymentStateDto` 新增 `storeOpen`（訂單與訂房的付款狀態都帶）。**`canPay` 的語意不變**（「訂單狀態允許付款」），店鋪狀態由 `storeOpen` 單獨表達；舊版沒有此欄位的回應前端視為營業中 |
| 前端 | 訂單詳情頁與訂房付款卡：`canPay && storeOpen === false` 時**不顯示付款按鈕**，改顯示「店鋪暫停營業，暫時無法付款。您可以取消這筆訂單；店鋪恢復營業後這裡會再出現付款按鈕」（`order-store-closed`／`booking-store-closed`）；結帳頁與服務層的 `E-2010` 對照文字改為涵蓋付款，並提示「若訂單已建立，可到『我的訂單』取消」 |

### 1.3 決策二的設計：V90 遷移（讀碼＋唯讀查測試資料庫結構）

**為什麼做成遷移，而不是只給一段 `SELECT` 讓人手動跑**：Sprint 240 登記時我沒有查正式環境，筆數未知。手動步驟會被忘記（本專案有「從未啟用排程」「文件沉默漂移」的前科）；而這批資料的現況比「管理員管不到」更糟——買家在這些商品上下單，訂單蓋成系統租戶（Sprint 237 起訂單歸屬商品所屬店鋪），**沒有任何店家能出貨或處理**。遷移與 V87～V89 同一慣例（使用者已對 V87／V88 表示同意「部署前先跑預覽 `SELECT`」），可重複執行、附預覽。

**判定條件比 Sprint 240 計畫書的預覽 `SELECT` 更廣，是讀碼後的修正**：預覽用 `owner.role IN ('SELLER','HOST')`，但擁有權檢查比對的是**租戶**（`checkListingTenantOwnership`：`listing.tenantId == 呼叫者租戶`），不是建立者。所以「SELLER 先在系統租戶建了資料、之後才開店升成 `STORE_OWNER`」留下的資料同樣是孤兒（他的租戶換成店鋪了），預覽會漏掉。不變量改成：**系統租戶底下對外公開的內容，建立者必須是平台管理員（`ADMIN`、`SUPER_ADMIN`）**。

| 資料 | 條件 | 處理 |
|------|------|------|
| 商品／房源（`listings`） | 系統租戶、`ACTIVE`、擁有者角色不是 `ADMIN`／`SUPER_ADMIN` | → `INACTIVE`（店鋪停權時 `AdminService.deactivateTenantListings` 也是這個做法） |
| 貼文（`posts`） | 系統租戶、`PUBLISHED`、作者角色不是平台管理員 | → `DRAFT`（`Post.unpublish()` 的語意） |
| CMS 頁面（`cms_pages`） | 系統租戶、`PUBLISHED`、作者角色不是平台管理員（`author_id` 為 NULL 的平台自建頁面不動） | → `DRAFT` |

草稿、已下架、已歸檔、已刪除的不動（不復活、不改變）；店鋪租戶底下的不動；平台管理員建立的不動。**橫幅（`cms_banners`）沒有建立者欄位，無法判斷是不是孤兒，不處理。**

**可還原**：每筆被下架的資料寫一列 `audit_log`（`action = 'DEF333_UNPUBLISHED_ORPHAN'`、`entity_type` 為 `LISTING`／`POST`／`CMS_PAGE`、`old_value`／`new_value` 是前後狀態），要還原就依這些列把狀態改回 `old_value`。遷移以 `RAISE NOTICE` 輸出三種資料的筆數。

### 1.4 全量驗證抓到的問題：`tenantId` 影子欄位在同一個持久化脈絡裡是 `null`（我自己的缺陷，已修）

第一輪全量 `mvn -o clean verify` 有 **4 個失敗**：`M07PaymentMockIntegrationTest`（`testProcessPayment`／`testMockPaySuccess`／`testMockRefund`／`testPaymentStateTransitions`）預期 200 實得 **422**。原因：我的守門讀 `order.getTenantId()`，而 `Order.tenantId` 是 `insertable = false, updatable = false` 的唯讀影子欄位——只有「從資料庫載入」才有值，**同一個持久化脈絡裡剛用 `.tenant(...)` 建立的實體它是 `null`**（記憶 `erp-tenant-test-seeding-gotcha` 早已記載這個陷阱，我寫守門時沒套用）。那個測試是 `@Transactional`，訂單在同一個交易裡建立後被付款端點讀到，`tenantId == null` 被守門當成「租戶不存在」而擋下。

- **正式環境不會遇到**（付款是另一個 HTTP 請求，從資料庫載入），但守門不該依賴一個「只在某些時刻有值」的欄位，所以修的是**守門**不是測試固件：新增 `PaymentStoreGuard.storeIdOf(Order|Booking)`，優先讀 `tenant` 關聯（真正對應資料庫欄位；取關聯的 id 不觸發載入），沒有關聯才退回影子欄位；6 處呼叫（含 `storeOpen`）與舊版 `PaymentService` 2 處都改用它。
- **為什麼我先前只跑付款相關的單元與整合測試沒發現**：`M07PaymentMockIntegrationTest` 是 `*IntegrationTest`，`mvn test` 排除、而我只對自己的測試類別跑了 `-Dtest=`。**改共用付款服務一定要跑全量 `verify`**——這與 Sprint 239 補五個測試固件是同型的教訓。
- 新增單元測試（`storeIdOf` 的四種組合、服務層「只有關聯的訂單」接線）；2 個突變（`storeIdOf` 改回只讀影子欄位：訂單 6 個轉紅、訂房 1 個轉紅）全被抓到。

### 1.5 真實全棧 E2E 抓到的兩件事（我自己的規格問題，已修）

1. **我的規格撞上登入端點的 IP 限流，連帶讓不相干的既有規格失敗**：第一次守門 2 個失敗——SSP-02 與既有的 `at-system-tenant-isolation-real` 的 STI-01，都是「登入後拿不到 accessToken」。原因：每次停權／恢復都用 `setStoreStatus`（清掉登入、以管理員重新登入），接著又把買家重新登入，全規格約 **22 次登入**；登入端點每個來源 IP、每個路徑每分鐘 30 次，整個套件共用同一個 IP（記憶 `e2e-gate-flake-rate-limiters`）。修法：`helpers/store.ts` 新增 `adminAccessToken`（API 登入一次、不動瀏覽器狀態）與 `setStoreStatusWithToken`，買家改在 `beforeAll` 登入一次、各案例還原 localStorage（`restoreSession`），規格的登入次數降到約 **7 次**。之後的完整守門 STI-01 恢復通過。**誠實揭露**：中間一輪突變驗證的守門（見 §4）又出現一次 STI-04 同型失敗（不是我的修改造成，登入限流本來就在邊緣：套件基準約每分鐘 38 次登入），重跑就過；這是既有的 flake 類別，不是新缺陷。
2. **舊版 `POST /v2/payments` 的訂房分支從 HTTP 根本打不到**：SSP-03 的探針（只帶 `bookingId`）得 **400「Order ID or Booking ID is required」**——`PaymentDto.PaymentRequest.orderId` 是 `@NotNull`（Sprint 221 `DEF-303` (1) 就記載「訂房其實根本付不了款」）。我先前假設兩條舊路徑都能從 HTTP 探，是沒查驗證註解的錯。守門仍保留在該分支（死碼上的縱深防禦，成本是一行），由 `PaymentServiceStoreGuardTest`／`StoreSuspendedPaymentIntegrationTest` 在服務層覆蓋；E2E 不對它做 HTTP 探針（規格內註明原因）。

## 2. 使用者決策與需要使用者知悉的行為變更

使用者授權我依最佳狀態決策（見 §1.1）。行為變更：

1. **停權店鋪的未付款訂單／訂房不能再付款**（422 `E-2010`）；訂單詳情頁與訂房頁顯示「店鋪暫停營業」並收起付款按鈕；仍可取消。店鋪恢復營業後又能付款。
2. **V90 會在部署時自動下架系統租戶底下建立者不是平台管理員的上架商品／房源與已發布的貼文、CMS 頁面**（不刪除、有稽核紀錄）。我沒有查正式環境，筆數未知；**部署前請先跑 [V90 檔頭](../../backend/src/main/resources/db/migration/V90__Unpublish_Orphaned_System_Tenant_Content.sql) 的三個預覽 `SELECT` 看筆數**（唯讀）。
3. `E-2010` 的預設訊息改為「此店鋪目前暫停營業，無法下單或付款」。

## 3. 實作內容

**程式碼**
- `PaymentStoreGuard`（新，`core/payment`）；`PaymentStateService`（四個付款入口＋兩個狀態組裝帶 `storeOpen`）、`PaymentService`（舊版兩個入口）、`OrderPaymentStateDto.storeOpen`、`ErrorCode.E_2010` 訊息。
- `V90__Unpublish_Orphaned_System_Tenant_Content.sql`（新）。
- 前端：`services/payment.ts`（`storeOpen` 型別）、`orders/[id]/page.tsx`、`BookingPaymentCard.tsx`、三處 `E-2010` 錯誤對照。
- **既有測試**：`PaymentStateService` 建構子新增一個參數，4 個手動建構的測試類別補上、2 個 `@InjectMocks` 的測試類別補 `@Mock PaymentStoreGuard`。

**文件**：`API_Error_Codes.md`（`E-2010` 訊息）；追蹤文件。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | `PaymentStoreGuardTest`（8 次執行）：ACTIVE 放行，其餘四個狀態逐一拒絕、租戶不存在與 `null` 視為不營業（`null` 不查資料庫）、`storeIdOf` 優先讀關聯。`PaymentStateServiceStoreGuardTest`（14）：四個入口守門拒絕時**沒有任何副作用**（沒搶占狀態、沒寫付款、沒呼叫 Stripe、沒稽核）且用的是訂單／訂房自己的租戶；營業時照常成功；**既有錯誤先後不變**（已取消回 `E-5011`、已付款回 `E-6003`，守門不被諮詢）；**刻意不擋**的入口即使守門會拒絕也照常運作（Stripe 入帳、Mock 付款失敗）；`storeOpen` 反映守門判斷。`PaymentServiceStoreGuardTest`（4）：舊版 `/v2/payments` 的訂單與訂房兩條路徑同樣被擋，錯誤先後不變 |
| 真實 PostgreSQL＋Redis 整合（+22） | `StoreSuspendedPaymentIntegrationTest`（19）：訂單與訂房走**真實結帳**（訂單蓋的是商品所屬店鋪，前提有斷言）；四個非 ACTIVE 狀態逐一拒絕；Stripe 完全沒被呼叫；舊版端點同樣被擋；停權後仍可取消、`storeOpen=false` 且 `canPay` 不變；恢復營業後又能付款；已取消的單回 `E-5011`；**停權前建立的 Stripe session 在停權後付完仍入帳**（訂單與訂房）。`UnpublishOrphanedSystemTenantContentMigrationIntegrationTest`（3）：V90 的資料邏輯（見下） |
| 真實後端 Playwright（+3） | `at-store-suspended-payment-real.spec.ts`：**SSP-01** 訂單營業時有付款按鈕→管理員真的停權→付款卡換成「店鋪暫停營業」、Mock 付款與舊版端點都 422 `E-2010`、訂單仍待付款且沒有付款紀錄→恢復營業後同一張單付款成功；**SSP-02** 停權後在畫面上取消訂單；**SSP-03** 訂房同上（付款 422、畫面收起付款按鈕、恢復營業後付款成功），並驗證停權後可取消另一筆訂房（舊版端點的訂房分支 HTTP 打不到，見 §1.5） |

V90 遷移測試涵蓋：系統租戶底下 `SELLER`／`HOST`／開店後的 `STORE_OWNER`／`BUYER` 建立的上架商品與房源下架；`ADMIN`／`SUPER_ADMIN` 建立的、草稿、已下架、已刪除、店鋪租戶底下（含 `SELLER` 角色但有真實店鋪者）的不動；貼文與 CMS 頁面同理，歸檔的不動、`author_id` 為 NULL 的平台頁面不動；稽核列的筆數、`entity_type`、前後值、租戶；可重複執行（第二次不再改狀態、不再寫稽核）。

### 突變驗證（Rule 9：測試必須在守門被拿掉或改錯時失敗）

**付款守門（15 個，跑 `PaymentStoreGuardTest`＋`PaymentStateServiceStoreGuardTest`＋`PaymentServiceStoreGuardTest`＋`StoreSuspendedPaymentIntegrationTest`，43 個測試）——全部被抓到**

| # | 突變 | 結果（失敗＋錯誤） |
|---|------|------|
| MP1 | 拿掉訂單 Mock 付款的守門 | 7 個轉紅 |
| MP2 | 拿掉訂單 Stripe Checkout 的守門 | 2 |
| MP3 | 拿掉訂房 Mock 付款的守門 | 5 |
| MP4 | 拿掉訂房 Stripe Checkout 的守門 | 2 |
| MP5 | 拿掉舊版端點（訂單）的守門 | 2 |
| MP6 | 拿掉舊版端點（訂房）的守門 | 2 |
| MP15 | **用呼叫者（消費者＝系統租戶，一定營業）的租戶而不是訂單的店鋪**——固件繞過型突變 | 7（單元驗參數、整合用真實消費者） |
| MP7 | 守門移到「是否可付款」檢查之前（改變錯誤先後） | 3 |
| MP8 | Stripe 的守門移到呼叫 Stripe 之後（session 已建立才擋） | 2 |
| MP9 | **過度擋**：Stripe 入帳也擋（收了錢卻不記帳） | 2 |
| MP10 | **過度擋**：Mock 付款失敗也擋 | 1 |
| MP11 | 訂單付款狀態不帶 `storeOpen` | 2 |
| MP12 | 訂房付款狀態不帶 `storeOpen` | 2 |
| MP13 | 守門把「租戶不存在」當成放行 | 2 |
| MP14 | `isStoreOpen` 一律回 `true` | 8 |
| MX1 | `storeIdOf(Order)` 改回只讀 `tenantId` 影子欄位（§1.4；加跑 `M07PaymentMockIntegrationTest`，53 個測試） | 6 |
| MX2 | `storeIdOf(Booking)` 改回只讀影子欄位 | 1 |

**V90 遷移（15 個有效突變，跑 `UnpublishOrphanedSystemTenantContentMigrationIntegrationTest`，3 個測試）——全部被抓到**

| # | 突變 | 結果 |
|---|------|------|
| MV1 | 商品／房源拿掉角色條件（連平台管理員建立的也下架） | 轉紅 |
| MV2 | 角色條件改成只認 `SELLER`／`HOST`（漏掉開店後的 `STORE_OWNER`、`BUYER`——**Sprint 240 預覽 `SELECT` 的原條件**） | 轉紅 |
| MV3 | 商品／房源拿掉系統租戶條件（店鋪租戶底下的也下架） | 轉紅 |
| MV4 | 商品／房源拿掉 `ACTIVE` 條件（草稿、已刪除的也被改成 `INACTIVE`） | 2 個轉紅（含可重複執行） |
| MV5 | 商品／房源改成 `DELETED`（刪除而不是下架） | 2 |
| MV6 | 貼文改成 `ARCHIVED` 而不是 `DRAFT` | 2 |
| MV7 | 貼文拿掉 `PUBLISHED` 條件（歸檔的被改成草稿） | 2 |
| MV8 | 貼文拿掉系統租戶條件 | 轉紅 |
| MV9 | CMS 頁面拿掉系統租戶條件 | 轉紅 |
| MV10 | CMS 頁面連沒有建立者（`author_id` 為 NULL）的平台頁面也下架 | 轉紅 |
| MV11 | 商品／房源稽核列的前後值對調（還原依據錯） | 轉紅 |
| MV12 | 貼文稽核列的 `entity_type` 寫錯 | 轉紅 |
| MV13b | CMS 頁面稽核列的 `entity_type` 寫錯 | 轉紅 |
| MV14 | 商品／房源稽核列的 `tenant_id` 寫成 NULL | 轉紅 |
| MV15 | 貼文稽核列的 `action` 名稱寫錯 | 2 |

（我原先的 MV13「拿掉 CMS 頁面的稽核 `INSERT`」是語法錯誤的無效突變——3 個測試都是 SQL 錯誤而紅，不算數；改用 MV13b 與 MV14、MV15 三個語意有效的突變取代。）

**前端畫面（2 個，改完重建前端、跑完整真實全棧守門）——全部被抓到**

| # | 突變 | 結果 |
|---|------|------|
| MF1 | 訂單詳情頁無視 `storeOpen`（「店鋪暫停營業」卡永不顯示、付款卡不看 `storeOpen`） | SSP-01 轉紅（serial 模式下 SSP-02／03 因此「did not run」） |
| MF2 | 訂房付款卡無視 `storeOpen` | SSP-03 轉紅 |

（第一次嘗試的突變 `false && …` 讓 TypeScript 的 null 收斂失效、建置失敗，沒有產生結果，已改成型別安全的 `Date.now() < 0`；前端檔案已還原並以 `git diff` 確認只剩預期的改動。）

每次突變都先備份源碼、等 class 比源碼新才跑（IDE 的 Java 語言服務有在跑，會搶先編譯）、跑完還原並以 `cmp`／`sha256` 確認逐位元組一致。

## 5. 驗證結果

- **後端全量**：`mvn -o clean verify` **BUILD SUCCESS（17 分 04 秒）**——單元 **2102**（+26：`PaymentStoreGuardTest` 8、`PaymentStateServiceStoreGuardTest` 14、`PaymentServiceStoreGuardTest` 4）／整合 **768**（+22：`StoreSuspendedPaymentIntegrationTest` 19、`UnpublishOrphanedSystemTenantContentMigrationIntegrationTest` 3）／**0 失敗**／0 略過。**第一輪全量（14 分 26 秒）有 4 個失敗**（`M07PaymentMockIntegrationTest`，見 §1.4），修守門後重跑全綠。
- **`make validate-schema-doc`**：對乾淨 PostgreSQL 套用 **90** 個 Flyway 遷移（含 V90）通過；SRD 資料庫文件與實際 schema 一致（V90 只動資料、不動 schema）。
- **前端**：`tsc --noEmit` 無錯、ESLint 只有既有的 `payment.ts` 匿名預設匯出警告（非本輪）、`next build` 成功（以還原後的程式碼重建）。
- **真實後端 E2E**：`E2E_GATE_SKIP_BUILD=1 make validate-e2e`（JAR 是上面 verify 建好的最終版，前端手動重建）**142 個測試：138 通過／4 略過／0 失敗（4.1 分鐘）**，後端以 `ddl-auto=validate` 啟動確認 entity 與 Flyway schema 對齊；新增的 SSP-01／02／03 全綠。那 4 個略過是既有基準。（過程中跑了 6 次守門：第 1 次 2 失敗與第 2 次 1 失敗見 §1.5，第 3 次全綠，第 4～5 次是前端突變驗證，第 6 次是還原後的最終全綠。）
- **突變驗證**：見 §4（付款守門 15＋`storeIdOf` 2＋V90 15＋前端 2＝**34 個有效突變全部被抓到**；另有 1 個語法錯誤的無效 V90 突變與 1 個建置失敗的無效前端突變，已誠實標示不算數）。
- **push 與雲端 CI**：（待回填）

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **停權前已建立的 Stripe Checkout Session 在停權後付完，仍會入帳為已付款**（刻意，見 §1.2）。Stripe Checkout Session 預設 24 小時過期，這個窗口最長 24 小時；入帳後訂單／訂房的取消與退款路徑照常可用。若要連這個窗口也關掉，需要在停權時主動讓 Stripe session 過期（要呼叫 Stripe，真實路徑從未驗證），我沒做。
2. **V90 的判定是「建立者角色」**：系統租戶底下由 `BUYER`／`SELLER`／`HOST`／`STORE_OWNER`／`STORE_STAFF`／`CFO` 建立的上架內容都會被下架。橫幅（`cms_banners`）、運費模板、定價規則、ERP 資料沒有可判斷的建立者或不對買家公開，**不處理**；運費模板與定價規則只對系統租戶底下的商品生效，商品下架後也就不再被用到。
3. **管理員停權店鋪會把店鋪所有上架中的商品／房源自動下架（既有行為），恢復營業不會自動重新上架**——見 `DEF-337`。本輪的 Playwright 因此把所有訂單與訂房在店鋪營業時一次建好。
4. **已簽發的 access token 15 分鐘窗口**等 Sprint 240 的限制不變。
5. **前端沒有元件層測試**（承 [SPRINT_238_PLAN.md](SPRINT_238_PLAN.md) §4）；新增的兩個畫面由真實後端 Playwright 覆蓋。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| 本輪新增 `DEF-337` | 停權店鋪→恢復營業，商品／房源不會自動重新上架（店主得逐一手動上架）。無法區分「停權自動下架」與「店主自己下架」 | 讓停權時記錄被自動下架的項目（比照 V90 的稽核列），恢復營業時只還原這些；是產品行為決定，我沒做 |
| `DEF-333` | ✅ 本輪以 V90 處理；**部署前請先跑 V90 檔頭的預覽 `SELECT` 看筆數** | — |
| Sprint 239 §6.1 第 1 點 | ✅ 本輪已擋 | — |
| Sprint 234 §6.4／Sprint 235～237 §2 | 使用者沒有異議，照現狀 | — |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 243**：M05 訂單與 M06 訂房的 API 規格依實作改寫（含 `OrderPaymentController` 7 個端點、`BookingPaymentController` 3 個、舊版 `PaymentController`、`CheckoutController`、`GET /v2/dashboard/bookings`、`PATCH /v2/orders/{id}/status`、`Idempotency-Key`、Sprint 235～242 的契約變更：`storeId`、`E-2010`（含付款）、`E-5020`、`storeOpen`、訂單與訂房歸屬店鋪），並把它們納入 `ApiRouteDocDriftTest`。
3. **Sprint 244**：SRD／FRD／PRD／TC／環境變數文件（見 [SPRINT_241_PLAN.md](SPRINT_241_PLAN.md) §1.1）。
4. 之後：店鋪成員管理前端（`DEF-321` (a)）→ CMS 卡片連結（`DEF-321` (c)）→ 通知事件（`DEF-318`）→ 店鋪前台 `/stores`（`DEF-321` (b)）。
