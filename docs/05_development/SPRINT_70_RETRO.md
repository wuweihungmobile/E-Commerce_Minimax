# Sprint 70 Retrospective / Sprint 70 回顧會議

> **Sprint 編號**: Sprint 70
> **期間**: 2026-07-05（緊急插入，優先於例行排程）
> **回顧日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 3 SP（US-001）|
| 完成 SP | 3 SP（全數）|
| 主題 | 緊急安全修復：`OrderService.updateOrderStatus` 跨租戶 IDOR（`DEF-024`） |

---

## 2. 做得好的（What went well）

- **修復前先完成業務情境分析，未直接套用前一個 Sprint 的模式**：`DEF-024` 表面上與 Sprint 68 的 `DEF-023`（Booking IDOR）性質相同，但深入探查 `updateOrderStatus` 的實際呼叫鏈（`PaymentController`→`PaymentService`內部呼叫、`OrderController.updateStatus`直接呼叫）後，發現本方法有「買家自助付款」與「賣家管理自己租戶訂單」兩種正當情境並存，不可直接套用 Booking 側單純的 owner-or-admin 模式。沒有假設「性質相同=解法相同」，避免了修復安全漏洞卻誤擋合法賣家操作的風險。
- **設計方案先回報確認，取得同意後才動手**：依任務指示，在分析完成後先將「owner OR same-tenant OR admin」三選一放行邏輯的設計理由（含兩個既有前例的引用：`DEF-018` 買家模式 + `DEF-019` `LogisticsService.checkOrderTenant` 賣家模式）回報給使用者，待確認後才實作，而非自行假設方向直接修改生產程式碼。
- **精確複用既有前例，未自創邏輯**：修復邏輯完全由程式碼庫既有的兩個已驗證模式合成，沒有引入新的權限判斷方式，符合「配合程式碼庫慣例」原則（Rule 11），也讓修復的正確性有充分先例佐證而非憑空設計。
- **開發-編譯-測試循環嚴格執行**：修復完成後立即編譯 + 執行 `OrderServiceTest`（先確認既有 42 個測試不受影響），再撰寫 3 個新增擁有權/租戶測試並再次驗證，未累積到最後才一次驗證。
- **背景長時間指令的等待協議持續遵守**：`mvn verify -Pintegration-test` 與 `make validate-schema` 皆以背景/前景視預期耗時妥善處理並回報 log 路徑或結果，延續 Sprint 69 建立的良好習慣。

---

## 3. 待改善的（What to improve）

- **`checkOrderStatusUpdateAuthorization` 的租戶檢查未區分角色**：目前設計刻意不檢查呼叫者是否持有 `order:update` 權限（該分工交由 Controller 層 `@PreAuthorize` 負責），這是合理的分層設計，但也代表 service 層的防禦縱深有一個隱性假設——若未來有新的呼叫路徑繞過 Controller 層直接呼叫 `updateOrderStatus`（比照 `PaymentService` 的內部呼叫模式），需要重新檢視是否符合「owner or same-tenant」的假設仍然成立。建議在未來新增任何直接呼叫 `OrderService` 內部方法的程式碼時，將此檢查清單納入 code review 重點。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| （技術債，未立案）| Service 層內部呼叫的授權假設文件化 | 將 `updateOrderStatus`/`checkOrderPaymentOwnership` 等「內部呼叫繞過 Controller `@PreAuthorize`」的授權假設整理為架構文件，供未來新增類似呼叫鏈時參考 | SD Marcus | 🟢 低 | 待排入（文件維護） |
| （待決策，延續自 Sprint 69）| `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403，需評估是否需比照 `LogisticsService` 補租戶側檢查 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策（未變更，非本 Sprint 範圍）|
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 69 → Sprint 70 Action Items 追蹤結果

| Action Item | 內容 | Sprint 70 達成狀態 |
|------------|------|---------------------|
| `DEF-024` | `OrderService.updateOrderStatus` 跨租戶 IDOR 修復 | ✅ **本 Sprint 完成**（3 個新增擁有權/租戶測試，全量回歸 948 tests 0 fail） |
| `HOST` 角色 `booking:update` 權限落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403 | 未啟動（續留，非本 Sprint 範圍，本 Sprint 聚焦 `OrderService`）|
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
| **S70** | **3**（緊急安全修復插隊，不計入例行測試強化排程節奏）|

> **觀察**：S70 為第二次緊急安全修復插隊（第一次為 S68 的 `DEF-023`）。品質：後端單元 **606 tests**（新增 `OrderServiceTest` +3 個）+ 完整整合 **342 tests**，合計 **948 tests 0 fail**、`make validate-schema` 無漂移。**Order 模組跨租戶 IDOR 缺口清零**，活躍高優先級延後項目回到 0。

---

## 7. 下一步

> **檢查點**：Sprint 70 已完成，準備收尾並 push。安全缺口已清零，**下一 Sprint（Sprint 71）**建議恢復例行測試強化排程：`BookingService`/`RoomCalendarService` 尚未涵蓋的其餘業務邏輯方法測試強化；Sprint 72+ 建議 ERP 模組整體測試。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
