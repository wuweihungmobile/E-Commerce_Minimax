# Release Notes - v2028.02.26-01 (Sprint 61)

**發布日期**: 2028-02-26（規劃）／實作完成 2026-07-04
**發布類型**: Minor（M13/M14 後台管理深化；schema-free；後端 + 前端 1 頁）
**Sprint**: Sprint 61
**狀態**: ⏳ 待 push（本 Sprint commit；收尾後即 push，嚴禁 `--no-verify`）

> Sprint 61 主題：**M13/M14 後台管理深化**。清償 DEF-016 遺留缺口（Admin Audit Log 只完成寫入未完成查詢）+ M13 SellerDashboardService 測試防護網補強。

---

## 新功能 / 改進 🚀

- **Admin Audit Log 查詢（DEF-016 後續）**：新增 `GET /v2/admin/audit-logs`（SUPER_ADMIN 限定），支援分頁 + 操作類型（action）+ 時間範圍（startDate/endDate）篩選。
- **前端稽核紀錄頁**：`/admin/audit-logs` 提供篩選表單與分頁列表。
- **M13 商家儀表板測試防護網**：`SellerDashboardService` 補齊功能測試（7d/30d 訂單數、營收、活躍上架、待處理訂單、最後下單時間），含無資料邊界情況。

## 測試 / 驗證 ✅

- **後端單元**：`mvn test` **439 tests，0 fail**（含新增 6 個）。
- **後端完整整合**：`mvn verify -Pintegration-test`（含 failsafe `*IntegrationTest.java`/`*E2ETest.java`）**全量 342 tests，0 fail**（含新增 2 個）。
- **schema 漂移守門（`make validate-schema`）**：無漂移（本 Sprint 無新 migration）。
- **前端**：ESLint / TypeScript 型別檢查 / production build 皆 0 error，新路由 `/admin/audit-logs` 成功產出。

## 技術決策 / 已知限制 ⚠️

- **實作方式調整**：原規劃以靜態 JPQL（`:param IS NULL OR ...`）實作動態篩選，實測發現 PostgreSQL 對純 null 參數會拋出型別推斷錯誤（Hibernate + PostgreSQL 已知限制），改用 `JpaSpecificationExecutor` + `Specification` 動態組合解決。
- **未進行手動瀏覽器互動測試**：已通過 lint/型別檢查/build 與後端 E2E（真實 HTTP + DB）驗證，但未實際於瀏覽器中手動操作此頁面。
- **M01 ElasticSearch 全文檢索本次排除**：PRD 明訂為 Phase 2+/長期項目、RICE 分數最低，且需新增未核准的 Docker image，需另案人工核准後評估。

## 資料庫遷移 🗄️

- 無（schema-free；`audit_log` 表已於 Sprint 28-31 建立）。

## 內含 Commit（Sprint 61）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| CI/hook 改造（前置） | 3578c7a | pre-push 加速改造 v6——本地輕量守門 + 完整整合測試移交雲端 |
| Sprint 61 Plan | 99f731d | M13/M14 後台管理深化計劃（2 US / 8 SP）|
| US-002 | 83cef1f | M13 SellerDashboardService 功能測試補強 |
| US-001 | 9cc8d1f | Admin Audit Log 查詢 API + 前端頁面（含 Specification 修復）|
| DEF-016 更新 | 22d737b | DEFERRED_ITEMS_TRACKER 狀態更新 |
| Sprint 61 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**基於**: AISDLC v0.09 Release Management Workflow
