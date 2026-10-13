# Sprint 246 Plan — 店家入住按鈕、人工退款、no-show 自動取消（DEF-350／354／352）

**Sprint**: Sprint 246
**日期**: 2026-10-09

> **⚠️ 本文件性質說明（2026-10-13 文件一致性檢查第四輪，Sprint 261 補登）**：Sprint 246 當時只完成了程式與 commit（`8e52d83c`），**從未建立對應的 Sprint Plan 文件**——這是本輪稽核由兩個獨立唯讀 agent 各自查證確認的既有缺口（`RELEASE_TRACKER.md` 第 Sprint 246 列的連結長期指向一個不存在的檔案）。本文件是事後依**唯一可查證的一手來源**（commit `8e52d83c` 的完整 commit message 與檔案異動、`RELEASE_TRACKER.md` 既有 Sprint 246 列、`DEFERRED_ITEMS_TRACKER.md` 既有 DEF-350 條目）重建，不是 Sprint 246 當時的即時記錄，內容僅限這些來源能佐證的事實，不補充無法查證的細節。

---

## 1. 範圍

延續 [SPRINT_245_PLAN.md](SPRINT_245_PLAN.md) 收尾時登記的三項：DEF-350（店家後台入住/退房按鈕與狀態日誌端點，Sprint 245 只做了 API 本體）、DEF-352（no-show 自動取消排程，PRD §17.4.6 Q15）、DEF-354（店家漏標入住的誤取消，人工退款入口）。

## 2. 實作內容

1. **DEF-350**：`/dashboard/bookings/[id]` 頁面新增辦理入住／退房按鈕與狀態歷程顯示；新增 `GET /v2/dashboard/bookings/{bookingId}/state-log`（對應 PRD §9.7），讀取既有 `audit_log` 資料。
2. **DEF-354**：新增 `POST /v2/bookings/{bookingId}/refund`（僅管理員可用），用於 no-show 系統取消中經核實為店家漏標入住的誤取消，直接 `NONE → COMPLETED`（不經過 `PENDING`）。
3. **DEF-352**：新增 `BookingNoShowService` 排程＋`BookingService.cancelBookingIfNoShowDue`：已付款、入住時刻＋24 小時（**使用者 2026-10-09 確認維持此時限**，PRD §17.4.6 Q15）仍未入住 → 系統取消、不退款、買家站內通知。上線開關 `app.booking-no-show.enabled`（對應環境變數 `BOOKING_NO_SHOW_AUTO_CANCEL_ENABLED`）**預設關閉**，待人工於生產環境驗證 DEF-350 後開啟。
4. **過程中發現並修正一個真實缺陷**：判斷與取消邏輯原本寫在排程類別自己身上，Spring AOP 自呼叫（同一 bean 內的 `this` 呼叫不經過代理）導致 `@Transactional` 形同沒有交易保護；真實資料庫整合測試執行時才會拋 `TransactionRequiredException`，mock 測試完全看不到。修法：改放進 `BookingService`（與 `BookingTimeoutService` 委派 `BookingService` 同一分層）。

## 3. 測試

依 commit `8e52d83c` 實際新增的測試檔案：`BookingNoShowServiceTest`（134 行）、`BookingServiceCheckInOutTest`（60 行）、`BookingServiceNoShowTest`（143 行）、`PaymentStateServiceBookingTest`（169 行）、`BookingApiRealStackIntegrationTest`（69 行，真實 DB／HTTP／權限鏈）、`BookingNoShowIntegrationTest`（248 行，真實資料庫整合測試，即為揭露上述 AOP 自呼叫缺陷的測試）。另依 `DEFERRED_ITEMS_TRACKER.md` DEF-350 條目記載：「DEF-350 另以 Playwright 對打包 JAR＋真實 Postgres/Redis＋瀏覽器走過一遍入住→退房流程確認畫面正確」。

## 4. 文件同步（commit 當時實際完成的部分）

`API_M06_Booking.md`（§4.14-4.15 新增）、`DEFERRED_ITEMS_TRACKER.md`（v3.38）、`ENVIRONMENT_VARIABLES.md`（v1.2）、PRD（1 處修訂）已於 commit `8e52d83c` 同步更新。**本次重建新發現**：PRD §9.7 與 FRD §6A 的「state-log 未實作」舊敘述未被這次 commit 更新到（分別在 Sprint 261 修正），且 Sprint 246 本身的 Plan 文件從未建立（即本檔案存在的原因）。

## 5. 範圍外／已知限制

- DEF-352 的前置條件（上線並「驗證店家能入住」）需實際部署後由人工確認，本輪只完成程式，排程預設關閉。
- no-show 入住日判斷以固定時鐘驗證，午夜跨日的真實時鐘行為未測試（與 Sprint 245 的已知限制同型）。

## 6. 下一步（commit 當時實際排定，後續已完成）

Sprint 247 納入結算（DEF-353）。

---

**文件狀態**：事後重建（見頂部說明），非即時記錄。
**來源**：commit `8e52d83c`、[RELEASE_TRACKER.md](RELEASE_TRACKER.md) Sprint 246 列、[DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) DEF-350 條目。
