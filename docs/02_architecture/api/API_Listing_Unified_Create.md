# API_Listing_Unified_Create

> **API 編號**: API-DEF-001（歷史編號，沿用自 Sprint 6；[TC_M02_Room.md](../../03_testing/TC_M02_Room.md) 等歷史文件仍引用此編號，不更改）
> **模組**: DEF-001（環境建設）／DEF-286 範圍（本次校正）
> **版本**: v2.0（Sprint 273 依實作校正；v1.0 全文為杜撰內容，與實作不符）
> **建立日期**: 2026-04-30
> **最後更新**: 2026-10-15
> **基於**: Sprint 6 Plan Section 5.1 T-DEF-001-01；依據 `DashboardListingController`／`CreateListingRequest`／`ProductService.createProductFromDashboard`／`RoomService.createRoomFromDashboard` 實際行為校正

> **⚠️ 修訂註記（Sprint 273）**：v1.0 幾乎每個欄位都是杜撰，已更正：
> 1. 請求沒有 `currency`／`categoryId`／`images`／`roomSpecificFields`／`productSpecificFields` 巢狀結構，`CreateListingRequest` 是扁平 DTO（見 §2.1）；
> 2. `category` 是選填的自由字串，**不是**「必填且需存在」的 `categoryId`——全庫無 `Category` 實體，不存在任何分類查詢（與 [API_M01_Product_Center.md](./API_M01_Product_Center.md) §3.4 同一更正）；
> 3. 成功 HTTP 狀態碼是 **200**，不是 201（`DashboardListingController.createListing` 用 `ResponseEntity.ok(...)`）；
> 4. 錯誤碼 `E_1001`／`E_2001`／`E_5000` 皆不存在於後端（`ErrorCode.java` 沒有這些碼），實際為 `E-9000`（驗證失敗）／`E-2004`（Feature Toggle）／`E-2009`（配額）；
> 5. 回應 `data` 不是扁平的 `{id, listingType, name, status, createdAt}`，而是依 `listingType` 回傳完整的 `ProductDto.Response` 或 `RoomDto.Response`（欄位分別見 [API_M01_Product_Center.md](./API_M01_Product_Center.md) §3.2、[API_M02_Listing_Center.md](./API_M02_Listing_Center.md) §3.1）。

---

## 1. API 概述

- **端點**: `POST /v2/dashboard/listings`

| 欄位 | 內容 |
|------|------|
| **功能** | 統一 Listing 建立端點，依 `listingType` 分流到 `ProductService.createProductFromDashboard` 或 `RoomService.createRoomFromDashboard` |
| **用途** | 支援 ROOM／PRODUCT 共用同一個請求格式建立；與型別專屬入口（`POST /v2/products`、`POST /v2/rooms`）是兩條合約不同的平行路徑，見 §5.3 |
| **所需權限** | 類別層 `@PreAuthorize` 為 `hasAuthority('product:create') or hasAuthority('room:create')`（OR 條件，方便共用單一端點），但 Controller 內部依 `listingType` **逐分支再次要求對應的精確權限**（`product:create` 建 PRODUCT、`room:create` 建 ROOM），見 §5.1 |

---

## 2. Request 規格

### 2.1 Request DTO

**類別**: `CreateListingRequest`（扁平結構，無巢狀欄位）

| 欄位 | 類型 | 必填 | 限制 | 適用類型 |
|------|------|:----:|------|---------|
| `listingType` | String | ✅ | 僅 `PRODUCT`／`ROOM`（Bean Validation `@Pattern`，大小寫須完全相符） | 通用 |
| `name` | String | ✅ | ≤200 字元 | 通用（對應 `Listing.title`） |
| `description` | String | ❌ | ≤5000 字元 | 通用 |
| `price` | BigDecimal | ✅ | 0.01～999999999.99 | 通用（對應 `Listing.basePrice`） |
| `coverImageUrl` | String | ❌ | 禁止 `javascript:`／`data:`／`vbscript:`／`file:` 協定 | 通用 |
| `tags` | `List<String>` | ❌ | ≤10 筆 | 通用 |
| `category` | String | ❌ | ≤50 字元，**選填**（與 `POST /v2/products` 的必填不同，見 §5.4） | PRODUCT |
| `brand` | String | ❌ | ≤100 字元 | PRODUCT |
| `weightGrams` | Integer | ❌ | ≥1 | PRODUCT |
| `dimensionsCm` | String | ❌ | ≤50 字元，自由格式 | PRODUCT |
| `location` | String | ❌ | ≤200 字元 | ROOM |
| `latitude` / `longitude` | Double | ❌ | -90~90 / -180~180 | ROOM |
| `maxGuests` | Integer | ❌ | ≥1 | ROOM |
| `amenities` | `List<String>` | ❌ | — | ROOM |
| `checkInTime` / `checkOutTime` | LocalTime | ❌ | — | ROOM |
| `roomCount` | Integer | ❌ | ≥1 | ROOM |
| `additionalFields` | `Map<String,Object>` | ❌ | 通過驗證但**完全不會被讀取**（`createProductFromDashboard`／`createRoomFromDashboard` 皆未存取此欄位，純粹的請求體死欄位） | 通用 |

沒有 `currency`（建立時一律寫死 `"TWD"`）、沒有 `openUntilDate`／`bookingWindowDays`（此入口不支援設定開放窗，需另呼叫 `PUT /v2/rooms/{listingId}`）、沒有 `isFeatured`（同理）。

### 2.2 Request 範例

**ROOM 類型**:
```json
{
  "listingType": "ROOM",
  "name": "豪華雙人房",
  "description": "面海景觀大床房",
  "price": 3500,
  "location": "屏東縣恆春鎮",
  "maxGuests": 2,
  "amenities": ["WiFi", "Mini Bar", "Sea View"]
}
```

**PRODUCT 類型**:
```json
{
  "listingType": "PRODUCT",
  "name": "精選咖啡豆",
  "description": "來自衣索比亞的精品咖啡豆",
  "price": 450,
  "category": "食品",
  "brand": "NextKey Coffee"
}
```

---

## 3. Response 規格

### 3.1 Success Response

**HTTP Status**: `200 OK`（不是 201——`DashboardListingController.createListing` 用 `ResponseEntity.ok(...)`）

```json
{
  "success": true,
  "message": "Listing created successfully",
  "data": { "...": "見下方說明" },
  "timestamp": "2026-10-15T10:30:00Z"
}
```

`data` 依 `listingType` 分別是完整的 `ProductDto.Response`（欄位見 [API_M01_Product_Center.md](./API_M01_Product_Center.md) §3.2）或 `RoomDto.Response`（欄位見 [API_M02_Listing_Center.md](./API_M02_Listing_Center.md) §3.1），**不是**精簡的 `{id, listingType, name, status, createdAt}`。建立結果的 `status` 一律是 `ACTIVE`（無 `DRAFT` 階段，見下方兩份文件各自的 §7 已知限制 DEF-380）。

### 3.2 Error Response

與全站共用同一個扁平封包，見 [API_Error_Codes.md](../API_Error_Codes.md)：

```json
{
  "success": false,
  "code": "E-9000",
  "message": "錯誤訊息",
  "errors": [],
  "timestamp": "2026-10-15T10:30:00Z",
  "requestId": "..."
}
```

沒有巢狀的 `error` 物件。

---

## 4. 錯誤碼

| 錯誤碼 | HTTP Status | 說明 |
|--------|:-----------:|------|
| `E-9000` | 400 | Bean Validation 失敗（必填欄位缺失、格式不符、`listingType` 不是 `PRODUCT`／`ROOM`） |
| `E-2004` | 403 | Feature Toggle 未啟用（`RETAIL_ENABLED`／`BOOKING_ENABLED`，依 `listingType`） |
| `E-2009` | 400 | 已達商品／房源配額上限（分別 100／20，見 §5.2） |
| `E-3001` | 422 | `listingType` 既非 `PRODUCT` 亦非 `ROOM`——**理論上的分支，實際不可達**：`@Pattern(regexp="^(PRODUCT|ROOM)$")` 已在 Bean Validation 階段擋下任何其他字面值，此 Controller 內的 `else` 分支無法被觸發 |

---

## 5. 業務規則

### 5.1 權限檢查（兩層）

1. 類別層 `@PreAuthorize("hasAuthority('product:create') or hasAuthority('room:create')")`：先放寬為 OR 條件，讓單一端點能同時服務兩種角色。
2. Controller 方法內再依 `listingType` 呼叫 `requireAuthority("product:create")` 或 `requireAuthority("room:create")`：這一層才是真正的精確檢查（DEF-263，Sprint 189）。若只有第 1 層、沒有第 2 層，僅持有 `room:create` 的 `HOST` 即可經此端點建立 `PRODUCT` listing（反之 `SELLER` 可建立 `ROOM` listing），繞過角色權限模型。

### 5.2 Feature Toggle 與配額檢查

| `listingType` | Feature Toggle | 禁用時 | 配額上限 | 超額時 |
|--------------|-----------------|--------|---------|--------|
| `PRODUCT` | `RETAIL_ENABLED` | `403 E-2004` | `AppConstants.QUOTA_MAX_PRODUCTS`（100，租戶 `ACTIVE` 商品數） | `400 E-2009` |
| `ROOM` | `BOOKING_ENABLED` | `403 E-2004` | `AppConstants.QUOTA_MAX_ROOMS`（20，租戶 `ACTIVE` 房源數） | `400 E-2009` |

兩項檢查都在 Service 層（`ProductService.createProductFromDashboard`／`RoomService.createRoomFromDashboard`），不在 Controller 層（PRD §4.4 分層規範，Sprint 148，DEF-184）。

### 5.3 與型別專屬入口（`POST /v2/products`／`POST /v2/rooms`）的合約差異

| 項目 | 本統一入口 | 型別專屬入口 |
|------|-----------|-------------|
| 成功 HTTP 狀態碼 | 200 | 201 |
| `category`（PRODUCT） | 選填 | 必填（`@NotBlank`） |
| `title`／`location`（ROOM） | 共用 `name`／`location`，`location` 選填 | `title`／`location` 皆必填 |
| `isFeatured`、SKU 建立 | 不支援（建立後需另呼叫對應端點） | 不支援（`isFeatured` 由型別專屬 DTO 支援，SKU 見 M01 §4.12） |

兩條路徑共用同一個底層 `ProductService`／`RoomService`，建立出的 `Listing`／`Product`／`Room` 資料結構完全相同，差別只在請求/回應的 DTO 合約。

### 5.4 欄位驗證摘要

| 欄位 | 規則 |
|------|------|
| `name` | 必填，≤200 字元 |
| `price` | 必填，0.01～999999999.99 |
| `category` | **選填**，≤50 字元，無「需存在」驗證（全庫無 `Category` 實體） |
| `location` | ROOM 選填（與型別專屬入口的必填不同） |
| `maxGuests` | ROOM 選填，≥1（未提供時由 `RoomService` 預設為 2） |

---

## 6. 對應測試案例

| TC ID | 描述 | 預期結果 |
|-------|------|----------|
| API-DEF-001-01 | 建立 ROOM Listing 成功 | 200 + `RoomDto.Response` |
| API-DEF-001-02 | 建立 PRODUCT Listing 成功 | 200 + `ProductDto.Response` |
| API-DEF-001-03 | `BOOKING_ENABLED=false` 時建立 ROOM | 403 `E-2004` |
| API-DEF-001-04 | `RETAIL_ENABLED=false` 時建立 PRODUCT | 403 `E-2004` |
| API-DEF-001-05 | 缺少必填欄位 | 400 `E-9000` |
| API-DEF-001-06 | `price` ≤ 0 | 400 `E-9000` |

本文件未附測試程式碼位置——`DashboardListingController`／`CreateListingRequest` 目前的單元測試覆蓋範圍，見 `backend/src/test/java/com/nextkey/ecommerce/api/controller/DashboardListingControllerTest.java`（如存在）；本輪（Sprint 273）僅校正文件內容，未新增測試。

---

## 7. 誰需要這個 API

| 角色 | 使用場景 |
|------|----------|
| SELLER／STORE_OWNER | 建立商品 |
| HOST／STORE_OWNER | 建立房源 |
| SUPER_ADMIN | 持有 `EnumSet.allOf(Permission.class)`，兩種皆可建立；但仍受 §5.2 的 Feature Toggle／配額限制（配額检查未對 `SUPER_ADMIN` 特殊放寬） |

---

## 8. 關聯文件

- [API_M01_Product_Center.md](./API_M01_Product_Center.md) — PRODUCT 側完整規格（型別專屬入口、瀏覽、SKU）
- [API_M02_Listing_Center.md](./API_M02_Listing_Center.md) — ROOM 側完整規格（型別專屬入口、維護窗口）
- [API_Error_Codes.md](../API_Error_Codes.md) — 全站錯誤碼權威來源
- [SPRINT_06_PLAN.md](../../04_planning/SPRINT_06_PLAN.md) — Sprint 6 計劃（歷史背景）
- [TC_M02_Room.md](../../03_testing/TC_M02_Room.md) — M02 測試案例（沿用歷史編號，描述情境與本文件校正後的現況可能仍有落差，未在本輪一併校正）

---

**文件狀態**：v2.0（Sprint 273 校正，DEF-286 範圍）。
