# Release Notes - v2028.06.17-01 (Sprint 69)

**發布日期**: 2028-06-17（規劃）／實作完成 2026-07-05
**發布類型**: 🧪 測試強化（Test-only；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 69（多 Sprint 測試強化計劃第三階段）
**狀態**: ✅ 已 push

> Sprint 69 主題：**`OrderService`（訂單狀態機核心）單元測試從 0 建立**。恢復因 Sprint 68 緊急安全修復（`DEF-023`）而順延的例行測試強化排程，完成 `SPRINT_67_PLAN.md` 第 6 節原建議之 Sprint 68 目標。

---

## 測試強化 🧪

- **`OrderServiceTest.java`（新檔，42 個測試）**：為 `OrderService` 7 個 public 方法建立完整 Mockito 單元測試——
  - `createOrderFromCart`（PRODUCT 分支）：happy path、使用者/租戶不存在（E_1006/E_2000）、購物車為空（E_5004）、刊登項目不存在/未上架（E_3000/E_3002）、SKU 找不到優雅降級。
  - `createOrderFromCart`（ROOM 分支）：happy path、必填欄位/日期缺口（E_9005/E_4003）、房源相關錯誤（E_3000/E_9005/E_3002）、租戶三層 fallback（系統租戶 → `platform` slug → E_2000）。
  - `createBooking`：驗證欄位檢查與現況 stub 行為（`UnsupportedOperationException`）。
  - `getUserOrders`：分頁大小上限裁切、排序透傳。
  - `getOrder`/`cancelOrder`/`getOrderStateLogs`：擁有者放行、非擁有者 E_1007、admin 放行、資源不存在 E_5000。
  - `updateOrderStatus`：狀態機合法/不合法轉換（E_5001）。
  - `cancelOrder`：CREATED/CONFIRMED 直接取消 vs. PAID 自動觸發 REFUNDING 退款流程聯動。

  修復前：`OrderService` 完全沒有專屬 Mockito 單元測試，僅有不觸及 Service 本身的 `OrderStateMachineTest` 與需完整 Spring Context 的 Controller 層 E2E/整合測試間接涵蓋部分流程。
  修復後：7 個方法皆有正常/邊界/錯誤路徑的隔離單元測試覆蓋。

## 🔴 安全發現（未修復，已決策排入 Sprint 70）

- **`DEF-024`：`OrderService.updateOrderStatus` 無擁有權/租戶檢查（跨租戶 IDOR）**：撰寫測試時發現同檔案 `getOrder`/`cancelOrder`/`getOrderStateLogs` 皆有 owner-or-admin 擁有權檢查，唯獨 `updateOrderStatus` 沒有；持有 `order:update` 權限的 SELLER/STORE_OWNER 角色分散於各租戶，形同任一租戶賣家可對其他租戶訂單執行狀態轉換。**本 Sprint 依範圍未修改生產程式碼**，已記錄於 `DEFERRED_ITEMS_TRACKER.md`。使用者收尾時看到揭露後，**決定比照 Sprint 68（`DEF-023`）模式另立 Sprint 70 緊急修復**。

## 測試 / 驗證 ✅

- **後端單元**（`mvn verify -Pintegration-test` 的 unit 階段）：**603 tests，0 fail**（含本 Sprint 新增 `OrderServiceTest` 42 個）。
- **後端完整整合**（`mvn verify -Pintegration-test`，含 failsafe）：**603 + 342 = 945 tests，0 fail**。
- **checkstyle-main / checkstyle-test**：0 violations。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。
- **開發-編譯-測試循環**：依方法分組（PRODUCT/ROOM 分支 → createBooking+getUserOrders+getOrder → updateOrderStatus+cancelOrder+getOrderStateLogs）逐段撰寫並立即編譯 + 測試，過程中發現的測試 fixture 缺陷（影子欄位觀察陷阱、mock 呼叫順序誤判）皆當場修正，未累積驗證。
- **前端**：本 Sprint 無前端變動，未執行。

## 技術決策 / 已知限制 ⚠️

- **`Order.tenantId` 影子欄位單元測試陷阱**：`insertable=false/updatable=false` 欄位僅由 Hibernate 讀取 DB 時回填，純 Mockito mock 無法透過 `OrderResponse.getTenantId()` 觀察租戶回退結果，改以 `ArgumentCaptor<Order>` 驗證實際傳入 repository 的 `Order.getTenant()`。此為既有「erp-tenant-test-seeding-gotcha」（整合測試層級）教訓在單元測試層級的等價陷阱。
- **`createBooking` 現況為未完成 stub**：驗證通過後固定拋出 `UnsupportedOperationException`（缺 `RoomBookingService` 實作），測試如實記錄現況，非本 Sprint 修復範圍。
- **`DEF-024` 不在本 Sprint 修復範圍**：詳見上方安全發現段落，已排入 Sprint 70。

## 資料庫遷移 🗄️

- 無（schema-free；純測試新增）。

## 內含 Commit（Sprint 69）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 69 Plan | （見下方實際 commit hash）| OrderService 測試強化計劃（1 US / 8 SP）|
| US-001 | （見下方實際 commit hash）| 新增 `OrderServiceTest.java`（42 個測試）|
| Sprint 69 收尾 | （見下方實際 commit hash）| Review / Retro / Release Notes + trackers（含 DEF-024 記錄）|

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**基於**: AISDLC v0.09 Release Management Workflow
