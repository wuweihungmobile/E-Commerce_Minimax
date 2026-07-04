# Sprint 61 Review / Sprint 61 評審會議

> **Sprint 編號**: Sprint 61
> **期間**: 2028-02-13 ~ 2028-02-26
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: M13/M14 後台管理深化——Admin Audit Log 查詢功能補齊 + M13 商家儀表板測試防護網

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | Admin Audit Log 查詢 API + 前端頁面（DEF-016 後續）| 5 | ✅ 完成 |
| US-002 | M13 SellerDashboardService 功能測試補強 | 3 | ✅ 完成 |

**8 SP 全數完成**。清償 DEF-016 遺留缺口（僅完成寫入、未完成查詢），並補齊 M13 既有邏輯的功能測試防護網。

---

## 2. 交付內容

- **`GET /v2/admin/audit-logs`**：SUPER_ADMIN 限定，支援分頁（page/size）、操作類型（action）、時間範圍（startDate/endDate）篩選。
- **`AdminService.getAuditLogs()`**：以 `JpaSpecificationExecutor` + `Specification` 動態組合篩選條件，只有實際提供的條件才出現在查詢中。
- **`AuditLogRepository`**：新增 `JpaSpecificationExecutor<AuditLog>` 介面。
- **前端 `admin/audit-logs/page.tsx`**：篩選表單（操作類型、日期範圍）+ 分頁列表，比照 `admin/tenants/page.tsx` 風格。
- **`SellerDashboardServiceTest.java`**：補齊 7d/30d 訂單數、30d 營收加總、活躍上架數、待處理訂單數、最後下單時間的功能測試，含無資料邊界情況（區別於既有僅測快取 TTL 的 `SellerDashboardServiceCacheTest`）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn test`）| ✅ **439 tests，0 fail**（含新增 6 個：`SellerDashboardServiceTest` 3 + `AdminServiceTest` 新增 3）|
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe `*IntegrationTest.java`/`*E2ETest.java`）| ✅ **全量 342 tests，0 fail**（含新增 2 個 `AdminControllerE2ETest`：Admin 成功查詢、BUYER 403）|
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（本 Sprint 無新 migration，`audit_log` 表已於 Sprint 28-31 建立）|
| 前端 lint/型別檢查/build | ✅ 0 error（新增 `/admin/audit-logs` 路由成功產出於 build）|

---

## 4. 誠實揭露（Rule 12）

1. **實作方式與原計劃有調整**：原計劃描述以靜態 JPQL（`:param IS NULL OR ...` 動態篩選寫法）實作查詢，但實測發現 PostgreSQL 對此類純 null 參數會拋出 `could not determine data type of parameter` 錯誤（Hibernate + PostgreSQL 已知限制）。已改用 `JpaSpecificationExecutor` + `Specification` 動態組合查詢條件解決，功能行為與驗收標準不變，但底層實作機制與原規劃不同，特此記錄。
2. **未進行手動瀏覽器互動測試**：前端頁面已通過 ESLint、TypeScript 型別檢查、production build（含新路由成功產出），後端 E2E 測試已透過真實 HTTP + 真實 DB 驗證完整的 API 契約（成功查詢 + 權限拒絕），但未實際啟動前後端 dev server 並在瀏覽器中手動操作過此頁面。
3. **未排入本 Sprint 的候選項目**：Sprint 61 規劃階段盤點出的其他 M13/M14 強化候選（Admin 租戶/使用者列表伺服器端篩選修正、`/dashboard/revenue` 營收報表頁、CSV/Excel 匯出）維持原計劃排除，留待後續 Sprint 依優先級評估。

---

## 5. Demo 重點

- **稽核紀錄可查詢**：平台管理者呼叫 `GET /v2/admin/audit-logs?action=TENANT_APPROVED` 可查到所有租戶審核通過的操作紀錄，含操作時間、操作者、變更前後值。
- **前端稽核紀錄頁**：`/admin/audit-logs` 提供篩選表單與分頁列表，非管理者角色存取會收到 403。
- **M13 儀表板測試防護網**：`SellerDashboardService` 的統計計算邏輯現有明確的功能測試覆蓋，未來修改此邏輯若破壞既有行為會立即被測試攔截。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
