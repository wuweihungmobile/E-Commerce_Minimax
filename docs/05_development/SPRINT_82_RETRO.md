# Sprint 82 Retrospective / Sprint 82 回顧會議

> **Sprint 編號**: Sprint 82
> **期間**: 2026-07-08
> **回顧日期**: 2026-07-08
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 3 SP（DEF-034）|
| 完成 SP | 3 SP |
| 主軸 | 承接 Sprint 81 retro 記錄的候選項目 DEF-034（CMS 公開瀏覽端點租戶範圍設計），使用者指示「兩者依序進行」|

---

## 2. 做得好的（What went well）

- **動手前重新探查程式碼，發現並修正追蹤器原始記錄的過時假設**：`DEFERRED_ITEMS_TRACKER.md` 記錄這三個端點是「公開」端點，但重新讀碼確認 `SecurityConfig` 其實沒有對應的 `permitAll()`，實際上需要登入才能呼叫；同時發現前端完全沒有任何地方呼叫這三個端點。這個發現改變了整個修復的風險評估（零前端呼叫端＝零破壞性），也讓向使用者提出的業務決策問題更精準。
- **業務決策點正確識別並提問，而非自行假設**：CMS 頁面/橫幅未來是否要對外公開（訪客瀏覽）還是僅限會員，是真正的產品定位問題，不是單純的技術一致性判斷（不同於 DEF-040 有 Sprint 80 剛建立的強前例可直接套用）。主動用 `AskUserQuestion` 讓使用者拍板，而非援引較疏遠的 `PostController` 前例自行決定。
- **實作中自我抓到一個差點犯下的安全回歸**：原計畫直接對 `/v2/cms/pages/*` 整段設定 `permitAll()`，但發現 `PUT /v2/cms/pages/{pageId}`（Admin 更新）與 `GET /v2/cms/pages/{slug}`（公開查詢）是同一個 path pattern、只差 HTTP method；若不限定方法，會意外把 Admin 寫入端點也一併公開放行，形成一個新的、比原本 DEF-034 更嚴重的漏洞。動手前多想一步用 `requestMatchers(HttpMethod.GET, ...)` 限定方法後才套用，避免了這個問題。
- **修改公開查詢方法簽名前，先確認零呼叫端**：透過 Explore agent 確認前端與既有測試皆未使用這三個端點後才安心修改簽名，降低了「改了以後才發現破壞既有呼叫」的風險。

---

## 3. 待改善的（What to improve）

- 無重大待改善事項；本 Sprint 規模小且風險已在事前充分排查。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （待決策，延續自 Sprint 76-82）| `DEF-037`：`ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| （技術債，延續自 Sprint 71-82）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 81 → Sprint 82 Action Items 追蹤結果

| Action Item | 內容 | Sprint 82 達成狀態 |
|------------|------|---------------------|
| `DEF-034`：`CmsService` 公開端點租戶範圍設計 | Sprint 74-81 連續記錄待業務決策 | ✅ **已完成**：使用者拍板定位為公開行銷內容，比照 `PostController` 模式修復 |
| `RELEASE_TRACKER.md` 補齊 | Sprint 71-81 連續記錄的技術債 | 未啟動（續留）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S78 | 5 |
| S79 | 5 |
| S80 | 13 |
| S81 | 6 |
| **S82** | **3**（DEF-034）|

---

## 7. 下一步

> **檢查點**：Sprint 82 完成，`DEFERRED_ITEMS_TRACKER.md` 目前已無任何 🔴/🟡 待決策安全性項目（`DEF-037` 已擱置結案）。**下一輪規劃建議**：
> 1. 回到 PRD v1.0 剩餘待辦功能盤點，確認是否有尚未實作的核心功能。
> 2. `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列——技術債，可視容量排入。
> 3. `AI-1903` 買家閉環 live 走查，需 live 環境時排入。

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
