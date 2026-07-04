# Sprint 58 Review / Sprint 58 評審會議

> **Sprint 編號**: Sprint 58
> **期間**: 2028-01-02 ~ 2028-01-15
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: Availability unavailableReason 錯誤碼化——後端回傳 code，前端中文對照表顯示

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | Availability reason 碼化（AI-2408）| 2 | ✅ 完成 |

**2 SP 全數完成**。`checkAvailability` 回傳結構化 reason code 取代英文字串字面值，前端以中文對照表顯示。

---

## 2. 交付內容

- **`BookingDto.AvailabilityReasonCode`（新 enum）**：`INVALID_DATE_RANGE`、`NOT_OPEN_FOR_BOOKING`、`BOOKED`、`BLOCKED`、`MAINTENANCE`。
- **`BookingService.checkAvailability`**：三處 `unavailableReason` 賦值改為 enum `.name()`（`RoomCalendarStatus` 的 BOOKED/BLOCKED/MAINTENANCE 與新 code 同名，直接複用）。
- **前端 `ListingDetail.tsx`**：新增 `AVAILABILITY_REASON_MESSAGES` 對照表，查無對應 code 時 fallback 原樣顯示。
- **測試更新**：`BookingControllerE2ETest`（後端整合）、`BookingServiceOpenWindowTest`（後端單元）、`at-room-booking.spec.ts`（前端 E2E）皆更新為新 code 格式斷言。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（全量）| ✅ **515 tests，0 fail** |
| 後端整合（真實 DB，全量）| ✅ **422 tests，0 fail** |
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（schema-free）|
| 前端 TypeScript / ESLint / build | ✅ 皆 0 error |
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **範圍決策：不導入 i18n 框架**：確認全站無任何 i18n 依賴、純中文介面，導入完整多語系框架屬過度工程；採最小方案（code + 對照表），此為工程範圍判斷，不涉及業務語意，未徵詢 PO。
2. **只碼化 `checkAvailability` 回傳欄位**：`BookingService` 建單/改期超窗擋訂拋出的 `BusinessException(E_3002, "Room is not open for booking on ...")` 例外訊息仍為英文，不在本次範圍——那是全站所有 `BusinessException` 訊息一律英文的更大範圍問題，非本 ticket（僅針對 availability reason）應解決的範疇。
3. **開發-編譯-測試循環攔截遺漏**：全量單元測試回歸時發現 `BookingServiceOpenWindowTest.checkAvailability_beyondWindow_notAvailable`（第一輪修改時遺漏的測試）仍斷言舊字串格式 `contains("not open")`，因新 code 不含該子字串而失敗；當場定位並修正為 `isEqualTo("NOT_OPEN_FOR_BOOKING")`，重新驗證通過，未讓遺漏累積到下個 Sprint，這正是嚴格開發-編譯-測試循環的價值所在。

---

## 5. Demo 重點

- **Code 取代英文字串**：`checkAvailability` 回傳 `unavailableReason: "NOT_OPEN_FOR_BOOKING"`（取代原 `"Date 2026-08-01 is not open for booking"`）。
- **前端中文顯示**：`ListingDetail.tsx` 顯示「所選日期尚未開放預訂」而非英文原句。
- **向後相容 fallback**：對照表查無對應 code 時原樣顯示，不會空白或報錯。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
