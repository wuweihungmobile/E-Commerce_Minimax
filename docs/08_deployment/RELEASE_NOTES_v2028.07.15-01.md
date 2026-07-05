# Release Notes - v2028.07.15-01 (Sprint 71)

**發布日期**: 2028-07-15（規劃）／實作完成 2026-07-06
**發布類型**: 🧪 測試強化（schema-free；後端聚焦，無前端變動；未修改任何生產程式碼）
**Sprint**: Sprint 71（恢復例行測試強化排程）
**狀態**: ✅ 已 push

> Sprint 71 主題：**`BookingService`（createBooking/updateBooking 日期變更）+ `RoomCalendarService`（訂房核心，含 idempotency）單元測試強化**。依 `SPRINT_70_RETRO.md` 建議，安全缺口已於 Sprint 68/70 清零後恢復例行排程。

---

## 測試 / 驗證 ✅

- **前置盤點**：`BookingService` 並非零測試——Sprint 68（`DEF-023`）已建立 `BookingServiceOwnershipTest`（10 個，擁有權檢查）；但 `createBooking`（訂房建立流程）與 `updateBooking` 日期變更業務邏輯完全零單元測試（先前僅由 E2E/整合測試間接涵蓋）。`RoomCalendarService` 確認為真正的零覆蓋（先前無對應測試檔案）。
- **`RoomCalendarServiceTest`（新檔）**：17 個測試，涵蓋 `bookDateRange` 的 idempotency 核心（同 bookingId 重複呼叫跳過、不同 bookingId 衝突擋 E_4001、unique constraint/悲觀鎖並發衝突）、`isDateRangeAvailable`、`releaseDateRange`/`blockDateRange`/`unblockDateRange`、`lockDateRangeNoWait`/`unlockDateRange`（含部分取鎖失敗回滾已取得的鎖）。
- **`BookingServiceCreateBookingTest`（新檔）**：12 個測試，涵蓋 `createBooking` 正常路徑、房源/房型/人數/日期/開放窗前置驗證錯誤路徑、鎖定與並發控制（含 `finally` 區塊無論成功失敗皆釋放鎖的健壯性）。
- **`BookingServiceUpdateDateChangeTest`（新檔）**：5 個測試，涵蓋 `updateBooking` 日期未變更/變更成功/新鎖定失敗/開放窗違反/新日期不可用。
- 三檔合計新增 **34 個單元測試**。
- **後端單元回歸（`mvn test`）**：**640 tests，0 fail**（含本 Sprint 新增 34 個）。因本 Sprint 未修改生產程式碼，依既定全量回歸頻率政策，純補測試 Sprint 只需 `mvn test`，不需執行全量 `mvn verify -Pintegration-test`。
- **`make validate-schema`**：無漂移（本 Sprint 無 entity/migration 變更）。

## 技術決策 / 已知限制 ⚠️

- **`DEF-025`（🟢 低優先級技術債，已記錄不清理）**：撰寫測試時發現 `BookingService.createBooking(request, idempotencyKey)` 的 `idempotencyKey` 參數在方法本體內完全未被引用——真正的 idempotency 由 `BookingController` 於呼叫前經 `IdempotencyService`（Redis-backed）把關並快取回應，此為死碼參數而非安全缺口。使用者審閱後決定不清理，僅記錄備查。
- **無前端變動**：本次修改純屬後端測試層，不影響任何 API 契約或前端流程。

## 資料庫遷移 🗄️

- 無（schema-free；純測試新增）。

## 內含 Commit（Sprint 71）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 71 Plan | （見下方實際 commit hash）| `BookingService`/`RoomCalendarService` 測試強化計劃（1 US / 8 SP）|
| US-001 | （見下方實際 commit hash）| 新增 `RoomCalendarServiceTest`（17）+ `BookingServiceCreateBookingTest`（12）+ `BookingServiceUpdateDateChangeTest`（5），合計 34 個測試 |
| Sprint 71 收尾 | （見下方實際 commit hash）| Review / Retro / Release Notes + trackers（含 `DEF-025` 記錄）|

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
