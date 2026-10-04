# Sprint 245 Plan — 訂房入住與退房（DEF-345，依 PRD Phase 1）

**Sprint**: Sprint 245
**日期**: 2026-10-04

## 1. 起點與範圍

### 1.1 起點與排程

Sprint 244 收尾時，使用者拍板 DEF-345「依 PRD Phase 1」：付款成功即等同確認，不另設 `CONFIRMED`；店家標記入住與退房；完成由退房後自動處理（見 [SPRINT_244_PLAN.md](SPRINT_244_PLAN.md) §2.3）。本輪依該拍板實作，範圍是 Sprint 244 §6.3 排定的內容：店家端的入住與退房端點與權限、`PAID → CHECKED_IN → CHECKED_OUT → COMPLETED` 的轉換規則、M06 規格與 FRD §6A、SRD §6.3.4 的同步、單元與真實資料庫的整合測試、突變驗證。

### 1.2 動工前的程式碼核對

| 核對項 | 程式碼現況 | 本輪處理 |
|--------|------------|----------|
| 訂房狀態的寫入路徑 | 只有付款（`CREATED → PAID`）與取消（`→ CANCELLED`，含逾時）會寫入；店家端只有列表 | 新增 `checkIn`、`checkOut` |
| 店家端點 | `DashboardBookingController` 只有 `GET`（列表） | 新增兩個 `POST` |
| 權限 | `booking:update` 在 HOST、STORE_OWNER、ADMIN、SUPER_ADMIN；STORE_STAFF 只有 `booking:read`，與 PRD §7.3 的 M06 列一致（RX*／R*） | 沿用 `booking:update`，不新增權限碼 |
| 下一步表 | `PaymentStateService.getBookingNextValidStates` 仍列 `PAID → CONFIRMED`，與拍板不符 | 改為 `PAID → CHECKED_IN, CANCELLED` |
| 既有測試的舊斷言 | `PaymentStateServiceTest` UT-PAY-STATE-009、`BookingApiRealStackIntegrationTest.payment()`、前端 e2e mock 都寫 `CONFIRMED,CANCELLED` | 三處同步更新 |
| 排程 | 退房後完成採同一交易，沒有新增 `@Scheduled` | 不動 `SchedulingConfigTest` 的排程清單 |
| 結算 | `core/settlement` 沒有任何 `Booking` 參照 | 完成不觸發結算（寫入 SRD §6.3.4） |

### 1.3 方法

- 規則依 PRD Phase 1 與拍板；PRD 未明定處列為假設（§2.2），寫進規格，並登記 DEF-351 請使用者確認。
- 寫入採既有的條件式 UPDATE 搶占（同 `cancelBooking`）；擁有權採既有的同租戶模式（排除系統租戶）；錯誤碼沿用 `E-5010`，不新增錯誤碼（免去 `ErrorCode`、API 錯誤碼文件與守門的四處同步）。
- 測試順序：先單元（規則），再真實全棧（HTTP＋JWT＋真實權限表＋真實 PostgreSQL），最後突變驗證守門有效。

## 2. 使用者決策與假設

### 2.1 已拍板（Sprint 244 收尾）

- DEF-345：依 PRD Phase 1（付款即等同確認；店家標記入住與退房；完成由退房後自動處理）。

### 2.2 本輪假設（PRD 未明定；使用者已於收尾確認，見 DEF-351）

使用者於 Sprint 245 收尾確認：入住日閘門**保留**、退房後**立即完成**（兩者與實作一致）；no-show **不退款**，入住日隔天 00:00（營運時區）仍 `PAID` 即自動取消。no-show 的規則已定義，但**本輪未實作**，登記為 DEF-352（見 §6）。

| 題目 | 本輪採用 | 理由 | 若要改 |
|------|----------|------|--------|
| 入住日限制 | 營運時區（Asia/Taipei）的今天 ≥ 入住日才可入住，否則 `422 E-5010`；入住日已過仍可補記 | PRD 寫「`CHECKED_IN` 前置抵達」，抵達前不應入住；補記避免歷史資料缺口 | 改為不設限，或限定只能當天入住 |
| 退房後完成的時點 | 退房與完成在同一交易內完成，不經排程 | 拍板字面「退房後自動處理」；不需排程或時間參數；`COMPLETED` 目前沒有結算副作用 | 改為隔日或結算週期後完成：需新增排程與守門，並決定對結算的影響 |
| 操作權限 | 沿用 `booking:update`：店主、Host、管理員可操作；店員只讀 | 與 PRD §7.3 的 M06 列及生產權限表一致；不新增權限碼 | 若要讓店員入住，需同時修改 PRD §7.3 與權限表 |
| 服務層擁有權 | `checkStoreBookingAccess`：管理員與同租戶商家；買家本人不算 | 入住與退房是店家的動作；不依賴控制器的權限註解（縱深防禦） | — |
| 重複入住、重複退房 | `422 E-5010`，不做冪等成功 | 狀態機轉換明確；誤操作不會被靜默吞掉 | 改為冪等的 `200` |
| no-show（已付款未入住） | **使用者決定不退款**：入住日隔天 00:00（營運時區）仍 `PAID` 即自動取消（`SYSTEM`），本輪未實作（DEF-352） | 與 PRD Q14「入住前 24 小時內取消不退款」一致：未到場視為最晚取消 | 改為全額退款：需先解決退款責任（DEF-353） |
| 入住後的取消與改期 | 不可（取消 `400 E-4007`、更新 `422 E-5010`） | 狀態機的自然結果；PRD Q14 只規範入住前的取消 | — |

### 2.3 程式行為變更

- `BookingService`：新增 `checkIn`、`checkOut`；新增私有的 `checkStoreBookingAccess`、`isPlatformAdmin`、`moveStatus`、`requireStatus`；`checkBookingOwnership` 改用 `isPlatformAdmin()`（行為不變）。
- `DashboardBookingController`：新增 `POST /v2/dashboard/bookings/{bookingId}/check-in` 與 `…/check-out`，權限 `booking:update`。
- `PaymentStateService.getBookingNextValidStates`：`PAID → CHECKED_IN, CANCELLED`，移除 `CONFIRMED` 列。
- 稽核（`audit_log`）：`BOOKING_CHECKED_IN`、`BOOKING_CHECKED_OUT`、`BOOKING_COMPLETED`。
- 沒有資料庫遷移、排程、錯誤碼或設定的變更。

## 3. 實作內容（清單）

| 檔案 | 變更 |
|------|------|
| `backend/.../core/booking/BookingService.java` | `checkIn`、`checkOut` 與私有 helper；`checkBookingOwnership` 抽出 `isPlatformAdmin()` |
| `backend/.../api/controller/DashboardBookingController.java` | 兩個 `POST` 端點 |
| `backend/.../core/payment/PaymentStateService.java` | 預訂的下一步表 |
| `backend/src/test/.../core/booking/BookingServiceCheckInOutTest.java`（新增，22 項） | 規則單元測試 |
| `backend/src/test/.../integration/BookingApiRealStackIntegrationTest.java` | 新增 `checkInAndCheckOut_byStoreOwner`、`checkInAndCheckOut_byPlatformAdmin`；`payment()` 的下一步斷言改為新值 |
| `backend/src/test/.../core/payment/PaymentStateServiceTest.java` | UT-PAY-STATE-009 斷言改為 `CHECKED_IN,CANCELLED` |
| `frontend/e2e/at-booking-payment.spec.ts` | mock 資料的 `nextValidStates` 同步（僅資料，無程式變更） |
| [API_M06_Booking.md](../02_architecture/API_M06_Booking.md) | v2.1：修訂註記、§1.1～§1.3、§2 端點總覽、§4.12～§4.13（新增）、§5 權限、§6 錯誤碼、§7 已知限制、§8 追蹤性 |
| [SRD_System_Architecture.md](../02_architecture/SRD_System_Architecture.md) | v1.5：§6.3.4 依實作重寫；修訂紀錄；標題版本號由 v1.3 同步為 v1.5 |
| [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md) | v1.4：頂部修訂註記、§6A.1、§6A.2、§6A.5（BR-M06-04／05，新增 BR-M06-11／12）、§6A.6、§6A.7 第 2 點；修訂紀錄；檔頭版本號由 v1.2 同步為 v1.4 |
| [E-Commerce_PRD_v1.0_Final.md](../01_requirements/E-Commerce_PRD_v1.0_Final.md) | 兩處修訂註記（§3 狀態機的 Sprint 244 註記、§9.7 的註記）；內文未改 |
| [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) | v3.34：DEF-345 移入「已完成延後項目」；新增 DEF-350、DEF-351；版本鏈 |
| [RELEASE_TRACKER.md](RELEASE_TRACKER.md) | Sprint 245 列（待 push）與統計區 |
| 本檔 | 計畫書 |

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 單元（規則） | `BookingServiceCheckInOutTest`（22 項，含參數化） | 22／22 通過 |
| 真實全棧 | `BookingApiRealStackIntegrationTest`（13 項，含 2 個新案例） | 13／13 通過 |
| 相關既有測試 | `BookingServiceOwnershipTest`（16）、`BookingServiceCancelRefundTest`（13）、`PaymentStateServiceTest`（含 UT-PAY-STATE-009） | 全數通過（`checkBookingOwnership` 重構後重跑） |
| 文件守門 | `ApiRouteDocDriftTest`（2 項）、`ErrorCodeDocDriftTest`（1 項） | 通過（API_M06 補上兩個宣告行後） |
| 全量後端 | `mvn -o clean verify`：單元 2132、整合 794 | BUILD SUCCESS，0 失敗／0 錯誤／0 略過，16 分 14 秒 |
| 突變驗證 | 14 個突變（§5.1） | 14／14 KILLED，每個檔案逐位元組還原 |
| 連結檢查 | 本輪變更的 Markdown 檔共 647 條相對連結；7 條無法解析，全部是 Sprint 244 即存在的既有問題（同 [SPRINT_244_PLAN.md](SPRINT_244_PLAN.md) §1.6）；本輪新增的連結全部解析 | 新增 0 條壞連結 |
| 本地 E2E | `make validate-e2e` | 138 通過、4 略過、0 失敗（見 §5.3） |

## 5. 驗證結果

### 5.1 突變驗證

驅動腳本放在 scratchpad，不進 repo。每個突變：備份 → 套用（`old` 必須恰好出現一次）→ 等 IDE 編出的 class 追上源碼 → 跑指定測試 → 還原 → 再等 class → sha256 比對原檔。

| 編號 | 突變 | 擊殺它的測試 |
|------|------|--------------|
| M01 | 入住日閘門整段失效 | `checkInDateStillAhead_isRejectedWithoutTouchingTheRow` |
| M02 | 入住日閘門方向反了（`isBefore` 改 `isAfter`） | `stayThatAlreadyStarted_canStillBeRecordedAsCheckedIn`（錯誤）、`checkInDateStillAhead_isRejectedWithoutTouchingTheRow` |
| M03 | 入住拿掉店家擁有權檢查 | `buyerWhoOwnsTheBooking_cannotCheckIn`、`storeOfAnotherTenant_cannotCheckIn` |
| M04 | 入住改用 `checkBookingOwnership`（買家本人放行） | `buyerWhoOwnsTheBooking_cannotCheckIn` |
| M05 | 任何店鋪租戶都放行（跨店） | `storeOfAnotherTenant_cannotCheckIn`、`checkOut_fromAnotherTenant_isRejected` |
| M06 | `isPlatformAdmin` 恆假（管理員被擋） | `BookingServiceCancelRefundTest` 的管理員取消案例（既有測試，也共用此路徑） |
| M07 | 條件式 UPDATE 的併發守門拿掉（`== 0` 改 `== -1`） | `losingARaceToAConcurrentChange_isRejected`、`completionStepLosingItsRace_isRejected` |
| M08 | 轉換不寫稽核 | `paidBookingDueToday_checksInAndAuditsTheTransition`、`checkedInBooking_checksOutAndCompletesInTheSameCall` |
| M09 | 退房後不自動完成 | `checkedInBooking_checksOutAndCompletesInTheSameCall`、`completionStepLosingItsRace_isRejected` |
| M10 | 退房拿掉 `CHECKED_IN` 前置檢查 | `onlyACheckedInBookingCanBeCheckedOut`（參數化的 6 項全數失敗：CAS 仍被呼叫） |
| M11 | 第二段完成以 `CHECKED_IN` 為起點 | `checkedInBooking_checksOutAndCompletesInTheSameCall`；整合測試 `BookingApiRealStackIntegrationTest` 的 2 項 |
| M12 | 入住端點權限放寬為 `booking:read`（店員可入住） | `BookingApiRealStackIntegrationTest.checkInAndCheckOut_byStoreOwner` |
| M13 | 退房端點權限放寬為 `booking:read`（店員可退房） | `BookingApiRealStackIntegrationTest.checkInAndCheckOut_byStoreOwner` |
| M14 | 下一步表退回 `PAID → CONFIRMED` | `PaymentStateServiceTest` 的 UT-PAY-STATE-009 |

**判讀**：

- **M10 是防禦縱深類**：資料庫的條件式 UPDATE 仍會擋下非法轉換，外部可見行為不變；擊殺它的是單元測試「不應嘗試 CAS」的斷言。這代表前置檢查沒有被整合測試直接覆蓋，屬可接受、但需記錄的覆蓋特性。
- **M06 同時被既有的取消案例擊殺**，表示既有測試也覆蓋 `isPlatformAdmin` 的共用路徑；未突變的全量測試通過，代表抽出沒有造成迴歸。
- **無效突變為 0**：每個突變都改在語意有效的位置、編譯通過；`NOT_APPLIED` 為 0。

### 5.2 單元與整合測試數字

- 全量後端（16 分 14 秒）：單元（surefire）2132、整合（failsafe）794，0 失敗、0 錯誤、0 略過。S243 為 2110 與 792，差額 +22 與 +2 即本輪新增的測試。
- `BookingApiRealStackIntegrationTest` 的 13 項包含：可用性、日曆、建立、列表與店家列表、詳情、更新、取消、退款政策、付款、入住與退房（店主）、入住與退房（管理員）。

### 5.3 本地 E2E（`make validate-e2e`）

- `make validate-e2e`：**138 通過、4 略過、0 失敗**（4.2 分鐘），退出碼 0；守門訊息「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」。
- 略過數與 Sprint 243 相同。本輪在 `frontend/e2e/` 的唯一變更是 `at-booking-payment.spec.ts` 裡一行 mock 資料，沒有新增或移除任何略過標記。

### 5.4 未驗證的項目（明示）

- **店家後台沒有入住與退房按鈕**（DEF-350）：因此沒有 UI 層測試；API 層以真實全棧驗證。
- **`GET /v2/bookings/{id}/state-log` 未實作**（DEF-350）。
- **真實 Stripe 路徑**：本輪未觸及。
- **雲端 CI**：run `37141640995`（commit `8a0024f`）全綠，11 分 24 秒；見 §8。
- **時間邊界**：入住日的判斷以 `BusinessTime` 固定為 2026-10-04（台北）驗證；午夜跨日的實際行為未以真實時鐘測試。

## 6. 範圍外（延後）與已知限制

- **DEF-350**：店家後台的入住與退房按鈕，以及 `state-log` 端點。
- **DEF-351**：已由使用者確認（§2.2）。no-show 的實作登記為 **DEF-352**（建議為 Sprint 246 首項）；訂房不進結算登記為 **DEF-353**（待評估）。
- **既有但不可達的 `CONFIRMED` 判斷**（`updateBooking` 允許 `CONFIRMED` 更新、`cancelBooking` 的 `wasPaid` 判斷）保留未刪（Rule 3：本輪不清理相鄰程式）。
- **退房不釋放日曆**：已住的晚數維持 `BOOKED`，沿用既有的日曆語意。

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. ✅ commit：`16f2011`（實作、測試、規格文件）與 `8a0024f`（計畫書與追蹤表）。
2. ✅ `make validate-e2e`：138 通過、4 略過、0 失敗（§5.3）。
3. ✅ push：`1307c8d..8a0024f`，pre-push 輕量守門通過。雲端 CI run `37141640995` 全綠（11 分 24 秒；Backend Unit Tests、Backend Integration Tests & Package、Frontend Lint & Build 皆 success）。RELEASE_TRACKER 的狀態欄與統計已依此回填。
4. ✅ 使用者確認 DEF-351：入住日閘門保留、退房後立即完成、no-show 不退款（§2.2）。no-show 的實作登記為 DEF-352。
5. Sprint 246 候選：DEF-352（no-show 自動取消，建議首項；動工前先決定寬限時段與買家通知）、DEF-350（店家後台按鈕與 state-log，已排入下一個 Sprint）、DEF-347（SMTP 非同步寄送，啟用 SMTP 前必修）、DEF-346（上線 runbook 的 Stripe 開關步驟）。
6. DEF-353（訂房收益與退款不進結算）待評估，見 DEF-353。
