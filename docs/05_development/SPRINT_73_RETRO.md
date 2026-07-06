# Sprint 73 Retrospective / Sprint 73 回顧會議

> **Sprint 編號**: Sprint 73
> **期間**: 2026-07-06
> **回顧日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 14 SP（US-001 8 + US-002 2 + US-003 2 + US-004 2 + US-005 0）|
| 完成 SP | 14 SP（全數）|
| 主題 | `ReviewService` 7 個零覆蓋方法單元測試從零建立 + 3 項擁有權/租戶問題修復（`DEF-028`/`DEF-029`/`DEF-030`）+ 1 項技術債記錄（`DEF-031`） |

---

## 2. 做得好的（What went well）

- **依使用者要求主動審視擁有權缺口，而非被動等待撞見**：延續 Sprint 68/70/72 連續發現跨租戶問題的觀察，本 Sprint 開始前使用者明確要求「不要只是被動地在寫測試時剛好發現，而是要主動用『這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者』的角度去審視每一個方法」。探查階段確實一次揭露 3 項缺口（`markAsHandled`/`markAsUnhandled`/`getReviewsByHandlingStatus`/`getUserReviews`），且都在動手寫測試之前就先完成分析回報，取得使用者確認後才實作，未重演過去「先寫測試才意外撞見」的被動模式。
- **紅燈測試的正確性本身也被交叉驗證**：初版紅燈測試因 fixture 缺陷（`reviewType` 遺漏、stub 誤用）產生了 9 個 Error（而非 Failure），若未經協作發現並修正，可能誤判「測試失敗=漏洞證實」，但實際上只是測試本身寫錯。修正後才呈現乾淨的、真正對應「未拋出例外」語意的 4 個 Failure，這個過程展現了「紅燈本身的有效性也需要驗證」的紀律，比單純「跑到失敗就當作證明」更嚴謹。
- **三種擁有權檢查模式各自比照最合適的既有前例**：`markAsHandled`/`markAsUnhandled` 比照 `DEF-024`（本租戶 or admin，無本人語意）；`getReviewsByHandlingStatus` 比照 `DEF-026`（保留舊查詢 + 新增租戶過濾查詢的分支模式）；`getUserReviews` 比照 `DEF-018`（owner-or-admin）。未套用單一制式模板，而是依每個方法的實際呼叫語意挑選對應前例。
- **開發-編譯-測試循環嚴格執行，含中途發現測試 fixture 缺陷的即時修正**：紅燈測試撰寫 → 執行確認失敗 → 發現 fixture 缺陷 → 修正 fixture → 重新執行確認乾淨紅燈 → 修復生產程式碼 → 執行確認轉綠 → 發現多餘 stub → 清理 → 最終確認轉綠，每一步都立即執行驗證，未累積到最後才一次驗證。
- **全量回歸依政策正確選擇**：因本 Sprint 修改生產程式碼（`ReviewService`/`ReviewRepository`），正確選擇執行全量 `mvn verify -Pintegration-test`（1058 tests 0 fail）而非僅 `mvn test`，符合既定的全量回歸頻率政策。
- **執行涉及 `@SpringBootTest`/`integration-test` profile 的指令前先確認測試 DB 狀態**：落實 Sprint 72 Retro 記錄但未落實的改善行動——本 Sprint 執行 `mvn verify -Pintegration-test` 前先確認 `docker ps` 無 postgres 容器，主動執行 `make test-db-up` 才啟動全量回歸，避免重演 Sprint 71/72 連續 2 次的無謂失敗排查。
- **背景長時間指令的等待協議持續遵守**：`mvn test`（多次紅綠燈驗證）、`mvn verify -Pintegration-test`、`make validate-schema` 皆以背景或前景快速執行並回報 log 路徑，延續 Sprint 69-72 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **紅燈測試撰寫時應更謹慎設計 stub，避免「巧合性失敗」混淆「有效紅燈」**：本 Sprint 初版紅燈測試出現 2 類 fixture 問題（`reviewType` 遺漏導致的 Error、stub 設計不當導致的巧合性 NPE），雖然最終都被協作發現並修正，但理想情況應在撰寫階段就先手動追蹤程式碼執行路徑（修復前會走到哪一行、需要哪些 mock），減少來回修正的次數。建議未來紅燈測試撰寫前，先在心裡（或註解中）明確寫下「修復前預期執行路徑」與「修復後預期執行路徑」兩種情境各自需要的 stub，一次到位。
- **`isCurrentUserAdmin()` 判斷邏輯與 `RolePermissionMapping.java` 文件註解存在潛在語意落差，未在本 Sprint 一併釐清**：`ADMIN` 角色的程式碼行為（可跨租戶放行）與其註解「租戶內管理」不一致，本 Sprint 依 Rule 11 選擇沿用既有慣例而非重新定義，但此落差已橫跨 `DEF-018/019/023/024` 到本次 `DEF-028/029/030`，範圍持續擴大卻始終未被明確排上議程澄清，建議下次有餘裕時（非緊急安全修復 Sprint）安排一次性盤點：`ADMIN` 角色的實際權限邊界應該是租戶內還是全域，並統一所有既有檢查邏輯。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （流程改善，未立案）| 紅燈測試撰寫前先明確寫下修復前後預期執行路徑與對應 stub | 減少來回修正次數，避免巧合性失敗混淆有效紅燈 | Dev David | 🟡 中 | 下次安全修復類 Sprint |
| （待決策，橫跨多個 Sprint）| `ADMIN` 角色權限邊界（租戶內 vs 全域）與程式碼行為的一致性盤點 | `DEF-018/019/023/024/028/029/030` 皆採用「`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 同等視為全域放行」，與 `RolePermissionMapping.java` 註解「`ADMIN` 租戶內管理」不一致 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待排入（非緊急） |
| （技術債，未立案，延續自 Sprint 71/72）| `RELEASE_TRACKER.md` 補齊 Sprint 68/69/70 列 | 已連續 3 個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| DEF-031 | `ReviewService.markHelpful` 重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | 業務規則疑點，使用者已決策擱置 | PO Victoria（決策） | 🟢 低 | 待業務規則確認 |
| （待決策，延續自 Sprint 71/72）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 72 → Sprint 73 Action Items 追蹤結果

| Action Item | 內容 | Sprint 73 達成狀態 |
|------------|------|---------------------|
| 執行涉及 `@SpringBootTest`/`integration-test` profile 的指令前先確認測試 DB 狀態 | Sprint 71/72 連續 2 次未落實的改善建議 | ✅ **本 Sprint 落實**：`mvn verify -Pintegration-test` 執行前先 `docker ps` 確認、`make test-db-up` 啟動，未重演無謂失敗排查 |
| `RELEASE_TRACKER.md` 補齊 Sprint 68/69/70 列 | Sprint 71/72 標記的技術債 | 未啟動（續留，非本 Sprint 範圍）|
| `PurchaseOrderService.purchaseOrderItemRepository` 欄位 `@SuppressWarnings("unused")` 是否為死碼 | Sprint 72 發現但未評估 | 未啟動（非 `ReviewService` 範圍，續留）|
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍）|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S69 | 8（恢復例行測試強化排程節奏）|
| S70 | 3（緊急安全修復插隊，不計入例行測試強化排程節奏）|
| S71 | 8（恢復例行測試強化排程節奏）|
| S72 | 13（ERP 模組測試強化 8 SP + 2 項安全問題處理 5 SP）|
| **S73** | **14**（`ReviewService` 測試強化 8 SP + 3 項安全問題處理 6 SP + 技術債記錄 0 SP）|

> **觀察**：S73 略高於 S72，因本 Sprint 一次確認並修復 3 項擁有權/租戶問題（`DEF-028`/`DEF-029`/`DEF-030`，共 6 SP），較 S72 的 2 項（5 SP）多一項。品質：全量回歸 **1058 tests 0 fail**（本 Sprint 新增 18 個）、`make validate-schema` 無漂移。**`ReviewService` 單元測試缺口清零**，14 個方法皆有對應的單元或整合/E2E 測試覆蓋；同時清償 3 項跨租戶/擁有權安全缺口，延續 Sprint 68/70/72 建立的「主動審視、先驗證再修復」紀律，並首次做到**在動手寫測試前就先完成缺口分析與使用者確認**，而非撰寫過程中意外發現。活躍高優先級延後項目維持 0（`DEF-028`/`DEF-029`/`DEF-030` 皆已修復並直接結案，未進入活躍延後清單）。

---

## 7. 下一步

> **檢查點**：Sprint 73 已完成，準備收尾並 push。`ReviewService` 測試強化與 3 項安全缺口清償告一段落，多 Sprint 測試強化計劃已完整涵蓋 Payment/Order/Booking/ERP/Review 五大核心模組。**下一 Sprint（Sprint 74+）**建議依 `SPRINT_72_PLAN.md`/`SPRINT_73_PLAN.md` 排程：`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService` 等其餘尚未強化的模組，或優先處理本 Sprint 新增的待決策項目（`ADMIN` 角色權限邊界盤點）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
