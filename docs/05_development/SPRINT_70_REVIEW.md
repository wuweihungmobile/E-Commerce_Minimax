# Sprint 70 Review / Sprint 70 評審會議

> **Sprint 編號**: Sprint 70
> **期間**: 2026-07-05（緊急插入，優先於例行排程）
> **評審日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 緊急安全修復——`OrderService.updateOrderStatus` 跨租戶 IDOR（`DEF-024`）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `updateOrderStatus` 擁有權/租戶檢查修復（DEF-024） | 3 | ✅ 完成 |

**3 SP 全數完成**。比照 Sprint 68（`DEF-023`）處理模式，插隊優先於例行測試強化排程。

---

## 2. 業務情境分析與修復設計（本 Sprint 核心）

`updateOrderStatus` 與 Sprint 68 的 Booking 案例不同——動手修改前先完成完整業務情境探查，確認有兩種正當呼叫路徑並存：

1. **買家付款流程（內部呼叫）**：`PaymentController`（`order:create` 權限）→ `PaymentService.processOrderPayment` → 已先執行 `checkOrderPaymentOwnership(order)`（`DEF-019` 已修，限定買家本人或 admin）→ 才呼叫 `orderService.updateOrderStatus(order.getId(), "PAID", ...)`。
2. **賣家/店主直接呼叫**（`PATCH /v2/orders/{orderId}/status`）：`OrderController.updateStatus` 以 `@PreAuthorize("hasAuthority('order:update')")` 把關，持有此權限者為 SELLER、STORE_OWNER、ADMIN、SUPER_ADMIN（`BUYER` 不持有），用於出貨等租戶內訂單管理。

若僅套用 Booking 側的 owner-or-admin 模式會誤擋合法賣家操作；若僅套用租戶模式則會誤擋買家自助付款。因此在動手修改前，先將分析與設計方案回報給使用者確認，取得同意後才實作「owner OR same-tenant OR admin」三選一放行邏輯——精確合成既有兩個修復前例：

- **owner**：比照 `getOrder`/`cancelOrder`/`getOrderStateLogs`（`DEF-018`）的買家自助模式。
- **same-tenant**：比照 `LogisticsService.checkOrderTenant`（`DEF-019`）的賣家側租戶隔離模式。
- **admin**：沿用所有既有前例皆有的 admin 放行規則。

---

## 3. 交付內容

### 生產程式碼修改

- **`OrderService.java`**：新增 `checkOrderStatusUpdateAuthorization(Order order)` private method，於 `updateOrderStatus` 呼叫 `OrderStateMachine.canTransition` 之前執行，越權拋出 `BusinessException(ErrorCode.E_1007)`（映射 HTTP 403）。

### 測試（擴充既有檔案）

- **`OrderServiceTest.java`** 新增 3 個測試：
  - `updateOrderStatus_otherTenantNonOwner_throwsE1007`：他租戶賣家對其他租戶訂單執行狀態轉換 → `E_1007`，且先於狀態機檢查觸發（狀態設為可合法轉換的 `CREATED`，若得 `E_1007` 而非轉換成功，證明擁有權檢查先於狀態機邏輯）。
  - `updateOrderStatus_sameTenantNonOwner_passesAuthorization`：本租戶賣家（非訂單擁有者）執行合法轉換（`CONFIRMED→SHIPPING`）→ 放行，證明修復未破壞既有業務能力。
  - `updateOrderStatus_admin_bypassesTenant`：admin 跨租戶放行（續走狀態機邏輯）。
  - 既有 `updateOrderStatus_validTransition`/`updateOrderStatus_invalidTransition_throwsE5001`（買家付款流程場景）維持通過，未修改，證明修復不破壞既有回歸基準線。

### 文件

- **`SPRINT_70_PLAN.md`**（新檔）：本 Sprint 計劃，含業務情境分析、前置條件確認、User Story/AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：`DEF-024` 自「🔴 高優先級」移至「已完成延後項目」，補上 Sprint 70 歷史紀錄條目。

---

## 4. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| `OrderServiceTest` 單獨執行 | ✅ **45 tests，0 fail**（既有 42 個 + 本 Sprint 新增 3 個） |
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe）| ✅ **606 + 342 = 948 tests，0 fail** |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更） |
| 開發-編譯-測試循環 | ✅ 修復完成後立即編譯 + 執行 `OrderServiceTest` 驗證通過，才繼續撰寫新測試；新測試完成後再次編譯 + 執行驗證，未累積 |

---

## 5. 誠實揭露（Rule 12）

1. **未新增角色層級的區分測試**：`checkOrderStatusUpdateAuthorization` 的「same-tenant」放行條件僅檢查 `TenantContext.getCurrentTenant()` 是否等於訂單租戶，不額外檢查呼叫者角色（例如是否確實持有 `order:update`）。這是刻意的設計決策而非疏漏——因為唯一能在無此權限下觸及 `updateOrderStatus` 的路徑（`PaymentService.processOrderPayment` 內部呼叫）已受 `checkOrderPaymentOwnership` 的 owner-only 限制保護在前，本次三選一檢查屬 service 層防禦縱深（defense-in-depth），角色層級的正確性仍由 Controller 層 `@PreAuthorize` 負責，此分工已於 `SPRINT_70_PLAN.md` 業務情境分析段落中說明。
2. **本 Sprint 未涉及前端**：`PATCH /v2/orders/{orderId}/status` 目前尚無對應的賣家管理前端頁面（探查確認），本次修復僅涉及後端 service 層授權邏輯，不影響任何既有前端流程。

---

## 6. Demo 重點

- **跨租戶 IDOR 缺口清零**：他租戶賣家無法再對任意 `orderId` 執行狀態轉換，越權統一回 403/`E_1007`。
- **合法業務能力完整保留**：買家自助付款（`CREATED→PAID`）與賣家管理自己租戶訂單（如 `CONFIRMED→SHIPPING`）兩條路徑皆通過測試驗證未受影響。
- **設計方案審查機制的實際運作**：面對比 Sprint 68 更複雜的雙情境呼叫方法，遵循任務指示「先分析回報、取得確認再實作」，避免了「修復安全漏洞卻破壞既有商業邏輯」的風險。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
