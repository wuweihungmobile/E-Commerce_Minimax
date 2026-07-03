# Sprint 50 Retrospective / Sprint 50 回顧會議

> **Sprint 編號**: Sprint 50
> **期間**: 2027-09-12 ~ 2027-09-25
> **回顧日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001~002）|
| 完成 SP | 8 SP（全數）|
| 主題 | 真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）|

---

## 2. 做得好的（What went well）

- **S49 spike 直接驅動 S50 實作，路線精準**：S49 評估文件的分階段路線（A~D）+ real/stub/missing 表讓 S50 開工即知「接回孤兒 gateway + 補 Checkout Session + toggle + migration」，且複用已真接線的 Stripe SDK/驗簽。決策先行第三次驗證有效（S45→S47、S49→S50）。
- **先解結構、以 WireMock 早驗 SDK**：先加 Checkout Session gateway 方法並用 WireMock（TC-S004/005）證實 Stripe SDK 整合正確，再往上接 service/controller。金流敏感，早驗 SDK 避免上層堆疊後才發現 SDK 用法錯。
- **toggle 預設 mock 保零退步**：`STRIPE_PAYMENT_ENABLED` 預設關，既有 mock 付款路徑與全部既有 E2E 完全不受影響（validate-e2e 54/6/0）。真金流以灰度方式安全並存。
- **冪等設計到位**：confirmStripeCheckout 對「已 SUCCESS」直接回、對回跳重入不重複更新——單元測試（UT-PAY-STRIPE-004）編碼此不變量。
- **hosted Checkout 省前端複雜度**：選 hosted Checkout（PO 決策）使前端無需 @stripe/Elements 依賴，僅重導 + success/cancel 頁，PCI 範疇最輕、實作最快。

---

## 3. 待改善的（What to improve）

- **Phase A 狀態依賴回跳，非權威**：買家未回跳（關分頁）時本地狀態會滯後，需 Phase B webhook 補權威更新。→ 這是 MVP 的已知限制（已揭露），但上線前 Phase B（AI-2411）應緊接，避免「Stripe 已收款但本地未 PAID」的對帳缺口長期存在。
- **端到端無法全鏈自動測**：hosted Checkout 重導至外部 Stripe，Playwright 無法走完整鏈。→ 以 WireMock（後端）+ 前端 mock return 覆蓋；真實端到端須人工於 Stripe 測試模式驗證（列入上線 checklist）。
- **孤兒 gateway 的 stub 仍在**：confirmPayment/processRefund/getPaymentStatus 仍為 S14/S21 stub，本 Sprint 只接 Checkout 路徑。→ 退款（Phase C/AI-2412）時應一併清理這些 stub，避免誤用。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S50 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2411 | 真實金流 Phase B：webhook 驅動狀態 | Webhook.constructEvent 解析 + 冪等 + 權威狀態（補回跳未達的缺口）+ 3DS | SD Marcus | P2 | 建議緊接 |
| AI-2412 | 真實金流 Phase C：退款真串接 | Refund.create + charge.refunded webhook + 清理既有 refund stub | Dev David | P3 | 待 AI-2411 |
| AI-2413 | 真實金流 Phase D：分帳/提現 | Stripe Connect 或手動；賣家 onboarding/KYC | SD Marcus | P3 | 待 PO 決策 |
| AI-2407 | 定價規則選取語意評估 | bestRule priority + range 查詢 | SD Marcus | P3 | 待評估 |
| AI-1903 | 買家閉環 live 走查（真人）| live 環境跨角色資料流（含真金流測試模式驗證）| QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 49 → Sprint 50 Action Items 追蹤結果

| Action Item | 內容 | Sprint 50 達成狀態 |
|------------|------|---------------------|
| AI-2410 | 真實金流 Phase A 卡片付款 MVP | ✅ 完成（US-001 後端 Checkout Session + toggle + V59 + US-002 前端重導/success/cancel）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S50 累積 10 Sprint）|
| AI-2411/2412/2413 | 金流 Phase B/C/D | 🟡 續留（Phase B 建議緊接）|
| AI-2407 | 定價規則語意評估 | 🟡 續留（P3）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S45 | 5 |
| S46 | 8 |
| S47 | 7 |
| S48 | 8 |
| S49 | 5 |
| **S50** | **8** |

> **觀察**：S50 = 8 SP，實作型（含 schema + 外部金流整合）。品質：後端單元 9 + 真 DB 整合 25（mock 不退步）、validate-e2e **54 passed/0 fail**（+1）、validate-schema 無漂移（V59）、catch(Exception)/@Deprecated=0。**真實金流首個可運行階段落地**（Stripe Checkout 卡片付款閉環，toggle 灰度）。Phase B（webhook 權威狀態）建議緊接。

---

## 7. 下一步

> **檢查點**：Sprint 50 已完成（US-001 `6d913b5` + US-002 `8d31f82` + 收尾，本地各層驗證通過含 validate-schema 無漂移 + validate-e2e 54/6/0）。**push 債已累積 S41~S50（10 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。**真實金流 Phase A（卡片付款 MVP）已落地**，Sprint 51 建議：**AI-2411 Phase B webhook 驅動狀態（補回跳未達的權威狀態缺口，上線前必要）**，或 AI-2412 退款、AI-2407 定價語意、AI-1903 真人 live 走查。**強烈建議：10 Sprint push 債宜於此里程碑清償。**

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
