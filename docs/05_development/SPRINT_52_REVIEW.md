# Sprint 52 Review / Sprint 52 評審會議

> **Sprint 編號**: Sprint 52
> **期間**: 2027-10-10 ~ 2027-10-23
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 真實金流 Phase C——退款真串接（Stripe Refund）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | Stripe Refund 真串接 + 退款 toggle-aware + V61（AI-2412 後端）| 3 | ✅ 完成 |
| US-002 | charge.refunded webhook + pi metadata 補強 + 測試（AI-2412 後端）| 2 | ✅ 完成 |

**承諾 5 SP（US-001~002）全數完成**。承 Phase A/B（S50/S51）真實付款 + webhook 權威狀態，本 Sprint 讓**退款真實串接 Stripe**（此前 `processRefund` 為 stub、`mockRefund` 為 mock）。**後端聚焦（退款無前端 UI，admin/API 觸發）；只做全額退款；分帳留 Phase D。** V61 續 V59/V60 付款 schema 演進。

---

## 2. 交付內容

- **US-001 + US-002（後端，AI-2412，commit `af1591b`）**：
  - **gateway 真退款**：`StripePaymentGateway.processRefund` 由 stub（假 re_ id、只讀本地）改真——`Refund.create`（`RefundCreateParams` 以 `payment_intent` + 選配 amount；idempotency key），回真 refund id + status；例外對應 E_6006/E_6007。
  - **service toggle-aware**：`PaymentStateService.mockRefund` 重構為 `refundOrderPayment`——canRefund 檢查 + 找 SUCCESS Payment；`STRIPE_PAYMENT_ENABLED` 開啟且 Payment 為 STRIPE → 經 gateway 真退款（以 stripePaymentIntentId 全額）+ 存 `stripe_refund_id`；否則 mock（既有）。標記 Payment REFUNDED + Order REFUNDED。`OrderPaymentController /refund` 改呼叫 refundOrderPayment。
  - **charge.refunded webhook**：`PaymentWebhookService` 加 dispatch `charge.refunded` → 依 payment_intent 找 Payment → `markStripeRefunded`（權威 REFUNDED + refund id + Order REFUNDED，冪等；沿用 V60 event 去重）。
  - **pi metadata 補強**：`createCheckoutSession` 加 `payment_intent_data.metadata.order_id`，使 payment_intent 相關事件（payment_failed / charge.refunded）可由 order_id 可靠對應（補 S51 retro 的 best-effort 缺口）。
  - **V61 migration**：`payments` 加 `stripe_refund_id`（nullable）；`Payment` entity 加 `stripeRefundId`。
  - **測試**：`StripePaymentGatewayTest` +`TC-S006`（WireMock Refund.create）；`PaymentStateServiceStripeTest` +`UT-PAY-STRIPE-005/006/007`（stripe 真退款 / mock 退款 / webhook markStripeRefunded）；`PaymentWebhookServiceTest` +`UT-WH-006`（charge.refunded）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 + checkstyle | ✅ 0 error / BUILD SUCCESS |
| 後端單元（`StripePaymentGatewayTest` 6 + `PaymentStateServiceStripeTest` 7 + `PaymentWebhookServiceTest` 6）| ✅ **19 tests 0 fail** |
| 後端整合（真實 DB，mock/Phase A/B 回歸）| ✅ **21 tests 0 fail**（`OrderPaymentControllerE2ETest` 4 含 /refund mock 路徑 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（V61 stripe_refund_id 與 entity 對齊）|
| 本地 E2E 守門（`make validate-e2e`）| ✅ **54 passed / 6 skipped / 0 failed**（重跑後；後端聚焦無新前端 E2E；相較 S51 持平；V61 全棧啟動驗證通過）。⚠️ 首跑 1 failed = `at-m10-chat` STOMP 即時聊天 happy path（page.click 逾時）— WebSocket 時序 flaky，與 Phase C 純後端退款無關，重跑 54/0 恢復 |
| 前端變動 | 無（退款無前端 UI；admin/API 觸發）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **退款由 stub/mock 改真實**：此前 `StripePaymentGateway.processRefund` 從未呼叫 Stripe（假 re_ id）、`mockRefund` 直接設 REFUNDED；本 Sprint 真實串接 `Refund.create`。退款 toggle-aware（stripe 真退款 / mock 保留）。
2. **只做全額退款**：本 Sprint 全額退款（Order REFUNDED）；**部分退款（partially_refunded）不做**、另評估。
3. **補 Phase B best-effort 缺口**：S51 的失敗/退款事件依 pi id 查找不可靠（Checkout 惰性建 pi）；本 Sprint 於 createCheckoutSession 補 pi metadata order_id，改善事件對應可靠性。
4. **測試以 WireMock（不打真 Stripe）**：`Refund.create` 以 WireMock 攔截驗證；真退款端到端於 Stripe 測試模式人工驗證（上線 checklist AI-2414）。
5. **退款需 payment_intent**：真退款以 stripePaymentIntentId 執行；缺 pi（未經 Phase B 回填）則報錯 E_6001（不假退款）。
6. **validate-e2e 首跑遇 at-m10-chat flaky（誠實揭露）**：首次 validate-e2e 1 failed = `at-m10-chat` M10 即時聊天 STOMP happy path（`page.click` 逾時 30s）——WebSocket 時序 flaky，與 S52 純後端退款變更無關（未觸及 chat/WebSocket）；重跑恢復 54/0。屬既有 STOMP E2E 偶發時序問題，非本 Sprint regression。
7. **push 債累積 S41~S52（12 Sprint）**：本 Sprint 有 schema（V61）+ 金流退款變動 → push 需完整 `make validate-release`。承 S41~S51 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **真實退款**：（測試模式）對已付款訂單觸發 `/refund` → 呼叫 Stripe Refund.create → Payment REFUNDED + 存 refund id + Order REFUNDED（WireMock TC-S006 + UT-PAY-STRIPE-005 佐證）。
- **webhook 權威退款**：外部/非同步退款以 `charge.refunded` 事件權威更新（UT-WH-006）。
- **mock 保留**：toggle 關閉時退款維持 mock（UT-PAY-STRIPE-006），既有 /refund 測試不退步。
- **金流閉環完整度**：付款（Phase A）+ 權威狀態（Phase B）+ 退款（Phase C）皆真實；分帳（Phase D）待決策。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
