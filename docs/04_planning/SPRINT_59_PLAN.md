# Sprint 59 計劃 / Sprint 59 Plan

> **Sprint 編號**: Sprint 59
> **期間**: 2028-01-16 ~ 2028-01-29 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 活躍延後項目（AI-2417，Sprint 56 探勘時發現、本次使用者授權立案）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 前置技術調查 | ✅ 完成：`PaymentController`（`/v2/payments/*`，純 Mock）與 `OrderPaymentController`（`/v2/orders/{id}/refund`，真 Stripe）**目前皆無任何前端呼叫端**（僅後端測試呼叫）；`PaymentService` vs `PaymentStateService` 是整個類別層級平行（processPayment/refund/getStatus 三者皆重複），非僅 refund 一個方法 |
| **關鍵發現：非單純重複，而是功能缺口** | ✅ 已確認：`PaymentStateService.refundOrderPayment` 只支援 **Order**（訂單/商品購買）退款；**Booking**（訂房）退款目前完全依賴舊的 `PaymentService` mock 路徑，`PaymentStateService` 無對應的 `refundBookingPayment`。回顧 `PAYMENT_INTEGRATION_ASSESSMENT.md`（Sprint 49）與後續 AI-2410~2416 所有實作，真實 Stripe 金流（Phase A~D）自始至終**只涵蓋 Order 流程，從未涵蓋 Booking**——這不是意外重複，而是金流專案從 Sprint 49 起的既定範圍（Booking 付款/退款一直維持 mock，非本次才形成的技術債）| 此為觀察結果的釐清，非本 Sprint 決策 |
| Sprint 類型 | ✅ Spike（評估型）：產出決策文件，**不改 production code**——因兩端點皆無前端呼叫端，貿然整合/刪除任一方屬無需求驅動的變更；若要讓 Booking 退款也走真實 Stripe，屬於「擴大金流範圍」的新功能決策，超出本次「純技術債整合評估」範疇，應另立項目由 PO 決定是否需要 | 與 AI-2409（定價計算器）相同的 Spike 模式 |
| Push 狀態 | ✅ 依新節奏，本 Sprint 收尾後立即 push | 不累積多 Sprint |

---

## 1. Sprint 59 目標

> **主題**: 純 Mock 退款路徑與真 Stripe 退款路徑整合評估

探勘 `PaymentService`（純 Mock，涵蓋 Order+Booking）與 `PaymentStateService`（Order 真 Stripe，Sprint 56 起支援部分退款）兩套付款/退款邏輯的重疊與差異範圍，判斷是否值得整合，產出決策文件供未來參考。

---

## 2. User Story

### US-001：退款路徑整合評估（AI-2417，Spike）

> **SP**: 3 | **優先級**: P4 | **狀態**: ✅ 完成

**AC-001-1**: 確認 `PaymentController`（`/v2/payments/*`）與 `OrderPaymentController`（`/v2/orders/{id}/refund`）目前皆無前端呼叫端（僅後端測試涵蓋）。

**AC-001-2**: 確認重疊範圍是整個服務類別（`processPayment`/`processRefund`/`getPaymentStatus` 三者皆有平行版本），非單一方法。

**AC-001-3**: 確認關鍵差異：`PaymentStateService` 僅完整支援 Order 真實 Stripe 金流（含 Sprint 56 部分退款）；Booking 退款/付款目前完全仰賴 `PaymentService` mock 路徑，且此為 Sprint 49 金流評估以來的既定範圍（Phase A~D 從未涵蓋 Booking），非意外遺漏。

**AC-001-4**: 產出決策文件 `docs/06_quality/REFUND_PATH_CONSOLIDATION_ASSESSMENT.md`，記錄上述發現、選項比較、建議。

**AC-001-5**: 本 Sprint **不修改 production code**（純評估，因兩端點目前皆無使用者流量，任何整合/刪除都是無需求驅動的臆測性變更）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 退款路徑整合評估（AI-2417，Spike）| 3 | P4 |

---

## 4. Definition of Done

- [x] US-001：`REFUND_PATH_CONSOLIDATION_ASSESSMENT.md` 完成
- [x] 不改動 production code
- [ ] `DEFERRED_ITEMS_TRACKER.md` AI-2417 狀態更新
- [ ] Sprint 59 Review / Retro / Release Notes + trackers

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 決策文件 | `docs/06_quality/REFUND_PATH_CONSOLIDATION_ASSESSMENT.md`（新）|
| 文件更新 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md`（AI-2417 狀態）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無程式碼變動**（純評估型 Spike）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
