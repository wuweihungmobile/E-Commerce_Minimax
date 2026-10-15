# API 規格文件 - M01 商品中心 / API Specification - M01 Product Center

> **文件版本**: v2.0（Sprint 273 依實作全面改寫，DEF-286 範圍）
> **建立日期**: 2026-04-09（v1.0）
> **最後更新**: 2026-10-15
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`ListingController`／`PublicListingController`／`ProductController`（`api/controller/`）、`ProductService`（`core/product/`）、`PricingService`（動態價格計算，屬 M12）、`ReviewService`（評分統計，屬 M08）、`ProductSkuService`（`core/product/`）、`CreateListingRequest`／`ProductDto`／`SkuDto`／`ListingResponse`／`ReviewDto`／`PricingDto`（`api/dto/`）、`Listing`／`Product`（`domain/model/`）
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`，**沒有**巢狀的 `error`，也**沒有**數字型的 `code`）
> **取代關係**: 本文件**取代** v1.0 全文；v1.0 的端點、請求/回應格式與錯誤碼表皆為杜撰，與實作差距極大

> **⚠️ 修訂註記（Sprint 273）**：v1.0 與實作至少有以下落差，本版已更正：
> 1. **回應封包是扁平的 `success`／`code`／`message`／`data`**，不是 v1.0 寫的巢狀 `{code, message, data}`；
> 2. **不存在的端點**：`GET /v2/listings/search`（搜尋是 `GET /v2/listings?keyword=`的參數，不是獨立路徑）、`GET /v2/categories`（全庫無 `Category` 實體，見 §3.4）、`PUT /v2/dashboard/listings/:id/publish`（不存在任何「發布」端點，見 §7 DEF-380）、`PUT/DELETE /v2/dashboard/listings/:id`（統一入口只有 `POST`，更新/刪除要用型別專屬的 `PUT/DELETE /v2/products/{listingId}`）；
> 3. **程式有、v1.0 完全沒提的端點**：`GET /v2/public/listings`（Sprint 259 新增的匿名瀏覽）、`GET /v2/listings/{id}/price`／`GET /v2/listings/{listingId}/effective-price`（動態定價，規則屬 M12）、`GET /v2/products/{productId}/reviews/stats`（評分統計，屬 M08）、商品 SKU 的建立/列表/更新（Sprint 178 新增，共 3 個端點）；
> 4. **`category` 欄位必填與否依建立路徑不同**：`POST /v2/products`（`ProductDto.CreateRequest`）必填；統一入口 `POST /v2/dashboard/listings`（`CreateListingRequest`）選填，v1.0／舊版 `API_Listing_Unified_Create.md` 皆誤寫成「必填且需存在」，且全庫無 `Category` 實體可供驗證存在性；
> 5. **錯誤碼表全部換成實際的**：v1.0 的 `E-4001`／`E-2020`／`E-4041` 都不是後端真正會回的碼（實際為 `E-9000`／`E-2004`／`E-3000`，見 §6）。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M01 |
| **模組名稱** | 商品中心 / Product Center |
| **描述** | 商品與房源共用的跨類型瀏覽（`ListingController`／`PublicListingController`）、商品專屬的 CRUD／SKU／評分統計（`ProductController`） |
| **API 前綴** | 後端 context path 是 `/api`，完整網址為 `/api/v2/listings/...`、`/api/v2/public/listings`、`/api/v2/products/...` |
| **多租戶隔離** | `/v2/listings`／`/v2/public/listings` 刻意跨租戶（供前台瀏覽所有店鋪的上架項目）；`/v2/products` 的預設瀏覽與關鍵字搜尋以呼叫者目前租戶 `+ ACTIVE` 為範圍，但 `category`／`brand` 篩選分支**不受租戶限制**，見 §7 DEF-379 |
| **範圍外** | 本文件不含「統一建立」端點（`POST /v2/dashboard/listings`）的完整規格，該端點同時服務 PRODUCT／ROOM 兩種類型，獨立於 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md)；也不含動態定價規則管理（M12）、評價本體（M08）、ERP 庫存細節 |

### 1.2 設計重點

1. **Listing 統一抽象**：`PRODUCT`／`ROOM` 共用 `listings` 表（id、tenantId、listingType、title、description、coverImageUrl、status、basePrice、currency、tags、isFeatured），型別專屬資料各自存 `products`／`rooms` 表（`listingId` 同時是 PK 與 FK）。本文件只涵蓋 `PRODUCT` 側與兩者共用的瀏覽端點；`ROOM` 側見 [API_M02_Listing_Center.md](./API_M02_Listing_Center.md)。
2. **兩條平行的建立路徑，合約不同**：型別專屬 `POST /v2/products`（`ProductController`，`ProductDto.CreateRequest`，`category` 必填，成功回 **201**）與統一入口 `POST /v2/dashboard/listings`（`DashboardListingController`，`CreateListingRequest`，`category` 選填，成功回 **200**，見 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md)）。前端目前只會呼叫其中一條（依頁面而定），但兩者都是生產可達的真實端點，共用同一個底層 `ProductService`（分別是 `createProduct`／`createProductFromDashboard`，邏輯幾乎相同，各自獨立套用請求驗證）。
3. **建立時一律直接 `ACTIVE`，沒有 `DRAFT` 階段**：`createProduct`／`createProductFromDashboard` 皆寫死 `.status(Listing.ListingStatus.ACTIVE)`。`ListingStatus.DRAFT` 列舉值存在、`UpdateRequest.status` 也能事後把任何商品改成 `DRAFT`，但**沒有任何建立路徑會產生 `DRAFT` 列**，也沒有「發布」端點把 `DRAFT` 轉 `ACTIVE`。FRD §3 描述的「先建 `DRAFT` 商品、再呼叫發布端點」兩階段流程在目前實作中不存在，詳見 §7 DEF-380。
4. **配額與 Feature Toggle**：`RETAIL_ENABLED` 未對該租戶啟用時無法建立商品（`E-2004`，檢查在 `ProductService` 層，不在 Controller，依 PRD §4.4 分層規範）；租戶 `ACTIVE` 商品數達 `AppConstants.QUOTA_MAX_PRODUCTS`（**100**）上限時無法再建立或把既有商品轉為 `ACTIVE`（`E-2009`）。
5. **三層瀏覽端點，範圍各不相同**：`GET /v2/listings`（已登入或 Guest 皆可，`PRODUCT`＋`ROOM` 混合瀏覽，`type` 參數篩選，跨租戶，Sprint 259 起免登入）、`GET /v2/public/listings`（刻意掛在 `permitAll()` 規則下的匿名瀏覽，Sprint 259 新增，回應額外帶 `tenantName`／`tenantSlug`）、`GET /v2/products`（要求 `product:read` 權限字串，但 `GUEST`／`BUYER` 角色也持有此字串，等同形同公開，範圍見 §7 DEF-379）。三者查詢的是同一批資料，差異在於範圍與回應形狀，不是三套不同資料。
6. **動態定價端點借用 `ListingController`**：`GET /v2/listings/{id}/price`、`GET /v2/listings/{listingId}/effective-price` 宣告在本模組的 GUARDED Controller 上，但計算規則（週末倍率、節日調價、人工覆蓋）屬 M12，詳見 [API_M12_Dynamic_Pricing.md](./API_M12_Dynamic_Pricing.md)。本文件只記載這兩個端點的請求/回應形狀。
7. **SKU 是後補的庫存入口（Sprint 178）**：PRD 原將「SKU 多規格管理」列為 Phase 2+ 排除項，但 ERP 庫存子系統（`stock_movements`、`product_inventory`、採購單收貨入庫）早已完成且依賴 SKU 存在；`ProductController` 補上 SKU 的建立/列表/更新端點，使既有庫存管線得以被觸發。SKU 無刪除端點（`stock_movements.sku_id` 一旦有異動記錄即無法刪除，只能用 `status=INACTIVE` 停用）。
8. **評分統計掛在 `ProductController` 下**：`GET /v2/products/{productId}/reviews/stats` 路由在本模組的 Controller，但底層資料與計算屬 M08，詳見 [API_M08_Review.md](./API_M08_Review.md)。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M01-001 | Guest／Buyer 瀏覽商品/房源列表、詳情、關鍵字搜尋 | `GET /v2/listings`、`GET /v2/listings/{id}`、`GET /v2/public/listings` | ✅ 已實作 |
| US-M01-002 | Seller/StoreOwner 建立、更新、下架商品 | `POST/PUT/DELETE /v2/products`、`POST /v2/dashboard/listings` | ✅ 已實作（無 DRAFT/發布兩階段，見 §7 DEF-380） |
| US-M01-003 | 依分類/品牌篩選商品 | `GET /v2/products?category=&brand=` | ✅ 已實作（範圍落差見 §7 DEF-379） |
| US-M01-004 | 商品規格（SKU）管理 | `POST/GET/PUT /v2/products/{listingId}/skus...` | ✅ 已實作（Sprint 178，原列 Phase 2+） |
| US-M01-005 | 查看商品動態價格與評分統計 | `GET /v2/listings/{id}/price`、`GET /v2/products/{productId}/reviews/stats` | ✅ 已實作（規則/資料分屬 M12／M08） |

對應 PRD §9.3（M01 商品中心）、§13.1（Phase 1 最小子集）；FRD §3 詳述功能需求（§3.6 端點表已依本文件同步更正，技術實現概要與驗收標準仍描述未實作的 DRAFT/發布流程，見 FRD 修訂記錄與本文件 §7）。

---

## 2. API 端點總覽 / Endpoints Summary

| 方法 | 路徑（`/api` 之後） | 描述 | 權限 | 成功 |
|------|--------------------|------|------|:----:|
| **GET** | `/v2/listings` | 商品＋房源混合列表（分頁／`type`／`keyword`／排序） | `product:read` 或 `room:read` | 200 |
| **GET** | `/v2/listings/{id}` | 單一 Listing 詳情（Guest 可用） | 公開 | 200 |
| **GET** | `/v2/listings/{id}/price` | 動態價格試算（規則屬 M12） | `product:read` 或 `room:read` | 200 |
| **GET** | `/v2/listings/{listingId}/effective-price` | 有效單價試算（規則屬 M12） | `product:read` 或 `room:read` | 200 |
| **GET** | `/v2/public/listings` | 匿名跨店鋪瀏覽（含店鋪名稱/slug） | 公開（`permitAll`） | 200 |
| **GET** | `/v2/products` | 商品列表（分頁／`category`／`brand`／`keyword`／排序） | `product:read` | 200 |
| **GET** | `/v2/products/{listingId}` | 商品詳情 | `product:read` | 200 |
| **POST** | `/v2/products` | 建立商品（型別專屬入口，直接 `ACTIVE`） | `product:create` | 201 |
| **PUT** | `/v2/products/{listingId}` | 更新商品（含手動變更 `status`） | `product:update` | 200 |
| **DELETE** | `/v2/products/{listingId}` | 軟刪除商品（`status → DELETED`） | `product:delete` | 200 |
| **GET** | `/v2/products/{productId}/reviews/stats` | 評分統計（屬 M08） | 已登入 | 200 |
| **POST** | `/v2/products/{listingId}/skus` | 建立 SKU | `product:update` | 201 |
| **GET** | `/v2/products/{listingId}/skus` | SKU 列表 | `product:read` | 200 |
| **PUT** | `/v2/products/{listingId}/skus/{skuId}` | 更新 SKU | `product:update` | 200 |

另有 `POST /v2/dashboard/listings`（統一建立入口，PRODUCT／ROOM 共用）獨立記載於 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md)，不在本文件 GUARDED 範圍內。

未帶 token 存取需登入的端點回 `401`；已登入但沒有該權限字串回 `403 E-1007`。

---

## 3. 資料模型 / Data Model

### 3.1 `Listing`（表 `listings`，PRODUCT／ROOM 共用）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | not null，唯讀影子欄位（建立時必須設定 `.tenant(...)` 關聯物件才會寫入，見 DEF-041） |
| `listingType` | enum `ListingType` | `PRODUCT`／`ROOM`，not null |
| `title` | String | ≤200，not null |
| `description` | String | ≤5000，選填 |
| `coverImageUrl` | String | 選填，禁止 `javascript:`／`data:`／`vbscript:`／`file:` 協定 |
| `status` | enum `ListingStatus` | `DRAFT`／`ACTIVE`／`INACTIVE`／`DELETED`；建立時一律 `ACTIVE`，見 §1.2 第 3 點 |
| `basePrice` | BigDecimal | not null，0.01～999999999.99 |
| `currency` | String | 建立時一律寫死 `"TWD"`，請求中帶的 `currency` 欄位（僅統一入口 DTO 有此欄位）不會被讀取 |
| `tags` | `List<String>` | ≤10 筆 |
| `isFeatured` | Boolean | 店鋪前台首頁策展旗標（Sprint 252，DEF-321(b)） |
| `ownerId` | UUID | 唯讀影子欄位，建立者 |
| `createdAt` / `updatedAt` | Instant | |

### 3.2 `Product`（表 `products`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `listingId` | UUID | PK，同時是 FK → `listings.id` |
| `category` | String | ≤50，**選填或必填依建立路徑而定**，見 §3.4 |
| `brand` | String | ≤100，選填 |
| `weightGrams` | Integer | ≥1，選填 |
| `dimensionsCm` | String | ≤50，選填（自由格式字串，非結構化長寬高） |
| `createdAt` / `updatedAt` | Instant | |

無 `specifications`／`sku`／`inventory` 內嵌欄位（v1.0 杜撰）；規格與庫存由 §3.3 的獨立 SKU 機制管理。

### 3.3 `ProductSku`（表 `product_skus`，回應為 `SkuDto.Response`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `listingId` | UUID | FK → `listings.id` |
| `skuCode` | String | ≤50，建立時必填，對齊 V1 schema `product_skus.sku_code VARCHAR(50)` |
| `specName` | String | ≤100，選填 |
| `priceOverride` | BigDecimal | 選填，> 0；提供時覆蓋 `basePrice` |
| `status` | String | `ACTIVE`／`INACTIVE`，無硬刪除 |
| `totalQty` / `reservedQty` / `availableQty` | Integer | 來自 `stock_movements`／`product_inventory` 的庫存彙總，非 SKU 表本身欄位 |
| `createdAt` / `updatedAt` | Instant | |

### 3.4 `CreateListingRequest`（統一建立入口專用，`category` 選填）vs `ProductDto.CreateRequest`（型別專屬入口，`category` 必填）

**全庫沒有 `Category` 實體或資料表**——`category` 在兩種請求 DTO 中都只是一個長度上限 50 的自由字串，沒有任何地方驗證它「必須存在」於某個分類清單。兩者的差異僅在於**是否允許空白**：

| DTO | 使用端點 | `category` 驗證 |
|-----|----------|-----------------|
| `ProductDto.CreateRequest` | `POST /v2/products` | `@NotBlank` + `@Size(max=50)`，必填 |
| `CreateListingRequest` | `POST /v2/dashboard/listings` | 僅 `@Size(max=50)`，選填（可為 `null`） |

v1.0 本文件與舊版 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md) 皆誤寫成「`category`／`categoryId` 必填且需存在」，已於本次改寫更正。

### 3.5 列舉 / Enums

| 列舉 | 值 |
|------|-----|
| `ListingType` | `PRODUCT`、`ROOM` |
| `ListingStatus` | `DRAFT`、`ACTIVE`、`INACTIVE`、`DELETED`（`DRAFT` 無任何建立路徑會產生，見 §1.2 第 3 點） |

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `GET /v2/listings` — 商品＋房源混合列表

**權限**：`product:read` 或 `room:read`（`GUEST`／`BUYER` 等角色皆持有，等同已登入即可；未登入會被 Security 擋在更前層）。

**查詢參數**：`page`（預設 0）、`size`（預設 20，上限 100）、`type`（選填，`PRODUCT`／`ROOM`，大小寫不拘；無法解析回 `422 E-3001`）、`keyword`（選填，比對 `title`／`description`）、`sortBy`（預設 `createdAt`，**未作白名單限制，任意 `Listing` 屬性字串皆可**）、`sortDir`（預設 `DESC`）。無 `keyword` 亦無 `type` 時回傳**全平台**所有 `ACTIVE` 的 Listing（不分租戶）。

**回應**：`Page<ListingResponse>`（見 §3.1；`tenantName`／`tenantSlug` 為 `null`，只有 §4.5 的匿名端點會填入）。

### 4.2 `GET /v2/listings/{id}` — 單一 Listing 詳情

**權限**：公開（Sprint 259 起移除登入要求，DEF-358(a)，比照 PRD §12.7）。只回傳「本身 `ACTIVE` 且所屬租戶也 `ACTIVE`」的項目；其餘情況（不存在／`DRAFT`／`INACTIVE`／`DELETED`／租戶停權）一律回同一個 `404 E-3000`，不區分，避免成為列舉側通道。

### 4.3 `GET /v2/listings/{id}/price` — 動態價格試算

**權限**：`product:read` 或 `room:read`。查詢參數 `checkIn`／`checkOut`（ISO 日期，必填）。回應 `PricingDto.CalculatePriceResponse`：`roomListingId`、`checkInDate`、`checkOutDate`、`nights`、`baseTotal`、`adjustedTotal`、`discount`、`currency`、`breakdown`（逐日 `PriceBreakdown`：`date`／`basePrice`／`adjustedPrice`／`appliedRuleName`／`adjustmentType`／`adjustmentValue`）。語意上僅對 `ROOM` 類型有意義（按日計價），對 `PRODUCT` 呼叫不會報錯但計算結果無業務意義；計價規則見 [API_M12_Dynamic_Pricing.md](./API_M12_Dynamic_Pricing.md)。

### 4.4 `GET /v2/listings/{listingId}/effective-price` — 有效單價試算

**權限**：同 §4.3。查詢參數 `checkDate`（必填）、`stayDays`（預設 1）。回應 `PricingDto.EffectivePriceResponse`：`listingId`、`checkDate`、`stayDays`、`basePrice`、`effectivePrice`、`appliedRuleType`、`appliedRuleId`、`appliedRuleName`。

### 4.5 `GET /v2/public/listings` — 匿名跨店鋪瀏覽

**權限**：公開（`permitAll()`，Sprint 259 新增，DEF-358(a)，與 `StorefrontController` 同一慣例）。查詢參數同 §4.1，但 `sortBy` 僅接受白名單 `createdAt`／`basePrice`（其餘回 `400 E-9000`，與 §4.1 的「不限制」不同）。只回傳 `ACTIVE` 且所屬租戶 `ACTIVE` 的項目。回應額外帶入 `tenantName`／`tenantSlug`（批次查詢本頁所有租戶，避免 N+1）。

### 4.6 `GET /v2/products` — 商品列表

**權限**：`product:read`。查詢參數：`category`／`brand`／`keyword`（皆選填）、`page`（預設 0）、`size`（預設 20，上限 100）、`sortBy`（預設 `createdAt`，接受 `basePrice`／`createdAt`／`updatedAt`，其餘原樣傳給 JPA 排序）、`sortDir`（預設 `DESC`）。

**篩選邏輯與範圍（逐一互斥，非疊加）**：

| 條件 | 查詢範圍 |
|------|---------|
| 有 `keyword` | 呼叫者目前租戶 `+ ACTIVE`，比對 `title`／`description` |
| 無 `keyword`，有 `category` 且有 `brand` | **全平台** `ACTIVE` 商品（不分租戶） |
| 無 `keyword`，僅有 `category` | **全平台** `ACTIVE` 商品（不分租戶） |
| 無 `keyword`，僅有 `brand` | **全平台** `ACTIVE` 商品（不分租戶） |
| 以上皆無 | 呼叫者目前租戶 `+ ACTIVE` |

`category`／`brand` 篩選分支與其他分支範圍不一致，見 §7 DEF-379。

### 4.7 `GET /v2/products/{listingId}` — 商品詳情

**權限**：`product:read`。商品不存在或已 `DELETED` 回 `404 E-3000`。

### 4.8 `POST /v2/products` — 建立商品

**權限**：`product:create`。`RETAIL_ENABLED` 未啟用回 `403 E-2004`；租戶 `ACTIVE` 商品數已達 100 回 `400 E-2009`。請求見 §3.4（`category` 必填）。成功 **201**，`status` 直接為 `ACTIVE`（無 `DRAFT` 階段，見 §1.2 第 3 點）。

### 4.9 `PUT /v2/products/{listingId}` — 更新商品

**權限**：`product:update`，且限同租戶（非 `SUPER_ADMIN` 時 `listing.tenantId` 須等於呼叫者租戶，否則 `403 E-1007`）。所有欄位皆選填、只更新有帶的欄位（`null` 視為不變）；`status` 可任意改成 `DRAFT`／`ACTIVE`／`INACTIVE`／`DELETED`（字串無法解析成列舉回 `400 E-9000`）。僅當「由非 `ACTIVE` 轉入 `ACTIVE`」時才檢查配額（`E-2009`），避免既有 `ACTIVE` 商品因改其他欄位被誤擋。

### 4.10 `DELETE /v2/products/{listingId}` — 軟刪除商品

**權限**：`product:delete`，租戶檢查同 §4.9。實作為 `status → DELETED`，資料不會真的被移除。

### 4.11 `GET /v2/products/{productId}/reviews/stats` — 評分統計

**權限**：`isAuthenticated()`（任何已登入角色，不限 `product:read`）。回應 `ReviewDto.RatingStats`：`listingId`、`averageRating`、`totalReviews`、`rating1Count`～`rating5Count`、`distribution`（`Map<評分,筆數>`）。底層計算屬 M08，見 [API_M08_Review.md](./API_M08_Review.md)。

### 4.12 `POST /v2/products/{listingId}/skus` — 建立 SKU

**權限**：`product:update`（與商品更新共用同一權限字串，非獨立的 `sku:*`）。請求：`skuCode`（必填，≤50）、`specName`（選填，≤100）、`priceOverride`（選填，>0）。`skuCode` 在同一 `listingId` 下需唯一，重複回對應的驗證/衝突錯誤。成功 **201**。

### 4.13 `GET /v2/products/{listingId}/skus` — SKU 列表

**權限**：`product:read`。回傳該商品全部 SKU（含 `INACTIVE`），不分頁。

### 4.14 `PUT /v2/products/{listingId}/skus/{skuId}` — 更新 SKU

**權限**：`product:update`。可更新 `specName`／`priceOverride`／`status`（`ACTIVE`／`INACTIVE`）。無刪除端點，停用用 `status=INACTIVE`。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | Guest／Buyer | STORE_STAFF | SELLER | HOST | STORE_OWNER | ADMIN | SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|:----:|:----:|:----:|
| 瀏覽（§4.1～4.2、4.5） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 動態價格（§4.3～4.4） | ✅（持有 `product:read`/`room:read` 即可） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 商品列表/詳情（§4.6～4.7） | ✅ | ✅ | ✅ | ❌（無 `product:read`） | ✅ | ✅ | ✅ |
| 建立/更新/刪除商品（§4.8～4.10） | ❌ | ❌ | ✅（僅自己租戶） | ❌ | ✅（僅自己租戶） | ✅（僅自己租戶） | ✅（跨租戶） |
| 評分統計（§4.11） | ✅（限已登入） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SKU 建立/更新（§4.12、4.14） | ❌ | ❌ | ✅（僅自己租戶） | ❌ | ✅（僅自己租戶） | ✅（僅自己租戶） | ✅（跨租戶） |
| SKU 列表（§4.13） | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |

`HOST` 不持有 `product:*`；`SELLER` 不持有 `room:*`（見 [API_M02_Listing_Center.md](./API_M02_Listing_Center.md) §5）。`STORE_STAFF` 僅持有 `PRODUCT_READ`，無任何商品寫入權限。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-3000` | 404 | 找不到 Listing（§4.2、§4.7、§4.9、§4.10） |
| `E-3001` | 422 | 無效的 `type` 篩選值（§4.1） |
| `E-2004` | 403 | `RETAIL_ENABLED` 未對該租戶啟用（§4.8） |
| `E-2009` | 400 | 已達商品配額上限（§4.8、§4.9 轉為 `ACTIVE` 時） |
| `E-1007` | 403 | 跨租戶操作他人商品（§4.9、§4.10） |
| `E-9000` | 400 | 列舉字串無法解析、排序參數不合法（§4.5）、或 Bean Validation 失敗 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| **`GET /v2/products` 的 `category`／`brand` 篩選分支無租戶範圍（本輪新發現，登記為 DEF-379，未修復）** | `ProductService.getProducts`：無篩選與有 `keyword` 的分支都以「呼叫者目前租戶 + ACTIVE」為範圍，但有 `category` 或 `brand`（無 `keyword`）的三個分支改用 `ProductRepository.findByCategory`／`findByBrand`／`findByCategoryAndBrand`，查詢條件只有 `status = ACTIVE`，**完全不比對 `tenantId`**。同一方法內部範圍不一致，持有 `product:read` 的使用者（含 `SELLER`）用 `category`／`brand` 篩選時會看到**其他租戶**的 `ACTIVE` 商品。本輪純文件撰寫過程中的附帶發現，不修復（修法是在 `ProductRepository` 對應 `@Query` 加上 `tenantId` 條件，但是否該讓這三個分支改為「全平台商品分類瀏覽」而非「我的商品」是產品範圍問題，留待決定） |
| **`DRAFT`→發布兩階段上架流程從未實作（本輪新發現，登記為 DEF-380，未修復）** | FRD §3.2「技術實現概要」與 AC-M01-002-1／AC-M01-002-2 描述「先建立 `status=DRAFT` 的 Listing，再呼叫 `PUT /api/v2/dashboard/listings/:id/publish` 轉為 `ACTIVE`」，但 `ProductService.createProduct`／`createProductFromDashboard` 皆寫死建立時 `status=ACTIVE`，沒有任何路徑建立 `DRAFT` 列，後端也沒有任何「發布」端點（`PUT .../publish` 不存在）。`ListingStatus.DRAFT` 列舉值仍可透過 `PUT /v2/products/{listingId}`（`UpdateRequest.status`）事後手動設定，但那是「改」不是建立流程的一部分。是否要實作真正的草稿機制、或把 FRD／驗收標準改為「建立即上架」的現況，屬產品決策，本輪僅記錄不處理 |
| 統一建立入口（`POST /v2/dashboard/listings`）與型別專屬入口（`POST /v2/products`）合約不同 | 見 §1.2 第 2 點、§3.4：`category` 必填與否不同、成功 HTTP 狀態碼不同（200 vs 201）、`CreateListingRequest.additionalFields` 欄位會通過驗證但 `ProductService.createProductFromDashboard` 完全不讀取它，純粹是請求體裡的死欄位 |
| `GET /v2/listings` 的 `sortBy` 無白名單 | 與 §4.5 `GET /v2/public/listings`（有白名單，`DEF-339` 已修復的模式）不同，本端點仍直接把使用者輸入的 `sortBy` 字串交給 `Sort.by()`，非法屬性名稱時的行為未逐一驗證（可能 500 或 `PropertyReferenceException`），本輪未重現測試，僅記錄現況差異 |
| `dimensionsCm` 是自由格式字串 | 非結構化的長/寬/高三欄位，前端需自行約定格式（例如 `"20x10x5"`），後端不解析也不驗證格式 |
| SKU 無刪除端點 | `stock_movements.sku_id` 一旦有異動記錄即無法刪除（V1 schema FK 無 `ON DELETE CASCADE`），比照既有慣例改用 `status=INACTIVE` 停用 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §9.3（M01 商品中心）、§13.1（Phase 1 最小子集：M03+M01+M02+M05） |
| FRD | §3「M01 商品中心」（§3.6 端點表已同步本文件；技術實現概要與驗收標準的 DRAFT/發布敘述與實作不符，見本文件 §7 DEF-380 與 FRD 修訂記錄） |
| 相關追蹤項目 | [DEF-286](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（API 文件完整性，本文件為其範圍內一項）、[DEF-372](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（`ApiRouteDocDriftTest` 覆蓋範圍，本文件新納入）、[DEF-379](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（本文件新發現，商品分類/品牌篩選租戶範圍缺口）、[DEF-380](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（本文件新發現，DRAFT/發布流程未實作） |
| 實作 | `ListingController`／`PublicListingController`／`ProductController`（`api/controller/`）；`ProductService`（`core/product/`）；`ProductSkuService`（`core/product/`） |
| 測試 | `ProductControllerTest`／`ProductServiceTest`（單元）；本文件撰寫過程以讀碼逐行核對，未新增端對端整合測試（純文件工作，見 SPRINT_273_PLAN.md） |

---

**文件狀態**：v2.0（Sprint 273 全面改寫，DEF-286 範圍內補齊 M01）。
