# Sprint 1 測試計劃 / Sprint 1 Test Plan

> **Sprint 編號**: Sprint 1
> **期間**: 2026-04-15 ~ 2026-04-28 (2 週)
> **模組**: M03 會員系統
> **版本**: v1.0
> **建立日期**: 2026-04-15

---

## 1. 測試範圍

### 1.1 測試對象

| 模組 | 功能 | 測試類型 |
|------|------|----------|
| M03 - 會員系統 | 會員註冊 | UT, IT, E2E |
| M03 - 會員系統 | 會員登入 | UT, IT, E2E |
| M03 - 會員系統 | JWT 刷新 | UT, IT, E2E |
| M03 - 會員系統 | 會員登出 | IT, E2E |
| M03 - 會員系統 | 取得當前用戶資訊 | IT, E2E |

### 1.2 測試案例統計

| 測試類型 | P0 | P1 | P2 | 總計 |
|----------|----|----|----|------|
| 單元測試 (UT) | 5 | 4 | 3 | 12 |
| 整合測試 (IT) | 2 | 4 | 3 | 9 |
| API E2E 測試 | 3 | 4 | 3 | 10 |
| **合計** | **10** | **12** | **9** | **31** |

---

## 2. 測試策略

### 2.1 測試金字塔

```
        ┌─────────────────┐
        │   API E2E      │  ← 10 個測試
        │   (10 tests)   │
        ├─────────────────┤
        │  Integration    │  ← 9 個測試
        │   (9 tests)    │
        ├─────────────────┤
        │     Unit        │  ← 12 個測試
        │   (12 tests)   │
        └─────────────────┘
```

### 2.2 測試環境

| 環境 | 用途 | 資料庫 | Redis |
|------|------|--------|-------|
| 本機 Dev | 開發調試 | PostgreSQL (local) | Redis (local) |
| Test | CI/CD 測試 | H2 Memory DB | Embedded Redis |
| Staging | 整合測試 | PostgreSQL (staging) | Redis (staging) |

### 2.3 測試資料策略

- **隔離原則**: 每個測試使用独立的測試資料
- **Setup/Teardown**: `@BeforeEach` / `@AfterEach`
- **工廠模式**: 使用 TestDataFactory 產生測試資料
- **Mock 策略**: Mockito for external dependencies

---

## 3. 測試案例清單

### 3.1 單元測試 (Unit Tests)

#### 3.1.1 密碼加密邏輯

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| UT-M03-001 | BCrypt密碼加密-密碼不可逆 | P0 | 加密結果無法解密為原始密碼 |
| UT-M03-002 | BCrypt密碼加密-鹽值不同 | P0 | 兩次加密結果不同（因鹽值隨機） |
| UT-M03-003 | BCrypt密碼加密-相同密碼驗證成功 | P0 | 相同密碼驗證成功 |
| UT-M03-004 | BCrypt密碼加密-不同密碼驗證失敗 | P1 | 不同密碼驗證失敗 |
| UT-M03-005 | BCrypt密碼加密-cost factor設定 | P1 | Cost factor 為 12 |

#### 3.1.2 JWT Token 產生/驗證

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| UT-M03-006 | JWT Token產生-payload正確 | P0 | Payload 包含 sub, email, roles, tenantId, iat, exp |
| UT-M03-007 | JWT Token產生-有效期限30分鐘 | P0 | exp - iat = 1800 秒 (30分鐘) |
| UT-M03-008 | JWT Token驗證-有效Token通過 | P0 | 驗證成功，回傳 true |
| UT-M03-009 | JWT Token驗證-過期Token失敗 | P1 | 驗證失敗，拋出 ExpiredException |
| UT-M03-010 | JWT Token驗證-篡改Token失敗 | P1 | 驗證失敗，拋出 SignatureException |
| UT-M03-011 | JWT Refresh Token產生-payload正確 | P1 | Payload 包含 sub, type=refresh, iat, exp |
| UT-M03-012 | JWT Refresh Token有效期限30天 | P2 | exp - iat = 30天 |

**測試位置**:
- `backend/src/test/java/com/nextkey/ecommerce/core/auth/AuthServiceTest.java`
- `backend/src/test/java/com/nextkey/ecommerce/infrastructure/security/JwtTokenServiceTest.java`

---

### 3.2 整合測試 (Integration Tests)

#### 3.2.1 會員註冊流程

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| IT-M03-001 | 會員註冊-成功註冊新會員 | P0 | 201 Created，回傳 userId |
| IT-M03-002 | 會員註冊-Email已被註冊 | P0 | 409 Conflict，回傳 EMAIL_ALREADY_EXISTS |
| IT-M03-003 | 會員註冊-密碼格式不符 | P1 | 400 Bad Request，回傳驗證錯誤 |
| IT-M03-004 | 會員註冊-預設角色為BUYER | P1 | userType 為 BUYER |

#### 3.2.2 登入/登出流程

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| IT-M03-005 | 登入-成功登入 | P0 | 200 OK，回傳 accessToken 和 refreshToken |
| IT-M03-006 | 登入-錯誤密碼 | P0 | 401 Unauthorized |
| IT-M03-007 | 登入-帳號被停用 | P1 | 403 Forbidden |
| IT-M03-008 | 登出-成功登出 | P1 | Refresh Token 已失效 |

#### 3.2.3 JWT Refresh 流程

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| IT-M03-009 | JWT Refresh流程-成功刷新 | P0 | 200 OK，回傳新 Access Token |

**測試位置**:
- `backend/src/test/java/com/nextkey/ecommerce/api/controller/AuthControllerIntegrationTest.java`

---

### 3.3 API E2E 測試

#### 3.3.1 會員註冊 API

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| API-M03-001 | POST /api/v2/auth/register-成功 | P0 | 201, data.userId 不為 null |
| API-M03-002 | POST /api/v2/auth/register-缺少必填欄位 | P0 | 400, errors 包含 password 錯誤 |
| API-M03-003 | POST /api/v2/auth/register-Email格式錯誤 | P1 | 400, errors 包含 email 格式錯誤 |

#### 3.3.2 會員登入 API

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| API-M03-004 | POST /api/v2/auth/login-成功 | P0 | 200, data.accessToken 不為 null |
| API-M03-005 | POST /api/v2/auth/login-密碼錯誤 | P0 | 401, message="Invalid credentials" |
| API-M03-006 | POST /api/v2/auth/login-帳號不存在 | P1 | 401, message="Invalid credentials" |

#### 3.3.3 Token 刷新 API

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| API-M03-007 | POST /api/v2/auth/refresh-成功 | P0 | 200, data.accessToken 不為 null |
| API-M03-008 | POST /api/v2/auth/refresh-Token過期 | P1 | 401, message="Invalid or expired refresh token" |

#### 3.3.4 取得當前用戶資訊 API

| TC ID | 測試描述 | 優先級 | 驗證點 |
|-------|----------|--------|--------|
| API-M03-009 | GET /api/v2/auth/me-成功取得 | P0 | 200, data 包含 userId, email, profile |
| API-M03-010 | GET /api/v2/auth/me-未授權 | P1 | 401, message="JWT invalid" |

**測試位置**:
- `backend/src/test/java/com/nextkey/ecommerce/api/controller/AuthControllerE2ETest.java`
- 使用 REST Assured 框架

---

## 4. 測試執行計劃

### 4.1 第一週執行計劃

| 日期 | 測試任務 | 負責人 |
|------|----------|--------|
| Day 1 (04/15) | UT-M03-001 ~ UT-M03-005 (密碼加密) | Dev |
| Day 2 (04/16) | IT-M03-001 ~ IT-M03-004 (註冊 IT) | Dev |
| Day 3 (04/17) | UT-M03-006 ~ UT-M03-010 (JWT Token) | Dev |
| Day 4 (04/18) | IT-M03-005 ~ IT-M03-007 (登入 IT) | Dev |
| Day 5 (04/19) | UT-M03-011 ~ UT-M03-012 (Refresh Token) | Dev |

### 4.2 第二週執行計劃

| 日期 | 測試任務 | 負責人 |
|------|----------|--------|
| Day 8 (04/22) | IT-M03-008 ~ IT-M03-009 (登出/刷新 IT) | Dev |
| Day 9 (04/23) | API-M03-001 ~ API-M03-003 (註冊 E2E) | QA |
| Day 10 (04/24) | API-M03-004 ~ API-M03-010 (登入/刷新/用戶 E2E) | QA |
| Day 11 (04/25) | 測試覆蓋度驗證 + 補測 | Dev/QA |
| Day 12 (04/26) | Bug Fix + Regression | Dev |
| Day 13 (04/27) | Sprint Review + Test Report | QA |

---

## 5. 測試環境設定

### 5.1 本機測試環境

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
  data:
    redis:
      host: localhost
      port: 6379
```

### 5.2 測試資料工廠

```java
@TestConfiguration
public class TestDataFactory {
    public static User createTestUser(String email) {
        return User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode("TestPass123"))
            .role(User.UserRole.BUYER)
            .status("ACTIVE")
            .emailVerified(false)
            .build();
    }
}
```

---

## 6. 品質指標

### 6.1 測試覆蓋率目標

| 類別 | 覆蓋率目標 |
|------|-----------|
| AuthService | >= 80% |
| JwtTokenService | >= 90% |
| AuthController | >= 70% |
| 整體 (Sprint 1) | >= 75% |

### 6.2 通過率目標

| 測試類型 | 通過率目標 |
|----------|-----------|
| 單元測試 (UT) | 100% |
| 整合測試 (IT) | 100% |
| API E2E 測試 | 100% |

---

## 7. 缺陷管理

### 7.1 缺陷嚴重性定義

| 等級 | 定義 | 處理方式 |
|------|------|----------|
| P0 - Critical | 功能完全不可用、資料安全問題 | 立即修復 |
| P1 - High | 功能有重大問題 | Sprint 1 內修復 |
| P2 - Medium | 功能有問題但不影響主要流程 | Sprint 2 修復 |

### 7.2 缺陷追蹤

| 缺陷 ID | 描述 | 嚴重性 | 狀態 | 修復日期 |
|---------|------|--------|------|----------|
| (待發現) | | | | |

---

## 8. 相關文件

| 文件 | 路徑 |
|------|------|
| 測試案例 | `docs/03_testing/TC_M03_Auth.md` |
| Sprint 1 計劃 | `docs/04_planning/SPRINT_01_PLAN.md` |
| Sprint 1 任務 | `docs/05_development/SPRINT_01_TASKS.md` |
| API 規格 | `docs/02_architecture/api/API_M03_Auth.md` |

---

## ✅ 確認簽核

| 角色 | 確認狀態 | 簽核日期 |
|------|----------|----------|
| QA (Quincy) | 待確認 | - |
| Dev (David) | 待確認 | - |
| PM/PO (Victoria) | 待確認 | - |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-15
