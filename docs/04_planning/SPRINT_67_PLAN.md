# Sprint 67 計劃 / Sprint 67 Plan

> **Sprint 編號**: Sprint 67
> **期間**: 2028-05-07 ~ 2028-05-20 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `SPRINT_66_PLAN.md` 第 6 節「後續 Sprint 待處理清單」建議之 Sprint 67 目標——`PaymentStateService`（金流核心）測試強化
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 66 收尾狀態 | ✅ 已收尾並 push（commit `bda32f1`） | Review/Retro/Release Notes 齊備 |
| `PaymentStateService` 方法盤點 | ✅ 完成：共 **10 個 public 方法**——`getOrderPaymentState`、`getBookingPaymentState`、`mockPaymentSuccess`、`mockPaymentFailure`、`refundOrderPayment`、`markStripeRefunded`、`initiateStripeCheckout`、`confirmStripeCheckout`、`markStripePaymentSucceeded`、`markStripePaymentFailed` | 與 `SPRINT_66_PLAN.md` 第 6 節所述「10 方法」數量一致 |
| ⚠️ **修正前置認知落差** | Sprint 66 規劃當下的措辭「`PaymentStateService` 完全零測試」**經本 Sprint 實查證實不準確**：既有 `PaymentStateServiceStripeTest.java`（Sprint 50/52/56 建立，11 個測試）已覆蓋 `initiateStripeCheckout`/`confirmStripeCheckout`/`refundOrderPayment`/`markStripeRefunded` 4 個方法的 happy-path 與部分退款情境；另有 `M07PaymentMockIntegrationTest`（Controller 層整合測試）涵蓋 mock 付款/退款流程。**真正完全零單元測試的方法只有 6 個**：`getOrderPaymentState`、`getBookingPaymentState`、`mockPaymentSuccess`、`mockPaymentFailure`、`markStripePaymentSucceeded`（僅被間接覆蓋、未獨立驗證）、`markStripePaymentFailed`。已測方法也普遍缺少擁有權（E_1007）、資源不存在（E_5000/E_6000）等錯誤路徑覆蓋 | 依 Rule 12「大聲失敗」誠實揭露此落差，Sprint 目標調整為「補齊完整覆蓋」而非「從 0 建立」，範疇不變（仍是同一個高風險金流 class） |
| 額外發現：授權缺口（待人工決策，本 Sprint **不**修改） | ⚠️ 調查 `getBookingPaymentState` 時發現：該方法（以及同模組的 `BookingService.getBooking/updateBooking`、`PaymentService.processBookingPayment`）**完全沒有擁有權檢查**，而 `booking:read` 權限連 `GUEST` 角色都持有，形同任何登入者可查詢/操作他人 booking 的付款狀態。此為 Booking 模組系統性缺口，非 `PaymentStateService` 單一方法問題。過去 `DEF-018/019`（Sprint 33/36）已修復 Order 側同類 IDOR，commit 訊息當時**明確記載**「booking 付款側非本次範圍，如需另立項目評估」，但後續未見對應票證關閉。已登記為 `DEF-023`（見 `DEFERRED_ITEMS_TRACKER.md`），**待 PO/Security owner 決策是否及何時修復**，本 Sprint 僅記錄不修改 | 屬於超出「幫 PaymentStateService 補測試」授權範圍的架構/安全決策，且影響面跨多個 Service，依任務指示不自行假設並修改 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 67 目標

> **主題**: 多 Sprint 測試強化計劃（第二階段）——`PaymentStateService`（金流核心）單元測試補齊至 10 方法完整覆蓋

為金流核心 `PaymentStateService` 補齊單元測試缺口：6 個完全零測試的方法從 0 建立，另外 4 個已有 happy-path 測試的方法補齊擁有權/資源不存在/退款失敗等錯誤路徑，目標是 10 個方法均具備正常流程 + 邊界案例 + 錯誤路徑的完整測試覆蓋。

---

## 2. User Story

### US-001：PaymentStateService 單元測試補齊至 10 方法完整覆蓋

> **SP**: 8 | **優先級**: P1 | **狀態**: ✅ 完成

**AC-001-1**：新增 `PaymentStateServiceTest.java`，為 `getOrderPaymentState`、`getBookingPaymentState`、`mockPaymentSuccess`、`mockPaymentFailure` 這 4 個先前完全零測試的方法建立完整單元測試，涵蓋成功路徑、資源不存在（E_5000/E_4006）、擁有權檢查（E_1007，含 admin 放行）、狀態不可操作（E_5011）、重複處理（E_6003）等案例。

**AC-001-2**：為 `markStripePaymentSucceeded`、`markStripePaymentFailed` 建立**獨立**單元測試（先前 `markStripePaymentSucceeded` 僅透過 `confirmStripeCheckout` 間接覆蓋，`markStripePaymentFailed` 完全零測試），涵蓋冪等性（已終態 no-op）、找不到付款記錄、`paymentIntentId`/`orderId` 為 null 的邊界情況。

**AC-001-3**：補齊既有 `PaymentStateServiceStripeTest.java` 已覆蓋方法（`refundOrderPayment`、`markStripeRefunded`、`initiateStripeCheckout`、`confirmStripeCheckout`）遺漏的錯誤路徑：訂單/付款記錄不存在、擁有權檢查、狀態機不允許操作（E_5012/E_5011）、Stripe 退款缺少 payment intent 或 gateway 回傳失敗（E_6001）、`markStripeRefunded` 的冪等與邊界情況。

**AC-001-4**：測試風格比照既有 `PaymentStateServiceStripeTest`/`AuthServiceTest`，使用 Mockito mock repository/`FeatureToggleService`/`PaymentGatewayFactory`（Stripe API 呼叫全程 mock，不打真實 Stripe API），沿用 `UT-PAY-STRIPE-XXX` 之外另建 `UT-PAY-STATE-XXX` 編號避免衝突。

**AC-001-5**：過程中若發現 `PaymentStateService` 生產程式碼真實 bug 則修正並記錄；若發現行為疑似缺陷但無法確定是否為刻意設計（如本 Sprint 發現的 booking 擁有權缺口），停止修改、記錄為待決策項目（`DEF-023`），不自行假設。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | PaymentStateService 單元測試補齊至 10 方法完整覆蓋 | 8 | P1 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60-66（皆 8 SP）之單 Sprint 產能。金流核心風險較高，單一 Sprint 聚焦一個 Service 的完整覆蓋，不與其他模組並行。

---

## 4. Definition of Done

- [x] US-001：新增 `PaymentStateServiceTest.java`（48 個測試，涵蓋 10 個方法的正常/邊界/錯誤路徑）
- [x] 確認**未修改任何 `PaymentStateService` 生產程式碼**（本 Sprint 測試撰寫過程未發現需修正的真實 bug；`getBookingPaymentState` 擁有權缺口記錄為 `DEF-023`，不在本 Sprint 修改）
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）**542 + 342 = 884 tests，0 fail**（含撰寫過程中發現並修正的 checkstyle-test 未使用 import，修正後重跑全量確認 0 fail）
- [x] `make validate-schema` 無漂移（本 Sprint 無 migration）
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-023`（booking 付款擁有權缺口，待 PO 決策）
- [x] Sprint 67 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `PaymentStateServiceTest.java`（新檔，48 個測試） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 DEF-023） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動、無生產程式碼變動**（純測試補強 + 一項待決策發現的文件化記錄）。

---

## 6. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 68+ 規劃參考：

1. **Sprint 68（建議）**：`OrderService`（7 方法，訂單狀態機核心）
2. **Sprint 69（建議）**：`BookingService` + `RoomCalendarService`（訂房核心，含 idempotency）。**建議一併評估 `DEF-023`**（booking 付款擁有權缺口），因修復需同時碰觸 `BookingService`/`PaymentService`/`PaymentStateService` 三處，適合與此 Sprint 的既有工作範圍一併規劃
3. **Sprint 70+（建議）**：ERP 模組整體（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在）
4. 其餘：`ReviewService`（14 方法）、`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
