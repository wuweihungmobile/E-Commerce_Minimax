# Sprint 47 Review / Sprint 47 評審會議

> **Sprint 編號**: Sprint 47
> **期間**: 2027-08-01 ~ 2027-08-14
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 開放窗語意實作——區分「未開放 vs 可訂」

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端開放窗語意三層 + migration V58（AI-2202e 後端）| 4 | ✅ 完成 |
| US-002 | 前端開放窗買家顯示 + 賣家設定 + E2E（AI-2202e 前端）| 3 | ✅ 完成 |

**承諾 7 SP（US-001~002）全數完成**。承 S45 決策文件（CALENDAR_OPEN_WINDOW_ASSESSMENT.md）與 PO 拍板選項 A + 追加滾動視窗 + host UI，在 `rooms` 新增 `open_until_date` + `booking_window_days` 兩欄，於 **calendar 顯示 + availability 檢查 + booking 寫入三層**一致實作「超窗日 = 未開放（NOT_OPEN）」語意。收掉買家端「新房源全部日期都顯示可訂、無法表達未開放」的產品缺口。**本 Sprint 因 V58 結束 S42~S46 連續零-migration（5 Sprint）。**

---

## 2. 交付內容

- **US-001（後端，AI-2202e，commit `fbfba4a`）**：
  - **Schema + entity**：`V58__Add_Open_Window_To_Rooms.sql` 對 `rooms` 加 `open_until_date DATE` + `booking_window_days INT`（皆 nullable、無 DEFAULT、`ADD COLUMN IF NOT EXISTS` 冪等；既有列自動 NULL = 無限制，**backfill 免資料異動**）。`Room` entity 加兩欄；`RoomDto.Create/Update/Response` 透出；`RoomService` create/update/toResponse 接線（update 沿用部分更新慣例）。
  - **共用 helper**：`Room.resolveOpenUntil(referenceDate)` 回傳有效開放上限——取已設定約束的**最早生效者** `min(open_until_date, referenceDate + booking_window_days)`；兩者皆 NULL 回 null（無限制）。`isBeyondOpenWindow` 判定。三層一律呼叫此 helper（Rule 5：確定性轉換用程式碼），杜絕語意分歧。
  - **三層一致實作**（同一 `resolveOpenUntil`）：
    - **Calendar**（`getCalendar`）：超過開放上限之「無記錄日」補一筆 `status="NOT_OPEN"` CalendarResponse（前端把「未回傳日」當可訂，故須顯式回傳）；抽 `appendNotOpenDays` helper 控 NPath。
    - **Availability**（`checkAvailability`）：載入 room，區間含未開放日 → `available=false` + `unavailableReason`（優先於日曆已訂原因）。
    - **Booking 寫入**（`createBooking` + `reschedule.processDateRangeChange`）：超窗擋訂，丟 `E_3002`（→ 422）；抽 `assertWithinOpenWindow` helper。`RoomCalendarService` 未改（其唯一呼叫者即 BookingService，擋在 caller 層更精準）。
  - **向後相容**：兩欄皆 NULL（既有房源）時三層短路維持現狀「無記錄=可訂」。
- **US-002（前端，AI-2202e 前端，commit `cf019da`）**：
  - `booking.ts`：`RoomCalendarStatus` union 加 `'NOT_OPEN'`。
  - `MonthCalendar`：NOT_OPEN 加入禁選集合；灰底禁選但**不刪除線**（區別於 BLOCKED/已訂的刪除線）+ `data-not-open` 屬性 + 圖例文字補「未開放日」。
  - `ListingDetail`：超窗 availability 回 available=false + reason → **沿用既有不可訂 UI 路徑**（顯示原因 + 禁用加購），無需改動。
  - `room.ts` + `RoomForm`：`Room`/`Create`/`Update` 加 `openUntilDate` + `bookingWindowDays`；RoomForm 加「開放預訂至」日期 + 「開放未來天數」數字雙欄位（空 = 無限制，說明兩者取最早生效）。
  - **E2E**：新增 `E2E-ROOM-10`（日曆 NOT_OPEN 禁選 + 不刪除線）+ `E2E-ROOM-11`（availability 未開放 → 禁用加購 + 顯示原因）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 + checkstyle | ✅ 0 error / BUILD SUCCESS（getCalendar NPath 曾 480>200，抽 `appendNotOpenDays` 解決）|
| 後端單元（`RoomOpenWindowTest` 5 + `BookingServiceOpenWindowTest` 4）| ✅ **9 tests 0 fail**（resolveOpenUntil 4 情境 + calendar/availability 層 + 回歸）|
| 後端整合（真實 DB）| ✅ **38 tests 0 fail**（`BookingControllerE2ETest` 20 含新 API-M06-016 三層一致 + `M12`×11 + `Booking`×4 + `M02Room`×3）|
| **schema 漂移守門（`make validate-schema`）** | ✅ 無漂移（backend 以 ddl-auto=validate 對 Flyway 重建 DB 啟動成功，V58 兩欄與 entity 對齊）|
| 本地 E2E 守門（`make validate-e2e`）| ✅ **52 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-10/11；相較 S46 50 passed +2；既有不退步）|
| 前端 tsc / lint | ✅ tsc 0 error；lint 0 error（本次改的 5 檔零新增 warning；既有 warning 不變）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **本 Sprint 結束連續零-migration（V58，已 PO 拍板）**：S42~S46 連續 5 Sprint 零 migration，本 Sprint 因開放窗必然需 schema（選項 A），以 V58 加 2 欄結束此連續紀錄。migration 為單純 ADD COLUMN nullable、既有列 NULL=無限制、backfill 免異動，風險受控；已先 `make validate-schema` 把關（記憶教訓：本地 act 用 ddl-auto=update 抓不到 schema-validation 錯誤）。
2. **NOT_OPEN 為計算產物、非持久化**：NOT_OPEN 由 `getCalendar` 依 `open_until_date`/`booking_window_days` 計算補入，不寫 room_calendar、不加 `RoomCalendar` enum 值——避免 densify（逐日 seed）與稀疏模型衝突。
3. **開放窗擋訂集中在 BookingService caller 層**：評估文件列的四處中，`RoomCalendarService.isDateRangeAvailable`/`bookDateRange` **未直接改**——因其唯一呼叫者即 BookingService（createBooking/checkAvailability/reschedule），擋在 caller 層以同一 `resolveOpenUntil` 判斷更精準、且不需在低階日曆層載入 Room 改動相依（Rule 2/3）。三層一致性由整合測試 API-M06-016 同時斷言 availability + calendar + booking 佐證。
4. **部分更新無法清除開放窗回 NULL**：`updateRoom` 沿用專案「非 null 才更新」的部分更新慣例（Rule 11），故無法透過送 null 把已設的開放窗清回「無限制」——與既有其他欄位（location 等）行為一致。若未來需支援清除，另立機制。
5. **ListingDetail 未開放原因顯示後端原字串**：超窗 availability 的 `unavailableReason` 為後端英文字串（"Date ... is not open for booking"），前端沿用既有不可訂路徑以「此日期不可預訂（原因）」呈現——與其他不可訂原因（"is booked" 等）一致（Rule 11）。booking 建立失敗另有 E-3002 中文訊息「此房型目前未開放預訂」。
6. **只做 ROOM**：開放窗為 ROOM 房源專屬，PRODUCT 不涉及。
7. **push 債累積 S41~S47（7 Sprint）**：本 Sprint 有實質後端（schema + 三層）+ 前端變動 → push 需完整 `make validate-release`。承 S41~S46 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **未開放 vs 可訂**：展示賣家於 RoomForm 設「開放預訂至 08-31」或「開放未來 90 天」後，買家日曆超窗日呈灰底（不刪除線、禁選）、availability 查詢超窗回「未開放」、下單超窗被擋（422）——三層一致。
- **既有房源零衝擊**：未設開放窗（NULL）的房源行為完全不變（整合測試 API-M06-016 第 4 步 + 既有 38 整合測試佐證）。
- **雙約束取最早**：固定截止 `open_until_date` 與滾動 `booking_window_days` 並存時取最早生效（resolveOpenUntil 單元測試 5 情境佐證）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
