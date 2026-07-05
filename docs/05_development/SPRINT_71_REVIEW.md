# Sprint 71 Review / Sprint 71 評審會議

> **Sprint 編號**: Sprint 71
> **期間**: 2026-07-06（恢復例行測試強化排程）
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 多 Sprint 測試強化計劃（恢復例行排程）——`BookingService`（createBooking/updateBooking 日期變更）+ `RoomCalendarService`（訂房核心，含 idempotency）單元測試強化

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `BookingService`（createBooking/updateBooking 日期變更）+ `RoomCalendarService`（idempotency 核心）單元測試強化 | 8 | ✅ 完成 |

**8 SP 全數完成**。安全缺口已於 Sprint 68/70 清零，本 Sprint 依 `SPRINT_70_RETRO.md` 建議恢復例行測試強化排程。

---

## 2. 前置盤點與誠實揭露（本 Sprint 起手式）

動手前先完整盤點 `BookingService`（813 行，9 個 public 方法）與 `RoomCalendarService`（328 行，9 個 public 方法）的既有覆蓋現況，避免重複 Sprint 68 已完成的工作：

- **`BookingService` 並非零測試**：Sprint 68（`DEF-023`）已建立 `BookingServiceOwnershipTest.java`（10 個測試，涵蓋 `getBooking`/`updateBooking`/`cancelBooking` 擁有權檢查），另有 `BookingServiceDynamicPricingTest`（4）、`BookingServiceOpenWindowTest`（4）、`BookingServiceRoomTitleTest`（2）分別聚焦特定切面。
- **真正的覆蓋缺口**：`createBooking`（訂房建立流程，含 Redis 鎖並發控制）與 `updateBooking` 的日期變更（reschedule）業務邏輯完全零單元測試——先前僅由 `BookingIntegrationTest`/`BookingControllerE2ETest`（需完整 Spring Context + 真實 PostgreSQL + Redis）間接涵蓋。
- **`RoomCalendarService` 確認為真正的零覆蓋**：先前完全沒有對應的單元測試檔案，含 `bookDateRange` 的 idempotency 核心邏輯（同 bookingId 重複呼叫應跳過、不同 bookingId 衝突應擋）從未被任何純 Mockito 測試驗證過。

本 Sprint 精準聚焦這兩塊真正的缺口，不重複造 Sprint 68 已完成的擁有權測試。

---

## 3. 交付內容

### 生產程式碼修改

- **無**。本 Sprint 純測試補強，未修改 `BookingService.java`/`RoomCalendarService.java` 或任何其他生產程式碼。

### 新增測試

- **`RoomCalendarServiceTest.java`**（新檔，17 個測試）：
  - `bookDateRange`（idempotency 核心）6 個：日期無記錄建立新條目、unique constraint 並發衝突（E_4001）、**同一 bookingId 重複呼叫 idempotent 跳過（不重複 save）**、不同 bookingId 佔用衝突（E_4001）、既有非 BOOKED 記錄更新為 BOOKED、悲觀鎖失敗（E_4001）。
  - `isDateRangeAvailable` 4 個：無記錄視為可訂、皆 AVAILABLE 可訂、任一 BOOKED 不可訂、悲觀鎖失敗視為不可用。
  - `releaseDateRange` 2 個、`blockDateRange`/`unblockDateRange` 2 個：狀態轉換僅動到符合條件的日期。
  - `lockDateRangeNoWait`/`unlockDateRange` 3 個：全部取鎖成功回傳統一 key、部分取鎖失敗釋放已取得的鎖（避免鎖洩漏）、unlock 釋放所有日期鎖與統一 key。
- **`BookingServiceCreateBookingTest.java`**（新檔，12 個測試）：正常路徑 1 個（鎖定→可用性二次確認→寫入→更新日曆→finally 釋放鎖）；前置驗證錯誤路徑 7 個（房源不存在/非 ROOM/未上架/Room 資料不存在/人數超限/日期不合法/超出開放窗）；鎖定/並發控制 4 個（取鎖失敗不誤呼叫 unlock、取鎖後可用性二次確認失敗仍 unlock、使用者/租戶不存在仍 unlock）。
- **`BookingServiceUpdateDateChangeTest.java`**（新檔，5 個測試）：日期未變更不觸碰日曆、日期變更成功完整流程（釋放舊→鎖定新→確認可用→更新→重新計價）、新鎖定失敗、新日期超出開放窗、新日期不可用（後三者皆驗證 finally 釋放鎖的健壯性）。
- 三檔合計新增 **34 個單元測試**。

### 技術債記錄

- **`DEF-025`（🟢 低優先級，已記錄）**：撰寫 `BookingServiceCreateBookingTest` 時發現 `BookingService.createBooking(request, idempotencyKey)` 的 `idempotencyKey` 參數在方法本體內完全未被引用——真正的 idempotency 由 `BookingController.createBooking` 於呼叫前經 `IdempotencyService`（Redis-backed）把關並快取回應。此為死碼參數，非安全缺口（機制本身正確運作）。已回報使用者，**使用者審閱後明確決定此為低優先級技術債，記錄至 `DEFERRED_ITEMS_TRACKER.md` 即可，不需在本 Sprint 或近期排程清理**。

### 文件

- **`SPRINT_71_PLAN.md`**（新檔）：本 Sprint 計劃，含前置覆蓋現況盤點、User Story/AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-025`（🟢 低優先級）+ Sprint 71 歷史紀錄條目。
- **`RELEASE_TRACKER.md`**：新增 Sprint 71 列。

---

## 4. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 每完成一個測試檔案立即編譯 + 執行該檔驗證通過，才撰寫下一檔，未累積（`RoomCalendarServiceTest` → `BookingServiceCreateBookingTest` → `BookingServiceUpdateDateChangeTest` 依序） |
| `RoomCalendarServiceTest` 單獨執行 | ✅ 17 tests，0 fail |
| `BookingServiceCreateBookingTest` 單獨執行 | ✅ 12 tests，0 fail（首版一個斷言因 `Booking.roomListingId` 為 `insertable=false/updatable=false` 影子欄位、純 mock 情境不會自動回填而失敗，已改用 checkIn/checkOut/totalAmount + `argThat` 驗證關聯房源，修正後通過） |
| `BookingServiceUpdateDateChangeTest` 單獨執行 | ✅ 5 tests，0 fail |
| `booking` package 整體（既有 20 + 新增 34） | ✅ 54 tests，0 fail |
| 後端單元回歸（`mvn test`） | ✅ **640 tests，0 fail，BUILD SUCCESS**（含本 Sprint 新增 34 個）。首次執行因本機測試 DB 未啟動（`make test-db-up`），既有 `SellerDashboardServiceCacheTest`（`@ActiveProfiles("integration-test")` + `@SpringBootTest`，與本 Sprint 變更無關）3 個測試因 `ApplicationContext` 載入失敗而報錯；執行 `make test-db-up` 後重跑，640 tests 全數 0 fail，確認為既有環境前置條件缺失，非本 Sprint 迴歸 |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0 |
| 驗證方式選擇 | 依 2026-07-05 使用者確認之全量回歸頻率政策：本 Sprint 純補測試、未修改生產程式碼，僅需 `mvn test`（純 Mockito 單元測試），不需執行全量 `mvn verify -Pintegration-test` |

---

## 5. 誠實揭露（Rule 12）

1. **`mvn test` 首次執行失敗為環境問題，非迴歸**：見上方驗證結果，已排查根因並記錄避免誤判為本 Sprint 造成的迴歸。
2. **`DEF-025` 為死碼參數，非安全缺口**：`idempotencyKey` 參數未使用不影響系統正確性（Controller 層機制正確運作，`BookingControllerE2ETest` 已有 `createBooking_duplicateIdempotencyKey_returnsCachedResponse` 等測試涵蓋），純粹是程式碼清潔度考量，已交由使用者決策並記錄為低優先級技術債。
3. **發現既有 `RELEASE_TRACKER.md` 的 Release 總覽表缺少 Sprint 68/69/70 列**（非本 Sprint 造成，屬前序 Sprint 遺漏）：本 Sprint 僅新增 Sprint 71 自己的列，未回頭補齊 68-70（超出本 Sprint「BookingService/RoomCalendarService 測試強化」範圍，屬另一項獨立的文件維護技術債，留待使用者決定是否另立任務補齊）。

---

## 6. Demo 重點

- **`RoomCalendarService` 從零覆蓋到 17 個測試**：訂房核心的 idempotency 邏輯（同 bookingId 重複呼叫安全跳過、不同 bookingId 衝突正確擋下）首次獲得單元測試保護。
- **`createBooking`/`updateBooking` 日期變更流程從純 E2E 間接涵蓋到直接單元測試**：Redis 鎖定/釋放的 `finally` 健壯性（無論成功或失敗皆正確釋放鎖，避免並發鎖洩漏）獲得明確驗證。
- **精準聚焦真正缺口**：動手前完整盤點既有覆蓋，避免與 Sprint 68 `BookingServiceOwnershipTest` 重工。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
