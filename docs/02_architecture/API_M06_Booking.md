# API 規格文件 - M06 預訂系統 / API Specification - M06 Booking System

> **文件編號**: API-M06
> **文件版本**: v2.0（Sprint 243 依實作改寫）
> **建立日期**: 2026-04-28（v1.0，Sprint 4）
> **最後更新**: 2026-10-03
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`BookingController`、`BookingPaymentController`、`DashboardBookingController`、`BookingService`、`RoomCalendarService`、`BookingRefundPolicy`、`PaymentStateService`、`BookingDto`、`GlobalExceptionHandler`；**每個回應形狀、狀態碼、錯誤碼與權限宣稱都用真實 PostgreSQL＋Redis（日曆鎖）＋完整 HTTP／JWT／權限鏈實測**（[BookingApiRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/BookingApiRealStackIntegrationTest.java)）
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](./API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`，**沒有**巢狀的 `error`，也**沒有**數字型的 `code`）
> **取代關係**: 本文件**取代** v1.0 全文

> **⚠️ 修訂註記（Sprint 243）**：v1.0 只描述 Sprint 4 的 6 個端點，與實作有七處落差，本版已更正：
> 1. **端點由 6 個改為實際的 11 個**：漏了日曆（`GET /calendar`）、整組付款端點（Sprint 221）與商家端訂房列表（`GET /v2/dashboard/bookings`，Sprint 231）；
> 2. **退款與取消方**（Sprint 225～227）：取消回應多了 `canceledBy`／`refundStatus`／`refundAmount`；已付款訂房的退款依 PRD Q14（買家入住前 ≥ 24 小時全額、不足 24 小時不退；商家或管理員代為取消一律全額）；
> 3. **付款期限**：訂房建立時帶 `paymentDueAt`（預設 24 小時），逾時未付款自動取消並釋放日曆（Sprint 225）；
> 4. **訂房歸屬房源所屬的店鋪**（Sprint 236，`DEF-319`）：`tenantId` 是店鋪，不是買家的租戶；店鋪暫停營業不能訂房、也不能付款（`E-2010`，Sprint 239／242）；
> 5. **商家可讀、改、取消自己店鋪的訂房**（Sprint 231）——v1.0 寫「買家只能看到自己 tenant 下的預訂」；
> 6. **錯誤碼表以實際為準**；
> 7. **三個缺陷在撰寫本版時以真實服務實測發現並修復**：建立訂房與合併結帳的回應 `tenantId`／`userId`／`roomListingId` 原本是 `null`（`DEF-338`）；入住日＝退房日的請求原本回 `201` 並建出 0 晚 0 元的訂房（`DEF-340`；購物車對同一情形本來就回 `E-4004`）；**預訂建立後 60 秒內，同房源同日期無法再被訂**——取消後立刻重訂、改期到重疊日期都回 `E-4001`，因為日期鎖從來沒被釋放（`DEF-343`，鎖鍵被加了兩次 `lock:` 前綴）。另外列表的 `sortBy`／`sortDir` 亂填原本是 `500`（`DEF-339`）。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M06 |
| **模組名稱** | 預訂系統 / Booking |
| **描述** | 買家檢查房源在日期區間是否可訂、查看日曆、建立訂房、付款、取消；店家（店主／房東）檢視自己店鋪的訂房、代為修改住客資料或取消 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/bookings/...` |
| **多租戶隔離** | 訂房的 `tenantId` 是**房源所屬的店鋪**。買家只能讀寫自己的訂房；店主／房東只能存取自己店鋪的訂房（系統租戶的呼叫者**不算**「同店鋪」）；管理員不受限 |
| **範圍外** | 房東維護日曆（`/v2/dashboard/rooms/{id}/maintenance`）、訂房評價（`/v2/booking-reviews`）、房源管理（M02）、動態定價規則（M12）、合併結帳（[API_M05_Order.md](./api/API_M05_Order.md) §4.8） |

### 1.2 設計重點

1. **日曆以「晚」為單位**：區間 `[checkInDate, checkOutDate)`，**退房日不佔用**；晚數 = 退房日 − 入住日，**必須 ≥ 1**。日曆只存「有記錄的日期」（`AVAILABLE`／`BOOKED`／`BLOCKED`／`MAINTENANCE`），**沒有記錄的日期視為可訂**；超出房源「開放窗」的日期回 `NOT_OPEN`。
2. **併發與衝突**：建立訂房時對每一晚取 Redis 日期鎖（NO WAIT，取不到立刻 `E-4001`）、再以資料庫列鎖（`FOR UPDATE NOWAIT`）確認並標記 `BOOKED`；鎖在 `finally` 釋放。
3. **金額在建立當下算好**：`totalAmount = 房價（動態定價優先，否則 basePrice × 晚數） − 優惠券折扣`。付款期限 `paymentDueAt` = 建立時間 + 24 小時（`BOOKING_PAYMENT_TIMEOUT_HOURS`）；**歷史訂房（`null`）永不逾時**。
4. **狀態**：`CREATED → PAID → CONFIRMED → CHECKED_IN → CHECKED_OUT → COMPLETED`，`CANCELLED` 為取消終態。**可取消**＝`CREATED`／`PAID`／`CONFIRMED`；**可更新**＝`CREATED`／`CONFIRMED`（實測：**已付款（`PAID`）的訂房不能更新**，回 `422 E-5010`）。
5. **取消的退款（PRD Q14，Sprint 227）**：只有已付款的訂房才有款項可退。買家**本人**取消：入住前 **≥ 24 小時全額退款**、**不足 24 小時不退**；商家／管理員**代為取消**、系統逾時取消一律全額退款。入住時刻是「入住日＋房型的入住時間（預設 15:00）」在營運時區（Asia/Taipei）的絕對時刻。取消回應的 `refundStatus: PENDING` 表示**等待排程自動退款**（取消請求本身不呼叫金流）。
6. **店鋪暫停營業不能訂房、也不能付款**（`E-2010`）；**取消、退款、Stripe 入帳不擋**（Sprint 239／242）。付款狀態的 `storeOpen` 讓前端提前顯示「店鋪暫停營業」。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M06-001 | 檢查日期可用性 | `GET /v2/bookings/availability`、`GET /v2/bookings/calendar` | ✅ 已實作 |
| US-M06-002 | 建立訂房 | `POST /v2/bookings` | ✅ 已實作 |
| US-M06-003 | 查詢訂房 | `GET /v2/bookings`、`GET /v2/bookings/{bookingId}` | ✅ 已實作 |
| US-M06-004 | 更新訂房 | `PUT /v2/bookings/{bookingId}` | ✅ 已實作 |
| US-M06-005 | 取消訂房（含退款通知） | `POST /v2/bookings/{bookingId}/cancel` | ✅ 已實作 |
| US-M06-006 | 訂房付款 | `POST /v2/bookings/{bookingId}/pay*` | ✅ 已實作（Mock；Stripe 需 `STRIPE_PAYMENT_ENABLED`） |
| US-M06-007 | 商家管理訂房 | `GET /v2/dashboard/bookings` ＋ 上列的讀取／更新／取消 | ✅ 已實作 |

---

## 2. API 端點總覽 / Endpoints Summary

| 方法 | 路徑（`/api` 之後） | 描述 | 權限 | 成功 |
|------|--------------------|------|------|:----:|
| **GET** | `/v2/bookings/availability` | 檢查日期區間可用性與總價 | `booking:read` | 200 |
| **GET** | `/v2/bookings/calendar` | 房源日曆（有記錄的日期） | `booking:read` | 200 |
| **POST** | `/v2/bookings` | 建立訂房（可帶 `Idempotency-Key`） | `booking:create` | 201（冪等重送 200） |
| **GET** | `/v2/bookings` | 買家自己的訂房列表（分頁） | `booking:read` | 200 |
| **GET** | `/v2/bookings/{bookingId}` | 訂房詳情 | `booking:read` | 200 |
| **PUT** | `/v2/bookings/{bookingId}` | 更新訂房（日期、人數、住客資料） | `booking:update` | 200 |
| **POST** | `/v2/bookings/{bookingId}/cancel` | 取消訂房（含退款決定） | `booking:cancel` | 200 |
| **POST** | `/v2/bookings/{bookingId}/pay` | Mock 付款成功 | `booking:create` 或 `booking:update` | 200 |
| **POST** | `/v2/bookings/{bookingId}/pay/checkout` | 發起 Stripe Checkout | `booking:create` 或 `booking:update` | 200 |
| **GET** | `/v2/bookings/{bookingId}/pay/checkout/return` | Stripe 回跳後確認付款 | `booking:read` | 200 |
| **GET** | `/v2/dashboard/bookings` | 店鋪收到的訂房列表（分頁） | `booking:read` | 200 |

訂房的**付款狀態**查詢走 `GET /v2/orders/bookings/{bookingId}/payment`（路由在訂單付款控制器，規格見 [API_M05_Order.md](./api/API_M05_Order.md) §7.7）。未帶 token 回 `401 E-1000`；已登入但沒有該權限、或不是資源的擁有者／同店鋪／管理員回 `403 E-1007`。

---

## 3. 資料模型 / Data Models

### 3.1 Request

#### CreateBookingRequest — `POST /v2/bookings`

```json
{
  "roomListingId": "550e8400-e29b-41d4-a716-446655440000",
  "checkInDate": "2026-11-02",
  "checkOutDate": "2026-11-04",
  "guestCount": 2,
  "guestName": "王小明",
  "guestPhone": "0912345678",
  "guestEmail": "guest@example.com",
  "specialRequests": "晚到",
  "promoCode": "SAVE20"
}
```

| 欄位 | 型別 | 必填 | 限制 | 說明 |
|------|------|:----:|------|------|
| `roomListingId` | UUID | 是 | 必須是上架中（`ACTIVE`）的 ROOM | |
| `checkInDate` | date | 是 | **今天或未來**（`@FutureOrPresent`） | 入住日 |
| `checkOutDate` | date | 是 | **未來**（`@Future`），且**晚於**入住日（含同一天 → `E-4003`，`DEF-340`） | 退房日（不佔用） |
| `guestCount` | int | 是 | ≥ 1，**不超過房型 `maxGuests`**（`E-4005`） | |
| `guestName` | string | 是 | 不可空白、≤ 200 字 | |
| `guestPhone` | string | 否 | `^[0-9]{8,15}$` | |
| `guestEmail` | string | 否 | Email 格式 | |
| `specialRequests` | string | 否 | ≤ 1000 字 | |
| `promoCode` | string | 否 | | 訂房沒有購物車，促銷碼由請求帶入；以**房源所屬店鋪**解析（PC-005：Phase 1 不支援跨商家優惠）；成立後才佔用額度 |

標頭：`Idempotency-Key`（選填，UUID v4）——見 §4.3。

#### UpdateBookingRequest — `PUT /v2/bookings/{bookingId}`

所有欄位選填（部分更新，沒帶的欄位不動）：`checkInDate`（今天或未來）、`checkOutDate`（未來）、`guestCount`（≥ 1）、`guestName`（≤ 200）、`guestPhone`、`guestEmail`、`specialRequests`。

### 3.2 Response

#### BookingResponse — 詳情／建立／更新的 `data`

```json
{
  "id": "adbf0eb2-e8fc-4a2c-8e96-9de81e0a2c6d",
  "tenantId": "2e29c33f-5b7a-43d3-b7b3-fb534354652a",
  "userId": "f8679d67-499c-41ab-b58d-0c5922420af3",
  "roomListingId": "0c04c8e2-dd6d-4396-a872-87563f0a970f",
  "roomTitle": "探針房間",
  "coverImageUrl": null,
  "checkInDate": "2026-11-02",
  "checkOutDate": "2026-11-04",
  "guestCount": 2,
  "status": "CREATED",
  "totalAmount": 2000.00,
  "paymentDueAt": "2026-10-04T09:28:02.454053Z",
  "canceledAt": null,
  "canceledBy": null,
  "refundStatus": "NONE",
  "refundAmount": null,
  "promoCode": null,
  "discountAmount": 0.00,
  "currency": "TWD",
  "guestName": "探針住客",
  "guestPhone": "0912345678",
  "guestEmail": "guest@example.com",
  "specialRequests": null,
  "nightsCount": 2,
  "checkInTime": "15:00:00",
  "checkOutTime": "11:00:00",
  "createdAt": "2026-10-03T09:28:02.454Z",
  "updatedAt": "2026-10-03T09:28:02.454Z"
}
```

| 欄位 | 說明 |
|------|------|
| `tenantId` | 訂房歸屬的**店鋪**（房源所屬的租戶） |
| `status` | `CREATED`／`PAID`／`CONFIRMED`／`CHECKED_IN`／`CHECKED_OUT`／`COMPLETED`／`CANCELLED` |
| `paymentDueAt` | 付款期限（建立 + 24 小時）；`null`＝歷史訂房，不會逾時。逾時未付款自動取消並釋放日曆 |
| `canceledAt`／`canceledBy` | 取消時間與取消方（`CUSTOMER` 買家本人／`MERCHANT` 商家或管理員／`SYSTEM` 逾時）；未取消為 `null`。欄位名沿用 PRD §15.2.5 的拼法 `canceled…` |
| `refundStatus` | `NONE`（不需退款：未付款、或買家入住前不足 24 小時）／`PENDING`（等待自動退款）／`COMPLETED`（已退回） |
| `refundAmount` | 應退金額；`refundStatus` 為 `NONE` 時為 `null` |
| `currency` | 一律 `TWD` |
| `nightsCount`／`checkInTime`／`checkOutTime` | 晚數；房型設定的入住／退房時間（預設 15:00／11:00） |

> **`DEF-338`（Sprint 243 修復）**：建立訂房與合併結帳的回應（含冪等重放存下的回應），`tenantId`／`userId`／`roomListingId` 原本是 `null`；`GET` 詳情從資料庫載入，本來就是對的。

#### BookingListResponse — 列表的 `data.content[]`

```json
{
  "id": "9c3f9a78-61b2-4da5-9b05-7747e125709f",
  "roomListingId": "3690ead0-e721-4cce-858e-6d8ea0a2cb5c",
  "roomTitle": "探針房間",
  "checkInDate": "2026-12-12",
  "checkOutDate": "2026-12-13",
  "guestCount": 2,
  "status": "CREATED",
  "totalAmount": 1000.00,
  "currency": "TWD",
  "nightsCount": 1,
  "createdAt": "2026-10-03T09:21:57.414945Z",
  "guestName": "探針住客"
}
```

`data` 是 **Spring `Page`**（`content[]`、`totalElements`、`totalPages`、`size`、`number`、`first`、`last`…）。`guestName` 供商家識別訂房人（買家看到的就是自己填的）。

#### AvailabilityResponse — `GET /v2/bookings/availability` 的 `data`

```json
{
  "available": true,
  "roomListingId": "3690ead0-e721-4cce-858e-6d8ea0a2cb5c",
  "checkInDate": "2026-11-02",
  "checkOutDate": "2026-11-04",
  "nightsCount": 2,
  "totalPrice": 2000.00,
  "currency": "TWD",
  "calendarDetails": [],
  "unavailableReason": null,
  "originalTotalPrice": null,
  "discountAmount": null,
  "appliedRuleName": null,
  "priceAdjustmentType": null
}
```

| 欄位 | 說明 |
|------|------|
| `available` | 區間內每一晚都可訂，且都在開放窗內 |
| `unavailableReason` | 不可訂的原因碼：`INVALID_DATE_RANGE`（退房不晚於入住，**仍是 `200`，不是錯誤**）、`NOT_OPEN_FOR_BOOKING`（含未開放日，優先回報）、`BOOKED`、`BLOCKED`、`MAINTENANCE`；可訂為 `null` |
| `totalPrice` | 總價（動態定價規則生效時是調整後總價）；不可訂且是日期無效時為 `null` |
| `originalTotalPrice`／`discountAmount`／`appliedRuleName`／`priceAdjustmentType` | 動態定價規則生效時填入：調整前總價、有號差額（正＝折扣、負＝加價）、規則名、方向（`DISCOUNT`／`MARKUP`／`NONE`）；沒有規則時為 `null` |
| `calendarDetails` | 可訂時是區間內「已有記錄」的日曆項目（沒有記錄的日期不在內，所以全新的房源是 `[]`）；不可訂為 `null` |

#### CalendarEntry — `GET /v2/bookings/calendar` 的 `data[]`

```json
{ "date": "2026-11-02", "status": "BOOKED", "price": 1000.00, "bookingId": "5ebb6dc1-…", "originalPrice": null, "appliedRuleName": null, "priceAdjustmentType": null }
```

`status`：`AVAILABLE`／`BOOKED`／`BLOCKED`／`MAINTENANCE`／**`NOT_OPEN`**（超過開放窗的「無記錄日」，顯式回傳）。動態定價規則對可訂日生效時 `price` 是調整後價、`originalPrice` 是調整前價。**沒有記錄的日期不會出現在回應裡，視為可訂**（前端以房源 `basePrice` 補齊）。

#### CancelBookingResponse — `POST …/cancel` 的 `data`

```json
{
  "bookingId": "d2b02594-10e6-496e-9688-699d0dd01900",
  "status": "CANCELLED",
  "canceledAt": "2026-10-03T09:21:57.884655Z",
  "canceledBy": "CUSTOMER",
  "refundStatus": "PENDING",
  "refundAmount": 2000.00
}
```

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `GET /v2/bookings/availability` — 檢查可用性

**權限**：`booking:read`。查詢參數（全必填）：`roomListingId`、`checkInDate`、`checkOutDate`（`yyyy-MM-dd`）。成功 `200`，`data` 是 `AvailabilityResponse`；**日期區間無效或不可訂都是 `200`＋`available=false`＋原因碼**，不是錯誤。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 房源不存在 | 404 | `E-4000` |
| 不是 ROOM 類型的刊登項目 | 422 | `E-3001` |
| 缺參數／日期格式不對（例如 `checkInDate=abc`） | 400 | `E-9005`／`E-9000`（訊息「請求參數格式錯誤」） |

### 4.2 `GET /v2/bookings/calendar` — 房源日曆

**權限**：`booking:read`。查詢參數：`roomListingId`、`startDate`、`endDate`（含頭含尾）。**區間最多 92 天**。成功 `200`，`data` 是 `CalendarEntry[]`（只含有記錄的日期，另含超出開放窗的 `NOT_OPEN` 日）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 房源不存在 | 404 | `E-4000` |
| 不是 ROOM；`endDate` 早於 `startDate`；區間超過 92 天 | 422 | `E-3001`（**預設訊息是「無效的刊登類型」，與實際原因不符**，`DEF-341`） |

### 4.3 `POST /v2/bookings` — 建立訂房

**權限**：`booking:create`（Buyer、Store Owner、Admin、Super Admin 持有；**Host 沒有**）。標頭 `Idempotency-Key`（選填，UUID v4）：

- 帶了：第一次 `201`，**同一把鍵**重送回 `200` 與**同一筆訂房**（不重複建立）；處理中回 `409 E-6005`；格式不對回 `400 E-9004`；建立失敗時鍵被清除，可安全重試。前端結帳每次都帶。
- 不帶：維持原行為（不建議）。

**成功 `201`**：`data` 是 `BookingResponse`（`status` `CREATED`、`refundStatus` `NONE`、`paymentDueAt` 約 24 小時後）。

**錯誤**

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 房源不存在／房型資料缺失 | 404 | `E-4000` |
| 不是 ROOM／房源未上架 | 422 | `E-3001`／`E-3002` |
| 區間含**未開放日**（超出房源開放窗） | 422 | `E-3002` |
| 入住人數超過房型上限 | 400 | `E-4005` |
| **退房不晚於入住**（含同一天；`DEF-340`：原本同一天回 201） | 400 | `E-4003` |
| 日期已被訂走／正被別人訂（Redis 日期鎖或資料庫列鎖衝突） | 400 | `E-4001` |
| 入住日是過去、退房日不是未來、缺欄位、欄位超過長度 | 400 | `E-9000`（帶 `errors[]`） |
| 店鋪暫停營業（非 `ACTIVE`） | 422 | `E-2010` |
| 促銷碼失效／售罄／超過每人限用 | 400 | `E-5007`／`E-5008`／`E-5009` |

> **`E-4001` 的一個已修復的假衝突（`DEF-343`）**：建立成功後，同房源同日期的 Redis 日期鎖原本**不會被釋放**（`RoomCalendarService.unlockDateRange` 把已帶 `lock:` 前綴的鍵傳給 `forceReleaseLock`，後者再加一次前綴，刪掉的是不存在的 `lock:lock:…`），要等 60 秒 TTL 才消失——期間取消後重訂、改期到重疊日期都會得到 `E-4001`。修復後鎖在請求結束時即釋放。

### 4.4 `GET /v2/bookings` — 買家訂房列表

**權限**：`booking:read`。只回**呼叫者自己**的訂房。參數同訂單列表：`page`（預設 0）、`size`（預設 20，**上限 100**）、`sortBy`（`createdAt`／`updatedAt`／`checkInDate`／`checkOutDate`／`totalAmount`／`status`，其他回 `400 E-9000`）、`sortDir`（`asc`／`desc`，其他回 `400 E-9000`）。成功 `200`，`data` 是 `Page<BookingListResponse>`。

> `sortBy`／`sortDir` 亂填原本是 `500 E-9900`（`DEF-339`，Sprint 243 修復）。**注意**：repository 方法名寫死 `OrderByCreatedAtDesc`，這兩個參數目前**實際上不改變順序**（永遠建立時間新到舊）——`DEF-342`，已登記。

### 4.5 `GET /v2/bookings/{bookingId}` — 訂房詳情

**權限**：`booking:read`，且是**訂房本人**、**該店鋪**的店主／房東（呼叫者的租戶是真實店鋪且等於訂房的 `tenantId`）或**管理員**。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂房不存在 | 404 | `E-4006` |
| 別的買家、別家店鋪的人 | 403 | `E-1007` |

### 4.6 `PUT /v2/bookings/{bookingId}` — 更新訂房

**權限**：`booking:update`（Host、Store Owner、Admin、Super Admin 持有；**Buyer 沒有，回 `403 E-1007`**），且是本人、同店鋪或管理員——所以實務上是**店家代買家修改**。請求：`UpdateBookingRequest`（部分更新）。

- 只有 **`CREATED`／`CONFIRMED`** 可更新；**`PAID`（已付款）回 `422 E-5010`**。
- **改日期**：先釋放舊日期、鎖新日期、確認可用、重算金額（沿用已記錄的促銷碼重新計算折扣，不重新驗證券）。新區間以「合併後的最終日期」檢查（只改單一欄位也適用）。
- **改人數**：不得超過房型上限（`E-4005`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂房不存在／不是本人也不是同店鋪／管理員 | 404／403 | `E-4006`／`E-1007` |
| 狀態不是 `CREATED`／`CONFIRMED` | 422 | `E-5010` |
| 新區間**退房不晚於入住**（含 0 晚；`DEF-340`） | 400 | `E-4003` |
| 新日期被訂走／被鎖／超出開放窗 | 400／422 | `E-4001`／`E-3002` |
| 人數超過上限 | 400 | `E-4005` |

### 4.7 `POST /v2/bookings/{bookingId}/cancel` — 取消訂房

**權限**：`booking:cancel`（Buyer、Host、Store Owner、Admin、Super Admin 持有），且是本人、同店鋪（店主／房東）或管理員。選填 query 參數 `reason`。

只有 `CREATED`／`PAID`／`CONFIRMED` 可取消。取消時（同一個交易內，先以條件式更新搶占「目前狀態 → `CANCELLED`」，併發取消只會有一個成功）：

1. 釋放日曆（日期立刻可再訂）。
2. 退還優惠券額度。
3. 決定退款（PRD Q14，見 §1.2 第 5 點），記在訂房上（`refundStatus`／`refundAmount`／`canceledAt`／`canceledBy`）；**不呼叫金流**，`PENDING` 由排程自動退回原付款方式。
4. 交易提交後通知買家（取消與退款狀態，PRD US-005）。

成功 `200`，`message` 是 `"Booking cancelled successfully"`，`data` 是 `CancelBookingResponse`。

| 情境 | `canceledBy` | `refundStatus`／`refundAmount` |
|------|--------------|-------------------------------|
| 未付款（`CREATED`）取消 | 取消者 | `NONE`／`null` |
| 買家本人取消已付款，入住前 **≥ 24 小時** | `CUSTOMER` | `PENDING`／全額（付款金額減已退金額） |
| 買家本人取消已付款，入住前 **不足 24 小時**（含入住時刻已過） | `CUSTOMER` | `NONE`／`null` |
| 店主／房東／管理員代為取消已付款 | `MERCHANT` | `PENDING`／全額 |
| 逾時未付款自動取消 | `SYSTEM` | `NONE`（沒有款項） |

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂房不存在 | 404 | `E-4006` |
| 不是本人／同店鋪／管理員 | 403 | `E-1007` |
| 狀態不可取消（已取消、已入住…，含併發取消輸的一邊） | 400 | `E-4007` |

**店鋪暫停營業時仍可取消**（Sprint 239／242）。

### 4.8 `POST /v2/bookings/{bookingId}/pay` — Mock 付款成功

**權限**：`booking:create` 或 `booking:update`（Buyer 持有前者）；**本人或管理員**（店主不能代付）。訂房 `CREATED → PAID`，留一筆 `SUCCESS` 的 Mock 付款（金額＝訂房總額）。**只有未啟用 Stripe 時可用**。成功 `200`，`message` 是 `"Payment successful"`，`data` 是付款狀態（形狀見 [API_M05_Order.md](./api/API_M05_Order.md) §3.2 `OrderPaymentState`，`orderId`／`orderStatus` 欄位裝的是訂房 id 與狀態）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂房不存在／不是本人 | 404／403 | `E-4006`／`E-1007` |
| 已啟用 Stripe（Mock 不可用） | 422 | `E-6004` |
| 訂房不是 `CREATED`（已付款、已取消…；含重複付款） | 422 | `E-5011` |
| 已有成功付款紀錄（併發的另一邊） | 400 | `E-6003` |
| **店鋪暫停營業**（Sprint 242） | 422 | `E-2010` |

### 4.9 `POST /v2/bookings/{bookingId}/pay/checkout` — 發起 Stripe Checkout

權限同 §4.8。需 `STRIPE_PAYMENT_ENABLED` 開啟（**未啟用回 `400 E-6002`**，預設訊息是「付款已取消」，`DEF-341`）。建立 `PROCESSING` 的付款紀錄與 Stripe Checkout Session，成功 `200`，`data` 是 `{ bookingId, sessionId, sessionUrl }`。同一筆訂房再次發起拿回同一個 Session，不重複寫付款紀錄；查與寫之間的極小競態回可重試的 `422 E-6005`。**店鋪暫停營業回 `422 E-2010`，且 Stripe 完全不會被呼叫**；訂房不是 `CREATED` 回 `422 E-5011`。

### 4.10 `GET /v2/bookings/{bookingId}/pay/checkout/return` — Stripe 回跳確認

**權限**：`booking:read`，本人或管理員。必填 query 參數 `sessionId`，且必須是**屬於這筆訂房**的 Session（否則 `404 E-6000`）。已付款則與 webhook 共用同一個入帳核心（付款 `SUCCESS`＋訂房 `PAID`，冪等）。**不受店鋪營業狀態限制**（錢已收，必須入帳）。若付款成功時訂房已被取消，系統把全額標成等待退款（`refundStatus: PENDING`）並留稽核紀錄。Stripe 查詢失敗回 `503 E-6007`。

### 4.11 `GET /v2/dashboard/bookings` — 店鋪收到的訂房

**權限**：`booking:read`。回**呼叫者所屬店鋪**的訂房（參數與回應同 §4.4）。**沒有店鋪的呼叫者（一般買家）回空頁**（`200`、`totalElements` 0），不查詢、不報錯。商家取消訂房沿用 §4.7（沒有另外的店家層取消端點）。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | Buyer | Host | Store Owner | Store Staff | Admin／Super Admin |
|------|:----:|:----:|:----:|:----:|:----:|
| `GET availability`／`calendar` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /v2/bookings` | ✅ | ❌ | ✅ | ❌ | ✅ |
| `GET /v2/bookings`（自己的） | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /v2/bookings/{id}` | 本人 | 同店鋪 | 同店鋪 | 同店鋪 | ✅ |
| `PUT /v2/bookings/{id}` | ❌（403） | 同店鋪 | 同店鋪 | ❌ | ✅ |
| `POST …/cancel` | 本人 | 同店鋪 | 同店鋪 | ❌ | ✅ |
| `POST …/pay`、`…/pay/checkout` | 本人 | ❌ | ❌ | ❌ | ✅ |
| `GET /v2/dashboard/bookings` | 空頁 | ✅ | ✅ | ✅ | 呼叫者的租戶是真實店鋪才有資料 |

---

## 6. 錯誤碼 / Error Codes

M06 常見錯誤碼（完整對照表與預設訊息見 [API_Error_Codes.md](./API_Error_Codes.md)）：

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1000`／`E-1007` | 401／403 | 沒帶 token／沒有權限或不是本人、同店鋪、管理員 |
| `E-2010` | 422 | 店鋪暫停營業：訂房、付款（Sprint 239／242） |
| `E-3001`／`E-3002` | 422 | 不是 ROOM（或日曆參數無效，`DEF-341`）／房源未上架或區間含未開放日 |
| `E-4000` | 404 | 找不到房源 |
| `E-4001` | 400 | 房源日曆衝突（日期已被訂、被鎖） |
| `E-4003` | 400 | 無效的日期區間（退房不晚於入住） |
| `E-4005` | 400 | 入住人數超過房源容納上限 |
| `E-4006` | 404 | 找不到訂房 |
| `E-4007` | 400 | 此訂房無法取消 |
| `E-5007`／`E-5008`／`E-5009` | 400 | 優惠碼無效／過期／達使用上限 |
| `E-5010` | 422 | 無效的訂房狀態（不可更新） |
| `E-5011` | 422 | 無效的付款狀態（不可付款） |
| `E-6000`／`E-6002`／`E-6003`／`E-6004`／`E-6005`／`E-6007` | 404／400／400／422／422・409／503 | 付款：找不到紀錄、Stripe 未啟用、已付款、Stripe 啟用後 Mock 不可用、冪等衝突、金流服務商錯誤 |
| `E-9000` | 400 | 驗證錯誤（請求本文驗證帶 `errors[]`；`sortBy`／`sortDir` 無效、參數格式錯誤〔如日期無法解析〕不帶） |
| `E-9004`／`E-9005` | 400 | `Idempotency-Key` 不是 UUID v4／缺必填參數 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `sortBy`／`sortDir` 不改變順序 | repository 方法名寫死 `OrderByCreatedAtDesc`（`DEF-342`） |
| `E-3001`／`E-6002` 的預設訊息誤導 | 日曆參數無效回「無效的刊登類型」、Stripe 未啟用回「付款已取消」（`DEF-341`） |
| 已付款的訂房不能更新 | `PAID` 回 `E-5010`，而 `CONFIRMED` 可以——實測行為；要改已付款的訂房請取消後重訂 |
| 舊版 `POST /v2/payments` 的訂房分支 | HTTP 打不到（`orderId` 是 `@NotNull`，只帶 `bookingId` 在驗證層回 400；Sprint 221 `DEF-303` (1)）；訂房請用 §4.8～4.10 |
| 訂房沒有逾時以外的自動取消 | 已付款但沒有人確認的訂房不會自動處理（`CONFIRMED` 由店家操作） |
| Stripe 路徑未對真實 Stripe 驗證 | 以 Mock／WireMock 驗證，從未對真實 Stripe 驗證（Sprint 221／227 紀錄） |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §5 訂房（日曆、開放窗、動態定價）、§7.3 權限矩陣、§9.16 商家端訂房、§15.2.5／Q14 取消與退款、US-005 取消通知、US-008／PC-005 單一商家 |
| 相關規格 | [API_M05_Order.md](./api/API_M05_Order.md)（訂單與付款狀態）、[API_M04_Cart.md](./API_M04_Cart.md)（購物車）、[API_Error_Codes.md](./API_Error_Codes.md) |
| 實作 | `BookingController`、`BookingPaymentController`、`DashboardBookingController`（`api/controller`）；`BookingService`、`RoomCalendarService`、`BookingRefundPolicy`、`PaymentStateService`（`core`） |
| 契約測試 | `BookingApiRealStackIntegrationTest`（真實 PostgreSQL＋Redis）、`RoomCalendarLockRealRedisIntegrationTest`（日期鎖）、`StoreSuspendedPaymentIntegrationTest`；路由漂移守門 `ApiRouteDocDriftTest` |
| 缺陷 | `DEF-338`、`DEF-339`、`DEF-340`、`DEF-343`（Sprint 243 已修）、`DEF-341`、`DEF-342`（已登記）；Sprint 239／242（`E-2010`） |
