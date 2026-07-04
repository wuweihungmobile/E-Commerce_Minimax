# Sprint 64 計劃 / Sprint 64 Plan

> **Sprint 編號**: Sprint 64
> **期間**: 2028-03-26 ~ 2028-04-08 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: `docs/05_development/SPRINT_63_RETRO.md` 第 4 節候選項目「Admin 租戶/使用者列表伺服器端篩選修正」
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 問題重新確認 | ✅ 已確認比原描述更嚴重：`AdminService.getTenants(page, size)` 與 `getUsers(page, size, tenantId, role)` 的 `page`/`size` 參數**完全未被使用**——兩者皆呼叫 `repository.findAll()`/`findByTenantId()` 取回全部資料後才在記憶體中組裝回應，`totalCount` 恆等於回傳筆數。這不只是「缺篩選功能」，而是**分頁機制本身是假的**，租戶/使用者數量成長後有效能風險 |
| 前端連動問題確認 | ✅ `frontend/src/app/admin/tenants/page.tsx` 的篩選是對已抓回的當頁（實際上是全部）資料做 client-side filter，因後端未真正分頁，目前不會有資料遺漏，但屬於「意外不出錯」而非設計正確——修正後端分頁後若不同步修正前端，會變成「篩選條件套用在單頁子集上」的真正 bug |
| 範圍界定 | ✅ 沿用 Sprint 61 US-001 已驗證有效的 `JpaSpecificationExecutor` + `Specification` 動態篩選模式（避免重蹈 PostgreSQL 對純 JPQL null 參數的型別推斷限制）|
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 64 目標

> **主題**: Admin 租戶/使用者列表真分頁化 + 伺服器端篩選

修正 `getTenants`/`getUsers` 的假分頁問題，改為真正的資料庫層級分頁（`Pageable`），並補上 `status`/關鍵字篩選；同步修正前端 `admin/tenants/page.tsx` 改為伺服器端篩選。

---

## 2. User Story

### US-001：AdminService.getTenants 真分頁化 + 篩選

> **SP**: 5 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**: `AdminService.getTenants` 改用 `TenantRepository`（新增 `JpaSpecificationExecutor<Tenant>`）搭配 `Specification` 動態組合 `status`（可選）與 `keyword`（可選，比對 `name`/`slug`）篩選，並以 `PageRequest.of(page, size)` 做真正的資料庫分頁。

**AC-001-2**: `AdminDto.TenantListResponse` 補上分頁 metadata（`page`/`size`/`totalElements`/`totalPages`，比照 Sprint 61 `AuditLogListResponse` 的欄位設計）。

**AC-001-3**: `AdminController.getTenants` 新增 `status`/`keyword` 查詢參數。

**AC-001-4**: 前端 `admin/tenants/page.tsx` 移除 client-side filter，改為篩選條件變更時重新呼叫 API（伺服器端篩選+分頁），並顯示分頁控制項（比照既有 `Pagination` 元件）。

---

### US-002：AdminService.getUsers 真分頁化 + 篩選

> **SP**: 3 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-002-1**: `AdminService.getUsers` 改用 `UserRepository`（新增 `JpaSpecificationExecutor<User>`）搭配 `Specification` 動態組合既有 `tenantId`/`role` 篩選 + 新增 `status`/關鍵字（比對 `email`/`fullName`）篩選，並以 `PageRequest.of(page, size)` 做真正分頁。

**AC-002-2**: `AdminDto.UserListResponse` 補上分頁 metadata。

**AC-002-3**: `AdminController.getUsers` 新增 `status`/`keyword` 查詢參數。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | AdminService.getTenants 真分頁化 + 篩選（含前端修正）| 5 | P2 |
| US-002 | AdminService.getUsers 真分頁化 + 篩選 | 3 | P2 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60-63（皆 8 SP）之單 Sprint 產能。US-001 含前端修正故較重，US-002 為後端對稱模式複用，規模較小。

---

## 4. Definition of Done

- [x] US-001：`getTenants` 真分頁化 + 篩選 + 前端 `admin/tenants/page.tsx` 改伺服器端篩選
- [x] US-002：`getUsers` 真分頁化 + 篩選
- [ ] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）0 fail
- [ ] `make validate-schema` 無漂移（本 Sprint 無新 migration）
- [x] 前端 lint/tsc/build 0 error
- [ ] Sprint 64 Review / Retro / Release Notes + trackers
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 | `core/admin/AdminService.java`、`api/controller/AdminController.java`、`api/dto/AdminDto.java`、`domain/repository/TenantRepository.java`、`domain/repository/UserRepository.java` |
| 前端 | `frontend/src/app/admin/tenants/page.tsx`（改伺服器端篩選）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration**（純邏輯修正，欄位皆已存在）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
