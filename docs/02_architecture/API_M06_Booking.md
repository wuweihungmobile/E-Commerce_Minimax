# API 規格文件 - M06 預訂系統 / API Specification - M06 Booking System

> **文件編號**: API-M06
> **文件版本**: v2.2（Sprint 246 新增狀態日誌、店家後台按鈕與人工退款；v2.1 為 Sprint 245 新增入住與退房；v2.0 為 Sprint 243 依實作改寫）
> **建立日期**: 2026-04-28（v1.0，Sprint 4）
> **最後更新**: 2026-10-09
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`BookingController`、`BookingPaymentController`、`DashboardBookingController`、`BookingService`、`BookingNoShowService`、`RoomCalendarService`、`BookingRefundPolicy`、`PaymentStateService`、`BookingDto`、`GlobalExceptionHandler`；**每個回應形狀、狀態碼、錯誤碼與權限宣稱都用真實 PostgreSQL＋Redis（日曆鎖）＋完整 HTTP／JWT／權限鏈實測**（[BookingApiRealStackIntegrationTest](../../backend/src/test/java/com/nextkey/ecommerce/integration/BookingApiRealStackIntegrationTest.java)）
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](./API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`，**沒有**巢狀的 `error`，也**沒有**數字型的 `code`）
> **取代關係**: 本文件**取代** v1.0 全文

> **⚠️ 修訂註記（Sprint 246，2026-10-09）**：DEF-350（店家入住／退房 UI 與狀態日誌）與 DEF-354（店家漏標入住的人工退款）已實作。新增 §4.14 `GET /v2/dashboard/bookings/{bookingId}/state-log`（PRD §9.7，查詢 §4.12～4.13 轉換留下的稽核時間線）；`/dashboard/bookings/[id]` 已加入住／退房按鈕與狀態歷程顯示。新增 §4.15 `POST /v2/bookings/{bookingId}/refund`（僅管理員；用於 no-show 自動取消中經核實為店家漏標入住的誤取消，見 DEF-352、PRD §17.4.6 Q15）。no-show 自動取消本身（DEF-352，`BookingNoShowService`）程式已完成，規則：入住時刻＋24 小時仍未入住 → 系統取消、不退款；上線開關 `app.booking-no-show.enabled` 預設關閉，待人工於生產環境驗證 DEF-350 後開啟（見 §7「已知限制」）。
>
> **⚠️ 修訂註記（Sprint 245，2026-10-04）**：入住與退房已實作（DEF-345，依 PRD Phase 1：付款即等同確認，不另設 `CONFIRMED`）。新增 §4.12 `POST /v2/dashboard/bookings/{bookingId}/check-in`（`PAID → CHECKED_IN`）與 §4.13 `POST …/check-out`（`CHECKED_IN → CHECKED_OUT`，同一交易內自動 `→ COMPLETED`）。`CONFIRMED` 仍不會產生；`PUT` 與取消對 `CONFIRMED` 的判斷是既有、不可達的程式碼，未刪除。`GET /v2/bookings/{id}/state-log`（PRD §9.7）與店家後台的入住按鈕仍未實作。
>
> **⚠️ 修訂註記（Sprint 244，2026-10-03；入住與退房已由 Sprint 245 實作，見上方）**：本文件所有寫「`CONFIRMED` 可更新」或「由店家操作」的敘述與實作不符：目前**沒有任何程式路徑**會寫入 `CONFIRMED`、`CHECKED_IN`、`CHECKED_OUT`、`COMPLETED`，可達的狀態只有 `CREATED`、`PAID`、`CANCELLED`（DEF-345）。Sprint 243 把「`CONFIRMED` 可更新」當成實測行為，那只有直接寫資料庫的測試才能產生這個狀態，此處更正。
>
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
| **描述** | 買家檢查房源在日期區間是否可訂、查看日曆、建立訂房、付款、取消；店家（店主／房東）檢視自己店鋪的訂房、代為修改住客資料或取消、標記入住與退房 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/bookings/...` |
| **多租戶隔離** | 訂房的 `tenantId` 是**房源所屬的店鋪**。買家只能讀寫自己的訂房；店主／房東只能存取自己店鋪的訂房（系統租戶的呼叫者**不算**「同店鋪」）；管理員不受限 |
| **範圍外** | 房東維護日曆（`/v2/dashboard/rooms/{id}/maintenance`）、訂房評價（`/v2/booking-reviews`）、房源管理（M02）、動態定價規則（M12）、合併結帳（[API_M05_Order.md](./api/API_M05_Order.md) §4.8） |

### 1.2 設計重點

1. **日曆以「晚」為單位**：區間 `[checkInDate, checkOutDate)`，**退房日不佔用**；晚數 = 退房日 − 入住日，**必須 ≥ 1**。日曆只存「有記錄的日期」（`AVAILABLE`／`BOOKED`／`BLOCKED`／`MAINTENANCE`），**沒有記錄的日期視為可訂**；超出房源「開放窗」的日期回 `NOT_OPEN`。
2. **併發與衝突**：建立訂房時對每一晚取 Redis 日期鎖（NO WAIT，取不到立刻 `E-4001`）、再以資料庫列鎖（`FOR UPDATE NOWAIT`）確認並標記 `BOOKED`；鎖在 `finally` 釋放。
3. **金額在建立當下算好**：`totalAmount = 房價（動態定價優先，否則 basePrice × 晚數） − 優惠券折扣`。付款期限 `paymentDueAt` = 建立時間 + 24 小時（`BOOKING_PAYMENT_TIMEOUT_HOURS`）；**歷史訂房（`null`）永不逾時**。
4. **狀態**：`CREATED → PAID → CHECKED_IN → CHECKED_OUT → COMPLETED`（付款即等同確認，PRD Phase 1；**不產生 `CONFIRMED`**，DEF-345），`CANCELLED` 為取消終態。**可取消**＝`CREATED`／`PAID`（入住後不可取消，回 `400 E-4007`）；**可更新**＝`CREATED`（實測：**已付款（`PAID`）與入住中的訂房不能更新**，回 `422 E-5010`）。`CHECKED_OUT` 只存在於同一次退房請求之內（自動完成），查得到的訂房不會停在這個狀態。
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
| US-M06-008 | 店家標記入住與退房 | `POST /v2/dashboard/bookings/{bookingId}/check-in`、`…/check-out` | ✅ 已實作（API＋店家後台按鈕，Sprint 246 DEF-350） |
| — | 訂房狀態日誌 | `GET /v2/dashboard/bookings/{bookingId}/state-log` | ✅ 已實作（PRD §9.7，Sprint 246 DEF-350） |
| — | 店家漏標入住的人工退款 | `POST /v2/bookings/{bookingId}/refund` | ✅ 已實作（僅管理員，Sprint 246 DEF-354；PRD §17.4.6 Q15） |

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
| **POST** | `/v2/dashboard/bookings/{bookingId}/check-in` | 店家標記入住（`PAID → CHECKED_IN`，入住日當天起） | `booking:update` | 200 |
| **POST** | `/v2/dashboard/bookings/{bookingId}/check-out` | 店家標記退房（`CHECKED_IN → CHECKED_OUT → COMPLETED`） | `booking:update` | 200 |
| **GET** | `/v2/dashboard/bookings/{bookingId}/state-log` | 訂房狀態機日誌（PRD §9.7） | `booking:read` | 200 |
| **POST** | `/v2/bookings/{bookingId}/refund` | 人工退款（僅管理員；no-show 誤取消的例外） | `booking:update` | 200 |

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

### 4.12 `POST /v2/dashboard/bookings/{bookingId}/check-in` — 店家標記入住

**權限**：`booking:update`（店主、Host、管理員；店員只能讀，見 §5）。擁有權**不同於** §4.7：買家本人不算，只放行管理員與**同店鋪**的商家（其他店鋪回 `403 E-1007`）。

**條件**：
- 訂房必須是 `PAID`，否則回 `422 E-5010`（未付款的 `CREATED` 也是）。
- 營運時區（Asia/Taipei）的今天必須 ≥ 入住日；入住日還沒到回 `422 E-5010`，訂房狀態不變。入住日已過仍可補記入住（本輪假設，PRD 未限制）。
- 以條件式 UPDATE 搶占狀態；併發搶不到回 `422 E-5010`。

**請求**：無本文。**回應**：`200`，`data` 為 `BookingResponse`（形狀同 §3.2），`status` 為 `CHECKED_IN`。**稽核**：`BOOKING_CHECKED_IN`（`PAID` → `CHECKED_IN`）。

**實測**：入住後，買家取消回 `400 E-4007`、改訂房回 `422 E-5010`、再次入住回 `422 E-5010`。

### 4.13 `POST /v2/dashboard/bookings/{bookingId}/check-out` — 店家標記退房

**權限**：同 §4.12。

**條件**：訂房必須是 `CHECKED_IN`，否則回 `422 E-5010`。不限日期（提早退房、入住日之後才退房都可以）。

**行為**：`CHECKED_IN → CHECKED_OUT`，接著在**同一交易內**自動 `CHECKED_OUT → COMPLETED`（「完成由退房後自動處理」，店家不需再操作）。回應的 `status` 是 `COMPLETED`。稽核兩筆：`BOOKING_CHECKED_OUT` 與 `BOOKING_COMPLETED`（後者的原因為 `auto-completed after check-out`）。日曆不釋放（已住的晚數維持 `BOOKED`）；結算模組目前不讀訂房，完成不觸發結算（SRD §6.3.4）。

**實測**：退房回 `200` 且 `status` 為 `COMPLETED`；再次退房回 `422 E-5010`；店員退房回 `403`。

### 4.14 `GET /v2/dashboard/bookings/{bookingId}/state-log` — 訂房狀態機日誌

**權限**：`booking:read`。擁有權同 §4.12（僅管理員與同店鋪商家，買家本人不算）。

**行為**：回傳 §4.12～4.13（與未來 DEF-352 no-show 自動取消）寫入 `audit_log` 的完整轉換時間線，依發生時間遞增排序。資料來源是共用的稽核紀錄表，不是訂單版獨立的 `OrderStateLog`，因此**沒有 `sequence` 欄位**。

**回應**：`200`，`data` 為陣列：

```json
[
  {
    "id": "f1e2d3c4-...",
    "bookingId": "adbf0eb2-...",
    "action": "BOOKING_CHECKED_IN",
    "fromStatus": "PAID",
    "toStatus": "CHECKED_IN",
    "changedBy": "11111111-...",
    "reason": null,
    "createdAt": "2026-10-09T02:00:00Z"
  }
]
```

無轉換紀錄（例如訂房剛建立、還沒入住過）回 `200` 與空陣列，不報錯。

### 4.15 `POST /v2/bookings/{bookingId}/refund` — 人工退款

**權限**：`booking:update`（Host／Store Owner／Admin 都有此權限，但服務層只放行管理員——比照 `order:update` ＋ BR-M07-05「退款實際上只有管理員」的既有模式，見 [API_M05_Order.md](./api/API_M05_Order.md) §7.4 的訂單版）。**非管理員回 `403 E-1007`**，即使是訂房所屬店鋪的店主。

**用途**：no-show 自動取消（DEF-352，PRD §17.4.6 Q15）預設不退款；若經核實是**店家漏標入住**造成的誤取消，管理員可用本端點例外退款。

**前置條件**（任一不符合都回 `422 E-5012`，不動用付款額度）：
- 訂房狀態為 `CANCELLED`；
- `canceledBy` 為 `SYSTEM`（買家自己取消、商家代為取消都不適用本端點，§4.7 的一般退款已處理那些情況）；
- `refundStatus` 為 `NONE`（已在等待自動退款或已退過的不可重複處理）。

**請求參數**（query，皆選填）：`amount`（未指定＝退實付全額；超過可退餘額回 `400 E-6009`）、`reason`（寫入稽核）。

**行為**：與付款層共用的退款核心一致（CAS 佔用退款額度、Stripe 付款會真的呼叫 Stripe）；成功後 `refundStatus` 直接 `NONE → COMPLETED`（管理員的即時動作，不經過 `PENDING` 中繼態）。稽核寫入 `BOOKING_MANUAL_REFUND`。

**回應**：`200`，`data` 形狀同 §4.8（`OrderPaymentState`，`orderId` 欄位裝的是訂房 id）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 非管理員 | 403 | `E-1007` |
| 找不到訂房 | 404 | `E-4006` |
| 不是「no-show 系統取消、尚未退款」 | 422 | `E-5012` |
| 找不到可退款的付款紀錄 | 404 | `E-6000` |
| 退款金額無效（超過可退餘額、負數、小數位過多） | 400 | `E-6009` |
| 額度被併發的另一次退款搶先 | 400 | `E-6009` |

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
| `POST …/check-in`、`…/check-out`（§4.12～4.13） | ❌ | 同店鋪 | 同店鋪 | ❌ | ✅ |
| `GET …/state-log`（§4.14） | ❌ | 同店鋪 | 同店鋪 | 同店鋪 | ✅ |
| `POST …/refund`（§4.15） | ❌ | ❌ | ❌ | ❌ | ✅（僅管理員） |

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
| `E-4007` | 400 | 此訂房無法取消（入住後也不行） |
| `E-5007`／`E-5008`／`E-5009` | 400 | 優惠碼無效／過期／達使用上限 |
| `E-5010` | 422 | 無效的訂房狀態（不可更新；不可入住／退房；入住日還沒到；併發搶不到，§4.12～4.13） |
| `E-5011` | 422 | 無效的付款狀態（不可付款） |
| `E-5012` | 422 | 無效的退款狀態（§4.15：不是「no-show 系統取消、尚未退款」） |
| `E-6000`／`E-6002`／`E-6003`／`E-6004`／`E-6005`／`E-6007` | 404／400／400／422／422・409／503 | 付款：找不到紀錄、Stripe 未啟用、已付款、Stripe 啟用後 Mock 不可用、冪等衝突、金流服務商錯誤 |
| `E-6009` | 400 | 無效的退款金額（§4.15：超過可退餘額、負數、小數位過多、或被併發的另一次退款搶先） |
| `E-9000` | 400 | 驗證錯誤（請求本文驗證帶 `errors[]`；`sortBy`／`sortDir` 無效、參數格式錯誤〔如日期無法解析〕不帶） |
| `E-9004`／`E-9005` | 400 | `Idempotency-Key` 不是 UUID v4／缺必填參數 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `sortBy`／`sortDir` 不改變順序 | repository 方法名寫死 `OrderByCreatedAtDesc`（`DEF-342`） |
| `E-3001`／`E-6002` 的預設訊息誤導 | 日曆參數無效回「無效的刊登類型」、Stripe 未啟用回「付款已取消」（`DEF-341`） |
| 已付款或入住中的訂房不能更新 | `PAID`、`CHECKED_IN` 回 `E-5010`；`CONFIRMED` 也可以更新，但本系統不會產生該狀態（DEF-345）；要改已付款的訂房請取消後重訂 |
| 舊版 `POST /v2/payments` 的訂房分支 | HTTP 打不到（`orderId` 是 `@NotNull`，只帶 `bookingId` 在驗證層回 400；Sprint 221 `DEF-303` (1)）；訂房請用 §4.8～4.10 |
| no-show 自動取消的上線開關預設關閉 | 程式已完成（`BookingNoShowService`＋`BookingService#cancelBookingIfNoShowDue`，PRD §17.4.6 Q15：入住時刻＋24 小時仍未入住 → 系統取消、不退款、買家站內通知），但 `app.booking-no-show.enabled` 預設 `false`；需人工於生產環境驗證 DEF-350（店家入住按鈕）後才開啟，否則店家介面還沒跟上就啟用，真實住客會被誤取消 |
| 入住日閘門 | 入住日還沒到不可入住（使用者於 Sprint 245 收尾確認保留；PRD 未明定，見 §4.12） |
| 人工退款僅管理員 | §4.15 刻意不開放給店主自助：店家不能自己核准退款給自己造成的誤取消，必須由管理員核實 |
| Stripe 路徑未對真實 Stripe 驗證 | 以 Mock／WireMock 驗證，從未對真實 Stripe 驗證（Sprint 221／227 紀錄） |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §5 訂房（日曆、開放窗、動態定價）、§7.3 權限矩陣、§9.16 商家端訂房、§15.2.5／Q14 取消與退款、US-005 取消通知、US-008／PC-005 單一商家 |
| 相關規格 | [API_M05_Order.md](./api/API_M05_Order.md)（訂單與付款狀態）、[API_M04_Cart.md](./API_M04_Cart.md)（購物車）、[API_Error_Codes.md](./API_Error_Codes.md) |
| 實作 | `BookingController`、`BookingPaymentController`、`DashboardBookingController`（`api/controller`）；`BookingService`（含 `checkIn`／`checkOut`，Sprint 245）、`RoomCalendarService`、`BookingRefundPolicy`、`PaymentStateService`（`core`） |
| 契約測試 | `BookingApiRealStackIntegrationTest`（真實 PostgreSQL＋Redis，含入住與退房）、`BookingServiceCheckInOutTest`（規則單元測試）、`RoomCalendarLockRealRedisIntegrationTest`（日期鎖）、`StoreSuspendedPaymentIntegrationTest`；路由漂移守門 `ApiRouteDocDriftTest` |
| 缺陷 | `DEF-338`、`DEF-339`、`DEF-340`、`DEF-343`（Sprint 243 已修）、`DEF-341`、`DEF-342`（已登記）；Sprint 239／242（`E-2010`）；`DEF-345`（Sprint 245 實作入住與退房；state-log 與店家後台按鈕待排） |
