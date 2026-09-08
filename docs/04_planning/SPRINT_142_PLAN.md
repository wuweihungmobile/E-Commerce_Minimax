# Sprint 142 Plan — 房源日曆（RoomCalendarService + RoomService）併發競態技術債查證與修復

**Sprint**: Sprint 142
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

延續 Sprint 137~141 做法（不另開 Workflow，主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復）。Sprint 141 完成退貨主題後，剩餘 18 筆技術債。本輪聚焦「房源日曆」主題——`RoomCalendarService`（`DEF-134`/`135`/`136`）與 `RoomService`（`DEF-137`）共 4 筆候選，理由：`RoomCalendarService` 本檔案已有 `bookDateRange`/`isDateRangeAvailable` 兩個既有的逐日 `FOR UPDATE NOWAIT` 鎖定範例可直接延伸套用，`RoomService.clearOpenWindow` 則與同批次的 `Room` 實體相關。

**查證結果**：逐一重讀原始碼、entity 定義後，**3 筆確認為真並修復，1 筆查證發現已被同一 Sprint 136 的另一項修復意外解決**：

| ID | 方法 | 查證結論 |
|----|------|---------|
| DEF-134 | markMaintenance | 真實：`findByListingIdAndCalendarDateBetween` 不上鎖，與 `bookDateRange` 既有的逐日 `FOR UPDATE NOWAIT` 鎖不對稱，讀後寫在併發下會靜默遺失更新 |
| DEF-135 | releaseDateRange | 真實，與 DEF-134/136 同根因 |
| DEF-136 | unmarkMaintenance | 真實，與 DEF-134/135 同根因 |
| DEF-137 | RoomService.clearOpenWindow | 🔴 **已被意外修復**：`Room` 實體已在 Sprint 136 §6（commit `b8ab23f`，同一輪）為了 `updateRoom` 加上 `@DynamicUpdate`，這正是 DEF-137 描述的根因；技術債清單沒有回頭勾掉 |

**誠實揭露**：DEF-137 的發現方式與 Sprint 137 §1 推翻 DEF-145/146/147（死流程）不同——不是「問題從未存在」，而是「Sprint 136 自己在同一輪的另一個小節（§6）已經把根因修掉了，只是第 8 節的技術債清單登記時沒有同步核對已修復的第 6 節內容，導致同一個問題被同時記成『已修復』（§6 的 `Listing`/`Room`/`Product`/`Booking`）與『待修復技術債』（§8 的 DEF-137）」。這提醒未來排 Sprint 前，除了「重新獨立複核」，也要交叉檢查同一輪內其他小節是否已經處理過同一根因。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136~141 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行 `RoomCalendarServiceTest`，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check@checkstyle-main`/`@checkstyle-test` + `make validate-schema`/`validate-schema-doc`；另以 `git log` 交叉核對 `Room.java` 的 `@DynamicUpdate` 加註時間，確認 DEF-137 是否真的已被涵蓋，而非僅憑印象判斷。

---

## 3. 修復摘要：房源日曆批次操作（`RoomCalendarService`，DEF-134/135/136）

**根因**：`RoomCalendarService` 本檔案的 `isDateRangeAvailable`/`bookDateRange` 已使用逐日 `findByRoomListingIdAndCalendarDateWithLockNowait`（`FOR UPDATE NOWAIT`）序列化併發存取，但 `releaseDateRange`/`markMaintenance`/`unmarkMaintenance` 三個方法呼叫的是完全不上鎖的 `findByListingIdAndCalendarDateBetween`，然後直接「讀狀態→條件式改欄位→`save()`」。這三個方法與 `bookDateRange`（或彼此）交錯時，同一 `room_calendar` 列的 `status`/`booking_id` 可能靜默遺失更新；`markMaintenance`/`unmarkMaintenance` 額外牽動 `Booking.statusFlags`（一個 Map 欄位的讀後寫），兩個併發呼叫對同一筆 Booking 的不同日期範圍觸發維護標記/解除時，也可能互相覆蓋對方剛寫入的 flag。

**修法**：新增 `RoomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait`（批次版 `FOR UPDATE NOWAIT` 原生查詢，比照本檔案既有的單日鎖定模式，一次鎖住整段日期範圍而非逐日查詢，減少往返次數）。三個方法改用新增的私有輔助方法 `lockCalendarRange`（統一處理 `PessimisticLockingFailureException` → `E_4001` 的轉譯，比照 `bookDateRange` 既有的錯誤處理慣例）取代原本的不上鎖查詢。

**驗證真實 SQL**：由於既有測試皆為 Mockito 單元測試或 `@MockBean` 版 MVC 整合測試，沒有涵蓋 `RoomCalendarRepository` 真實 DB 呼叫的既有測試案例，本輪額外以 `docker exec ... psql` 直接對測試 DB 的 `room_calendar` 表執行 `BEGIN; SELECT ... WHERE ... BETWEEN ... FOR UPDATE NOWAIT; COMMIT;`，確認新增的批次原生查詢語法在真實 PostgreSQL 上正確可執行（0 rows 但無語法錯誤）。

---

## 4. 誠實揭露：`RoomService.clearOpenWindow`（DEF-137）已被 Sprint 136 §6 意外修復

**查證過程**：讀取 `RoomService.clearOpenWindow` 原始碼，發現其邏輯（載入 Room→設 `openUntilDate`/`bookingWindowDays` 為 `null`→`save()`）與 DEF-137 描述的根因（`Room` 無 `@DynamicUpdate`，全欄位覆寫）完全一致，準備依樣畫葫蘆加上 `@DynamicUpdate` 時，發現 `Room.java` **已經有** `@DynamicUpdate` 註解，且旁邊的註解寫著「🔴 併發防護（DEF-136）：見 `Listing` 同一段 `@DynamicUpdate` 說明」（這裡的「DEF-136」是舊 Sprint 的技術債編號，與本輪 DEF-136 = `RoomCalendarService.unmarkMaintenance` 是兩個不相關但恰好重複使用的編號，容易混淆）。

`git log` 確認 `Room.java` 最近一次改動是 `b8ab23f`（Sprint 136 的修復 commit本身），對照 `SPRINT_136_PLAN.md` §6：「對 `Listing`、`Room`、`Product`、`Booking` 四個實體加上 `@DynamicUpdate`」——證實 DEF-137 的根因已在 Sprint 136 自己執行修復的同一輪被处理掉，只是 §8 的技術債清單建立時沒有排除已經在 §6 修掉的候選。

**結論**：`clearOpenWindow` 只顯式碰 `openUntilDate`/`bookingWindowDays` 兩欄位，`@DynamicUpdate` 讓 UPDATE 只包含這兩欄，不會再覆寫 `updateRoom` 併發已提交的其他欄位（title/price/location 等）。**不需要任何程式碼變更**，僅在追蹤表更正狀態並記錄查證過程。

---

## 5. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test` **0 violations**。
- **單元測試**：`RoomCalendarServiceTest` 由 23 增至 **24**（+1：`markMaintenance_pessimisticLockFailure_throwsE4001`），既有 8 個 releaseDateRange/markMaintenance/unmarkMaintenance 測試的 mock 同步改為 stub 新的批次鎖定查詢方法；`blockDateRange`/`unblockDateRange`（非本輪範圍，`unblockDateRange` 已於 Sprint 136 Verify 階段被多數駁倒）的既有測試維持不變。
- **整合測試**：`M17MaintenanceWorkflowIntegrationTest`（5 案例，`@MockBean` 版 MVC 整合測試）3 個案例的 mock 同步更新，全數通過。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，本輪未新增任何 Flyway 遷移。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1183**（相對 Sprint 141 的 1182，+1）、整合 **477**（與 Sprint 141 持平），0 failures/errors；checkstyle（main+test）**0 violations**；總耗時 7:23 min。

---

## 6. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 14 筆技術債（客服工單/租戶功能開關/聊天室/媒體/訂單/付款狀態記錄等領域）——本輪聚焦房源日曆主題，其餘留待後續 Sprint 依主題分批查證（Sprint 143 起）。
- 不處理 `blockDateRange`/`unblockDateRange`——結構上與 `markMaintenance`/`releaseDateRange` 相同（同樣不上鎖的批次讀後寫），但 `unblockDateRange` 已在 Sprint 136 Verify 階段被 3 個獨立懷疑視角多數駁倒（判定非真實風險），`blockDateRange` 從未被 Discover 階段納入候選；兩者皆非本輪 4 筆候選之一，不在此範圍內重新翻案。
- 不補寫真實 DB（非 `@MockBean`）的 `RoomCalendarService` 整合測試——本輪以 `psql` 直接驗證新增原生 SQL 語法正確性已足夠建立信心，比照既有測試覆蓋範圍的既定慣例（Mockito 單元測試 + `@MockBean` MVC 整合測試），新增專屬真實 DB 整合測試屬更大範圍的測試基礎建設投資，非本輪範圍。
