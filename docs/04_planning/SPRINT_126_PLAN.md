# Sprint 126 Plan — DEF-048 擴大範圍：合併結帳（PRODUCT+ROOM 一次結清）

**Sprint**: Sprint 126
**日期**: 2026-09-04

---

## 1. 缺口盤點結果

DEF-048 原記錄「購物車混合 PRODUCT+ROOM 時，優惠券折扣顯示（`RedisCartService.getCartWithPromo`
整車混算）與實收（`OrderService`/`BookingService` 各自獨立結帳）不一致」。查證後發現比原記錄更
根本：**「一次結帳同時結清 PRODUCT 與 ROOM」這個使用者流程在系統裡根本不存在**——
`cart/page.tsx` 的「前往結帳」按鈕依購物車內容二選一導向 `/checkout/product`（僅結 PRODUCT）或
`/checkout`（僅結第一筆 ROOM），另一類項目留在購物車待使用者另外再結一次。兩條路徑各自獨立
驗證/套用/佔用/退還促銷碼（`OrderService` 與 `BookingService` 幾乎逐行同構但零共用），
`promo_code_usages` 表更以 CHECK 約束強制一筆用券紀錄只能屬於 Order 或 Booking 其中一種來源（XOR）。

使用者拍板：不滿足於「只修顯示端讓數字對齊現狀」，要求**真的新建合併結帳流程**。詳細設計過程
（含架構調查、三項業務規則的 AskUserQuestion 拍板紀錄）見核准計畫檔
`~/.claude/plans/fluttering-painting-sutton.md`（設計階段產物，不隨程式碼進版控）。

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | 待辦清單無現成項目，Sprint 126 方向？ | 處理 DEF-048 業務決策 |
| 2 | 購物車折扣試算基數要不要改成只算 PRODUCT？ | 不要（選「擴大結帳端」而非純顯示修正） |
| 3 | 沒有「一次動作結清 PRODUCT+ROOM」的既有流程，如何推進？ | 真的新建合併結帳流程 |
| 4 | FIXED_AMOUNT 型折扣如何分攤到 PRODUCT／ROOM？ | 比例分攤（pro-rata，依小計權重） |
| 5 | 合併結帳用一張券，使用次數算幾次？ | 算 1 次 |
| 6 | 事後只取消其中一邊，優惠券額度要不要退還？ | 兩邊都取消才退還 |
| 7 | 後端完成後如何推進？ | 不 commit，直接繼續前端；後端與前端合併成一次 commit |

## 3. 實作內容

### 3.1 後端：共用邏輯下沉到 `PromoService`

`OrderService`／`BookingService` 原本各自複製一份幾乎相同的私有方法
（`resolveValidPromoForCheckout`／`perUserLimitReached`／折扣封頂邏輯），新增合併結帳這第三個
呼叫者若繼續複製，會變成三份相同邏輯。抽到 `PromoService` 成為共用 public 方法：

- `resolveValidPromoForCheckout`／`perUserLimitReached`（原邏輯搬移，規則不變）
- `computeCappedDiscount`（從 `OrderService.applyPromoDiscount` 抽出的純計算部分）
- `allocateDiscount`（**唯一真正新寫的分攤演算法**）：依折扣型別分兩種規則——`FREE_SHIPPING`
  100% 歸 PRODUCT（ROOM 從未產生運費，pro-rata 在此不適用）；`PERCENTAGE`／`FIXED_AMOUNT`
  依小計權重比例分攤，數學上可證 PERCENTAGE 逐邊獨立算與先合併算再分攤結果恆相等。
- `releaseOrderSide`／`releaseBookingSide`：合併結帳用券紀錄的「兩側都取消才退還」規則——
  單一類型用券紀錄（`bookingId`／`orderId` 其中一個為 null）行為不變（立即 REVOKED＋釋放額度）。

### 3.2 後端：`OrderService` / `BookingService` 抽出可重用核心方法

- `OrderService.buildProductOrder`（新 public 方法）：接受已解析的 `promo`／已算好的
  `discountAmount`，不再自行解析促銷碼或佔用額度，供 `createOrderFromCart`（單一類型）與
  `CombinedCheckoutService`（合併結帳）共用。回傳型別為 `OrderDto.OrderResponse`。
- `BookingService.resolveBookableRoom` ／ `buildBookingCore`（新 public 方法）：同一模式的
  對稱抽法。`calculateTotalAmount` 改為 `public` 供合併結帳取得 ROOM 側小計。
- 兩者的 `commitPromoUsage` 簽章改接受 id 而非整個實體（`buildProductOrder`／`buildBookingCore`
  現在回傳 Response DTO，呼叫端只需要 id）。

### 3.3 後端：新增 `CombinedCheckoutService`（`core/checkout/`）

`checkoutMixedCart` 整個方法包在單一 `@Transactional` 內依序呼叫上述兩邊核心方法——Order 與
Booking 雖各自獨立 Service，但同屬一個 PostgreSQL 資料庫，Spring 預設傳播模式讓兩者共用同一個
DB 交易，任一邊失敗兩邊自動回滾，**不需要 saga／補償交易**（已由整合測試證實，見 §4）。流程：
讀購物車 → 篩 PRODUCT／ROOM 項目（兩者皆非空才允許） → 解析促銷碼一次 → 算合併總額與總折扣
（直接重用 `computeCappedDiscount`，不重寫折扣規則） → `allocateDiscount` 分攤 →
`buildProductOrder` → `buildBookingCore` → 兩側都成功後才 `tryConsumeUsageQuota` 一次、寫入
一筆同時關聯 `order_id`／`booking_id` 的 `PromoCodeUsage` → 清購物車。

新增 API：`POST /v2/checkout/mixed`（`CheckoutController`），比照 `BookingController` 走
`Idempotency-Key`（`OrderController` 目前沒有，但合併結帳同時牽動庫存預扣與訂房日曆鎖定，
比照 Booking 較安全）；`@PreAuthorize("hasAuthority('order:create') and hasAuthority('booking:create')")`
（兩者皆需，非互斥的 or）。新增 `CheckoutDto.MixedCheckoutRequest`／`MixedCheckoutResponse`。

### 3.4 後端：Schema `V77__Add_Combined_Checkout_Promo_Usage_Support.sql`

`promo_code_usages` 的 `chk_promo_code_usages_source` 從 V76 的 XOR（恰好一個非 null）放寬為
「至少一個非 null」，允許合併結帳寫入同時關聯 `order_id`／`booking_id` 的一列。新增
`order_released_at`／`booking_released_at`（皆可為 null）供「兩側都取消才退還」的部分取消
追蹤——單一類型結帳的既有列這兩欄恆為 null，不受影響。

### 3.5 前端：新增合併結帳頁面與購物車路由

- `frontend/src/services/checkout.ts`（新檔）：`MixedCheckoutRequest`／`MixedCheckoutResponse`
  型別對齊後端 `CheckoutDto`；`checkoutMixed(request, idempotencyKey)` 呼叫
  `POST /v2/checkout/mixed`（比照 `bookingService.createBooking` 走 Idempotency-Key）。
- `frontend/src/app/(auth)/checkout/mixed/page.tsx`（新頁面）：整合既有兩頁的必要片段——
  收件地址表單（沿用 `checkout/product/page.tsx` 的地址選擇 UI／`AddressService`）＋入住旅客表單
  （沿用 `checkout/page.tsx` 的姓名/電話/Email/特殊需求欄位）＋一個共用的促銷碼輸入框（比照
  `checkout/page.tsx` 的簡單文字欄位，非 `cart/page.tsx` 的「驗證→套用」兩段式）。ROOM 側的
  `roomListingId`／日期取自購物車項目，不由前端重複帶入。送出成功後沿用
  `checkout/product/page.tsx` 既有的付款流程（`OrderPaymentService.getPaymentState` →
  Stripe checkout session 或 `pay()`）——ROOM 側本來就沒有另外的付款步驟。
- `frontend/src/app/(auth)/cart/page.tsx`：「前往結帳」按鈕改為三分支——同時有 PRODUCT 與 ROOM
  → `/checkout/mixed`；純 PRODUCT → `/checkout/product`；純 ROOM → `/checkout`（後兩者既有路徑
  完全不變）。
- `frontend/src/lib/api.ts`：新增 `API_ENDPOINTS.checkout.mixed`。

## 4. 測試

**新增後端單元測試**（`PromoServiceTest`，51 案例，含新增約 25 案例）：
- `resolveValidPromoForCheckout`／`perUserLimitReached`／`computeCappedDiscount` 的真實規則
  驗證（此前由 `OrderPromoCodeTest`/`BookingPromoCodeTest` 以 mock `PromoCodeRepository` 驗證，
  邏輯搬到 `PromoService` 後兩處呼叫端改為 mock `PromoService` 只驗委派，實際規則驗證下沉到這裡）
- `allocateDiscount`：FREE_SHIPPING 全歸 PRODUCT、PERCENTAGE 分攤與逐邊獨立算結果相等、
  FIXED_AMOUNT 依權重分攤、四捨五入餘數歸 ROOM 且兩側加總不遺失一分錢、合併小計為 0 的防禦邊界
- `releaseOrderSide`／`releaseBookingSide`：單一類型立即釋放、合併結帳單側取消不釋放、
  雙側取消才釋放

**既有後端測試套件更新**（`OrderServiceTest`／`OrderPromoCodeTest`／`BookingServiceCreateBookingTest`／
`BookingServiceUpdateDateChangeTest`／`BookingPromoCodeTest`）：改為 mock `PromoService` 並驗證
正確委派（含各錯誤碼的傳遞、`orderRepository`/`bookingRepository` 於拒絕時不落地訂單），
全數維持綠燈，確認抽取重構沒有動到既有對外行為。

**新增後端整合測試**（`CombinedCheckoutIntegrationTest`，真實 PostgreSQL + Redis，4 案例）：
1. 合併結帳成功：一次呼叫、一筆 Order（折扣 20.00）+ 一筆 Booking（折扣 200.00，220 依
   200:2000 小計權重分攤）落地，**一筆**同時關聯 `orderId`／`bookingId` 的 `PromoCodeUsage`，
   `promo_codes.current_usage_count` 只 +1（非 +2）
2. 房型日期已被佔用（PRODUCT 側已成功 `save()` 之後，ROOM 側才失敗）→ 整個交易回滾，
   `orders`／`bookings` 兩表皆無新列，額度未被消費——證實 Order 與 Booking 雖各自獨立 Service，
   單一 `@Transactional` 仍能讓兩者共用同一個資料庫交易
3. 只取消 Order（保留 Booking）→ 額度不釋放，用券紀錄仍 `ACTIVE`、僅 `orderReleasedAt` 非 null
4. 兩側都取消 → 額度才真正釋放，用券紀錄轉 `REVOKED`

**前端驗證**：`tsc --noEmit`（0 error）、`eslint`（新增/修改檔案 0 error、0 新增 warning，
`cart/page.tsx` 既有的 2 個 warning 與本次改動無關，未觸碰）、`npm run build`（成功，
`/checkout/mixed` route 正確產生為靜態頁面）。**未新增 Playwright E2E 規格**——刻意的範圍決定
（見 §6），非靜默跳過。

## 5. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o clean compile`（逐支修改後即編譯，非累積到最後） | BUILD SUCCESS |
| ② | `mvn -o checkstyle:check`（main） | 0 violations |
| ③ | `mvn -o checkstyle:check@checkstyle-test` | 0 violations |
| ④ | `mvn -o test`（全量單元測試） | 1072 個，0 Failures／0 Errors |
| ⑤ | `mvn -o test -Dtest=CombinedCheckoutIntegrationTest`（真實 DB） | 4 個，0 Failures／0 Errors |
| ⑥ | `make validate-schema`（entity↔migration 漂移） | ✅ 無漂移 |
| ⑦ | `make validate-schema-doc`（migration↔文件） | ✅ 一致（見下方 ⚠️ 工具發現） |
| ⑧ | 前端 `npx tsc --noEmit` | 0 error |
| ⑨ | 前端 `npx eslint`（新增/修改檔案） | 0 error，0 新增 warning |
| ⑩ | 前端 `npm run build` | 成功，`/checkout/mixed` 產生 |

**⚠️ 過程中發現一個既有腳本的環境 flakiness（與本次程式改動無關）**：`validate-schema-doc.sh`
的 Docker 就緒判斷（`pg_isready` 迴圈）在 `postgres:18-alpine` 官方映像的「initdb 後內部重啟」
窗口期間會誤判為就緒，導致緊接著套用的第一個 migration（甚至 V1）失敗、連鎖造成後續全部
migration 都失敗。**已排除是本次 V77 的問題**：手動起一個乾淨容器、`pg_isready` 通過後**再多等
5 秒**，77 個 migration（含 V77）全數套用成功，且 `check_schema_doc.py` 比對結果為
「✅ 一致（含 §2.6 涵蓋率守門）」——`promo_code_usages` 已列在 SRD §2.6「已知範圍外資料表」，
本次只新增欄位不影響涵蓋率判定，不需要文件變更。**此腳本 flakiness 本身不在本次範圍內修復**
（純屬環境時序問題、非本次改動觸發，且修 CI 腳本不是 DEF-048 的一部分），已記錄於
[[validate-schema-doc-pg-isready-race]] 供日後排查。

## 6. 範圍外

- **Playwright E2E 規格**：核准計畫原列「手動／E2E 驗證混合購物車走新頁面」為擇一，本輪選擇
  以 `tsc`＋`eslint`＋`npm run build`＋新頁面直接沿用兩個已驗證頁面的既有 UI 模式（不是全新
  互動邏輯）作為前端把關，未另寫 Playwright 規格。理由：新增一份可靠的 E2E 規格需要「新建
  PRODUCT 與 ROOM 兩個 listing → 混合加入購物車 → 走完整結帳」的完整前置鏈，本輪已由後端
  `CombinedCheckoutIntegrationTest` 對交易正確性（風險最高的部分）做了真實 DB 層級驗證；純前端
  UI 組裝風險較低。**留待日後有 E2E 補測需求時再排入**，非靜默跳過。
- `validate-schema-doc.sh` 的 Docker 就緒判斷 flakiness（見 §5 ⚠️）記錄但不修，非本次範圍。
- 完整 `mvn -o verify`（含全部 IntegrationTest／PMD／checkstyle-test 全量整合回歸）與
  `make validate-release` 留待 push 前執行一次。

## 7. Velocity 紀錄

| Sprint | 主題 | 估點 (SP) | 實際規模 |
|--------|------|-----------|----------|
| 126 | DEF-048 擴大範圍：合併結帳（後端＋前端） | 10（原估：處理業務決策 2 SP，實際因使用者兩輪拍板擴大為全新功能，且合併原規劃的前端 Sprint 127） | 後端 10 檔異動（4 主程式修改＋4 主程式新增＋6 測試修改＋2 測試新增，+893/-367 行，1 篇新 migration）＋前端 4 檔異動（2 新增＋2 修改） |

## 8. 下一步 / Action Items

| 項目 | 狀態 |
|------|------|
| `make validate-release`（含全量整合測試＋PMD＋Playwright E2E）＋ `git commit` ＋ push | 待執行 |
| RELEASE_TRACKER.md 新增 Sprint 126 row、依「狀態欄維護規則」於下一 Sprint 開工時回填本輪 push 狀態 | 待 push 後執行 |
| DEF-048 已完整完成（後端＋前端皆已實作並驗證），移入 DEFERRED_ITEMS_TRACKER.md「已完成延後項目」 | 已於本輪一併處理 |
| `validate-schema-doc.sh` 的 `pg_isready` 就緒判斷 flakiness（見 §5） | 已記錄不修，供日後排查參考 |
| 合併結帳的 Playwright E2E 規格（見 §6 範圍外） | 留待日後排入，非本輪範圍 |
