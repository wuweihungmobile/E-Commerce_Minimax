# Release Notes - v2028.05.20-01 (Sprint 67)

**發布日期**: 2028-05-20（規劃）／實作完成 2026-07-05
**發布類型**: Patch（測試強化；schema-free；後端聚焦，無前端變動；未改生產程式碼）
**Sprint**: Sprint 67
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 67 主題：**多 Sprint 測試強化計劃（第二階段）——PaymentStateService（金流核心）單元測試補齊至 10 方法完整覆蓋**。

---

## 新功能 / 改進 🚀

- **`PaymentStateService` 單元測試補齊**：新增 `PaymentStateServiceTest.java`，48 個 Mockito 單元測試，為金流核心 `PaymentStateService` 的 10 個 public 方法（`getOrderPaymentState`/`getBookingPaymentState`/`mockPaymentSuccess`/`mockPaymentFailure`/`refundOrderPayment`/`markStripeRefunded`/`initiateStripeCheckout`/`confirmStripeCheckout`/`markStripePaymentSucceeded`/`markStripePaymentFailed`）補齊正常流程、邊界案例、錯誤路徑（E_5000/E_4006/E_1007/E_5011/E_5012/E_6000/E_6001/E_6003）三層覆蓋。

## 測試 / 驗證 ✅

- **後端單元**：`mvn verify -Pintegration-test` unit 階段 **542 tests，0 fail**（含新增 `PaymentStateServiceTest` 48 個）。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe）**542 + 342 = 884 tests，0 fail**。
- **checkstyle-main / checkstyle-test**：0 violations（過程中攔到 1 次未使用 import，已修正並重跑全量確認 0 fail）。
- **schema 漂移守門**：`make validate-schema` 無漂移（無新 migration）。
- **前端**：本 Sprint 無前端變動，未執行。

## 技術決策 / 已知限制 ⚠️

- **未修改任何 `PaymentStateService` 生產程式碼**：與 Sprint 66（`ProductService` 搜尋死碼修復）不同，本 Sprint 新增的 48 個測試在既有生產程式碼下全數通過，純測試補強。
- **Sprint 66 規劃當下「`PaymentStateService` 完全零測試」的措辭有落差**：實查發現既有 `PaymentStateServiceStripeTest.java`（Sprint 50/52/56）已覆蓋 4 個方法的 happy-path（11 個測試）。本 Sprint 實際新增/補齊的是先前完全零測試的 6 個方法，以及既有 4 個方法遺漏的錯誤路徑。
- **發現但未修復：booking 付款擁有權檢查缺口（DEF-023）**：`PaymentStateService.getBookingPaymentState`（以及 `BookingService.getBooking/updateBooking`、`PaymentService.processBookingPayment`）完全沒有擁有權檢查，`booking:read` 權限連 GUEST 角色都持有。過去 `DEF-018/019` 已修復 Order 側同類 IDOR 但當時明確排除 booking 側「另立項目評估」，後續未見票證關閉。已登記 `DEF-023`（🔴 高優先級），待 PO/Security owner 決策修復範圍與時程，本 Sprint 僅記錄不修改（修復涉及跨 Service，超出本 Sprint「補測試」授權）。
- **pre-commit 與 `mvn verify` checkstyle 檢查範疇落差（與 Sprint 66 相同教訓再次發生）**：`PaymentStateServiceTest.java` 初版含未使用 import，pre-commit（僅檢查 `src/main`）未攔截，於全量 `mvn verify -Pintegration-test` 的 `checkstyle-test` execution 才被抓到，已修正並重新驗證通過。

## 資料庫遷移 🗄️

- 無（schema-free）。

## 內含 Commit（Sprint 67）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 67 Plan | 39794de | PaymentStateService 測試補齊計劃（1 US / 8 SP）|
| US-001 | 55aa7c1 | 新增 PaymentStateServiceTest（48 個測試）+ DEF-023 記錄 |
| Sprint 67 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
