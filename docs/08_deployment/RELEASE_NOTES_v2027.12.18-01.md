# Release Notes - v2027.12.18-01 (Sprint 56)

**發布日期**: 2027-12-18（規劃）／實作完成 2026-07-04
**發布類型**: Minor（部分退款；含 schema 變更 V63；後端聚焦）
**Sprint**: Sprint 56
**狀態**: ⏳ 待 push（本 Sprint commit；於檢查點徵詢後連同 S41~S56 一併 push，嚴禁 `--no-verify`）

> Sprint 56 主題：**部分退款（Partial Refund）——任意金額，運費不退**。承 Sprint 52（Phase C 全額退款），本 Sprint 讓 `refundOrderPayment` 支援指定任意金額的部分退款，PO 已就粒度（任意金額）與運費政策（不退）做出決策。

---

## 新功能 / 改進 🚀

- **部分退款金額支援（AI-2415）**：`PaymentStateService.refundOrderPayment` 新增 `amount` 參數，未指定時向後相容退剩餘全額；指定時驗證正數且不超過剩餘可退額度。
- **累計退款追蹤（AI-2415）**：`Payment` 新增 `refundedAmount` 欄位，累計已退款金額；達 `Payment.amount` 全額才轉 `REFUNDED` + `Order.REFUNDED`，未達轉新狀態 `PARTIALLY_REFUNDED`（訂單狀態不變，持續履約）。
- **API（AI-2415）**：`POST /v2/orders/{orderId}/refund` 新增 `amount`（optional）request param；`OrderPaymentStateDto` 新增 `refundedAmount` 供查詢剩餘可退額度。

## 測試 / 驗證 ✅

- **後端單元**：`PaymentStateServiceStripeTest` 新增 UT-PAY-STRIPE-008~011（部分退款/補足全額/超額拒絕/非正數拒絕）+ 既有 005/006 更新為新簽章，**全量 512 tests，0 fail**。
- **後端整合（真實 DB）**：`M07PaymentMockIntegrationTest` 8 tests 既有退款測試不退步，**全量 419 tests，0 fail**。
- **schema 漂移守門（`make validate-schema`）**：無漂移（V63 與 entity 對齊）。
- **前端變動**：無。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **PO 決策**：部分退款粒度＝任意金額（非選品項）；運費不退（僅全額退款時隨訂單一併結束）。
- **只修真實 Stripe 退款路徑**：平行的純 Mock 退款路徑（`PaymentService.processRefund`）未修改，屬既有技術債，記錄不修。
- **`stripeRefundId` 單一欄位限制**：僅存最後一次退款 id，多次部分退款完整歷史需獨立子表，本次不做。
- **webhook 路徑未同步**：`charge.refunded` webhook 仍假設全額退款，未解析部分退款金額。

## 資料庫遷移 🗄️

- **V63__Add_Refunded_Amount_To_Payments.sql**：`payments` 加 `refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0`。Flyway V62 → **V63**。

## 內含 Commit（Sprint 56）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 56 Plan | 8210df1 | 部分退款計劃（1 US / 5 SP，補寫於實作完成後）|
| US-001 AI-2415 | aae96bd | V63 migration + refundOrderPayment 部分退款邏輯 + API + DTO + 測試 |
| Sprint 56 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
