# Sprint 44 Review / Sprint 44 評審會議

> **Sprint 編號**: Sprint 44
> **期間**: 2027-06-20 ~ 2027-07-03
> **評審日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 完成 M12 進階定價全覆蓋（PRODUCT/Cart 折扣生效 + 買家整月日曆每日折扣）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 進階定價接入 PRODUCT 購物車/訂單計價鏈（AI-2403）| 3 | ✅ 完成 |
| US-002 | 買家整月日曆每日折扣顯示（AI-2405b）| 3 | ✅ 完成 |
| US-003 | Inter 字體自 host 離線化（AI-2303，附帶）| 2 | ✅ 完成 |

**承諾 8 SP（US-001~003）全數完成**。M12 進階定價自此**全覆蓋**：ROOM 訂房（S43）+ PRODUCT 購物車/訂單折扣皆生效，買家端 ROOM 詳情 availability（S43）+ 整月日曆（本 Sprint）皆顯示折扣。

---

## 2. 交付內容

- **US-001（後端，AI-2403）**：`getEffectivePrice` 回傳補 `appliedRuleName`；`RedisCartService.getCart` 讀取時對 PRODUCT 項以 `getEffectivePrice`（今日基準）套折扣——`DYNAMIC_PRICING_ENABLED` 開啟且折扣後 < 現價時，unitPrice/subtotal 為折扣後 + 回 `originalUnitPrice/discountAmount/appliedRuleName`；Redis 只存 basePrice（讀取時算，避免 stale）；toggle 關/無折扣 fallback。`CartItemResponse` 補 transient 折扣欄位。**`OrderService.createOrderFromCart` 未改**——訂單金額繼承 getCart 折扣後 subtotal，顯示與下單一致（單一計價來源）。
- **US-002（後端 + 前端，AI-2405b）**：`BookingService.getCalendar` 以 `PricingService.calculatePrice` 逐日 breakdown 取折扣後價（`dailyDiscountMap`，checkOut exclusive → endDate+1；只對可訂日、只套折扣型）；`CalendarResponse` 補 `originalPrice/appliedRuleName`。前端 `CalendarDay` 補欄位、`MonthCalendar` 新增 `originalPriceByDate`，可訂日有折扣時顯示折扣後價 + 原價刪除線（保留 `calendar-price-{date}` testid，新增 `calendar-original-price-{date}`）；E2E-ROOM-07。
- **US-003（前端，AI-2303）**：`layout.tsx` 由 `next/font/google` 改 `next/font/local`，指向 committed 的 Inter latin variable woff2（`src/app/fonts/Inter-latin.woff2`，48KB，OFL 授權，一次性自 Google Fonts 取得）；消除 build 期 Google Fonts 網路依賴；CJK 系統堆疊不動。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端單元（RedisCartServiceDynamicPricingTest）| ✅ **3 tests 0 fail**（折扣生效 / toggle 關 / 無折扣 三路徑）|
| 後端單元（RedisCartServiceTest 既有）| ✅ **20 tests 0 fail**（補 2 mock 建構參數，toggle 預設關 → 行為不變）|
| 後端整合（真實 DB：cart/order/M12）| ✅ **45 tests 0 fail**（M11CartPromo 10 + M12Product 3 + M12EffectivePrice 3 + OrderControllerE2E 12 + BuyerOrderJourney 5 + CartControllerE2E 12；toggle 關不退步）|
| 後端整合（getCalendar 回歸）| ✅ **26 tests 0 fail**（BookingControllerE2E 18 + M12Pricing 8）|
| 前端 tsc / build | ✅ 0 error；build 無 `next/font/google` 引用（離線可 build）|
| 本地 E2E 守門（make validate-e2e）| ✅ **48 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-07 日曆折扣）|
| schema 漂移 | ✅ 無（ddl-auto=validate 對齊）；**無 schema 變動**（Flyway V57，沿用 jsonb config + DTO-only 折扣欄位）|

---

## 4. 誠實揭露（Rule 12）

1. **schema-free 取捨**：訂單/購物車不新增「原價/折扣」持久欄位——訂單 unitPrice 直接為折扣後價（比照 S43 booking.totalAmount），cart（Redis）與 CalendarResponse 折扣資訊為 transient DTO 欄位。避免 order_items migration，維持 S42~S44「無 schema 變動」。代價：訂單不留折扣前原價的歷史紀錄（如未來需帳務對帳可另立含 schema 的 AI）。
2. **PRODUCT 折扣基準**：以 `getEffectivePrice`（basePrice + config.discountPercent）折扣後價，僅當「低於現存單價」才套用（買家favorable）；SKU priceOverride 若已低於 basePrice 折扣後價則維持 SKU 價。與 ROOM 的 basePrice 基準一致。
3. **一致性界線**：cart/order 與日曆只套「折扣型」規則；漲價型（weekend/seasonal）不套（與 S43 availability 一致）；計算失敗降級不阻斷。
4. **買家日曆每日折扣以「今日下單」為基準**：getCalendar 的 early-bird/last-minute 折扣以 bookingDate 預設今日計，代表買家當下瀏覽的折扣。
5. **AI-2303 字體資產**：Inter latin woff2 為一次性自 Google Fonts（OFL 授權）取得並 commit（48KB），非新增 npm 依賴；CJK 仍為系統堆疊（DEF-021 決策不變）。
6. **未接項**：`OrderService` ROOM 直接建單路徑（createRoomOrder，非經 cart）不在本次；定價機制統一（room_calendar 手動價 vs 規則）續留 AI-2406。

---

## 5. Demo 重點

- 買家（PRODUCT）：賣家設一條商品折扣規則 → 買家加入購物車 → 購物車顯示折扣後單價/小計（原價劃線）→ 結帳訂單金額 = 折扣後（顯示與收費一致）。
- 買家（ROOM 日曆）：ROOM 詳情整月日曆，可訂日格顯示「折扣後價 + 原價刪除線」。
- 開發體驗：`npm run build` 不再連 Google Fonts，離線可 build（降 validate-e2e flakiness）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
