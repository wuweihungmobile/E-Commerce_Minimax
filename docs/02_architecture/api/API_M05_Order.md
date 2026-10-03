# API 規格文件 - M05 訂單履約系統 / API Specification - M05 Order Fulfillment

> **文件版本**: v2.0（Sprint 243 依實作改寫）
> **建立日期**: 2026-04-09（v1.0）
> **最後更新**: 2026-10-03
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`OrderController`、`CheckoutController`、`OrderPaymentController`、`OrderService`、`OrderStateMachine`、`PaymentStateService`、`CombinedCheckoutService`、`OrderDto`、`CheckoutDto`、`OrderPaymentStateDto`、`GlobalExceptionHandler`；**每個回應形狀、狀態碼、錯誤碼與權限宣稱都用真實 PostgreSQL＋Redis＋完整 HTTP／JWT／權限鏈實測**（[OrderApiRealStackIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/OrderApiRealStackIntegrationTest.java)）
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`，**沒有**巢狀的 `error`，也**沒有**數字型的 `code`）
> **取代關係**: 本文件**取代** v1.0 全文（v1.0 的端點、請求格式與錯誤碼表與實作差距很大，見下方修訂註記）

> **⚠️ 修訂註記（Sprint 243）**：v1.0 與實作有六處落差，本版已更正：
> 1. **建立訂單不帶 `items[]`**：訂單由**購物車**結帳產生（`POST /v2/orders` 的請求只帶收件資訊；項目、單價、金額一律取自購物車並在結帳當下重新驗證計算）。v1.0 寫的 `items`、`paymentMethod`、`unitPrice` 並不存在；
> 2. **端點由 6 個改為實際的 16 個**：v1.0 列了不存在的 `GET /dashboard/orders`、`PUT /dashboard/orders/:id/status`（實際是 `GET /v2/orders/tenant`、`PATCH /v2/orders/{orderId}/status`），漏了狀態日誌、合併結帳與整組付款端點；
> 3. **冪等標頭是 `Idempotency-Key`**（UUID v4），不是 `X-Idempotency-Key`；
> 4. **訂單歸屬商品所屬的店鋪**（Sprint 237，`DEF-319`）：一次結帳只能結一家店鋪（`storeId`、`E-5020`）；店鋪暫停營業不能下單、也不能付款（`E-2010`，Sprint 239／242）；
> 5. **錯誤碼表全部換成實際的**：v1.0 的 `E-4021`／`E-4022`／`E-4023`／`E-4024`／`E-4041` 都不存在於後端；
> 6. **使用者輸入錯誤原本會回 500（Sprint 243 發現並修復）**：列表的 `sortBy`／`sortDir` 亂填原本是 `500 E-9900`（`DEF-339`）；建立訂單的回應與冪等重放原本把 `tenantId`／`userId`／`items[].listingId` 回成 `null`（`DEF-338`）。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M05 |
| **模組名稱** | 訂單履約 / Order Fulfillment |
| **描述** | 買家從購物車結帳建立訂單、付款（Mock 或 Stripe Checkout）、取消；店家（店主／賣家）檢視自己店鋪的訂單並推進出貨流程；管理員退款。含「商品＋房間」的合併結帳與付款狀態查詢 |
| **使用角色** | 買家（Buyer）下單、付款、取消；店主（Store Owner）／賣家（Seller）看店鋪的訂單並更新狀態；管理員（Admin／Super Admin）取消、退款。權限矩陣見 §6 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/orders/...` |
| **多租戶隔離** | 訂單的 `tenantId` 是**商品所屬的店鋪**（不是買家的租戶——沒有店鋪的消費者是系統租戶佔位值）。買家只能讀寫自己的訂單；店主／賣家只能讀寫自己店鋪的訂單（系統租戶的呼叫者**不算**「同店鋪」）；管理員不受限 |
| **範圍外** | 舊版 `POST /v2/payments`（相容端點，M07）、Stripe／LINE Pay webhook（`/v2/payments/webhook/*`）、物流、退貨（M11）、結算（M13）、訂房（M06，見 [API_M06_Booking.md](../API_M06_Booking.md)） |

### 1.2 設計重點

1. **訂單來自購物車，結帳一次一家店鋪**（PRD US-008／PC-005）。購物車（[API_M04_Cart.md](../API_M04_Cart.md)）可以放多家店鋪的商品；`POST /v2/orders` 只結一家：購物車只有一家店鋪的商品時可省略 `storeId`，**有多家店鋪時必填，否則 `400 E-5020`**。其餘店鋪（與 ROOM 項目）的項目留在購物車分開結帳。
2. **金額由後端重算**：單價、運費（該店鋪的運費模板）、優惠券折扣都在結帳當下重新驗證計算，不採信購物車的預估（PRD §9.5.1）。`totalAmount = 項目小計 + shippingFee − discountAmount`。
3. **狀態機**（§5）：`CREATED → PAID → CONFIRMED → SHIPPING → DELIVERED → COMPLETED`，可在出貨前取消；已付款的訂單取消後轉 `REFUNDING` 等待退款，退完轉 `REFUNDED`。`PAID`／`REFUNDED`／`REFUNDING` 只有付款子系統與取消流程能進入，**不能**用 `PATCH /status` 直接指定。
4. **取消有補償**：釋放預留庫存、退還優惠券額度；已付款者轉 `REFUNDING`，由排程自動退款（Sprint 226）。庫存**出貨才扣**（Sprint 218，PRD §6.7.3）。
5. **未付款 24 小時逾時自動取消**（Sprint 219）。已有成功付款、或在 24 小時內開始過 Stripe 結帳的訂單不會被逾時取消（避免收了錢訂單卻被取消）。
6. **店鋪暫停營業不能下單、也不能付款**（`E-2010`，422）：Sprint 239 擋建立、Sprint 242 擋所有「會收錢」的付款入口；**取消、退款、Stripe 入帳（錢已收）不擋**。付款狀態回應的 `storeOpen` 讓前端提前顯示「店鋪暫停營業」。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M05-001 | 買家建立訂單 | `POST /v2/orders`、`POST /v2/checkout/mixed` | ✅ 已實作 |
| US-M05-002 | 買家查詢訂單 | `GET /v2/orders`、`GET /v2/orders/{orderId}`、`GET /v2/orders/{orderId}/logs` | ✅ 已實作 |
| US-M05-003 | 買家付款 | `GET/POST /v2/orders/{orderId}/pay*`、`GET /v2/orders/{orderId}/payment` | ✅ 已實作（Mock；Stripe Checkout 需 `STRIPE_PAYMENT_ENABLED`） |
| US-M05-004 | 買家取消訂單 | `POST /v2/orders/{orderId}/cancel` | ✅ 已實作 |
| US-M05-005 | 店家處理訂單 | `GET /v2/orders/tenant`、`PATCH /v2/orders/{orderId}/status` | ✅ 已實作 |
| US-M05-006 | 管理員退款 | `POST /v2/orders/{orderId}/refund` | ✅ 已實作 |

---

## 2. API 端點總覽 / Endpoints Summary

| 方法 | 路徑（`/api` 之後） | 描述 | 權限 | 成功 |
|------|--------------------|------|------|:----:|
| **POST** | `/v2/orders` | 從購物車建立訂單（可帶 `Idempotency-Key`） | `order:create` | 201（冪等重送 200） |
| **GET** | `/v2/orders` | 買家自己的訂單列表（分頁） | `order:read` | 200 |
| **GET** | `/v2/orders/{orderId}` | 訂單詳情 | `order:read` | 200 |
| **GET** | `/v2/orders/tenant` | 店鋪收到的訂單列表（分頁、可依狀態篩選） | `order:read` | 200 |
| **PATCH** | `/v2/orders/{orderId}/status` | 更新訂單狀態（店家出貨流程） | `order:update` | 200 |
| **POST** | `/v2/orders/{orderId}/cancel` | 取消訂單（本人或管理員） | 已登入 | 200 |
| **GET** | `/v2/orders/{orderId}/logs` | 訂單狀態日誌 | 已登入 | 200 |
| **POST** | `/v2/checkout/mixed` | 合併結帳：同時建立訂單（PRODUCT）與訂房（ROOM） | `order:create` 且 `booking:create` | 201（冪等重送 200） |
| **GET** | `/v2/orders/{orderId}/payment` | 取得付款狀態 | `order:read` | 200 |
| **POST** | `/v2/orders/{orderId}/pay` | Mock 付款成功 | `order:create` 或 `order:update` | 200 |
| **POST** | `/v2/orders/{orderId}/pay/fail` | Mock 付款失敗（只記錄，不改訂單） | `order:create` 或 `order:update` | 200 |
| **POST** | `/v2/orders/{orderId}/refund` | 退款（全額或部分） | `order:update` ＋ 本人或管理員 | 200 |
| **POST** | `/v2/orders/{orderId}/pay/checkout` | 發起 Stripe Checkout，回前端重導網址 | `order:create` 或 `order:update` | 200 |
| **GET** | `/v2/orders/{orderId}/pay/checkout/return` | Stripe 回跳後確認付款 | `order:read` | 200 |
| **GET** | `/v2/orders/bookings/{bookingId}/payment` | 取得**訂房**的付款狀態（路由在訂單付款控制器，屬 M06） | `booking:read` | 200 |

未帶 token 回 `401 E-1000`；已登入但沒有該權限、或不是資源的擁有者／同店鋪／管理員回 `403 E-1007`。

---

## 3. 資料模型 / Data Models

### 3.1 Request

#### CreateOrderRequest — `POST /v2/orders`

```json
{
  "orderType": "PRODUCT",
  "storeId": "550e8400-e29b-41d4-a716-446655440099",
  "addressId": "550e8400-e29b-41d4-a716-446655440123",
  "shippingAddress": "台北市信義區測試路 1 號",
  "shippingRecipientName": "測試收件人",
  "shippingPhone": "0912345678",
  "notes": "請放管理室"
}
```

| 欄位 | 型別 | 必填 | 限制 | 說明 |
|------|------|:----:|------|------|
| `orderType` | string | 是 | `PRODUCT`／`ROOM` | `PRODUCT` 從購物車結帳（本文件）。`ROOM` 是舊路徑（前端沒有呼叫點），**請改用 [`POST /v2/bookings`](../API_M06_Booking.md)** |
| `storeId` | UUID | 否 | | 要結哪一家店鋪。購物車只有一家店鋪的商品時可省略；**有多家店鋪時必填**，否則 `E-5020` |
| `addressId` | UUID | 否 | 必須是呼叫者自己的地址 | 提供時**覆蓋**下面三個手動欄位（下單當下複製一份快照，日後編輯／刪除地址簿不影響已建立的訂單）；不是自己的地址 `E-8007` |
| `shippingAddress` | string | 否 | ≤ 500 字 | 收件地址 |
| `shippingRecipientName` | string | 否 | ≤ 200 字 | 收件人 |
| `shippingPhone` | string | 否 | ≤ 50 字 | 收件電話 |
| `notes` | string | 否 | | 備註 |

> `ROOM` 路徑另有 `listingId`、`checkInDate`、`checkOutDate`、`guestCount`、`guestName`、`guestPhone`、`guestEmail`、`specialRequests` 欄位；它建立的是 `orderType=ROOM` 的訂單而不是訂房（Booking），與 M06 的訂房流程無關，新整合不要使用。

標頭：`Idempotency-Key`（選填，UUID v4）——見 §4.1。

#### UpdateOrderStatusRequest — `PATCH /v2/orders/{orderId}/status`

```json
{ "targetStatus": "SHIPPING", "reason": "已交給物流" }
```

| 欄位 | 必填 | 說明 |
|------|:----:|------|
| `targetStatus` | 是 | 目標狀態（列舉名稱，見 §5） |
| `reason` | 否 | 原因，寫入狀態日誌 |

#### MixedCheckoutRequest — `POST /v2/checkout/mixed`

```json
{
  "shippingAddress": "台北市信義區測試路 1 號",
  "shippingRecipientName": "測試收件人",
  "shippingPhone": "0912345678",
  "guestCount": 2,
  "guestName": "合併住客",
  "guestPhone": "0912345678",
  "guestEmail": "guest@example.com",
  "specialRequests": "晚到",
  "promoCode": "SAVE20",
  "storeId": "550e8400-e29b-41d4-a716-446655440099"
}
```

| 欄位 | 必填 | 限制 | 說明 |
|------|:----:|------|------|
| `shippingAddress`／`shippingRecipientName`／`shippingPhone`／`addressId`／`notes` | 否 | 同 `CreateOrderRequest` | PRODUCT 側收件資訊 |
| `guestCount` | 是 | ≥ 1 | ROOM 側入住人數 |
| `guestName` | 是 | ≤ 200 字，不可空白 | 住客姓名 |
| `guestPhone` | 否 | `^[0-9]{8,15}$` | |
| `guestEmail` | 否 | Email 格式 | |
| `specialRequests` | 否 | ≤ 1000 字 | |
| `promoCode` | 否 | | 同時分攤到訂單與訂房兩側（依小計比例） |
| `storeId` | 否 | | 商品與房間必須屬於**同一家店鋪**；購物車含多家店鋪時必填，否則 `E-5020` |

房源與日期取自購物車的第一筆 ROOM 項目（不由請求重複帶入）。

### 3.2 Response

#### OrderResponse — 訂單詳情／建立／狀態更新／取消的 `data`

```json
{
  "id": "7b0240d6-41cf-4782-be7c-c860c386cdb4",
  "tenantId": "4bf82653-ac37-42bd-a4fc-7b2407512433",
  "userId": "8ccb35b6-73d8-4a95-a397-572b6ad2cab9",
  "orderType": "PRODUCT",
  "status": "CREATED",
  "totalAmount": 200.00,
  "shippingFee": 0.00,
  "promoCode": null,
  "discountAmount": 0.00,
  "currency": "TWD",
  "shippingAddress": "台北市信義區測試路 1 號",
  "shippingRecipientName": "測試收件人",
  "shippingPhone": "0912345678",
  "notes": null,
  "guestCount": null,
  "guestName": null,
  "guestPhone": null,
  "guestEmail": null,
  "items": [
    {
      "id": "6e7ffab5-b2f3-4d61-9b65-ecdf9a778035",
      "listingId": "313211db-277c-4743-9723-40deaf4c71a4",
      "listingTitle": "探針商品",
      "coverImageUrl": null,
      "skuId": null,
      "skuCode": null,
      "specName": null,
      "quantity": 2,
      "unitPrice": 100.00,
      "subtotal": 200.00
    }
  ],
  "createdAt": "2026-10-03T09:21:54.381690Z",
  "updatedAt": "2026-10-03T09:21:54.381692Z"
}
```

| 欄位 | 說明 |
|------|------|
| `tenantId` | 訂單歸屬的**店鋪**（商品所屬的租戶） |
| `userId` | 下單的買家 |
| `status` | `CREATED`／`PAID`／`CONFIRMED`／`SHIPPING`／`DELIVERED`／`COMPLETED`／`CANCELLED`／`REFUNDING`／`REFUNDED` |
| `shippingFee`／`promoCode`／`discountAmount` | 下單當下的運費、套用的促銷碼與折扣；`totalAmount` 已扣除折扣 |
| `guestCount`／`guestName`／… | 只有舊的 `orderType=ROOM` 訂單才有 |
| `items[].skuId`／`skuCode`／`specName` | 商品有規格（SKU）時才有 |

> **`DEF-338`（Sprint 243 修復）**：建立訂單、合併結帳的回應與冪等重放存下的回應，`tenantId`／`userId`／`items[].listingId` 原本是 `null`（同一個持久化脈絡剛建立的實體，唯讀影子欄位還沒有值）；`GET` 詳情從資料庫載入，本來就是對的。

#### OrderListResponse — `GET /v2/orders`、`GET /v2/orders/tenant` 的 `data.content[]`

```json
{
  "id": "16667a56-0544-40a4-b5cf-7f6dfa15102a",
  "orderType": "PRODUCT",
  "status": "CREATED",
  "totalAmount": 100.00,
  "currency": "TWD",
  "itemCount": 1,
  "shippingRecipientName": "測試收件人",
  "createdAt": "2026-10-03T09:21:54.580916Z"
}
```

`data` 是 **Spring `Page`**：`content[]`、`totalElements`、`totalPages`、`size`、`number`（頁碼，從 0 起）、`first`、`last`、`numberOfElements`、`empty`、`pageable`、`sort`。`shippingRecipientName` 供店家識別買家（買家自己看到的就是自己填的）。

#### StateLogResponse — `GET /v2/orders/{orderId}/logs` 的 `data[]`

```json
{
  "id": "57d04746-19e3-4c3e-b174-04c616e21952",
  "orderId": "7b0240d6-41cf-4782-be7c-c860c386cdb4",
  "sequence": 3,
  "fromStatus": "PAID",
  "toStatus": "CONFIRMED",
  "changedBy": "3be52604-e5bd-40d0-b4ec-a0437c8b5cb4",
  "reason": null,
  "createdAt": "2026-10-03T09:21:56.615061Z"
}
```

依 `sequence` 由小到大；第一筆 `fromStatus` 為 `null`（`CREATED`，原因 `Order created from cart`）。系統觸發（逾時取消、Stripe webhook）的 `changedBy` 為 `null`。

#### OrderPaymentState — 付款端點的 `data`

```json
{
  "orderId": "7b0240d6-41cf-4782-be7c-c860c386cdb4",
  "orderStatus": "PAID",
  "paymentId": "f20af634-141d-4b40-81e9-7e80fae62843",
  "paymentStatus": "SUCCESS",
  "transactionId": "MOCK-5DEF8387",
  "nextValidStates": "CONFIRMED,CANCELLED",
  "canPay": false,
  "canCancel": true,
  "canRefund": true,
  "paidAt": "2026-10-03T09:21:55.441340Z",
  "updatedAt": "2026-10-03T09:21:55.453968Z",
  "paymentProvider": "mock",
  "storeOpen": true,
  "refundedAmount": 0.00
}
```

| 欄位 | 說明 |
|------|------|
| `paymentStatus` | `PENDING`／`PROCESSING`／`SUCCESS`／`FAILED`／`PARTIALLY_REFUNDED`／`REFUNDED`；還沒有付款紀錄為 `null` |
| `canPay` | **訂單狀態**允許付款（只有 `CREATED`）。**不含店鋪狀態**——店鋪是否營業由 `storeOpen` 單獨表達 |
| `canCancel`／`canRefund` | 依訂單狀態：可取消＝`CREATED`／`PAID`／`CONFIRMED`；可退款＝`PAID`／`CANCELLED`／`REFUNDING` |
| `nextValidStates` | 逗號分隔的合法下一個狀態（狀態機，§5） |
| `paymentProvider` | `mock`（預設）或 `stripe`（`STRIPE_PAYMENT_ENABLED` 開啟）；前端據此顯示模擬付款按鈕或重導 Stripe |
| `storeOpen` | 這筆訂單所屬店鋪目前是否營業中（只有 `ACTIVE` 算營業）。`false`（暫停、終止、待審核、已駁回）時所有付款端點回 `422 E-2010`，只能取消（Sprint 242） |
| `refundedAmount` | 累計已退款金額；沒有付款紀錄為 `null` |

#### CheckoutSession — `POST /v2/orders/{orderId}/pay/checkout` 的 `data`

```json
{ "orderId": "…", "sessionId": "cs_test_…", "sessionUrl": "https://checkout.stripe.com/c/pay/cs_test_…" }
```

#### MixedCheckoutResponse — `POST /v2/checkout/mixed` 的 `data`

```json
{
  "order": { "…OrderResponse…" },
  "booking": { "…BookingResponse，見 API_M06_Booking.md…" },
  "promoCode": null,
  "totalDiscountAmount": 0
}
```

---

## 4. 訂單端點規格 / Order Endpoints

### 4.1 `POST /v2/orders` — 從購物車建立訂單

**權限**：`order:create`（Buyer、Store Owner、Admin、Super Admin 持有）。

**標頭**：`Idempotency-Key`（選填，UUID v4）。

- 帶了：第一次 `201`，之後**同一把鍵**重送回 `200` 與**同一張訂單**（不重複建立）；上一次還在處理中回 `409 E-6005`；格式不是 UUID v4 回 `400 E-9004`；建立失敗（拋例外）時鍵會被清除，客戶端可以安全重試。
- 不帶：維持原行為（不建議）。
- **不解決**兩個沒有共用鍵的獨立請求對同一購物車的併發競態。

**成功 `201`**：`data` 是 `OrderResponse`，`message` 是 `"Order created successfully"`；已結帳的項目從購物車移除，其餘（ROOM、其他店鋪）留著。

**錯誤**

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 購物車是空的／沒有 PRODUCT 項目 | 400 | `E-5004` |
| 購物車含多家店鋪的商品但沒有指定 `storeId`（或指定的店鋪沒有項目） | 400 | `E-5020` |
| `orderType` 不是 `PRODUCT`／`ROOM` | 422 | `E-3001` |
| 缺 `orderType`／欄位超過長度 | 400 | `E-9000`（帶 `errors[]`） |
| 店鋪暫停營業（非 `ACTIVE`） | 422 | `E-2010` |
| 商品不存在／未上架 | 404／422 | `E-3000`／`E-3002` |
| 庫存不足 | 400 | `E-3004` |
| 促銷碼失效、售罄、超過每人限用 | 400 | `E-5007`／`E-5008`／`E-5009` |
| `addressId` 不是自己的地址 | 403 | `E-8007` |
| 沒帶 token | 401 | `E-1000` |

### 4.2 `GET /v2/orders` — 買家訂單列表

**權限**：`order:read`。只回**呼叫者自己**下的訂單。

| 參數 | 預設 | 說明 |
|------|------|------|
| `page` | 0 | 頁碼（負數視為 0） |
| `size` | 20 | 每頁筆數，**上限 100**（超過截為 100，小於 1 視為 1） |
| `sortBy` | `createdAt` | 允許：`createdAt`、`updatedAt`、`totalAmount`、`status`；其他回 `400 E-9000` |
| `sortDir` | `DESC` | `asc`／`desc`（不分大小寫）；其他回 `400 E-9000` |

> `sortBy`／`sortDir` 亂填原本是 `500 E-9900`（`DEF-339`，Sprint 243 修復）。**注意**：repository 方法名寫死 `OrderByCreatedAtDesc`，Pageable 的排序只是次要排序，所以這兩個參數目前**實際上不改變順序**（永遠建立時間新到舊）——`DEF-342`，已登記。

**成功 `200`**：`data` 是 `Page<OrderListResponse>`。

### 4.3 `GET /v2/orders/{orderId}` — 訂單詳情

**權限**：`order:read`，且是**訂單本人**、**該店鋪**的店主／賣家（呼叫者的租戶是真實店鋪且等於訂單的 `tenantId`）或**管理員**。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂單不存在 | 404 | `E-5000` |
| 別的買家、別家店鋪的人 | 403 | `E-1007` |

### 4.4 `GET /v2/orders/tenant` — 店鋪收到的訂單

**權限**：`order:read`。回**呼叫者所屬店鋪**的訂單，參數同 §4.2，另加：

| 參數 | 說明 |
|------|------|
| `status` | 選填，依訂單狀態篩選（列舉名稱）；不認得的值回 `422 E-5001` |

**沒有店鋪的呼叫者（一般買家，租戶是系統租戶佔位值）回空頁**（`200`、`totalElements` 0），不查詢、不報錯——避免把系統租戶底下的歷史訂單（含收件人）交給任一登入者（Sprint 232）。路徑是 `/tenant` 而不是 `/dashboard/orders`，因為後者已被訂單統計端點佔用。

### 4.5 `PATCH /v2/orders/{orderId}/status` — 更新訂單狀態

**權限**：`order:update`（Seller、Store Owner、Admin、Super Admin 持有；**Buyer 沒有，回 `403 E-1007`**），且是訂單本人、同店鋪或管理員。

請求：`UpdateOrderStatusRequest`。成功 `200`，`data` 是更新後的 `OrderResponse`。

**限制**（都回 `422 E-5001`）：

- 不在狀態機允許的轉換內（例如 `CREATED → CONFIRMED`；§5）。
- `PAID`、`REFUNDED` 只能由付款子系統設定（`DEF-245`——不對任何角色例外，含管理員；否則可以偽造已付款來灌業績）。
- `REFUNDING` 只能由取消流程進入（`DEF-301`）；要取消已付款訂單請指定 `CANCELLED`，補償後會自動轉 `REFUNDING`。
- 不認得的狀態值。
- 兩個併發請求搶同一個轉換：輸的一邊 `E-5001`（`Order status changed concurrently, please retry`），可重試。

**副作用**：轉 `SHIPPING` 時**扣庫存**（出貨才扣，`DEF-303`）；轉 `CANCELLED` 時與 §4.6 相同的補償（釋放預留庫存、退還優惠券額度，已付款者轉 `REFUNDING`）。缺 `targetStatus` 回 `400 E-9000`。

### 4.6 `POST /v2/orders/{orderId}/cancel` — 取消訂單

**權限**：已登入（沒有 authority 檢查），服務層限**訂單本人或管理員**（`ROLE_ADMIN`／`ROLE_SUPER_ADMIN`）。**店主／賣家要取消請用 `PATCH …/status` → `CANCELLED`**（呼叫這個端點回 `403 E-1007`）。

| 參數 | 說明 |
|------|------|
| `reason` | 選填 query 參數，寫入狀態日誌 |

成功 `200`，`data` 是 `OrderResponse`（`status` 為 `CANCELLED`，已付款者在補償後為 `REFUNDING`）。只有 `CREATED`／`PAID`／`CONFIRMED` 可取消；出貨後（`SHIPPING` 起）請走退貨／退款流程。

**補償**：釋放預留庫存、退還優惠券額度（合併結帳的券要**兩邊都取消**才退還）；已付款（`PAID`／`CONFIRMED`）者轉 `REFUNDING`，由排程自動退款。**店鋪暫停營業時仍可取消**（Sprint 239／242）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂單不存在 | 404 | `E-5000` |
| 不是本人也不是管理員 | 403 | `E-1007` |
| 已取消、已出貨、已完成等不可取消的狀態（含併發取消輸的一邊） | 400 | `E-5002` |

### 4.7 `GET /v2/orders/{orderId}/logs` — 訂單狀態日誌

**權限**：已登入，且是訂單本人、同店鋪或管理員（別人 `403 E-1007`）。`data` 是 `StateLogResponse[]`，依 `sequence` 遞增。

### 4.8 `POST /v2/checkout/mixed` — 合併結帳

**權限**：**同時**持有 `order:create` 與 `booking:create`。標頭 `Idempotency-Key`（選填，規則同 §4.1）。

購物車須同時有 **PRODUCT 與 ROOM** 項目（否則走 `POST /v2/orders`／`POST /v2/bookings` 單一類型結帳），且商品與房間屬於**同一家店鋪**。一次只結**第一筆 ROOM 項目**。訂單與訂房在同一個交易內建立，優惠券額度在兩側都成功後**統一佔用一次**，折扣依小計比例分攤。

成功 `201`（冪等重送 `200`），`data` 是 `MixedCheckoutResponse`（訂單與訂房都帶非 `null` 的 `tenantId`／`userId`／`listingId`／`roomListingId`，`DEF-338`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 購物車沒有同時含 PRODUCT 與 ROOM（或該店鋪沒有兩種項目） | 400 | `E-5004` |
| 多家店鋪沒指定 `storeId` | 400 | `E-5020` |
| 缺 `guestName`／`guestCount` 或格式不對 | 400 | `E-9000`（帶 `errors[]`） |
| 店鋪暫停營業 | 422 | `E-2010` |
| 日期被訂走 | 400 | `E-4001` |
| 人數超過房源上限／退房不晚於入住 | 400 | `E-4005`／`E-4003` |

---

## 5. 訂單狀態機 / Order State Machine

```
CREATED ──付款──▶ PAID ──店家確認──▶ CONFIRMED ──出貨──▶ SHIPPING ──▶ DELIVERED ──▶ COMPLETED
   │               │                     │
   └──取消──▶ CANCELLED ◀──取消──────────┘       （PAID／CONFIRMED 取消後）
                  │
                  └──已付款者自動──▶ REFUNDING ──退款完成──▶ REFUNDED
```

| 目前狀態 | 可轉到（`PATCH /status`） | 觸發 |
|----------|---------------------------|------|
| `CREATED` | `CANCELLED`（`PAID` 只由付款子系統） | 買家付款／取消、逾時 24 小時自動取消 |
| `PAID` | `CONFIRMED`、`CANCELLED` | 店家確認、取消 |
| `CONFIRMED` | `SHIPPING`、`CANCELLED` | 店家出貨（扣庫存）、取消 |
| `SHIPPING` | `DELIVERED` | 送達（出貨後**不可**取消） |
| `DELIVERED` | `COMPLETED` | 完成（終態） |
| `CANCELLED` | （`REFUNDING` 只由取消流程進入） | 已付款者取消後自動轉 |
| `REFUNDING` | （`REFUNDED` 只由付款子系統） | 自動退款完成、Stripe 退款 webhook |
| `COMPLETED`、`REFUNDED` | — | 終態 |

---

## 6. 權限矩陣 / Permission Matrix

| 端點 | Buyer | Seller | Store Owner | Store Staff | Admin／Super Admin |
|------|:----:|:----:|:----:|:----:|:----:|
| `POST /v2/orders`、`POST /v2/checkout/mixed` | ✅ | ❌ | ✅（`order:create`；**店主沒有購物車權限，實務上無法結帳**） | ❌ | ✅ |
| `GET /v2/orders`（自己的） | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /v2/orders/{id}`、`…/logs` | 本人 | 同店鋪 | 同店鋪 | 同店鋪（`order:read`） | ✅ |
| `GET /v2/orders/tenant` | 空頁 | ✅ | ✅ | ✅ | 呼叫者的租戶是真實店鋪才有資料，否則空頁 |
| `PATCH …/status` | ❌（403） | ✅（同店鋪） | ✅（同店鋪） | ❌ | ✅ |
| `POST …/cancel` | 本人 | ❌（403） | ❌（403） | ❌ | ✅ |
| `POST …/pay`、`…/pay/fail`、`…/pay/checkout` | 本人 | ❌ | ❌ | ❌ | ✅ |
| `POST …/refund` | ❌（403） | ❌（403） | ❌（403） | ❌ | ✅ |

> **退款端點實際上只有管理員能成功**：它要求 `order:update` **且**「訂單本人或管理員」。Buyer 持有「本人」但沒有 `order:update`；店主／賣家持有 `order:update` 但不是訂單本人。這是實測行為（兩者都回 `403 E-1007`）；店主沒有自己的退款入口——取消已付款訂單後由排程自動退款。

---

## 7. 付款端點規格 / Payment Endpoints

付款、取消、退款共用同一個店鋪營業判斷：**非 `ACTIVE` 的店鋪不能收款**。所有「會收錢」的入口——Mock 付款、Stripe Checkout 發起（在呼叫 Stripe **之前**）、舊版 `POST /v2/payments`——回 `422 E-2010`；守門放在既有檢查之後（已取消的訂單仍回 `E-5011`，已付款的回 `E-6003`／`E-5011`）。**不擋**：取消、退款、Mock 付款失敗（不收錢）、Stripe 回跳確認與 webhook（錢已經在 Stripe 收了，擋掉就是收了錢卻不記帳；停權前已建立的 Checkout Session 若在停權後才付完，仍入帳為已付款，最長 24 小時）。

### 7.1 `GET /v2/orders/{orderId}/payment` — 付款狀態

**權限**：`order:read`，本人或管理員（別人 `403 E-1007`；不存在 `404 E-5000`）。`data` 是 `OrderPaymentState`（尚未付款：`paymentId`／`paymentStatus` 為 `null`、`canPay` 為 `true`、`nextValidStates` 為 `PAID,CANCELLED`）。

### 7.2 `POST /v2/orders/{orderId}/pay` — Mock 付款成功

**權限**：`order:create` 或 `order:update`（Buyer 持有前者；DEF-298 前買家付不了款）。本人或管理員。訂單 `CREATED → PAID`，留一筆 `SUCCESS` 的 Mock 付款。**只有未啟用 Stripe 時可用**（啟用後一律拒絕，否則買家不必付錢就能把訂單標成已付款）。

成功 `200`，`message` 是 `"Payment successful"`，`data` 是 `OrderPaymentState`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂單不存在／不是本人 | 404／403 | `E-5000`／`E-1007` |
| 已啟用 Stripe（Mock 不可用） | 422 | `E-6004` |
| 訂單不是 `CREATED`（已付款、已取消…；含重複付款） | 422 | `E-5011` |
| 已有成功付款紀錄（併發的另一邊） | 400 | `E-6003` |
| **店鋪暫停營業**（Sprint 242） | 422 | `E-2010` |

### 7.3 `POST /v2/orders/{orderId}/pay/fail` — Mock 付款失敗

標頭／權限同 §7.2；選填 query 參數 `reason`。只留一筆 `FAILED` 的付款紀錄，**訂單維持 `CREATED`**（可重試）。不受店鋪營業狀態限制。

### 7.4 `POST /v2/orders/{orderId}/refund` — 退款

**權限**：`order:update` ＋ 訂單本人或管理員（實際上只有管理員，見 §6）。

| 參數 | 說明 |
|------|------|
| `amount` | 選填，退款金額。**未指定＝退剩餘全額**；指定時須為正數、不超過剩餘可退額度、至多 2 位小數 |
| `reason` | 選填 |

- 達到付款金額才轉 `REFUNDED`（訂單也轉 `REFUNDED`）；否則付款轉 `PARTIALLY_REFUNDED`，**訂單狀態不變**（持續履約）。可重複呼叫直到退完。
- **運費不參與部分退款計算**，只退商品金額（PO 決策 2026-07-04）。
- 付款方式為 Stripe 一律經 Stripe（冪等鍵＝付款意圖＋退款前累計額＋本次金額）；先以 compare-and-swap 佔用額度才呼叫 Stripe，併發的第二次請求不會重複退款。
- 成功 `200`，`message` 是 `"Refund processed"`，`data` 是 `OrderPaymentState`（`refundedAmount` 為累計）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂單不是 `PAID`／`CANCELLED`／`REFUNDING` | 422 | `E-5012` |
| 找不到成功的付款紀錄 | 404 | `E-6000` |
| 金額 ≤ 0、超過剩餘、超過 2 位小數、與併發退款衝突 | 422 | `E-6009` |
| Stripe 退款失敗／缺付款意圖 | 400 | `E-6001` |
| 不是管理員 | 403 | `E-1007` |

### 7.5 `POST /v2/orders/{orderId}/pay/checkout` — 發起 Stripe Checkout

**權限**：同 §7.2。需要 `STRIPE_PAYMENT_ENABLED` 開啟（**未啟用回 `400 E-6002`**，預設訊息是「付款已取消」，實際原因是 Stripe 未啟用）。建立 `PROCESSING` 的付款紀錄與 Stripe Checkout Session，成功 `200`，`data` 是 `CheckoutSession`（前端導向 `sessionUrl`）。同一張訂單再次發起（買家在 Stripe 頁按返回再按一次）會拿回同一個 Session，不重複寫付款紀錄；查與寫之間的極小競態回可重試的 `422 E-6005`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| Stripe 未啟用 | 400 | `E-6002` |
| 訂單不是 `CREATED` | 422 | `E-5011` |
| 已有成功付款 | 400 | `E-6003` |
| **店鋪暫停營業**（Stripe **完全不會被呼叫**） | 422 | `E-2010` |
| 金流服務商錯誤 | 503 | `E-6007` |

### 7.6 `GET /v2/orders/{orderId}/pay/checkout/return` — Stripe 回跳確認

**權限**：`order:read`，本人或管理員。必填 query 參數 `sessionId`。以 Session 向 Stripe 查詢，已付款則與 webhook **共用同一個入帳核心**（付款 `SUCCESS`＋訂單 `PAID`，冪等）；尚未付款則原樣回傳狀態。**不受店鋪營業狀態限制**（錢已收，必須入帳）。若付款成功時訂單已被取消，系統把訂單轉 `REFUNDING` 交給自動退款並留稽核紀錄。Stripe 查詢失敗（含 Stripe 未設定）回 `503 E-6007`。

### 7.7 `GET /v2/orders/bookings/{bookingId}/payment` — 訂房付款狀態

回應形狀與 §7.1 相同，但 `orderId`／`orderStatus` 欄位裝的是**訂房的 id 與狀態**（`CREATED`／`PAID`／…）。權限 `booking:read`，本人或管理員（別人 `403 E-1007`；不存在 `404 E-4006`）。訂房的付款動作在 [`/v2/bookings/{bookingId}/pay*`](../API_M06_Booking.md)。

---

## 8. 錯誤碼 / Error Codes

M05 常見錯誤碼（完整對照表與預設訊息見 [API_Error_Codes.md](../API_Error_Codes.md)）：

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1000` | 401 | 沒帶 token |
| `E-1007` | 403 | 沒有權限、不是本人／同店鋪／管理員 |
| `E-2010` | 422 | 店鋪暫停營業：下單、付款（Sprint 239／242） |
| `E-3001` | 422 | `orderType` 無效 |
| `E-3000`／`E-3002`／`E-3004` | 404／422／400 | 商品不存在／未上架／庫存不足 |
| `E-5000` | 404 | 找不到訂單 |
| `E-5001` | 422 | 無效的訂單狀態／不允許的轉換 |
| `E-5002` | 400 | 此訂單無法取消 |
| `E-5004` | 400 | 購物車是空的 |
| `E-5007`／`E-5008`／`E-5009` | 400 | 優惠碼無效／過期／達使用上限 |
| `E-5011` | 422 | 無效的付款狀態（訂單不可付款） |
| `E-5012` | 422 | 無效的退款狀態 |
| `E-5020` | 400 | 訂單的商品必須屬於同一家店鋪，請指定 `storeId` |
| `E-6000` | 404 | 找不到付款紀錄 |
| `E-6001` | 400 | 付款（退款）失敗 |
| `E-6002` | 400 | Stripe 未啟用（預設訊息「付款已取消」） |
| `E-6003` | 400 | 付款已處理完成 |
| `E-6004` | 422 | Stripe 啟用後 Mock 付款不可用 |
| `E-6005` | 422／409 | 冪等鍵已被使用／處理中 |
| `E-6007` | 503 | 金流服務商錯誤 |
| `E-6009` | 422 | 無效的退款金額 |
| `E-8007` | 403 | 無權使用此收件地址 |
| `E-9000` | 400 | 驗證錯誤（帶 `errors[]`；`sortBy`／`sortDir` 無效時不帶） |
| `E-9004` | 400 | `Idempotency-Key` 不是 UUID v4 |

---

## 9. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `sortBy`／`sortDir` 不改變順序 | repository 方法名寫死 `OrderByCreatedAtDesc`，列表永遠建立時間新到舊（`DEF-342`） |
| `E-6002` 的訊息誤導 | Stripe 未啟用時回的是「付款已取消」（`DEF-341`） |
| 退款端點只有管理員 | 店主沒有自己的退款入口（見 §6）；取消已付款訂單後由排程自動退款 |
| Stripe 路徑未對真實 Stripe 驗證 | 發起、回跳、webhook、退款的行為以 Mock／WireMock 驗證，從未對真實 Stripe 驗證（Sprint 207／226 紀錄） |
| 舊的 `orderType=ROOM` 訂單路徑 | 前端沒有呼叫點，與訂房（Booking）無關，新整合請用 [M06](../API_M06_Booking.md) |
| 兩個沒有共用冪等鍵的併發結帳 | 對同一購物車仍有競態（`DEF-285` 後續範圍） |

---

## 10. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §4.5 訂單管理、§6.7.3 庫存扣減時機、§7.3 權限矩陣、§9.5.1 促銷碼重驗、§15.2.5 取消補償、US-008／PC-005 單一商家訂單 |
| 相關規格 | [API_M04_Cart.md](../API_M04_Cart.md)（購物車）、[API_M06_Booking.md](../API_M06_Booking.md)（訂房）、[API_Error_Codes.md](../API_Error_Codes.md) |
| 實作 | `OrderController`、`CheckoutController`、`OrderPaymentController`（`api/controller`）；`OrderService`、`OrderStateMachine`、`PaymentStateService`、`PaymentStoreGuard`、`CombinedCheckoutService`（`core`） |
| 契約測試 | `OrderApiRealStackIntegrationTest`（真實 PostgreSQL＋Redis）、`StoreSuspendedPaymentIntegrationTest`（店鋪暫停營業）；路由漂移守門 `ApiRouteDocDriftTest` |
| 缺陷 | `DEF-338`、`DEF-339`（Sprint 243 已修）、`DEF-341`、`DEF-342`（已登記）；Sprint 239／242（`E-2010`） |
