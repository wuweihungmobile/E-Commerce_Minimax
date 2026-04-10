# API 規格 - M17 租戶/店鋪管理 / Tenant Management

> **API ID**: API-M17 (API-501 ~ API-509)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|-------|
| API-M17-001 | `/api/v2/tenants/apply` | POST | 申請開店 | Guest |
| API-M17-002 | `/api/v2/tenants` | GET | 取得我的店鋪列表 | StoreOwner |
| API-M17-003 | `/api/v2/tenants/:id` | GET | 店鋪詳情 | Guest+ |
| API-M17-004 | `/api/v2/tenants/:id` | PUT | 更新店鋪資訊 | StoreOwner |
| API-M17-005 | `/api/v2/dashboard/tenants/features` | GET | 取得功能開關狀態 | StoreOwner+ |
| API-M17-006 | `/api/v2/dashboard/tenants/features/:feature` | PUT | 更新功能開關 | StoreOwner |
| API-M17-007 | `/api/v2/admin/tenants` | GET | 平台店鋪列表 | Admin |
| API-M17-008 | `/api/v2/admin/tenants/:id/approve` | POST | 審核通過店鋪 | Admin |
| API-M17-009 | `/api/v2/admin/tenants/:id/reject` | POST | 駁回店鋪申請 | Admin |

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

## 8. API-M17-008: 審核通過店鋪（Admin）

- **端點**: `POST /api/v2/admin/tenants/:id/approve`
- **描述**: 平台管理員審核通過店鋪申請
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: Admin

### 8.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Tenant ID |

**Request Body**:
```json
{
  "approvedFeatures": ["BOOKING_ENABLED"],
  "notes": "審核通過，預設開啟基礎功能"
}
```

### 8.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Tenant approved successfully",
  "data": {
    "tenantId": "tenant-uuid-001",
    "storeName": "我的數位商店",
    "status": "ACTIVE",
    "approvedFeatures": ["BOOKING_ENABLED"],
    "approvedAt": "2026-04-09T12:00:00.000Z",
    "approvedBy": "admin-uuid"
  },
  "timestamp": "2026-04-09T12:00:00.000Z",
  "requestId": "..."
}
```

---

## 9. API-M17-009: 駁回店鋪申請（Admin）

- **端點**: `POST /api/v2/admin/tenants/:id/reject`
- **描述**: 平台管理員駁回店鋪申請
- **對應需求**: [US-M17-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m17-001)
- **角色**: Admin

### 9.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Tenant ID |

**Request Body**:
```json
{
  "reason": "營業執照已過期，請重新上傳有效證件"
}
```

### 9.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Tenant application rejected",
  "data": {
    "tenantId": "tenant-uuid-001",
    "storeName": "我的數位商店",
    "status": "REJECTED",
    "rejectedAt": "2026-04-09T12:00:00.000Z",
    "rejectedBy": "admin-uuid",
    "reason": "營業執照已過期，請重新上傳有效證件"
  },
  "timestamp": "2026-04-09T12:00:00.000Z",
  "requestId": "..."
}
```

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
