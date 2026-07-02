# Release Notes - v2027.07.17-01 (Sprint 45)

**發布日期**: 2027-07-17（規劃）／實作完成 2026-07-02
**發布類型**: Patch（定價區技術債收斂：清死碼 + 停讀 room_calendar.price + 兩份決策文件；行為等價；無 schema 變動）
**Sprint**: Sprint 45
**狀態**: ⏳ 待 push（本 Sprint 3 commit；push 債累積 S41+S42+S43+S44+S45，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 45 主題：**定價區技術債收斂**。M12 進階定價全覆蓋（S43~S44）後，收斂定價區的兩項技術/架構債。探勘揭示兩者本質皆為**決策密集**而非實作密集，故本 Sprint 定位為「**低風險清理 + 決策文件**」：**(1)** 統一手動日價機制——揭穿並移除 `room_calendar.price` 的死碼寫入路徑、邏輯層停讀該（恆 NULL）欄位，確立 `MANUAL_OVERRIDE` 規則為唯一手動日價路徑；**(2)** 開放窗語意——產出「未開放 vs 可訂」的評估與決策文件（選項比較 + schema/backfill 策略）。後端變動限於**移除死碼 + 讀取簡化（行為等價）**，無 entity 新增 / migration / schema 變動（Flyway 維持 V57；連續 S42~S45 零 migration）。

---

## 改進 🚀

- **定價機制統一——清死碼 + 停讀（AI-2406，US-001）**：揭穿「雙定價機制並存」實為**死碼假象**——`room_calendar.price`（每日手動價）的寫入路徑 `RoomCalendarService.setDatePrice` / `setDatePriceBulk` 全專案零呼叫者（無 controller/service/test/前端/seed），`room_calendar` 自 V1 後無 INSERT seed，記錄僅由 bookDateRange/blockDateRange lazy 建立而**不寫 price** → 欄位恆 NULL、讀取恆回 basePrice。本次**移除死碼**兩方法；`BookingService` 三處讀取（`checkAvailability`、`getCalendar`、`calendarBaseTotal`）移除 `calendar.getPrice() ?? basePrice` 死欄位 fallback，每日基準價一律取 `listing.getBasePrice()`（因恆 NULL，**行為等價**）；`calendarBaseTotal` 由逐日 getCalendarRange 迴圈改為 `basePrice × nights`，**順帶修正**舊寫法對「有記錄之日」加 NULL→ZERO 的潛在低估。`RoomCalendar.price` 以註解標記停用/保留（schema 對齊；DROP COLUMN 另立）。**確立手動日價唯一路徑 = `PricingService` 的 `MANUAL_OVERRIDE` 規則。**

## 文件 / 決策 📋

- **定價機制統一決策（AI-2406，US-001）**：[PRICING_MECHANISM_UNIFICATION.md](../06_quality/PRICING_MECHANISM_UNIFICATION.md)——ADR 候選，記錄死碼發現、確立 MANUAL_OVERRIDE 為唯一手動日價路徑；就「**漲價型規則（MANUAL_OVERRIDE 調高、WEEKDAY_WEEKEND 週末加成、SEASONAL 旺季加成）是否計入 booking 總價**」提出選項分析（A 維持現狀只吃折扣 / B 全面走 PricingService，含漲價）與建議，標記需 PO 裁決 → 另立 **AI-2406b**（行為變更 + QA 回歸，5-8 SP）。
- **開放窗「未開放 vs 可訂」語意評估（AI-2202d，US-002，spike）**：[CALENDAR_OPEN_WINDOW_ASSESSMENT.md](../07_design/CALENDAR_OPEN_WINDOW_ASSESSMENT.md)——記錄「無 room_calendar 記錄 = 可訂」為 availability/booking/calendar 三層硬語意、room_calendar 稀疏 lazy 建立；比較三選項（A Room 層級開放窗欄位【推薦，需 migration】/ B room_calendar 新增 CLOSED 狀態【高風險】/ C 純前端【不建議單用】），含 schema 影響、既有房源衝擊、與零-migration 慣例的張力；建議選項 A（`open_until_date DATE NULL`）+ NULL 安全過渡，標記需 PO 拍板 → 決策後另立實作 **AI-2202e**（估 5 SP）。**本 US 為 spike，不改 production code。**

## 測試 / 驗證 ✅

- **後端編譯**：mvn 0 error（US-001 清死碼後）。
- **後端單元**：計價相關（BookingServiceDynamicPricingTest 等）**6 tests 0 fail**。
- **後端整合（真實 DB）**：booking/M12/cart/order 計價 **57 tests 0 fail**（行為等價、不退步）。
- **本地 E2E 守門（`make validate-e2e`）**：**48 passed / 6 skipped / 0 failed**（US-001 清理不退步；S45 無新增 E2E）。
- **schema 漂移**：無（ddl-auto=validate 對齊）。
- **`@Deprecated` 計數**：維持 0（RoomCalendar.price 以註解標記停用，未加 `@Deprecated` 以維持專案 `@Deprecated=0` 慣例）。

## 技術決策 / 已知限制 ⚠️

- **行為等價清理（誠實揭露 Rule 12）**：US-001 因 room_calendar.price 恆 NULL，移除其 fallback 讀取不改變任何對外行為；calendarBaseTotal 改寫順帶修正舊迴圈 NULL→ZERO 潛在低估（該路徑實務未觸發，屬防禦性修正）。以真 DB 整合 57 tests 證實不退步。
- **決策密集項誠實另立**：本 Sprint 不做任何計價/語意行為變更——漲價計入 booking → **AI-2406b**（需 PO 決策）；開放窗實作 → **AI-2202e**（需 PO 拍板 schema，將打破零-migration 慣例）。
- **room_calendar.price 欄位暫留**：邏輯已全面停讀，欄位保留以符 ddl-auto=validate；`V58__Drop_Room_Calendar_Price.sql`（DROP COLUMN）另立後續低風險任務。
- **後端無 schema 變動**：US-001 為移除死碼 + 讀取簡化，US-002 純文件，沿用既有 schema，Flyway 維持 V57（連續 S42~S45 零 migration）。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 45）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 45 Plan | 9463ced | 定價區技術債收斂（2 US / 5 SP，決策型）|
| US-001 AI-2406 | f214352 | 定價機制統一——清死碼 + 停讀 room_calendar.price（BookingService 三處讀取簡化 + 決策文件）|
| US-002 AI-2202d | a040d3b | 開放窗「未開放 vs 可訂」語意評估（spike 決策文件，無 production code）|
| Sprint 45 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
