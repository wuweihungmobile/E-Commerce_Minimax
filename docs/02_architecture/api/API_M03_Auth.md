# API 規格 - M03 會員與權限系統 / Auth System

> **API ID**: API-M03（API-M03-001 ~ API-M03-013）
> **版本**: v2.0（Sprint 241 依實作改寫）
> **最後更新日期**: 2026-10-03
> **作者**: Marcus (SD-Architect)；v2.0 依實作改寫
> **依據**: 後端實際行為——`AuthController`、`OAuthController`、`AuthService`、`JwtTokenService`、`LoginAttemptService`、`RefreshTokenService`、`AccountSecurityService`、`UserPrivacyService`
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`，**沒有**數字型的 `code`，也沒有 `EMAIL_ALREADY_EXISTS` 之類的字串碼）
>
> **⚠️ 修訂註記（Sprint 241）**：v1.0（2026-04-09）的 §1～§5 與實作有以下落差，本版已更正：
> 1. **回應封包與錯誤碼**：v1.0 範例的 `{"code": 200, …}`、`errors[].code`、文末的 `E-3001`～`E-4001` 對照表都不是實作；
> 2. **Token**：Access Token **15 分鐘**（v1.0 寫 30 分鐘）、Refresh Token 預設 **7 天**（compose 設 30 天；v1.0 寫 30 天）、`expiresIn` 是**毫秒**（`900000`；v1.0 寫秒）、Refresh Token **不是 HttpOnly Cookie**——後端沒有任何 Cookie 處理，前端存在 `localStorage`；JWT 的 claim 是 `role`／`tenantId`（不是 `userType`／`roles`）；
> 3. **停用帳號登入回 `401 E-1001`**（與帳密錯誤同一個回應，不透露帳號是否存在；v1.0 寫 `403 Account suspended`）；
> 4. **新增記載**：登入鎖定、Refresh Token 輪替與重放偵測、登出的兩種行為、**有效角色（Sprint 240，`DEF-326`）**、OAuth 兩個端點（API-M03-010／011）、資料匯出與帳戶刪除（API-M03-012／013）、JWT 規格；
> 5. **`GET /me`**：`tenants[].role` 是使用者的角色而不是店鋪角色（v1.0 範例的 `OWNER` 不存在），而且只列出 `users.tenant_id` 指向的那一家。
>
> **§6～§9（Sprint 204 新增）原本就是依實際行為撰寫**，本版未更動。

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M03-001 | `/api/v2/auth/register` | POST | 會員註冊 | Guest |
| API-M03-002 | `/api/v2/auth/login` | POST | 會員登入 | Guest |
| API-M03-003 | `/api/v2/auth/refresh` | POST | 換發 Token（輪替） | Guest+ |
| API-M03-004 | `/api/v2/auth/logout` | POST | 會員登出 | Buyer+ |
| API-M03-005 | `/api/v2/auth/me` | GET | 取得當前用戶資訊 | Buyer+ |
| API-M03-006 | `/api/v2/auth/password/forgot` | POST | 申請密碼重設連結（Sprint 204） | Guest |
| API-M03-007 | `/api/v2/auth/password/reset` | POST | 以連結重設密碼（Sprint 204） | Guest |
| API-M03-008 | `/api/v2/auth/email/verify` | POST | 以連結完成 Email 驗證（Sprint 204） | Guest |
| API-M03-009 | `/api/v2/auth/email/verify/send` | POST | 重寄驗證信（Sprint 204） | Buyer+ |
| API-M03-010 | `/api/v2/auth/oauth/login` | POST | OAuth 登入／註冊 | Guest |
| API-M03-011 | `/api/v2/auth/oauth/link` | POST | 連結 OAuth 帳號到目前會員 | Buyer+ |
| API-M03-012 | `/api/v2/auth/me/data-export` | GET | 會員資料匯出（PRD §1.5.1） | Buyer+ |
| API-M03-013 | `/api/v2/auth/me` | DELETE | 會員自助刪除帳戶（被遺忘權，PRD §1.5.1） | Buyer |

「Buyer+」＝已登入（任何有效 access token）；沒有帶 token 回 `401 E-1000`。後端的 context path 是 `/api`。

---

## 1. API-M03-001: 會員註冊

- **端點**: `POST /api/v2/auth/register`
- **描述**: 新用戶註冊成為會員。**註冊永遠不建立任何租戶關聯**（`DEF-244`）：店主只能經開店申請審核取得、店員只能經店主邀請加入
- **對應需求**: [US-M03-001](../../01_requirements/E-Commerce_FRD_v1.0.md#us-m03-001)
- **角色**: Guest

### 1.1 Request

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "fullName": "王小明",
  "phone": "+886912345678",
  "userType": "BUYER"
}
```

| 欄位 | 類型 | 必填 | 限制 | 說明 |
|------|------|:----:|------|------|
| `email` | string | 是 | Email 格式 | 唯一 |
| `password` | string | 是 | 8～128 字元，須含大寫、小寫與數字 | 以 bcrypt（強度 12）雜湊儲存 |
| `fullName` | string | 否 | | 顯示名稱 |
| `phone` | string | 否 | | |
| `userType` | string | 否 | `BUYER`／`SELLER`／`HOST`，預設 `BUYER` | **`STORE_OWNER`／`STORE_STAFF` 不接受**（400 `E-9000`）；沒有 `tenantId` 欄位 |

### 1.2 Response

**201 Created**:
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "userType": "BUYER",
    "createdAt": "2026-10-03T02:30:00Z"
  },
  "timestamp": "2026-10-03T02:30:00Z"
}
```

`userType` 是**註冊時存下的角色**。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 409 | `E-1005` | Email 已被註冊 |
| 400 | `E-9000` | 驗證失敗（帶 `errors[]`：Email 格式、密碼強度、`userType` 不在允許清單） |
| 429 | `E-9904` | 來源 IP 短時間請求過多（每來源 IP、每路徑 30 次／分鐘，`LoginRateLimitFilter`；涵蓋登入、註冊、忘記密碼、重設密碼、Email 驗證與重寄，**不含**換發與 OAuth） |

**之後會發生的事**：註冊交易提交後會自動寄一次 Email 驗證信（失敗不影響註冊；見 §9）；`emailVerified` 一開始是 `false`。

> **有效角色（Sprint 240，`DEF-326`）**：註冊存下的角色（`SELLER`／`HOST`）**不等於登入後的角色**。沒有店鋪的 `SELLER`／`HOST` 登入時以 **`BUYER`** 簽發——開店核准前不能管理商品、房源、定價、運費模板、CMS、貼文，只能像一般消費者購物；詳見 §2.3。

---

## 2. API-M03-002: 會員登入

- **端點**: `POST /api/v2/auth/login`
- **描述**: 會員登入，回傳 Access Token、Refresh Token 與使用者資訊
- **對應需求**: [US-M03-002](../../01_requirements/E-Commerce_FRD_v1.0.md#us-m03-002)
- **角色**: Guest

### 2.1 Request

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
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900000,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "fullName": "王小明",
      "role": "BUYER",
      "tenantId": "00000000-0000-0000-0000-000000000001"
    }
  },
  "timestamp": "2026-10-03T02:30:00Z"
}
```

| 欄位 | 說明 |
|------|------|
| `expiresIn` | Access Token 的有效期，**毫秒**（預設 `900000`＝15 分鐘；`jwt.access-token-expiration`） |
| `user.role` | **有效角色**（見 §2.3），也是 access token 的 `role` 宣告 |
| `user.tenantId` | 登入時解析出的租戶：`users.tenant_id`，沒有就取有效的店鋪成員資格（`tenant_members.status = ACTIVE`），再沒有就是**系統租戶佔位值** `00000000-0000-0000-0000-000000000001`（沒有店鋪的使用者，不是 `null`） |

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 401 | `E-1001` | 帳號或密碼錯誤。**Email 不存在、密碼錯誤、帳號已停用（非 ACTIVE）都回同一個**，不透露帳號是否存在 |
| 401 | `E-1004` | 帳號已被暫時鎖定（見下） |
| 400 | `E-9000` | 驗證失敗（Email 格式、缺欄位） |
| 429 | `E-9904` | 來源 IP 短時間請求過多（每來源 IP、每路徑 30 次／分鐘，`LoginRateLimitFilter`） |

**登入鎖定（`DEF-220`／`DEF-293`）**：同一個 Email（以送來的原字串為單位，區分大小寫）從第一次計入起的 **15 分鐘固定視窗**（不是滑動視窗）內，累計嘗試超過 **5 次**（第 6 次起）回 `E-1004`，直到視窗結束。「計入本次嘗試」與「判斷是否鎖定」是**同一個原子步驟，且在驗證密碼之前**——鎖定期間不做任何密碼比對；登入成功會歸零。不存在的 Email 也會計入（避免用鎖定與否列舉帳號）。已知取捨：同一帳號在同一瞬間送出 6 個以上**正確**的登入也會被鎖定。

### 2.3 有效角色（Sprint 240，`DEF-326`）

權限完全由 access token 的 `role` 宣告決定，所以簽發時推導**有效角色**：

| 條件 | 簽發的 `role` |
|------|---------------|
| `users.role` 是 `SELLER` 或 `HOST`，且解析出的租戶**不是真實店鋪**（系統租戶佔位值，或租戶解析不到） | **`BUYER`** |
| `users.role` 是 `SELLER` 或 `HOST`，且租戶是真實店鋪（`users.tenant_id` 指向店鋪，或有效的店鋪成員） | 照舊（`SELLER`／`HOST`） |
| 其他角色（`BUYER`、`STORE_OWNER`、`STORE_STAFF`、`ADMIN`、`SUPER_ADMIN`、`CFO`、`GUEST`） | 照舊 |

- 判斷依據是**租戶，不是角色標籤**；資料庫的 `users.role` **不動**（開店核准時才由管理流程改成 `STORE_OWNER`），每次簽發（登入、換發、OAuth）重新推導。
- 登入回應的 `user.role`、token 的 `role` 宣告、換發後的角色、`GET /me` 的 `userType` 一致。**註冊回應的 `userType` 不變**（是存下的角色）。
- **已簽發的 access token 在有效期內（15 分鐘）不受影響**。

---

## 3. API-M03-003: 換發 Token（輪替）

- **端點**: `POST /api/v2/auth/refresh`
- **描述**: 使用 Refresh Token 取得一組**新的** Access Token 與 Refresh Token；舊的 Refresh Token 立即失效（輪替，`DEF-219`）
- **對應需求**: [BR-M03-001](../../01_requirements/E-Commerce_FRD_v1.0.md#br-m03-001)
- **角色**: Guest+（不需要 access token，只需要有效的 refresh token）

### 3.1 Request

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9…" }
```

### 3.2 Response

**200 OK**：同 §2.2 的 `AuthResponse`（`message` 是 `Token refreshed`）；**重新解析租戶並重新推導有效角色**，所以角色或租戶的變更（開店核准、成員被移除）在換發後生效。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 401 | `E-1003` | Refresh Token 無效、已被撤銷、**已被換發過又被重放**，或與另一個請求同時使用同一顆（併發） |
| 401 | `E-1002` | Refresh Token 已過期 |
| 401 | `E-1004` | 帳號不是 ACTIVE |
| 404 | `E-1006` | 找不到使用者 |

**重放偵測（`DEF-219`／Sprint 213）**：同一顆 Refresh Token 只能換發一次。已經換發過的 token 再次出現，視為外洩訊號——撤銷該使用者**名下所有** Refresh Token（所有裝置重新登入）並回 `E-1003`；兩個請求同時用同一顆 token 換發，輸掉的那個同樣觸發全部撤銷。每顆 Refresh Token 帶隨機 `jti`（`DEF-315`），同一秒簽發的兩顆不會相同。

---

## 4. API-M03-004: 會員登出

- **端點**: `POST /api/v2/auth/logout`
- **描述**: 撤銷 Refresh Token
- **對應需求**: [BR-M03-001](../../01_requirements/E-Commerce_FRD_v1.0.md#br-m03-001)
- **角色**: Buyer+（需 `Authorization: Bearer {access_token}`）

### 4.1 Request

Request Body **選填**：
```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9…" }
```

| Body | 行為 |
|------|------|
| 帶 `refreshToken` | 只撤銷**那一顆** |
| 不帶（或空字串） | 撤銷該使用者**名下所有** Refresh Token（所有裝置登出） |

### 4.2 Response

**200 OK**:
```json
{
  "success": true,
  "message": "Logout successful",
  "data": { "success": true, "message": "Logout successful" },
  "timestamp": "2026-10-03T02:30:00Z"
}
```

**Access Token 無法撤銷**：登出後已簽發的 access token 仍可使用到它自然到期（最長 15 分鐘）。

---

## 5. API-M03-005: 取得當前用戶資訊

- **端點**: `GET /api/v2/auth/me`
- **描述**: 取得已登入會員的個人資訊
- **對應需求**: [US-M03-002](../../01_requirements/E-Commerce_FRD_v1.0.md#us-m03-002)
- **角色**: Buyer+（需 `Authorization: Bearer {access_token}`）

### 5.1 Response

**200 OK**:
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "userType": "BUYER",
    "status": "ACTIVE",
    "emailVerified": false,
    "profile": { "displayName": "王小明", "phone": "+886912345678", "avatarUrl": null },
    "tenants": [
      { "tenantId": "550e8400-e29b-41d4-a716-446655440099", "tenantName": "山居選物", "role": "STORE_OWNER" }
    ],
    "createdAt": "2026-10-03T02:30:00Z"
  },
  "timestamp": "2026-10-03T02:30:00Z"
}
```

| 欄位 | 說明 |
|------|------|
| `userType` | **有效角色**（§2.3），與登入回應的 `user.role` 一致 |
| `tenants[]` | **只列出 `users.tenant_id` 指向的那一家**（沒有就是空陣列）；透過成員資格（`tenant_members`）加入的店鋪不會出現在這裡，完整列表用 `GET /api/v2/tenants/my`。`tenants[].role` 是使用者的（有效）角色，**不是**店鋪內的角色 |
| 找不到使用者 | `404 E-1006` |

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

---

## 10. API-M03-010: OAuth 登入／註冊

- **端點**: `POST /api/v2/auth/oauth/login`
- **描述**: 以 Google／GitHub 的授權碼交換並登入；Email 不存在時自動建立會員。**走與密碼登入同一個簽發入口**（`AuthService.completeLogin`，`DEF-296`）：帳號狀態檢查、refresh token 登記、租戶解析、有效角色（§2.3）都一致
- **角色**: Guest

**Request Body**:
```json
{ "provider": "GOOGLE", "code": "<授權碼>", "redirectUri": "https://shop.example.com/oauth/callback/google" }
```

| 欄位 | 必填 | 說明 |
|------|:----:|------|
| `provider` | 是 | `GOOGLE` 或 `GITHUB` |
| `code` | 是 | 供應商回傳的授權碼 |
| `redirectUri` | 否 | 授權請求使用的 redirect URI；必須在允許的 origin 清單內 |

**200 OK**：同 §2.2 的 `AuthResponse`（`message` 是 `OAuth login successful`）。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 503 | `E-1096` | 該供應商的 OAuth client 尚未設定 |
| 400 | `E-1097` | `redirectUri` 缺漏、格式錯誤或 origin 不在允許清單 |
| 503 | `E-9903` | 供應商的授權碼交換／使用者資訊取得失敗，或 Google 帳號的 Email 未驗證 |
| 401 | `E-1004` | 對應的帳號不是 ACTIVE |
| 400 | `E-9000` | 請求內容驗證失敗 |

> **現況**：流程已實作，但**從未對真實的 Google／GitHub 驗證**（Sprint 215 紀錄），預設沒有設定 client（回 `E-1096`）；前端依設定決定是否顯示 OAuth 按鈕（`isOAuthProviderConfigured`）。啟用前需用真實供應商走一遍。

## 11. API-M03-011: 連結 OAuth 帳號

- **端點**: `POST /api/v2/auth/oauth/link`
- **描述**: 把供應商帳號連結到**目前登入的會員**
- **角色**: Buyer+

Request Body 同 §10；**200 OK**，`data` 為 `null`（`message` 是 `OAuth account linked successfully`）。錯誤碼同 §10，另有 `409 E-1008`（此供應商帳號已綁定其他使用者）。

---

## 12. API-M03-012: 會員資料匯出

- **端點**: `GET /api/v2/auth/me/data-export`
- **描述**: 會員自助下載本人在系統中的全部資料（PRD §1.5.1，Sprint 94）
- **角色**: Buyer+

**200 OK** 的 `data`：`profile`、`orders`、`bookings`、`productReviews`、`bookingReviews`、`addresses`、`notificationPreferences`、`notifications`、`supportTickets`、`oauthProviders`、`tenantMemberships`、`knownLimitations`、`exportedAt`。**已知限制**：聊天訊息與客服工單訊息串的完整內容不在匯出範圍（只列摘要），`knownLimitations` 會寫明。

## 13. API-M03-013: 會員自助刪除帳戶

- **端點**: `DELETE /api/v2/auth/me`
- **描述**: 被遺忘權（PRD §1.5.1，Sprint 94）：把帳號**匿名化**（不是實體刪除）
- **角色**: Buyer（**資料庫角色**必須是 `BUYER`；店主、店員、賣家、房東、管理員一律不適用）

**200 OK**，`data` 為 `null`。

| 狀態 | `code` | 說明 |
|:----:|--------|------|
| 403 | `E-1009` | 此帳號角色不適用自助刪除（檢查的是**資料庫角色**，不是 token 角色：沒有店鋪的 SELLER／HOST 的 token 是 `BUYER`，仍會得到這個錯誤，見 `DEF-334`） |
| 409 | `E-1010` | 尚有未結案（非終態）的訂單或訂房 |

匿名化內容：帳號狀態改為 `DELETED`（之後無法登入）、Email 改為 `…@anonymized.local`、姓名改為「已刪除的使用者」；刪除地址與 OAuth 連結；撤銷所有 Refresh Token；稽核紀錄一筆。訂單與訂房內的收件人姓名電話快照視為交易歷史，**保留不動**（已知限制）。

---

## 📝 JWT Token 規格

### Access Token

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "role": "BUYER",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "iat": 1790000000,
  "exp": 1790000900
}
```

- `role` 是**有效角色**（§2.3）；`tenantId` 是登入時解析出的租戶（沒有店鋪的使用者是系統租戶佔位值）。
- 簽章演算法 HS256；密鑰來自 `JWT_SECRET`。**密鑰含佔位標記 `change-in-production`（原始碼、`docker-compose.yml`、`.env.example` 三處出貨的預設值都含）時應用程式拒絕啟動**（`DEF-251`／`DEF-320`），部署必須設定 `JWT_SECRET`。

### Refresh Token

```json
{ "sub": "550e8400-e29b-41d4-a716-446655440000", "jti": "…", "iat": 1790000000, "exp": 1790604800 }
```

只有 `sub`（使用者 ID）、隨機 `jti`、`iat`、`exp`；**沒有** `role`／`email`／`tenantId`。把 Refresh Token 當 Bearer 送來不會被當成已驗證（`DEF-222`）。

### Token 有效期與儲存

| Token | 預設有效期 | 設定 | 儲存 |
|-------|-----------|------|------|
| Access Token | **15 分鐘**（900000 ms） | `jwt.access-token-expiration` | 前端 `localStorage`（**後端沒有任何 Cookie 處理**） |
| Refresh Token | **7 天**（604800000 ms；`docker-compose.yml` 設為 30 天） | `jwt.refresh-token-expiration` | 前端 `localStorage`；有效狀態另記在 Redis（30 天 TTL，換發與登出時標記） |

---

## 📝 本模組常見錯誤碼

完整對照見 [API_Error_Codes.md](../API_Error_Codes.md) §4。

| 錯誤碼 | HTTP | 情境 |
|--------|:----:|------|
| `E-1000` | 401 | 未帶 token 或 token 無效 |
| `E-1001` | 401 | 帳號或密碼錯誤（含停用帳號） |
| `E-1002` | 401 | Refresh Token 已過期 |
| `E-1003` | 401 | Refresh Token 無效、已撤銷、重放 |
| `E-1004` | 401 | 登入鎖定；換發／OAuth 登入時帳號不是 ACTIVE |
| `E-1005` | 409 | Email 已被註冊 |
| `E-1006` | 404 | 找不到使用者 |
| `E-1008` | 409 | OAuth 帳號已綁定其他使用者 |
| `E-1009` | 403 | 此帳號角色不適用自助刪除 |
| `E-1010` | 409 | 尚有未完成的訂單或訂房，無法刪除帳號 |
| `E-1011` | 400 | 重設／驗證連結無效或已過期 |
| `E-1012` | 403 | 請先驗證電子郵件（開店申請） |
| `E-1096`／`E-1097` | 503／400 | OAuth client 未設定／redirect_uri 不允許 |
| `E-9000` | 400 | 請求內容驗證失敗（帶 `errors[]`） |
| `E-9903` | 503 | 外部（OAuth 供應商）API 錯誤 |
| `E-9904` | 429 | 已超過速率限制 |
| `E-9905` | 503 | 寄信服務暫時無法使用 |

---

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-09 | 初版（API-M03-001～005） | SD (Marcus) |
| v1.1 | 2026-09-26／27 | Sprint 203／204：加修訂註記；新增 §6～§9（密碼重設、Email 驗證，依實際行為撰寫） | Claude |
| v2.0 | 2026-10-03 | Sprint 241 依實作改寫 §1～§5、新增 §10～§13（OAuth、資料匯出、帳戶刪除）、JWT 規格與常見錯誤碼；回應封包改為扁平封包；記載登入鎖定、Refresh Token 輪替與重放偵測、有效角色（`DEF-326`） | Claude（依使用者 Sprint 232 拍板「比照 Sprint 203」） |

---

**文件結束**
