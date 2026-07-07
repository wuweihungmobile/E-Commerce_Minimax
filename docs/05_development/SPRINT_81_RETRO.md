# Sprint 81 Retrospective / Sprint 81 回顧會議

> **Sprint 編號**: Sprint 81
> **期間**: 2026-07-07 ~ 2026-07-08
> **回顧日期**: 2026-07-08
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 6 SP（US-101 DEF-040 修復 3 + US-102 webhook 修正版 3）|
| 完成 SP | 6 SP（US-101/US-102 全數完成）|
| 主軸 | 承接 Sprint 80 retro 記錄的兩個 🔴 高優先候選項目：DEF-040 + US-005（webhook 同步），使用者明確指示「一起處理」|

---

## 2. 做得好的（What went well）

- **規劃前主動查證外部事實，發現並修正前一輪 Sprint 遺留的錯誤假設**：Sprint 80 retro 記錄的 US-005 原文為「補 `transfer.paid`/`transfer.failed` webhook」，動手前先查閱 Stripe 官方文件（`docs.stripe.com/api/events/types`）確認 `Transfer` 物件實際只有 `transfer.created`/`transfer.reversed`/`transfer.updated` 三種事件，`transfer.paid`/`transfer.failed` 根本不存在（屬於不同資源 `Payout`）。及時修正範圍為 `transfer.reversed`，避免實作一個永遠不會被觸發的死碼 webhook handler。
- **業務決策點套用既有慣例而非另起新設計**：DEF-040 的修復方向（ADMIN 限自己租戶、SUPER_ADMIN 可跨租戶）並非憑空決定，而是明確比照 `DEF-038`（`TenantContextFilter`）與 Sprint 80 剛完成的 `TransferService.checkTenantAccess`/`TransferController` 既有模式，保持同一結算領域內的規則一致性，避免使用者需要記住兩套不同的租戶隔離規則。
- **修改既有 Service 方法簽名前，主動找出所有呼叫端並同步修復**：`SettlementReviewer.approveStatement`/`rejectStatement`/`getPendingReviewStatements` 簽名變更後，主動 grep 全專案找到 `M07SettlementIntegrationTest.java` 這個既有測試檔案仍呼叫舊簽名，且其 `@WithMockUser` 預設 principal 型別與新增的 `@AuthenticationPrincipal UserPrincipal` 不符（會導致 principal 為 null 而 NPE），比照 Sprint 80 `TransferControllerE2ETest` 的 `authAs` 模式修復，並補上 2 個新的跨租戶案例，而不是僅讓編譯通過就結束。
- **紅燈測試延續多 Sprint 累積的安全修復慣例**：`SettlementReviewerTest` 從第一版就包含跨租戶拒絕案例，未等到之後才發現漏洞回頭補。

---

## 3. 待改善的（What to improve）

- **`SPRINT_80_PLAN.md`/retro 記錄 US-005 時未先查證 Stripe 事件是否存在**：僅依常識假設 webhook 事件命名（`transfer.paid`/`transfer.failed` 對稱於 `payment_intent.succeeded`/`payment_intent.payment_failed` 的命名直覺），未實際查閱 Stripe API 文件即寫入正式規劃文件。往後任何涉及第三方 API（webhook 事件名稱、欄位、狀態機）的規劃，動手實作前應先查證官方文件，而非依賴命名直覺假設。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （新流程建議，本 Sprint）| 規劃涉及第三方 API（webhook 事件、狀態機）的功能前，先查證官方文件而非依賴命名直覺 | 避免重演 Sprint 80 retro 記錄 `transfer.paid`/`transfer.failed` 不存在事件的規劃錯誤 | SA Amanda | 🟡 中 | 下一輪規劃時套用 |
| （待決策，延續自 Sprint 74-81）| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | 涉及 API 簽名變更與 `SecurityConfig` 調整 | PO Victoria（決策） | 🟡 中 | 待業務決策 |
| （技術債，延續自 Sprint 71-81）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 80 → Sprint 81 Action Items 追蹤結果

| Action Item | 內容 | Sprint 81 達成狀態 |
|------------|------|---------------------|
| US-005：`transfer.paid`/`transfer.failed` webhook 同步 | Sprint 80 記錄的 🔴 高優先候選 | ✅ **已完成（修正版）**：查證後確認原定事件不存在，改實作真正存在的 `transfer.reversed`，詳見上方「做得好的」 |
| DEF-040：`SettlementController` admin 審核端點無租戶過濾 | Sprint 80 記錄的 🔴 高優先候選 | ✅ **已完成**：使用者指示「一起處理」，與 US-005 併入本 Sprint |
| `DEF-034`/`RELEASE_TRACKER.md` 補齊等既有待決策/技術債項目續留 | | 未啟動（續留）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S77 | 7 |
| S78 | 5 |
| S79 | 5 |
| S80 | 13 |
| **S81** | **6**（US-101 DEF-040 修復 3 + US-102 webhook 修正版 3）|

---

## 7. 下一步

> **檢查點**：Sprint 81 已完成，準備收尾並徵詢使用者同意後 push（本 Sprint 涉及生產授權邏輯變更 + 金流狀態同步，不比照一般 Sprint 自動 push）。**下一輪規劃建議**：
> 1. `DEF-034`（`CmsService` 公開端點租戶範圍設計）——待業務決策，可視使用者優先級排入。
> 2. `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列——技術債，可視容量排入。
> 3. 目前 `DEFERRED_ITEMS_TRACKER.md` 已無 🔴 高優先級待決策項目，PRD v1.0 主要安全/金流缺口已全數處理完畢，下一輪可考慮回到 PRD 待辦功能盤點。

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
