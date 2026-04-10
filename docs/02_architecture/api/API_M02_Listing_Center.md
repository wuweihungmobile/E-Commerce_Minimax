# API 規格 - M02 房源中心 / Listing Center

> **API ID**: API-M02 (API-201 ~ API-207)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M02-001 | `/api/v2/listings?type=ROOM` | GET | 房源列表 | Guest+ |
| API-M02-002 | `/api/v2/listings/:id` | GET | 房源詳情 | Guest+ |
| API-M02-003 | `/api/v2/listings/:id/calendar` | GET | 日曆與價格查詢 | Guest+ |
| API-M02-004 | `/api/v2/dashboard/listings` | GET | 店鋪房源列表 | Host+ |
| API-M02-005 | `/api/v2/dashboard/listings` | POST | 建立房源 | Host+ |
| API-M02-006 | `/api/v2/dashboard/listings/:id` | PUT | 更新房源 | Host+ |
| API-M02-007 | `/api/v2/dashboard/listings/:id/status` | PUT | 更新房源狀態 | Host+ |

---

## 1. API-M02-001: 房源列表

- **端點**: `GET /api/v2/listings?type=ROOM`
- **描述**: 取得房源列表（支援地區/日期/人數過濾）
- **對應需求**: [US-M02-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m02-001)
- **角色**: Guest+

### 1.1 Request

**Query Parameters**:

| 參數 | 類型 | 必填 | 預設值 | 說明 |
|------|------|------|--------|------|
| type | string | 是 | ROOM | 固定為 ROOM |
| location | string | 否 | — | 地區關鍵字（如「高雄」） |
| checkInDate | date | 否 | — | 入住日期 (YYYY-MM-DD) |
| checkOutDate | date | 否 | — | 退房日期 (YYYY-MM-DD) |
| guests | integer | 否 | — | 入住人數 |
| page | integer | 否 | 1 | 頁碼（1-based） |
| limit | integer | 否 | 20 | 每頁筆數（1-100） |
| sort | string | 否 | createdAt:desc | 排序 |

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
        "listingType": "ROOM",
        "title": "墾丁海景民宿 - 豪華雙人房",
        "description": "面海景觀，步行至海灘 5 分鐘",
        "coverImageUrl": "https://example.com/images/room1.jpg",
        "basePrice": 2500,
        "currency": "TWD",
        "status": "ACTIVE",
        "location": "屏東縣恆春鎮",
        "maxGuests": 4,
        "amenities": ["WiFi", "空調", "冰箱", "陽台"],
        "rating": 4.8,
        "reviewCount": 128,
        "owner": {
          "id": "user-uuid",
          "displayName": "墾丁好客民宿",
          "avatarUrl": "https://example.com/avatar/host1.jpg"
        }
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "totalItems": 45,
      "totalPages": 3,
      "hasNextPage": true,
      "hasPreviousPage": false
    }
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "123e4567-e89b-12d3-a456-426614174000"
}
```

### 1.3 錯誤碼

| 錯誤碼 | HTTP 狀態 | 說明 |
|--------|-----------|------|
| E-4001 | 400 | 日期範圍無效（checkOut ≤ checkIn）|

---

## 2. API-M02-002: 房源詳情

- **端點**: `GET /api/v2/listings/:id`
- **描述**: 取得單一房源的詳細資訊（含動態價格）
- **對應需求**: [US-M02-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m02-002)
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
    "listingType": "ROOM",
    "title": "墾丁海景民宿 - 豪華雙人房",
    "description": "面海景觀，步行至海灘 5 分鐘，房間約 15 坪",
    "coverImageUrl": "https://example.com/images/room1.jpg",
    "images": [
      "https://example.com/images/room1_1.jpg",
      "https://example.com/images/room1_2.jpg",
      "https://example.com/images/room1_3.jpg"
    ],
    "basePrice": 2500,
    "currency": "TWD",
    "status": "ACTIVE",
    "location": "屏東縣恆春鎮恆春路 123 號",
    "latitude": 22.0043,
    "longitude": 120.7464,
    "maxGuests": 4,
    "amenities": ["WiFi", "空調", "冰箱", "陽台", "停車場"],
    "checkInTime": "15:00",
    "checkOutTime": "11:00",
    "tags": ["海景", "民宿", "親子友善"],
    "owner": {
      "id": "user-uuid",
      "displayName": "墾丁好客民宿",
      "avatarUrl": "https://example.com/avatar/host1.jpg",
      "rating": 4.8
    },
    "room": {
      "totalRooms": 5,
      "availableRooms": 3
    },
    "createdAt": "2026-01-15T10:00:00.000Z",
    "updatedAt": "2026-04-01T10:00:00.000Z"
  }
}
```

---

## 3. API-M02-003: 日曆與價格查詢

- **端點**: `GET /api/v2/listings/:id/calendar`
- **描述**: 查詢房源的可用日期與動態價格
- **對應需求**: [US-M02-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m02-002)
- **角色**: Guest+

### 3.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Listing ID |

**Query Parameters**:

| 參數 | 類型 | 必填 | 預設值 | 說明 |
|------|------|------|--------|------|
| start | date | 是 | — | 起始日期 (YYYY-MM-DD) |
| end | date | 是 | — | 結束日期 (YYYY-MM-DD) |
| guests | integer | 否 | 1 | 入住人數 |

### 3.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "start": "2026-05-01",
    "end": "2026-05-07",
    "guests": 2,
    "calendar": [
      {
        "date": "2026-05-01",
        "dayOfWeek": "FRIDAY",
        "status": "AVAILABLE",
        "price": 3250,
        "priceType": "WEEKEND",
        "breakdown": {
          "basePrice": 2500,
          "weekendMultiplier": 1.3
        }
      },
      {
        "date": "2026-05-02",
        "dayOfWeek": "SATURDAY",
        "status": "AVAILABLE",
        "price": 3250,
        "priceType": "WEEKEND",
        "breakdown": {
          "basePrice": 2500,
          "weekendMultiplier": 1.3
        }
      },
      {
        "date": "2026-05-03",
        "dayOfWeek": "SUNDAY",
        "status": "AVAILABLE",
        "price": 2750,
        "priceType": "HOLIDAY",
        "breakdown": {
          "basePrice": 2500,
          "holidayMultiplier": 1.1
        }
      },
      {
        "date": "2026-05-04",
        "dayOfWeek": "MONDAY",
        "status": "AVAILABLE",
        "price": 2500,
        "priceType": "WEEKDAY",
        "breakdown": {
          "basePrice": 2500,
          "weekdayMultiplier": 1.0
        }
      },
      {
        "date": "2026-05-05",
        "dayOfWeek": "TUESDAY",
        "status": "BOOKED",
        "price": null,
        "priceType": null,
        "reason": "Already booked"
      },
      {
        "date": "2026-05-06",
        "dayOfWeek": "WEDNESDAY",
        "status": "BLOCKED",
        "price": null,
        "priceType": null,
        "reason": "Host blocked"
      }
    ],
    "totalPrice": 15000,
    "averagePrice": 3000
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

### 3.3 日曆狀態說明

| 狀態 | 說明 | 是否可預訂 |
|------|------|-----------|
| AVAILABLE | 可預訂 | ✅ |
| BOOKED | 已被預訂 | ❌ |
| BLOCKED | 房東主動封鎖 | ❌ |
| MAINTENANCE | 維護中 | ❌ |

---

## 4. API-M02-004: 店鋪房源列表

- **端點**: `GET /api/v2/dashboard/listings`
- **描述**: 取得当前店铺的房源列表（含所有状态）
- **對應需求**: [US-M02-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m02-001)
- **角色**: Host+

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
        "listingType": "ROOM",
        "title": "墾丁海景民宿 - 豪華雙人房",
        "status": "ACTIVE",
        "basePrice": 2500,
        "calendar": {
          "availableToday": 3,
          "blockedToday": 1
        },
        "bookingsThisMonth": 12,
        "createdAt": "2026-01-15T10:00:00.000Z"
      }
    ],
    "pagination": {...}
  }
}
```

---

## 5. API-M02-005: 建立房源

- **端點**: `POST /api/v2/dashboard/listings`
- **描述**: 建立新房源（草稿狀態）
- **對應需求**: [US-M02-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m02-001)
- **角色**: Host+

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
  "listingType": "ROOM",
  "title": "墾丁海景民宿 - 豪華雙人房",
  "description": "面海景觀，步行至海灘 5 分鐘",
  "coverImageUrl": "https://example.com/images/room1.jpg",
  "basePrice": 2500,
  "location": "屏東縣恆春鎮恆春路 123 號",
  "latitude": 22.0043,
  "longitude": 120.7464,
  "maxGuests": 4,
  "amenities": ["WiFi", "空氣", "冰箱", "陽台"],
  "checkInTime": "15:00",
  "checkOutTime": "11:00",
  "tags": ["海景", "民宿"]
}
```

**欄位說明**:

| 欄位 | 類型 | 大小限制 | 必填 | 說明 |
|------|------|---------|------|------|
| listingType | string | — | 是 | 固定為 `ROOM` |
| title | string | 1-200 | 是 | 房源標題 |
| description | string | 0-5000 | 否 | 房源描述 |
| coverImageUrl | string | 0-500 | 否 | 封面圖 URL |
| basePrice | number | > 0 | 是 | 基底價格（每晚）|
| location | string | 1-200 | 是 | 詳細地址 |
| latitude | number | -90~90 | 否 | 緯度 |
| longitude | number | -180~180 | 否 | 經度 |
| maxGuests | integer | 1-20 | 是 | 最大入住人數 |
| amenities | string[] | — | 否 | 設施清單 |
| checkInTime | string | HH:mm | 否 | 入住時間，預設 15:00 |
| checkOutTime | string | HH:mm | 否 | 退房時間，預設 11:00 |
| tags | string[] | — | 否 | 標籤 |

### 5.2 Response

**201 Created**:
```json
{
  "code": 201,
  "message": "Listing created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "listingType": "ROOM",
    "title": "墾丁海景民宿 - 豪華雙人房",
    "status": "DRAFT",
    "basePrice": 2500,
    "createdAt": "2026-04-09T10:30:00.000Z"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 6. API-M02-007: 更新房源狀態

- **端點**: `PUT /api/v2/dashboard/listings/:id/status`
- **描述**: 更新房源狀態（上架/下架/封鎖）
- **對應需求**: [BR-M02-002](../01_requirements/E-Commerce_FRD_v1.0.md#br-m02-002)
- **角色**: Host+

### 6.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Listing ID |

**Request Body**:
```json
{
  "status": "ACTIVE"
}
```

**可用狀態值**:

| 狀態 | 說明 |
|------|------|
| DRAFT | 草稿（不可見） |
| ACTIVE | 上架（可預訂） |
| INACTIVE | 下架（不可預訂） |
| DELETED | 刪除 |

### 6.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Listing status updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ACTIVE",
    "updatedAt": "2026-04-09T10:30:00.000Z"
  }
}
```

---

## 📝 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-4001 | 400 | 驗證失敗 | 檢查必填欄位 |
| E-2020 | 403 | 民宿功能未啟用 | 聯繫平台管理員 |
| E-4041 | 404 | Listing 不存在 | 確認 ID |
| E-1001 | 401 | JWT 無效 | 重新登入 |
| E-2003 | 403 | 租戶上下文不明 | 提供 X-Tenant-ID Header |

---

**文件結束**
