# Sprint 47 計劃 / Sprint 47 Plan

> **Sprint 編號**: Sprint 47
> **期間**: 2027-08-01 ~ 2027-08-14 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **基於**: Sprint 45 決策文件 [CALENDAR_OPEN_WINDOW_ASSESSMENT.md](../07_design/CALENDAR_OPEN_WINDOW_ASSESSMENT.md)（AI-2202d spike）+ S47 落點探勘（BookingService / RoomCalendarService / Room entity / 前端 MonthCalendar / 測試）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2202e 開放窗語意實作（選項 A：Room 層級開放窗）**」；PO 追加拍板「**固定截止 `open_until_date` + 滾動視窗 `booking_window_days` 兩者並行**」+「賣家設定 UI 納入本 Sprint」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「AI-2202e 選項 A」+ 追加滾動視窗 + host UI | Room 加 `open_until_date DATE NULL` + `booking_window_days INT NULL`，超窗日對買家標 NOT_OPEN 並擋訂 |
| S46 狀態 | ✅ 已完成（4 commit，未 push）；活躍 DEF=0 | push 債累積 S41~S46（6 Sprint）|
| 技術現況已調查 | ✅ Explore Agent 確認「無記錄=可訂」三層落點 | getCalendar / checkAvailability + isDateRangeAvailable / bookDateRange 四處 |
| **「無記錄=可訂」為三層硬語意** | 🔴 `BookingService.getCalendar`（L197-224 稀疏 map，無記錄日不回傳）、`checkAvailability`（L107-108 空 stream allMatch→true）+ `RoomCalendarService.isDateRangeAvailable`（L55-58 無記錄日 continue）、`bookDateRange`（L191-198 無記錄日直接建 BOOKED）| 開放窗必須**同時改三層**，否則「顯示未開放但後端仍接受訂房」不一致 |
| **開放窗組合語意** | 🔴 有效開放上限 = 已設定約束的**最早者**：`min(open_until_date?, referenceDate + booking_window_days?)`；兩者皆 NULL = 無限制 | 抽共用 helper `resolveOpenUntil(Room, referenceDate)` 供三層一致取用（referenceDate：calendar 用今日、availability/booking 用下單日）|
| **schema 影響（本 Sprint 打破零-migration）** | 🔴 需 migration `V58__Add_Open_Window_To_Rooms.sql`（2 欄）| 連續 S42~S46 零 migration 於本 Sprint 結束（PO 已知悉並拍板）|
| **既有房源安全過渡** | ✅ 兩欄皆 nullable 無 DEFAULT → 既有列自動 NULL = 無限制 = 維持現狀「全可訂」| **backfill 免資料異動**（`RoomService.createRoom` 本就不 seed 日曆，稀疏模型相容）|
| **NOT_OPEN 是否需持久化** | ✅ 否——NOT_OPEN 為 `getCalendar` 依 `open_until_date` **計算產物**，非寫入 room_calendar | `RoomCalendar` enum 不需改；`CalendarResponse.status` 是 String，前端 union 加 `'NOT_OPEN'` |
| 擋訂 reason 可複用 | ✅ 前端 `booking.ts` L108 已有 `'E-3002': '此房型目前未開放預訂'` | 後端超窗擋訂回 E-3002（或評估專屬碼）|
| push 前置 | ⚠️ 本 Sprint 有實質後端（schema + 三層邏輯）+ 前端變動 → push 需完整 `make validate-release` + `make validate-schema` | 承 S41~S46 push 債 |

---

## 1. Sprint 47 目標

> **主題**: 開放窗語意實作——區分「未開放 vs 可訂」，收掉買家端「新房源全部日期都顯示可訂」的產品缺口

目前系統「無 room_calendar 記錄之日」一律視為可預訂，房源無法表達「僅開放未來某段期間預訂」。本 Sprint 依 S45 決策文件與 PO 拍板選項 A，在 `rooms` 新增 `open_until_date`（開放至某固定日）+ `booking_window_days`（開放未來 N 天，滾動）兩欄（皆 NULL = 無限制，既有房源安全過渡）；有效開放上限取兩者中**最早生效者**。並在 **calendar 顯示 + availability 檢查 + booking 寫入三層**一致實作「超窗日 = 未開放（NOT_OPEN）」語意：買家日曆超窗日灰底標記（不刪除線、禁選）、可用性查詢與下單對超窗日擋訂並回明確原因。**含 migration（V58，2 欄，打破零-migration 慣例）；賣家可於 RoomForm 設定固定截止日與滾動天數。**

---

## 2. User Stories

### US-001：後端——開放窗語意三層實作 + migration（P2）（AI-2202e 後端）

> **SP**: 4 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**: **Schema + entity**——新增 `V58__Add_Open_Window_To_Rooms.sql`：`ALTER TABLE rooms ADD COLUMN open_until_date DATE;` + `ADD COLUMN booking_window_days INT;`（皆 nullable、無 DEFAULT，冪等 DO 區塊仿 V55；既有列自動 NULL = 無限制，backfill 免異動）。`Room` entity 加 `@Column(name = "open_until_date") private LocalDate openUntilDate;` + `@Column(name = "booking_window_days") private Integer bookingWindowDays;`（roomCount 後、createdAt 前，無 `@Builder.Default` 即預設 null）。`RoomDto.Response`/`CreateRequest`/`UpdateRequest` 透出兩欄。**啟動 ddl-auto=validate 對齊無漂移。**

**AC-001-2**: **共用 helper**——`resolveOpenUntil(Room room, LocalDate referenceDate)` 回傳有效開放上限（`LocalDate`，NULL = 無限制）：取已設定約束的**最早者** `min(open_until_date?, referenceDate + booking_window_days?)`；兩者皆 NULL 回 null。三層一律呼叫此 helper，避免語意分歧（Rule 5：確定性轉換用程式碼）。

**AC-001-3**: **Calendar 層**（`BookingService.getCalendar`）——以 `resolveOpenUntil(room, today)` 求上限；超過上限之日（且原無記錄）補一筆 `status="NOT_OPEN"` 的 `CalendarResponse`（因前端把「未回傳日」當可訂）；上限為 null 維持現狀（不補）；已有記錄之日（BOOKED/BLOCKED）語意不變。窗內無記錄日仍不補（維持稀疏 + 前端 basePrice）。

**AC-001-4**: **Availability 層**（`BookingService.checkAvailability` + `RoomCalendarService.isDateRangeAvailable`）——以 `resolveOpenUntil(room, bookingDate)` 求上限；查詢區間含超上限日 → `available=false`，`unavailableReason` 明確（如 `"Date ... is not open for booking"`）；`isDateRangeAvailable` 對超上限日 `return false`。上限為 null 維持現狀。

**AC-001-5**: **Booking 寫入層**（`BookingService.createBooking` / `RoomCalendarService.bookDateRange`）——下單區間含超上限日 → 擋訂，丟 BusinessException 回 `E-3002`（前端已有訊息「此房型目前未開放預訂」）或評估專屬碼；**擋在 service 層**（createBooking L346-351 前置檢查），確保與 availability 一致。上限為 null 維持現狀。

**AC-001-6**: 新增 / 複查測試（單元 Mockito + 真 DB 整合）：
- 單元：`resolveOpenUntil` 三情境（僅 open_until_date / 僅 booking_window_days / 兩者取最早 / 皆 NULL）；getCalendar 超窗補 NOT_OPEN、isDateRangeAvailable 超窗 false、createBooking 超窗擋訂（丟 E-3002）；兩欄皆 NULL 時三層維持現狀（回歸保護）。
- 整合（`BookingControllerE2ETest`，JdbcTemplate `UPDATE rooms SET open_until_date=? / booking_window_days=?`）：availability 超窗回不可訂 + calendar 回 NOT_OPEN + 建單超窗回 4xx；NULL（既有房源）不退步。

**AC-001-7**: 現有 booking/calendar/availability 測試全數不退步（`make test-db-up` 整合綠）；catch(Exception)=0、@Deprecated=0。

### US-002：前端——開放窗買家顯示 + 賣家設定 + E2E（P2）（AI-2202e 前端）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: `booking.ts` `RoomCalendarStatus` union 加 `'NOT_OPEN'`（L86）；`CalendarDay` 沿用 `status` 欄位。

**AC-002-2**: `MonthCalendar` 對 `NOT_OPEN` 日呈現「灰底 + 禁選 + **不刪除線**」（區別於 BLOCKED 的刪除線；評估 §4）；加入不可選集合但另給 class；更新底部圖例文字（新增「未開放」說明）。保留 `calendar-day-{date}` / `calendar-price-{date}` testid。

**AC-002-3**: `ListingDetail` availability 查詢遇超窗（後端回 available=false + E-3002/reason）顯示「未開放預訂」訊息並禁用加購（沿用既有不可訂 UI 路徑）。

**AC-002-4**: **賣家設定**——`room.ts` `Room`/`CreateRoomRequest`/`UpdateRoomRequest` 加 `openUntilDate?: string | null` + `bookingWindowDays?: number | null`；`RoomForm` 加「開放預訂至（選填）」日期欄位 + 「開放未來天數（選填）」數字欄位（皆空 = 無限制；並列說明「兩者取最早生效」）。

**AC-002-5**: E2E——`at-room-booking.spec.ts` 新增 NOT_OPEN 變體（calendar mock 回 `status:'NOT_OPEN'` → 斷言禁選 + 無刪除線 class；availability mock 回 available=false + reason → 斷言未開放訊息 + 禁用加購）；既有案例不退步。

**AC-002-6**: 前端 `tsc` / `build`（Turbopack）/ `lint` 0 error；`make validate-e2e` 綠（含新增 NOT_OPEN E2E）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 後端開放窗語意三層（含 resolveOpenUntil helper）+ migration V58（2 欄）（AI-2202e 後端）| 4 | P2 |
| US-002 | 前端開放窗買家顯示 + 賣家設定（2 欄）+ E2E（AI-2202e 前端）| 3 | P2 |
| **承諾合計** | | **7 SP** | |

> **Velocity 參考**：S42=7, S43=10, S44=8, S45=5, S46=8。**本 Sprint 7 SP**，健康區間。相較初估 5 SP，因 PO 追加滾動視窗（`booking_window_days` + resolveOpenUntil 組合邏輯 + 對應測試）與雙欄 host UI，各 US +1 SP。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 後端（V58 migration【2 欄】→ make validate-schema 確認對齊 → Room entity/DTO → resolveOpenUntil helper
   → 三層邏輯逐層呼叫 helper 加窗檢查 → 每層立即 mvn 編譯 + 單元測試 → test-db-up 跑整合測試不退步 + 超窗案例）
   ↓ 後端綠（三層一致 + 雙欄組合 + NULL 回歸保護）
US-002 前端（booking.ts type → MonthCalendar NOT_OPEN 樣式 → ListingDetail 未開放訊息 → RoomForm 雙設定欄位
   → E2E NOT_OPEN 變體 → tsc/build/lint）
   ↓ 前端綠
make validate-schema + make validate-e2e（schema 對齊 + 全棧：既有不退步 + NOT_OPEN 新案例）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：US-001 **先落 V58 migration 並 `make validate-schema` 確認 entity↔schema 對齊**（記憶 [[local-ci-cannot-catch-schema-validation]]：本地 act 用 ddl-auto=update 抓不到 schema-validation 錯誤，改 entity 前須手動 validate），再逐層加窗檢查、每層立即編譯 + 測試；三層改完**立即**跑 booking/calendar/availability 整合測試（`make test-db-up`）確認 NULL 房源不退步 + 超窗生效，才進 US-002。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **打破 S42~S46 零-migration 慣例** | PO 已拍板選項 A（需 schema）；migration 為單純 ADD COLUMN nullable、既有列 NULL=無限制、backfill 免異動；先 `make validate-schema` 把關 |
| 三層不一致（顯示未開放但後端接受訂房）| 三層（calendar/availability/booking）同一 `open_until_date` 判斷；整合測試同時斷言三層；createBooking 前置擋訂為第二道 |
| 既有房源突然全不可訂（regression）| `open_until_date` 預設 NULL = 無限制；三層對 NULL 短路維持現狀；新增 NULL 回歸保護測試 |
| local act 抓不到 schema-validation 錯誤 | 記憶 [[local-ci-cannot-catch-schema-validation]]：改 entity/migration 後**手動** `make validate-schema`（ddl-auto=validate 對乾淨 Flyway DB）|
| NOT_OPEN 與 BLOCKED 視覺混淆 | NOT_OPEN 灰底不刪除線、BLOCKED 刪除線；圖例文字區分；E2E 斷言 class 差異 |
| 雙欄組合語意不清（open_until_date vs booking_window_days 衝突）| 統一「取最早生效者」`min(...)`，抽 `resolveOpenUntil` helper 單一真相源；helper 單元測試涵蓋僅其一 / 兩者 / 皆 NULL；RoomForm 說明「兩者取最早」|
| 滾動視窗 referenceDate 基準不一（calendar 今日 vs booking 下單日）| helper 收 referenceDate 參數，各層明確傳入（calendar=今日、availability/booking=下單日）；整合測試分別覆蓋 |
| 有實質前後端 + schema 變動 → push 需完整 validate-release | 承 S41~S46 push 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001：V58 migration（ADD COLUMN nullable × 2）+ Room entity/DTO（openUntilDate + bookingWindowDays）；`resolveOpenUntil` helper（取最早生效）；三層一致實作（getCalendar 補 NOT_OPEN、availability+isDateRangeAvailable 超窗擋、createBooking 超窗擋訂 E-3002）；雙欄皆 NULL 維持現狀；單元（helper 4 情境）+ 真 DB 整合（超窗 + NULL 回歸）；`make validate-schema` 無漂移；既有不退步
- [ ] US-002：booking.ts NOT_OPEN type；MonthCalendar 灰底不刪除線禁選 + 圖例；ListingDetail 未開放訊息；RoomForm 開放窗雙設定欄位（截止日 + 未來天數）；E2E NOT_OPEN 變體；tsc/build/lint 0 error
- [ ] `make validate-schema` + `make validate-e2e` 綠、既有不退步；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 47 Review / Retro / Release Notes + trackers 更新
- [ ]（檢查點）承 S41~S46 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V58__Add_Open_Window_To_Rooms.sql`（2 欄）|
| 後端 entity/DTO | `backend/.../domain/model/room/Room.java`、`api/dto/RoomDto.java`、`api/dto/BookingDto.java` |
| 後端三層邏輯 | `backend/.../core/booking/BookingService.java`、`core/booking/RoomCalendarService.java` |
| 後端測試 | `backend/.../api/controller/BookingControllerE2ETest.java`、相關單元測試 |
| 前端顯示 | `frontend/src/components/storefront/MonthCalendar.tsx`、`ListingDetail.tsx`、`services/booking.ts` |
| 前端賣家設定 | `frontend/src/services/room.ts`、`components/room/RoomForm.tsx` |
| 前端 E2E | `frontend/e2e/at-room-booking.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. ✅ 使用者（PO）確認點（已拍板）

1. **範圍**：AI-2202e 選項 A = US-001 後端開放窗三層 + migration V58（4 SP）+ US-002 前端買家顯示 + 賣家設定 + E2E（3 SP）= **7 SP**。✅ 核准。
2. **開放窗語意**：**固定截止 `open_until_date` + 滾動視窗 `booking_window_days` 兩者並行**，有效上限取最早生效者（`resolveOpenUntil` helper）。✅ PO 追加拍板「同時做滾動視窗」。
3. **未開放日買家呈現**：新狀態 `NOT_OPEN`（灰底、不刪除線、禁選，區別於 BLOCKED）。✅ 採推薦預設。
4. **賣家設定 UI**：✅ **納入本 Sprint**（US-002 AC-002-4，RoomForm 雙欄設定）。
5. **打破零-migration 慣例**：本 Sprint 因 schema 變更（V58，2 欄）結束 S42~S46 連續零-migration。✅ PO 於選定主軸時已知悉並接受。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
