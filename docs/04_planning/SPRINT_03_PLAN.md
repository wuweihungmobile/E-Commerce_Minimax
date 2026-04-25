# Sprint 3 計劃 / Sprint 3 Plan

> **Sprint 編號**: Sprint 3
> **期間**: 2026-05-13 ~ 2026-05-26 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-24
> **基於**: Sprint 2 完成 + M17 租戶管理核心功能完成

---

## 🔴 人機協作確認點結果

### Sprint 2 回顧摘要
**已完成**:
- US-M17-001 ~ US-M17-006 全部完成 (13 SP)
- 14 API E2E 測試通過
- TenantControllerE2ETest: 14 tests, 0 failures

### Sprint 3 準備確認
**選擇**: ✅ 確認 Sprint 3 範圍合理

### Sprint 3 目標確認
| Sprint | 目標 | Story Points |
|---------|------|--------------|
| Sprint 3 | M17 Admin 審核功能 | 5 SP (+ 13 buffer) |

### 確認人
- **Human User**: 待確認
- **PM/PO (Victoria)**: 待確認
- **SA (Amanda)**: 待確認
- **SD (Marcus)**: 待確認
- **Dev (David)**: 待確認
- **QA (Quincy)**: 待確認

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 3 |
| **開始日期** | 2026-05-13 |
| **結束日期** | 2026-05-26 |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 5 SP |
| **Buffer** | 13 SP (72.2%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 Admin 審核店鋪功能（審核通過、審核駁回），形成完整的租戶生命週期管理流程。

### 具體目標

1. **Admin 審核通過店鋪 (US-M17-007)** - 3 SP
   - 完成 API 端點 `POST /api/v2/admin/tenants/:id/approve`
   - 實現店鋪狀態 PENDING → ACTIVE 轉換
   - 初始化 Feature Toggle 預設值

2. **Admin 駁回店鋪申請 (US-M17-008)** - 2 SP
   - 完成 API 端點 `POST /api/v2/admin/tenants/:id/reject`
   - 實現店鋪狀態 PENDING → REJECTED 轉換
   - 記錄駁回原因供用戶查看

---

## 3. User Stories

| ID | 標題 | SP | 優先級 | 狀態 | 負責人 |
|----|------|-----|--------|------|--------|
| US-M17-007 | Admin 審核通過店鋪 | 3 | P0 | 待實現 | Dev |
| US-M17-008 | Admin 駁回店鋪申請 | 2 | P0 | 待實現 | Dev |

**Sprint 3 Total**: 5 SP

---

## 4. 當前進度分析

### 4.1 已完成（Sprint 1 + Sprint 2）

| 功能 | 端點 | 測試狀態 |
|------|------|----------|
| 會員註冊/登入 | POST /v2/auth/* | ✅ 通過 |
| 開店申請 | POST /v2/tenants/apply | ✅ 通過 |
| 取得店鋪列表 | GET /v2/tenants | ✅ 通過 |
| 店鋪詳情 | GET /v2/tenants/:id | ✅ 通過 |
| 更新店鋪 | PUT /v2/tenants/:id | ✅ 通過 |
| 功能開關查詢 | GET /v2/dashboard/tenants/features | ✅ 通過 |
| 功能開關更新 | PUT /v2/dashboard/tenants/features/:feature | ✅ 通過 |

### 4.2 Sprint 3 待實現功能

| 功能 | 端點 | 技術要點 |
|------|------|----------|
| Admin 審核通過 | POST /v2/admin/tenants/:id/approve | Admin RBAC、Tenant 狀態機 |
| Admin 駁回申請 | POST /v2/admin/tenants/:id/reject | Admin RBAC、Reason 記錄 |

---

## 5. 任務分解 / Task Breakdown

### 5.1 US-M17-007: Admin 審核通過店鋪 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-007-01 | 實現 POST /admin/tenants/:id/approve 端點 | 2h | 待實現 |
| T-M17-007-02 | 實現 Tenant 狀態 PENDING → ACTIVE 轉換 | 1h | 待實現 |
| T-M17-007-03 | 實現 Feature Toggle 預設值初始化 | 1h | 待實現 |
| T-M17-007-04 | 整合測試：成功審核通過 | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-007-1: 審核通過開店申請（狀態變為 ACTIVE）
- [ ] AC-007-2: 初始化 Feature Toggle
- [ ] AC-007-3: 多次審核回傳錯誤

### 5.2 US-M17-008: Admin 駁回店鋪申請 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-008-01 | 實現 POST /admin/tenants/:id/reject 端點 | 1.5h | 待實現 |
| T-M17-008-02 | 實現 Reason 欄位驗證和記錄 | 0.5h | 待實現 |
| T-M17-008-03 | 整合測試：成功駁回 | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-008-1: 駁回開店申請（狀態變為 REJECTED）
- [ ] AC-008-2: 駁回後用戶可重新申請
- [ ] AC-008-3: 多次駁回回傳錯誤

---

## 6. API 規格

### 6.1 Admin 審核通過

**端點**: `POST /api/v2/admin/tenants/:id/approve`

**Request Body**:
```json
{
  "approvedFeatures": ["BOOKING_ENABLED"],
  "notes": "審核通過，預設開啟基礎功能"
}
```

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Store approved successfully",
  "data": {
    "tenantId": "uuid",
    "status": "ACTIVE",
    "approvedAt": "2026-05-13T10:30:00Z",
    "approvedBy": "admin-user-id"
  }
}
```

**Error Codes**:
| Error Code | 描述 |
|------------|------|
| E-4041 | TENANT_NOT_FOUND - 店鋪不存在 |
| E-4001 | INVALID_STORE_STATUS - 店鋪狀態非 PENDING |
| E-4001 | STORE_ALREADY_APPROVED - 店鋪已審核通過 |

### 6.2 Admin 駁回申請

**端點**: `POST /api/v2/admin/tenants/:id/reject`

**Request Body**:
```json
{
  "reason": "營業執照已過期，請重新上傳有效證件"
}
```

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Store rejected",
  "data": {
    "tenantId": "uuid",
    "status": "REJECTED",
    "rejectedAt": "2026-05-13T10:30:00Z",
    "reason": "營業執照已過期，請重新上傳有效證件",
    "rejectedBy": "admin-user-id"
  }
}
```

**Error Codes**:
| Error Code | 描述 |
|------------|------|
| E-4041 | TENANT_NOT_FOUND - 店鋪不存在 |
| E-4001 | INVALID_STORE_STATUS - 店鋪狀態非 PENDING |
| E-4001 | STORE_ALREADY_REJECTED - 店鋪已駁回 |
| E-4001 | VALIDATION_ERROR - reason 欄位空白 |

---

## 7. 測試策略

### 7.1 測試類型分佈

| 測試類型 | 數量 | 負責人 |
|----------|------|--------|
| 單元測試 (UT) | 4 | Dev |
| 整合測試 (IT) | 3 | Dev/QA |
| API E2E 測試 | 4 | QA |

### 7.2 測試優先級

| 優先級 | 測試案例 | 數量 |
|--------|----------|------|
| P0 | 審核通過流程、駁回流程、多次操作錯誤 | 6 |
| P1 | 異常資料驗證、Feature Toggle 初始化 | 3 |
| P2 | 邊界條件測試 | 2 |

---

## 8. 風險與依賴

### 8.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| Admin RBAC 權限驗證複雜度 | 低 | 中 | 使用現有的 Admin 角色校驗機制 |
| 狀態機轉換邏輯一致性 | 中 | 高 | 確保所有狀態轉換都經過測試 |

### 8.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| Sprint 2 Tenant 基本功能 | 前置 | ✅ 已完成 |
| TenantService 狀態機方法 | 基礎設施 | 待實現 |

---

## 9. Sprint 執行計劃

### 9.1 第一週 (2026-05-13 ~ 2026-05-19)

| 日期 | 重點任務 |
|------|----------|
| Day 1 (05/13) | Sprint Kickoff, US-M17-007 實現 |
| Day 2 (05/14) | US-M17-007 IT/E2E 測試 |
| Day 3 (05/15) | US-M17-008 實現 |
| Day 4 (05/16) | US-M17-008 IT/E2E 測試 |
| Day 5 (05/17) | 整合測試、Bug Fix |
| Day 6-7 | Weekend |

### 9.2 第二週 (2026-05-20 ~ 2026-05-26)

| 日期 | 重點任務 |
|------|----------|
| Day 8 (05/20) | 測試覆蓋率檢查 |
| Day 9 (05/21) | Bug Fix |
| Day 10 (05/22) | Code Review |
| Day 11 (05/23) | 準備 Sprint Review |
| Day 12 (05/24) | Sprint Review + Retro |
| Day 13 (05/25) | Buffer / Sprint 4 準備 |
| Day 14 (05/26) | Buffer |

---

## 10. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| 代碼完成 | 所有 User Stories 實作完成 | - |
| Code Review | 通過團隊 Code Review | - |
| 單元測試覆蓋率 | >= 80% (AdminService) | - |
| 整合測試通過 | IT-M17-004 ~ IT-M17-006 全部通過 | - |
| API E2E 測試通過 | API-M17-007 ~ API-M17-009 全部通過 | - |
| Admin RBAC 驗證 | 所有 Admin API 都通過角色校驗 | - |
| 文檔更新 | API 規格更新 | - |

---

## 11. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 2 計劃 | `docs/04_planning/SPRINT_02_PLAN.md` | Sprint 2 完成狀態 |
| User Stories | `docs/01_requirements/E-Commerce_FRD_v1.0.md#us-m17-007` | M17 User Stories |
| API 規格 | `docs/02_architecture/api/API_M17_Tenant.md` | M17 API 規格 |
| 測試案例 | `docs/03_testing/TC_M17_Tenant.md` | M17 測試案例 |

---

## ✅ 確認簽核

| 角色 | 確認狀態 | 簽核日期 | 備註 |
|------|----------|----------|------|
| Human User | ⏳ 待確認 | - | - |
| PM/PO (Victoria) | ⏳ 待確認 | - | - |
| SA (Amanda) | ⏳ 待確認 | - | - |
| SD (Marcus) | ⏳ 待確認 | - | - |
| Dev (David) | ⏳ 待確認 | - | - |
| QA (Quincy) | ⏳ 待確認 | - | - |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-24

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-24 | 初始版本 (Sprint 3 計劃) | - |