# 退款路徑整合評估 / Refund Path Consolidation Assessment

> **文件類型**: 技術債評估（Spike，非架構決策記錄）
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **來源**: Sprint 59 US-001（AI-2417）；探勘於 Sprint 56（AI-2415 部分退款）發現
> **狀態**: 🔍 評估完成；**建議見第 4 節，是否行動待 PO 決定是否要擴大金流範圍**

---

## 1. 背景：兩套並行的付款/退款服務類別

專案中存在兩個功能大致平行的服務類別：

| # | 類別 | 端點 | 涵蓋範圍 | Stripe 整合 |
|---|------|------|---------|-------------|
| (1) | `PaymentService`（`core/payment/PaymentService.java`）| `PaymentController` `/v2/payments/*`（`processPayment`/`processRefund`/`getPaymentStatus`）| **Order + Booking 皆涵蓋** | ❌ 純 Mock，未呼叫任何 Stripe API |
| (2) | `PaymentStateService`（`core/payment/PaymentStateService.java`）| `OrderPaymentController` `/v2/orders/{id}/*`（`mockPaymentSuccess`/`refundOrderPayment`/`initiateStripeCheckout`/...）| **僅 Order** | ✅ Sprint 50~56（Phase A~C + 部分退款）真實 Stripe |

兩者並非「單一方法重複」，而是**整個服務類別層級**的重疊：`processPayment`/`processRefund`/`getPaymentStatus` 在兩邊都各自有一份實作。

---

## 2. 關鍵發現：這不是意外重複，而是金流專案自始的既定範圍

回顧 `docs/07_design/PAYMENT_INTEGRATION_ASSESSMENT.md`（Sprint 49）與後續所有真實金流實作（AI-2410 Phase A 卡片付款 → AI-2411 Phase B webhook → AI-2412 Phase C 退款 → AI-2413 Phase D-1 Connect onboarding → AI-2415 部分退款），**全部只針對 Order（商品訂單）流程**，從未涉及 Booking（訂房）。`PaymentStateService` 完全沒有 `refundBookingPayment` 或訂房版的 Stripe Checkout 方法。

**結論**：`PaymentService`（含 Booking 支援）與 `PaymentStateService`（僅 Order，但含真實 Stripe）之間的「重疊」，實際上是「Order 這一半功能被真實金流計畫重做了一份，Booking 那一半從未被觸及」——不是兩個團隊各寫一份同樣的東西，而是金流真實化計畫刻意只做了 Order。

---

## 3. 使用現況：兩條退款路徑目前都沒有前端呼叫端

- `PaymentController`/`PaymentService`（純 Mock）：全域搜尋 `frontend/src` 找不到任何呼叫 `/v2/payments/refund`（或 `/v2/payments` 下任何端點）的程式碼。`frontend/src/lib/api.ts` 雖定義了 `payments.create/mock/callback` 三個路徑常數，但這些常數在整個前端**沒有任何組件引用**，且 `mock`/`callback` 兩個路徑在後端根本沒有對應端點（死程式碼）。
- `OrderPaymentController` 的 `/v2/orders/{id}/refund`：`frontend/src/lib/api.ts` 定義了 `orders.refund(id)`，但 `frontend/src/services/payment.ts`（`OrderPaymentService`）**沒有實作對應的 `refund` 方法**，故此常數同樣沒有被任何組件呼叫。

**兩套退款邏輯目前都只由後端測試（`M07PaymentMockIntegrationTest`、`OrderPaymentControllerE2ETest`、`PaymentStateServiceStripeTest`）驗證，沒有真實使用者流量**。

---

## 4. 額外發現：Mock 路徑的部分退款計數本身也有缺陷

`PaymentService.processRefund`（`:148-189`）接受 `request.getAmount()` 作為退款金額，但：
- 無論金額是否等於全額，`payment.setStatus()` 一律設為 `REFUNDED`（終態），不會使用 Sprint 56 新增的 `PARTIALLY_REFUNDED` 狀態或 `refundedAmount` 累計欄位——這兩個欄位是全域共用的 `Payment` entity 欄位，`PaymentService` 完全沒有同步更新。
- 若透過此路徑「部分退款」，`Payment.refundedAmount` 會維持 0（因為沒被寫入），與 `PaymentStateService` 路徑的語意不一致——若兩條路徑曾經被同一筆 `Payment` 交替呼叫，會產生資料不一致。**目前因無前端呼叫端而無實際風險**，但屬於潛在陷阱記錄在案。

---

## 5. 選項比較

### 選項 A：維持現狀（建議）

- **內容**：不做任何改動，僅以本文件記錄現況。
- **理由**：兩條路徑目前都沒有真實使用者流量，貿然刪除或整合屬於「無需求驅動」的臆測性變更（違反 Rule 2 簡潔優先——不為不存在的需求寫程式碼）。`PaymentService` 對 Booking 的 mock 支援目前仍是 Booking 退款的**唯一**實作，若貿然刪除會造成功能倒退。

### 選項 B：擴大金流範圍，補齊 Booking 真實 Stripe 退款

- **內容**：仿照 Order 的 Phase A~C 模式，為 `PaymentStateService` 新增 Booking 版本的真實 Stripe 付款/退款方法，讓 Booking 也能走真實金流，之後才有基礎談「整合/棄用 `PaymentService`」。
- **理由**：這是**新功能開發**（擴大金流真實化範圍），規模與 Order 的 Phase A~D 相當（估 15+ SP），需要 PO 決定「訂房是否也需要真實收款」這個商業優先級問題，不是技術債清理範疇。
- **不建議在本次評估直接執行**：超出 AI-2417「純技術債整合評估」的原始 ticket 範圍。

### 選項 C：清理死程式碼（前端未使用的 API 常數）

- **內容**：`frontend/src/lib/api.ts` 的 `payments.mock`/`payments.callback` 常數對應後端不存在的端點，屬於死程式碼，可獨立清理（與退款路徑整合與否無關）。
- **風險**：極低，純刪除未使用常數。
- **建議**：可視未來容量獨立處理，非本次評估範圍內的緊急事項。

---

## 6. 建議

- **本次不採取程式碼行動**（選項 A）：兩條退款路徑皆無使用者流量，維持現狀不影響任何實際功能；若強行整合，反而需要先臆測「該保留哪一套語意」，風險大於效益。
- **若未來 Booking 需要真實金流**：那是選項 B 的範疇，應由 PO 評估訂房收款的商業優先級後另立項目（不在本次 AI-2417 授權範圍內）。
- **選項 C（清理死程式碼）**：可獨立記錄為極低優先級的小清理，不需要另立正式追蹤項目。

---

## 7. 影響與驗證

- **schema**：無變動。
- **行為**：本次純評估，**不改動 production code**，無行為變更。
- **驗證**：無需額外測試（無程式碼異動）。

---

**文件版本**: v1.0｜**建立者**: SD Marcus + Dev David + Claude Code｜**基於**: AISDLC v0.09
