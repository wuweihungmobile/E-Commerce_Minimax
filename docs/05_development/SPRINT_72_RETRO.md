# Sprint 72 Retrospective / Sprint 72 回顧會議

> **Sprint 編號**: Sprint 72
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 13 SP（US-001 8 + US-002 2 + US-003 3）|
| 完成 SP | 13 SP（全數）|
| 主題 | ERP 模組（Supplier/StockMovement/PurchaseOrder/Inventory）單元測試從零建立 + 2 項跨租戶問題處理（1 項已確認修復、1 項驗證後證實成立並修復） |

---

## 2. 做得好的（What went well）

- **動手前先探查範圍，未重演 Sprint 66 之前的教訓**：使用者明確要求「先探查範圍，不要直接開始大規模實作」，本 Sprint 依此完整盤點 4 個 Service 的方法數/行數/既有測試現況，與 Sprint 71 量級比較後才確認可單一 Sprint 涵蓋，避免 Sprint 66 之前「規劃前未探查、一次塞入過多變更」的狀況重演。
- **誠實揭露「測試目錄不存在」的精確範圍**：探查時未籠統接受「ERP 測試目錄完全不存在」的說法，而是進一步確認雖然單元測試層確實空白，但既有 `M16ErpIntegrationTest`/`M16ErpE2ETest`（43 個測試）已提供功能行為的規格依據，避免「從零猜測規格」的風險，也避免誤導使用者以為 ERP 模組完全沒有任何驗證。
- **2 項安全問題皆以測試驗證為前提，不自行假設**：US-002（已確認的漏洞）與 US-003（僅是探查階段的推論）都先寫測試取得紅燈證據才動手修復，US-003 尤其展現了「先驗證推論是否成立」的紀律——沒有因為推論聽起來合理就直接修，而是先寫測試證實。
- **善用程式碼本身的線索交叉佐證**：US-003 修復前發現 `PurchaseOrderService` 的 `ListingRepository` 欄位帶 `@SuppressWarnings("unused")`——這個標記本身就是「該做的驗證沒有做」的具體證據，修復後移除標記，形成程式碼本身的一致性檢查（該用的依賴用了、不再需要抑制警告）。
- **開發-編譯-測試循環嚴格執行**：4 個測試檔案依序完成，每檔完成後立即編譯 + 單獨執行驗證，US-002/US-003 更額外要求「先跑一次確認紅燈，才動手修復，再跑一次確認轉綠」的雙重驗證節奏，未跳過任何一步。
- **全量回歸依政策正確選擇**：因本 Sprint 修改生產程式碼（US-002 + US-003），正確選擇執行全量 `mvn verify -Pintegration-test`（1032 tests 0 fail）而非僅 `mvn test`，符合 2026-07-05 使用者確認的全量回歸頻率政策。
- **背景長時間指令的等待協議持續遵守**：`mvn test`（首次因環境問題自動轉背景）、`mvn verify -Pintegration-test`、後續 `git commit`/`git push` 皆以背景模式執行並回報 log 路徑，延續 Sprint 69-71 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **`mvn test` 執行前未再次確認測試 DB 狀態**：與 Sprint 71 相同的教訓重複發生——本 Sprint 首次執行 `mvn test` 前未先確認 `make test-db-up` 是否已啟動，導致一次無謂的失敗排查（3 個與本 Sprint 無關的 `SellerDashboardServiceCacheTest` 錯誤）。Sprint 71 Retro 已記錄此改善建議但本 Sprint 未落實執行前檢查，屬於流程紀律尚未內化為習慣，建議正式將「執行涉及 `@SpringBootTest` 的指令前先 `docker ps` 確認」納入 Sprint 起手式檢查清單，而非僅記錄於文件。
- **`RELEASE_TRACKER.md` 缺少 Sprint 68/69/70 列的技術債持續未處理**：Sprint 71 已標記此為前序遺留、非本 Sprint 範圍；本 Sprint 同樣選擇不回頭補齊（維持 Rule 3「精準改動」），但已連續 2 個 Sprint 記錄同一項技術債卻未安排清理，建議下次收尾時明確排入小任務一次性補齊，避免無限期擱置。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，未立案）| 執行涉及 `@SpringBootTest`/`integration-test` profile 的指令前，先確認測試 DB 狀態 | 已連續 2 個 Sprint（71、72）發生相同的無謂失敗排查，建議正式納入起手式檢查清單 | Dev David | 🟡 中 | 下個 Sprint 起手式 |
| （技術債，未立案，延續自 Sprint 71）| `RELEASE_TRACKER.md` 補齊 Sprint 68/69/70 列 | 已連續 2 個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （技術債，未評估）| `PurchaseOrderService.purchaseOrderItemRepository` 欄位 `@SuppressWarnings("unused")` 是否為死碼 | 本 Sprint 修復 US-003 時發現同檔案還有另一個標記未使用的欄位，範圍外未處理 | Dev David | 🟢 低 | 待評估 |
| （待決策，延續自 Sprint 71）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 71 → Sprint 72 Action Items 追蹤結果

| Action Item | 內容 | Sprint 72 達成狀態 |
|------------|------|---------------------|
| ERP 模組整體測試強化 | Sprint 71 收尾建議下一 Sprint 排程 ERP 模組 | ✅ **本 Sprint 完成**（50 個新增測試，1032 tests 全量 0 fail） |
| `mvn test` 執行前先確認測試 DB 狀態 | Sprint 71 Retro 記錄的改善建議 | ❌ **未落實**，本 Sprint 再次發生相同問題（見上方「待改善」）|
| `RELEASE_TRACKER.md` 補齊 Sprint 68/69/70 列 | Sprint 71 標記的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S67 | 8 |
| S68 | 6（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| S69 | 8（恢復例行測試強化排程節奏）|
| S70 | 3（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| S71 | 8（恢復例行測試強化排程節奏）|
| **S72** | **13**（ERP 模組測試強化 8 SP + 2 項安全問題處理 5 SP，較例行排程略高）|

> **觀察**：S72 較 S66/67/69/71 的例行 8 SP 略高，因額外包含 2 項安全問題的測試先行驗證與修復工作（US-002/US-003 共 5 SP），非單純測試補強。品質：全量回歸 **1032 tests 0 fail**（本 Sprint 新增 50 個）、`make validate-schema` 無漂移。**ERP 模組單元測試缺口清零**，與既有 43 個 M16 整合/E2E 測試互補，形成完整測試金字塔；同時清償 2 項跨租戶安全缺口（`DEF-026`/`DEF-027`），延續 Sprint 68/70 建立的「發現即記錄、驗證後才修復」紀律。活躍高優先級延後項目維持 0（`DEF-026`/`DEF-027` 皆已修復並直接結案，未進入活躍延後清單）。

---

## 7. 下一步

> **檢查點**：Sprint 72 已完成，準備收尾並 push。ERP 模組測試強化與 2 項安全缺口清償告一段落，多 Sprint 測試強化計劃的核心高風險模組（Payment/Order/Booking/ERP）皆已補齊單元測試防護網。**下一 Sprint（Sprint 73+）**建議依 `SPRINT_71_PLAN.md`/`SPRINT_72_PLAN.md` 排程：`ReviewService`（14 方法）、`CmsService`（11 方法）等其餘尚未強化的模組，或優先處理本 Sprint 新增的 Action Items（測試 DB 起手式檢查、`RELEASE_TRACKER.md` 補齊）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
