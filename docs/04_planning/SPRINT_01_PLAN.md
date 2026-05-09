# Sprint 1 計劃 / Sprint 1 Plan

> **Sprint 編號**: Sprint 1
> **期間**: 2026-04-15 ~ 2026-04-28 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-15
> **基於**: Stage 6 Sprint Planning

---

## 🔴 人機協作確認點結果

### Sprint 承諾確認
**選擇**: ✅ 確認 Sprint 1 範圍合理

### Sprint 目標確認
| Sprint | 目標 | Story Points |
|---------|------|--------------|
| Sprint 1 | M03 會員系統 (核心) | 13 SP (+ 5 buffer) |

### 確認人
- **Human User**: 確認通過
- **PM/PO (Victoria)**: 待確認
- **SA (Amanda)**: 待確認
- **SD (Marcus)**: 待確認
- **Dev (David)**: 待確認
- **QA (Quincy)**: 待確認

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 1 |
| **開始日期** | 2026-04-15 |
| **結束日期** | 2026-04-28 |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 13 SP |
| **Buffer** | 5 SP (27.8%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成會員系統核心功能（註冊、登入、JWT 刷新、登出），建立認證基礎設施供後續 Sprint 使用。

### 具體目標

1. **會員註冊 (US-M03-001)** - 3 SP
   - 完成 API 端點 `POST /v2/auth/register`
   - 實現密碼驗證和 BCrypt 加密
   - 返回標準化的 RegisterResponse

2. **會員登入 (US-M03-002)** - 3 SP
   - 完成 API 端點 `POST /v2/auth/login`
   - 實現 JWT Access Token + Refresh Token 產生
   - 錯誤處理（無效憑證、帳號停用）

3. **JWT 刷新 (US-M03-003)** - 3 SP
   - 完成 API 端點 `POST /v2/auth/refresh`
   - 實現 Refresh Token 驗證和新的 Access Token 產生
   - Token 過期和無效處理

4. **會員登出 (US-M03-004)** - 2 SP
   - 完成 API 端點 `POST /v2/auth/logout`
   - 實現 Refresh Token 失效機制

5. **取得當前用戶資訊 (US-M03-005)** - 2 SP
   - 完成 API 端點 `GET /v2/auth/me`
   - 實現從 JWT 解析用戶資訊
   - 返回完整的用戶資料和租戶資訊

---

## 3. User Stories

| ID | 標題 | SP | 優先級 | 狀態 | 負責人 |
|----|------|-----|--------|------|--------|
| US-M03-001 | 會員註冊 | 3 | P0 | ✅ 已完成 | Dev |
| US-M03-002 | 會員登入 | 3 | P0 | ✅ 已完成 | Dev |
| US-M03-003 | JWT 刷新 | 3 | P0 | ✅ 已完成 | Dev |
| US-M03-004 | 會員登出 | 2 | P2 | ✅ 已完成 | Dev |
| US-M03-005 | 取得當前用戶資訊 | 2 | P1 | ✅ 已完成 | Dev |

**Sprint 1 Total**: 13 SP

---

## 4. 當前進度分析

### 4.1 已實現功能

| 功能 | 端點 | 實現位置 | 測試狀態 |
|------|------|----------|----------|
| 會員註冊 | POST /v2/auth/register | AuthController, AuthService | 待測試 |
| 會員登入 | POST /v2/auth/login | AuthController, AuthService | 待測試 |
| JWT 刷新 | POST /v2/auth/refresh | AuthController, AuthService | 待測試 |
| Logout | POST /v2/auth/logout | AuthService.logout(), RefreshTokenService | ✅ 已測試 |
| 取得用戶資訊 | GET /v2/auth/me | AuthService.getCurrentUser() | ✅ 已測試 |

### 4.2 已實現功能

| 功能 | 端點 | 實現位置 | 測試狀態 |
|------|------|----------|----------|
| Logout | POST /v2/auth/logout | AuthService.logout(), RefreshTokenService | ✅ 已測試 |
| 取得用戶資訊 | GET /v2/auth/me | AuthService.getCurrentUser() | ✅ 已測試 |

---

## 5. 任務分解 / Task Breakdown

### 5.1 US-M03-001: 會員註冊 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M03-001-01 | 單元測試：密碼加密邏輯 | 2h | 待實現 |
| T-M03-001-02 | 整合測試：成功註冊 | 1h | 待實現 |
| T-M03-001-03 | 整合測試：Email 重複 | 1h | 待實現 |
| T-M03-001-04 | API E2E 測試：註冊 API | 2h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-001: 成功註冊返回 201 和 userId
- [ ] AC-002: Email 已存在返回 409
- [ ] AC-003: 密碼格式不符返回 400
- [ ] AC-004: 預設角色為 BUYER

### 5.2 US-M03-002: 會員登入 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M03-002-01 | 單元測試：JWT Token 產生/驗證 | 2h | 待實現 |
| T-M03-002-02 | 整合測試：成功登入 | 1h | 待實現 |
| T-M03-002-03 | 整合測試：錯誤密碼 | 1h | 待實現 |
| T-M03-002-04 | API E2E 測試：登入 API | 2h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-001: 成功登入返回 200 和 accessToken + refreshToken
- [ ] AC-002: 錯誤密碼返回 401
- [ ] AC-003: 帳號不存在返回 401
- [ ] AC-004: 帳號停用返回 403

### 5.3 US-M03-003: JWT 刷新 (3 SP)

**負責人**: Dev
**預估時間**: 4 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M03-003-01 | 單元測試：Refresh Token 產生 | 1h | 待實現 |
| T-M03-003-02 | 整合測試：成功刷新 | 1h | 待實現 |
| T-M03-003-03 | 整合測試：Token 過期 | 1h | 待實現 |
| T-M03-003-04 | API E2E 測試：刷新 API | 2h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-001: 有效 Refresh Token 返回新 Access Token
- [ ] AC-002: 過期 Refresh Token 返回 401
- [ ] AC-003: 無效 Refresh Token 返回 401

### 5.4 US-M03-004: 會員登出 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M03-004-01 | 實現 Refresh Token Redis blacklist | 2h | 待實現 |
| T-M03-004-02 | 整合測試：成功登出 | 1h | 待實現 |
| T-M03-004-03 | API E2E 測試：登出 API | 2h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-001: 成功登出返回 200
- [ ] AC-002: Refresh Token 在 Redis blacklist 中
- [ ] AC-003: 使用已登出 Token 刷新失敗

### 5.5 US-M03-005: 取得當前用戶資訊 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M03-005-01 | 實現 AuthController /me 端點 | 2h | 待實現 |
| T-M03-005-02 | 整合測試：成功取得資訊 | 1h | 待實現 |
| T-M03-005-03 | API E2E 測試：/me API | 2h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-001: 有效 Token 返回 200 和用戶資訊
- [ ] AC-002: 無 Token 返回 401
- [ ] AC-003: 回傳包含 userId, email, profile, tenants

---

## 6. 測試策略

### 6.1 測試類型分佈

| 測試類型 | 數量 | 負責人 |
|----------|------|--------|
| 單元測試 (UT) | 12 | Dev |
| 整合測試 (IT) | 9 | Dev/QA |
| API E2E 測試 | 10 | QA |

### 6.2 測試優先級

| 優先級 | 測試案例 | 數量 |
|--------|----------|------|
| P0 | 密碼加密不可逆、JWT 有效/過期、成功註冊/登入、Token 刷新 | 10 |
| P1 | 角色測試、錯誤處理、RBAC | 9 |
| P2 | 長度邊界、格式驗證 | 6 |

### 6.3 測試環境

- [ ] 本機開發環境 (PostgreSQL + Redis)
- [ ] 測試資料隔離策略
- [ ] Mock 策略 (Mockito)

---

## 7. 風險與依賴

### 7.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| Refresh Token Redis blacklist 實現複雜度 | 中 | 中 | 使用 existing Redis lock service pattern |
| JWT Token 驗證在高併發下效能 | 低 | 中 | 已有的 JwtTokenService 經過驗證 |
| 測試環境 PostgreSQL/Redis 設定 | 低 | 高 | Sprint 0 已完成環境建置 |

### 7.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| Sprint 0 環境建置 | 前置 | ✅ 已完成 |
| JWT Token Service | 技術 | ✅ 已有 JwtTokenService |
| Redis 設定 | 基礎設施 | ✅ Sprint 0 已設定 |
| BCrypt PasswordEncoder | 技術 | ✅ Spring Security 已提供 |

---

## 8. Sprint 執行計劃

### 8.1 第一週 (2026-04-15 ~ 2026-04-21)

| 日期 | 重點任務 |
|------|----------|
| Day 1 (04/15) | Sprint Kickoff, US-M03-001 UT 實現 |
| Day 2 (04/16) | US-M03-001 IT/E2E 測試 |
| Day 3 (04/17) | US-M03-002 UT 實現 |
| Day 4 (04/18) | US-M03-002 IT/E2E 測試 |
| Day 5 (04/19) | US-M03-003 UT 實現 |
| Day 6-7 | Weekend |

### 8.2 第二週 (2026-04-22 ~ 2026-04-28)

| 日期 | 重點任務 |
|------|----------|
| Day 8 (04/22) | US-M03-003 IT/E2E 測試 |
| Day 9 (04/23) | US-M03-004 實現 + 測試 |
| Day 10 (04/24) | US-M03-005 實現 + 測試 |
| Day 11 (04/25) | 測試覆蓋度驗證 |
| Day 12 (04/26) | Bug Fix + Code Review |
| Day 13 (04/27) | Sprint Review + Retro |
| Day 14 (04/28) | Buffer / Sprint 2 準備 |

---

## 9. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| 代碼完成 | 所有 User Stories 實作完成 | - |
| Code Review | 通過團隊 Code Review | - |
| 單元測試覆蓋率 | >= 80% (AuthService, JwtTokenService) | - |
| 整合測試通過 | IT-M03-001 ~ IT-M03-009 全部通過 | - |
| API E2E 測試通過 | API-M03-001 ~ API-M03-010 全部通過 | - |
| RBAC 測試通過 | RBAC-001 ~ RBAC-003 全部通過 | - |
| 文檔更新 | API 規格更新 (如需要) | - |

---

## 10. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 規劃 | `docs/04_planning/Stage6_Sprint_Planning.md` | 原始 Sprint 規劃 |
| User Stories | `docs/04_planning/Stage5_UserStory_Confirmation.md` | User Story 確認 |
| 測試案例 | `docs/03_testing/TC_M03_Auth.md` | M03 測試案例 |
| API 規格 | `docs/02_architecture/api/API_M03_Auth.md` | M03 API 規格 |
| FRD | `docs/01_requirements/E-Commerce_FRD_v1.0.md` | 功能需求文檔 |

---

## ✅ 確認簽核

| 角色 | 確認狀態 | 簽核日期 |
|------|----------|----------|
| Human User | 待確認 | - |
| PM/PO (Victoria) | 待確認 | - |
| SA (Amanda) | 待確認 | - |
| SD (Marcus) | 待確認 | - |
| Dev (David) | 待確認 | - |
| QA (Quincy) | 待確認 | - |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-15
