# Sprint 44 計劃 / Sprint 44 Plan

> **Sprint 編號**: Sprint 44
> **期間**: 2027-06-20 ~ 2027-07-03 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: Sprint 43 Retro Action Items（AI-2403 / AI-2405b）+ 活躍延後（AI-2303）+ S44 二 Agent 探勘（PRODUCT/Cart 計價、getCalendar 每日折扣）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**完成 M12 進階定價全覆蓋**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「完成 M12 進階定價全覆蓋」 | 延續 S43 ROOM 折扣，補 PRODUCT/Cart + 買家日曆 |
| S43 狀態 | ✅ 已完成（6 commit，未 push）；活躍 DEF=0 | push 債累積 S41+S42+S43 |
| 技術現況已調查 | ✅ 2 個 Explore Agent（PRODUCT/Cart、getCalendar 折扣） | 可行範圍已釐清 |
| **AI-2403 現況** | PRODUCT 購物車（RedisCartService）與訂單（OrderService PRODUCT 路徑）直接用 basePrice/SKU、**未經 PricingService**；`getEffectivePrice` 已有 PRODUCT 折扣邏輯（config.discountPercent）但**無人於 cart/order 呼叫** | 資料面已備（pricing_rules.listing_id V44），缺「接線」 |
| **AI-2405b 現況** | `getCalendar` 回稀疏每日 status+price（calendar/basePrice），**未套折扣**；`calculatePrice` 已有每日 breakdown（adjustedPrice/appliedRuleName）可 merge | read-only |
| schema 影響 | ✅ **schema-free**：訂單 unitPrice 直接為折扣後價（不加欄位，比照 S43 booking.totalAmount）；cart（Redis）與 CalendarResponse 折扣欄位皆 DTO-only | Flyway 維持 V57 |
| 一致性界線 | ✅ 只套「折扣型」（早鳥/長住/末班車），漲價型（weekend/seasonal）不套，與 S43 availability 一致 | 誠實界線 |
| push 前置 | ⚠️ 本 Sprint 有後端 production 變動（計價接線）→ push 需完整 `make validate-release` | 承 S41~S43 push 債 |

---

## 1. Sprint 44 目標

> **主題**: 完成 M12 進階定價全覆蓋（PRODUCT/Cart 折扣生效 + 買家整月日曆每日折扣）

延續 S43（ROOM 訂房折扣已生效），補齊 M12 進階定價的兩塊缺口：**(1)** 把折扣接進 **PRODUCT 購物車/訂單計價鏈**（商品折扣真正反映在購物車顯示與下單金額）；**(2)** 買家 **整月日曆每日折扣顯示**（每格顯示折扣後價 + 原價刪除線）。附帶清償 **AI-2303**（Inter 字體 build 期 Google Fonts 依賴，降 validate-e2e flakiness）。全部**無 schema 變動**（沿用 jsonb config + DTO-only 折扣欄位），採 Feature Toggle + 向後相容，與 S43 同範式。

---

## 2. User Stories

### US-001：進階定價接入 PRODUCT 購物車/訂單計價鏈（P2）（AI-2403）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: `PricingService.getEffectivePrice` 回傳補 `appliedRuleName`（與 ROOM 折扣顯示對齊；EffectivePriceResponse DTO 加欄位，無 schema）
**AC-001-2**: `OrderService.createOrderFromCart` PRODUCT 迴圈：`DYNAMIC_PRICING_ENABLED` 開啟且有折扣時，OrderItem unitPrice/subtotal 以 `getEffectivePrice` 折扣後單價計；toggle 關閉/無規則時走原 `cartItem.getUnitPrice()`（向後相容）——訂單金額為權威計價點
**AC-001-3**: `RedisCartService.getCart`（及 getCartWithPromo）讀取時即時重算折扣後單價/小計顯示，使購物車顯示與結帳一致；Redis 只存 basePrice（不存折扣結果，避免 stale）
**AC-001-4**: `CartDto.CartItemResponse` 加 transient 折扣欄位（originalUnitPrice / discountAmount / appliedRuleName，DTO-only）；無折扣時為 null
**AC-001-5**: 新增測試——「toggle on + 有規則 → cart 顯示與 order 金額為折扣後」「toggle off / 無規則 → 維持 basePrice（不退步）」「promo code 與定價折扣疊加行為明確」；既有 cart/order 測試（RedisCartServiceTest/OrderControllerE2ETest/BuyerOrderJourneyE2ETest）不退步
**AC-001-6**: 界線——只接 PRODUCT 路徑折扣；促銷券（promo code）邏輯不變；ROOM 路徑不受影響

### US-002：買家整月日曆每日折扣顯示（P3）（AI-2405b）

> **SP**: 3 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-002-1**: `BookingService.getCalendar`：以 `pricingService.calculatePrice(start, endDate.plusDays(1))` 取每日 breakdown，merge 進 CalendarResponse——每日回折扣後價（僅折扣型、adjustedPrice < basePrice 才套；toggle 保護）
**AC-002-2**: `CalendarResponse` 加 `originalPrice` + `appliedRuleName`（DTO-only，無 schema）；無折扣之日為 null
**AC-002-3**: 界線——只對**可訂日**套折扣（BOOKED/BLOCKED 日不顯示價格，不受影響）；只套折扣型（漲價型 weekend/seasonal 不套，與 availability 一致）；日期對齊注意 calculatePrice checkOut **exclusive**（傳 endDate+1）
**AC-002-4**: 前端 `booking.ts` `CalendarDay` 加 `originalPrice`/`appliedRuleName`；`MonthCalendar` 新增 `originalPriceByDate`，可訂日有折扣時顯示折扣後價 + 原價刪除線（沿用 ListingDetail/ProductCard 的 line-through 樣式）；**保留 `calendar-price-{date}` testid**（不破壞 E2E-ROOM-05），新增 `calendar-original-price-{date}`
**AC-002-5**: 新增 E2E-ROOM-07（mock getCalendar 回含折扣之日 → 斷言日曆格顯示折扣後價 + 原價刪除線）；`make validate-e2e` 綠、不退步

### US-003：Inter 字體自 host 離線化（P3，附帶技術債）（AI-2303）

> **SP**: 2 | **優先級**: P3 | **狀態**: 📋 Ready

**AC-003-1**: 取得 Inter latin 子集 woff2，放入 `frontend/src/app/fonts/`（或適當位置）
**AC-003-2**: `layout.tsx` 由 `next/font/google` 改 `next/font/local`（維持 `variable: "--font-inter"`，globals.css 不需改）；先讀 `node_modules/next/dist/docs/` 確認 Next 16 `next/font/local` API（AGENTS.md 規範）
**AC-003-3**: `npm run build`（Turbopack）0 error，且 build 期**不再連 Google Fonts**（離線可 build）；字體視覺無回歸
**AC-003-4**: 不動 CJK 系統堆疊（DEF-021 決策維持）；僅 latin Inter 改自 host

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 進階定價接入 PRODUCT/Cart 計價鏈（AI-2403）| 3 | P2 |
| US-002 | 買家整月日曆每日折扣顯示（AI-2405b）| 3 | P3 |
| US-003 | Inter 字體自 host 離線化（AI-2303）| 2 | P3 |
| **承諾合計** | | **8 SP** | |

> **Velocity 參考**：S39=10, S40=9, S41=12, S42=7, S43=10。**本 Sprint 8 SP**，功能完成型 + 技術債，落在健康區間。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 AI-2403（getEffectivePrice+appliedRuleName → OrderService PRODUCT 折扣 → getCart 重算 → CartItemResponse 欄位 → 測試）
   ↓ 後端計價鏈綠（需 make test-db-up 跑整合測試）
US-002 AI-2405b（getCalendar merge 每日折扣 → CalendarResponse 欄位 → 前端 MonthCalendar 刪除線 → E2E-ROOM-07）
   ↓ 前後端綠
US-003 AI-2303（Inter woff2 子集 → next/font/local → build 離線驗證）
   ↓
make validate-e2e（一次驗證 US-002 前端 + US-003 build + 全棧不退步）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：US-001 後端整合測試需 `make test-db-up`（integration-test profile 連真 postgres:5432），跑完 `make test-db-down`；每後端單元 mvn 驗證、每前端檔 build/tsc/lint；US-002 後 validate-e2e。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **PRODUCT 計價接線改變既有訂單金額（高風險）** | toggle（DYNAMIC_PRICING_ENABLED）保護；無規則 fallback 原 unitPrice（向後相容）；權威計價點在 createOrderFromCart（單一 PRODUCT 迴圈，ROOM 不受影響）；補 toggle on/off × 有無規則測試 + 既有 cart/order 測試不退步 |
| 購物車顯示與下單金額不一致 | getCart 讀取時即時重算（同一 getEffectivePrice 來源），Redis 只存 basePrice 避免 stale |
| promo code 與定價折扣疊加語意混淆 | 明確界線：定價折扣先算出折扣後單價，promo code 仍作用於小計層（既有邏輯）；補疊加測試釐清 |
| getCalendar 每日折扣的稀疏/密集、漲價、BOOKED、per-day 手動價界線 | 只對可訂日、只套折扣型、日期對齊 endDate+1、per-day 手動價不參與（與 availability 一致）；界線寫入程式註解 |
| next/font/local Inter 子集與 Turbopack 相容 | 先讀 node_modules/next docs 確認 API；build 驗證離線可 build；CJK 系統堆疊不動 |
| 有後端 production 變動 → push 需完整 validate-release | 承 S41~S43 push 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001（AI-2403）：PRODUCT cart/order 折扣生效（toggle + 向後相容）；cart 顯示與 order 金額一致；CartItemResponse 折扣欄位；測試涵蓋 on/off + 不退步；無 schema
- [ ] US-002（AI-2405b）：getCalendar 每日折扣 + CalendarResponse 欄位；MonthCalendar 折扣後價 + 原價刪除線；E2E-ROOM-07；保留 ROOM-05 testid
- [ ] US-003（AI-2303）：Inter 自 host、build 離線 0 error、視覺無回歸；CJK 堆疊不動
- [ ] `make validate-e2e` 綠、既有不退步；schema 無漂移（Flyway V57）
- [ ] Sprint 44 Review / Retro / Release Notes + trackers 更新
- [ ]（檢查點）承 S41~S43 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| PRODUCT 計價 | `backend/.../core/pricing/PricingService.java`(getEffectivePrice)、`PricingDto`(EffectivePriceResponse)、`core/order/OrderService.java`、`core/cart/RedisCartService.java`、`CartDto`、相關測試 |
| 日曆折扣 | `backend/.../core/booking/BookingService.java`(getCalendar)、`BookingDto`(CalendarResponse)、`frontend/src/services/booking.ts`、`components/storefront/MonthCalendar.tsx`、`e2e/at-room-booking.spec.ts` |
| Inter 離線化 | `frontend/src/app/layout.tsx`、`frontend/src/app/fonts/`（新 woff2） |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. 🔴 待使用者確認點

1. **範圍**：完成 M12 全覆蓋 = US-001 PRODUCT/Cart 折扣 + US-002 買家日曆每日折扣 + US-003 Inter 離線化（附帶）= **8 SP**。是否核准?
2. **schema-free 取捨**：訂單不新增「原價/折扣」欄位（unitPrice 直接為折扣後價，比照 S43 booking），僅 cart/calendar DTO 帶 transient 折扣資訊。是否同意（避免 order_items migration）?
3. **一致性界線**：cart/order 與日曆只套「折扣型」規則（漲價型 weekend/seasonal 不套，與 S43 availability 一致）。是否同意?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
