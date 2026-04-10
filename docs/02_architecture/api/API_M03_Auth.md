# API 規格 - M03 會員與權限系統 / Auth System

> **API ID**: API-M03 (API-101 ~ API-105)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M03-001 | `/api/v2/auth/register` | POST | 會員註冊 | Guest |
| API-M03-002 | `/api/v2/auth/login` | POST | 會員登入 | Guest |
| API-M03-003 | `/api/v2/auth/refresh` | POST | 刷新 Access Token | Guest+ |
| API-M03-004 | `/api/v2/auth/logout` | POST | 會員登出 | Buyer+ |
| API-M03-005 | `/api/v2/auth/me` | GET | 取得當前用戶資訊 | Buyer+ |

---

## 1. API-M03-001: 會員註冊

- **端點**: `POST /api/v2/auth/register`
- **描述**: 新用戶註冊成為會員
- **對應需求**: [US-M03-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m03-001)
- **角色**: Guest

### 1.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Content-Type | 是 | application/json |

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "userType": "BUYER"
}
```

**欄位說明**:

| 欄位 | 類型 | 大小限制 | 必填 | 說明 |
|------|------|---------|------|------|
| email | string | 符合 Email 格式 | 是 | 會員 Email（唯一） |
| password | string | 8-128 字元，含大小寫字母和數字 | 是 | 密碼 |
| userType | string | BUYER/SELLER/HOST | 否 | 預設 BUYER |

### 1.2 Response

**201 Created**:
```json
{
  "code": 201,
  "message": "Registration successful",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "userType": "BUYER",
    "createdAt": "2026-04-09T10:30:00.000Z"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

**409 Conflict** (Email 已被註冊):
```json
{
  "code": 409,
  "message": "Email already exists",
  "errors": [
    {
      "field": "email",
      "message": "此 Email 已被註冊",
      "code": "EMAIL_ALREADY_EXISTS"
    }
  ],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

**400 Validation Error**:
```json
{
  "code": 400,
  "message": "Validation failed",
  "errors": [
    {
      "field": "password",
      "message": "Password must contain uppercase, lowercase and numeric characters",
      "code": "INVALID_FORMAT"
    }
  ],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 2. API-M03-002: 會員登入

- **端點**: `POST /api/v2/auth/login`
- **描述**: 會員登入，系統回傳 Access Token 和 Refresh Token
- **對應需求**: [US-M03-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m03-002)
- **角色**: Guest

### 2.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Content-Type | 是 | application/json |

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

### 2.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 1800,
    "tokenType": "Bearer"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

**401 Unauthorized** (錯誤密碼或 Email 不存在):
```json
{
  "code": 401,
  "message": "Invalid credentials",
  "errors": [],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

**403 Forbidden** (帳號被停用):
```json
{
  "code": 403,
  "message": "Account suspended",
  "errors": [],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 3. API-M03-003: 刷新 Access Token

- **端點**: `POST /api/v2/auth/refresh`
- **描述**: 使用 Refresh Token 取得新的 Access Token
- **對應需求**: [BR-M03-001](../01_requirements/E-Commerce_FRD_v1.0.md#br-m03-001)
- **角色**: Guest+

### 3.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Content-Type | 是 | application/json |

**Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 3.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // 新的 Refresh Token
    "expiresIn": 1800,
    "tokenType": "Bearer"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

**401 Unauthorized** (Refresh Token 無效或過期):
```json
{
  "code": 401,
  "message": "Invalid or expired refresh token",
  "errors": [],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 4. API-M03-004: 會員登出

- **端點**: `POST /api/v2/auth/logout`
- **描述**: 登出會員，失效 Refresh Token
- **對應需求**: [BR-M03-001](../01_requirements/E-Commerce_FRD_v1.0.md#br-m03-001)
- **角色**: Buyer+

### 4.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |

**Request Body**: (可選)
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  // 若不提供，失效當前 Token
}
```

### 4.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Logout successful",
  "data": null,
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 5. API-M03-005: 取得當前用戶資訊

- **端點**: `GET /api/v2/auth/me`
- **描述**: 取得已登入會員的個人資訊
- **對應需求**: [US-M03-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m03-002)
- **角色**: Buyer+

### 5.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |

### 5.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "userType": "BUYER",
    "status": "ACTIVE",
    "emailVerified": false,
    "profile": {
      "displayName": "User",
      "phone": null,
      "avatarUrl": null
    },
    "tenants": [
      {
        "tenantId": "tenant-uuid-1",
        "tenantName": "My Store",
        "role": "OWNER"
      }
    ],
    "createdAt": "2026-04-09T10:30:00.000Z"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 📝 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-3001 | 409 | Email 已被註冊 | 使用其他 Email |
| E-3002 | 401 | 登入認證失敗 | 檢查 Email/密碼 |
| E-3003 | 403 | 帳號被停用 | 聯繫客服 |
| E-1001 | 401 | JWT 無效 | 重新登入 |
| E-1002 | 401 | JWT 過期 | 刷新 Token |
| E-4001 | 400 | 驗證失敗 | 檢查輸入格式 |

---

## 📝 JWT Token 規格

### Access Token Payload

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // userId
  "email": "user@example.com",
  "userType": "BUYER",
  "roles": ["BUYER"],
  "tenantId": null,  // 如果隸屬於多個 tenant，需要 X-Tenant-ID
  "iat": 1712640000,
  "exp": 1712641800   // 30 分鐘後過期
}
```

### Refresh Token Payload

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "type": "refresh",
  "iat": 1712640000,
  "exp": 1715244000   // 30 天後過期
}
```

### Token 有效期

| Token 類型 | 有效期 | 儲存方式 |
|-----------|--------|---------|
| Access Token | 30 分鐘 (1800 秒) | 記憶體 |
| Refresh Token | 30 天 | HttpOnly Cookie |

---

**文件結束**
