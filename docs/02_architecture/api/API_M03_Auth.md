# API 規格 - M03 會員與權限系統 / Auth System

> **API ID**: API-M03 (API-101 ~ API-105)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
>
> **⚠️ 修訂註記（Sprint 203／204）**：本文件 §1～§5（API-M03-001～005）是 v1.0 的原文，其回應範例（數字 `code`、`errors[].code`）與文末「錯誤碼對照表」**與實作不一致，且尚未逐項重新核對**。回應封包與錯誤碼一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準。**§6～§9（Sprint 204 新增）已依實際行為撰寫**。

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M03-001 | `/api/v2/auth/register` | POST | 會員註冊 | Guest |
| API-M03-002 | `/api/v2/auth/login` | POST | 會員登入 | Guest |
| API-M03-003 | `/api/v2/auth/refresh` | POST | 刷新 Access Token | Guest+ |
| API-M03-004 | `/api/v2/auth/logout` | POST | 會員登出 | Buyer+ |
| API-M03-005 | `/api/v2/auth/me` | GET | 取得當前用戶資訊 | Buyer+ |
| API-M03-006 | `/api/v2/auth/password/forgot` | POST | 申請密碼重設連結（Sprint 204） | Guest |
| API-M03-007 | `/api/v2/auth/password/reset` | POST | 以連結重設密碼（Sprint 204） | Guest |
| API-M03-008 | `/api/v2/auth/email/verify` | POST | 以連結完成 Email 驗證（Sprint 204） | Guest |
| API-M03-009 | `/api/v2/auth/email/verify/send` | POST | 重寄驗證信（Sprint 204） | Buyer+ |

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

## 6. API-M03-006: 申請密碼重設連結（Sprint 204）

- **端點**: `POST /api/v2/auth/password/forgot`
- **描述**: 以註冊 Email 申請一次性重設連結（30 分鐘有效）
- **對應需求**: [US-M03-006](../../01_requirements/E-Commerce_FRD_v1.0.md)、[PRD §7.4.2](../../01_requirements/E-Commerce_PRD_v1.0_Final.md)
- **角色**: Guest（不需登入）
- **限流**: 每來源 IP、每路徑 30 次／分鐘（`LoginRateLimitFilter`），超過回 `429 E-9904`

**Request Body**:
```json
{ "email": "user@example.com" }
```

**200 OK**（**Email 是否已註冊、是否冷卻中、寄信是否成功，回應完全相同**）:
```json
{
  "success": true,
  "message": "If the email is registered, a password reset link has been sent",
  "timestamp": "2026-09-26T10:30:00Z"
}
```

| 情境 | 行為 |
|------|------|
| 帳號存在、可用密碼登入 | 簽發連結並寄信 |
| Email 不存在、帳號已停用、僅以 OAuth 登入（沒有密碼） | 不寄信，回應相同 |
| 同一帳號 60 秒內已寄過 | 不再寄信，回應相同 |
| 寄信失敗 | 記錄錯誤，回應相同 |
| Email 格式不合 | `400 E-9000`（帶 `errors`） |

---

## 7. API-M03-007: 重設密碼（Sprint 204）

- **端點**: `POST /api/v2/auth/password/reset`
- **描述**: 以重設連結中的 token 設定新密碼
- **對應需求**: US-M03-006（AC-M03-006-2／006-3）
- **角色**: Guest（不需登入）

**Request Body**（JSON，兩個欄位）:

| 欄位 | 必填 | 規則 |
|------|:----:|------|
| `token` | 是 | 重設連結中的 token；不可空白、最長 128 字元 |
| `newPassword` | 是 | 新密碼；規則同註冊：8～128 字元、含大小寫與數字 |

**200 OK**: `{ "success": true, "message": "Password has been reset", … }`

成功後：連結立即失效；該會員**所有 Refresh Token 失效**（需重新登入）；登入鎖定解除。**已簽發的 Access Token 無法撤銷**，最長仍可使用至其自然到期。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 400 | `E-9000` | 新密碼格式不合。**連結不會被消耗**（格式驗證在 Service 之前），可改好再送 |
| 400 | `E-1011` | 連結無效、已使用、已過期、被新連結取代，或帳號已不可用（不區分原因） |
| 429 | `E-9904` | 來源 IP 短時間請求過多 |

---

## 8. API-M03-008: Email 驗證（Sprint 204）

- **端點**: `POST /api/v2/auth/email/verify`
- **描述**: 以驗證信中的 token 完成 Email 驗證（24 小時有效、一次性）
- **對應需求**: US-M03-007（AC-M03-007-2）
- **角色**: Guest（**不需登入**：連結常在另一個瀏覽器或裝置開啟）

**Request Body**: `{ "token": "<連結中的 token>" }`

**200 OK**: `{ "success": true, "message": "Email verified", … }`；已驗證的會員再開一次連結也回成功。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 400 | `E-9000` | token 空白或過長 |
| 400 | `E-1011` | 連結無效、已使用、已過期或被新連結取代 |
| 429 | `E-9904` | 來源 IP 短時間請求過多 |

---

## 9. API-M03-009: 重寄驗證信（Sprint 204）

- **端點**: `POST /api/v2/auth/email/verify/send`
- **描述**: 重寄 Email 驗證信給**目前登入的會員本人**（無法替他人重寄）；新連結使舊連結失效
- **對應需求**: US-M03-007（AC-M03-007-3）
- **角色**: Buyer+（需 `Authorization: Bearer`）

**Request Body**: 無。

**200 OK**: `{ "success": true, "message": "Verification email requested", … }`。**已驗證、或 60 秒內已寄過時不寄信，回應仍是成功**。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 401 | `E-1000` | 未登入 |
| 503 | `E-9905` | 寄信服務暫時無法使用（使用者主動要求重寄時才讓使用者知道；註冊時自動寄的失敗則靜默略過） |
| 429 | `E-9904` | 來源 IP 短時間請求過多 |

**註冊時**：註冊成功（交易提交後）會自動寄一次驗證信，失敗不影響註冊。

**開店申請前置條件**（`POST /api/v2/tenants/apply`）：已登入的申請者若 Email 未驗證，回 `403 E-1012`。訪客（未登入）不受此限。**此前置條件只在寄信服務能真正寄出信的環境生效**（見 PRD §7.4.2）。

---

> **⚠️ 下表是 v1.0 的舊錯誤碼，與實作不一致，請勿引用**；以 [API_Error_Codes.md](../API_Error_Codes.md) 為準。

## 📝 錯誤碼對照表（v1.0 舊碼，已由 API_Error_Codes.md 取代）

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
