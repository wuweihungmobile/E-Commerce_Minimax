# M03 會員系統測試案例 / Auth System Test Cases

> **模組**: M03 會員與權限系統
> **版本**: v1.0
> **建立日期**: 2026-04-10
> **依據**: API_M03_Auth.md, SRD_Database_Schema.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 5 | 4 | 3 | 12 |
| IT | 2 | 2 | 1 | 5 |
| API | 3 | 3 | 2 | 8 |
| **合計** | 10 | 9 | 6 | **25** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 密碼加密邏輯

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M03-001 | BCrypt密碼加密-密碼不可逆 | P0 | 無 | 1. 輸入明文密碼 "SecurePass123"<br>2. 呼叫 BCrypt 加密<br>3. 嘗試用加密結果解密 | 加密結果無法解密為原始密碼 |
| UT-M03-002 | BCrypt密碼加密-鹽值不同 | P0 | 無 | 1. 對同一密碼加密兩次<br>2. 比較兩次結果 | 兩次加密結果不同（因鹽值隨機） |
| UT-M03-003 | BCrypt密碼加密-相同密碼驗證成功 | P0 | 無 | 1. 加密密碼並儲存<br>2. 使用 BCrypt.check() 驗證 | 相同密碼驗證成功 |
| UT-M03-004 | BCrypt密碼加密-不同密碼驗證失敗 | P1 | 無 | 1. 加密密碼 "Pass123"<br>2. 驗證 "Pass456" | 不同密碼驗證失敗 |
| UT-M03-005 | BCrypt密碼加密-cost factor設定 | P1 | 無 | 1. 確認 BCrypt cost factor = 12<br>2. 加密密碼並測量時間 | Cost factor 為 12，加密時間約 200-400ms |

### 1.2 JWT Token 產生/驗證

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M03-006 | JWT Token產生-payload正確 | P0 | 無 | 1. 產生 Access Token<br>2. 解碼 JWT payload | Payload 包含 sub, email, roles, tenantId, iat, exp |
| UT-M03-007 | JWT Token產生-有效期限30分鐘 | P0 | 無 | 1. 產生 Access Token<br>2. 檢查 exp 欄位 | exp - iat = 1800 秒 (30分鐘) |
| UT-M03-008 | JWT Token驗證-有效Token通過 | P0 | 已有有效 JWT | 1. 驗證有效 Token | 驗證成功，回傳 true |
| UT-M03-009 | JWT Token驗證-過期Token失敗 | P1 | 已過期 JWT | 1. 驗證過期 Token | 驗證失敗，拋出 ExpiredException |
| UT-M03-010 | JWT Token驗證-篡改Token失敗 | P1 | 被篡改 JWT | 1. 驗證篡改過的 Token | 驗證失敗，拋出 SignatureException |
| UT-M03-011 | JWT Refresh Token產生-payload正確 | P1 | 無 | 1. 產生 Refresh Token<br>2. 解碼 payload | Payload 包含 sub, type=refresh, iat, exp |
| UT-M03-012 | JWT Refresh Token有效期限30天 | P2 | 無 | 1. 產生 Refresh Token<br>2. 檢查 exp 欄位 | exp - iat = 30天 |

---

## 2. 整合測試 (Integration Tests)

### 2.1 會員註冊流程

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M03-001 | 會員註冊-成功註冊新會員 | P0 | 資料庫乾淨 | 1. POST /api/v2/auth/register<br>2. 驗證資料庫有一筆新 users 記錄 | 201 Created，回傳 userId |
| IT-M03-002 | 會員註冊-Email已被註冊 | P0 | Email 已存在 | 1. POST /api/v2/auth/register (相同 Email) | 409 Conflict，回傳 EMAIL_ALREADY_EXISTS |
| IT-M03-003 | 會員註冊-密碼格式不符 | P1 | 無 | 1. POST /api/v2/auth/register (密碼: "123") | 400 Bad Request，回傳驗證錯誤 |
| IT-M03-004 | 會員註冊-預設角色為BUYER | P1 | 無 | 1. POST /api/v2/auth/register (不指定 userType)<br>2. 查詢資料庫 | userType 為 BUYER |

### 2.2 登入/登出流程

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M03-005 | 登入-成功登入 | P0 | 會員已註冊 | 1. POST /api/v2/auth/login<br>2. 驗證回傳 accessToken 和 refreshToken | 200 OK，回傳雙 Token |
| IT-M03-006 | 登入-錯誤密碼 | P0 | 會員已註冊 | 1. POST /api/v2/auth/login (錯誤密碼) | 401 Unauthorized |
| IT-M03-007 | 登入-帳號被停用 | P1 | 會員 status = SUSPENDED | 1. POST /api/v2/auth/login | 403 Forbidden |
| IT-M03-008 | 登出-成功登出 | P1 | 已有有效 Refresh Token | 1. POST /api/v2/auth/logout<br>2. 嘗試使用同一 Refresh Token 刷新 | Refresh Token 已失效 |
| IT-M03-009 | JWT Refresh流程-成功刷新 | P1 | 有效 Refresh Token | 1. POST /api/v2/auth/refresh<br>2. 取得新 Access Token | 200 OK，回傳新 Access Token |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 會員註冊 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M03-001 | POST /api/v2/auth/register-成功 | P0 | 無 | 1. POST /api/v2/auth/register<br>Body: {"email":"new@test.com","password":"SecurePass123"} | 201, data.userId 不為 null |
| API-M03-002 | POST /api/v2/auth/register-缺少必填欄位 | P0 | 無 | 1. POST /api/v2/auth/register<br>Body: {"email":"test@test.com"} | 400, errors 包含 password 錯誤 |
| API-M03-003 | POST /api/v2/auth/register-Email格式錯誤 | P1 | 無 | 1. POST /api/v2/auth/register<br>Body: {"email":"invalid-email","password":"SecurePass123"} | 400, errors 包含 email 格式錯誤 |

### 3.2 會員登入 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M03-004 | POST /api/v2/auth/login-成功 | P0 | 會員已註冊 | 1. POST /api/v2/auth/login<br>Body: {"email":"user@test.com","password":"SecurePass123"} | 200, data.accessToken 不為 null |
| API-M03-005 | POST /api/v2/auth/login-密碼錯誤 | P0 | 會員已註冊 | 1. POST /api/v2/auth/login (錯誤密碼) | 401, message="Invalid credentials" |
| API-M03-006 | POST /api/v2/auth/login-帳號不存在 | P1 | 無 | 1. POST /api/v2/auth/login (不存在的 Email) | 401, message="Invalid credentials" |

### 3.3 Token 刷新 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M03-007 | POST /api/v2/auth/refresh-成功 | P0 | 有效 Refresh Token | 1. POST /api/v2/auth/refresh<br>Body: {"refreshToken":"valid_token"} | 200, data.accessToken 不為 null |
| API-M03-008 | POST /api/v2/auth/refresh-Token過期 | P1 | 過期 Refresh Token | 1. POST /api/v2/auth/refresh | 401, message="Invalid or expired refresh token" |

### 3.4 取得當前用戶資訊 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M03-009 | GET /api/v2/auth/me-成功取得 | P0 | 有效 Access Token | 1. GET /api/v2/auth/me<br>Header: Authorization: Bearer {token} | 200, data 包含 userId, email, profile |
| API-M03-010 | GET /api/v2/auth/me-未授權 | P1 | 無 Token | 1. GET /api/v2/auth/me | 401, message="JWT invalid" |

---

## 4. RBAC 權限測試

### 4.1 角色繼承測試

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| RBAC-001 | 角色繼承-OWNER包含STAFF權限 | P1 | OWNER 角色 | 1. 以 OWNER 角色存取 STAFF API | 存取成功 |
| RBAC-002 | 角色繼承-STAFF不包含OWNER權限 | P1 | STAFF 角色 | 1. 以 STAFF 角色存取 OWNER 專屬 API | 403 Forbidden |
| RBAC-003 | 角色繼承-BUYER不包含SELLER權限 | P2 | BUYER 角色 | 1. 以 BUYER 角色存取 SELLER API | 403 Forbidden |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 密碼不可明文儲存 | UT-M03-001, UT-M03-002 | P0 |
| JWT Token 過期後 Refresh Token 可續命 | IT-M03-009, API-M03-007 | P0 |
| 角色繼承正確 (OWNER > STAFF > BUYER) | RBAC-001, RBAC-002, RBAC-003 | P0 |
| 多租戶資料隔離 | (見 TC_M17_Tenant.md) | P0 |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-10
