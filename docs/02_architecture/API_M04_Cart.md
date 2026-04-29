# API 規格文件 - M04 購物車模組 / API Specification - M04 Cart Module

> **文件版本**: v1.0
> **建立日期**: 2026-04-28
> **基於 Sprint**: Sprint 4
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M04 |
| **模組名稱** | 購物車 / Cart |
| **描述** | 買家可將商品/房源加入購物車、檢視、修改數量、移除商品 |
| **使用角色** | BUYER |
| **依賴服務** | Redis (購物車儲存), ListingService, ProductSkuService |
| **多租戶隔離** | 每位買家只能操作自己的購物車，通過 userId + tenantId 隔離 |

### 1.2 User Stories

| ID | 標題 | SP | 優先級 | 狀態 |
|----|------|-----|--------|------|
| US-M04-001 | 加入購物車 | 2 | P0 | 待實現 |
| US-M04-002 | 檢視購物車 | 2 | P0 | 待實現 |
| US-M04-003 | 更新數量 | 2 | P0 | 待實現 |
| US-M04-004 | 移除商品 | 2 | P0 | 待實現 |

---

## 2. API 端點總覽 / API Endpoints Summary

| 方法 | 路徑 | 描述 | 認證角色 | 預期狀態碼 |
|------|------|------|----------|------------|
| **POST** | `/api/v2/cart/items` | 加入購物車 | BUYER | 201 Created |
| **GET** | `/api/v2/cart` | 檢視購物車 | BUYER | 200 OK |
| **GET** | `/api/v2/cart/items` | 檢視購物車項目列表 | BUYER | 200 OK |
| **PUT** | `/api/v2/cart/items/{cartItemKey}` | 更新商品數量 | BUYER | 200 OK |
| **DELETE** | `/api/v2/cart/items/{cartItemKey}` | 移除商品 | BUYER | 200 OK |

---

## 3. 資料模型 / Data Models

### 3.1 Request DTOs

#### AddItemRequest - 加入購物車請求

```json
{
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "skuId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 2,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
```

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| `listingId` | UUID | **必填** | 商品/房源 ID |
| `skuId` | UUID | 選填 | SKU ID（僅商品有 SKU，房源可忽略） |
| `quantity` | Integer | **必填** | 數量，範圍 1-999 |
| `startDate` | LocalDate | 選填 | 入住日期（ROOM 類型必填） |
| `endDate` | LocalDate | 選填 | 退房日期（ROOM 類型必填） |

#### UpdateItemRequest - 更新數量請求

```json
{
  "quantity": 3
}
```

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| `quantity` | Integer | **必填** | 新數量，範圍 1-999 |

### 3.2 Response DTOs

#### CartItemResponse - 購物車項目響應

```json
{
  "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "精緻雙人房",
  "coverImageUrl": "https://example.com/images/room-001.jpg",
  "skuId": "550e8400-e29b-41d4-a716-446655440001",
  "skuCode": "ROOM-STD-001",
  "specName": "標準入住",
  "quantity": 2,
  "unitPrice": 1500.00,
  "subtotal": 3000.00,
  "listingType": "ROOM",
  "addedAt": "2026-04-28T10:30:00Z",
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
```

| 欄位 | 類型 | 說明 |
|------|------|------|
| `cartItemKey` | String | 購物車項目唯一識別鍵（格式：`listingId:skuId:startDate:endDate`） |
| `listingId` | UUID | 商品/房源 ID |
| `title` | String | 商品/房源名稱 |
| `coverImageUrl` | String | 封面圖片 URL |
| `skuId` | UUID | SKU ID（可為 null） |
| `skuCode` | String | SKU 編碼（可為 null） |
| `specName` | String | 規格名稱（可為 null） |
| `quantity` | Integer | 數量 |
| `unitPrice` | BigDecimal | 單價 |
| `subtotal` | BigDecimal | 小計金額 |
| `listingType` | String | 類型：PRODUCT 或 ROOM |
| `addedAt` | Instant | 加入購物車時間 |
| `startDate` | LocalDate | 入住日期（ROOM 類型） |
| `endDate` | LocalDate | 退房日期（ROOM 類型） |

#### CartResponse - 購物車響應

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440099",
  "cartKey": "cart:550e8400-e29b-41d4-a716-446655440099:550e8400-e29b-41d4-a716-446655440001",
  "items": [
    {
      "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
      "listingId": "550e8400-e29b-41d4-a716-446655440000",
      "title": "精緻雙人房",
      "coverImageUrl": "https://example.com/images/room-001.jpg",
      "skuId": "550e8400-e29b-41d4-a716-446655440001",
      "skuCode": "ROOM-STD-001",
      "specName": "標準入住",
      "quantity": 2,
      "unitPrice": 1500.00,
      "subtotal": 3000.00,
      "listingType": "ROOM",
      "addedAt": "2026-04-28T10:30:00Z",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03"
    }
  ],
  "totalItems": 2,
  "totalAmount": 3000.00,
  "currency": "TWD",
  "updatedAt": "2026-04-28T10:30:00Z"
}
```

| 欄位 | 類型 | 說明 |
|------|------|------|
| `userId` | UUID | 買家用戶 ID |
| `cartKey` | String | 購物車唯一鍵 |
| `items` | List\<CartItemResponse\> | 購物車項目列表 |
| `totalItems` | Integer | 總項目數量 |
| `totalAmount` | BigDecimal | 總金額 |
| `currency` | String | 貨幣（預設 TWD） |
| `updatedAt` | Instant | 最後更新時間 |

#### AddItemResponse - 加入購物車響應

```json
{
  "success": true,
  "item": {
    "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "精緻雙人房",
    "quantity": 2,
    "unitPrice": 1500.00,
    "subtotal": 3000.00,
    "listingType": "ROOM",
    "startDate": "2026-06-01",
    "endDate": "2026-06-03"
  },
  "totalItemsInCart": 2,
  "message": "Item added to cart successfully"
}
```

| 欄位 | 類型 | 說明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `item` | CartItemResponse | 新增的購物車項目 |
| `totalItemsInCart` | Integer | 購物車總項目數 |
| `message` | String | 訊息 |

---

## 4. API 端點詳細規格 / API Endpoint Details

### 4.1 US-M04-001: 加入購物車 / Add Item to Cart

#### Endpoint

| 欄位 | 內容 |
|------|------|
| **HTTP Method** | `POST` |
| **Path** | `/api/v2/cart/items` |
| **Description** | 買家可將商品/房源加入購物車 |
| **認證** | Required (BUYER role) |

#### Request

**Headers:**
| Header | 值 | 必填 |
|--------|-----|------|
| `Authorization` | `Bearer {access_token}` | **必填** |
| `Content-Type` | `application/json` | **必填** |

**Request Body:**
```json
{
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "skuId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 2,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
```

#### Response

**201 Created:**
```json
{
  "code": 201,
  "message": "Item added to cart",
  "data": {
    "success": true,
    "item": {
      "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
      "listingId": "550e8400-e29b-41d4-a716-446655440000",
      "title": "精緻雙人房",
      "coverImageUrl": "https://example.com/images/room-001.jpg",
      "skuId": "550e8400-e29b-41d4-a716-446655440001",
      "skuCode": "ROOM-STD-001",
      "specName": "標準入住",
      "quantity": 2,
      "unitPrice": 1500.00,
      "subtotal": 3000.00,
      "listingType": "ROOM",
      "addedAt": "2026-04-28T10:30:00Z",
      "startDate": "2026-06-01",
      "endDate": "2026-06-03"
    },
    "totalItemsInCart": 2,
    "message": "Item added to cart successfully"
  }
}
```

#### Error Responses

| HTTP Status | Error Code | 說明 |
|-------------|------------|------|
| 400 Bad Request | E-5006 | Invalid quantity (數量不在 1-999 範圍) |
| 400 Bad Request | E-4003 | Invalid date range (ROOM 類型未提供日期) |
| 400 Bad Request | E-4004 | Check-out must be after check-in |
| 401 Unauthorized | E-1000 | Authentication required |
| 403 Forbidden | E-1007 | Insufficient permissions (非 BUYER 角色) |
| 404 Not Found | E-3000 | Listing not found (房源不存在) |
| 500 Internal Server Error | E-9900 | Internal server error |
| 503 Service Unavailable | E-9902 | Redis error |

#### Business Rules

1. **同一商品累加**: 若相同 listingId + skuId + 日期範圍的項目已存在，數量會累加
2. **ROOM 類型驗證**: 必須提供 startDate 和 endDate，且 endDate > startDate
3. **PRODUCT 類型**: 可選是否提供 skuId，無日期範圍
4. **庫存檢查**: 不在加入購物車時檢查，僅在結帳時檢查

---

### 4.2 US-M04-002: 檢視購物車 / View Cart

#### Endpoint

| 欄位 | 內容 |
|------|------|
| **HTTP Method** | `GET` |
| **Path** | `/api/v2/cart` |
| **Description** | 買家可查看自己的購物車內容 |
| **認證** | Required (BUYER role) |

#### Request

**Headers:**
| Header | 值 | 必填 |
|--------|-----|------|
| `Authorization` | `Bearer {access_token}` | **必填** |

#### Response

**200 OK:**
```json
{
  "code": 200,
  "message": "Cart retrieved",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440099",
    "cartKey": "cart:550e8400-e29b-41d4-a716-446655440099:550e8400-e29b-41d4-a716-446655440001",
    "items": [
      {
        "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "title": "精緻雙人房",
        "coverImageUrl": "https://example.com/images/room-001.jpg",
        "skuId": "550e8400-e29b-41d4-a716-446655440001",
        "skuCode": "ROOM-STD-001",
        "specName": "標準入住",
        "quantity": 2,
        "unitPrice": 1500.00,
        "subtotal": 3000.00,
        "listingType": "ROOM",
        "addedAt": "2026-04-28T10:30:00Z",
        "startDate": "2026-06-01",
        "endDate": "2026-06-03"
      }
    ],
    "totalItems": 2,
    "totalAmount": 3000.00,
    "currency": "TWD",
    "updatedAt": "2026-04-28T10:30:00Z"
  }
}
```

**空購物車 (200 OK):**
```json
{
  "code": 200,
  "message": "Cart retrieved",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440099",
    "cartKey": "cart:550e8400-e29b-41d4-a716-446655440099:550e8400-e29b-41d4-a716-446655440001",
    "items": [],
    "totalItems": 0,
    "totalAmount": 0.00,
    "currency": "TWD",
    "updatedAt": "2026-04-28T10:30:00Z"
  }
}
```

#### Error Responses

| HTTP Status | Error Code | 說明 |
|-------------|------------|------|
| 401 Unauthorized | E-1000 | Authentication required |
| 403 Forbidden | E-1007 | Insufficient permissions (非 BUYER 角色) |
| 500 Internal Server Error | E-9900 | Internal server error |
| 503 Service Unavailable | E-9902 | Redis error |

#### 多租戶隔離

- 系統根據 JWT token 中的 userId 和 tenantId 自動過濾
- 買家只能看到屬於自己 tenant 的購物車內容

---

### 4.3 US-M04-003: 更新數量 / Update Item Quantity

#### Endpoint

| 欄位 | 內容 |
|------|------|
| **HTTP Method** | `PUT` |
| **Path** | `/api/v2/cart/items/{cartItemKey}` |
| **Description** | 買家可調整購物車中的商品數量 |
| **認證** | Required (BUYER role) |

**Path Parameters:**
| 參數 | 類型 | 說明 |
|------|------|------|
| `cartItemKey` | String | 購物車項目唯一識別鍵（URL-encoded） |

**cartItemKey 格式:**
- PRODUCT: `{listingId}:{skuId}` 或 `{listingId}`（無 SKU）
- ROOM: `{listingId}:{skuId}:{startDate}:{endDate}` 或 `{listingId}::{startDate}:{endDate}`（無 SKU）

**範例:**
- `550e8400-e29b-41d4-a716-446655440000` (PRODUCT, 無 SKU)
- `550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001` (PRODUCT, 有 SKU)
- `550e8400-e29b-41d4-a716-446655440000::2026-06-01:2026-06-03` (ROOM, 無 SKU)
- `550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03` (ROOM, 有 SKU)

#### Request

**Headers:**
| Header | 值 | 必填 |
|--------|-----|------|
| `Authorization` | `Bearer {access_token}` | **必填** |
| `Content-Type` | `application/json` | **必填** |

**Request Body:**
```json
{
  "quantity": 3
}
```

#### Response

**200 OK:**
```json
{
  "code": 200,
  "message": "Item quantity updated",
  "data": {
    "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "title": "精緻雙人房",
    "coverImageUrl": "https://example.com/images/room-001.jpg",
    "skuId": "550e8400-e29b-41d4-a716-446655440001",
    "skuCode": "ROOM-STD-001",
    "specName": "標準入住",
    "quantity": 3,
    "unitPrice": 1500.00,
    "subtotal": 4500.00,
    "listingType": "ROOM",
    "addedAt": "2026-04-28T10:30:00Z",
    "startDate": "2026-06-01",
    "endDate": "2026-06-03"
  }
}
```

#### Error Responses

| HTTP Status | Error Code | 說明 |
|-------------|------------|------|
| 400 Bad Request | E-5006 | Invalid quantity (數量不在 1-999 範圍) |
| 401 Unauthorized | E-1000 | Authentication required |
| 403 Forbidden | E-1007 | Insufficient permissions (非 BUYER 角色) |
| 404 Not Found | E-5005 | Cart item not found |
| 500 Internal Server Error | E-9900 | Internal server error |
| 503 Service Unavailable | E-9902 | Redis error |

#### Business Rules

1. **數量為 0**: 數量設為 0 時，應該移除該項目（而非更新為 0）
2. **最小/最大**: quantity 必須在 1-999 範圍內

---

### 4.4 US-M04-004: 移除商品 / Remove Item from Cart

#### Endpoint

| 欄位 | 內容 |
|------|------|
| **HTTP Method** | `DELETE` |
| **Path** | `/api/v2/cart/items/{cartItemKey}` |
| **Description** | 買家可移除購物車中的商品 |
| **認證** | Required (BUYER role) |

**Path Parameters:**
| 參數 | 類型 | 說明 |
|------|------|------|
| `cartItemKey` | String | 購物車項目唯一識別鍵（URL-encoded） |

#### Request

**Headers:**
| Header | 值 | 必填 |
|--------|-----|------|
| `Authorization` | `Bearer {access_token}` | **必填** |

#### Response

**200 OK:**
```json
{
  "code": 200,
  "message": "Item removed from cart",
  "data": null
}
```

#### Error Responses

| HTTP Status | Error Code | 說明 |
|-------------|------------|------|
| 401 Unauthorized | E-1000 | Authentication required |
| 403 Forbidden | E-1007 | Insufficient permissions (非 BUYER 角色) |
| 404 Not Found | E-5005 | Cart item not found |
| 500 Internal Server Error | E-9900 | Internal server error |
| 503 Service Unavailable | E-9902 | Redis error |

#### Business Rules

1. **多租戶隔離**: 只能移除自己 tenant 下的購物車項目
2. **項目不存在**: 移除不存在的項目時回傳 404

---

## 5. Error Codes 彙整 / Error Codes Reference

### 5.1 認證/授權錯誤 (E-1000s)

| Error Code | HTTP Status | 說明 |
|-------------|-------------|------|
| E-1000 | 401 | Authentication required |
| E-1001 | 401 | Invalid credentials |
| E-1002 | 401 | Token expired |
| E-1003 | 401 | Invalid token |
| E-1007 | 403 | Insufficient permissions |

### 5.2 購物車相關錯誤 (E-5000s)

| Error Code | HTTP Status | 說明 |
|-------------|-------------|------|
| E-5004 | 400 | Cart is empty |
| E-5005 | 404 | Cart item not found |
| E-5006 | 400 | Invalid quantity (must be 1-999) |

### 5.3 房源/預訂相關錯誤 (E-3000s, E-4000s)

| Error Code | HTTP Status | 說明 |
|-------------|-------------|------|
| E-3000 | 404 | Listing not found |
| E-4003 | 400 | Invalid date range |
| E-4004 | 400 | Check-out must be after check-in |

### 5.4 系統錯誤 (E-9900s)

| Error Code | HTTP Status | 說明 |
|-------------|-------------|------|
| E-9900 | 500 | Internal server error |
| E-9902 | 503 | Redis error |
| E-9905 | 503 | Service unavailable |

---

## 6. 多租戶隔離說明 / Multi-Tenant Isolation

### 6.1 隔離機制

購物車資料使用 `userId + tenantId` 複合鍵隔離：

```
Redis Key Format: cart:{userId}:{tenantId}
Example: cart:550e8400-e29b-41d4-a716-446655440099:550e8400-e29b-41d4-a716-446655440001
```

### 6.2 隔離規則

1. **用戶隔離**: 買家只能操作自己的購物車
2. **租戶隔離**: 不同租戶的購物車完全隔離
3. **資料過濾**: 所有 API 自動過濾，確保用戶無法存取他人資料

---

## 7. 追蹤性鏈 / Traceability Chain

### 7.1 文件關聯

```
SPRINT_04_PLAN.md
  └── US-M04-001 ~ US-M04-004
       └──本文檔 (API_M04_Cart.md)
            └── TC_M04_Cart.md (測試案例)
```

### 7.2 User Story 對應

| User Story | API Endpoint |
|------------|--------------|
| US-M04-001 | POST /api/v2/cart/items |
| US-M04-002 | GET /api/v2/cart |
| US-M04-003 | PUT /api/v2/cart/items/{cartItemKey} |
| US-M04-004 | DELETE /api/v2/cart/items/{cartItemKey} |

---

## 8. 參考實作 / Reference Implementation

| 檔案 | 路徑 | 說明 |
|------|------|------|
| RedisCartService | `backend/src/main/java/com/nextkey/ecommerce/core/cart/RedisCartService.java` | 購物車服務實作 |
| CartDto | `backend/src/main/java/com/nextkey/ecommerce/api/dto/CartDto.java` | DTO 定義 |
| ErrorCode | `backend/src/main/java/com/nextkey/ecommerce/shared/exception/ErrorCode.java` | 錯誤碼定義 |
| GlobalExceptionHandler | `backend/src/main/java/com/nextkey/ecommerce/api/dto/GlobalExceptionHandler.java` | 全域異常處理 |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-28

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-28 | 初始版本（M04 購物車 API 規格） | SD (Marcus) |