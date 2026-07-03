# Sprint 49 Retrospective / Sprint 49 回顧會議

> **Sprint 編號**: Sprint 49
> **期間**: 2027-08-29 ~ 2027-09-11
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001~002）|
| 完成 SP | 5 SP（全數）|
| 主題 | 真實金流（Stripe）上線評估——決策先行 spike（backlog #10）|

---

## 2. 做得好的（What went well）

- **探勘揪出「兩套並行程式碼 + 孤兒死碼」，避免低估**：規劃前若憑 release 歷史（S14/S21「Stripe 整合」）以為只需填金鑰，會嚴重低估。探勘（grep 全 main source）證實 `PaymentGatewayFactory` 無人注入、webhook 只回 OK、前端純 Mock——**先驗證假設再規劃**（Rule 1/8），與 S45 揭穿 room_calendar.price 死碼同一手法。
- **spike 產出可決策、可排程**：把模糊的「真實金流」（backlog #10，13 SP）拆為 Phase A~D + 6 項 PO 決策 + 4 個實作 US 建議（AI-2410~2413），每期附 SP/相依/風險。決策密集項不硬做，交 PO。
- **識別可複用基礎**：createPaymentIntent + webhook 簽章驗證 + Stripe SDK 已真接線——分階段路線據此把 Phase A/B 的複用點標明，避免「全部重寫」的過度估算。
- **PCI/合規納入評估**：Stripe.js 選型明列 SAQ 等級差異（Checkout SAQ-A vs Elements SAQ-A EP），分帳評估納入 Connect KYC/onboarding——避免實作期才發現合規盲點。
- **零風險交付**：純文件、不動 code，M12 收官後的驗證狀態（validate-e2e 53/0）不受任何影響。

---

## 3. 待改善的（What to improve）

- **孤兒 scaffolding 存活多個 Sprint 未被清理/接線**：Stripe gateway 抽象層自 S14/S21 起即為死碼，直到 S49 評估才被系統性盤點。→ 教訓：大型 scaffolding（如金流）若跨 Sprint 未接線，應在後續 Sprint 明確標記「死碼 or 待接線」，避免 release 歷史造成「已完成」的錯覺。
- **release 歷史用語誤導**：「Stripe 整合」「Phase 3」等字眼讓人以為已上線。→ 建議：release notes 對「scaffolding/未接線」與「真實上線」用語應明確區分（本次評估文件已建立此區分）。
- **決策型 sprint 連續出現，實作 backlog 累積**：S45（定價/開放窗決策）、S49（金流決策）皆 spike；AI-2202e 已於 S47 實作，但金流實作（AI-2410~2413）與其他 P3/P4 待評估項在累積。→ 觀察：spike 有效降低實作風險，但需在檢查點主動呈現決策，避免決策文件擱置（本次已排入檢查點呈現）。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S49 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2410 | 卡片付款 MVP（Phase A）| 接 gateway + PaymentIntent + 前端 Stripe.js Checkout + toggle + payments migration | SD Marcus + Dev David | P3 | 待 PO 決策 |
| AI-2411 | webhook 事件驅動狀態（Phase B）| Webhook.constructEvent 解析 + 冪等 + 狀態機對映 | SD Marcus | P3 | 待 AI-2410 |
| AI-2412 | 退款真串接（Phase C）| Refund.create + charge.refunded webhook + partially_refunded | Dev David | P3 | 待 AI-2411 |
| AI-2413 | 分帳/提現（Phase D）| Stripe Connect 或手動；賣家 onboarding/KYC；payout | SD Marcus | P3 | 待 PO 決策（Connect vs 手動）|
| AI-2407 | 定價規則選取語意評估 | bestRule priority + range 查詢 | SD Marcus | P3 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| live 環境跨角色資料流 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 48 → Sprint 49 Action Items 追蹤結果

| Action Item | 內容 | Sprint 49 達成狀態 |
|------------|------|---------------------|
| （S48 主軸）AI-2406c | PRODUCT 漲價 | ✅ 已於 S48 完成（M12 收官）|
| 真實金流評估（backlog #10）| S43~S49 候選 | ✅ 完成（本 Sprint spike，產 PAYMENT_INTEGRATION_ASSESSMENT.md）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S49 累積 9 Sprint）|
| AI-2407 / AI-2409 / AI-2202f / AI-2408 | 定價治理 / 邊角 | 🟡 續留（P3~P4 待評估）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S44 | 8 |
| S45 | 5 |
| S46 | 8 |
| S47 | 7 |
| S48 | 8 |
| **S49** | **5** |

> **觀察**：S49 = 5 SP，決策/spike 型（偏輕，比照 S45）。品質：純文件、不動 code，M12 收官驗證狀態（validate-e2e 53/0）不受影響、catch(Exception)/@Deprecated=0、schema-free（V58）。**產出金流整合評估決策文件**，把 backlog #10（13 SP + 外部依賴）拆為 Phase A~D + 6 項 PO 決策，待決策後另立實作（AI-2410~2413）。

---

## 7. 下一步

> **檢查點**：Sprint 49 已完成（spike，1 US commit + 收尾；純文件、無 code/schema 變更）。**push 債已累積 S41~S49（9 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。**建議：金流評估已產出、M12 已收官，宜於檢查點向 PO 呈現金流 §待決策 6 項（尤其 Connect vs 手動、分期優先），決策後啟動 AI-2410 卡片付款 MVP**；或先清償 9 Sprint push 債。其他候選：AI-2407 定價規則語意、AI-1903 真人 live 走查（需環境）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
