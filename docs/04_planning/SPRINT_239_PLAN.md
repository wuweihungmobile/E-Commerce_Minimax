# Sprint 239 Plan — 結帳擋掉非 ACTIVE 的店鋪（使用者拍板；DEF-319 的已知限制收尾）

**Sprint**: Sprint 239
**日期**: 2026-10-03

## 1. 缺口盤點結果

### 1.1 起點與排程（順序為我的安排，屬推論）

使用者在 Sprint 238 結尾以互動選擇拍板三件事，全選建議項：(1) `V89` 照現狀回填歷史訂單；(2) **結帳擋掉非 ACTIVE 的店鋪**（新錯誤碼，購物車標示該店鋪暫停營業）；(3) `DEF-326` 未歸屬店鋪的 SELLER／HOST 不得寫入店家層資料。(1) 不需要改程式；本輪做 (2)，(3) 排在 Sprint 240。決定記錄見 [SPRINT_237_PLAN.md](SPRINT_237_PLAN.md) §6.1 第 2 點（原本登記為「結帳不檢查店鋪本身的狀態」的已知限制）。

### 1.2 為什麼現在才相關（讀碼）

Sprint 236～237 之前，訂單與訂房蓋成「下單者的租戶」。沒有店鋪的消費者的租戶是系統租戶佔位值，**一定是 ACTIVE**，所以店鋪的狀態從來不在結帳路徑上。訂單改蓋商品所屬店鋪之後，結帳會把款項歸給那家店鋪——若店鋪已被停權或終止，消費者仍能下單並付款給它。`Tenant.TenantStatus` 有五個值：`PENDING_REVIEW`、`ACTIVE`、`REJECTED`、`SUSPENDED`、`TERMINATED`。

### 1.3 設計

| 面向 | 做法 |
|------|------|
| 判斷 | 單一元件 `StoreCheckoutGuard`（`isOpen`／`requireOpen`）：**只有 `ACTIVE` 能下單**，其餘四個狀態一律拒絕（用列舉逐一測，之後新增狀態預設不能下單，要明確放行才行） |
| 錯誤碼 | 新增 `E-2010`「此店鋪目前暫停營業，無法下單」，HTTP **422**（比照 `E-3002`「商品未上架」同屬業務規則違反）。**新增錯誤碼同步四處**：`ErrorCode`、`GlobalExceptionHandler` 狀態對應、`API_Error_Codes.md`、`GlobalExceptionHandlerTest` 期望表 |
| 擋的位置 | 三個「核心」方法，所有建立路徑都經過它們：`OrderService.buildProductOrder`（單一類型結帳與合併結帳共用；放在最前面，店鋪暫停營業比商品、庫存細節更根本）、`BookingService.buildBookingCore`（單獨訂房與合併結帳共用；刻意放在既有檢查——日曆鎖、可用性、使用者、店鋪存在——之後，不改變這些既有錯誤的先後順序，日曆鎖在 `finally` 釋放）、舊的 `OrderService.createRoomOrder`（前端零呼叫點，一併處理避免日後接上又重演） |
| **只擋「建立」** | 既有訂單與訂房的付款、取消、退款**不受影響**——店鋪停權後，消費者仍要能取消與退款（整合測試驗證：訂單成立後店鋪才停權，消費者仍能取消） |
| 購物車 | `CartItemResponse` 與 `StoreCartSummary` 新增 `storeActive`（`getCart` 本來就批次查店鋪名稱，改成查店鋪實體，順便帶出狀態，沒有多一次查詢）。**加入購物車不擋**（使用者拍板的範圍是結帳） |
| 前端 | 購物車頁：`storeActive === false` 的店鋪區塊顯示「此店鋪目前暫停營業，無法下單。請移除這家店鋪的商品，或等店鋪恢復營業」並**停用該店鋪的「前往結帳」**（單一店鋪的畫面同樣）；沒有 `storeActive` 欄位的舊版回應視為營業中。商品／合併／房間結帳頁與服務層的錯誤對照補 `E-2010` |

## 2. 使用者決策與需要使用者知悉的行為變更

本輪實作的就是使用者在 Sprint 238 結尾拍板的第 (2) 項，沒有新的決策。行為變更：

1. **非 ACTIVE 店鋪（待審核、已駁回、停權、終止）的商品與房源不能再被下單或訂房**，回 `E-2010`（422）。
2. **購物車標示「暫停營業」並停用結帳**；使用者可以移除該店鋪的項目，或等店鋪恢復。
3. **已經成立的訂單不受影響**，包含付款（見 §6.1 第 1 點）、取消與退款。

## 3. 實作內容

- `StoreCheckoutGuard`（新，`core/tenant`）、`ErrorCode.E_2010` 與四處同步。
- `OrderService.buildProductOrder`／`createRoomOrder`、`BookingService.buildBookingCore` 加守門。
- `CartDto`（`CartItemResponse.storeActive`、`StoreCartSummary.storeActive`）、`RedisCartService`（`loadStores` 取代 `loadStoreNames`）。
- 前端：`cart/page.tsx`（標示與停用）、`services/booking.ts`／`services/checkout.ts`／`checkout/product/page.tsx` 的錯誤對照；`e2e/helpers/store.ts` 新增 `setStoreStatus`（管理員 `PUT /v2/admin/tenants/{id}/status`）。
- **既有單元測試的固件**：`Tenant.builder().build()` 的預設狀態是 `PENDING_REVIEW`，所以五個測試檔（`OrderServiceTest`、`OrderPromoCodeTest`、`OrderStoreCheckoutTest`、`BookingPromoCodeTest`、`BookingServiceCreateBookingTest`）的店鋪固件補上 `ACTIVE`（真實資料庫的整合測試原本就設 `ACTIVE`，不受影響）。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元 | `StoreCheckoutGuardTest`（3 個方法、6 次執行）：ACTIVE 可下單、其餘四個狀態逐一拒絕（`E-2010`）、`null` 視為不能下單。`GlobalExceptionHandlerTest`（`ErrorCode` 全值參數化，+1 列）：`E-2010` 對應 422。`OrderStoreCheckoutTest`（+1）：停權店鋪→`E-2010`、沒有訂單、購物車不動；同一輪營業中的另一家店鋪照常。`OrderServiceTest`（+1）：舊的 ROOM 訂單路徑也擋。`BookingServiceCreateBookingTest`（+1）：停權店鋪的房源→`E-2010`、不建立訂房、日曆鎖釋放。`RedisCartServiceTest`（+1）：購物車依店鋪帶出 `storeActive`（營業中 true、停權 false，摘要與項目一致） |
| 真實 PostgreSQL＋Redis 整合（+3） | `StoreScopedCheckoutIntegrationTest`：停權後購物車標示 `storeActive=false`、結帳 `E-2010`、沒有訂單、購物車原封不動、另一家營業中的店鋪照常結帳；合併結帳與單獨訂房都 `E-2010`、不留下任何訂單或訂房；**訂單成立後店鋪才停權，消費者仍能取消** |
| 真實後端 Playwright（+1） | `at-store-checkout-real.spec.ts` **SCHK-07**：管理員把店鋪 A 停權→購物車顯示店鋪 A 的「暫停營業」說明、店鋪 A 的結帳按鈕停用、店鋪 B 的啟用；API 結帳店鋪 A→422 `E-2010`、店鋪 B→201；結束後還原店鋪狀態（守門共用同一個資料庫） |

### 突變驗證（Rule 9：測試必須在守門被拿掉時失敗）

| # | 突變 | 結果 |
|---|------|------|
| MS1 | 拿掉 `buildProductOrder` 的店鋪營業檢查 | 2 個轉紅（單元＋整合） |
| MS2 | 拿掉 `buildBookingCore` 的店鋪營業檢查 | 2 個轉紅（單元＋整合） |
| MS3 | `StoreCheckoutGuard.isOpen` 改成「只要店鋪存在就算營業」 | 10 個轉紅 |
| MS4 | 購物車的 `storeActive` 一律回 `true` | 2 個轉紅（單元＋整合） |
| MS5 | 拿掉舊 ROOM 訂單路徑的檢查 | `createOrderFromCart_room_suspendedStore_isRejected` 轉紅 |

每次突變都先備份源碼、突變後等 class 比源碼新才跑、跑完還原並以 `cmp` 確認逐位元組一致。（MS1 第一次因為樣式在檔案裡出現兩次被腳本拒絕，改用帶註解的唯一樣式重跑。）前端沒有元件層單元測試，畫面行為由 SCHK-07 守住。

## 5. 驗證結果

- **後端全量**：`mvn -o clean verify` **BUILD SUCCESS（23 分 51 秒）**——單元 **2058**（+11：守門測試 6 次執行、訂單／訂房／舊 ROOM 路徑／購物車各 +1、`ErrorCode` 全值對照表 +1）／整合 **723**（+3）／**0 失敗**／0 略過。耗時比 Sprint 237 的 16 分 30 秒多約 7 分鐘，**原因未查**：本輪只新增 14 個測試（Sprint 237 新增 36 個），測試數量解釋不了。
- **`make validate-schema-doc`**：對乾淨 PostgreSQL 套用 **89** 個 Flyway 遷移（本輪沒有新遷移）通過。
- **前端**：產線建置通過（含 TypeScript 型別檢查）；`eslint` 對本輪改動的 6 個檔案 0 錯誤（1 個警告是 `cart/page.tsx` 既有的 `<img>`）。
- **真實後端 E2E**：`E2E_GATE_SKIP_BUILD=1 make validate-e2e`（JAR 由上面的 verify 剛建好、前端先手動重建，見記憶 `e2e-gate-skip-build-reuses-stale-frontend`）**136 個測試：132 通過／4 略過／0 失敗（4.6 分鐘）**；後端以 `ddl-auto=validate` 啟動，確認 entity 與 Flyway schema 對齊；新增的 **SCHK-07** 全綠，Sprint 238 的 SCHK-05／06 與既有 mock 型購物車規格一併回歸通過。那 4 個略過是既有基準（Sprint 231～238 皆為 4 個）。本次守門沒有失敗，也沒有靠重試通過（Playwright 摘要沒有 flaky 項目）。
- **突變驗證**：見 §4（5 個全部被抓到，每次還原後以 `cmp` 確認逐位元組一致）。
- **push 與雲端 CI**：（push 後於回填 commit 補上）

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 已知限制

1. **店鋪停權前就已成立、尚未付款的訂單，停權後仍可付款**：只擋建立，不擋付款（使用者要求的範圍是「結帳」）。這類訂單會被既有的逾時機制（24 小時未付款自動取消）清掉，消費者也可以隨時取消；但在逾時前，消費者仍可把款項付給已停權的店鋪。是否也要擋「付款」是新的產品決定，見 §6.2。
2. **加入購物車不擋**：停權店鋪的商品仍可加入購物車，到結帳才被擋；購物車頁會標示，使用者可以移除。
3. **前端沒有元件層測試**（承 [SPRINT_238_PLAN.md](SPRINT_238_PLAN.md) §4）。
4. **購物車頁的「暫停營業」只在重新載入購物車時才更新**：使用者停留在購物車頁期間店鋪被停權，要重新整理才會看到；但後端結帳一定會擋。

### 6.2 待使用者決定／確認（累積）

| 項目 | 內容 | 我的建議 |
|------|------|----------|
| 本輪 §6.1 第 1 點 | 店鋪停權前已成立、尚未付款的訂單，是否也擋掉付款（回 `E-2010`，提示消費者取消） | 擋掉：與「避免消費者付款給停權店鋪」同一個理由；代價是多一個擋點與一則提示 |
| `DEF-326` | **使用者已拍板（Sprint 238 結尾）選項 (a)**，Sprint 240 實作 | — |
| Sprint 234 §6.4 | 沒有店鋪的使用者的限流單位（已登入→使用者、匿名→來源 IP，容量 100 次／分）是工程決策，PRD 未定義 | 同意（有疑慮可調整容量或加全域上限） |
| Sprint 235～237 §2 | `V87`／`V88` 會改既有資料庫的使用者角色與訂房租戶；店主／店員第一次看得到消費者訂房與訂單的個資；`V89`（**使用者已拍板照現狀**） | `V87`／`V88` 同意（部署前先跑預覽 `SELECT`） |

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果。
2. **Sprint 240：`DEF-326`（使用者已拍板）**：未歸屬店鋪的 SELLER／HOST 不得寫入店家層資料（依租戶判斷，不是依角色標籤）；`GET /v2/seller/dashboard`（`hasRole('SELLER')`，真正的店主呼叫不了）併入處理；依賴 SELLER／HOST 寫入的測試改用 STORE_OWNER；前端提示（未開店者引導去申請開店）。
3. 之後（我的安排）：文件對齊（`DEF-322`、`DEF-323` (b)；API／SRD 補上 Sprint 235～239 的契約變更）→ 店鋪成員管理前端（`DEF-321` (a)）→ CMS 卡片連結（`DEF-321` (c)）→ 通知事件（`DEF-318`）→ 店鋪前台 `/stores`（`DEF-321` (b)）。
4. 選配：整合測試的 bcrypt 強度。**第六個資料點（Sprint 238，後端沒有改動）整合 job 7m55s，比 Sprint 237 的 6m05s 多近 2 分鐘——同一份後端程式碼差這麼多，證明那個序列的差異主要是 runner 變異，不是 bcrypt**；除非有人真的量測，否則不再追這個假說。
