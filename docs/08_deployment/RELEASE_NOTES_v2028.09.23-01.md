# Release Notes - v2028.09.23-01 (Sprint 76)

**發布日期**: 2028-09-23（規劃）／實作完成 2026-07-06
**發布類型**: 🔍 探查 + 🔴 安全修復（P0；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 76（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 76 主題：**`LogisticsService` 範圍探查**，發現除 `createLogistics`（`DEF-019`，Sprint 36 已修復）外，其餘 6 個 public 方法（`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics`）皆完全沒有租戶擁有權檢查，屬跨租戶 IDOR，已全數修復（`DEF-036`）；附帶檢查 `ShippingTemplateService` 發現 1 項低風險缺口（`DEF-037`），使用者決策擱置僅記錄。

---

## 🔍 範圍探查（US-001）

- **`LogisticsService`（`core/logistics/LogisticsService.java`，329 行）7 個 public 方法**：`createLogistics` 已有 `DEF-019` 的租戶擁有權檢查（`checkOrderTenant`）；其餘 6 個方法完全沒有——`LogisticsController` 僅以 `order:read`/`order:update` 權限把關（權限分散於各租戶角色，非租戶範圍限制）。
- **`ShippingTemplateService`（`core/logistics/ShippingTemplateService.java`，139 行）6 個 public 方法**：CRUD 四方法皆已由 Controller 傳入 `TenantContext` 的 `tenantId` 正確隔離；僅 `calculateFee` 缺租戶過濾（`DEF-037`，新發現）。

## 🔴 安全修復 P0：`DEF-036`（新發現）

- **`LogisticsService` 除 `createLogistics` 外全數缺租戶擁有權檢查，跨租戶可讀取/竄改他租戶物流單**：
  - **修復前**：`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics` 皆直接 `findById`/`findByOrderId` 後即讀取或寫入；任一租戶持有對應權限即可跨租戶讀取物流單詳情/追蹤歷史，或竄改其物流狀態（`updateLogisticsStatus`/`cancelLogistics` 屬寫入操作，`DELIVERED` 狀態變更還會連動竄改他租戶訂單狀態）。
  - **紅燈證明**：新增 `LogisticsServiceTenantAccessTest.java`（8 個測試）；以 `git stash` 暫時還原 `LogisticsService.java` 至修復前版本執行，5 個跨租戶案例確認未被攔截、3 個成功案例因 stub 未被呼叫觸發 `UnnecessaryStubbingException`，證實漏洞成立。
  - **修復後**：新增 `checkLogisticsTenant(Logistics)` helper（依 `orderId` 反查 `Order` 後委派既有 `checkOrderTenant`），6 個方法皆於狀態/業務邏輯檢查之前呼叫；同步更新既有 `LogisticsServiceCancelTest`（`DEF-011`）補上擁有權 fixture。

## 📝 已記錄不修復：`DEF-037`（`ShippingTemplateService.calculateFee` 跨租戶查詢）

- `calculateFee(templateId, orderAmount)` 未驗證 `templateId` 租戶歸屬，任一登入使用者可查得他租戶運費模板設定（`feeType`/`fixedAmount`/`freeThreshold`）。無 PII、無寫入風險，且是否應限制涉及「買家跨租戶比價試算」既有使用情境的業務判斷，記入 `DEFERRED_ITEMS_TRACKER.md` 後**使用者已確認擱置，本 Sprint 未修改程式碼**。

## 測試 / 驗證 ✅

- **`LogisticsServiceTenantAccessTest` 紅綠燈流程**：修復前（`git stash` 還原至修復前版本執行）8 tests 5 Failures + 3 Errors（`DEF-036`×5 跨租戶案例未攔截 + 3 個因程式碼當時不查 `orderRepository` 觸發的 `UnnecessaryStubbingException`）→ 修復後 8 tests 0 fail。
- **`LogisticsServiceCancelTest`（`DEF-011`）同步更新**：補上 `orderRepository`/`TenantContext` 本租戶 fixture 後 3 tests 0 fail。
- **後端單元回歸**：**749 tests，0 fail**。
- **後端全量回歸**（`mvn verify -Pintegration-test`）：**BUILD SUCCESS**，單元 749 + 整合（failsafe）342 = **1091 tests，0 fail**。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。

## 技術決策 / 已知限制 ⚠️

- **`DEF-037` 僅記錄不修復**：`ShippingTemplateService.calculateFee` 跨租戶查詢風險屬性與 `DEF-036` 明顯不同（無 PII、無寫入），是否收斂涉及既有買家比價試算情境的業務判斷，使用者已確認擱置。
- **未修正 `E_7000` 錯誤碼語意問題**：`getLogistics`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus` 於「物流不存在」情境仍沿用語意錯誤的 `E_7000`（官方定義「找不到供應商」），與 `DEF-011` 已修正的 `E_7500` 屬同一類問題，但非安全缺口、非本 Sprint 範圍，僅記錄觀察。
- **無前端變動**：本 Sprint 純後端探查與擁有權檢查修復。

## 資料庫遷移 🗄️

- 無（schema-free；純 Java Service 層擁有權檢查邏輯，未新增/修改任何 Entity 或 Repository 方法）。

## 內含 Commit（Sprint 76）

| US / 項目 | 說明 |
|----------|------|
| Sprint 76 Plan | `LogisticsService` 範圍探查 + 擁有權檢查修復計劃（2 US / 4 SP）|
| US-001 | `LogisticsService`（+`ShippingTemplateService`）範圍探查（無程式碼變更）|
| US-002 | `DEF-036` 修復：`LogisticsService` 除 `createLogistics` 外 6 個方法補齊租戶擁有權檢查 |
| Sprint 76 收尾 | Review / Retro / Release Notes + trackers（含 `DEF-037` 記錄擱置）|

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
