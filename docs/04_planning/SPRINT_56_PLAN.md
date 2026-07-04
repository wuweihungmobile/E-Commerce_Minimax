# Sprint 56 計劃 / Sprint 56 Plan

> **Sprint 編號**: Sprint 56
> **期間**: 2027-12-05 ~ 2027-12-18 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 活躍延後項目（AI-2415）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: AI-2415「部分退款」自 Sprint 52（Phase C 只做全額退款）延後至今，前置調查確認 Stripe gateway 層已支援金額參數，僅上層硬傳 null；規劃前先徵詢 PO 2 項業務決策（見下）

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 前置技術調查 | ✅ 完成：`PaymentStateService.refundOrderPayment` 硬傳 `null` 給 `paymentGatewayFactory.processRefund`（gateway 層已支援 amount）；`Payment` 無 `refundedAmount`/`PARTIALLY_REFUNDED`；`OrderItem` 有 `unitPrice`/`quantity` 但無退款追蹤欄位；另存在一條平行的純 Mock 退款路徑（`PaymentService.processRefund`，未接真 Stripe），與本次修改的 `PaymentStateService.refundOrderPayment` 為兩套獨立邏輯 | 詳見程式碼註解與 commit 說明 |
| **PO 決策 1：部分退款粒度** | ✅ **任意金額**（非選品項退款）| 2026-07-04 規劃前徵詢；符合 Stripe 退款本質為金額導向、實作最簡單 |
| **PO 決策 2：運費退款政策** | ✅ **運費不退**（僅全額退款時運費隨訂單一併結束）| 2026-07-04 規劃前徵詢；與多數電商平台預設行為一致，邏輯最簡單 |
| 範圍決策 | ✅ 僅修改真實 Stripe 退款路徑（`PaymentStateService.refundOrderPayment` / `OrderPaymentController`），**不動平行的純 Mock 路徑**（`PaymentService.processRefund`），避免範圍蔓延；此為既有技術債，已於程式碼註解與本文件記錄，非本次引入 | 精準改動原則（Rule 3）|
| Push 狀態 | ⏸️ 維持批次 push 決策，累積延伸至 S41~S56 | 不影響本 Sprint 開發 |

---

## 1. Sprint 56 目標

> **主題**: 部分退款（Partial Refund）——任意金額，運費不退

目前退款（Sprint 52 AI-2412）只支援全額。本 Sprint 讓 `PaymentStateService.refundOrderPayment` 支援指定任意金額的部分退款：累計追蹤 `Payment.refundedAmount`，達 `Payment.amount` 全額才轉終態 `REFUNDED` + `Order.REFUNDED`；未達全額則轉 `PARTIALLY_REFUNDED`，訂單狀態維持不變（持續履約）。運費（`Order.shippingFee`）不參與部分退款計算。

---

## 2. User Story

### US-001：後端——部分退款支援（AI-2415）

> **SP**: 5 | **優先級**: P4 | **狀態**: ✅ 完成

**AC-001-1**: Migration V63 `payments` 表新增 `refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0`；`Payment` entity 對應欄位；`PaymentStatus` enum 新增 `PARTIALLY_REFUNDED`（VARCHAR 欄位無 CHECK 常數限制於 Java 層，schema 免異動於狀態值本身，僅需新增金額欄位）。

**AC-001-2**: `PaymentStateService.refundOrderPayment` 簽章擴充為 `(UUID orderId, BigDecimal amount, String reason)`：
- `amount` 為 `null` 時向後相容退剩餘全額；指定時驗證須為正數且不超過剩餘可退額度（`payment.amount - payment.refundedAmount`），否則丟 `E_6009`。
- 支援對已 `SUCCESS` 或已 `PARTIALLY_REFUNDED` 的付款重複呼叫（直到全額退完）。
- Stripe 路徑：`paymentGatewayFactory.processRefund` 改傳實際 `refundAmount`（gateway 層 `StripePaymentGateway.processRefund` 早已支援 amount 參數，本次僅修正上層呼叫）。
- 累計 `refundedAmount`；達全額轉 `REFUNDED` + `Order.REFUNDED`，未達轉 `PARTIALLY_REFUNDED`（Order 狀態不變）。
- 為符合 NPathComplexity 規範，抽出 `resolveRefundAmount`/`executeStripeRefund` 兩個私有輔助方法。

**AC-001-3**: `OrderPaymentController` 的 `POST /v2/orders/{orderId}/refund` 端點新增 `amount`（`BigDecimal`，optional）request param。

**AC-001-4**: `OrderPaymentStateDto` 新增 `refundedAmount` 欄位，供呼叫端查詢剩餘可退額度。

**AC-001-5**: 測試——`PaymentStateServiceStripeTest` 新增 UT-PAY-STRIPE-008~011（部分退款轉 PARTIALLY_REFUNDED、第二次部分退款補足全額轉 REFUNDED、金額超過剩餘拋 E_6009、金額為零/負數拋 E_6009）；既有 UT-PAY-STRIPE-005/006（全額退款）更新為新簽章並驗證傳給 gateway 的金額為剩餘全額（向後相容）；`M07PaymentMockIntegrationTest` 既有真 DB 整合測試不退步。

**誠實揭露**：
- `stripeRefundId` 欄位僅存最後一次退款 id，多次部分退款的完整歷史需獨立子表（`payment_refunds`），本次不做。
- webhook 路徑（`markStripeRefunded`，對應 Stripe `charge.refunded` 事件）仍假設全額退款、未解析部分退款金額，本次僅修改同步呼叫路徑（`refundOrderPayment`）；若未來需支援 webhook 驅動的部分退款狀態同步，需另立項目。
- 平行的純 Mock 退款路徑（`PaymentService.processRefund`，`/v2/payments/refund`）未於本次修改，是否需要與本次修改的路徑整合另立技術債評估。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 部分退款支援（AI-2415）| 5 | P4 |

> **Velocity 參考**：`DEFERRED_ITEMS_TRACKER.md` 原估 3 SP；前置調查發現需新增 migration + enum + 兩個新輔助方法 + 4 個新測試案例 + 2 個既有測試更新，規模略高於原估，收斂至 5 SP（貼近歷史區間下緣）。

---

## 4. Definition of Done

- [x] US-001：V63 migration + entity + `refundOrderPayment` 部分退款邏輯 + API 端點 + DTO 欄位
- [x] 後端單元 512 tests 0 fail（含新增 4 + 更新 2）
- [x] 真 DB 整合 419 tests 0 fail（含 `M07PaymentMockIntegrationTest` 既有退款測試不退步）
- [x] `make validate-schema` 無漂移（V63）
- [ ] `DEFERRED_ITEMS_TRACKER.md` AI-2415 狀態更新
- [ ] Sprint 56 Review / Retro / Release Notes + trackers

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| Migration | `backend/src/main/resources/db/migration/V63__Add_Refunded_Amount_To_Payments.sql` |
| 後端 Entity | `domain/model/payment/Payment.java`（`refundedAmount` 欄位 + `PARTIALLY_REFUNDED` enum 值）|
| 後端 Service | `core/payment/PaymentStateService.java`（`refundOrderPayment` 擴充 + `resolveRefundAmount`/`executeStripeRefund` 輔助方法）|
| 後端 API | `api/controller/OrderPaymentController.java`（`/refund` 端點加 `amount` 參數）|
| 後端 DTO | `api/dto/payment/OrderPaymentStateDto.java`（新增 `refundedAmount`）|
| 錯誤碼 | `shared/exception/ErrorCode.java`（新增 `E_6009`）|
| 後端測試 | `PaymentStateServiceStripeTest.java`（UT-PAY-STRIPE-008~011 新增 + 005/006 更新）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無前端變動**（僅後端 API + DTO 擴充；前端串接視未來容量另評估）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
