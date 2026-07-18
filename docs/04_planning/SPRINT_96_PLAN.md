# Sprint 96 Plan — M17 MAINTENANCE 狀態工作流程與 Admin MaintenanceWarnings

**Sprint**: Sprint 96
**日期**: 2026-07-19
**主題**: PRD §5.5.3/§18.9.4 明列的 `room_calendar.MAINTENANCE` 狀態工作流程（房東標記/解除維護、Booking under_maintenance 標記、Admin Dashboard 緊急警告列表），重新全面比對 PRD 全文後發現的第四個落地缺口，96 個 Sprint 以來從未實作。

---

## 1. 缺口盤點結果（背景）

比照 Sprint 93-95 的方法論，先做「孤兒錯誤碼」分析（`ErrorCode.java` 定義但全庫從未 `throw` 的常數），再逐段核對 PRD 全文交叉比對程式碼。找到：

- **PRD §5.5.3**：`room_calendar.status` 的 `MAINTENANCE` 完整工作流程——狀態轉換矩陣（AVAILABLE/BLOCKED/BOOKED → MAINTENANCE 皆合法；MAINTENANCE → BOOKED 需先轉 AVAILABLE）、`booking_id` 保留、`bookings.status_flags.under_maintenance` 標記、M09 通知系統上線前的 Admin Dashboard 替代方案。
- **PRD §18.9.4 TC-LO2-M17-003**：Admin Dashboard `MaintenanceWarnings` 列表需顯示 Booking ID、入住日期、房客 Email，入住日期 24 小時內需紅色緊急標記。

程式碼證據：`RoomCalendar.RoomCalendarStatus.MAINTENANCE`、`Booking.statusFlags`（JSONB）欄位皆已存在（Schema/Entity 早已就緒），但 `RoomCalendarService` 只有 `blockDateRange`/`unblockDateRange`（對應 BLOCKED），完全沒有任何方法可轉換到/解除 MAINTENANCE；`AdminController` 沒有任何 `maintenance-warnings` 端點；全庫 grep `under_maintenance` 零寫入點。

**額外發現（範圍調整）**：`blockDateRange`/`unblockDateRange` 本身也是死碼（全庫零呼叫者，無任何 Controller 曾對外暴露），代表房東端目前完全沒有「日曆狀態管理」的對外 API。本 Sprint 因此需一併新增最基本的房東對外端點（`RoomCalendarController`），而非只是把邏輯塞進既有端點。

## 2. 規格落差與工程決策

1. **PRD 內部兩處敘述表面矛盾，以「狀態轉換矩陣」與正式 Test Case 為準**：§5.5.3 早期敘述段（v0.9_R02_Loop_01）說「MAINTENANCE 解除後恢復為 BOOKED」，但同章節較晚加入、明確標示 `[Specification]` 的狀態轉換矩陣（v0.9_R02_Loop_02）列出「MAINTENANCE → BOOKED：X（需先改為 AVAILABLE）」。判讀：矩陣描述的是「一般狀態轉換 API 操作」（如試圖直接呼叫 `bookDateRange` 對 MAINTENANCE 日期）不允許跳過 AVAILABLE；而「解除維護」是專屬動作，其副作用（依 `booking_id` 是否保留決定恢復為 BOOKED 或 AVAILABLE）不受矩陣約束。兩者不衝突，`unmarkMaintenance` 實作為：`bookingId != null` → 恢復 BOOKED（清除 Booking 的 `under_maintenance` 標記）；否則 → 恢復 AVAILABLE。
2. **`room_calendar.price` 設為 NULL 的規格已自動滿足**：PRD 要求 MAINTENANCE 狀態時 `price` 應為 `NULL`。程式碼庫既有決策（`docs/06_quality/PRICING_MECHANISM_UNIFICATION.md`，Sprint 45 AI-2406）已將 `room_calendar.price` 整欄位停用（恆為 NULL，動態定價一律即時計算不落庫），因此本項 PRD 規格對現行架構天然成立，無需額外程式碼。
3. **`markMaintenance`/`unmarkMaintenance` 沿用 `blockDateRange`/`unblockDateRange` 既有慣例，僅處理已存在的 calendar 記錄**：不為缺漏日期建立新記錄。此為既有姊妹方法的既定（雖不完美但一致）行為，依 Rule 11 比照辦理，不在本 Sprint 擴大修正範圍。
4. **24 小時緊急判定的精度落差**：PRD 描述以「小時」為單位，但 `Booking.checkInDate` 僅有日期粒度（無入住時刻）。採用「入住日期為今日或明日」近似 24 小時窗口，屬粒度不匹配下的合理工程折衷，非業務判斷分歧。
5. **MaintenanceWarnings 查詢實作方式**：不透過 native JSONB 查詢 `Booking.status_flags`（全庫零先例，會打破既有慣例），改為先查 `RoomCalendarRepository.findByStatusAndBookingIdIsNotNull(MAINTENANCE)` 取得受影響 `booking_id`（一次查詢即可去重多晚同一 Booking 的情況），再用 `BookingRepository.findAllById(...)` 批次取得 Booking 詳情組出回應。

## 3. 實作內容

1. **`RoomCalendarService`**：新增 `markMaintenance`/`unmarkMaintenance`，注入 `BookingRepository` 以讀寫 `Booking.statusFlags.under_maintenance`。
2. **`RoomCalendarRepository`**：新增 `findByStatusAndBookingIdIsNotNull(status)`。
3. **`RoomCalendarController`**（新檔）：`POST /v2/dashboard/rooms/{roomListingId}/maintenance`（標記）、`DELETE .../maintenance`（解除），`room:update` 權限。
4. **`BookingDto.MaintenanceRequest`**（新增巢狀 DTO）。
5. **`AdminService.getMaintenanceWarnings()`** + **`AdminController`** 新增 `GET /v2/admin/maintenance-warnings`（`SUPER_ADMIN` 權限），回傳 Booking ID、入住日期、房客 Email、`urgent` 旗標。
6. **測試**：
   - `RoomCalendarServiceTest` 新增 6 項（標記/解除維護的各狀態轉換、idempotent 略過、Booking 標記讀寫）。
   - `AdminServiceTest` 新增 4 項（緊急/非緊急判定、多晚同 Booking 去重、空清單）。
   - `M17MaintenanceWorkflowIntegrationTest`（新檔，5 項）：房東標記/解除維護的真實 HTTP 呼叫、日期範圍錯誤 400、Admin 查詢緊急警告。

## 4. 驗證結果

- 單元測試：`RoomCalendarServiceTest` +6、`AdminServiceTest` +4，皆 0 fail
- 整合測試：`M17MaintenanceWorkflowIntegrationTest` 5 tests 0 fail
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息
- `make validate-schema`：無 migration，schema-free（`RoomCalendar`/`Booking` 欄位早已存在，只是首次被賦值）

## 5. 範圍外（延後）

- `blockDateRange`/`unblockDateRange` 對外端點補齊——雖然本 Sprint 發現兩者也是死碼，但這不在本輪 PRD 缺口範圍內（PRD 未對 BLOCKED 狀態要求獨立 Admin/Host 工作流程規格），留待未來需求明確時處理。
- 每日凌晨 03:00 `room_calendar.price` 全量重算排程（PRD §5.5.2，與本 Sprint 無關，Sprint 95 已記錄為範圍外）。
- M09 通知系統（Phase 2）上線後，`MaintenanceWarnings` 應改為主動推播通知取代人工查看，屬於通知系統模組本身的後續工作。
