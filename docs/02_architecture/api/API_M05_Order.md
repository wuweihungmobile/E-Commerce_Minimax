# API 規格 - M05 訂單履約系統 / Order Fulfillment

> **API ID**: API-M05 (API-301 ~ API-306)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M05-001 | `/api/v2/orders` | POST | 建立訂單 | Buyer+ |
| API-M05-002 | `/api/v2/orders` | GET | 買家訂單列表 | Buyer+ |
| API-M05-003 | `/api/v2/orders/:id` | GET | 訂單詳情 | Buyer+ |
| API-M05-004 | `/api/v2/orders/:id/cancel` | POST | 取消訂單 | Buyer+ |
| API-M05-005 | `/api/v2/dashboard/orders` | GET | 賣家訂單列表 | Seller+ |
| API-M05-006 | `/api/v2/dashboard/orders/:id/status` | PUT | 更新訂單狀態 | Seller+ |

---

## 1. API-M05-001: 建立訂單

- **端點**: `POST /api/v2/orders`
- **描述**: 買家建立訂單（Phase 1 使用 Payment Mock）
- **對應需求**: [BR-M05-001](../01_requirements/E-Commerce_FRD_v1.0.md#br-m05-001)
- **角色**: Buyer+

### 1.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| Content-Type | 是 | application/json |
| X-Idempotency-Key | 建議 | UUID，用於防重複 |

**Request Body**:
```json
{
  "items": [
    {
      "listingId": "550e8400-e29b-41d4-a716-446655440000",
      "skuId": null,
      "quantity": 2,
      "unitPrice": 42900
    }
  ],
  "paymentMethod": "MOCK",
  "notes": "請於三天前確認訂單"
}
```

**欄位說明**:

| 欄位 | 類型 | 大小限制 | 必填 | 說明 |
|------|------|---------|------|------|
| items | array | 1-50 項 | 是 | 訂購項目 |
| items[].listingId | UUID | — | 是 | Listing ID |
| items[].skuId | UUID | — | 否 | SKU ID（零售商品有 SKU）|
| items[].quantity | integer | 1-999 | 是 | 數量 |
| items[].unitPrice | number | > 0 | 是 | 單價（已含動態定價）|
| paymentMethod | string | — | 是 | 固定為 `MOCK`（Phase 1）|
| notes | string | 0-500 | 否 | 訂單備註 |

### 1.2 Response

**201 Created**:
```json
{
  "code": 201,
  "message": "Order created successfully",
  "data": {
    "orderId": "ord-20260409-001",
    "orderNumber": "ORD-20260409-001",
    "status": "CREATED",
    "statusDescription": "訂單已建立（已付款）",
    "items": [
      {
        "itemId": "item-uuid-1",
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "title": "iPhone 15 Pro",
        "skuId": null,
        "quantity": 2,
        "unitPrice": 42900,
        "subtotal": 85800
      }
    ],
    "totalAmount": 85800,
    "currency": "TWD",
    "payment": {
      "paymentId": "mock_550e8400-e29b-41d4-a716-446655440000",
      "status": "SUCCESS",
      "method": "MOCK",
      "mock": true
    },
    "createdAt": "2026-04-09T10:30:00.000Z"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

### 1.3 Phase 1 狀態流轉

```
CREATED (= PAID)  ──→ SHIPPING ──→ DELIVERED ──→ COMPLETED
       │                  │              │
       └──→ CANCELLED ←───┘              └──→ REFUNDING ──→ REFUNDED
```

### 1.4 錯誤碼

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-4001 | 400 | 驗證失敗 | 檢查必填欄位 |
| E-4041 | 404 | Listing 不存在 | 確認 listingId |
| E-4021 | 422 | 庫存不足 | 減少數量或等待補貨 |
| E-4022 | 409 | 併發衝突 | 使用 idempotency key 重試 |
| E-1001 | 401 | JWT 無效 | 重新登入 |

**庫存不足錯誤**:
```json
{
  "code": 422,
  "message": "Insufficient inventory",
  "errors": [
    {
      "field": "items[0]",
      "message": "商品庫存不足（可售：5，欲購買：10）",
      "code": "INSUFFICIENT_INVENTORY",
      "details": {
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "available": 5,
        "requested": 10
      }
    }
  ],
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

---

## 2. API-M05-002: 買家訂單列表

- **端點**: `GET /api/v2/orders`
- **描述**: 取得當前買家的訂單列表
- **對應需求**: [US-M05-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m05-001)
- **角色**: Buyer+

### 2.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |

**Query Parameters**:

| 參數 | 類型 | 必填 | 預設值 | 說明 |
|------|------|------|--------|------|
| status | string | 否 | — | 篩選狀態 |
| page | integer | 否 | 1 | 頁碼 |
| limit | integer | 否 | 20 | 每頁筆數 |
| sort | string | 否 | createdAt:desc | 排序 |

### 2.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "items": [
      {
        "orderId": "ord-20260409-001",
        "orderNumber": "ORD-20260409-001",
        "status": "CREATED",
        "totalAmount": 85800,
        "currency": "TWD",
        "itemCount": 1,
        "createdAt": "2026-04-09T10:30:00.000Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "totalItems": 5,
      "totalPages": 1,
      "hasNextPage": false,
      "hasPreviousPage": false
    }
  }
}
```

---

## 3. API-M05-003: 訂單詳情

- **端點**: `GET /api/v2/orders/:id`
- **描述**: 取得訂單詳細資訊
- **對應需求**: [US-M05-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m05-001)
- **角色**: Buyer+

### 3.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | string | 是 | Order ID（如 `ord-20260409-001`）|

### 3.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "orderId": "ord-20260409-001",
    "orderNumber": "ORD-20260409-001",
    "status": "CREATED",
    "statusDescription": "訂單已建立（已付款）",
    "items": [
      {
        "itemId": "item-uuid-1",
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "listingType": "PRODUCT",
        "title": "iPhone 15 Pro",
        "coverImageUrl": "https://example.com/images/iphone15.jpg",
        "skuId": null,
        "skuCode": null,
        "quantity": 2,
        "unitPrice": 42900,
        "subtotal": 85800
      }
    ],
    "totalAmount": 85800,
    "currency": "TWD",
    "payment": {
      "paymentId": "mock_550e8400-e29b-41d4-a716-446655440000",
      "status": "SUCCESS",
      "method": "MOCK",
      "paidAt": "2026-04-09T10:30:00.000Z"
    },
    "tenant": {
      "id": "tenant-uuid",
      "name": "Apple Store"
    },
    "stateLogs": [
      {
        "sequence": 1,
        "fromStatus": null,
        "toStatus": "CREATED",
        "reason": "Order created via Payment Mock",
        "operatorId": "system",
        "createdAt": "2026-04-09T10:30:00.000Z"
      }
    ],
    "createdAt": "2026-04-09T10:30:00.000Z",
    "updatedAt": "2026-04-09T10:30:00.000Z"
  }
}
```

### 3.3 訂單狀態說明

| 狀態 | 說明 | 可執行操作 |
|------|------|-----------|
| CREATED | 訂單已建立（已付款）| cancel |
| SHIPPING | 已出貨 | delivered |
| DELIVERED | 已送達 | complete, refund |
| COMPLETED | 已完成 | — |
| CANCELLED | 已取消 | — |
| REFUNDING | 退款中 | — |
| REFUNDED | 已退款 | — |

---

## 4. API-M05-004: 取消訂單

- **端點**: `POST /api/v2/orders/:id/cancel`
- **描述**: 買家取消訂單（釋放庫存）
- **對應需求**: [BR-M05-003](../01_requirements/E-Commerce_FRD_v1.0.md#br-m05-003)
- **角色**: Buyer+

### 4.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | string | 是 | Order ID |

**Request Body**:
```json
{
  "reason": "改變心意"
}
```

### 4.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Order cancelled successfully",
  "data": {
    "orderId": "ord-20260409-001",
    "orderNumber": "ORD-20260409-001",
    "status": "CANCELLED",
    "cancelledAt": "2026-04-09T11:00:00.000Z",
    "inventoryReleased": true
  }
}
```

### 4.3 取消限制

| 訂單狀態 | 是否可取消 | 說明 |
|----------|-----------|------|
| CREATED | ✅ | 尚未出貨，可取消 |
| SHIPPING | ❌ | 已出貨，需先收回 |
| DELIVERED | ❌ | 已送達，需走退款流程 |
| COMPLETED | ❌ | 已完成，不可取消 |
| CANCELLED | ❌ | 已取消 |

**錯誤範例**:
```json
{
  "code": 400,
  "message": "Cannot cancel order in SHIPPING status",
  "errors": [],
  "timestamp": "2026-04-09T11:00:00.000Z",
  "requestId": "..."
}
```

---

## 5. API-M05-005: 賣家訂單列表

- **端點**: `GET /api/v2/dashboard/orders`
- **描述**: 賣家取得店鋪的訂單列表
- **對應需求**: [US-M05-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m05-001)
- **角色**: Seller+

### 5.1 Request

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

### 5.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "items": [
      {
        "orderId": "ord-20260409-001",
        "orderNumber": "ORD-20260409-001",
        "buyer": {
          "id": "user-uuid",
          "displayName": "John Doe"
        },
        "status": "CREATED",
        "totalAmount": 85800,
        "itemCount": 1,
        "createdAt": "2026-04-09T10:30:00.000Z"
      }
    ],
    "pagination": {...}
  }
}
```

---

## 6. API-M05-006: 更新訂單狀態

- **端點**: `PUT /api/v2/dashboard/orders/:id/status`
- **描述**: 賣家更新訂單狀態（出貨/送達）
- **對應需求**: [BR-M05-001](../01_requirements/E-Commerce_FRD_v1.0.md#br-m05-001)
- **角色**: Seller+

### 6.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | string | 是 | Order ID |

**Request Body**:
```json
{
  "status": "SHIPPING",
  "trackingNumber": "包裹追蹤號碼（選填）"
}
```

### 6.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Order status updated successfully",
  "data": {
    "orderId": "ord-20260409-001",
    "orderNumber": "ORD-20260409-001",
    "previousStatus": "CREATED",
    "currentStatus": "SHIPPING",
    "updatedAt": "2026-04-09T12:00:00.000Z"
  }
}
```

### 6.3 狀態轉換權限

| 目標狀態 | 觸發角色 | 說明 |
|----------|---------|------|
| SHIPPING | Seller | 賣家出貨 |
| DELIVERED | Seller/物流 | 買家或物流確認收貨 |
| COMPLETED | Buyer | 買家確認完成 |
| REFUNDING | Buyer | 買家申請退款 |
| CANCELLED | Admin | 管理員取消 |

---

## 📝 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-4001 | 400 | 驗證失敗 | 檢查必填欄位 |
| E-4021 | 422 | 庫存不足 | 減少數量或等待補貨 |
| E-4022 | 409 | 併發衝突（樂觀鎖失敗）| 重試 |
| E-4023 | 400 | 狀態轉換不允許 | 確認目前狀態 |
| E-4024 | 403 | 非訂單擁有者 | 無權操作此訂單 |
| E-4041 | 404 | 訂單不存在 | 確認 orderId |
| E-1001 | 401 | JWT 無效 | 重新登入 |
| E-2003 | 403 | 租戶上下文不明 | 提供 X-Tenant-ID |

---

**文件結束**
