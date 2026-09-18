# Sprint 174 Plan — BookingService.updateBooking 未驗證合併後日期範圍，無效區間落到未攔截的 IllegalArgumentException（DEF-226）

**Sprint**: Sprint 174
**日期**: 2026-09-18

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 於 Sprint 173 結束時已無待排程項目（`DEF-225` 已於 Sprint 173 結案）。延伸 Sprint 173 §8 誠實揭露的觀察方向——「本輪未擴大檢查其他 Service 是否有類似『create 有驗證、update 沒有』不對稱缺口」——本輪對全庫 Service 做系統性掃描：先列出所有含日期先後比較邏輯（`isBefore`/`isAfter`）的 Service（`OrderService`/`RoomCalendarService`/`BookingService`/`AdminService`/`RedisCartService`/`PricingService`/`AnalyticsService`），逐一檢查是否有對應的 `update` 方法且缺少與 `create` 相同的日期驗證。查證 `BookingService` 時發現實質缺口。

## 2. 缺口說明

`BookingService.createBooking`（經 `resolveBookableRoom`，`BookingService.java:440`）驗證 `request.getCheckOutDate().isBefore(request.getCheckInDate())`，不符合則拋出 `ErrorCode.E_4003`「Check-out must be after check-in」。但 `updateBooking`（`BookingService.java:591`）的 `handleDateChange`（`BookingService.java:621`）只是把請求欄位與既有值合併成 `newCheckIn`/`newCheckOut`，從未檢查兩者順序是否仍有效，就直接呼叫 `processDateRangeChange` 進行「釋放舊日期 → 鎖定新日期」。

具體攻擊面：`BookingDto.UpdateRequest` 的 `checkInDate`/`checkOutDate` 皆為獨立可空欄位（partial update），呼叫端只需送單一欄位就能造出無效狀態——例如既有訂房 `checkIn=2026-XX-10, checkOut=2026-XX-12`，只送 `checkOutDate=2026-XX-05`（不動 `checkInDate`）即可讓合併後的區間變成 `checkIn > checkOut`。

後果查證：`processDateRangeChange` 呼叫 `roomCalendarService.lockDateRange(roomListingId, newCheckIn, newCheckOut)`（`RoomCalendarService.java:88`），內部呼叫 `generateDateRange(checkIn, checkOut.minusDays(1))`（`RoomCalendarService.java:397`），實作為 `start.datesUntil(end.plusDays(1))`。用 jshell 實測確認：當 `start`（即 `checkIn`）晚於 `end.plusDays(1)`（即 `checkOut`）時，`LocalDate.datesUntil` 會拋出 `IllegalArgumentException`（`"2026-10-05 < 2026-10-10"` 這類訊息）。`GlobalExceptionHandler` 沒有對 `IllegalArgumentException` 的專屬 handler（Sprint 161 §4 已拍板刻意不做，理由是該例外可能源自非日期驗證的其他程式邏輯，全域攔截有掩蓋真正錯誤的風險），落入 catch-all `@ExceptionHandler(Exception.class)` → 裸露的 500（`E-9900`）。與 `createBooking` 對完全相同輸入回應的乾淨 `E_4003`/400 形成明顯不對稱。

`@Transactional` 有掛在 `updateBooking` 上，`releaseDateRange` 對舊日期的 DB 寫入會隨例外回滾，不會造成資料損毀；`lockDateRange` 的例外發生在 `generateDateRange` 這一步、早於任何 Redis 鎖的實際取得，故無鎖洩漏疑慮。純粹是「本該乾淨拒絕的輸入，卻裸露成未分類的 500」的錯誤體驗與一致性缺口，與 Sprint 161~166、169、170 已確立的「未攔截例外」家族同型。

## 3. 修法決策

比照 `createBooking`（`resolveBookableRoom`）既有的驗證語意與訊息文字，在 `handleDateChange` 合併請求欄位與既有值之後（涵蓋「只改一邊」的部分更新情境）、呼叫 `processDateRangeChange` 之前，檢查合併後的 `newCheckIn`/`newCheckOut` 是否仍滿足順序，違反則拋出**與 `createBooking` 相同的 `ErrorCode.E_4003`**（訊息："Check-out must be after check-in"）——同一條業務規則在 create/update 兩個入口的一致實作，不新增錯誤碼，比照 Rule 11（配合既有慣例）與 `DEF-225` 的修法決策同一模式。

## 4. 修復內容

- `BookingService.java:621-636`（`handleDateChange`）：在計算出 `newCheckIn`/`newCheckOut` 之後、判斷是否變更之前，新增：
  ```java
  if (newCheckOut.isBefore(newCheckIn)) {
      throw new BusinessException(ErrorCode.E_4003, "Check-out must be after check-in");
  }
  ```
  檢查對象是合併後的最終日期（`newCheckIn`/`newCheckOut`），而非請求 DTO 的原始欄位——確保「只改一邊」的部分更新也能被攔截到最終狀態的不一致，且在觸碰 `roomCalendarService`（`releaseDateRange`/`lockDateRange`）之前就攔下。

## 5. 測試

- **紅燈先行**：`git stash push -- BookingService.java` 只暫存生產程式碼修復（測試檔案不暫存），對修復前程式碼執行新測試：
  - `updateBooking_mergedDateRangeInvalid_throwsE4003WithoutTouchingCalendar`（`BookingServiceUpdateDateChangeTest`）：既有訂房 `checkIn=OLD_CHECK_IN/checkOut=OLD_CHECK_OUT`，只送 `checkOutDate=OLD_CHECK_IN.minusDays(1)`。
  - 修復前實際拋出 `BusinessException(E_4001)`（而非預期的 `E_4003`）——因為 `roomCalendarService` 是 mock，未 stub 的 `lockDateRange` 預設回傳 `null`，落入既有「新日期取鎖失敗」分支；且 `releaseDateRange`/`lockDateRange` 皆被呼叫到，證實無效區間在觸碰日曆服務前完全沒被攔截。
  - 真實生產環境（`RoomCalendarService` 非 mock）的實際行為已另以 jshell 對 `LocalDate.datesUntil()` 手動實測確認會拋 `IllegalArgumentException`（見 §2），非本測試涵蓋範圍但已查證一致。
- `git stash pop` 還原修復後重跑：測試轉綠，斷言 `errorCode == ErrorCode.E_4003` 且 `verify(roomCalendarService, never()).releaseDateRange(any(), any(), any())` / `verify(roomCalendarService, never()).lockDateRange(any(), any(), any())`。
- `mvn -o test -Dtest=BookingServiceUpdateDateChangeTest,RoomCalendarServiceTest,BookingServiceCreateBookingTest`：42 個測試（+1），0 failed，確認既有日期變更成功/取鎖失敗/開放窗/可用性四案例（皆使用不同日期輸入）不受影響。
- `mvn -o verify`（完整回歸，`make test-db-up` 真實 postgres/redis）：**1553 個單元測試（+1）+ 482 個整合測試（持平），0 failed**，`BUILD SUCCESS`；checkstyle（main+test）0 違規；PMD 無新增問題。
- **前端呼叫點查證**：`grep -rn "bookings" frontend/src/lib/api.ts` 確認 `bookings` 端點註冊表僅有 `list`/`detail`/`create`/`cancel`/`availability`/`calendar` 六個 key，**無 `update`**——`PUT /v2/bookings/{id}` 目前前端零呼叫點。但此端點掛 `@PreAuthorize("hasAuthority('booking:update')")`，是真實註冊、可被任何持有效權限呼叫端直接觸發的 REST 端點，並非內部不可達的死碼路徑（與 `DEF-169`/`DEF-170`/`DEF-200` 等「零呼叫點且無外部可達路徑」的死碼案例性質不同），與 `DEF-225` 誠實揭露的「風險來自直接呼叫 API 的其他消費端」同一性質，故不比照死碼延後不修，本輪直接修復。
- 未執行 `make validate-e2e`：本輪修改僅在既有 `updateBooking` 流程中新增一項輸入驗證，不改變任何既有成功路徑的回應格式；`make validate-release` 執行前會涵蓋完整 E2E 驗證。

## 6. 驗證結果

- 紅燈階段：見 §5，修復前拋出 `E_4001`（非預期的 `E_4003`）且會呼叫 `releaseDateRange`/`lockDateRange`，修復後轉綠。
- `mvn -o verify` 完整回歸：**1553 個單元測試（+1）+ 482 個整合測試（持平），0 failed**，checkstyle 0 違規，PMD 無新增問題。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-226`（🟢 低優先級表）：狀態直接記為「✅ 已修復（Sprint 174）」，發現與修復同輪完成，比照 `DEF-223`/`DEF-224`/`DEF-225` 同 Sprint 內發現並處理的先例。
- 🔴 **順便回填缺漏**：開工核對版本歷史區塊時發現 Sprint 173 完成時漏了同步更新「文件版本」/「最後更新」/「歷史版本」鏈（活躍項目表格的 `DEF-225` 記錄本身是完整且正確的，只有版本歷史鏈斷了一節）。已補上 `歷史版本 v2.63`（回填 Sprint 173），`文件版本` 更新為 `v2.64`（Sprint 174）。此為 [[deferred-items-tracker-drift]] 記錄過的同型漂移，非本輪新問題，僅為既有紀律在本輪開工核對時被落實。

---

## 8. 誠實揭露總結

- **全庫掃描範圍止於「含 `isBefore`/`isAfter` 日期比較邏輯的 Service」**（`grep -rln "isBefore\|isAfter"`），共 7 個檔案：`OrderService`/`RoomCalendarService`/`BookingService`/`AdminService`/`RedisCartService`/`PricingService`/`AnalyticsService`。已逐一確認 `BookingService` 有此缺口；其餘 6 個檔案本輪未逐一深入查證是否也有同型「create 驗證、update 沒有」不對稱（`OrderService`/`AdminService`/`RedisCartService`/`AnalyticsService` 觀察上多為狀態機轉換或唯讀查詢，未見明顯的 create/update 配對缺口，但未做窮盡審查）。此掃描角度本身也可能遺漏未使用 `isBefore`/`isAfter`（例如改用 `compareTo`/`equals` 或其他數值範圍而非日期）的同型缺口，若未來時間允許可另開一輪擴大掃描範圍。
- **前端零呼叫點的判斷止於 `frontend/src/lib/api.ts` 的端點註冊表**，未逐行審查是否有繞過此註冊表、直接組字串呼叫 `PUT /v2/bookings/{id}` 的呼叫路徑（例如測試輔助工具或未來可能存在的行動端程式碼），但這不影響本輪修復本身的正確性——即使前端註冊表之外還有其他呼叫路徑，修復後的行為對所有路徑一致更正確。
- **未擴大檢查 `handleDateChange` 之外，`BookingService` 是否還有其他 `update` 類方法（例如取消、狀態轉換）存在類似的驗證不對稱**：本輪聚焦於本次查證過程中具體發現的日期範圍缺口，未做該 Service 的全方法審查。
