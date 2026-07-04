# Sprint 56 Review / Sprint 56 評審會議

> **Sprint 編號**: Sprint 56
> **期間**: 2027-12-05 ~ 2027-12-18
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 部分退款（Partial Refund）——任意金額，運費不退

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端——部分退款支援（AI-2415）| 5 | ✅ 完成 |

**5 SP 全數完成**。承 Sprint 52（Phase C 全額退款），本 Sprint 讓 `PaymentStateService.refundOrderPayment` 支援指定任意金額的部分退款，PO 已就粒度（任意金額）與運費政策（不退）做出決策。

---

## 2. 交付內容

- **Migration V63**：`payments` 表新增 `refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0`。
- **`Payment.PaymentStatus`**：新增 `PARTIALLY_REFUNDED`。
- **`PaymentStateService.refundOrderPayment`**：簽章擴充為 `(orderId, amount, reason)`；`amount=null` 向後相容退剩餘全額；指定金額時驗證正數且不超過剩餘可退額度（逾越丟 `E_6009`）；支援對 `SUCCESS`/`PARTIALLY_REFUNDED` 付款重複呼叫直到全額退完；累計 `refundedAmount`，達全額轉 `REFUNDED`+`Order.REFUNDED`，未達轉 `PARTIALLY_REFUNDED`（Order 狀態不變）；抽出 `resolveRefundAmount`/`executeStripeRefund` 兩個私有方法（消解 NPathComplexity checkstyle 超標）。
- **`OrderPaymentController`**：`/refund` 端點新增 `amount`（optional）request param。
- **`OrderPaymentStateDto`**：新增 `refundedAmount` 欄位。
- **`ErrorCode`**：新增 `E_6009`（Invalid refund amount）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error（含 checkstyle NPathComplexity 修正）|
| 後端單元（`PaymentStateServiceStripeTest`）| ✅ **11 tests，0 fail**（新增 UT-PAY-STRIPE-008~011 + 既有 005/006 更新為新簽章）|
| 全量後端單元（`mvn test -Dtest="com.nextkey.ecommerce.core.**"`）| ✅ **512 tests，0 fail** |
| 全量後端整合（真實 DB）| ✅ **419 tests，0 fail**（含 `M07PaymentMockIntegrationTest` 8 tests 既有退款測試不退步）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（V63）|
| 前端變動 | 無 |
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **只修真實 Stripe 退款路徑**：`PaymentService.processRefund`（`/v2/payments/refund`，純 Mock、未接 Stripe）為既有的平行退款邏輯，本 Sprint 未修改，已記錄為技術債現況，非本次引入。
2. **`stripeRefundId` 單一欄位限制**：僅存最後一次退款 id；若同一筆付款發生多次部分退款，先前退款的 Stripe id 會被覆蓋。完整多筆退款歷史需獨立 `payment_refunds` 子表，本次未做（範圍決策已於規劃前確認）。
3. **webhook 路徑未同步更新**：`markStripeRefunded`（對應 Stripe `charge.refunded` 事件）仍假設全額退款，未解析事件中的部分退款金額；本次僅修改同步呼叫路徑（`refundOrderPayment`）。
4. **PO 決策執行範圍**：任意金額粒度、運費不退，皆為規劃前經 `AskUserQuestion` 明確徵詢後的決策，非 AI 自行假設。

---

## 5. Demo 重點

- **部分退款**：`UT-PAY-STRIPE-008` 示範退 500/1500 元 → `PARTIALLY_REFUNDED`，Order 維持 `PAID`。
- **補足全額**：`UT-PAY-STRIPE-009` 示範對已部分退款的付款再退 1000 元補足 1500 全額 → 轉 `REFUNDED` + Order `REFUNDED`。
- **金額驗證**：`UT-PAY-STRIPE-010`/`011` 示範超額或非正數金額皆拋 `E_6009`，不呼叫 Stripe gateway。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
