# API 規格文件 - M06 預訂系統 / Booking System

> **文件編號**: API-M06
> **版本**: v1.0
> **建立日期**: 2026-04-28
> **基於**: Sprint 4 Plan + BookingService + RoomCalendarService
> **文件狀態**: ✅ 已完成

---

## 1. 概述 / Overview

### 1.1 API 目的

M06 預訂系統提供房源預訂的完整 REST API，支援建立、查詢、更新、取消預訂，以及日期衝突檢查。

### 1.2 Base URL

```
/api/v2/bookings
```

### 1.3 認證需求

| 端點 | 需要角色 | Authority |
|------|----------|-----------|
| GET /availability | BUYER | `booking:read` |
| POST / | BUYER | `booking:create` |
| GET / | BUYER | `booking:read` |
| GET /:id | BUYER | `booking:read` |
| PUT /:id | BUYER | `booking:update` |
| POST /:id/cancel | BUYER | `booking:cancel` |

### 1.4 多租戶隔離

所有端點透過 `TenantContext` 自動隔離，買家只能看到自己 tenant 下的預訂。

---

## 2. 端點規格 / Endpoint Specifications

### 2.1 檢查日期可用性 / Check Availability

#### Request

```
GET /api/v2/bookings/availability
Content-Type: application/json
Authorization: Bearer <token>
```

##### Path Parameters

無

##### Query Parameters

無

##### Request Body

```json
{
  "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
  "checkInDate": "2026-06-01",
  "checkOutDate": "2026-06-03"
}
```

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| roomListingId | UUID | ✅ | 房源 ID |
| checkInDate | date | ✅ | 入住日期 (yyyy-MM-dd) |
| checkOutDate | date | ✅ | 退房日期 (yyyy-MM-dd) |

#### Response

**200 OK** - 可用性檢查成功

```json
{
  "code": 200,
  "message": "Availability checked",
  "data": {
    "available": true,
    "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
    "checkInDate": "2026-06-01",
    "checkOutDate": "2026-06-03",
    "nightsCount": 2,
    "totalPrice": 3000.00,
    "currency": "TWD",
    "calendarDetails": [
      {
        "date": "2026-06-01",
        "status": "AVAILABLE",
        "price": 1500.00,
        "bookingId": null
      },
      {
        "date": "2026-06-02",
        "status": "AVAILABLE",
        "price": 1500.00,
        "bookingId": null
      }
    ],
    "unavailableReason": null
  }
}
```

**200 OK** - 日期不可用

```json
{
  "code": 200,
  "message": "Availability checked",
  "data": {
    "available": false,
    "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
    "checkInDate": "2026-06-01",
    "checkOutDate": "2026-06-03",
    "nightsCount": 2,
    "totalPrice": null,
    "currency": "TWD",
    "calendarDetails": null,
    "unavailableReason": "Date 2026-06-01 is BOOKED"
  }
}
```

##### Error Responses

| HTTP Status | Error Code | 描述 |
|-------------|------------|------|
| 400 | E-4003 | Invalid date range |
| 400 | E-4004 | Check-out must be after check-in |
| 404 | E-3000 | Listing not found |
| 400 | E-3001 | Invalid listing type (不是 ROOM) |

---

### 2.2 建立預訂 / Create Booking

#### Request

```
POST /api/v2/bookings
Content-Type: application/json
Authorization: Bearer <token>
```

##### Path Parameters

無

##### Query Parameters

無

##### Request Body

```json
{
  "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
  "checkInDate": "2026-06-01",
  "checkOutDate": "2026-06-03",
  "guestCount": 2,
  "guestName": "王小明",
  "guestPhone": "0912345678",
  "guestEmail": "wang@example.com",
  "specialRequests": "需要嬰兒床"
}
```

| 欄位 | 類型 | 必填 | 說明 | 驗證規則 |
|------|------|------|------|----------|
| roomListingId | UUID | ✅ | 房源 ID | 非 null |
| checkInDate | date | ✅ | 入住日期 | 今日或未來 |
| checkOutDate | date | ✅ | 退房日期 | 未來日期 |
| guestCount | integer | ✅ | 客人數量 | >= 1 |
| guestName | string | ✅ | 客人姓名 | 非空白, max 200 |
| guestPhone | string | | 客人電話 | 8-15 位數字 |
| guestEmail | string | | 客人 email | 有效 email 格式 |
| specialRequests | string | | 特殊要求 | max 1000 |

#### Response

**201 Created** - 預訂建立成功

```json
{
  "code": 201,
  "message": "Booking created successfully",
  "data": {
    "id": "7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": "9e9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
    "roomTitle": "豪華海景套房",
    "coverImageUrl": "https://example.com/room1.jpg",
    "checkInDate": "2026-06-01",
    "checkOutDate": "2026-06-03",
    "guestCount": 2,
    "status": "CREATED",
    "totalAmount": 3000.00,
    "paymentDueAt": "2026-04-29T10:30:00Z",
    "currency": "TWD",
    "guestName": "王小明",
    "guestPhone": "0912345678",
    "guestEmail": "wang@example.com",
    "specialRequests": "需要嬰兒床",
    "nightsCount": 2,
    "checkInTime": "15:00",
    "checkOutTime": "11:00",
    "createdAt": "2026-04-28T10:30:00Z",
    "updatedAt": "2026-04-28T10:30:00Z"
  }
}
```

##### Error Responses

| HTTP Status | Error Code | 描述 |
|-------------|------------|------|
| 400 | E-4003 | Invalid date range (check-out <= check-in) |
| 400 | E-4004 | Check-out must be after check-in |
| 400 | E-4005 | Guest count exceeds capacity |
| 400 | E-3001 | Invalid listing type (不是 ROOM) |
| 400 | E-3002 | Listing not active |
| 409 | E-4001 | Room calendar conflict (日期已被預訂) |
| 409 | E-4002 | Date already booked |
| 404 | E-3000 | Listing not found |
| 404 | E-4000 | Room data not found |
| 401 | E-1000 | Authentication required |
| 403 | E-1007 | Insufficient permissions |

##### Business Logic

1. **日期鎖定機制**: 建立預訂時使用 Redis 分散式鎖鎖定日期範圍，防止並發預訂衝突
2. **價格計算**: 總金額 = Σ(每日價格)，每日價格來自 RoomCalendar
3. **日曆更新**: 預訂成功後，自動將日期格標記為 `BOOKED`
4. **UUID 產生**: 預訂 ID 由系統自動產生 (UUID)
5. **付款期限（Sprint 225，DEF-311）**: 新預訂的 `paymentDueAt` ＝ 建立時間 ＋ 24 小時（`BOOKING_PAYMENT_TIMEOUT_HOURS`）。排程每 5 分鐘把付款期限已過、仍是 `CREATED` 的預訂取消（釋放日期格、退還優惠券額度）；已付款、或 24 小時內開始過 Stripe 結帳的不取消。**歷史預訂**（Sprint 225 之前建立）的 `paymentDueAt` 為 `null`，永不逾時取消；逾時取消後（Sprint 229，PRD US-014）會通知訂房人「訂房因逾期未付款已取消」並附「重新預訂」連結（站內通知，`data.listingId`）

---

### 2.3 取得預訂列表 / List User Bookings

#### Request

```
GET /api/v2/bookings?page=0&size=20&sortBy=createdAt&sortDir=DESC
Content-Type: application/json
Authorization: Bearer <token>
```

##### Path Parameters

無

##### Query Parameters

| 參數 | 類型 | 預設值 | 說明 |
|------|------|--------|------|
| page | integer | 0 | 頁碼 (0-indexed) |
| size | integer | 20 | 每頁數量 (max 100) |
| sortBy | string | createdAt | 排序欄位 |
| sortDir | string | DESC | 排序方向 (ASC/DESC) |

#### Response

**200 OK** - 預訂列表取得成功

```json
{
  "code": 200,
  "message": "Bookings retrieved",
  "data": {
    "content": [
      {
        "id": "7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
        "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
        "roomTitle": "豪華海景套房",
        "checkInDate": "2026-06-01",
        "checkOutDate": "2026-06-03",
        "guestCount": 2,
        "status": "CREATED",
        "totalAmount": 3000.00,
        "currency": "TWD",
        "nightsCount": 2,
        "createdAt": "2026-04-28T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

##### 多租戶隔離

此端點只返回目前使用者 (`getCurrentUser()`) 且屬於目前租戶 (`getCurrentTenant()`) 的預訂。

---

### 2.4 取得預訂詳情 / Get Booking Details

#### Request

```
GET /api/v2/bookings/7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a
Authorization: Bearer <token>
```

##### Path Parameters

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| bookingId | UUID | ✅ | 預訂 ID |

#### Response

**200 OK** - 預訂詳情取得成功

```json
{
  "code": 200,
  "message": "Booking retrieved",
  "data": {
    "id": "7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": "9e9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
    "roomTitle": "豪華海景套房",
    "coverImageUrl": "https://example.com/room1.jpg",
    "checkInDate": "2026-06-01",
    "checkOutDate": "2026-06-03",
    "guestCount": 2,
    "status": "CREATED",
    "totalAmount": 3000.00,
    "currency": "TWD",
    "guestName": "王小明",
    "guestPhone": "0912345678",
    "guestEmail": "wang@example.com",
    "specialRequests": "需要嬰兒床",
    "nightsCount": 2,
    "checkInTime": "15:00",
    "checkOutTime": "11:00",
    "createdAt": "2026-04-28T10:30:00Z",
    "updatedAt": "2026-04-28T10:30:00Z"
  }
}
```

##### Error Responses

| HTTP Status | Error Code | 描述 |
|-------------|------------|------|
| 404 | E-4006 | Booking not found |
| 401 | E-1000 | Authentication required |

---

### 2.5 更新預訂 / Update Booking

#### Request

```
PUT /api/v2/bookings/7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a
Content-Type: application/json
Authorization: Bearer <token>
```

##### Path Parameters

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| bookingId | UUID | ✅ | 預訂 ID |

##### Query Parameters

無

##### Request Body

所有欄位皆為可選，若不提供則保持原值。

```json
{
  "checkInDate": "2026-06-05",
  "checkOutDate": "2026-06-07",
  "guestCount": 3,
  "guestName": "王小明",
  "guestPhone": "0923456789",
  "guestEmail": "wang_new@example.com",
  "specialRequests": "需要加床"
}
```

| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| checkInDate | date | | 新的入住日期 |
| checkOutDate | date | | 新的退房日期 |
| guestCount | integer | | 新的客人數量 |
| guestName | string | | 新的客人姓名 |
| guestPhone | string | | 新的客人電話 |
| guestEmail | string | | 新的客人 email |
| specialRequests | string | | 新的特殊要求 |

#### Response

**200 OK** - 預訂更新成功

```json
{
  "code": 200,
  "message": "Booking updated successfully",
  "data": {
    "id": "7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "userId": "9e9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
    "roomTitle": "豪華海景套房",
    "coverImageUrl": "https://example.com/room1.jpg",
    "checkInDate": "2026-06-05",
    "checkOutDate": "2026-06-07",
    "guestCount": 3,
    "status": "CREATED",
    "totalAmount": 3000.00,
    "currency": "TWD",
    "guestName": "王小明",
    "guestPhone": "0923456789",
    "guestEmail": "wang_new@example.com",
    "specialRequests": "需要加床",
    "nightsCount": 2,
    "checkInTime": "15:00",
    "checkOutTime": "11:00",
    "createdAt": "2026-04-28T10:30:00Z",
    "updatedAt": "2026-04-28T11:00:00Z"
  }
}
```

##### Error Responses

| HTTP Status | Error Code | 描述 |
|-------------|------------|------|
| 400 | E-5001 | Booking cannot be updated in current status |
| 409 | E-4001 | New date range is not available |
| 404 | E-4006 | Booking not found |
| 400 | E-4005 | Guest count exceeds capacity |

##### Business Logic

1. **狀態檢查**: 只有 `CREATED` 或 `CONFIRMED` 狀態的預訂可更新
2. **日期變更**: 若更改日期，先釋放舊日期，再鎖定新日期
3. **價格重算**: 日期變更時，自動重新計算總金額

---

### 2.6 取消預訂 / Cancel Booking

#### Request

```
POST /api/v2/bookings/7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a/cancel?reason=行程變更
Authorization: Bearer <token>
```

##### Path Parameters

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| bookingId | UUID | ✅ | 預訂 ID |

##### Query Parameters

| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| reason | string | | 取消原因 (可選) |

#### Response

**200 OK** - 預訂取消成功（Sprint 227 起回應帶出退款資訊，PRD §15.2.5）

```json
{
  "code": 200,
  "message": "Booking cancelled successfully",
  "data": {
    "bookingId": "7c9e6679-9225-4c8b-ac0f-8e8b6d2a1f3a",
    "status": "CANCELLED",
    "canceledAt": "2026-05-20T10:00:00Z",
    "canceledBy": "CUSTOMER",
    "refundStatus": "PENDING",
    "refundAmount": 3000.00
  }
}
```

| 欄位 | 說明 |
|------|------|
| canceledBy | `CUSTOMER` 買家本人／`MERCHANT` 商家或管理員代為取消（目前只有管理員能代為取消，見 `DEF-316`） |
| refundStatus | `NONE` 不需退款（未付款，或依 Q14 不退）／`PENDING` 等待自動退款（排程每分鐘處理）；詳情端點還可能是 `COMPLETED`（已退回） |
| refundAmount | 應退金額；`refundStatus` 為 `NONE` 時為 `null` |

##### Error Responses

| HTTP Status | Error Code | 描述 |
|-------------|------------|------|
| 400 | E-4007 | Booking cannot be cancelled（狀態不允許，或同一筆被另一個請求搶先取消） |
| 404 | E-4006 | Booking not found |

##### Business Logic

1. **狀態檢查**: 透過 `OrderStateMachine.canCancel()` 判斷是否可取消
2. **併發防護**: 以條件式 UPDATE 搶占「目前狀態 → `CANCELLED`」，同一筆被同時取消只有一個成功（其餘 E-4007），退款只決定一次
3. **日曆釋放**: 取消後自動釋放日期格，標記為 `AVAILABLE`（不論退不退款）；優惠券額度一併退還
4. **退款規則（PRD §15.2.5／Q14，Sprint 227）**:
   - 只有**已付款**（`PAID`／`CONFIRMED`）的預訂有款項可退；未付款取消 `refundStatus = NONE`
   - 買家本人取消：入住前 **>= 24 小時**全額退款；**< 24 小時**（含入住時間已過）不退款。入住時刻是入住日當天、房型設定的入住時間（預設 15:00），以營運時區（Asia/Taipei）計算
   - 商家（含管理員代為取消）、系統取消：一律全額退款
   - 遲到付款：買家還在 Stripe 付款頁時預訂被取消，之後付款成功 → 付款金額全額退回（不適用 24 小時門檻：預訂沒有成立）
5. **退款執行**: 取消當下只決定並記錄（`refund_status = PENDING`、`refund_amount`），不呼叫金流；`RefundProcessingService` 排程把款項退回原付款方式（Stripe 付款經 Stripe，不看 `STRIPE_PAYMENT_ENABLED`），成功後 `refundStatus = COMPLETED`；失敗整個交易回滾、留 `AUTO_REFUND_FAILED` 稽核並指數退避重試
6. **稽核**: `BOOKING_CANCELLED`；已付款者另寫 `BOOKING_REFUND_DECIDED`（含「退多少／不退」與取消方、入住日，事後爭議的依據）
7. **舊版 `POST /v2/payments/refund`** 不再接受訂房付款（E-6002）：退款只能經取消預訂
8. **通知（Sprint 229，PRD US-005「取消後即時收到退款狀態通知」）**: 取消的交易**提交之後**，對訂房人送出站內通知（類型 `ORDER_CANCELLED`，標題「訂房已取消」），內容依結果分五種：尚未付款不需退款／全額退款處理中（含金額）／入住前不足 24 小時依政策不退款／商家取消（點名商家，含金額）／商家取消卻找不到可退款付款（交客服確認）。買家本人取消、商家或管理員代為取消**都通知**，收件人一律是訂房人。退款由排程退回後，再送一則 `REFUND_COMPLETED`「退款已完成」。通知是**盡力而為**：失敗只留警告日誌，不影響取消結果，也不會補送（見 `SPRINT_229_PLAN.md` §6）

---

## 3. 錯誤碼 / Error Codes

### 3.1 預訂系統錯誤碼 (E-4000s)

| Error Code | HTTP Status | 描述 | 解決方案 |
|------------|-------------|------|----------|
| E-4000 | 404 | Room not found | 確認 roomListingId 是否正確 |
| E-4001 | 409 | Room calendar conflict | 選擇其他日期範圍 |
| E-4002 | 409 | Date already booked | 選擇其他日期範圍 |
| E-4003 | 400 | Invalid date range | 確認 checkIn < checkOut |
| E-4004 | 400 | Check-out must be after check-in | 確認日期邏輯 |
| E-4005 | 400 | Guest count exceeds capacity | 減少 guestCount |
| E-4006 | 404 | Booking not found | 確認 bookingId 是否正確 |
| E-4007 | 400 | Booking cannot be cancelled | 預訂狀態不允許取消 |

### 3.2 相關錯誤碼

| Error Code | HTTP Status | 描述 |
|------------|-------------|------|
| E-3000 | 404 | Listing not found |
| E-3001 | 400 | Invalid listing type (不是 ROOM) |
| E-3002 | 400 | Listing not active |
| E-1000 | 401 | Authentication required |
| E-1007 | 403 | Insufficient permissions |

---

## 4. 資料模型 / Data Models

### 4.1 BookingResponse

| 欄位 | 類型 | 說明 |
|------|------|------|
| id | UUID | 預訂唯一識別碼 |
| tenantId | UUID | 所屬租戶 ID |
| userId | UUID | 預訂使用者 ID |
| roomListingId | UUID | 房源 ID |
| roomTitle | string | 房源標題 |
| coverImageUrl | string | 房源封面圖片 URL |
| checkInDate | date | 入住日期 (yyyy-MM-dd) |
| checkOutDate | date | 退房日期 (yyyy-MM-dd) |
| guestCount | integer | 客人數量 |
| status | string | 預訂狀態 |
| totalAmount | decimal | 總金額 |
| paymentDueAt | datetime | 付款期限 (ISO 8601)；逾時仍未付款的預訂會被自動取消，`null` ＝ 歷史預訂、不會逾時 |
| canceledAt | datetime | 取消時間 (ISO 8601)；未取消（或歷史取消）為 `null`（Sprint 227） |
| canceledBy | string | 取消方 `CUSTOMER`／`MERCHANT`／`SYSTEM`（逾時）；未取消（或歷史取消）為 `null` |
| refundStatus | string | 退款進度 `NONE`／`PENDING`／`COMPLETED`（PRD §15.2.5） |
| refundAmount | decimal | 取消時決定的應退金額（PRD Q14）；`refundStatus` 為 `NONE` 時為 `null` |
| currency | string | 幣別 (預設 TWD) |
| guestName | string | 客人姓名 |
| guestPhone | string | 客人電話 |
| guestEmail | string | 客人 email |
| specialRequests | string | 特殊要求 |
| nightsCount | integer | 晚數 |
| checkInTime | time | 入住時間 (HH:mm) |
| checkOutTime | time | 退房時間 (HH:mm) |
| createdAt | datetime | 建立時間 (ISO 8601) |
| updatedAt | datetime | 更新時間 (ISO 8601) |

### 4.2 預訂狀態 / Booking Status

| 狀態 | 說明 | 可取消 |
|------|------|--------|
| CREATED | 已建立，等待確認 | ✅ |
| CONFIRMED | 已確認 | ✅ |
| CHECKED_IN | 已入住 | ❌ |
| CHECKED_OUT | 已退房 | ❌ |
| CANCELLED | 已取消 | ❌ |
| NO_SHOW | 未入住 | ❌ |

---

## 5. 日期衝突邏輯 / Date Conflict Logic

### 5.1 鎖定流程 (預訂建立)

```
1. 接收建立預訂請求
2. 驗證 dates (checkIn < checkOut)
3. 呼叫 lockDateRange() 嘗試取得 Redis 分散式鎖
   - 成功 → 取得 lockValue
   - 失敗 → 回傳 E-4001 (日期正在被修改)
4. 呼叫 isDateRangeAvailable() 再次檢查
   - 不可用 → 釋放鎖，回傳 E-4001
5. 建立預訂資料
6. 呼叫 bookDateRange() 標記日期為 BOOKED
7. 釋放 Redis 鎖
8. 回傳成功
```

### 5.2 衝突檢查

```
日期衝突發生在以下情況：
- 日期格狀態為 BOOKED (已被其他預訂佔用)
- 日期格狀態為 BLOCKED (被管理員封鎖)

衝突判斷邏輯：
- checkInDate ~ checkOutDate 的任何一天不可用 → 整個請求失敗
```

### 5.3 RoomCalendarStatus

| 狀態 | 說明 |
|------|------|
| AVAILABLE | 可預訂 |
| BOOKED | 已被預訂 |
| BLOCKED | 被管理員封鎖 |
| MAINTENANCE | 維護中 |

---

## 6. 測試案例摘要 / Test Cases Summary

### 6.1 建立預訂 (US-M06-001)

| 測試案例 | 預期結果 |
|----------|----------|
| TC-M06-001-1: 有效資料建立預訂 | 成功回傳 201 + BookingResponse |
| TC-M06-001-2: 日期衝突 | 回傳 E-4001 |
| TC-M06-001-3: 入住日期 >= 退房日期 | 回傳 E-4003 |
| TC-M06-001-4: 客人數量超過上限 | 回傳 E-4005 |

### 6.2 預訂日曆鎖定 (US-M06-002)

| 測試案例 | 預期結果 |
|----------|----------|
| TC-M06-002-1: 預訂成功後日期格變為 BOOKED | calendar status = BOOKED |
| TC-M06-002-2: 其他人無法預訂相同日期 | 回傳 E-4001 |

### 6.3 取消預訂 (US-M06-003)

| 測試案例 | 預期結果 |
|----------|----------|
| TC-M06-003-1: 取消 CREATED 預訂 | 成功，日期格釋放為 AVAILABLE |
| TC-M06-003-2: 取消 CHECKED_IN 預訂 | 回傳 E-4007 |

---

## 7. 參考文件 / References

| 文件 | 路徑 |
|------|------|
| Sprint 4 計劃 | `docs/04_planning/SPRINT_04_PLAN.md` |
| BookingService | `backend/src/main/java/com/nextkey/ecommerce/core/booking/BookingService.java` |
| RoomCalendarService | `backend/src/main/java/com/nextkey/ecommerce/core/booking/RoomCalendarService.java` |
| BookingController | `backend/src/main/java/com/nextkey/ecommerce/api/controller/BookingController.java` |
| BookingDto | `backend/src/main/java/com/nextkey/ecommerce/api/dto/BookingDto.java` |
| ErrorCode | `backend/src/main/java/com/nextkey/ecommerce/shared/exception/ErrorCode.java` |
| FRD | `docs/01_requirements/E-Commerce_FRD_v1.0.md` |

---

**文件版本**: v1.0
**最後更新**: 2026-04-28
**維護者**: SD (Marcus)