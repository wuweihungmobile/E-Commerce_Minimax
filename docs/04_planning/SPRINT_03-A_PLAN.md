# Sprint 3-A 計劃 / Sprint 3-A Plan

> **Sprint 編號**: Sprint 3-A（補漏 Sprint）
> **期間**: 2026-04-26（單日緊急 Sprint）
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-26
> **目的**: 修復 Sprint 3 文件不一致問題，補充測試缺口

---

## 🔴 人機協作確認點

### 確認問題分析結果

**問題類型**:
| 類別 | 問題 | 根本原因 |
|------|------|----------|
| 文件不一致 | SPRINT_03_PLAN.md vs TC_M17_Tenant.md 測試數量不一致 | TC_M17_Tenant.md 未同步更新 |
| E2E 測試環境 | AdminControllerE2ETest 7 個測試因環境問題失敗 | RegisterRequest.userType 不支援 ADMIN + JWT authorities 同步問題 |
| RBAC 測試缺口 | StoreOwner/STORE_STAFF RBAC 測試不足 | 只有 IT-M17-006 (401 測試)，缺少 E2E RBAC 測試 |
| IT 測試限制 | @WithMockUser 在 MockMvc 環境有已知限制 | Spring Security filter chain 行為差異 |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 3-A |
| **類型** | 緊急補漏 Sprint |
| **期間** | 2026-04-26 (1 天) |
| **Sprint 容量** | 8h |
| **目標** | 修復文件不一致、補充測試缺口、改善 E2E 測試環境 |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 修復 Sprint 3 文件不一致問題，補充測試缺口，改善 E2E 測試環境

### 具體目標

1. **更新 TC_M17_Tenant.md** - 同步 Sprint 3 測試狀態
2. **重構 AdminControllerE2ETest** - 改善 E2E 測試環境支援 SUPER_ADMIN 測試
3. **新增 StoreOwner/STORE_STAFF RBAC E2E 測試** - 補充 RBAC 測試缺口
4. **記錄 IT 測試限制** - 建立測試策略準則

---

## 3. 問題分析摘要

### 3.1 文件不一致問題

| 問題文件 | 問題內容 | 影響 |
|----------|----------|------|
| TC_M17_Tenant.md | 缺少 API-M17-007-03/04, API-M17-008-03/04 | 文件追不上實際測試 |
| TC_M17_Tenant.md | 未更新 Sprint 3 測試狀態 (v1.4) | QA 無法準確追蹤 |
| SPRINT_03_PLAN.md | 第 505 行提到「API E2E 缺口測試已補充」，但 TC_M17_Tenant.md 未同步 | 文件不一致 |

### 3.2 三個主要測試缺口

| ID | 缺口描述 | 根本原因 |
|----|----------|----------|
| GAP-01 | AdminControllerE2ETest 需完整重構以支援 SUPER_ADMIN 角色測試 | RegisterRequest.userType 限制 + JWT authorities 同步問題 |
| GAP-02 | StoreOwner 和 STORE_STAFF 角色的 RBAC 測試應在 E2E 環境中驗證 | 只有 IT-M17-006 (401 測試)，缺少角色權限 E2E 測試 |
| GAP-03 | @WithMockUser 在 Spring Security 的 MockMvc 環境有已知限制 | IT 環境無法完全模擬真實 HTTP 環境 |

---

## 4. 任務分解 / Task Breakdown

### 4.1 更新 TC_M17_Tenant.md (同步 Sprint 3 測試狀態)

**負責人**: Dev/QA
**預估時間**: 1h

| 任務 | 描述 | 狀態 |
|------|------|------|
| T-A-001-01 | 新增 API-M17-007-03, API-M17-007-04 到 TC_M17_Tenant.md | 待實現 |
| T-A-001-02 | 新增 API-M17-008-03, API-M17-008-04 到 TC_M17_Tenant.md | 待實現 |
| T-A-001-03 | 更新 TC_M17_Tenant.md Sprint 3 測試狀態 (v1.5 → v1.6) | 待實現 |

### 4.2 重構 AdminControllerE2ETest (改善 E2E 測試環境)

**負責人**: Dev/QA
**預估時間**: 3h

| 任務 | 描述 | 解決方案 |
|------|------|----------|
| T-A-002-01 | 解決 SUPER_ADMIN 註冊問題 | 改用 Seed Script 預先建立 SUPER_ADMIN 用戶，或使用 SecurityContext 直接注入 |
| T-A-002-02 | 解決 JWT authorities 同步問題 | 直接使用 JwtTokenService 產生包含 SUPER_ADMIN 的測試 token |
| T-A-002-03 | 驗證重構後的 E2E 測試 | 確保 API-M17-007~009, API-M17-007-03/04, API-M17-008-03/04 全部通過 |

**解決方案 A（推薦）**: 使用 `JwtTokenService.generateAccessToken()` 直接產生測試 token
```java
// 直接產生 SUPER_ADMIN token，繞過 register → update role → re-login 的流程
String adminToken = jwtTokenService.generateAccessToken(
    adminUser.getId(),
    adminUser.getEmail(),
    "SUPER_ADMIN",  // 直接指定 role
    null
);
```

**解決方案 B**: 使用 Spring Security Test 注入
```java
// 使用 @WithMockUser 或 SecurityMockMvcRequestPostProcessors
.with(user(adminEmail).roles("SUPER_ADMIN"))
```

### 4.3 新增 StoreOwner/STORE_STAFF RBAC E2E 測試

**負責人**: QA
**預估時間**: 2h

| 任務 | 描述 | 測試案例 |
|------|------|----------|
| T-A-003-01 | 新增 StoreOwner RBAC E2E 測試 | API-M17-010: StoreOwner 呼叫 Admin API → 403 |
| T-A-003-02 | 新增 STORE_STAFF RBAC E2E 測試 | API-M17-011: STORE_STAFF 呼叫 Admin API → 403 |
| T-A-003-03 | 更新 TC_M17_Tenant.md | 新增 RBAC 測試章節 |

### 4.4 建立測試策略準則文檔

**負責人**: QA
**預估時間**: 1h

| 任務 | 描述 |
|------|------|
| T-A-004-01 | 建立測試策略準則文件 | 記錄 IT vs E2E 測試適用場景 |
| T-A-004-02 | 記錄 @WithMockUser 限制 | 更新到文件中 |

---

## 5. 測試策略準則（避免未來遺漏）

### 5.1 IT vs E2E 測試適用場景

| 場景 | IT 測試 | E2E 測試 | 備註 |
|------|---------|----------|------|
| 基本 CRUD | ✅ 適用 | ✅ 適用 | IT 可驗證核心邏輯 |
| JWT 認證流程 | ❌ 不適用 | ✅ 適用 | IT 無法完整模擬 JWT filter |
| RBAC 角色校驗 | ⚠️ 有限制 | ✅ 適用 | @WithMockUser 有已知限制 |
| 多租戶隔離 | ✅ 適用 | ✅ 適用 | IT 可驗證 TenantContext |
| Security filter chain | ❌ 不適用 | ✅ 適用 | IT MockMvc 配置與真實環境有差異 |

### 5.2 @WithMockUser 已知限制

**問題**: `@WithMockUser` 在 Spring Security 的 MockMvc 環境中：
- 無法完全模擬真實 HTTP 請求的 Spring Security filter chain
- 可能返回 500 而非預期的 401/403
- 建議使用真實的 JWT token 進行 E2E 測試

**解決方案**:
1. 使用 `JwtTokenService.generateAccessToken()` 產生測試 token
2. 在 E2E 測試中使用真實 HTTP 請求
3. IT 測試僅用於驗證 business logic，不驗證 Security filter chain

---

## 6. 新增測試案例

### 6.1 AdminControllerE2ETest 重構後測試案例

| TC ID | 測試案例 | 預期結果 | 狀態 |
|-------|----------|----------|------|
| API-M17-007 | Admin 審核通過店鋪 | 200, status=ACTIVE | 待重構 |
| API-M17-008 | Admin 審核駁回店鋪 | 200, status=REJECTED | 待重構 |
| API-M17-009 | Admin 取得店鋪列表 | 200, data.items | 待重構 |
| API-M17-007-03 | 非 PENDING 狀態審核 | 400 INVALID_STORE_STATUS | 待重構 |
| API-M17-007-04 | 非 Admin 審核 (BUYER) | 403 Forbidden | 待重構 |
| API-M17-008-03 | 空白 reason | 400 VALIDATION_ERROR | 待重構 |
| API-M17-008-04 | 非 Admin 駁回 (BUYER) | 403 Forbidden | 待重構 |

### 6.2 新增 StoreOwner/STORE_STAFF RBAC E2E 測試

| TC ID | 測試案例 | 角色 | 預期結果 | 狀態 |
|-------|----------|------|----------|------|
| API-M17-010 | StoreOwner 呼叫 Admin approve | STORE_OWNER | 403 Forbidden | 待新增 |
| API-M17-011 | STORE_STAFF 呼叫 Admin approve | STORE_STAFF | 403 Forbidden | 待新增 |
| API-M17-012 | StoreOwner 呼叫 Admin reject | STORE_OWNER | 403 Forbidden | 待新增 |
| API-M17-013 | STORE_STAFF 呼叫 Admin reject | STORE_STAFF | 403 Forbidden | 待新增 |

---

## 7. 文件更新計劃

### 7.1 需要更新的文件

| 文件 | 更新內容 | 負責人 |
|------|----------|--------|
| TC_M17_Tenant.md | 新增 API-M17-007-03/04, API-M17-008-03/04, API-M17-010~013, 更新版本為 v1.6 | Dev/QA |
| SPRINT_03_PLAN.md | 新增 Sprint 3-A 補漏說明，引用更新後的 TC | Dev |
| SPRINT_03_TEST_PLAN.md | 新增測試策略準則章節 | QA |

---

## 8. 執行計劃

### 8.1 Day 1 (2026-04-26)

| 時間 | 任務 | 負責人 |
|------|------|--------|
| 09:00-10:00 | 確認問題分析結果，更新 TC_M17_Tenant.md | Dev/QA |
| 10:00-13:00 | 重構 AdminControllerE2ETest (使用 JwtTokenService 直接產生 token) | Dev |
| 13:00-14:00 | 午餐 | - |
| 14:00-16:00 | 新增 StoreOwner/STORE_STAFF RBAC E2E 測試 | QA |
| 16:00-17:00 | 建立測試策略準則文件，更新相關文件 | QA/Dev |
| 17:00-18:00 | 驗證所有測試通過，更新文件版本 | Dev/QA |

---

## 9. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| TC_M17_Tenant.md 更新 | 新增 API-M17-007-03/04, API-M17-008-03/04, API-M17-010~013 | - |
| AdminControllerE2ETest 重構 | 使用 JwtTokenService 直接產生 SUPER_ADMIN token，所有 7 個測試通過 | - |
| RBAC E2E 測試新增 | 新增 4 個 StoreOwner/STORE_STAFF RBAC 測試 | - |
| 測試策略準則建立 | IT vs E2E 適用場景清楚記錄 | - |
| 文件一致性驗證 | 所有 Sprint 3 相關文件測試數量一致 | - |

---

## 10. 風險與依賴

### 10.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| E2E 測試重構可能影響現有測試 | 低 | 中 | 先備份現有測試，重構後驗證 |
| 時間不足 | 中 | 中 | 優先完成核心任務（TC 更新 + Admin E2E 重構） |

### 10.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| JwtTokenService | 技術 | ✅ 已有 |
| Spring Security Test | 技術 | ✅ 已有 |
| AdminControllerE2ETest | 現有測試 | ⏳ 待重構 |

---

## 11. 預防未來遺漏的措施

### 11.1 文件同步檢查清單

每次 Sprint 結束時，必須確認：
- [ ] TC_M17_Tenant.md 已更新 Sprint N 測試狀態
- [ ] SPRINT_N_PLAN.md 的測試數量與 TC 一致
- [ ] 所有新增的測試案例都記錄在 TC 中
- [ ] IT vs E2E 測試適用場景已確認

### 11.2 測試環境配置標準

1. **SUPER_ADMIN 測試**: 使用 `JwtTokenService.generateAccessToken()` 直接產生 token，不依賴 register → update role → re-login
2. **RBAC 測試**: 優先使用 E2E 測試驗證，IT 測試僅驗證 business logic
3. **Security filter chain 測試**: 只能在 E2E 環境中進行，IT 環境不適用

---

## 12. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 3 QA 驗證報告 | `docs/03_testing/SPRINT_03_QA_VERIFICATION.md` | 發現問題的來源 |
| TC M17 Tenant | `docs/03_testing/TC_M17_Tenant.md` | 待更新 |
| Sprint 3 Plan | `docs/04_planning/SPRINT_03_PLAN.md` | 待更新 |
| Sprint 3 Test Plan | `docs/03_testing/SPRINT_03_TEST_PLAN.md` | 待更新 |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-26

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-26 | 初始版本（基於 Sprint 3 QA 驗證報告發現的問題） | - |