# Sprint 70 計劃 / Sprint 70 Plan

> **Sprint 編號**: Sprint 70
> **期間**: 2026-07-05（緊急插入，優先於例行排程）
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `DEF-024`（Sprint 69 撰寫 `OrderService` 測試時發現的 IDOR 缺口）——使用者已明確授權插入緊急安全修復 Sprint，比照 Sprint 68（`DEF-023`）模式優先處理
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 69 收尾狀態 | ✅ 已收尾並 push（commit `f594369`） | `OrderServiceTest.java` 補齊 7 方法單元測試，過程中發現 `DEF-024` |
| `DEF-024` 完整記錄確認 | ✅ 已讀取 `DEFERRED_ITEMS_TRACKER.md` | 缺口：`OrderService.updateOrderStatus` 完全沒有訂單擁有權/租戶檢查，同檔案 `getOrder`/`cancelOrder`/`getOrderStateLogs` 皆有 owner-or-admin 檢查，唯獨此方法沒有；Controller 層僅 `@PreAuthorize("hasAuthority('order:update')")` 把關，`order:update` 由 SELLER/STORE_OWNER/ADMIN/SUPER_ADMIN 四種角色持有 |
| `updateOrderStatus` 業務情境探查 | ✅ 已確認兩種合法呼叫路徑（詳見下方「業務情境分析」） | 與 Sprint 68 Booking 案例不同：本方法有「買家自助付款」與「賣家管理自己租戶訂單」兩種正當情境並存，不可套用單純的 owner-or-admin 模式 |
| 修復設計確認 | ✅ 已由使用者審查並同意 | 採「owner OR same-tenant OR admin」三選一放行邏輯，精確複用既有兩個前例（`DEF-018` 買家 owner-or-admin 模式 + `DEF-019` `LogisticsService.checkOrderTenant` 的 tenant-based 模式），無新造邏輯 |
| Push 節奏 | 本 Sprint 收尾後立即 push（安全修復不宜久留本機） | 不累積 |

---

## 1. Sprint 70 目標

> **主題**: 緊急安全修復——`OrderService.updateOrderStatus` 跨租戶 IDOR（`DEF-024`）

修復 `updateOrderStatus` 完全無擁有權/租戶檢查的缺口：任一租戶的賣家（持有 `order:update` 權限的 SELLER/STORE_OWNER）原本都能對其他租戶的 `orderId` 執行狀態轉換。比照既有兩個修復前例合成一套「owner OR same-tenant OR admin」檢查邏輯，同時保留「買家透過付款流程自助轉態」與「賣家管理自己租戶訂單（如標記出貨）」兩種合法業務情境。

---

## 2. 業務情境分析（Dev David + SD Marcus）

`updateOrderStatus` 有兩種已確認的合法呼叫路徑：

1. **買家付款流程（內部呼叫）**：`PaymentController`（`order:create` 權限）→ `PaymentService.processOrderPayment` → 已先執行 `checkOrderPaymentOwnership(order)`（`DEF-019` 已修，限定買家本人或 admin）→ 才呼叫 `orderService.updateOrderStatus(order.getId(), "PAID", ...)`。此路徑下 `TenantContext.getCurrentUser()` 即買家本人 `= order.getUserId()`。
2. **賣家/店主直接呼叫（`PATCH /v2/orders/{orderId}/status`）**：`OrderController.updateStatus` 以 `@PreAuthorize("hasAuthority('order:update')")` 把關，持有此權限者為 **SELLER、STORE_OWNER、ADMIN、SUPER_ADMIN**（`BUYER` 不持有），用於出貨/物流狀態推進等賣家管理情境。`TenantContextFilter` 對一般角色會將 `TenantContext.getCurrentTenant()` 設為該使用者自己被指派的 `tenantId`。

**結論**：若僅套用 Booking 側的 owner-or-admin 模式會誤擋合法賣家操作；若僅套用 tenant-based 模式則會誤擋買家自助付款（買家的 `tenantId` 不一定等於訂單所屬租戶）。因此採三選一放行邏輯，分別對應兩個既有前例：

- **owner**（`userId.equals(order.getUserId())`）：比照 `getOrder`/`cancelOrder`/`getOrderStateLogs`（`DEF-018`）的買家自助模式。
- **same-tenant**（`TenantContext.getCurrentTenant().equals(order.getTenantId())`）：比照 `LogisticsService.checkOrderTenant`（`DEF-019`）的賣家側租戶隔離模式。
- **admin**（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）：沿用所有既有前例皆有的 admin 放行規則。

三者皆不成立時，拋出 `BusinessException(ErrorCode.E_1007)`（映射 HTTP 403），且此檢查置於狀態機轉換檢查（`OrderStateMachine.canTransition`）**之前**，比照 `cancelOrder`/`LogisticsService.createLogistics` 的順序慣例，避免向未授權者洩漏訂單狀態。

---

## 3. User Story

### US-001：`updateOrderStatus` 擁有權/租戶檢查修復（DEF-024）

> **SP**: 3 | **優先級**: 🔴 P0（安全） | **狀態**: ✅ 完成

**AC-001-1**：`OrderService.updateOrderStatus` 新增 `checkOrderStatusUpdateAuthorization` helper，實作「owner OR same-tenant OR admin」三選一放行邏輯，越權回 `BusinessException(E_1007)`。

**AC-001-2**：檢查置於 `OrderStateMachine.canTransition` 狀態機轉換檢查之前，避免向未授權者洩漏訂單狀態。

**AC-001-3**：新增回歸測試證明修復前後行為差異：
- 他租戶賣家對其他租戶 `orderId` 執行狀態轉換 → `E_1007`（且先於狀態機檢查觸發）。
- 本租戶賣家對自己租戶訂單執行合法狀態轉換 → 放行（續走狀態機邏輯）。
- admin 跨租戶放行（續走狀態機邏輯）。
- 買家本人透過付款流程觸發（`CREATED→PAID`）→ 放行，回歸測試不受影響（既有 `updateOrderStatus_validTransition`/`updateOrderStatus_invalidTransition_throwsE5001` 兩個測試維持通過）。

**AC-001-4**：開發-編譯-測試循環——修復完成後立即編譯 + 執行 `OrderServiceTest` 驗證通過，不累積。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | `updateOrderStatus` 擁有權/租戶檢查修復（DEF-024） | 3 | 🔴 P0 |
| **合計** | | **3** | |

> **說明**：本 Sprint 為緊急安全修復插入，不計入例行測試強化排程 velocity 序列；`Sprint 69` 收尾建議之 `BookingService`/`RoomCalendarService` 測試強化順延至 Sprint 71。

---

## 5. Definition of Done

- [x] US-001：`OrderService.updateOrderStatus` 新增擁有權/租戶檢查，比照既有兩個前例合成，最小改動
- [x] US-001：新增回歸測試證明修復前後行為差異（他租戶 403、本租戶放行、admin 放行、買家付款流程回歸不受影響）
- [x] 開發-編譯-測試循環：修復完成後立即編譯+執行對應測試，不累積
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）0 fail
- [x] `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）
- [x] `DEFERRED_ITEMS_TRACKER.md`：`DEF-024` 自「🔴 待決策」移至「已完成延後項目」，記錄解決方式與 commit
- [x] Sprint 70 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 生產程式碼修改 | `OrderService.java` |
| 後端測試 | `OrderServiceTest.java`（擴充 `updateOrderStatus` 擁有權/租戶案例） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（`DEF-024` 結案） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動**——純 Java service 層邏輯變更，不涉及資料庫 schema 或 API 合約變更（`PATCH /v2/orders/{orderId}/status` 的請求/回應格式不變，僅新增伺服器端授權判斷）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
