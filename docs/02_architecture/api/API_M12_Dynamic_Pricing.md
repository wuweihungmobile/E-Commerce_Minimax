# API 規格 - M12 動態定價引擎 / Dynamic Pricing Engine

> **API ID**: API-M12 (API-401 ~ API-406)
> **版本**: v1.0
> **最後更新日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
> **Phase**: **Phase 1 (Must Have)**

---

## 📋 API 總覽

| API ID | 端點 | 方法 | 說明 | 角色 |
|--------|------|------|------|------|
| API-M12-001 | `/api/v2/listings/:id/price` | GET | 取得動態價格 | Guest+ |
| API-M12-002 | `/api/v2/dashboard/pricing/rules` | GET | 定價規則列表 | StoreOwner+ |
| API-M12-003 | `/api/v2/dashboard/pricing/rules` | POST | 建立定價規則 | StoreOwner+ |
| API-M12-004 | `/api/v2/dashboard/pricing/rules/:id` | PUT | 更新定價規則 | StoreOwner+ |
| API-M12-005 | `/api/v2/dashboard/pricing/rules/:id` | DELETE | 刪除定價規則 | StoreOwner+ |
| API-M12-006 | `/api/v2/dashboard/pricing/rules/:id/override` | POST | 手動覆蓋價格 | StoreOwner+ |

---

## 1. API-M12-001: 取得動態價格

- **端點**: `GET /api/v2/listings/:id/price`
- **描述**: 計算並取得房源的動態價格（適用于日期範圍）
- **對應需求**: [US-M12-001](../01_requirements/E-Commerce_FRD_v1.0.md#us-m12-001)
- **角色**: Guest+

### 1.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Listing ID |

**Query Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| checkInDate | date | 是 | 入住日期 (YYYY-MM-DD) |
| checkOutDate | date | 是 | 退房日期 (YYYY-MM-DD) |
| guests | integer | 否 | 入住人數（用於長住折扣計算）|

### 1.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "basePrice": 2500,
    "currency": "TWD",
    "checkInDate": "2026-05-01",
    "checkOutDate": "2026-05-03",
    "guests": 2,
    "pricing": {
      "breakdown": [
        {
          "date": "2026-05-01",
          "dayOfWeek": "FRIDAY",
          "basePrice": 2500,
          "adjustedPrice": 3250,
          "priceType": "WEEKEND",
          "appliedRules": [
            {
              "ruleId": "rule-weekend",
              "ruleName": "週末加成",
              "adjustment": {
                "type": "MULTIPLIER",
                "value": 1.3
              }
            }
          ]
        },
        {
          "date": "2026-05-02",
          "dayOfWeek": "SATURDAY",
          "basePrice": 2500,
          "adjustedPrice": 3250,
          "priceType": "WEEKEND",
          "appliedRules": [
            {
              "ruleId": "rule-weekend",
              "ruleName": "週末加成",
              "adjustment": {
                "type": "MULTIPLIER",
                "value": 1.3
              }
            }
          ]
        }
      ],
      "subtotal": 6500,
      "discounts": [
        {
          "ruleId": "rule-early-bird",
          "ruleName": "早鳥折扣 7 天前",
          "discountAmount": -325
        }
      ],
      "totalPrice": 6175,
      "averageNightlyPrice": 3087.5
    }
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

### 1.3 動態定價規則類型

| 規則類型 | 說明 | 範例 |
|---------|------|------|
| WEEKDAY | 平日價格 | basePrice × 1.0 |
| WEEKEND | 週末加成 | basePrice × 1.3 |
| HOLIDAY | 節日加成 | basePrice × 1.5 |
| PEAK_SEASON | 旺季加成 | basePrice × 2.0 |
| EARLY_BIRD | 早鳥折扣 | -10%（入住前 7 天）|
| LAST_MINUTE | 最後一刻 | -15%（入住前 3 天）|
| LONG_STAY | 長住折扣 | 7天-10%，30天-20% |
| OVERRIDE | 手動覆蓋 | 直接指定價格 |

---

## 2. API-M12-002: 定價規則列表

- **端點**: `GET /api/v2/dashboard/pricing/rules`
- **描述**: 取得当前店铺的定價規則列表
- **對應需求**: [US-M12-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m12-002)
- **角色**: StoreOwner+

### 2.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |

**Query Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| listingId | UUID | 否 | 篩選特定房源的規則 |

### 2.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "rules": [
      {
        "ruleId": "rule-001",
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "listingTitle": "墾丁海景民宿 - 豪華雙人房",
        "ruleType": "WEEKEND",
        "ruleName": "週末加成",
        "isActive": true,
        "adjustment": {
          "type": "MULTIPLIER",
          "value": 1.3
        },
        "priority": 10,
        "conditions": {
          "daysOfWeek": ["FRIDAY", "SATURDAY"]
        },
        "createdAt": "2026-01-01T00:00:00.000Z",
        "updatedAt": "2026-01-01T00:00:00.000Z"
      },
      {
        "ruleId": "rule-002",
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "listingTitle": "墾丁海景民宿 - 豪華雙人房",
        "ruleType": "EARLY_BIRD",
        "ruleName": "早鳥折扣 7 天前",
        "isActive": true,
        "adjustment": {
          "type": "PERCENTAGE",
          "value": -0.10
        },
        "priority": 20,
        "conditions": {
          "minDaysBeforeCheckIn": 7
        },
        "createdAt": "2026-01-01T00:00:00.000Z",
        "updatedAt": "2026-01-01T00:00:00.000Z"
      },
      {
        "ruleId": "rule-003",
        "listingId": null,
        "listingTitle": "全部房源（預設規則）",
        "ruleType": "LONG_STAY",
        "ruleName": "長住折扣 7 天",
        "isActive": true,
        "adjustment": {
          "type": "PERCENTAGE",
          "value": -0.10
        },
        "priority": 30,
        "conditions": {
          "minConsecutiveDays": 7
        },
        "createdAt": "2026-01-01T00:00:00.000Z",
        "updatedAt": "2026-01-01T00:00:00.000Z"
      }
    ]
  }
}
```

---

## 3. API-M12-003: 建立定價規則

- **端點**: `POST /api/v2/dashboard/pricing/rules`
- **描述**: 為房源建立新的定價規則
- **對應需求**: [US-M12-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m12-002)
- **角色**: StoreOwner+

### 3.1 Request

**Headers**:

| 參數 | 必填 | 說明 |
|------|------|------|
| Authorization | 是 | Bearer {access_token} |
| Content-Type | 是 | application/json |
| X-Tenant-ID | 條件式 | 多租戶用戶必填 |

**Request Body**:
```json
{
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "ruleType": "WEEKEND",
  "ruleName": "週末加成",
  "adjustment": {
    "type": "MULTIPLIER",
    "value": 1.3
  },
  "priority": 10,
  "conditions": {
    "daysOfWeek": ["FRIDAY", "SATURDAY", "SUNDAY"]
  },
  "isActive": true
}
```

**欄位說明**:

| 欄位 | 類型 | 必填 | 說明 |
|------|------|---------|------|
| listingId | UUID | 否 | 綁定房源（null 表示全部房源）|
| ruleType | string | 是 | 規則類型 |
| ruleName | string | 是 | 規則名稱 |
| adjustment.type | string | 是 | `MULTIPLIER`（倍率）或 `PERCENTAGE`（百分比）或 `FIXED`（固定值）|
| adjustment.value | number | 是 | 調整值 |
| priority | integer | 否 | 優先順序（數字越大越優先），預設 0 |
| conditions | object | 否 | 觸發條件 |
| isActive | boolean | 否 | 是否啟用，預設 true |

### 3.2 Response

**201 Created**:
```json
{
  "code": 201,
  "message": "Pricing rule created successfully",
  "data": {
    "ruleId": "rule-004",
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "ruleType": "WEEKEND",
    "ruleName": "週末加成",
    "isActive": true,
    "adjustment": {
      "type": "MULTIPLIER",
      "value": 1.3
    },
    "priority": 10,
    "conditions": {
      "daysOfWeek": ["FRIDAY", "SATURDAY", "SUNDAY"]
    },
    "createdAt": "2026-04-09T10:30:00.000Z"
  },
  "timestamp": "2026-04-09T10:30:00.000Z",
  "requestId": "..."
}
```

### 3.3 規則類型與調整值範例

| 規則類型 | adjustment.type | adjustment.value | 說明 |
|---------|----------------|-------------------|------|
| 週末加成 | MULTIPLIER | 1.3 | 週末價格 × 1.3 |
| 旺季加成 | MULTIPLIER | 2.0 | 旺季價格 × 2.0 |
| 早鳥折扣 | PERCENTAGE | -0.10 | 折扣 10% |
| 長住折扣 | PERCENTAGE | -0.15 | 折扣 15% |
| 指定價格 | FIXED | 3000 | 直接設為 $3000 |

---

## 4. API-M12-004: 更新定價規則

- **端點**: `PUT /api/v2/dashboard/pricing/rules/:id`
- **描述**: 更新現有定價規則
- **對應需求**: [US-M12-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m12-002)
- **角色**: StoreOwner+

### 4.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Rule ID |

**Request Body**:
```json
{
  "ruleName": "週末加成（調高）",
  "adjustment": {
    "type": "MULTIPLIER",
    "value": 1.5
  },
  "priority": 15,
  "isActive": true
}
```

### 4.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Pricing rule updated successfully",
  "data": {
    "ruleId": "rule-004",
    "ruleName": "週末加成（調高）",
    "adjustment": {
      "type": "MULTIPLIER",
      "value": 1.5
    },
    "priority": 15,
    "isActive": true,
    "updatedAt": "2026-04-09T11:00:00.000Z"
  },
  "timestamp": "2026-04-09T11:00:00.000Z",
  "requestId": "..."
}
```

---

## 5. API-M12-005: 刪除定價規則

- **端點**: `DELETE /api/v2/dashboard/pricing/rules/:id`
- **描述**: 刪除定價規則
- **對應需求**: [US-M12-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m12-002)
- **角色**: StoreOwner+

### 5.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Rule ID |

### 5.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Pricing rule deleted successfully",
  "data": null,
  "timestamp": "2026-04-09T11:00:00.000Z",
  "requestId": "..."
}
```

---

## 6. API-M12-006: 手動覆蓋價格

- **端點**: `POST /api/v2/dashboard/pricing/rules/:id/override`
- **描述**: 為特定日期設定手動覆蓋價格（優先於所有規則）
- **對應需求**: [US-M12-002](../01_requirements/E-Commerce_FRD_v1.0.md#us-m12-002)
- **角色**: StoreOwner+

### 6.1 Request

**Path Parameters**:

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| id | UUID | 是 | Listing ID（不是 Rule ID）|

**Request Body**:
```json
{
  "overrides": [
    {
      "date": "2026-05-01",
      "price": 5000,
      "reason": "勞動節假期"
    },
    {
      "date": "2026-05-02",
      "price": 5500,
      "reason": "勞動節假期"
    }
  ]
}
```

### 6.2 Response

**200 OK**:
```json
{
  "code": 200,
  "message": "Price override created successfully",
  "data": {
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "overrides": [
      {
        "date": "2026-05-01",
        "price": 5000,
        "reason": "勞動節假期",
        "createdAt": "2026-04-09T11:00:00.000Z"
      },
      {
        "date": "2026-05-02",
        "price": 5500,
        "reason": "勞動節假期",
        "createdAt": "2026-04-09T11:00:00.000Z"
      }
    ]
  },
  "timestamp": "2026-04-09T11:00:00.000Z",
  "requestId": "..."
}
```

### 6.3 手動覆蓋優先級

> **重要**: 手動覆蓋價格 > 所有動態定價規則

當某日期存在手動覆蓋時，該日期的價格將直接使用覆蓋值，不會套用任何動態規則。

### 6.4 取消覆蓋

要取消覆蓋，請使用 `DELETE /api/v2/dashboard/pricing/overrides/:date`

---

## 📝 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 處理建議 |
|--------|-----------|------|----------|
| E-4001 | 400 | 驗證失敗 | 檢查必填欄位 |
| E-4041 | 404 | Listing 或 Rule 不存在 | 確認 ID |
| E-2020 | 403 | 動態定價功能未啟用 | 聯繫平台管理員 |
| E-4031 | 403 | 無權操作此房源的規則 | 確認所有權 |
| E-1001 | 401 | JWT 無效 | 重新登入 |
| E-2003 | 403 | 租戶上下文不明 | 提供 X-Tenant-ID |

---

## 📝 價格計算流程

```
1. 取得 basePrice（listings.base_price）
        │
        ▼
2. 檢查是否有手動覆蓋（Override）
        │ 有 → 使用覆蓋價格，直接輸出
        │ 無 → 繼續
        ▼
3. 套用規則（按 priority 從高到低）
        │
        ├── WEEKEND → basePrice × 1.3
        ├── HOLIDAY → basePrice × 1.5
        ├── PEAK_SEASON → basePrice × 2.0
        ├── EARLY_BIRD → subtotal - 10%
        ├── LAST_MINUTE → subtotal - 15%
        └── LONG_STAY → subtotal - X%
        │
        ▼
4. 套用長住折扣（如果符合條件）
        │
        ▼
5. 輸出最終價格
```

---

**文件結束**
