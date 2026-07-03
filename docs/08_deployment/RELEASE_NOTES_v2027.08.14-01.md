# Release Notes - v2027.08.14-01 (Sprint 47)

**發布日期**: 2027-08-14（規劃）／實作完成 2026-07-03
**發布類型**: Minor（新功能：房源開放窗語意；含 schema 變更 V58——結束連續零-migration）
**Sprint**: Sprint 47
**狀態**: ⏳ 待 push（本 Sprint 3 commit；push 債累積 S41~S47，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 47 主題：**開放窗語意實作——區分「未開放 vs 可訂」**。承 S45 決策文件（CALENDAR_OPEN_WINDOW_ASSESSMENT.md）與 PO 拍板選項 A（+ 追加滾動視窗 + 賣家設定 UI），在 `rooms` 新增 `open_until_date`（固定截止）+ `booking_window_days`（滾動未來 N 天）兩欄（皆 NULL = 無限制，既有房源安全過渡），並在 **calendar 顯示 + availability 檢查 + booking 寫入三層**一致實作「超窗日 = 未開放（NOT_OPEN）」。收掉買家端「新房源全部日期都顯示可訂、無法表達未開放」的產品缺口。**本 Sprint 因 V58 結束 S42~S46 連續零-migration（5 Sprint）。只做 ROOM。**

---

## 新功能 🚀

- **房源開放窗（AI-2202e，US-001 後端 + US-002 前端）**：賣家可為房源設定「開放預訂至某固定日」（`open_until_date`）與／或「開放未來 N 天」（`booking_window_days`，滾動）；有效開放上限取兩者**最早生效者**。超過上限的日期對買家標記為「未開放（NOT_OPEN）」：
  - **買家日曆**：未開放日灰底、禁選、**不刪除線**（區別於已訂/封鎖的刪除線）。
  - **可用性查詢**：查詢區間含未開放日 → 不可訂 + 明確原因，禁用加購。
  - **下單**：超窗建單被擋（E-3002 → 422），與顯示一致。
  - **賣家設定**：RoomForm 新增「開放預訂至」日期 +「開放未來天數」數字兩欄位（空 = 無限制）。

## 改進 / 架構 🔧

- **三層一致的開放窗判斷（AI-2202e）**：抽 `Room.resolveOpenUntil(referenceDate)` 領域方法（取最早生效上限），calendar/availability/booking 三層一律呼叫，杜絕「顯示未開放但後端仍接受訂房」的不一致。NOT_OPEN 為 `getCalendar` 依開放窗**計算補入**的顯示狀態，非持久化（不寫 room_calendar、不加 enum，避免 densify）。
- **向後相容**：兩欄皆 NULL（既有房源）時三層短路維持現狀「無 room_calendar 記錄之日 = 可訂」；migration 為 ADD COLUMN nullable、既有列自動 NULL、**backfill 免資料異動**。

## 測試 / 驗證 ✅

- **後端編譯 + checkstyle**：0 error / BUILD SUCCESS（getCalendar 抽 `appendNotOpenDays` 控 NPath）。
- **後端單元**：`RoomOpenWindowTest` 5（resolveOpenUntil 4 情境）+ `BookingServiceOpenWindowTest` 4（calendar/availability 層 + 回歸）= **9 tests 0 fail**。
- **後端整合（真實 DB）**：`BookingControllerE2ETest` 20（含新 API-M06-016 三層一致）+ `M12`×11 + `Booking`×4 + `M02Room`×3 = **38 tests 0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（V58 兩欄與 Room entity 對齊，ddl-auto=validate 對乾淨 Flyway DB 啟動成功）。
- **本地 E2E 守門（`make validate-e2e`）**：**52 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-10/11；相較 S46 50 passed +2；既有不退步）。
- **前端**：tsc 0 error；lint 0 error（本次改的 5 檔零新增 warning）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **結束連續零-migration（V58，誠實揭露 Rule 12）**：S42~S46 連續 5 Sprint 零 migration，本 Sprint 因開放窗必然需 schema（選項 A）以 V58 加 2 欄結束此紀錄；PO 已於選定主軸時知悉並接受。
- **開放窗擋訂集中在 BookingService caller 層**：`RoomCalendarService.isDateRangeAvailable`/`bookDateRange` 未直接改——其唯一呼叫者即 BookingService（createBooking/checkAvailability/reschedule），擋在 caller 以同一 `resolveOpenUntil` 判斷更精準、不需在低階日曆層載入 Room；三層一致由整合測試 API-M06-016 佐證。
- **部分更新無法清除開放窗回 NULL**：`updateRoom` 沿用「非 null 才更新」慣例（與其他欄位一致），無法送 null 把已設窗清回無限制；如需另立機制（AI-2202f）。
- **未開放原因為後端英文字串**：ListingDetail 沿用既有不可訂 UI 路徑顯示 availability 原字串（與 "is booked" 等一致）；availability reason 碼化 + i18n 另立（AI-2408）。
- **只做 ROOM**：開放窗為房源專屬，PRODUCT 不涉及。

## 資料庫遷移 🗄️

- **V58__Add_Open_Window_To_Rooms.sql**：`rooms` 加 `open_until_date DATE` + `booking_window_days INT`（皆 nullable、無 DEFAULT、`ADD COLUMN IF NOT EXISTS` 冪等）。既有列自動 NULL = 無限制，無需 backfill。Flyway V57 → **V58**。

## 內含 Commit（Sprint 47）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 47 Plan | f41d72b | 開放窗語意實作（2 US / 7 SP，選項 A + 滾動視窗 + host UI）|
| US-001 AI-2202e | fbfba4a | 後端開放窗語意三層 + migration V58 + resolveOpenUntil |
| US-002 AI-2202e | cf019da | 前端 NOT_OPEN 顯示 + RoomForm 雙欄位 + E2E-ROOM-10/11 |
| Sprint 47 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
