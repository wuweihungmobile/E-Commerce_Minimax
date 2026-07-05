# Sprint 71 Retrospective / Sprint 71 回顧會議

> **Sprint 編號**: Sprint 71
> **期間**: 2026-07-06（恢復例行測試強化排程）
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001）|
| 完成 SP | 8 SP（全數）|
| 主題 | `BookingService`（createBooking/updateBooking 日期變更）+ `RoomCalendarService`（訂房核心，含 idempotency）單元測試強化 |

---

## 2. 做得好的（What went well）

- **動手前先完整盤點既有覆蓋，避免重工**：`BookingService` 表面上與 Sprint 68 相關，但沒有假設「Sprint 68 做過 Booking 就等於全部涵蓋」，而是逐方法比對確認 Sprint 68 `BookingServiceOwnershipTest` 只涵蓋擁有權檢查，`createBooking`/`updateBooking` 日期變更的業務邏輯本身仍是真正缺口，精準鎖定目標不重複造輪子。
- **`RoomCalendarService` 零覆蓋確認嚴謹**：不只是「看起來沒測試」，而是實際 `find` 確認先前完全沒有對應測試檔案，才動手建立，符合「先讀後寫」原則。
- **開發-編譯-測試循環嚴格執行**：三個新測試檔案依序完成，每檔完成後立即編譯 + 單獨執行驗證通過才進行下一檔；`BookingServiceCreateBookingTest` 首版一個斷言失敗（`Booking.roomListingId` 影子欄位在純 mock 情境不會自動回填）立即定位根因並修正，未帶著失敗繼續往下寫。
- **技術債發現後正確處理**：撰寫測試過程中發現 `idempotencyKey` 死碼參數，先分析清楚（確認真正 idempotency 機制在 Controller 層正確運作、非安全缺口）後回報，未自行假設是否需要清理，交由使用者決策後記錄為 `DEF-025`。
- **環境問題正確排查而非誤判為迴歸**：`mvn test` 首次因測試 DB 未啟動導致 `SellerDashboardServiceCacheTest` 3 個無關測試報錯，冷靜比對錯誤名單（確認與本 Sprint 變更的檔案完全無關）並依經驗（已知的 `@ActiveProfiles("integration-test")` 環境陷阱）判斷為環境前置條件問題，`make test-db-up` 後重跑驗證確認，未誤判為本 Sprint 造成的迴歸而白費力氣排查。
- **背景長時間指令的等待協議持續遵守**：`mvn test`（兩次）與 `make validate-schema` 皆以背景模式執行並回報 log 路徑，延續 Sprint 69/70 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **`mvn test` 執行前未確認測試 DB 狀態**：本 Sprint 首次執行 `mvn test` 前未先確認 `make test-db-up` 是否已啟動，導致一次無謂的失敗排查（雖然最終正確判斷為環境問題而非迴歸，但若能在執行前先確認環境狀態可省去一次來回）。建議未來執行涉及 `@SpringBootTest`/`@ActiveProfiles("integration-test")` 測試的指令前，先養成確認 `docker ps` 或 `make test-db-up` 狀態的習慣。
- **發現 `RELEASE_TRACKER.md` 缺少 Sprint 68/69/70 列（前序遺留，非本 Sprint 造成）**：本 Sprint 僅新增自己的列，未回頭補齊，屬於獨立的文件維護技術債，建議另立小任務或於下次收尾時一併處理。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （技術債，未立案）| `RELEASE_TRACKER.md` 補齊 Sprint 68/69/70 列 | Sprint 71 發現總覽表缺少三列，屬前序遺留 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| DEF-025 | `BookingService.createBooking` 的 `idempotencyKey` 死碼參數 | 已記錄至 `DEFERRED_ITEMS_TRACKER.md`，使用者已決策不清理 | - | 🟢 低（不排入排程）| 已結案（記錄備查）|
| （待決策，延續自 Sprint 68）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403，需評估是否需比照 `LogisticsService` 補租戶側檢查 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 70 → Sprint 71 Action Items 追蹤結果

| Action Item | 內容 | Sprint 71 達成狀態 |
|------------|------|---------------------|
| `BookingService`/`RoomCalendarService` 測試強化 | Sprint 70 收尾建議恢復例行測試強化排程 | ✅ **本 Sprint 完成**（34 個新增測試，640 tests 全量 0 fail） |
| Service 層內部呼叫的授權假設文件化 | 將 `updateOrderStatus`/`checkOrderPaymentOwnership` 等授權假設整理為架構文件 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 聚焦測試強化）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S66 | 8 |
| S67 | 8 |
| S68 | 6（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| S69 | 8（恢復例行測試強化排程節奏）|
| S70 | 3（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| **S71** | **8**（恢復例行測試強化排程節奏）|

> **觀察**：S71 延續 S69 的例行測試強化節奏（8 SP）。品質：後端單元 **640 tests 0 fail**（本 Sprint 新增 34 個）、`make validate-schema` 無漂移。**`BookingService`/`RoomCalendarService` 訂房核心單元測試缺口清零**（`createBooking`/`updateBooking` 日期變更 + `RoomCalendarService` 全部方法），與 Sprint 68 擁有權檢查測試互補，訂房模組測試防護網完整；活躍延後項目新增 `DEF-025`（🟢 低優先級，已決策記錄不清理）。

---

## 7. 下一步

> **檢查點**：Sprint 71 已完成，準備收尾並 push。`BookingService`/`RoomCalendarService` 測試強化告一段落，**下一 Sprint（Sprint 72+）**建議依 `SPRINT_71_PLAN.md` 第 7 節排程：ERP 模組整體（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在）測試強化。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
