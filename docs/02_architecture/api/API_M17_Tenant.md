# API 規格 - M17 租戶/店鋪管理 / Tenant Management

> **API ID**: API-M17 (API-501 ~ API-509)
> **版本**: v1.0
> **最後更新日期**: 2026-09-02（Sprint 110：審核端點由 `/admin/tenants/*` 更正為 `/admin/tenant-applications/*`）
> **作者**: Marcus (SD-Architect)

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|-------|
| API-M17-001 | `/api/v2/tenants/apply` | POST | 申請開店 | Guest |
| API-M17-002 | `/api/v2/tenants/my` | GET | 取得我的店鋪列表 | StoreOwner |
| API-M17-003 | `/api/v2/tenants/:id` | GET | 店鋪詳情 | Guest+ |
| API-M17-004 | `/api/v2/tenants/:id` | PUT | 更新店鋪資訊 | StoreOwner |
| API-M17-005 | `/api/v2/dashboard/tenants/features` | GET | 取得功能開關狀態 | StoreOwner+ |
| API-M17-006 | `/api/v2/dashboard/tenants/features/:feature` | PUT | 更新功能開關 | StoreOwner |
| API-M17-007 | `/api/v2/admin/tenants` | GET | 平台店鋪列表 | SUPER_ADMIN |
| API-M17-APP-001 | `/api/v2/admin/tenant-applications` | GET | 待審核開店申請列表 | SUPER_ADMIN |
| API-M17-APP-002 | `/api/v2/admin/tenant-applications/:applicationId/approve` | POST | 核准開店申請 | SUPER_ADMIN |
| API-M17-APP-003 | `/api/v2/admin/tenant-applications/:applicationId/reject` | POST | 駁回開店申請 | SUPER_ADMIN |
| ~~API-M17-008~~ | `/api/v2/admin/tenants/:id/approve` | POST | ⚠️ 舊流程，生產不可達 | SUPER_ADMIN |
| ~~API-M17-009~~ | `/api/v2/admin/tenants/:id/reject` | POST | ⚠️ 舊流程，生產不可達 | SUPER_ADMIN |

> **⚠️ 開店審核走 `API-M17-APP-*`，不走 `API-M17-008/009`**（PRD §4.3 / §9.10.2、FRD BR-M17-001）
>
> 審核的對象是 `tenant_applications`，不是 `tenants`。`Tenant` 只在核准的那一刻才被建立、且建立即 `ACTIVE`，
> 所以要求 `Tenant.status == PENDING_REVIEW` 的 `API-M17-008/009` 在生產環境永遠等不到資料。
> 它們仍存在於後端且被歷史測試案例引用，故保留編號，但已無前端引用，新功能不得使用。

---

## 1. API-M17-001: 申請開店

- **端點**: `POST /api/v2/tenants/apply`
- **描述**: 用戶申請开设新店铺
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: Guest

### 1.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Content-Type | 是 | application/json |

**Request Body**:
```json
{
  "storeName": "我的數位商店",
  "storeDescription": "專營 3C 產品與周邊配件",
  "businessType": "RETAIL_ONLY",
  "contactEmail": "contact@mystore.com",
  "contactPhone": "+886-912-345-678",
  "businessLicenseUrl": "https://example.com/docs/license.pdf"
}
```

**欄位說明**:

| 欄位 | 類型 | 大小限制 | 必填 | 說明 |
|------|------|---------|------|------|
| storeName | string | 2-100 | 是 | 店鋪名稱 |
| storeDescription | string | 0-1000 | 否 | 店鋪描述 |
| businessType | string | — | 是 | `RETAIL_ONLY` / `BOOKING_ONLY` / `HYBRID` |
| contactEmail | string | 符合 Email | 是 | 聯絡 Email |
| contactPhone | string | 0-20 | 否 | 聯絡電話 |
| businessLicenseUrl | string | URL 格式 | 否 | 營業執照 URL（Phase 2 必填）|

### 1.2 Response

**201 Created**:
```json
{
  "code": 201,
  "message": "Store application submitted successfully",
  "data": {
    "applicationId": "app-uuid-001",
    "storeName": "我的數位商店",
    "businessType": "RETAIL_ONLY",
    "status": "PENDING",
    "statusDescription": "申請已提交，等待平台審核",
    "submittedAt": "2026-04-09T10:30:00.000Z",
    "estimatedReviewDays": 3
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

### 1.3 店鋪申請狀態

| 狀態 | 說明 |
|------|------|
| PENDING | 等待審核 |
| APPROVED | 審核通過 |
| REJECTED | 審核駁回 |
| SUSPENDED | 已停權 |

---

## 2. API-M17-002: 取得我的店鋪列表

- **端點**: `GET /api/v2/tenants`
- **描述**: 取得當前用戶所屬的店鋪列表
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: StoreOwner

### 2.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |

### 2.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "tenants": [
      {
        "tenantId": "tenant-uuid-001",
        "storeName": "我的數位商店",
        "businessType": "RETAIL_ONLY",
        "status": "ACTIVE",
        "role": "OWNER",
        "memberCount": 3,
        "features": {
          "RETAIL_ENABLED": true,
          "BOOKING_ENABLED": false,
          "CMS_ENABLED": true,
          "ERP_ENABLED": true,
          "DYNAMIC_PRICING_ENABLED": false
        },
        "createdAt": "2026-04-01T10:00:00.000Z"
      },
      {
        "tenantId": "tenant-uuid-002",
        "storeName": "墾丁海景民宿",
        "businessType": "BOOKING_ONLY",
        "status": "ACTIVE",
        "role": "OWNER",
        "memberCount": 2,
        "features": {
          "RETAIL_ENABLED": false,
          "BOOKING_ENABLED": true,
          "CMS_ENABLED": true,
          "ERP_ENABLED": false,
          "DYNAMIC_PRICING_ENABLED": true
        },
        "createdAt": "2026-04-05T10:00:00.000Z"
      }
    ]
  }
}
```

---

## 3. API-M17-003: 店鋪詳情

- **端點**: `GET /api/v2/tenants/:id`
- **描述**: 取得店鋪公開資訊
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: Guest+

### 3.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Tenant ID |

### 3.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "tenantId": "tenant-uuid-001",
    "storeName": "我的數位商店",
    "storeDescription": "專營 3C 產品與周邊配件",
    "businessType": "RETAIL_ONLY",
    "status": "ACTIVE",
    "contactEmail": "contact@mystore.com",
    "logoUrl": "https://example.com/logos/store1.jpg",
    "coverImageUrl": "https://example.com/covers/store1.jpg",
    "member": {
      "displayName": "王小明",
      "avatarUrl": "https://example.com/avatar/user1.jpg",
      "joinedAt": "2026-01-15T10:00:00.000Z"
    },
    "stats": {
      "listingCount": 45,
      "totalSales": 1250000,
      "rating": 4.8
    },
    "createdAt": "2026-04-01T10:00:00.000Z"
  }
}
```

---

## 4. API-M17-004: 更新店鋪資訊

- **端點**: `PUT /api/v2/tenants/:id`
- **描述**: 更新店鋪資訊（僅限擁有者）
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: StoreOwner

### 4.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |
| Content-Type | 是 | application/json |

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Tenant ID |

**Request Body**:
```json
{
  "storeName": "我的數位商店（更新版）",
  "storeDescription": "專營最新 3C 產品與周邊配件，提供快速出貨服務",
  "contactEmail": "new-contact@mystore.com",
  "contactPhone": "+886-988-888-888",
  "logoUrl": "https://example.com/logos/store1_new.jpg",
  "coverImageUrl": "https://example.com/covers/store1_new.jpg"
}
```

### 4.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Store information updated successfully",
  "data": {
    "tenantId": "tenant-uuid-001",
    "storeName": "我的數位商店（更新版）",
    "storeDescription": "專營最新 3C 產品與周邊配件，提供快速出貨服務",
    "contactEmail": "new-contact@mystore.com",
    "updatedAt": "2026-04-09T11:00:00.000Z"
  },
  "timestamp": "2026-04-09T11:00:00.000Z",
  "requestId": "..."
}
```

---

## 5. API-M17-005: 取得功能開關狀態

- **端點**: `GET /api/v2/dashboard/tenants/features`
- **描述**: 取得當前店鋪的功能開關狀態
- **對應需求**: [BR-FT-001](../01_requirements/E-Commerce_FRD_v1.0.md#br-ft-001)
- **角色**: StoreOwner+

### 5.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |

### 5.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "tenantId": "tenant-uuid-001",
    "features": [
      {
        "featureKey": "RETAIL_ENABLED",
        "featureName": "零售功能",
        "description": "可上架實體商品",
        "isEnabled": true,
        "enabledAt": "2026-04-01T10:00:00.000Z",
        "requestedAt": null
      },
      {
        "featureKey": "BOOKING_ENABLED",
        "featureName": "民宿預訂功能",
        "description": "可上架民宿房間",
        "isEnabled": false,
        "enabledAt": null,
        "requestedAt": "2026-04-01T10:00:00.000Z"
      },
      {
        "featureKey": "DYNAMIC_PRICING_ENABLED",
        "featureName": "動態定價功能",
        "description": "可使用動態定價引擎",
        "isEnabled": false,
        "enabledAt": null,
        "requestedAt": null
      }
    ]
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 6. API-M17-006: 更新功能開關

- **端點**: `PUT /api/v2/dashboard/tenants/features/:feature`
- **描述**: 更新功能開關狀態（申請啟用功能）
- **對應需求**: [BR-FT-001](../01_requirements/E-Commerce_FRD_v1.0.md#br-ft-001)
- **角色**: StoreOwner

### 6.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |
| Content-Type | 是 | application/json |

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| feature | string | 是 | Feature Key（如 `BOOKING_ENABLED`）|

**Request Body**:
```json
{
  "enabled": true
}
```

### 6.2 Response

**200 OK** (申請已提交，等待審核):
```json
{
  "code": 200,
  "message": "Feature request submitted successfully",
  "data": {
    "featureKey": "BOOKING_ENABLED",
    "previousState": false,
    "newState": false,
    "status": "PENDING_APPROVAL",
    "statusDescription": "功能申請已提交，等待平台審核"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

### 6.3 功能開關說明

| Feature Key | 說明 | 預設值 | 申請是否需要審核 |
|-------------|------|--------|-----------------|
| RETAIL_ENABLED | 可上架實體商品 | true | 否 |
| BOOKING_ENABLED | 可上架民宿房間 | false | 是 |
| CMS_ENABLED | 可發布 CMS 貼文 | true | 否 |
| ERP_ENABLED | 可使用進銷存管理 | true | 否 |
| DYNAMIC_PRICING_ENABLED | 可使用動態定價 | false | 是 |
| PROMO_ENABLED | 可建立促銷活動 | false | 是 |

---

## 7. API-M17-007: 平台店鋪列表（Admin）

- **端點**: `GET /api/v2/admin/tenants`
- **描述**: 平台管理員取得所有店鋪列表
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: Admin

### 7.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |

**Query Parameters**:

| 參數 | 類型 | 必填 | 預設值 | 說明 |
|------|------|------|--------|------|
| status | string | 否 | — | 篩選狀態 |
| businessType | string | 否 | — | 篩選業務類型 |
| page | integer | 否 | 1 | 頁碼 |
| limit | integer | 否 | 20 | 每頁筆數 |

### 7.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "items": [
      {
        "tenantId": "tenant-uuid-001",
        "storeName": "我的數位商店",
        "businessType": "RETAIL_ONLY",
        "status": "ACTIVE",
        "owner": {
          "id": "user-uuid",
          "email": "owner@mystore.com"
        },
        "features": {
          "RETAIL_ENABLED": true,
          "BOOKING_ENABLED": false
        },
        "pendingFeatureRequests": 1,
        "createdAt": "2026-04-01T10:00:00.000Z"
      }
    ],
    "pagination": {...}
  }
}
```

---

## 8. API-M17-APP-001: 待審核開店申請列表（Admin）

- **端點**: `GET /api/v2/admin/tenant-applications`
- **描述**: 取得所有 `status = PENDING` 的開店申請，供平台管理員審核
- **對應需求**: [US-M17-007](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-007)、PRD §7.4.1
- **角色**: SUPER_ADMIN

### 8.1 Request

無參數（一律回傳全部 `PENDING` 申請，不分頁）。

### 8.2 Response

**200 OK**:
```json
{
  "code": 200,
  "data": {
    "applications": [
      {
        "applicationId": "app-uuid-001",
        "userId": "user-uuid-001",
        "storeName": "我的數位商店",
        "storeDescription": "3C 周邊代購",
        "businessType": "RETAIL_ONLY",
        "contactEmail": "owner@example.com",
        "contactPhone": "0912345678",
        "status": "PENDING",
        "submittedAt": "2026-04-09T10:30:00.000Z"
      }
    ]
  },
  "timestamp": "2026-04-09T12:00:00.000Z",
  "requestId": "..."
}
```

---

## 9. API-M17-APP-002: 核准開店申請（Admin）

- **端點**: `POST /api/v2/admin/tenant-applications/:applicationId/approve`
- **描述**: 平台管理員核准開店申請。於**同一交易**內建立 `Tenant`（`ACTIVE`）、初始化 Feature Toggle、
  建立 `tenant_members`（`StoreOwner`）、同步 `users.role`，最後回寫申請狀態
- **對應需求**: [US-M17-007](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-007)、PRD §7.4.1
- **角色**: SUPER_ADMIN

### 9.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| applicationId | UUID | 是 | **開店申請 ID**（不是 Tenant ID——此時尚無 Tenant） |

**Request Body**: 無。Feature Toggle 一律以 BR-M17-002 預設值初始化，不由審核者逐項指定。

### 9.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Tenant application approved successfully",
  "data": {
    "applicationId": "app-uuid-001",
    "tenantId": "tenant-uuid-001",
    "status": "APPROVED",
    "approvedAt": "2026-04-09T12:00:00.000Z"
  },
  "timestamp": "2026-04-09T12:00:00.000Z",
  "requestId": "..."
}
```

### 9.3 錯誤情境

| 條件 | 錯誤碼 | 說明 |
|------|--------|------|
| 找不到該申請 | E-2006 | 找不到開店申請 |
| 申請狀態非 PENDING | E-2007 | 已核准或已駁回的申請不可再次核准 |
| 申請的 `user_id` 為 NULL | E-2008 | Guest 送出的申請無關聯帳號，無從授予 StoreOwner |

---

## 10. API-M17-APP-003: 駁回開店申請（Admin）

- **端點**: `POST /api/v2/admin/tenant-applications/:applicationId/reject`
- **描述**: 平台管理員駁回開店申請。**不建立任何 `Tenant`**，僅更新申請狀態與駁回原因
- **對應需求**: [US-M17-008](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-008)
- **角色**: SUPER_ADMIN

### 10.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| applicationId | UUID | 是 | 開店申請 ID |

**Request Body**:
```json
{
  "reason": "營業執照已過期，請重新上傳有效證件"
}
```

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| reason | string | 是（`@NotBlank`） | 駁回原因，寫入 `tenant_applications.rejection_reason` |

### 10.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Tenant application rejected successfully",
  "data": {
    "applicationId": "app-uuid-001",
    "status": "REJECTED",
    "rejectedAt": "2026-04-09T12:00:00.000Z",
    "reason": "營業執照已過期，請重新上傳有效證件"
  },
  "timestamp": "2026-04-09T12:00:00.000Z",
  "requestId": "..."
}
```

### 10.3 錯誤情境

| 條件 | 錯誤碼 | 說明 |
|------|--------|------|
| reason 空白 | E-4001 | 驗證失敗（`@NotBlank`） |
| 找不到該申請 | E-2006 | 找不到開店申請 |
| 申請狀態非 PENDING | E-2007 | 已核准或已駁回的申請不可再次駁回 |

> 駁回後申請人**可重新送出新申請**（重複申請的阻擋條件只看是否存在 `PENDING` 申請，見 API-M17-001）。

---

## 📝 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-4001 | 400 | 驗證失敗 | 檢查必填欄位 |
| E-4031 | 403 | 無權操作此店鋪 | 確認擁有者身份 |
| E-4041 | 404 | 店鋪不存在 | 確認 Tenant ID |
| E-4091 | 409 | 店鋪名稱已被使用 | 使用其他名稱 |
| E-4092 | 409 | 店鋪申請已存在 | 查詢現有申請狀態 |
| E-1001 | 401 | JWT 無效 | 重新登入 |
| E-2003 | 403 | 租戶上下文不明 | 提供 X-Tenant-ID |

---

## 📝 Feature Toggle 預設值

| Feature Key | 說明 | 新店鋪預設 | 是否需審核 |
|------------|------|-----------|-----------|
| RETAIL_ENABLED | 零售功能 | true | 否 |
| BOOKING_ENABLED | 民宿預訂功能 | false | 是 |
| CMS_ENABLED | CMS 貼文功能 | true | 否 |
| ERP_ENABLED | 進銷存功能 | true | 否 |
| DYNAMIC_PRICING_ENABLED | 動態定價功能 | false | 是 |
| PROMO_ENABLED | 促銷活動功能 | false | 是 |

---

**文件結束**
