# Sprint 255 計畫：修復 DEF-360（markMaintenance/blockDateRange 對全新房源靜默空操作）

## 1. 起點與範圍

Sprint 254 收尾以 `AskUserQuestion` 提出 DEF-358（4 個路由真缺口）與 DEF-360（修法方向）請使用者決策。使用者選擇：
- DEF-358：4 項全做（排入 Sprint 256 起依序處理，依「由小到大、依耦合度排列」的順序：部落格分類篩選頁 → 個人資料編輯表單 → 管理台總覽首頁 → 跨店鋪商品/房源列表）
- DEF-360：**UPSERT 語意**（推薦選項）

本輪（Sprint 255）處理 DEF-360。

## 2. 讀碼確認範圍

`RoomCalendarService` 內「僅處理已存在的 calendar 記錄，不建立缺漏日期的記錄」這個既有慣例實際涵蓋 3 個方法：`markMaintenance`、`blockDateRange`、`unblockDateRange`。但逐一分析每個方法「缺列時維持現狀」的行為是否真的是 bug：

- **`markMaintenance`**：缺列 = 隱含 AVAILABLE，呼叫端期望的目標狀態是 MAINTENANCE。兩者不同 → 空操作導致**假成功**（回 200 但房源實際仍可訂）。**真缺陷**。
- **`blockDateRange`**：缺列 = 隱含 AVAILABLE，目標狀態是 BLOCKED。同理，空操作導致**假成功**。**真缺陷**。
- **`unblockDateRange`**：缺列 = 隱含 AVAILABLE，目標狀態（unblock 後）也是 AVAILABLE。兩者相同 → 空操作的結果**已經正確**，不是缺陷，只是沒有把隱含狀態寫成顯式記錄。
- （`unmarkMaintenance`/`releaseDateRange` 同理：目標狀態與缺列的隱含狀態一致，不需補列。這兩者原本就不在 DEF-360 的「同一慣例」描述範圍內，`markMaintenance`/`unmarkMaintenance` 已有獨立的 `lockCalendarRange` 有鎖版本。）

**決策**：只對 `markMaintenance`、`blockDateRange` 套用 UPSERT（建立缺列記錄）；`unblockDateRange` 維持現狀不變。這與使用者核准的選項描述（「三個方法改為缺列時先建立再更新」）在**實作範圍**上有一處技術性縮小，原因已如上誠實說明——`unblockDateRange` 補列是沒有行為意義的額外寫入，不是拒絕使用者的決定，是找到更精準的落地方式。

## 3. 實作

`backend/src/main/java/com/nextkey/ecommerce/core/booking/RoomCalendarService.java`：

1. 新增私有方法 `fillMissingCalendarEntries(roomListingId, checkIn, checkOut, existing)`：接收呼叫端已用自己既有方式（有鎖／無鎖）查到的既有記錄列表，比對完整日期範圍，對缺漏的日期以既有 `createCalendarEntry` 建立（預設 AVAILABLE）。並發建立衝突（`DataIntegrityViolationException`）時改用單日有鎖查詢讀取對方剛建立的記錄，與既有 `bookDateRange` 的衝突處理方式一致。
2. `markMaintenance`：`lockCalendarRange(...)` 的回傳結果傳入 `fillMissingCalendarEntries` 補齊後才進入既有迴圈。
3. `blockDateRange`：`findByListingIdAndCalendarDateBetween(...)`（原無鎖版本，**不改變其鎖定特性**，鎖定不一致問題另登記 DEF-363 不在本輪處理）的回傳結果同樣傳入 `fillMissingCalendarEntries`。
4. 移除死碼常數 `CALENDAR_GENERATE_DAYS = 365`（`@SuppressWarnings("unused")`，代表已被否決的「預先生成」方向，其存在前提不再成立，且本就零呼叫點，與本次決策直接相關，非無關清理）。
5. 更新 `markMaintenance`/`blockDateRange` 的 Javadoc，移除已過期的「僅處理已存在記錄」描述，補上 DEF-360 修復說明。

**讀碼時發現的側向問題（另登記 DEF-363，不在本輪處理）**：`blockDateRange`/`unblockDateRange` 呼叫的是無鎖版本查詢，而 `markMaintenance`/`unmarkMaintenance`/`releaseDateRange` 早已改用 `FOR UPDATE NOWAIT` 有鎖版本（DEF-134/135/136）。兩者讀-改-寫結構相同，理論上有同類併發遺失更新風險，但未經實測驗證是否可重現，不擅自擴大本輪範圍去修復一個尚未證實存在的問題。

## 4. 測試

`backend/src/test/java/com/nextkey/ecommerce/core/booking/RoomCalendarServiceTest.java`：

- 新增 4 案例：`markMaintenance_freshRoomNoCalendarRecords_createsEntriesAndSetsMaintenance`（全新房源，DEF-360 原始回歸情境）、`markMaintenance_partialCalendarRecords_fillsOnlyMissingDates`（部分缺列，驗證合併邏輯不誤動既有記錄）、`markMaintenance_concurrentCreationConflict_recoversExistingEntry`（建立衝突恢復路徑）、`blockDateRange_freshRoomNoCalendarRecords_createsEntriesAndSetsBlocked`（`blockDateRange` 同等情境）。
- 修正 5 個既有案例（`blockDateRange_setsAllToBlocked`、`markMaintenance_availableDates_setsMaintenanceWithoutTouchingBooking`、`markMaintenance_bookedDate_preservesBookingIdAndFlagsBooking`、`markMaintenance_alreadyMaintenance_idempotentSkip`、`markMaintenance_sameTenantListing_passesAuthorization`）：這 5 個案例的 mock 原本對 2 天的日期範圍只提供 1 天的既有記錄（第 2 天的缺口在修復前的程式碼下會被既有邏輯直接忽略，故未被察覺），新邏輯會對缺口觸發補建流程而需要額外 mock（`listingRepository`/`saveAndFlush`），為保持這些案例原本「測試既有記錄行為」的意圖不被稀釋，改為補齊第 2 天的既有記錄而非額外 mock 補建路徑，不影響原始驗證目的。
- 突變驗證：`git diff`/`git checkout`/`git apply` 暫時還原 `RoomCalendarService.java` 至修復前版本，重跑整個測試檔案，確認恰好新增的 4 個案例轉紅（`Tests run: 31, Failures: 4`），其餘 27 個案例（含修正過 mock 的 5 個）維持綠燈，證實這 5 個案例確實仍在測試「既有記錄」行為、未意外依賴新邏輯；還原修復後重跑確認 31/31 恢復綠燈。
- 前端：`at-embed-card-status.spec.ts` 的 E2E-EMBED-02 移除 `test.fail()` 標記（DEF-360 已知缺口已修復，不再需要標記迴避；該規格原本模擬真實流程直接驗證 `POST .../maintenance` 端到端行為，與 `PostControllerE2ETest` 直接寫 DB 測試卡片渲染邏輯是不同層次，兩者都保留不衝突）。

## 5. 全量回歸結果

`mvn -o clean verify`（真實 PostgreSQL／Redis）**BUILD SUCCESS**：單元 **2212**（+4）／整合 **832**（持平）／**0 失敗**；checkstyle（main+test）**0 違規**；PMD 通過。

突變驗證：`git diff`/`git checkout`/`git apply` 暫時還原 `RoomCalendarService.java`，重跑 `RoomCalendarServiceTest`：`Tests run: 31, Failures: 4`，恰好是新增的 4 個 DEF-360 案例轉紅（`markMaintenance_freshRoomNoCalendarRecords_createsEntriesAndSetsMaintenance`／`markMaintenance_partialCalendarRecords_fillsOnlyMissingDates`／`markMaintenance_concurrentCreationConflict_recoversExistingEntry`／`blockDateRange_freshRoomNoCalendarRecords_createsEntriesAndSetsBlocked`），其餘 27 個（含修正過 mock 缺口的 5 個既有案例）維持綠燈；還原修復後重跑恢復 31/31。

`make validate-e2e`（host JAR＋npm build＋Playwright，commit 前先行驗證）：schema 對齊無漂移，**154 passed／4 skipped／0 failed**（6.6 分）。特別核對 `at-embed-card-status.spec.ts` 的 **E2E-EMBED-02**（原標記 `test.fail()` 的 DEF-360 已知缺口案例）：移除標記後確實正常通過，非僅單元測試層級的理論驗證——真實後端、真實資料庫、真實瀏覽器端到端證實 `markMaintenance` 對全新房源不再靜默空操作。
