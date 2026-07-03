# Sprint 49 Review / Sprint 49 評審會議

> **Sprint 編號**: Sprint 49
> **期間**: 2027-08-29 ~ 2027-09-11
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 真實金流（Stripe）上線評估——決策先行型 spike（backlog #10）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端真實金流接線評估 + 分階段實作路線（spike）| 3 | ✅ 完成 |
| US-002 | 分帳/提現 + 前端收單 + 上線策略評估（spike）| 2 | ✅ 完成 |

**承諾 5 SP（US-001~002）全數完成**。M12 進階定價收官後轉入平台變現關鍵評估。本 Sprint 為 **spike**——產出金流整合評估/決策文件（ADR 候選），**不寫 production code、無 schema 變更**（比照 S45 決策 sprint）。backlog #10 實作（13 SP + 外部依賴）待 PO 決策後另立。

---

## 2. 交付內容

- **US-001 + US-002（spike，commit `11f1a53`）**：產出 [PAYMENT_INTEGRATION_ASSESSMENT.md](../07_design/PAYMENT_INTEGRATION_ASSESSMENT.md)——
  - **關鍵發現**：專案存在**兩套並行付款程式碼**——(1) 上線中純 Mock（`PaymentService`/`PaymentStateService`，MOCK-xxxx，前端付款按鈕實際路徑）；(2) **孤兒 Gateway 抽象層**（`PaymentGatewayFactory`/`StripePaymentGateway`，S14/S21「Stripe Phase 3」遺留，**grep 全 main source 確認無人注入**——死碼）。「真實上線」≠「填金鑰」。
  - **real/stub/missing 速查表**（檔案:行號佐證）：**真實**（Stripe SDK 24.3.0、createPaymentIntent、webhook 簽章驗證、金鑰設定、WireMock 測試）；**stub**（confirmPayment/refund/getPaymentStatus/webhook 事件處理/LinePay）；**完全缺**（gateway 接主流程、Stripe DB 欄位、非同步對帳、前端 Stripe.js、分帳/提現）。
  - **分階段路線**：Phase A 卡片付款 MVP（5 SP，複用 createPaymentIntent + 前端 Stripe.js Checkout）→ B webhook 驅動狀態（3 SP，複用驗簽 + 補事件解析/冪等）→ C 退款真串接（2 SP）→ D 分帳/提現（5+ SP，外部依賴最重）。
  - **關鍵決策界定**：mock↔real toggle（PAYMENT_PROVIDER 按租戶灰度）、分帳架構（Stripe Connect vs 手動 settlement）、前端 Stripe.js 選型（Checkout PCI SAQ-A vs Elements SAQ-A EP）、接回孤兒層 vs 重寫。
  - **§待 PO 決策 6 項** + **§後續實作 US 建議**（AI-2410 卡片 MVP / AI-2411 webhook / AI-2412 退款 / AI-2413 分帳，含外部依賴標記）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| production code 變更 | ✅ 無（純 spike，只產決策文件）|
| schema 變更 | ✅ 無（Flyway 維持 V58）|
| 既有測試影響 | ✅ 無（未動任何 code；後端/前端測試不受影響）|
| 文件品質 | ✅ 決策文件含現況盤點（real/stub/missing 表 + 檔案佐證）+ 缺口 + 分階段路線 + 決策事項 |
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0（未動 code）|

> **說明**：本 Sprint 不改 production code，故不跑 `make validate-e2e`（無 code 變動可驗）；既有測試狀態沿用 S48（後端單元 22 + 整合 54 + validate-e2e 53，皆綠）。

---

## 4. 誠實揭露（Rule 12）

1. **spike 型 sprint，SP 偏輕（決策密集）**：本 Sprint 5 SP，產出為決策文件而非可運行功能。真實金流實作（backlog #10，13 SP + 外部依賴）誠實另立——分 AI-2410~2413 依 Phase A~D。定位比照 S45（決策先行）。
2. **揭穿「Stripe 已整合」假象**：release 歷史（S14/S21）標「Stripe 整合/Phase 3」，實際上線路徑為純 Mock，Stripe 程式碼為**未接線的孤兒抽象層**。與 S45 揭穿 room_calendar.price 死碼假象異曲同工——**先驗證假設再規劃**（Rule 1/8）避免低估。
3. **真實上線是「接線 + 補齊 + 補 DB + 補前端 + 分帳決策」的大工程**：非填金鑰。文件明列缺口與分階段，避免一次到位的高風險。
4. **分帳/提現為獨立大主題**：Stripe Connect（自動、合規外包、改動大）vs 手動（沿用 settlement、營運負擔）為重大商業/架構決策，本 spike 只界定選項，交 PO。
5. **外部依賴為排程關鍵路徑**：Stripe 測試帳號、Connect onboarding/KYC、合規、公開 webhook 端點——實作排程需納入。
6. **push 債累積 S41~S49（9 Sprint）**：本 Sprint 純文件 → push 相對低風險，但仍承 S41~S48 累積。於檢查點徵詢後完整 `make validate-release` 後 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **現況真相**：向團隊展示 grep 佐證——`PaymentGatewayFactory` 無人注入（死碼）、`StripeWebhookController` 驗簽後只回 OK（不處理事件）、前端 `orders/[id]` 明文「不會實際扣款」。
- **可複用基礎**：createPaymentIntent + webhook 簽章驗證 + Stripe SDK 已真接線，是 Phase A/B 的起點。
- **決策交付**：分階段路線 + 6 項待 PO 決策，把「真實金流」從模糊大項拆為可排程、可決策的階段。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
