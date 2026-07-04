# Sprint 61 計劃 / Sprint 61 Plan

> **Sprint 編號**: Sprint 61
> **期間**: 2028-02-13 ~ 2028-02-26 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/PRODUCT_BACKLOG.md` 候選 #8（M13/M14 後台深化）+ `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` DEF-016（Admin Audit Log 僅完成寫入、未完成查詢）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 61 主題篩選 | ✅ 已排除 M01 ElasticSearch 全文檢索：PRD 明訂為 Phase 2+/長期項目、RICE 分數為候選中最低（0.55），且需新增未在 `docs/08_deployment/DOCKER_POLICY.md` 核准清單的 Docker image，需人工另案核准，不適合本 Sprint | 2026-07-04 使用者決策 |
| 前置技術調查 | ✅ 完成：`AuditLog` entity/`AuditLogRepository`/V57 migration 已於 Sprint 28-31（DEF-016）完成寫入，但 `AdminController.java` 無任何查詢端點、前端無查詢頁面；`SellerDashboardService.java`（71 行）僅有快取 TTL 測試（`SellerDashboardServiceCacheTest`），無功能邏輯測試 |
| 範圍排除確認 | ✅ 本 Sprint 不含：Admin 租戶/使用者列表伺服器端篩選修正（候選 #2）、`/dashboard/revenue` 營收報表頁（候選 #4）、CSV/Excel 匯出（無既有 pattern 可重用）、Stripe Connect 前端串接（涉及金流，延後）——留待 Sprint 62 評估 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 61 目標

> **主題**: M13/M14 後台管理深化——Admin Audit Log 查詢功能補齊 + M13 商家儀表板測試防護網

清償 DEF-016 遺留缺口：為既有 `AuditLog` 持久化機制補上查詢 API 與前端頁面，讓平台管理者可實際檢視稽核紀錄。同時為 M13 `SellerDashboardService` 補上功能測試，比照 M14 `AdminService`/`AnalyticsService` 的測試覆蓋水準,填補 `docs/04_planning/PRODUCT_BACKLOG.md` 模組成熟度表中標注的「框架薄」缺口。

---

## 2. User Story

### US-001：Admin Audit Log 查詢 API + 前端頁面（DEF-016 後續）

> **SP**: 5 | **優先級**: P2 | **狀態**: ✅ 完成

**誠實揭露**：實作過程中發現原規劃的靜態 JPQL（`:param IS NULL OR ...` 動態篩選寫法）在 PostgreSQL 下對純 null 參數會拋出 `could not determine data type of parameter` 錯誤（Hibernate + PostgreSQL 已知限制），已改用 `JpaSpecificationExecutor` + `Specification` 動態組合查詢條件解決，AC-001-2 的實作方式因此與原計劃描述（「透過 `AuditLogRepository` 讀取」）有調整，但功能行為與驗收標準不變。

**AC-001-1**: `AdminController.java` 新增 `GET /v2/admin/audit-logs` 端點，支援分頁（`page`/`size`）、時間範圍（`startDate`/`endDate`）、操作類型（`action`）篩選；僅平台管理者角色可存取（比照既有 admin 端點的權限檢查機制）。

**AC-001-2**: `AdminService.java` 新增對應查詢方法，透過 `AuditLogRepository` 讀取既有 `audit_log` 表資料，回傳分頁結果（含操作者、操作類型、目標物件、時間戳記、操作內容摘要）。

**AC-001-3**: 前端新增 `frontend/src/app/admin/audit-logs/page.tsx`，提供分頁表格與篩選表單（日期範圍、操作類型），串接 AC-001-1 端點；比照既有 `admin/tenants/page.tsx` 的頁面風格與元件慣例。

**AC-001-4**: 測試——`AdminServiceTest` 新增查詢方法的單元測試（涵蓋分頁、篩選條件組合、空結果情況）；新增/擴充 `AdminControllerE2ETest` 涵蓋端點權限檢查（非管理者角色應被拒絕）與正常查詢流程。

---

### US-002：M13 SellerDashboardService 功能測試補強

> **SP**: 3 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-002-1**: 新增 `SellerDashboardServiceTest.java`（區別於既有僅測快取 TTL 的 `SellerDashboardServiceCacheTest`），涵蓋現有邏輯的正確性：近 7 天/30 天訂單數統計、近 30 天營收加總、活躍上架數、待處理訂單數、最後下單時間。

**AC-002-2**: 涵蓋邊界情況——賣家無任何訂單、無任何上架品項時，各統計欄位應回傳正確的預設值（0 或 null，依既有實作行為斷言，不改變現有邏輯）。

**AC-002-3**: 測試風格與斷言慣例比照既有 `AdminServiceTest`/`AnalyticsServiceTest`，使用 mock repository 驗證計算邏輯，不需真實 DB。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | Admin Audit Log 查詢 API + 前端頁面（DEF-016 後續）| 5 | P2 |
| US-002 | M13 SellerDashboardService 功能測試補強 | 3 | P2 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60（8 SP／單一 US）之單 Sprint 產能；本次拆為 2 個獨立 US 降低單一項目風險，US-002 不依賴 US-001，可並行驗證。

---

## 4. Definition of Done

- [x] US-001：`GET /v2/admin/audit-logs` 端點 + `AdminService` 查詢方法（Specification 動態篩選）+ 前端 `admin/audit-logs/page.tsx`；E2E 測試 18/18 通過（含新增 2 個：Admin 成功查詢、BUYER 403）
- [x] US-002：`SellerDashboardServiceTest.java` 功能測試新增（3 個測試：有資料統計正確性、無資料邊界值、pending 狀態清單查詢）
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）**439 + 342 = 781 tests，0 fail**
- [x] `make validate-schema` 無漂移（本 Sprint 無新 migration）
- [x] `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` DEF-016 狀態更新為完成（commit 22d737b）
- [x] Sprint 61 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 | `api/controller/AdminController.java`（新增端點）、`core/admin/AdminService.java`（新增查詢方法）|
| 後端測試 | `AdminServiceTest.java`（新增測試）、`AdminControllerE2ETest.java`（新增/擴充）、`SellerDashboardServiceTest.java`（新檔）|
| 前端 | `frontend/src/app/admin/audit-logs/page.tsx`（新頁面）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration**（`audit_log` 表已於 Sprint 28-31 建立）；前端新增 1 個頁面，不涉及既有頁面改動。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
