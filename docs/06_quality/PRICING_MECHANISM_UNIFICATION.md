# 定價機制統一決策 / Pricing Mechanism Unification

> **文件類型**: 架構決策記錄（ADR）+ 技術債收斂
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **來源**: Sprint 45 US-001（AI-2406）；探勘於 Sprint 44 Retro 提出
> **狀態**: ✅ 清理已落地（Sprint 45）；🔴 行為變更（漲價規則計入 booking）待 PO 決策 → AI-2406b

---

## 1. 背景：兩套 ROOM 每日定價機制並存

歷史上 ROOM 每日定價存在兩套機制：

| # | 機制 | 儲存 | 寫入 API | 讀取 |
|---|------|------|----------|------|
| (1) | `room_calendar.price`（每日手動價）| room_calendar 表 | `RoomCalendarService.setDatePrice` / `setDatePriceBulk` | getCalendarRange → booking/availability/getCalendar 的 base 價 fallback |
| (2) | PricingService 規則（含 `MANUAL_OVERRIDE`）| pricing_rules 表 | `PricingService.setCalendarPrice` / `overridePrice`（建 MANUAL_OVERRIDE 規則）| calculatePrice / getEffectivePrice |

Sprint 43-44 動態定價折扣以機制 (2)（basePrice + 規則）為基準，折扣生效時機制 (1) 的手動價不參與，兩機制語意重疊、易混淆。

---

## 2. 關鍵發現（Sprint 45 探勘）

**機制 (1) `room_calendar.price` 的寫入路徑是死碼。**

- `setDatePrice` / `setDatePriceBulk` 全專案**零呼叫者**：無 controller 端點、無其他 service 呼叫、無測試、無前端、無 seed 資料（探勘 + `grep` 雙重確認）。
- `room_calendar` 表自 `V1__Initial_Schema` 建立後**無任何 INSERT seed**；記錄僅由 `bookDateRange`（BOOKED）/ `blockDateRange`（BLOCKED）lazy 建立，這些路徑**不寫 price**。
- 故 `room_calendar.price` 在實務上**恆為 NULL**，所有 `calendar.getPrice() ?? basePrice` 的讀取恆回 `basePrice`。

**機制 (2) MANUAL_OVERRIDE 才是實際生效的手動日價路徑。** `setCalendarPrice` 本身即「手動日價 → MANUAL_OVERRIDE 規則」的實作，有端點（`POST /v2/dashboard/pricing/calendar/price`）。

**結論**：所謂「雙機制」實為**死碼假象**——真正運作的只有機制 (2)。統一 = 移除機制 (1) 死碼，確立機制 (2) 為唯一手動日價路徑。

---

## 3. 決策

### 3.1 已落地（Sprint 45 US-001，低風險、schema-free）

1. **移除死碼** `RoomCalendarService.setDatePrice` / `setDatePriceBulk`。
2. **停讀 room_calendar.price**：`BookingService` 的 `calendarBaseTotal`、`checkAvailability`、`getCalendar` 三處移除 `calendar.getPrice() ?? basePrice` 死欄位 fallback，每日基準價一律取 `listing.getBasePrice()`（因該欄位恆 NULL，**行為等價**；並修正舊 `calendarBaseTotal` 對「有記錄之日」加 NULL→ZERO 的潛在低估）。
3. **`RoomCalendar.price` 標記為停用/保留**（註解說明；欄位暫留以符 `ddl-auto=validate`。**未加 `@Deprecated` 註解以維持專案 `@Deprecated=0` 慣例**）。
4. **確立**：手動日價唯一路徑 = `PricingService` 的 `MANUAL_OVERRIDE` 規則。

### 3.2 待 PO 決策（→ 另立 AI-2406b，非本 Sprint）

**議題：漲價型規則是否計入 booking 總價？**

目前 booking 計價（`tryDynamicPricing`）只套「折扣」（`adjustedTotal < baseTotal`）；**漲價型規則（MANUAL_OVERRIDE 調高價、WEEKDAY_WEEKEND 週末加成、SEASONAL 旺季加成）目前不計入 booking 總價**，僅在 availability/calendar 顯示層以 `calculatePrice` 呈現。這造成「顯示加價、但下單仍原價」的潛在不一致。

- **選項 A（維持現狀）**：booking 只吃折扣。優點：買家 favorable、風險低。缺點：賣家設的加價規則對訂房不生效（與顯示不一致）。
- **選項 B（全面改走 PricingService）**：`calculateTotalAmount`/`checkAvailability`/`getCalendar` 一律用 `calculatePrice` 的 adjustedTotal（含漲價）。優點：機制真正統一、顯示與收費完全一致。缺點：**改變既有訂房金額行為**（漲價開始生效）→ 需 PO 商業決策 + QA 回歸；需補 `calculatePrice` 於無 Room 列時的優雅降級（避免 500）；更新計價測試。估 **5-8 SP**。

**建議**：選項 B 較徹底且消除顯示/收費不一致，但屬商業行為變更，應由 PO 拍板後另立 **AI-2406b** 實作（含 QA 回歸）。在此之前維持選項 A。

### 3.3 後續低風險清理（→ 另立）

- `V58__Drop_Room_Calendar_Price.sql`（DROP COLUMN price）：因無資料、無讀寫者，風險極低；待 3.1 邏輯統一穩定後執行。

---

## 4. 影響與驗證

- **schema**：無變動（Flyway 維持 V57；price 欄位保留）。
- **行為**：3.1 為行為等價清理；不含 3.2 的漲價行為變更。
- **驗證**：booking/M12/cart/order 計價整合測試不退步；`make validate-e2e` 綠。

---

**文件版本**: v1.0｜**建立者**: SD Marcus + Dev David + PM Victoria + Claude Code｜**基於**: AISDLC v0.09
