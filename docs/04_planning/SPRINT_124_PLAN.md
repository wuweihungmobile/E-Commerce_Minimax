# Sprint 124 Plan — DEF-047：訂房結帳套用促銷碼

**Sprint**: Sprint 124
**日期**: 2026-09-04
**主題**: 使用者授權依序處理三項待辦中的第二項，原估 5 SP。

---

## 1. 缺陷（原記錄）

PRD US-010「作為預訂買家，我希望在結帳時套用優惠碼」未落地。原記錄指向
`OrderService.createRoomOrder`（`POST /v2/orders` 的 ROOM 分支）：該方法直接以
`listingId` + 日期建單，不走購物車，完全沒有促銷碼路徑。

## 2. 查證推翻原記錄的修復目標

動工前先確認「訂房結帳」實際指向哪支程式碼——查證後發現原記錄指錯了目標：

- 全域搜尋 frontend 對 `orderType: 'ROOM'` 的送出點：**零命中**（`orderType` 在
  `orders/page.tsx`／`orders/[id]/page.tsx` 只用於**顯示**，`backend/…/OrderServiceTest.java`
  是唯一送出 `orderType("ROOM")` 的地方）。`OrderService.createRoomOrder` 從未被任何
  前端頁面呼叫。
- 買家實際的訂房結帳頁 `frontend/(auth)/checkout/page.tsx`（`cart/page.tsx:495`
  `router.push(hasProduct ? '/checkout/product' : '/checkout')` 導入）呼叫的是
  `bookingService.createBooking` → **`POST /v2/bookings`** → **`BookingController`**
  → **`core.booking.BookingService`**——與 `OrderService` 完全獨立的服務／實體
  （`Booking`，非 `Order`）。
- `Booking` 實體／`bookings` 表從未有促銷碼相關欄位，`PromoService`／
  `PromoCodeRepository`／`PromoCodeUsageRepository` 在 `BookingService` 零注入。
- PRD US-010 開頭即寫「作為**預訂**買家」，與 `BookingService` 服務的「民宿預訂」
  語意一致，佐證真正該修的是這條路徑而非 `OrderService.createRoomOrder`。

**結論**：DEF-047 原記錄的「問題存在」判斷正確，但「修復目標」判斷錯誤——若只修
`OrderService.createRoomOrder`，PRD US-010 實際上仍是斷鏈的（因為使用者從未走那條
路徑）。本輪改為修復真正被使用的 `BookingService` 路徑；`OrderService.createRoomOrder`
維持原樣（見 §5 範圍外）。

## 3. 修法

比照 Sprint 100（`OrderService`）已建立的模式（`resolveValidPromoForCheckout`／
`commitPromoUsage`／`refundPromoUsage`／`applyPromoDiscount`），移植到 `BookingService`：

1. **`V76__Add_Promo_Code_To_Bookings.sql`**：
   - `bookings` 新增 `promo_code`／`discount_amount`（比照 `orders` 表 V70 的做法）。
   - `promo_code_usages.order_id` 的 `NOT NULL` 放寬，新增 `booking_id`（FK
     `bookings`），並加 `CHECK ((order_id IS NOT NULL) <> (booking_id IS NOT NULL))`
     ——`order_id` 原本 `REFERENCES orders(id)`，不能塞入 booking id，故另開一欄而非
     沿用同一欄位存兩種語意不同的 ID；CHECK 約束確保一筆用券紀錄恰好屬於一種來源。
2. **`Booking.java`／`PromoCodeUsage.java`**：對應新增／放寬欄位映射。
3. **`BookingDto.CreateRequest`** 新增 `promoCode`（選填，訂房沒有購物車可預先套用，
   由前端於送出訂房請求時明確帶入）；**`BookingDto.BookingResponse`** 新增
   `promoCode`／`discountAmount`。
4. **`BookingService`**：
   - `createBooking`：計算晚數總額後，依 `request.getPromoCode()` 驗證並套用折扣
     （`resolveValidPromoForCheckout` → `applyPromoDiscount`，抽為獨立方法避免
     `createBooking` 的 NPath 分支複雜度衝到 checkstyle 上限 200，同 Sprint 100 的
     `OrderService.applyPromoDiscount` 理由）；訂房無運費，`FREE_SHIPPING` 券折扣基數
     傳 0（該類型券因此恆為 0 折扣，非本輪需处理的錯誤）。
   - 訂房寫入＋日曆更新後才呼叫 `commitPromoUsage`（原子佔用總量額度 + 寫入
     `PromoCodeUsage(bookingId=…)`），與 `OrderService` 同一理由：鎖定日曆期間仍可能
     被同張券的另一筆併發結帳搶先用完額度。
   - `cancelBooking`：取消時呼叫 `refundPromoUsage`（用券紀錄轉 `REVOKED` + 原子
     回補總量額度），比照 PRD §2630 對訂單的既有原則。
   - **`updateBooking` 的日期異動路徑（`recalculateAndBookDateRange`）**：這是 `Booking`
     特有、`Order` 沒有的風險——若只重算 `grossAmount` 卻不重算折扣，改個日期就會讓
     買家結帳當下算好的折扣憑空消失（`totalAmount` 被直接設回新的 gross 值）。修法：
     若該預訂已有 `promoCode`，依已記錄的券別對新總額重算折扣（不重新驗證有效性／
     不重新佔用額度，額度已在建立當下佔用完畢）。

## 4. 測試

新增 `BookingPromoCodeTest.java`（12 個測試，比照 `OrderPromoCodeTest` 同一套結構）：

- 折扣正確扣除、總額不為負、未套用時行為不變（回歸保護）
- 促銷碼不存在／過期／總量用罄／每人限用已達 → 均拒絕建立預訂，不留下 booking
- 原子佔用失敗（併發搶完額度）→ 拒絕，且鎖仍在 `finally` 正確釋放
- 用券紀錄的 `bookingId` 正確、`orderId` 為 null（區別於 `OrderService` 那條路徑）
- 取消已用券預訂 → 用券紀錄轉 `REVOKED` 且額度回補；取消未用券預訂則完全不碰優惠券資料
- **`Booking` 特有**：異動日期後折扣依新總額重算，而非被靜默丟棄

前端 `checkout/page.tsx` 新增「優惠碼」輸入框（訂房沒有像 PRODUCT／`cart/page.tsx` 那樣
「驗證→套用到購物車」的兩段式流程，改為送出訂房請求時一併帶入，由後端一次驗證與套用）；
成功畫面顯示已套用的優惠碼與折扣金額；`services/booking.ts` 補上 `promoCode`／
`discountAmount` 型別與 E-5007／E-5008／E-5009 的可讀錯誤訊息（對齊
`BookingService.resolveValidPromoForCheckout` 的錯誤碼）。

## 5. 範圍外

- **`OrderService.createRoomOrder`／`createOrderFromCart` 的 ROOM 分支維持原樣**：
  查證後確認前端零呼叫點（見 §2），非目前使用者能觸及的路徑；若未來要收斂「訂房到底
  該走 `Order` 還是 `Booking`」這個既有的雙路徑架構分裂，屬另一個獨立的架構課題，
  不在本次「補齊促銷碼」的範圍內。
- `promo_codes.discountType = FREE_SHIPPING` 對訂房恆為 0 折扣：訂房無運費本是既有
  事實，非本輪引入的缺陷，維持現狀（`computeDiscount` 早已如此設計，Sprint 101
  DEF-045 的既有邏輯，未修改）。
- 前端「先驗證再套用」的兩段式 UX（比照 `cart/page.tsx` 的 `validatePromo`／
  `applyPromo` 兩個端點）：訂房沒有等價的持久化購物車狀態可暫存已驗證的券，若要做
  該體驗需另開驗證端點，規模超出本輪，維持「送出時一次驗證」的簡化版。

## 6. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o compile` | BUILD SUCCESS |
| ② | `make validate-schema`（entity ↔ migration） | ✅ 無漂移 |
| ③ | `make validate-schema-doc`（migration ↔ 文件） | ✅ 無漂移（`bookings`／`promo_code_usages` 已在 SRD §2.6 範圍外清單，見 DEF-068） |
| ④ | `mvn -o test`（全量單元測試，含新增 `BookingPromoCodeTest` 12 案例） | 0 Failures / 0 Errors |
| ⑤ | `mvn -o checkstyle:check` | 0 violations（含修正 `createBooking` 抽出 `applyPromoDiscount` 後的 NPath 複雜度） |
| ⑥ | `mvn -o verify`（全量含整合測試＋PMD＋checkstyle-test） | 見下方回填 |
| ⑦ | 前端 `npx tsc --noEmit` | 0 error |
| ⑧ | 前端 `npm run build` | 成功，`/checkout` 路由正常產出 |

（改動涉及生產邏輯（`BookingService`／entity／migration），依 `sprint-full-regression-policy`
跑全量回歸而非快速測試。）
