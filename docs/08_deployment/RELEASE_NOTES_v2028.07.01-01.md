# Release Notes - v2028.07.01-01 (Sprint 70)

**發布日期**: 2028-07-01（規劃）／實作完成 2026-07-05
**發布類型**: 🔴 緊急安全修復（P0；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 70（緊急插隊，優先於例行測試強化排程）
**狀態**: ✅ 已 push

> Sprint 70 主題：**修復 `OrderService.updateOrderStatus` 跨租戶 IDOR（`DEF-024`）**。比照 Sprint 68（`DEF-023`）處理模式，插隊優先於例行測試強化排程。

---

## 🔴 安全修復 P0

- **`DEF-024`：`OrderService.updateOrderStatus` 無擁有權/租戶檢查（跨租戶 IDOR）**：
  - **修復前**：同檔案內 `getOrder`/`cancelOrder`/`getOrderStateLogs` 皆有 owner-or-admin 擁有權檢查，唯獨 `updateOrderStatus` 完全沒有；`order:update` 權限由 SELLER/STORE_OWNER/ADMIN/SUPER_ADMIN 四種角色持有（非僅平台 ADMIN），系統又是多租戶各自有 SELLER，形同**任一租戶的賣家可對任意 orderId（含其他租戶的訂單）執行狀態轉換**——跨租戶 IDOR。
  - **業務情境分析**：與 Sprint 68 的 Booking 案例不同，`updateOrderStatus` 有兩種正當呼叫情境並存——(1) 買家透過付款流程觸發（`PaymentService.processOrderPayment` 內部呼叫，已受 `checkOrderPaymentOwnership` 保護在前）、(2) 賣家/店主透過 `PATCH /v2/orders/{orderId}/status` 直接呼叫（管理自己租戶訂單，如標記出貨）。動手修改前先完成分析並取得使用者確認，避免修復安全漏洞卻誤擋合法賣家操作。
  - **修復後**：新增 `checkOrderStatusUpdateAuthorization` helper，採「訂單擁有者本人（比照 `DEF-018` 買家模式）or 本租戶（比照 `LogisticsService.checkOrderTenant` 的 `DEF-019` 賣家模式）or admin」三者其一放行，越權回 403/`E_1007`，置於狀態機轉換檢查之前避免洩漏訂單狀態。

## 測試 / 驗證 ✅

- **`OrderServiceTest` 新增 3 個測試**：他租戶賣家 403（且先於狀態機檢查觸發）、本租戶賣家放行（續走狀態機轉換 `CONFIRMED→SHIPPING`）、admin 跨租戶放行。既有買家付款流程回歸測試（`updateOrderStatus_validTransition`/`updateOrderStatus_invalidTransition_throwsE5001`）維持通過，證明修復未破壞既有合法流程。
- **後端單元**（`mvn verify -Pintegration-test` 的 unit 階段）：**606 tests，0 fail**（含本 Sprint 新增 3 個）。
- **後端完整整合**（`mvn verify -Pintegration-test`，含 failsafe）：**606 + 342 = 948 tests，0 fail**。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。
- **開發-編譯-測試循環**：修復完成後立即編譯 + 執行 `OrderServiceTest` 驗證既有測試不受影響，再撰寫新增測試並再次驗證，未累積。

## 技術決策 / 已知限制 ⚠️

- **Service 層防禦縱深不額外檢查角色**：`checkOrderStatusUpdateAuthorization` 的「same-tenant」放行條件僅比對租戶 ID，不重複檢查 `order:update` 權限本身（該分工交由 Controller 層 `@PreAuthorize` 負責）。刻意設計而非疏漏，詳見 `SPRINT_70_REVIEW.md`。
- **無前端變動**：`PATCH /v2/orders/{orderId}/status` 目前尚無對應的賣家管理前端頁面，本次修復僅涉及後端 service 層授權邏輯。

## 資料庫遷移 🗄️

- 無（schema-free；純 Java service 層邏輯變更）。

## 內含 Commit（Sprint 70）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 70 Plan | （見下方實際 commit hash）| DEF-024 跨租戶 IDOR 緊急修復計劃（1 US / 3 SP）|
| US-001 | （見下方實際 commit hash）| `checkOrderStatusUpdateAuthorization` 三選一放行邏輯 + 3 個新增測試 |
| Sprint 70 收尾 | （見下方實際 commit hash）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
