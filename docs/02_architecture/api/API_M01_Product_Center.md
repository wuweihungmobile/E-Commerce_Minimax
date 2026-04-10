# API 規格 - M01 商品中心 / Product Center

> **API ID**: API-M01 (API-001 ~ API-009)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M01-001 | `/api/v2/listings` | GET | 商品/房源列表 | Guest+ |
| API-M01-002 | `/api/v2/listings/:id` | GET | 商品/房源詳情 | Guest+ |
| API-M01-003 | `/api/v2/listings/search` | GET | 關鍵字搜尋 | Guest+ |
| API-M01-004 | `/api/v2/categories` | GET | 分類列表 | Guest+ |
| API-M01-005 | `/api/v2/dashboard/listings` | GET | 店鋪商品列表 | Seller+ |
| API-M01-006 | `/api/v2/dashboard/listings` | POST | 建立商品 | Seller+ |
| API-M01-007 | `/api/v2/dashboard/listings/:id` | PUT | 更新商品 | Seller+ |
| API-M01-008 | `/api/v2/dashboard/listings/:id` | DELETE | 下架商品 | Seller+ |
| API-M01-009 | `/api/v2/dashboard/listings/:id/publish` | PUT | 發布商品 | Seller+ |

---

## 1. API-M01-001: 商品/房源列表

- **端點**: `GET /api/v2/listings`
- **描述**: 取得商品或房源列表（支援分頁/篩選）
- **對應需求**: [US-M01-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m01-001)
- **角色**: Guest+

### 1.1 Request

**Query Parameters**:

| 參數 | 類型 | 必填 | 預設值 | 說明 |
|------|------|------|--------|------|
| type | string | 否 | — | 篩選類型：`PRODUCT` 或 `ROOM` |
| status | string | 否 | ACTIVE | 狀態：`ACTIVE`, `INACTIVE`, `DRAFT` |
| page | integer | 否 | 1 | 頁碼（1-based） |
| limit | integer | 否 | 20 | 每頁筆數（1-100） |
| sort | string | 否 | createdAt:desc | 排序 |
| category | string | 否 | — | 分類名稱 |

### 1.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "listingType": "PRODUCT",
        "title": "iPhone 15 Pro",
        "description": "最新款 iPhone",
        "coverImageUrl": "https://example.com/images/iphone15.jpg",
        "basePrice": 42900,
        "currency": "TWD",
        "status": "ACTIVE",
        "tags": ["3C", "手機", "蘋果"],
        "createdAt": "2026-04-01T10:00:00.000Z",
        "updatedAt": "2026-04-01T10:00:00.000Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "totalItems": 156,
      "totalPages": 8,
      "hasNextPage": true,
      "hasPreviousPage": false
    }
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "123e4567-e89b-12d3-a456-426614174000"
}
```

---

## 2. API-M01-002: 商品/房源詳情

- **端點**: `GET /api/v2/listings/:id`
- **描述**: 取得單一商品或房源的詳細資訊
- **對應需求**: [US-M01-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m01-001)
- **角色**: Guest+

### 2.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Listing ID |

### 2.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "listingType": "PRODUCT",
    "title": "iPhone 15 Pro",
    "description": "最新款 iPhone，採用鈦金屬邊框設計",
    "coverImageUrl": "https://example.com/images/iphone15.jpg",
    "images": [
      "https://example.com/images/iphone15_1.jpg",
      "https://example.com/images/iphone15_2.jpg"
    ],
    "basePrice": 42900,
    "currency": "TWD",
    "status": "ACTIVE",
    "tags": ["3C", "手機", "蘋果"],
    "owner": {
      "id": "user-uuid",
      "displayName": "Apple Store",
      "avatarUrl": "https://example.com/avatar/apple.jpg"
    },
    "product": {
      "category": "3C",
      "brand": "Apple",
      "specifications": {
        "color": "黑色鈦金屬",
        "storage": "256GB",
        "weight": "187g"
      }
    },
    "inventory": {
      "available": 50,
      "status": "IN_STOCK"
    },
    "createdAt": "2026-04-01T10:00:00.000Z",
    "updatedAt": "2026-04-01T10:00:00.000Z"
  }
}
```

**404 Not Found**:
```json
{
  "code": 404,
  "message": "Listing not found",
  "errors": [],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 3. API-M01-003: 關鍵字搜尋

- **端點**: `GET /api/v2/listings/search`
- **描述**: 關鍵字搜尋商品或房源
- **對應需求**: [US-M01-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m01-001)
- **角色**: Guest+

### 3.1 Request

**Query Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| q | string | 是 | 關鍵字 |
| type | string | 否 | `PRODUCT` 或 `ROOM` |
| page | integer | 否 | 頁碼 |
| limit | integer | 否 | 每頁筆數 |

### 3.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "items": [...],
    "pagination": {
      "page": 1,
      "limit": 20,
      "totalItems": 45,
      "totalPages": 3,
      "hasNextPage": true,
      "hasPreviousPage": false
    }
  }
}
```

---

## 4. API-M01-005: 店鋪商品列表

- **端點**: `GET /api/v2/dashboard/listings`
- **描述**: 取得当前店铺的商品列表（含所有状态）
- **對應需求**: [US-M01-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m01-002)
- **角色**: Seller+

### 4.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |

**Query Parameters**:

| 參數 | 類型 | 必填 | 預設值 | 說明 |
|------|------|------|--------|------|
| status | string | 否 | — | 篩選狀態 |
| page | integer | 否 | 1 | 頁碼 |
| limit | integer | 否 | 20 | 每頁筆數 |

### 4.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "listingType": "PRODUCT",
        "title": "iPhone 15 Pro",
        "status": "DRAFT",
        "basePrice": 42900,
        "inventory": {
          "total": 100,
          "available": 50,
          "reserved": 10
        },
        "createdAt": "2026-04-01T10:00:00.000Z"
      }
    ],
    "pagination": {...}
  }
}
```

---

## 5. API-M01-006: 建立商品

- **端點**: `POST /api/v2/dashboard/listings`
- **描述**: 建立新商品（草稿狀態）
- **對應需求**: [US-M01-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m01-002)
- **角色**: Seller+

### 5.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| Content-Type | 是 | application/json |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |

**Request Body**:
```json
{
  "listingType": "PRODUCT",
  "title": "iPhone 15 Pro",
  "description": "最新款 iPhone",
  "coverImageUrl": "https://example.com/images/iphone15.jpg",
  "basePrice": 42900,
  "category": "3C",
  "brand": "Apple",
  "tags": ["3C", "手機"],
  "specifications": {
    "color": "黑色鈦金屬",
    "storage": "256GB"
  }
}
```

**欄位說明**:

| 欄位 | 類型 | 大小限制 | 必填 | 說明 |
|------|------|---------|------|------|
| listingType | string | — | 是 | 固定為 `PRODUCT` |
| title | string | 1-200 | 是 | 商品標題 |
| description | string | 0-5000 | 否 | 商品描述 |
| coverImageUrl | string | 0-500 | 否 | 封面圖 URL |
| basePrice | number | > 0 | 是 | 基底價格 |
| category | string | 1-50 | 是 | 分類 |
| brand | string | 0-100 | 否 | 品牌 |
| tags | string[] | — | 否 | 標籤 |
| specifications | object | — | 否 | 規格資訊 |

### 5.2 Response

**201 Created**:
```json
{
  "code": 201,
  "message": "Listing created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "listingType": "PRODUCT",
    "title": "iPhone 15 Pro",
    "status": "DRAFT",
    "basePrice": 42900,
    "createdAt": "2026-04-09T10:30:00.000Z"
  },
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
      "field": "title",
      "message": "Title is required",
      "code": "REQUIRED_FIELD"
    },
    {
      "field": "basePrice",
      "message": "Base price must be greater than 0",
      "code": "OUT_OF_RANGE"
    }
  ],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

**403 Feature Disabled**:
```json
{
  "code": 403,
  "message": "FEATURE_DISABLED_FOR_TENANT: 零售功能尚未啟用，請聯繫平台管理員。",
  "errors": [],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 6. API-M01-009: 發布商品

- **端點**: `PUT /api/v2/dashboard/listings/:id/publish`
- **描述**: 將商品狀態從 DRAFT 改為 ACTIVE
- **對應需求**: [AC-M01-002-2](../01_requirements/E-Commerce_FRD_v1.0.md#ac-m01-002-2)
- **角色**: Seller+

### 6.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Listing ID |

### 6.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Listing published successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ACTIVE",
    "updatedAt": "2026-04-09T10:30:00.000Z"
  }
}
```

**400 Invalid State Transition**:
```json
{
  "code": 400,
  "message": "Cannot publish listing with status DELETED",
  "errors": [],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 📝 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-4001 | 400 | 驗證失敗 | 檢查必填欄位 |
| E-2020 | 403 | 功能未啟用 | 聯繫平台管理員 |
| E-1001 | 401 | JWT 無效 | 重新登入 |
| E-1002 | 401 | JWT 過期 | 刷新 Token |
| E-2003 | 403 | 租戶上下文不明 | 提供 X-Tenant-ID Header |
| E-4041 | 404 | Listing 不存在 | 確認 ID |

---

**文件結束**
