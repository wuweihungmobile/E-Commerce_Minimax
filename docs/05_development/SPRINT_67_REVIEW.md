# Sprint 67 Review / Sprint 67 評審會議

> **Sprint 編號**: Sprint 67
> **期間**: 2028-05-07 ~ 2028-05-20
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 多 Sprint 測試強化計劃（第二階段）——`PaymentStateService`（金流核心）單元測試補齊至 10 方法完整覆蓋

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | PaymentStateService 單元測試補齊至 10 方法完整覆蓋 | 8 | ✅ 完成 |

**8 SP 全數完成**。新增 `PaymentStateServiceTest.java`（48 個測試），為 `PaymentStateService` 的 10 個 public 方法補齊正常流程、邊界案例、錯誤路徑三個層次的覆蓋。

---

## 2. 交付內容

- **`PaymentStateServiceTest.java`**（新檔，48 個測試，`UT-PAY-STATE-001`~`048`）：
  - **4 個先前完全零測試的方法**（`getOrderPaymentState`/`getBookingPaymentState`/`mockPaymentSuccess`/`mockPaymentFailure`）：涵蓋成功路徑、資源不存在（E_5000/E_4006）、擁有權檢查（E_1007，含 admin 放行）、狀態不可操作（E_5011）、重複處理（E_6003）。
  - **`markStripePaymentSucceeded`/`markStripePaymentFailed`**：獨立補上直接單元測試（先前 `markStripePaymentSucceeded` 僅被 `confirmStripeCheckout` 間接覆蓋，`markStripePaymentFailed` 完全零測試），涵蓋冪等性（已終態 no-op）、找不到付款記錄、`paymentIntentId`/`orderId` 為 null 的邊界情況。
  - **既有 `PaymentStateServiceStripeTest.java` 已覆蓋方法**（`refundOrderPayment`/`markStripeRefunded`/`initiateStripeCheckout`/`confirmStripeCheckout`）：補齊訂單/付款記錄不存在、擁有權檢查、狀態機不允許操作（E_5012/E_5011）、Stripe 退款缺少 payment intent 或 gateway 回傳失敗（E_6001）等錯誤路徑，以及 `markStripeRefunded` 的冪等與邊界情況。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-023`（booking 付款擁有權檢查缺口，🔴 高優先級，待 PO/Security owner 決策，本 Sprint 僅記錄未修改程式碼）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn verify -Pintegration-test` 的 unit 階段）| ✅ **542 tests，0 fail**（含新增 `PaymentStateServiceTest` 48 個） |
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe）| ✅ **542 + 342 = 884 tests，0 fail** |
| checkstyle-main / checkstyle-test | ✅ 0 violations（過程中攔到 1 次未使用 import，已修正並重跑全量確認 0 fail） |
| `make validate-schema` | ✅ 無漂移（無新 migration） |
| 前端 | 本 Sprint 無前端變動，未執行 |

---

## 4. 誠實揭露（Rule 12）

1. **Sprint 66 規劃時「`PaymentStateService` 完全零測試」的認知有落差**：實際上既有 `PaymentStateServiceStripeTest.java`（Sprint 50/52/56 建立）已有 11 個測試覆蓋 4 個方法的 happy-path，另有 `M07PaymentMockIntegrationTest` 做 Controller 層端對端驗證。真正完全零單元測試的只有 6 個方法。本 Sprint 目標調整為「補齊完整覆蓋」，詳見 `SPRINT_67_PLAN.md` 前置條件確認表。
2. **`mvn verify` 再次攔到 pre-commit 未涵蓋的 checkstyle-test 違規**（與 Sprint 66 相同教訓）：`PaymentStateServiceTest.java` 初版含未使用的 import（`CheckoutSessionResponse`，因邊界案例測試呼叫 `initiateStripeCheckout` 但不需要接收/斷言其回傳值）。全量 `mvn verify -Pintegration-test` 的 `checkstyle-test` execution 攔下，已修正並重跑全量整合測試確認 884 tests 0 fail。
3. **發現 booking 付款擁有權檢查缺口，本 Sprint 未修改**：撰寫 `getBookingPaymentState` 測試時發現該方法（以及 `BookingService.getBooking/updateBooking`、`PaymentService.processBookingPayment`）完全沒有擁有權檢查，而 `booking:read` 權限連 GUEST 角色都持有。過去 `DEF-018/019` 已修復 Order 側同類 IDOR，commit 訊息當時明確記載「booking 付款側非本次範圍，如需另立項目評估」，但後續未見對應票證關閉。已登記為 `DEF-023`（🔴 高優先級），依任務指示不自行假設並修改，待 PO/Security owner 決策修復範圍與時程。
4. **未發現需修正的 `PaymentStateService` 生產程式碼真實 bug**：與 Sprint 66（`ProductService` 搜尋死碼）不同，本 Sprint 撰寫的 48 個測試在既有生產程式碼下全數通過，未修改任何 `PaymentStateService` 生產程式碼。

---

## 5. Demo 重點

- **金流核心 10 個方法全數具備自動化回歸保護**：`getOrderPaymentState`/`getBookingPaymentState`/`mockPaymentSuccess`/`mockPaymentFailure`/`refundOrderPayment`/`markStripeRefunded`/`initiateStripeCheckout`/`confirmStripeCheckout`/`markStripePaymentSucceeded`/`markStripePaymentFailed` 皆有正常流程+邊界+錯誤路徑測試，未來修改退款/對帳邏輯時可立即發現回歸。
- **揪出但未修復的授權缺口記錄在案**：`DEF-023` 已寫入追蹤器並標記高優先級，供下一輪規劃參考，不因本 Sprint 聚焦測試而遺漏。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
