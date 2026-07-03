# Sprint 52 Retrospective / Sprint 52 回顧會議

> **Sprint 編號**: Sprint 52
> **期間**: 2027-10-10 ~ 2027-10-23
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001~002）|
| 完成 SP | 5 SP（全數）|
| 主題 | 真實金流 Phase C——退款真串接（Stripe Refund）|

---

## 2. 做得好的（What went well）

- **金流三階段連續交付、序列清晰**：Phase A（付款）→ B（webhook 權威）→ C（退款），每階段緊接前一階段、複用既有基礎（SDK/驗簽/gateway 抽象層）。分階段路線（S49 評估）落地為可控的增量交付。
- **順帶償還 Phase B best-effort 債**：S51 retro 標記「失敗/退款事件 pi 查找不可靠」，本 Sprint 於 createCheckoutSession 補 pi metadata order_id 一併解決——退款需要可靠事件對應，正好連帶修正。
- **退款 toggle-aware 保 mock 不退步**：refundOrderPayment 依 toggle + paymentMethod 分流，stripe 真退款、mock 保留；既有 /refund 測試（OrderPaymentControllerE2ETest）零退步。
- **WireMock 早驗 Refund SDK**：TC-S006 以 WireMock 驗 Refund.create，金流敏感操作先驗 SDK 用法正確再往上接 service。
- **雙路徑退款一致**：service 主動退款（refundOrderPayment）與 webhook 被動退款（markStripeRefunded/charge.refunded）皆標記 REFUNDED，冪等一致。

---

## 3. 待改善的（What to improve）

- **部分退款未支援**：只做全額退款。→ 電商實務常有部分退款（退單一品項/運費）；partially_refunded 狀態 + 金額計算為後續需求，需 PaymentStatus 擴充 + 金額欄位，另評估。
- **真退款端到端仍賴人工驗證**：WireMock 驗 SDK，但「真觸發退款 → Stripe → charge.refunded 回來」的全鏈須測試模式人工驗證。→ 併入上線 checklist（AI-2414）。
- **金流 stub 尚餘 confirmPayment/getPaymentStatus + LinePay**：Phase C 清了 refund stub，但 confirmPayment（PaymentIntent 直連流程用）、getPaymentStatus 仍為 stub（Checkout 流程未用到）。→ 若未來加 Elements 直連流程再清理；目前 Checkout 流程不依賴，記錄備查。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S52 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2414 | 真金流上線 checklist | STRIPE_WEBHOOK_SECRET、公開端點、Stripe 測試模式端到端（付款/webhook/退款）人工驗證、真金鑰 | QA Quincy + Dev David | P2 | 上線前 |
| AI-2413 | 真實金流 Phase D：分帳/提現 | Stripe Connect 或手動；賣家 onboarding/KYC | SD Marcus | P3 | 待 PO 決策 |
| AI-2415 | 部分退款評估 | partially_refunded 狀態 + 金額計算 + 退款金額欄位 | SD Marcus | P4 | 待評估 |
| AI-2407 | 定價規則選取語意評估 | bestRule priority + range 查詢 | SD Marcus | P3 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| live 環境跨角色資料流 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 51 → Sprint 52 Action Items 追蹤結果

| Action Item | 內容 | Sprint 52 達成狀態 |
|------------|------|---------------------|
| AI-2412 | 真實金流 Phase C 退款真串接 | ✅ 完成（gateway 真 Refund + service toggle-aware + charge.refunded webhook + V61）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S52 累積 12 Sprint）|
| AI-2413 | Phase D 分帳 | 🟡 續留（待 PO 決策）|
| AI-2414 | 上線 checklist | 🟡 續留（上線前）|
| AI-2407 / AI-1903 | 定價語意 / live 走查 | 🟡 續留 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S47 | 7 |
| S48 | 8 |
| S49 | 5 |
| S50 | 8 |
| S51 | 5 |
| **S52** | **5** |

> **觀察**：S52 = 5 SP，後端聚焦（退款無前端 UI）。品質：後端單元 19 + 真 DB 整合 21（mock/Phase A/B 不退步）、validate-schema 無漂移（V61）、validate-e2e **54 passed/0 fail**（持平）、catch(Exception)/@Deprecated=0。**真實金流付款閉環完整**（付款 + 權威狀態 + 退款皆真實）。Phase D（分帳）+ 上線 checklist + 部分退款待排。

---

## 7. 下一步

> **檢查點**：Sprint 52 已完成（US-001+US-002 `af1591b` + 收尾，本地各層驗證通過含 validate-schema 無漂移 + validate-e2e 54/6/0）。**push 債已累積 S41~S52（12 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。**真實金流付款閉環（付款/權威狀態/退款）已完整**，剩分帳（Phase D）+ 上線 checklist。Sprint 53 建議：AI-2414 真金流上線 checklist（端到端人工驗證）、AI-2413 Phase D 分帳（需 PO 決策 Connect vs 手動）、AI-2407 定價語意、AI-1903 真人 live 走查。**極強烈建議：12 Sprint push 債（含完整真實金流 + V59/V60/V61 三支 schema）務必盡快清償——這是重大未落地風險。**

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
