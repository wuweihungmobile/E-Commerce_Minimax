# Sprint 260 計畫：修復 DEF-363（blockDateRange/unblockDateRange 缺少悲觀鎖）

## 1. 起點與範圍

Sprint 256～259 完成 DEF-358 四項路由缺口（已全數結案），收尾後詢問使用者下一步方向，使用者選擇處理 DEF-363（Sprint 255 處理 DEF-360 時讀碼比對發現並登記、未修、未實測驗證是否為真缺陷的技術債）。

DEF-363：`RoomCalendarService.blockDateRange`/`unblockDateRange` 呼叫無鎖版本查詢 `findByListingIdAndCalendarDateBetween`，而同檔案的 `markMaintenance`/`unmarkMaintenance`/`releaseDateRange` 三者早已在 DEF-134/135/136 改用有鎖版本 `lockCalendarRange`（`FOR UPDATE NOWAIT`）。登記時的待辦是「下一步應比照 DEF-134/135/136 當初的驗證方式，實測驗證後才知是否為真缺陷」。

## 2. 驗證方式（誠實記錄與原始待辦的差異）

讀碼回溯 DEF-134/135/136 本身的驗證紀錄（追蹤表 Sprint 142 項目）：當初的驗證方式是「主控 session 獨立複核確認為真」——即讀碼比對確認該方法與已知有問題的同型模式一致，而非附帶一次即時高併發重現的證據；regression 測試只保留了新鎖定路徑的悲觀鎖失敗guard測試（`markMaintenance_pessimisticLockFailure_throwsE4001`）。

本輪比照同一方式驗證 DEF-363，而非另外設計一次即時多執行緒競態重現：

1. **讀碼確認結構同型**：`blockDateRange`/`unblockDateRange` 目前就是「無鎖 SELECT → 迴圈內改欄位 → `save()`」，與 DEF-134/135/136 修復前的三個方法逐行比對結構完全一致。
2. **確認無其他防護網**：`RoomCalendar` 實體（`domain/model/room/RoomCalendar.java`）沒有 `@Version` 欄位，代表沒有樂觀鎖可以攔截這類併發覆蓋，風險並非理論上被其他機制消解。

**結論**：DEF-363 描述的結構性風險為真，套用與 DEF-134/135/136 相同的修法。

**範圍誠實修正（意外發現）**：讀碼確認 `blockDateRange`/`unblockDateRange` 在 `grep -rn` 全專案 `src/main`（含所有 Controller）與前端 `src` 皆為**零呼叫點**——沒有任何 REST 端點或前端介面會觸發這兩個方法，屬尚未啟用的死碼。本次修復的實際效果是**預防性**的（避免這兩個方法未來真正被接上端點時，才發現同一個已知修法模式沒有套用），而非修復一個正在被利用的生產環境漏洞。此修正不影響「該不該修」的判斷（仍應修，理由見下），但影響風險定性，如實記錄。

## 3. 實作

`backend/src/main/java/com/nextkey/ecommerce/core/booking/RoomCalendarService.java`：

1. `blockDateRange`：`roomCalendarRepository.findByListingIdAndCalendarDateBetween(...)` 改為 `lockCalendarRange(...)`（既有私有方法，複用不新增）。
2. `unblockDateRange`：同樣改為 `lockCalendarRange(...)`。
3. 更新兩者的 Javadoc 補上 DEF-363 說明；更新 `lockCalendarRange` 本身的 Javadoc，從「供三個方法共用」改為「供五個方法共用」。

**行為變化**：兩者現在會與 `bookDateRange` 等既有鎖定路徑共用同一把悲觀鎖——若日期範圍正被另一筆交易鎖定，會拋 `E_4001`（而非先前無鎖、永遠不會因鎖定衝突報錯）。此行為與 `markMaintenance`/`unmarkMaintenance`/`releaseDateRange` 既有行為一致，非新發明的語意。

**範圍內確認不處理**：`PricingService.overridePrice`（389 行）也呼叫同一個無鎖查詢 `findByRoomListingIdAndCalendarDateBetween`，但該處是唯讀驗證（檢查範圍內有無 BOOKED 日期，不寫回 `RoomCalendar`），不是 DEF-363 描述的「讀-改-寫」結構，不在本次範圍內。

## 4. 測試

`backend/src/test/java/com/nextkey/ecommerce/core/booking/RoomCalendarServiceTest.java`：

- 新增 2 案例：`blockDateRange_pessimisticLockFailure_throwsE4001`、`unblockDateRange_pessimisticLockFailure_throwsE4001`（鏡射既有 `markMaintenance_pessimisticLockFailure_throwsE4001`，驗證悲觀鎖搶占失敗時轉譯為 `E_4001`）。
- 修正 3 個既有案例的 mock 目標（`blockDateRange_setsAllToBlocked`、`blockDateRange_freshRoomNoCalendarRecords_createsEntriesAndSetsBlocked`、`unblockDateRange_onlyBlockedDatesRevert`）：原本 mock 無鎖查詢 `findByListingIdAndCalendarDateBetween`，改為 mock 有鎖查詢 `findByRoomListingIdAndCalendarDateBetweenWithLockNowait`，驗證意圖不變（僅對齊新的查詢路徑）。

**突變驗證（紅燈先行且實際執行）**：`git stash push` 只暫存 `RoomCalendarService.java`（保留測試檔案的新/改動），重跑 `RoomCalendarServiceTest`：`Tests run: 33, Failures: 3, Errors: 2`，恰好是新增的 2 案例與修正的 3 案例轉紅，其餘 28 案例不受影響；`git stash pop` 還原修復後重跑恢復 `33/33` 綠燈。

## 5. 全量回歸結果

`mvn test`（真實 PostgreSQL/Redis，`make test-db-up`）：單元 **2223／0 失敗／0 錯誤**（首次執行時因忘記啟動測試 DB 出現 11 個 `ApplicationContext` 載入錯誤，確認與本次改動無關、純環境缺口，補啟動後重跑乾淨）。

`make validate-release`（act 等價雲端 CI：backend-unit + backend-integration + frontend，schema 漂移檢查，Playwright E2E）：

- Backend Integration Tests & Package：`Main Run Integration Tests` ✅ Success（15m25s），`[REPO-EXEC] executed=808 skipped=0 failures=0`
- schema-gate / e2e-gate：backend 啟動成功，entity 與 Flyway schema 對齊，**無漂移**
- E2E：**170 passed／4 skipped／0 failed**（174 項規格，5.4 分鐘）
- 整體：`🏁 Job succeeded`，`✅ 完整測試程序全數通過（act + schema + E2E）`，已寫入 FULL 驗證記錄

**DEF-363 已修復並通過全量回歸。**
