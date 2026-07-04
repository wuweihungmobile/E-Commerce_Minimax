# Sprint 57 Review / Sprint 57 評審會議

> **Sprint 編號**: Sprint 57
> **期間**: 2027-12-19 ~ 2028-01-01
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 開放窗清除機制——新增專屬端點，讓賣家可將開放窗欄位清回無限制

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 開放窗清除機制——後端端點 + 前端觸發（AI-2202f）| 2 | ✅ 完成 |

**2 SP 全數完成**。承 Sprint 47（AI-2202e）建立開放窗語意，本 Sprint 補上賣家清除設定的能力。

---

## 2. 交付內容

- **`RoomService.clearOpenWindow(UUID listingId)`**：一次性將 `openUntilDate`/`bookingWindowDays` 皆設為 `null` 並儲存。
- **`RoomController`**：新增 `DELETE /v2/rooms/{listingId}/open-window`（`@PreAuthorize("hasAuthority('room:update')")`）。
- **前端 `RoomForm.tsx`**：新增「清除開放窗設定（恢復無限制）」按鈕——編輯既有房源時呼叫新端點，新增房源（尚未持久化）時僅清空本地表單欄位；同時修正了原本清空輸入框送出並不會清除既有值的誤導性行為（`undefined` 欄位會被 `JSON.stringify` 省略）。
- **`RoomServiceTest`（新）**：3 個單元測試（清除成功、不影響其他欄位、找不到房源拋例外）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（全量）| ✅ **515 tests，0 fail**（新增 `RoomServiceTest` 3 tests）|
| 後端整合（真實 DB，全量）| ✅ **422 tests，0 fail**（既有 API-M06-016 開放窗測試不退步）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（schema-free，無 migration）|
| 前端 TypeScript / ESLint / build | ✅ `tsc --noEmit` 0 error、`eslint` 0 error（既有警告非本次引入）、`npm run build` 0 error |
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **機制選擇為工程設計決策，未徵詢 PO**：新增專屬清除端點 vs 改 DTO 為 `Optional`/`JsonNullable` 包裝型別，兩者對賣家而言最終效果相同（皆可清除開放窗），純屬技術實作選擇，不涉及業務語意差異；依 Rule 11（配合既有慣例，全域一致的「非 null 才更新」模式）選擇風險最低的專屬端點方案，已於 Sprint Plan 記錄理由。
2. **未新增 RoomController 層級 E2E/整合測試**：調查確認本專案內 Room CRUD 端點（create/update/delete）原本就沒有專屬的 Controller 層級測試（僅有 `RoomOpenWindowTest` 這類 domain model 測試 + `BookingControllerE2ETest` 間接透過 `roomRepository` 操作），新增一整套帶認證 fixture 的 E2E 測試類別已超出本次 2 SP 範圍的比例原則；已用 Mockito 單元測試涵蓋新增的 Service 層邏輯（含錯誤情境）。
3. **前端修正順帶揭露既有缺陷**：清空輸入框送出不會清除欄位的行為，是全域「非 null 才更新」慣例在前端的鏡像現象，本次一併於 `RoomForm.tsx` 修正（改為明確按鈕觸發新端點），非本 Sprint 引入的新缺陷。
4. **安全提醒**：開發過程中發現 `frontend/AGENTS.md` 內含疑似提示注入的異常指令文字，已判斷為不可信內容並忽略，未依其指示行動，已另行提醒使用者檢查該檔案來源。

---

## 5. Demo 重點

- **清除生效**：`UT-ROOM-001` 示範呼叫 `clearOpenWindow` 後兩欄位皆為 `null`。
- **不影響其他欄位**：`UT-ROOM-002` 示範清除開放窗不影響 `location` 等其他欄位。
- **找不到房源**：`UT-ROOM-003` 示範對不存在的 `listingId` 呼叫會拋出 `BusinessException`。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
