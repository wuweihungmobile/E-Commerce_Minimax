# Sprint 2 計劃 / Sprint 2 Plan

> **Sprint 編號**: Sprint 2
> **期間**: 2026-04-29 ~ 2026-05-12 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-20
> **基於**: Sprint 1 完成 + Phase 1 PRD 優先級

---

## 🔴 人機協作確認點結果

### Sprint 1 回顧摘要
**已完成**:
- US-M03-001 ~ US-M03-005 全部完成 (13 SP)
- 44 API E2E 測試通過
- AuthControllerE2ETest: 10 tests, 0 failures

### Sprint 2 準備確認
**選擇**: ✅ 確認 Sprint 2 範圍合理

### Sprint 2 目標確認
| Sprint | 目標 | Story Points |
|---------|------|--------------|
| Sprint 2 | M17 租戶/店鋪管理 (核心) | 13 SP (+ 5 buffer) |

### 確認人
- **Human User**: Koala OK
- **PM/PO (Victoria)**: 待確認
- **SA (Amanda)**: 待確認
- **SD (Marcus)**: 待確認
- **Dev (David)**: 待確認
- **QA (Quincy)**: 待確認

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 2 |
| **開始日期** | 2026-04-29 |
| **結束日期** | 2026-05-12 |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 13 SP |
| **Buffer** | 5 SP (27.8%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成租戶/店鋪管理系統核心功能（開店申請、店鋪列表、店鋪詳情、更新店鋪），建立多租戶基礎設施供後續 Sprint 使用。

### 具體目標

1. **開店申請 (US-M17-001)** - 3 SP
   - 完成 API 端點 `POST /api/v2/tenants/apply`
   - 實現租戶申請與狀態追蹤
   - 返回申請結果和預計審核時間

2. **取得我的店鋪列表 (US-M17-004)** - 2 SP
   - 完成 API 端點 `GET /api/v2/tenants`
   - 實現從 JWT 解析用戶租戶關係
   - 返回用戶所屬的所有店鋪

3. **店鋪詳情 (US-M17-002)** - 2 SP
   - 完成 API 端點 `GET /api/v2/tenants/:id`
   - 實現租戶公開資訊查詢
   - 區分已審核和未審核狀態

4. **更新店鋪資訊 (US-M17-003)** - 3 SP
   - 完成 API 端點 `PUT /api/v2/tenants/:id`
   - 實現 StoreOwner 角色校驗
   - 支援部分欄位更新

5. **功能開關查詢 (US-M17-005)** - 2 SP
   - 完成 API 端點 `GET /api/v2/dashboard/tenants/features`
   - 實現 Feature Toggle 查詢機制
   - 支援多店鋪功能開關

6. **申請功能開關 (US-M17-006)** - 1 SP (Buffer 項目)
   - 完成 API 端點 `PUT /api/v2/dashboard/tenants/features/:feature`
   - 實現功能開關更新

---

## 3. User Stories

| ID | 標題 | SP | 優先級 | 狀態 | 負責人 |
|----|------|-----|--------|------|--------|
| US-M17-001 | 開店申請 | 3 | P0 | ✅ 已完成 | Dev |
| US-M17-004 | 取得我的店鋪列表 | 2 | P0 | ✅ 已完成 | Dev |
| US-M17-002 | 店鋪詳情 | 2 | P0 | ✅ 已完成 | Dev |
| US-M17-003 | 更新店鋪資訊 | 3 | P1 | ✅ 已完成 | Dev |
| US-M17-005 | 功能開關查詢 | 2 | P1 | ✅ 已完成 | Dev |
| US-M17-006 | 申請功能開關 | 1 | P2 | ✅ 已完成 | Dev |

**Sprint 2 Total**: 13 SP

### 3.1 延後至 Sprint 3 的 User Stories

| ID | 標題 | SP | 優先級 | 延後原因 |
|----|------|-----|--------|----------|
| US-M17-007 | Admin 審核通過店鋪 | 3 | P0 | Admin 功能，2人團隊優先實現核心功能 |
| US-M17-008 | Admin 駁回店鋪申請 | 2 | P0 | Admin 功能，2人團隊優先實現核心功能 |

> **說明**: US-M17-007 和 US-M17-008 是 Admin 角色的審核功能，需等核心租戶功能完成後再實現。這兩個功能不影響 StoreOwner 角色的基本開店流程。

---

## 4. 當前進度分析

### 4.1 已完成（Sprint 1）

| 功能 | 端點 | 測試狀態 |
|------|------|----------|
| 會員註冊 | POST /v2/auth/register | ✅ 通過 |
| 會員登入 | POST /v2/auth/login | ✅ 通過 |
| JWT 刷新 | POST /v2/auth/refresh | ✅ 通過 |
| 會員登出 | POST /v2/auth/logout | ✅ 通過 |
| 取得用戶資訊 | GET /v2/auth/me | ✅ 通過 |

### 4.2 Sprint 2 ✅ 已完成功能

| 功能 | 端點 | 測試狀態 |
|------|------|----------|
| 開店申請 | POST /v2/tenants/apply | ✅ 通過 (3 項 E2E) |
| 取得店鋪列表 | GET /v2/tenants | ✅ 通過 (2 項 E2E) |
| 店鋪詳情 | GET /v2/tenants/:id | ✅ 通過 (3 項 E2E) |
| 更新店鋪 | PUT /v2/tenants/:id | ✅ 通過 (3 項 E2E) |
| 功能開關查詢 | GET /v2/dashboard/tenants/features | ✅ 通過 (1 項 E2E) |
| 功能開關更新 | PUT /v2/dashboard/tenants/features/:feature | ✅ 通過 (2 項 E2E) |

> **Sprint 2 總測試**: 14 項 API E2E 測試全部通過 (2026-04-24)
> **Note**: Admin APIs (US-M17-007/008) are deferred to Sprint 3.

---

## 5. 任務分解 / Task Breakdown

### 5.0 資料庫 Migration（Day 0-1）

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-MIGRATION-01 | 建立 tenants、tenant_members、tenant_applications、tenant_features 表 | 3h | ✅ 已完成 |

> **Note**: Migration 是 Sprint 2 的前置任務，必須在 Day 1 完成，否則後續任務無法執行。

### 5.1 US-M17-001: 開店申請 (3 SP)

**負責人**: Dev
**預估時間**: 5 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-001-01 | 實現 Tenant 實體和 Repository | 2h | ✅ 已完成 |
| T-M17-001-02 | 實現 POST /tenants/apply 端點 | 2h | ✅ 已完成 |
| T-M17-001-03 | 整合測試：成功申請 | 1h | ✅ 已完成 |

**驗收標準 (AC)**:
- [ ] AC-001: 成功申請返回 201 和 applicationId
- [ ] AC-002: 申請狀態為 PENDING
- [ ] AC-003: 申請記錄與用戶關聯

### 5.2 US-M17-004: 取得我的店鋪列表 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-004-01 | 實現 GET /tenants 端點 | 2h | ✅ 已完成 |
| T-M17-004-02 | 整合測試：成功取得列表 | 1h | ✅ 已完成 |

**驗收標準 (AC)**:
- [ ] AC-001: 返回用戶所屬店鋪列表
- [ ] AC-002: 空列表返回空陣列

### 5.3 US-M17-002: 店鋪詳情 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-002-01 | 實現 GET /tenants/:id 端點 | 2h | ✅ 已完成 |
| T-M17-002-02 | 整合測試：成功取得詳情 | 1h | ✅ 已完成 |

**驗收標準 (AC)**:
- [ ] AC-001: 已審核店鋪返回完整資訊
- [ ] AC-002: 未審核店鋪返回基本資訊
- [ ] AC-003: 不存在的店鋪返回 404

### 5.4 US-M17-003: 更新店鋪資訊 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-003-01 | 實現 PUT /tenants/:id 端點 | 2h | ✅ 已完成 |
| T-M17-003-02 | StoreOwner RBAC 校驗 | 1h | ✅ 已完成 |
| T-M17-003-03 | 整合測試：成功更新 | 1h | ✅ 已完成 |

**驗收標準 (AC)**:
- [ ] AC-001: StoreOwner 可更新自己店鋪
- [ ] AC-002: 非 Owner 返回 403
- [ ] AC-003: 部分欄位更新成功

### 5.5 US-M17-005: 功能開關查詢 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-005-01 | 實現 Feature Toggle 機制 | 2h | ✅ 已完成 |
| T-M17-005-02 | 實現 GET /dashboard/tenants/features | 1h | ✅ 已完成 |

**驗收標準 (AC)**:
- [ ] AC-001: 返回所有功能開關狀態
- [ ] AC-002: StoreOwner+ 角色可查詢

---

## 6. 測試策略

### 6.1 測試類型分佈

| 測試類型 | 數量 | 負責人 |
|----------|------|--------|
| 單元測試 (UT) | 10 | Dev |
| 整合測試 (IT) | 8 | Dev/QA |
| API E2E 測試 | 9 | QA |

### 6.2 測試優先級

| 優先級 | 測試案例 | 數量 |
|--------|----------|------|
| P0 | 開店申請流程、店鋪列表查詢、權限校驗 | 12 |
| P1 | 店鋪詳情查詢、功能開關 | 8 |
| P2 | 更新店鋪、功能開關更新 | 7 |

---

## 7. 風險與依賴

### 7.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| TenantContext 多租戶隔離實現複雜度 | 中 | 高 | 使用現有的 TenantContextFilter |
| Feature Toggle 機制需要新表 | 低 | 中 | 預留擴展欄位 |

### 7.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| Sprint 1 Auth 系統 | 前置 | ✅ 已完成 |
| M17 API 規格 | 需求 | ✅ 已有 |
| 資料庫 Migration | 基礎設施 | 待執行 |

---

## 8. Sprint 執行計劃

### 8.1 第一週 (2026-04-29 ~ 2026-05-05)

| 日期 | 重點任務 |
|------|----------|
| Day 1 (04/29) | Sprint Kickoff, US-M17-001 實現 |
| Day 2 (04/30) | US-M17-001 IT/E2E 測試 |
| Day 3 (05/01) | US-M17-004 實現 |
| Day 4 (05/02) | US-M17-004 IT/E2E 測試 |
| Day 5 (05/03) | US-M17-002 實現 |
| Day 6-7 | Weekend |

### 8.2 第二週 (2026-05-06 ~ 2026-05-12)

| 日期 | 重點任務 |
|------|----------|
| Day 8 (05/06) | US-M17-002 IT/E2E 測試 |
| Day 9 (05/07) | US-M17-003 實現 + 測試 |
| Day 10 (05/08) | US-M17-005 實現 + 測試 |
| Day 11 (05/09) | US-M17-006 實現 + 測試 |
| Day 12 (05/10) | Bug Fix + Code Review |
| Day 13 (05/11) | Sprint Review + Retro |
| Day 14 (05/12) | Buffer / Sprint 3 準備 |

---

## 9. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| 代碼完成 | 所有 User Stories 實作完成 | - |
| Code Review | 通過團隊 Code Review | - |
| 單元測試覆蓋率 | >= 80% (TenantService) | - |
| 整合測試通過 | IT-M17-001 ~ IT-M17-009 全部通過 | - |
| API E2E 測試通過 | API-M17-001 ~ API-M17-011 全部通過 | - |
| TenantContext 隔離 | 所有 API 通過租戶隔離驗證 | - |
| 文檔更新 | API 規格更新 | - |

---

## 10. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 1 計劃 | `docs/04_planning/SPRINT_01_PLAN.md` | Sprint 1 完成狀態 |
| User Stories | `docs/01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001` | M17 User Stories |
| API 規格 | `docs/02_architecture/api/API_M17_Tenant.md` | M17 API 規格 |
| FRD | `docs/01_requirements/E-Commerce_FRD_v1.0.md` | 功能需求文檔 |

---

## ✅ 確認簽核

| 角色 | 確認狀態 | 簽核日期 | 備註 |
|------|----------|----------|------|
| Human User | ✅ 確認 | 2026-04-22 | Sprint 目標和範圍合理 |
| PM/PO (Victoria) | ✅ 確認 | 2026-04-22 | Sprint 目標和範圍合理 |
| SA (Amanda) | ✅ 確認 | 2026-04-22 | M1-M5 問題已修復確認 |
| SD (Marcus) | ✅ 確認 | 2026-04-22 | API 規格一致，架構合理 |
| Dev (David) | ✅ 確認 | 2026-04-22 | Migration 任務已新增確認 |
| QA (Quincy) | ✅ 確認 | 2026-04-24 | Sprint 2 測試驗證完成：14/14 API E2E 測試通過 |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-24

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-20 | 初始版本 | - |
| v1.1 | 2026-04-22 | 1. 修正租戶狀態 PENDING_REVIEW → PENDING 與 API/DB 一致<br>2. 修正 US-M17-001 Story Points 5→3<br>3. 修正 US-M17-006 Story Points 3→1<br>4. 新增 US-M17-007/008 延後至 Sprint 3 說明<br>5. 更新 TC_M17_Tenant.md Sprint 3 標記 | AI Review |
| v1.2 | 2026-04-22 | 1. 新增 Migration 任務 (T-M17-MIGRATION-01)<br>2. 新增 US-M17-003/006 API E2E 測試案例 (API-M17-012~015)<br>3. 更新 DoD 測試案例編號 | AI Review + Agent Confirmation |
| v1.3 | 2026-04-24 | Sprint 2 全部完成：US-M17-001~006 全部標記為 ✅ 已完成，新增 US-M17-004 正向成功測試，修復 Feature Toggle requiresApproval 邏輯，14/14 API E2E 測試通過 | QA Review |
