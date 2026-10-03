# API 規格文件 - M04 購物車模組 / API Specification - M04 Cart Module

> **文件版本**: v2.0（Sprint 241 依實作改寫）
> **建立日期**: 2026-04-28（v1.0，Sprint 4）
> **最後更新**: 2026-10-03
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`CartController`、`RedisCartService`、`CartStoreSelector`、`CartDto`、`GlobalExceptionHandler`
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](./API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`，**沒有**巢狀的 `error`，也**沒有**數字型的 `code`）
> **取代關係**: 本文件**取代** v1.0 全文（v1.0 只描述 Sprint 4 的 4 個端點與單一店鋪的心智模型）

> **⚠️ 修訂註記（Sprint 241）**：v1.0 與實作有五處落差，本版已更正：
> 1. 端點由 5 個改為實際的 **9 個**——v1.0 列了不存在的 `GET /cart/items`，卻漏了清空購物車、取得件數、套用／移除／驗證優惠券；
> 2. **成功狀態碼是 `200`**（v1.0 寫加入購物車回 `201`）；
> 3. **購物車可以放多家店鋪的項目，結帳一次只能結一家店鋪**（Sprint 237，`DEF-319`）：回應多了依店鋪分組的 `stores[]`，優惠券與運費都以店鋪為單位，多家店鋪時要帶 `storeId`（否則 `E-5020`）；`storeActive` 標示店鋪是否營業中（Sprint 239，結帳會擋下非營業中的店鋪，`E-2010`）；
> 4. 數量驗證失敗的錯誤碼是 **`E-9000`（400，帶 `errors[]`）**，不是 v1.0 寫的 `E-5006`（`E-5006` 是結帳時的「無效的數量」，422）；
> 5. **使用者輸入錯誤原本會回 500（Sprint 241 發現並修復，`DEF-335`）**：加入不存在的刊登項目、房源沒給日期、退房不晚於入住，原本丟通用的 `IllegalArgumentException`，落入 `handleGenericException` 回 `500 E-9900`；本版與實作都是 v1.0 原本就寫的契約（`E-3000` 404／`E-4003` 400／`E-4004` 400）。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M04 |
| **模組名稱** | 購物車 / Cart |
| **描述** | 買家把商品（PRODUCT）與房源（ROOM）放進購物車、檢視、改數量、移除、清空，並在購物車內套用／驗證店鋪的優惠券、預覽運費與應付金額 |
| **使用角色** | 只有 **BUYER**（與 SUPER_ADMIN）持有 `cart:read`／`cart:update`／`cart:delete`；店主、店員、賣家、房東、一般管理員都沒有。沒有店鋪的 SELLER／HOST 自 Sprint 240 起以 BUYER 身分簽發（`DEF-326`），所以也能使用購物車 |
| **儲存** | Redis（Hash，鍵 `cart:{userId}:{buyerTenantId}`；每次加入或修改項目都把 TTL 重設為 **30 天**） |
| **多租戶隔離** | 購物車以（使用者, 買家租戶）為鍵。沒有店鋪的消費者的租戶是系統租戶佔位值，所以實際上是**每位使用者一個購物車**；每位買家只能操作自己的購物車 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/cart/...` |

### 1.2 設計重點

1. **購物車是跨店鋪的，結帳是單店鋪的**（PRD US-008／PC-005：Phase 1 不支援跨商家訂單）。項目屬於哪家店鋪，由**該商品／房源所屬的租戶**決定（不是買家的租戶）；回應的每個項目帶 `storeId`／`storeName`／`storeActive`，頂層 `stores[]` 是依店鋪分組的結帳摘要。
2. **優惠券屬於店鋪**：在購物車內以「某家店鋪」為單位驗證、套用、移除；兩家店鋪可以各自套用自己的券，互不覆蓋。
3. **運費屬於店鋪**：該店鋪 **PRODUCT 項目小計**走該店鋪自己的運費模板；純 ROOM 的購物車運費為 0。
4. **顯示金額不是收款依據**：購物車的運費與折扣是預估（折扣計算失敗時靜默退回原價），訂單金額一律以結帳當下重新驗證與計算的結果為準（PRD §9.5.1）。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M04-001 | 加入購物車 | `POST /cart/items` | ✅ 已實作 |
| US-M04-002 | 檢視購物車 | `GET /cart`、`GET /cart/count` | ✅ 已實作 |
| US-M04-003 | 更新數量 | `PUT /cart/items/{cartItemKey}` | ✅ 已實作 |
| US-M04-004 | 移除商品 | `DELETE /cart/items/{cartItemKey}`、`DELETE /cart` | ✅ 已實作 |
| （Sprint 100／101／237） | 優惠券與運費預覽 | `POST /cart/apply-promo`、`DELETE /cart/promo`、`GET /cart/validate-promo` | ✅ 已實作 |

---

## 2. API 端點總覽 / Endpoints Summary

| 方法 | 路徑（`/api` 之後） | 描述 | 權限 | 成功 |
|------|--------------------|------|------|:----:|
| **GET** | `/v2/cart` | 取得購物車（含依店鋪分組的摘要、運費、優惠券折扣） | `cart:read` | 200 |
| **GET** | `/v2/cart/count` | 取得購物車**總件數**（各項目數量的總和，不是項目數） | `cart:read` | 200 |
| **POST** | `/v2/cart/items` | 加入購物車 | `cart:update` | 200 |
| **PUT** | `/v2/cart/items/{cartItemKey}` | 更新項目數量 | `cart:update` | 200 |
| **DELETE** | `/v2/cart/items/{cartItemKey}` | 移除項目 | `cart:update` | 200 |
| **DELETE** | `/v2/cart` | 清空購物車 | `cart:delete` | 200 |
| **POST** | `/v2/cart/apply-promo` | 在某家店鋪套用優惠券 | `cart:update` | 200 |
| **DELETE** | `/v2/cart/promo` | 移除某家店鋪已套用的優惠券 | `cart:update` | 200 |
| **GET** | `/v2/cart/validate-promo` | 驗證優惠券（不套用） | `cart:read` | 200 |

未帶 token 回 `401 E-1000`；已登入但沒有該權限回 `403 E-1007`。

---

## 3. 資料模型 / Data Models

### 3.1 Request

#### AddItemRequest — `POST /v2/cart/items`

```json
{
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "skuId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 2,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
```

| 欄位 | 型別 | 必填 | 限制 | 說明 |
|------|------|:----:|------|------|
| `listingId` | UUID | 是 | | 商品（PRODUCT）或房源（ROOM）的刊登 ID |
| `skuId` | UUID | 否 | 必須屬於 `listingId` | 規格。**不屬於該刊登項目的 `skuId` 被靜默當成沒帶**（不報錯、退回無規格，`DEF-237`：防止拿他人的 SKU 加入購物車） |
| `quantity` | int | 是 | 1～999 | 本次加入的數量（會與既有同一項目**累加**） |
| `startDate`／`endDate` | date | ROOM 必填 | `endDate` 必須晚於 `startDate` | PRODUCT 會忽略 |

#### UpdateItemRequest — `PUT /v2/cart/items/{cartItemKey}`

```json
{ "quantity": 5 }
```

`quantity` 必填、1～999。是**取代**目前數量，不是累加。

#### ApplyPromoRequest — `POST /v2/cart/apply-promo`

```json
{ "promoCode": "SAVE20", "storeId": "550e8400-e29b-41d4-a716-446655440099" }
```

| 欄位 | 必填 | 說明 |
|------|:----:|------|
| `promoCode` | 是 | 優惠券代碼（不分大小寫，回應會轉成大寫） |
| `storeId` | 否 | 套用在哪家店鋪。購物車只有一家店鋪的項目時可省略；**有多家店鋪時必填**，否則 `E-5020` |

#### `cartItemKey`

路徑參數。格式是 **`{listingId}`** 或 **`{listingId}:{skuId}`**（取自回應項目的 `cartItemKey`）。**日期不是 key 的一部分**：同一個刊登項目（＋SKU）在購物車裡只會有一個項目，重複加入會**累加數量**，ROOM 的日期則以**最後一次加入**為準。格式無法解析或找不到都回 `404 E-5005`。

### 3.2 Response

#### CartResponse — `GET /v2/cart` 的 `data`

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440100",
  "cartId": "cart:550e8400-e29b-41d4-a716-446655440100:00000000-0000-0000-0000-000000000001",
  "items": [
    {
      "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001",
      "listingId": "550e8400-e29b-41d4-a716-446655440000",
      "listingName": "手沖咖啡杯",
      "coverImageUrl": "https://…/cup.jpg",
      "skuId": "550e8400-e29b-41d4-a716-446655440001",
      "skuCode": "CUP-RED",
      "specName": "紅色",
      "quantity": 2,
      "unitPrice": 450.00,
      "subtotal": 900.00,
      "listingType": "PRODUCT",
      "addedAt": "2026-10-03T02:00:00Z",
      "storeId": "550e8400-e29b-41d4-a716-446655440099",
      "storeName": "山居選物",
      "storeActive": true,
      "startDate": null,
      "endDate": null,
      "originalUnitPrice": null,
      "discountAmount": null,
      "appliedRuleName": null,
      "priceAdjustmentType": null
    }
  ],
  "totalItems": 2,
  "totalAmount": 900.00,
  "shippingFee": 60.00,
  "appliedPromoCode": "SAVE20",
  "discountAmount": 20.00,
  "finalAmount": 940.00,
  "currency": "TWD",
  "updatedAt": "2026-10-03T02:00:00Z",
  "stores": [
    {
      "storeId": "550e8400-e29b-41d4-a716-446655440099",
      "storeName": "山居選物",
      "storeActive": true,
      "itemCount": 2,
      "totalAmount": 900.00,
      "shippingFee": 60.00,
      "appliedPromoCode": "SAVE20",
      "discountAmount": 20.00,
      "finalAmount": 940.00
    }
  ]
}
```

| 欄位 | 說明 |
|------|------|
| `totalItems` | **總件數**（各項目 `quantity` 的總和）。JSON 欄位名是 `totalItems`（Java 欄位名 `itemCount`） |
| `totalAmount` | 各項目 `subtotal` 的總和（已含動態定價調整） |
| `shippingFee` | 各店鋪預估運費的**合計** |
| `discountAmount` | 各店鋪已套用優惠券折扣的**合計** |
| `finalAmount` | `totalAmount + shippingFee − discountAmount` |
| `appliedPromoCode` | 頂層只在**恰好一家店鋪**套了券時有值（多家店鋪各自套券時為 `null`，看 `stores[].appliedPromoCode`） |
| `stores[]` | 依店鋪分組的結帳摘要（見下）。**頂層的合計在多家店鋪時只是資訊性的「全部都結」數字，實際要分店鋪各自結帳** |
| 空購物車 | `items: []`、`totalItems: 0`、`totalAmount: 0`、`stores: []`、`shippingFee: 0`、`discountAmount: 0`、`finalAmount: 0`、`currency: "TWD"`；`appliedPromoCode` 為 `null` |

**CartItemResponse**（`items[]`）

| 欄位 | 說明 |
|------|------|
| `unitPrice`／`subtotal` | 單價與小計。PRODUCT 在 `DYNAMIC_PRICING_ENABLED` 開啟且規則調整後單價 ≠ 現價時，以**調整後**單價重算（讀取時算，不存進 Redis）；計算失敗降級為原價 |
| `originalUnitPrice`／`discountAmount`／`appliedRuleName`／`priceAdjustmentType` | 動態定價調整資訊；無調整時全為 `null`。`discountAmount` 是此項目的有號差額（正＝折扣、負＝加價），`priceAdjustmentType` 為 `DISCOUNT`／`MARKUP`／`NONE` |
| `storeId`／`storeName` | 商品／房源**所屬的店鋪**（`listings.tenant_id`）。刊登項目已不存在時為 `null`、名稱為 `Unknown`，該項目**無法結帳**，只能移除 |
| `storeActive` | 該店鋪是否營業中（`ACTIVE`）。`false`（待審核／已駁回／停權／終止）時前端應標示「暫停營業」並停用該店鋪的結帳；後端結帳也會擋下（`E-2010`）。**加入購物車不擋** |
| `listingType` | `PRODUCT` 或 `ROOM` |
| `startDate`／`endDate` | ROOM 的日期範圍 |

**StoreCartSummary**（`stores[]`）

| 欄位 | 說明 |
|------|------|
| `storeId`／`storeName`／`storeActive` | 同上 |
| `itemCount` | 該店鋪項目的**總件數** |
| `totalAmount` | 該店鋪項目小計（含 ROOM 與 PRODUCT） |
| `shippingFee` | 預估運費：以該店鋪 **PRODUCT 項目小計**為基數，走**該店鋪自己的運費模板**；基數為 0（純 ROOM）時為 0 |
| `appliedPromoCode`／`discountAmount` | 該店鋪已套用的優惠券與折扣。折扣於每次讀取時重新驗證與計算，**券失效時靜默退回 0 折扣**（仍回傳 `appliedPromoCode`） |
| `finalAmount` | `totalAmount + shippingFee − discountAmount` |

#### AddItemResponse — `POST /v2/cart/items` 的 `data`

```json
{
  "success": true,
  "item": { "…": "同 CartItemResponse（累加後的數量與小計；不含 storeId／storeName／storeActive）" },
  "totalItemsInCart": 3,
  "message": "Item added to cart successfully"
}
```

注意：`item` 只填入該項目本身的欄位；**店鋪相關欄位（`storeId`／`storeName`／`storeActive`）要看 `GET /v2/cart`**。

#### ApplyPromoResponse — `POST /v2/cart/apply-promo` 的 `data`

```json
{
  "storeId": "550e8400-e29b-41d4-a716-446655440099",
  "appliedPromoCode": "SAVE20",
  "shippingFee": 60.00,
  "discountAmount": 20.00,
  "finalAmount": 940.00,
  "discountType": "FIXED_AMOUNT",
  "discountValue": 20.00
}
```

`finalAmount` 是**該店鋪**的應付金額（小計＋運費−折扣），不是整車。`FREE_SHIPPING` 券的折扣基數是運費。

#### PromoValidationResult — `GET /v2/cart/validate-promo` 的 `data`

```json
{ "valid": true, "promoCode": "SAVE20", "discountType": "FIXED_AMOUNT", "discountValue": 20.00, "maxDiscount": null }
```

無效時：`{ "valid": false, "invalidReason": "EXPIRED: 優惠券已過期" }`。`invalidReason` 的格式是 `{原因代碼}: {訊息}`，原因代碼為 `INVALID`（不存在或空白）、`INACTIVE`、`NOT_YET_ACTIVE`、`EXPIRED`、`USAGE_LIMIT`。

---

## 4. 端點詳細規格 / Endpoint Details

### 4.1 `GET /v2/cart` — 取得購物車

- 回傳 §3.2 的 `CartResponse`。每次讀取都會批次查出項目所屬的店鋪（名稱與是否營業中）、重算動態定價、各店鋪的運費與已套用優惠券的折扣。
- 不會刷新 Redis 的 TTL（只有加入與修改項目會）。

### 4.2 `GET /v2/cart/count` — 取得總件數

- `data` 是一個整數：各項目 `quantity` 的總和（不是項目數）。購物車空或不存在為 `0`。

### 4.3 `POST /v2/cart/items` — 加入購物車

**規則**

1. 刊登項目必須存在；不存在 → `404 E-3000`。（**不檢查**刊登項目是否上架、店鋪是否營業、庫存是否足夠——這些在結帳時才檢查。）
2. ROOM 必須同時帶 `startDate` 與 `endDate`，否則 `400 E-4003`；`endDate` 必須晚於 `startDate`，否則 `400 E-4004`。（**不檢查**房源在這段期間是否可訂。）
3. 同一個刊登項目（＋SKU）已在購物車：**累加數量**（累加後不設上限，只有單次請求限制 1～999），日期以本次為準。
4. 單價是**加入當下**刊登項目的基本價（或 SKU 的 `priceOverride`），存進 Redis；之後刊登價格變動不會自動反映到既有項目。
5. 每次加入都把購物車 TTL 重設為 30 天。

**錯誤**

| 狀況 | HTTP | `code` |
|------|:----:|--------|
| 刊登項目不存在 | 404 | `E-3000` |
| ROOM 沒給日期 | 400 | `E-4003` |
| 退房日期不晚於入住日期 | 400 | `E-4004` |
| `listingId` 缺漏、`quantity` 缺漏或不在 1～999 | 400 | `E-9000`（帶 `errors[]`） |

### 4.4 `PUT /v2/cart/items/{cartItemKey}` — 更新數量

- 取代該項目的數量並重算小計，TTL 重設為 30 天；回傳該項目的 `CartItemResponse`。
- 項目不存在或 `cartItemKey` 無法解析 → `404 E-5005`；`quantity` 不在 1～999 → `400 E-9000`。

### 4.5 `DELETE /v2/cart/items/{cartItemKey}` — 移除項目

- 回 `200`，`data` 為 `null`。項目不存在或 key 無法解析 → `404 E-5005`。

### 4.6 `DELETE /v2/cart` — 清空購物車

- 刪除購物車的**所有項目**，回 `200`、`data` 為 `null`；購物車本來就是空的也回 `200`。
- **只清項目，不清已套用的優惠券標記**（`DEF-336`）：之後同一家店鋪再加入商品，會再次看到先前套用的券（每次讀取都重新驗證，失效就退回 0 折扣；結帳成功會清掉該店鋪的標記）。

### 4.7 `POST /v2/cart/apply-promo` — 套用優惠券（以店鋪為單位）

**規則**

1. 購物車是空的 → `400 E-5004`。
2. 決定店鋪（`CartStoreSelector`）：有帶 `storeId` → 必須是購物車裡真的有項目的店鋪，否則 `400 E-5004`（"No items of the requested store in cart"）；沒帶 → 購物車只有一家店鋪就是那一家，有多家 → `400 E-5020`。（不屬於任何店鋪的項目——刊登項目已被刪除——不參與選擇。）
3. 在**該店鋪**驗證優惠券：任何無效（不存在、停用、尚未開始、已過期、已兌換完畢）一律 `400 E-5007`（訊息是泛用的「無效的優惠碼」，詳細原因看 `validate-promo` 的 `invalidReason`）。
4. 折扣以**該店鋪的項目小計與運費**計算；已套用的券以（使用者, 買家租戶, 店鋪）為鍵存進 Redis（TTL 30 天，不改變項目）。
5. 套用只是記住券碼：**訂單的實際折扣由結帳當下重新驗證計算**。

### 4.8 `DELETE /v2/cart/promo?storeId=…` — 移除優惠券

- `storeId` 的省略規則同套用（單一店鋪可省略，多家店鋪必填，否則 `400 E-5020`；購物車是空的 `400 E-5004`）。回 `200`、`data` 為 `null`；該店鋪沒套過券也回 `200`。

### 4.9 `GET /v2/cart/validate-promo?code=…&storeId=…` — 驗證優惠券（不套用）

- **永遠回 `200`**，是否有效寫在 `data.valid`（無效時看 `invalidReason`）。
- `storeId`：有帶就用該店鋪（**不要求購物車裡有該店鋪的項目**）；沒帶時用購物車唯一的店鋪；購物車沒有任何店鋪的項目時以買家租戶驗證（沒有店鋪的消費者會得到 `valid=false`）；購物車有多家店鋪卻沒帶才回 `400 E-5020`。
- `code` 缺漏 → `400 E-9005`。

---

## 5. 錯誤碼彙整 / Error Codes

完整對照見 [API_Error_Codes.md](./API_Error_Codes.md) §4；以下只列本模組會遇到的。

| 錯誤碼 | HTTP | 情境 |
|--------|:----:|------|
| `E-1000` | 401 | 未帶 token 或 token 無效 |
| `E-1007` | 403 | 沒有 `cart:*` 權限（非買家身分） |
| `E-3000` | 404 | 加入不存在的刊登項目 |
| `E-4003` | 400 | ROOM 沒給入住／退房日期 |
| `E-4004` | 400 | 退房日期不晚於入住日期 |
| `E-5004` | 400 | 購物車是空的（套用／移除優惠券），或指定的店鋪在購物車裡沒有項目 |
| `E-5005` | 404 | 找不到購物車項目（更新／移除；`cartItemKey` 無法解析也是） |
| `E-5007` | 400 | 優惠券無效（套用時） |
| `E-5020` | 400 | 購物車有多家店鋪，卻沒指定 `storeId` |
| `E-9000` | 400 | 請求內容驗證失敗（帶 `errors[]`） |
| `E-9005` | 400 | 缺少必填的查詢參數（`validate-promo` 的 `code`） |

**結帳時才會出現的**（不在本模組的端點）：`E-2010`（店鋪暫停營業，422）、`E-5006`（無效的數量，422）、`E-5008`／`E-5009`（優惠券過期／已達上限）、`E-3002`（刊登項目未上架，422）。見 [API_M05_Order.md](./api/API_M05_Order.md)。

---

## 6. 多租戶與店鋪 / Tenancy

### 6.1 隔離

- 購物車鍵是 `cart:{userId}:{buyerTenantId}`；優惠券標記鍵是 `cart:promo:{userId}:{buyerTenantId}:{storeId}`。
- **買家租戶 ≠ 店鋪**：買家租戶是登入時 JWT 帶的租戶（沒有店鋪的消費者是系統租戶佔位值 `00000000-0000-0000-0000-000000000001`）；項目所屬的**店鋪**是商品／房源的租戶。兩者是不同的概念，Sprint 232～238 才把訂單、訂房與購物車的歸屬從「買家租戶」改成「店鋪」（`DEF-319`）。

### 6.2 店鋪與結帳

| 情境 | 行為 |
|------|------|
| 購物車只有一家店鋪的項目 | 套用優惠券、結帳都可以省略 `storeId`，與改版前相同 |
| 購物車有多家店鋪的項目 | 套用／移除優惠券、結帳都必須帶 `storeId`（`E-5020`）；結帳後購物車只剩其他店鋪的項目 |
| 店鋪不是 `ACTIVE` | 項目仍在購物車、`storeActive=false`；結帳被擋（`E-2010`）。使用者可以移除這些項目 |
| 刊登項目已被刪除 | `storeId=null`，無法結帳，只能移除 |

---

## 7. 追蹤性鏈 / Traceability Chain

```
SPRINT_04_PLAN.md ── US-M04-001 ~ 004
SPRINT_100／101 ── 優惠券與運費預覽（AI-2435）
SPRINT_237／238／239_PLAN.md ── 店鋪歸屬、同店結帳、店鋪營業狀態（DEF-319）
SPRINT_241_PLAN.md ── 本版改寫、DEF-335（錯誤輸入回 500）
  └── 本文件 (API_M04_Cart.md)
       └── TC_M04_Cart.md（測試案例；停在 Sprint 4，見其現況聲明）
```

| User Story | 端點 |
|------------|------|
| US-M04-001 | `POST /api/v2/cart/items` |
| US-M04-002 | `GET /api/v2/cart`、`GET /api/v2/cart/count` |
| US-M04-003 | `PUT /api/v2/cart/items/{cartItemKey}` |
| US-M04-004 | `DELETE /api/v2/cart/items/{cartItemKey}`、`DELETE /api/v2/cart` |

---

## 8. 參考實作 / Reference Implementation

| 檔案 | 路徑 | 說明 |
|------|------|------|
| CartController | `backend/src/main/java/com/nextkey/ecommerce/api/controller/CartController.java` | 9 個端點與權限 |
| RedisCartService | `backend/src/main/java/com/nextkey/ecommerce/core/cart/RedisCartService.java` | 購物車服務（Redis） |
| CartStoreSelector | `backend/src/main/java/com/nextkey/ecommerce/core/cart/CartStoreSelector.java` | 決定「這次要結哪一家店鋪」 |
| CartDto | `backend/src/main/java/com/nextkey/ecommerce/api/dto/CartDto.java` | DTO 定義 |
| StoreCheckoutGuard | `backend/src/main/java/com/nextkey/ecommerce/core/tenant/StoreCheckoutGuard.java` | 店鋪是否營業中 |
| CartInputErrorMappingIntegrationTest | `backend/src/test/java/com/nextkey/ecommerce/integration/CartInputErrorMappingIntegrationTest.java` | 錯誤輸入的回應（真實 PostgreSQL＋Redis） |
| ApiRouteDocDriftTest | `backend/src/test/java/com/nextkey/ecommerce/api/ApiRouteDocDriftTest.java` | 路由漏記守門（Sprint 241） |

> **測試注意**：`CartControllerE2ETest` 用的 `IntegrationTestConfiguration` 把 `RedisCartService` 換成記憶體版的 mock，**驗證不了真實服務的例外與 Redis 語意**；要驗證請用不匯入該設定的整合測試（例如上表的 `CartInputErrorMappingIntegrationTest`、`StoreScopedCheckoutIntegrationTest`）。

---

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-28 | 初版（Sprint 4：加入、檢視、更新、移除四個端點） | SD (Marcus) |
| v2.0 | 2026-10-03 | Sprint 241 依實作改寫：9 個端點、成功狀態碼 200、多店鋪購物車（`stores[]`、`storeId`、`storeActive`）、優惠券與運費以店鋪為單位、錯誤碼對照、`cartItemKey` 實際格式、`DEF-335`（錯誤輸入回 500）與 `DEF-336`（清空不清優惠券標記）；回應封包改為扁平封包 | Claude（依使用者 Sprint 232 拍板「比照 Sprint 203」） |
