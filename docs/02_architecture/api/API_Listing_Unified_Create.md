# API_Listing_Unified_Create

> **API 編號**: API-DEF-001
> **模組**: DEF-001 (環境建設)
> **版本**: v1.0
> **建立日期**: 2026-04-30
> **基於**: Sprint 6 Plan Section 5.1 T-DEF-001-01

---

## 1. API 概述

| 欄位 | 內容 |
|------|------|
| **端點** | `POST /v2/dashboard/listings` |
| **功能** | 統一 Listing 建立端點 |
| **用途** | 支援 ROOM/PRODUCT 統一建立 |
| **所需權限** | STORE_OWNER |

---

## 2. Request 規格

### 2.1 Request DTO

**類別**: `CreateListingRequest`

```json
{
  "listingType": "ROOM | PRODUCT",
  "name": "string (必填, 1-200 chars)",
  "description": "string (可選, 0-2000 chars)",
  "basePrice": "number (必填, > 0)",
  "currency": "string (可選, 預設 TWD)",
  "categoryId": "string (必填)",
  "images": ["string array (可選)"],
  "roomSpecificFields": {
    "roomType": "STANDARD | DELUXE | SUITE (僅 ROOM)",
    "maxOccupancy": "number (僅 ROOM, > 0)",
    "amenities": ["string array (僅 ROOM)"]
  },
  "productSpecificFields": {
    "sku": "string (僅 PRODUCT)",
    "inventory": "number (僅 PRODUCT, >= 0)"
  }
}
```

### 2.2 Request 範例

**ROOM 類型**:
```json
{
  "listingType": "ROOM",
  "name": "豪華雙人房",
  "description": "面海景觀大床房",
  "basePrice": 3500,
  "currency": "TWD",
  "categoryId": "cat_room_001",
  "images": ["https://example.com/room1.jpg"],
  "roomSpecificFields": {
    "roomType": "DELUXE",
    "maxOccupancy": 2,
    "amenities": ["WiFi", "Mini Bar", "Sea View"]
  }
}
```

**PRODUCT 類型**:
```json
{
  "listingType": "PRODUCT",
  "name": "精選咖啡豆",
  "description": "來自衣索比亞的精品咖啡豆",
  "basePrice": 450,
  "currency": "TWD",
  "categoryId": "cat_prod_001",
  "images": ["https://example.com/coffee.jpg"],
  "productSpecificFields": {
    "sku": "COFFEE-ETH-450G",
    "inventory": 100
  }
}
```

---

## 3. Response 規格

### 3.1 Success Response

**HTTP Status**: `201 Created`

```json
{
  "success": true,
  "data": {
    "id": "listing_xxx",
    "listingType": "ROOM | PRODUCT",
    "name": "string",
    "status": "ACTIVE",
    "createdAt": "2026-04-30T10:00:00Z"
  },
  "message": "Listing created successfully"
}
```

### 3.2 Error Response

**HTTP Status**: `4xx / 5xx`

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "錯誤訊息"
  }
}
```

---

## 4. 錯誤碼

| 錯誤碼 | HTTP Status | 說明 |
|--------|-------------|------|
| E_1001 | 400 | 參數驗證失敗 |
| E_2004 | 403 | Feature Toggle 未啟用 (BOOKING_ENABLED / RETAIL_ENABLED) |
| E_2001 | 404 | Category 不存在 |
| E_5000 | 500 | 內部伺服器錯誤 |

---

## 5. 業務規則

### 5.1 Feature Toggle 檢查

| Listing Type | Feature Toggle Key | 禁用時行為 |
|--------------|-------------------|------------|
| ROOM | `BOOKING_ENABLED` | 回傳 403 E_2004 |
| PRODUCT | `RETAIL_ENABLED` | 回傳 403 E_2004 |

### 5.2 欄位驗證

| 欄位 | 規則 |
|------|------|
| name | 必填, 1-200 字元 |
| basePrice | 必填, > 0 |
| categoryId | 必填, 需存在 |
| roomType | ROOM 必填 |
| maxOccupancy | ROOM 必填, > 0 |
| sku | PRODUCT 唯一性檢查 |
| inventory | PRODUCT, >= 0 |

---

## 6. 對應測試案例

| TC ID | 描述 | 預期結果 |
|-------|------|----------|
| API-DEF-001-01 | 建立 ROOM Listing 成功 | 201 + Listing data |
| API-DEF-001-02 | 建立 PRODUCT Listing 成功 | 201 + Listing data |
| API-DEF-001-03 | BOOKING_ENABLED=false 時建立 ROOM | 403 E_2004 |
| API-DEF-001-04 | RETAIL_ENABLED=false 時建立 PRODUCT | 403 E_2004 |
| API-DEF-001-05 | 缺少必填欄位 | 400 E_1001 |
| API-DEF-001-06 | basePrice <= 0 | 400 E_1001 |

---

## 7. 誰需要這個 API

| 角色 | 使用場景 |
|------|----------|
| StoreOwner | 建立房源/商品 |
| Admin | 測試環境驗證 |

---

## 8. 關聯文件

- [API_M02_Listing_Center.md](API_M02_Listing_Center.md) - Listing API 參考
- [SPRINT_06_PLAN.md](../04_planning/SPRINT_06_PLAN.md) - Sprint 6 計劃
- [TC_M02_Room.md](../03_testing/TC_M02_Room.md) - M02 測試案例
