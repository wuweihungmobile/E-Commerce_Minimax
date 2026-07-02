# 整月日曆「未開放 vs 可訂」語意評估 / Calendar Open-Window Assessment

> **文件類型**: 設計評估 / 決策 spike（ADR 候選）
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **來源**: Sprint 45 US-002（AI-2202d，spike）；Sprint 43 提出、Sprint 44 Retro 續留
> **狀態**: 🔴 評估完成，待 PO 拍板；實作另立 AI-2202e（需 schema 決策）

---

## 1. 問題陳述

買家整月日曆（`GET /v2/bookings/calendar`）目前把「無 room_calendar 記錄之日」一律視為**可預訂**。但房源可能「尚未開放某些日期供預訂」（例如僅開放未來 90 天、或旺季尚未定價前不開放）。**「未開放」≠「可訂」**，但目前系統無法區分。本文件評估引入「開放窗（availability window）」語意的選項與取捨。

---

## 2. 現況（探勘結論）

**「無記錄 = 可訂」是三層一致的硬語意**（非僅顯示層）：

| 層 | 位置 | 行為 |
|----|------|------|
| Calendar | `BookingService.getCalendar` / `RoomCalendarService.getCalendarRange` | 只回「已有記錄」之日（稀疏）；無記錄之日不出現，前端補 basePrice 視為可訂 |
| Availability | `BookingService.checkAvailability` / `isDateRangeAvailable` | 空區間 `allMatch` → true；`calendars.isEmpty()` → 視為 AVAILABLE |
| Booking | `RoomCalendarService.bookDateRange` | 無記錄日**直接建 BOOKED entry 並成功** |

**room_calendar 稀疏、lazy 建立**：房源建立時**不 seed** 日曆（`RoomService.createRoom` 只建 Listing+Room）；記錄僅由 bookDateRange/blockDateRange lazy 產生。→ 新房源日曆全空，整張日曆對買家全部呈現「可訂」。

**狀態 enum**：`AVAILABLE / BOOKED / BLOCKED / MAINTENANCE`（MAINTENANCE 為死狀態，無寫入者）。**無「未開放 / CLOSED」狀態**。

**關鍵含義**：任何開放窗設計必須同時處理 **calendar 顯示 + availability 檢查 + booking 寫入**三層，否則後端仍會接受「未開放日」的預訂（顯示與可訂性不一致）。

---

## 3. 選項比較

### 選項 A：Room 層級開放窗欄位（推薦）

在 `rooms`（或 room 相關表）新增開放窗欄位，例如 `booking_window_days`（開放未來 N 天）或 `open_from_date` / `open_until_date`。

- **Schema**：需 1 支 migration（rooms 加欄）+ `Room` entity + `RoomDto` + 前端 type。
- **行為**：getCalendar / checkAvailability / isDateRangeAvailable / bookDateRange **四處**加「超出窗 → 未開放 → 擋訂 / 標記」判斷。
- **優點**：語意清楚；與稀疏模型相容（窗是規則，不需逐日 seed）；單一連續窗涵蓋最常見情境（「開放未來 N 天」）。
- **缺點**：只表達單一連續窗；「窗內挖洞」仍用既有 BLOCKED。
- **對既有房源**：需給預設值（如 `open_until_date = NULL` 表示無限制 = 維持現狀「全可訂」），**避免既有房源突然全不可訂**。

### 選項 B：room_calendar 新增 CLOSED 狀態

- **Schema**：`status` 為 `VARCHAR(20)` 無 CHECK 約束 + `@Enumerated(STRING)` → 新增 enum 值**理論上零 DB migration**（僅改 Java enum + 前端 type）。對「零 schema」慣例友善。
- **致命語意衝突**：稀疏模型下「無記錄」仍需被解讀——
  - 反轉預設（無記錄 = CLOSED）→ **既有房源（日曆全空）瞬間全部不可訂**，全面 regression。
  - Densify（逐日 seed CLOSED 列）→ 需 backfill + 未來逐日維護，與 lazy/稀疏架構背道而馳，量大。
- **結論**：單獨用 (B) **不可行 / 高風險**；可作 (A) 的補充（手動關閉個別日），但與既有 BLOCKED 幾乎重疊。

### 選項 C：純前端顯示層區分

- **Schema**：無。
- **缺點**：前端無權威訊號，只能啟發式（如「今日 +N 天後標未開放」），**且 booking 後端仍接受該日預訂** → 顯示與可訂性不一致。**不建議單獨使用**。

---

## 4. 建議

**採選項 A（Room 層級開放窗欄位）**，理由：語意權威、與稀疏模型相容、對既有房源可用 NULL 預設安全過渡。

- 欄位建議：`open_until_date DATE NULL`（開放至某日；NULL = 無限制，維持現狀）；如需「滾動開放未來 N 天」再加 `booking_window_days INT NULL`。
- 「未開放日」語意：calendar 回一個新的顯示狀態（如 `NOT_OPEN`，前端灰底但**不刪除線**以區別 BLOCKED）；availability/booking 對超窗日**擋訂**（回明確 reason）。

**與零-migration 慣例的張力（誠實揭露）**：選項 A **需 migration**（rooms 加欄），將打破 S42~S45 連續「無 schema 變動」。這是本項一直被延後的根因——它本質是**產品語意 + schema 決策**，非純技術清理。

---

## 5. 待 PO 決策事項

1. **是否引入開放窗**？（商業上是否需要「未開放」概念，或現行「全可訂 + 手動 BLOCK」已足夠？）
2. 若引入，採**選項 A**（推薦，需 migration）還是其他？
3. 開放窗語意：`open_until_date`（固定截止）或 `booking_window_days`（滾動視窗）或兩者？
4. 既有房源預設（建議 NULL = 無限制，安全過渡）。
5. 未開放日的買家呈現（新狀態 `NOT_OPEN` vs 沿用既有）。

**決策通過後** → 另立實作 US **AI-2202e**（含 migration + 四處 booking 邏輯 + 前端顯示 + E2E + 既有房源 backfill，估 5 SP）。

---

**文件版本**: v1.0｜**建立者**: SD Marcus + SA Amanda + PM Victoria + Claude Code｜**基於**: AISDLC v0.09
