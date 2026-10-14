# API 規格文件 - M08 評價與社交互動 / API Specification - M08 Review & Social Interaction

> **文件編號**: API-M08
> **文件版本**: v1.1（Sprint 264：DEF-373／374 已修復，見下方說明；Sprint 263 首次建立，v1.0；Sprint 15/16 規劃時僅留 `API_Index.md` 內嵌的片段表格與一份已標記過期的 [TC_M08_Review.md](../../03_testing/TC_M08_Review.md)，從未真正建立獨立規格文件，DEF-365）
> **建立日期**: 2026-10-14
> **最後更新**: 2026-10-14（Sprint 264）
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`ReviewController`、`BookingReviewController`（`api/controller/`）、`ReviewService`、`ReviewReplyService`、`BookingReviewService`（`core/review/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[ReviewServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewServiceTest.java)、[ReviewReplyServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewReplyServiceTest.java)、[BookingReviewServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/BookingReviewServiceTest.java)、[ReviewAuthorizationRealDbIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewAuthorizationRealDbIntegrationTest.java)、[ReviewSearchRealDbIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewSearchRealDbIntegrationTest.java)、[M08ReviewIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewIntegrationTest.java)、[M08ReviewImageIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewImageIntegrationTest.java)、[M08ReviewHelpfulVotingIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewHelpfulVotingIntegrationTest.java)、[M08ReviewStatsIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewStatsIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **編號說明（重要）**: `API_Index.md` 舊表格與 [TC_M08_Review.md](../../03_testing/TC_M08_Review.md) 兩者都使用裸編號 `API-M08-NNN`，但**彼此代表不同端點**（例如 `API-M08-001` 在前者是「回覆評價」、在後者是「建立評價」，詳見 §7 已知限制）——本文件不沿用任一方，改用全新、不衝突的 `API-M08-R-NNN`（ReviewController）／`API-M08-BR-NNN`（BookingReviewController）編號

> **⚠️ 本文件撰寫範圍說明**：`GET /v2/products/{productId}/reviews/stats`（`ProductController`）是評分統計功能在產品模組下的一個精簡包裝端點（直接呼叫 `ReviewService.getRatingStats`，功能與 §4.6 完全重複），**不屬於本文件機械防漂移範圍**（`ApiRouteDocDriftTest` 只登記 `ReviewController`／`BookingReviewController`，`ProductController` 的路由屬 M01 範疇），僅於 §7 已知限制提及。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M08 |
| **模組名稱** | 評價與社交互動 / Review & Social Interaction |
| **描述** | 買家對已完成的訂單（商品/房源）或訂房撰寫星級評價（1-5 分、文字、至多 9 張圖片，限商品/房源評價），商家/房東可回覆；買家可對評價投「有用」票；商家可查詢與標記評價處理狀態；支援多維度評價搜尋。 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/reviews/...`、`/api/v2/booking-reviews/...` |
| **多租戶隔離** | `Review`／`BookingReview` 實體本身皆**無 `tenant_id` 欄位**；買家建立、查詢、編輯、刪除自己的評價不受租戶限制。僅「回覆」（§4.8、§4.17）與「評價管理」（§4.11～4.12）經由 listing 的 tenant 關聯做範圍檢查：非管理員限自己租戶，`ADMIN`／`SUPER_ADMIN`（`isCurrentUserAdmin()`，Sprint 73 DEF-028/029/030）可跨租戶 |
| **範圍外** | 評論權重演算法（過濾水軍）、檢舉機制——PRD §6.2 列為本模組範圍，但未實作任何相關邏輯或端點（見 §7、§8） |

### 1.2 設計重點

1. **建立評價需購買證明**：`ReviewService.verifyPurchase` 要求呼叫者提供 `orderId` 或 `bookingId`（至少一個），且該訂單/訂房必須屬於呼叫者本人、並引用相同的 `listingId`，否則 `E-1007`（DEF-331(b) 修復，取代先前「任何人都能評價任何商品」的缺陷）。
2. **每筆訂單/訂房＋商品恰好評價一次**：防重複檢查是 `(orderId, listingId)` 或 `(bookingId, listingId)` 的組合，不是「每個使用者對每個商品僅一次」——同一買家若有兩筆不同訂單買了同一商品，可各評價一次。
3. **商品/房源評價（`Review`）與訂房評價（`BookingReview`）功能不對稱**：前者有編輯、刪除、統計、搜尋、圖片管理（上限 9 張＋媒體驗證）；後者**僅能建立與查詢單筆**——`BookingReviewService` 雖有 `getBookingReviews`（列表）、`deleteBookingReview`（軟刪除）、`getAverageRatingByBookingId`／`countByBookingId`（統計）等方法，但**沒有任何 Controller 端點呼叫它們**，圖片也無數量上限或媒體存在性驗證。這是實作現況的真實落差，不是文件遺漏（見 §7）。
4. **「有用」投票以原子 SQL 去重**：`ReviewRepository.registerHelpfulVote` 用單一 `UPDATE`（JSONB 合併＋從去重後的 key 數量重新計算 `helpfulCount`）取代先前的讀-改-寫實作，避免高併發下票數流失。
5. **回覆每則評價僅一次，授權邏輯已統一**：`ReviewReply` 有 DB 唯一索引（`uk_review_replies_review_id`）防止重複回覆（`E-1086`）；`BookingReview.hostReply` 是單一可覆寫欄位，重複呼叫不報錯、直接覆寫前一則。**誰能回覆的判斷邏輯**：`ReviewReplyService.createReply`（§4.8）、`ReviewService.checkReviewManagementAuthorization`（標記已/未處理，§4.11）、`BookingReviewService.replyToBookingReview`（§4.17）三者原先不一致（§4.8 原僅認定確切 `ownerId`），已於 Sprint 264（DEF-373）統一為**同租戶或管理員**。
6. **評分範圍僅 DTO 層驗證**：`rating` 的 1-5 範圍只靠 `@Min(1) @Max(5)` 驗證，資料庫欄位沒有對應的 `CHECK` 約束。
7. **編輯與刪除皆無時間窗限制**：評價作者可在建立後任何時間編輯或刪除（軟刪除，`isVisible=false`），不像部分電商平台限制「訂單完成後 N 天內才能評價/修改」。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M08-001 | 買家對已完成訂單撰寫商品/房源評價（含評分、文字、至多 9 張圖片） | `POST /v2/reviews` | ✅ 已實作 |
| US-M08-002 | 買家編輯／刪除自己的評價 | `PUT`／`DELETE /v2/reviews/{reviewId}` | ✅ 已實作 |
| US-M08-003 | 查詢評價列表、詳情、統計、多維度搜尋 | `GET /v2/reviews/listing/{listingId}`、`.../stats`、`.../search`、`GET /v2/reviews/user/{userId}` | ✅ 已實作 |
| US-M08-004 | 商家／房東回覆評價、查詢回覆 | `POST`／`GET /v2/reviews/{reviewId}/replies` | ✅ 已實作（授權邏輯已於 Sprint 264 統一，DEF-373） |
| US-M08-005 | 商家查詢與標記評價處理狀態 | `GET /v2/reviews/managed`、`PUT /v2/reviews/{reviewId}/handle` | ✅ 已實作 |
| US-M08-006 | 買家對評價投「有用」票 | `POST /v2/reviews/{reviewId}/helpful` | ✅ 已實作 |
| US-M08-007 | 評價圖片管理（新增／刪除／排序） | `POST`／`DELETE /v2/reviews/{reviewId}/images`、`PUT .../images/order` | ✅ 已實作（僅商品/房源評價） |
| US-M08-008 | 買家對已完成訂房撰寫評價與查詢 | `POST /v2/booking-reviews`、`GET /v2/booking-reviews/{reviewId}` | ⚠️ 已實作但無編輯/刪除/統計/搜尋（見 1.2.3） |
| US-M08-009 | 房東回覆訂房評價 | `POST /v2/booking-reviews/{reviewId}/reply` | ✅ 已實作 |

---

## 2. API 端點總覽 / Endpoints Summary

### 2.1 ReviewController（`/v2/reviews`）

| API ID | 方法 | 端點 | 說明 | 角色 |
|--------|:----:|------|------|------|
| API-M08-R-001 | POST | `/v2/reviews` | 建立評價 | Buyer（`order:create`／`booking:create`） |
| API-M08-R-002 | PUT | `/v2/reviews/{reviewId}` | 更新評價 | `order:read`＋本人 |
| API-M08-R-003 | DELETE | `/v2/reviews/{reviewId}` | 刪除評價（軟刪除） | `order:read`＋本人 |
| API-M08-R-004 | GET | `/v2/reviews/listing/{listingId}` | 取得商品/房源評價列表 | 已登入 |
| API-M08-R-005 | GET | `/v2/reviews/user/{userId}` | 取得使用者評價列表 | `order:read`＋本人或管理員 |
| API-M08-R-006 | GET | `/v2/reviews/listing/{listingId}/stats` | 取得評分統計 | 已登入 |
| API-M08-R-007 | GET | `/v2/reviews/listing/{listingId}/search` | 多維度評價搜尋 | 已登入 |
| API-M08-R-008 | POST | `/v2/reviews/{reviewId}/replies` | 商家/房東回覆評價 | `room:update`／`product:update` |
| API-M08-R-009 | GET | `/v2/reviews/{reviewId}/replies` | 取得評價回覆列表 | 已登入 |
| API-M08-R-010 | POST | `/v2/reviews/{reviewId}/helpful` | 標記「有用」 | 已登入 |
| API-M08-R-011 | PUT | `/v2/reviews/{reviewId}/handle` | 標記評價為已/未處理 | `room:update`／`product:update` |
| API-M08-R-012 | GET | `/v2/reviews/managed` | 依處理狀態查詢評價列表 | `room:update`／`product:update` |
| API-M08-R-013 | POST | `/v2/reviews/{reviewId}/images` | 新增評價圖片 | `order:read`＋本人 |
| API-M08-R-014 | DELETE | `/v2/reviews/{reviewId}/images/{imageIndex}` | 刪除評價圖片 | `order:read`＋本人 |
| API-M08-R-015 | PUT | `/v2/reviews/{reviewId}/images/order` | 重新排序評價圖片 | `order:read`＋本人 |

### 2.2 BookingReviewController（`/v2/booking-reviews`）

| API ID | 方法 | 端點 | 說明 | 角色 |
|--------|:----:|------|------|------|
| API-M08-BR-001 | POST | `/v2/booking-reviews` | 建立訂房評價 | Buyer（`booking:create`） |
| API-M08-BR-002 | POST | `/v2/booking-reviews/{reviewId}/reply` | 房東/店主回覆訂房評價 | `room:update`／`booking:update` |
| API-M08-BR-003 | GET | `/v2/booking-reviews/{reviewId}` | 取得訂房評價詳情 | 已登入 |

---

## 3. 資料模型 / Data Models

### 3.1 `Review`（table `reviews`）

| 欄位 | 型別 | 說明 |
|------|------|------|
| `id` | UUID | 主鍵 |
| `listingId` | UUID | 商品/房源（`@ManyToOne` 關聯＋shadow column） |
| `userId` | UUID | 評價作者（`@ManyToOne` 關聯＋shadow column） |
| `orderId`／`bookingId` | UUID | 購買證明來源，**純欄位非 JPA 關聯**，至少其中一個有值；referential integrity 只在 service 層（`verifyPurchase`）檢查，無 FK |
| `reviewType` | enum | `PRODUCT`、`ROOM` |
| `rating` | Integer | 1-5（僅 DTO 驗證，無 DB `CHECK`） |
| `title`／`content` | String | 標題（≤100字）／內容（≤2000字，必填） |
| `images` | List\<String\> | JSONB，至多 9 張 |
| `helpfulVotes` | Map\<String,Integer\> | JSONB，`userId → 1`，用於去重計票 |
| `helpfulCount` | Integer | 由 `helpfulVotes` 的 key 數量計算，非原始累加值 |
| `isVisible` | Boolean | 預設 `true`；軟刪除旗標 |
| `isAnonymous` | Boolean | 預設 `false` |
| `isHandled`／`handledAt`／`handledBy` | — | 商家處理狀態 |

完整 DDL 見 [SRD_Database_Schema.md](../SRD_Database_Schema.md)。

### 3.2 `ReviewReply`（table `review_replies`）

| 欄位 | 型別 | 說明 |
|------|------|------|
| `id` | UUID | 主鍵 |
| `reviewId` | UUID | 關聯評價（`@ManyToOne`＋shadow column），**`@UniqueConstraint`：每則評價僅一筆回覆** |
| `replierId` | UUID | 回覆者 |
| `content` | String | ≤1000字 |

### 3.3 `BookingReview`（table `booking_reviews`）

| 欄位 | 型別 | 說明 |
|------|------|------|
| `id` | UUID | 主鍵 |
| `bookingId` | UUID | 關聯訂房（`@ManyToOne`＋shadow column） |
| `userId` | UUID | 評價作者 |
| `rating` | Integer | 1-5 |
| `title`／`content` | String | 與 `Review` 同規則 |
| `images` | List\<String\> | JSONB，**無數量上限、無媒體存在性驗證**（與 `Review.images` 不對稱，見 §7） |
| `isVisible` | Boolean | 軟刪除旗標，**但目前無任何端點能設為 `false`**（§7） |
| `hostReply`／`hostRepliedAt` | String／Instant | 單一可覆寫欄位，非獨立回覆表；**無資料庫唯一約束防止覆寫** |

**無 `@UniqueConstraint` 於 `(bookingId, userId)`**：一人對同一訂房僅能評價一次是 service 層檢查（`findByBookingIdAndUserId`），非 DB 約束。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/reviews` — 建立評價

**權限**：`order:create` 或 `booking:create`。請求：`listingId`（必填）、`orderId`／`bookingId`（至少一個，須屬本人且對應同一 `listingId`）、`rating`（必填，1-5）、`title`（選填，≤100字）、`content`（必填，≤2000字）、`images`（選填，≤9張）、`isAnonymous`（選填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `listingId` 對應的商品/房源不存在 | 404 | `E-3000` |
| `orderId`／`bookingId` 不存在、不屬於本人，或與 `listingId` 不符 | 403 | `E-1007` |
| 同 `orderId`＋`listingId` 已評價過 | 409 | `E-1093`（訊息固定寫「您已評價過此商品」，即使 `reviewType=ROOM` 亦同，訊息未區分） |
| 同 `bookingId`＋`listingId` 已評價過 | 409 | `E-1094` |
| 圖片超過 9 張 | 400 | `E-1088` |
| 圖片 `mediaId` 無效（媒體不存在） | 400 | `E-1089` |

### 4.2 `PUT /v2/reviews/{reviewId}` — 更新評價

**權限**：`order:read`，且限評價本人。請求欄位皆選填（patch 語意）：`rating`、`title`、`content`、`images`。**無編輯時間窗限制**，建立後任何時間皆可修改。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非評價本人 | 403 | `E-1007` |
| 圖片驗證失敗（超限／無效） | 400 | `E-1088`／`E-1089` |

### 4.3 `DELETE /v2/reviews/{reviewId}` — 刪除評價（軟刪除）

**權限**：`order:read`，且限評價本人或管理員（`isCurrentUserAdmin()`，Sprint 264 DEF-374 修復：原實作缺少管理員例外分支，與程式碼註解意圖不符）。設定 `isVisible=false`，不實際刪除資料列。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非評價本人且非管理員 | 403 | `E-1007` |

### 4.4 `GET /v2/reviews/listing/{listingId}` — 取得商品/房源評價列表

**權限**：`isAuthenticated()`，任意已登入使用者。查詢參數：`page`（預設 0）、`size`（預設 10）、`minRating`（選填）。只回傳 `isVisible=true` 的評價。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `listingId` 不存在 | 404 | `E-3000` |

### 4.5 `GET /v2/reviews/user/{userId}` — 取得使用者評價列表

**權限**：`order:read`，且限本人或管理員（`isCurrentUserAdmin()`，DEF-030 修復）。查詢參數：`page`、`size`。`averageRating`／`totalReviews` 固定回 `null`（此端點不計算統計）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 非本人且非管理員 | 403 | `E-1007` |

### 4.6 `GET /v2/reviews/listing/{listingId}/stats` — 取得評分統計

**權限**：`isAuthenticated()`。回傳 `averageRating`（無評價時為 `null`，非 `0`）、`totalReviews`、`rating1Count`～`rating5Count`、`distribution`。結果以 `@Cacheable("ratingStats")` 快取（同步快取讀取，非訊息佇列式非同步預先計算，見 §7 與 PRD 的差異）。無錯誤碼（商品/房源不存在時回傳全 0／`null` 的統計，不拋例外）。

### 4.7 `GET /v2/reviews/listing/{listingId}/search` — 多維度評價搜尋（Sprint 18 US-003）

**權限**：`isAuthenticated()`。查詢參數：`keyword`（標題或內容關鍵字）、`minRating`／`maxRating`、`startDate`／`endDate`（ISO-8601）、`hasImages`、`hasReply`、`sortBy`（`CREATED_AT`／`RATING`／`HELPFUL_COUNT`）、`sortDir`（`ASC`／`DESC`）、`page`、`size`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `listingId` 不存在 | 404 | `E-3000` |
| `minRating`／`maxRating` 範圍無效（如 min > max，或超出 1-5） | 400 | `E-1095` |

### 4.8 `POST /v2/reviews/{reviewId}/replies` — 商家/房東回覆評價

**權限**：`room:update` 或 `product:update`，**且限評價所屬 listing 的同租戶或管理員**（與 §4.11／§4.17 一致，Sprint 264 DEF-373 修復：原僅認定確切 `ownerId`，導致同租戶的其他共同擁有人被鎖死，見 1.2.5）。每則評價僅限一次回覆（DB 唯一索引）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非同租戶且非管理員 | 403 | `E-1007` |
| 評價已有回覆 | 409 | `E-1086` |

### 4.9 `GET /v2/reviews/{reviewId}/replies` — 取得評價回覆列表

**權限**：`isAuthenticated()`。回傳 list（目前每則評價至多一筆，但回應形狀仍是列表）；無回覆時回空陣列，不拋例外。

### 4.10 `POST /v2/reviews/{reviewId}/helpful` — 標記「有用」

**權限**：`isAuthenticated()`。以原子 SQL（`registerHelpfulVote`）依呼叫者 `userId` 去重計票，同一使用者重複呼叫不會重複計入；目前**沒有「取消有用票」的對應端點**。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |

### 4.11 `PUT /v2/reviews/{reviewId}/handle` — 標記評價為已/未處理

**權限**：`room:update` 或 `product:update`，**且限評價所屬 listing 的同租戶或管理員**（`checkReviewManagementAuthorization`，DEF-028 修復；與 §4.8／§4.17 現已採同一套邏輯，Sprint 264 DEF-373）。查詢參數：`handled`（預設 `true`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非同租戶且非管理員 | 403 | `E-1007` |

### 4.12 `GET /v2/reviews/managed` — 依處理狀態查詢評價列表

**權限**：`room:update` 或 `product:update`。非管理員僅能查自己租戶名下 listing 的評價（`findByIsHandledAndTenantId`，DEF-029 修復）；`ADMIN`／`SUPER_ADMIN` 沿用跨租戶查詢。查詢參數：`isHandled`（預設 `false`）、`page`（預設 0）、`size`（預設 20）。

### 4.13 `POST /v2/reviews/{reviewId}/images` — 新增評價圖片

**權限**：`order:read`，且限評價本人。單次新增一張，須通過媒體存在性驗證。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非評價本人 | 403 | `E-1091` |
| 媒體不存在 | 400 | `E-1089` |
| 新增後將超過 9 張 | 400 | `E-1088` |

### 4.14 `DELETE /v2/reviews/{reviewId}/images/{imageIndex}` — 刪除評價圖片

**權限**：`order:read`，且限評價本人。`imageIndex` 為陣列索引（0-based）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非評價本人 | 403 | `E-1091` |
| `imageIndex` 超出範圍 | 404 | `E-1090` |

### 4.15 `PUT /v2/reviews/{reviewId}/images/order` — 重新排序評價圖片

**權限**：`order:read`，且限評價本人。請求 `imageUrls`（1-9 筆），**須與既有圖片集合完全相同（僅排序不同）**，否則視為無效操作而非部分更新。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 評價不存在 | 404 | `E-1087` |
| 非評價本人 | 403 | `E-1091` |
| 數量與既有不符 | 400 | `E-1088` |
| 集合內容與既有不同（非純排序） | 400 | `E-1089` |

### 4.16 `POST /v2/booking-reviews` — 建立訂房評價

**權限**：`booking:create`。請求：`bookingId`（必填，須屬本人）、`rating`（必填，1-5）、`title`（選填）、`content`（必填）、`images`（選填，**無數量上限、無媒體驗證**，與 §4.1 不對稱）、`isAnonymous`（選填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `bookingId` 不存在 | 404 | `E-3000` |
| 訂房不屬於本人 | 403 | `E-1007` |
| 已評價過此訂房 | 409 | `E-1094` |

### 4.17 `POST /v2/booking-reviews/{reviewId}/reply` — 房東/店主回覆訂房評價

**權限**：`room:update` 或 `booking:update`，**且限評價所屬訂房 listing 的同租戶或管理員**（比照 §4.11，DEF-331(c) 修復；§4.8 現已採同一套邏輯，Sprint 264 DEF-373）。`hostReply` 為單一欄位，**重複呼叫會直接覆寫前一則回覆，不報錯**（無類似 `E-1086` 的重複回覆防護）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂房評價不存在 | 404 | `E-1092` |
| 非同租戶且非管理員 | 403 | `E-1007` |

### 4.18 `GET /v2/booking-reviews/{reviewId}` — 取得訂房評價詳情

**權限**：`isAuthenticated()`。**查詢不過濾 `isVisible`**——即使該筆被軟刪除（目前無任何端點能觸發此狀態，見 §7），直接以 ID 查詢仍會回傳。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂房評價不存在 | 404 | `E-1092` |

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | 評價/訂房評價本人 | Listing 確切擁有者 | 同租戶管理角色 | ADMIN／SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|
| 建立（§4.1、§4.16） | ✅（Buyer） | ❌ | ❌ | ❌ |
| 刪除（§4.3） | ✅ | ❌ | ❌ | ✅（Sprint 264 DEF-374 修復） |
| 編輯／圖片管理（§4.2、4.13～4.15） | ✅ | ❌ | ❌ | ❌ |
| 查詢列表／詳情／統計／搜尋（§4.4、4.6～4.7、4.9、4.18） | ✅ | ✅ | ✅ | ✅（任意已登入） |
| 查自己評價列表（§4.5） | ✅ | — | — | ✅（可查任何人） |
| 回覆（§4.8） | ❌ | ✅（同租戶即可，非限確切擁有者） | ✅ | ✅（跨租戶） |
| 標記已/未處理（§4.11） | ❌ | ✅（同租戶） | ✅ | ✅（跨租戶） |
| 依處理狀態查列表（§4.12） | ❌ | ✅（限自己租戶） | ✅ | ✅（跨租戶） |
| 有用投票（§4.10） | ✅（任意已登入，非限本人） | ✅ | ✅ | ✅ |
| 訂房評價回覆（§4.17） | ❌ | ✅（同租戶） | ✅ | ✅（跨租戶） |

**Sprint 264（DEF-373）更新**：§4.8、§4.11、§4.17 三個端點的授權邏輯原先不一致（§4.8 曾要求「存於 `listing.ownerId` 的確切使用者帳號」，§4.11／§4.17 是「同租戶的任何有權限角色」），已統一為後者。單一擁有者的店鋪不受影響，但多人共同經營（`STORE_STAFF`／共同 `STORE_OWNER`）的店鋪下，§4.8 現在也能正常操作，不再被鎖死。

---

## 6. 錯誤碼 / Error Codes

M08 專屬錯誤碼（完整對照表與預設訊息見 [API_Error_Codes.md](../API_Error_Codes.md)）：

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1086` | 409 | 評價已有回覆（§4.8 重複回覆） |
| `E-1087` | 404 | 找不到評價（`Review`） |
| `E-1088` | 400 | 評價圖片數量超過上限（9 張） |
| `E-1089` | 400 | 評價圖片無效（媒體不存在，或重排序時集合不符） |
| `E-1090` | 404 | 找不到指定索引的評價圖片 |
| `E-1091` | 403 | 非評價本人操作圖片 |
| `E-1092` | 404 | 找不到訂房評價（`BookingReview`） |
| `E-1093` | 409 | 已評價過此商品（同 `orderId`＋`listingId`；訊息固定，`ROOM` 類型亦同） |
| `E-1094` | 409 | 已評價過此訂房（§4.1 的 `bookingId` 路徑或 §4.16） |
| `E-1095` | 400 | 搜尋評分範圍無效（§4.7） |

另使用以下跨模組共用錯誤碼：`E-1006`（找不到使用者）、`E-1007`（權限/擁有權不足）、`E-3000`（找不到商品/房源）。

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| 訂房評價功能明顯落後商品/房源評價 | `BookingReview` 無編輯、無刪除端點（`deleteBookingReview` 服務方法存在但零呼叫點）、無統計端點（`getAverageRatingByBookingId`／`countByBookingId` 存在但零呼叫點）、無搜尋、圖片無數量上限與媒體驗證 |
| `GET /v2/booking-reviews/{reviewId}` 不過濾軟刪除 | 即使 `isVisible=false`（目前無端點可觸發），直接查詢仍會回傳；與 `Review` 系列端點一律過濾 `isVisible=true` 的慣例不一致 |
| ~~刪除評價無管理員例外~~（**DEF-374**，✅ Sprint 264 已修復） | `deleteReview`／`deleteBookingReview` 原始碼註解寫「檢查是否是本人或是管理員」，但實作缺少管理員分支，與註解意圖不符；已補上 `isCurrentUserAdmin()` 例外，兩處皆修復（單元＋真實 DB 整合測試，含突變驗證） |
| ~~回覆授權邏輯三處不一致~~（**DEF-373**，✅ Sprint 264 已修復） | §4.8 原要求「listing 確切擁有者」，§4.11／§4.17 是「同租戶或管理員」；已將 §4.8 統一為後者（使用者拍板：放寬以符合既有 DEF-331(c) 的同型前例，避免鎖死共同擁有人） |
| `API-M08-NNN` 裸編號在既有文件彼此衝突（**DEF-375**，未修復） | `API_Index.md` 舊表格與 [TC_M08_Review.md](../../03_testing/TC_M08_Review.md) 都用 `API-M08-001～004` 等裸編號，但代表完全不同的端點（例如 `API-M08-001`：前者是回覆評價、後者是建立評價）。本文件改用不衝突的新編號（見文件頂部「編號說明」），但兩份舊文件彼此的衝突本身未被更正 |
| `GET /v2/products/{productId}/reviews/stats`（`ProductController`）與 §4.6 功能重複 | 兩個端點呼叫同一個 `ReviewService.getRatingStats`，`productId` 被當作 `listingId` 使用，屬歷史遺留的重複介面，非本文件機械防漂移範圍（見文件頂部範圍說明） |
| 評分範圍僅應用層驗證 | `rating` 1-5 只有 `@Min`／`@Max` DTO 驗證，資料庫欄位無 `CHECK` 約束，直接寫入資料庫（如未來的批量匯入）可能繞過此限制 |
| PRD 的評論權重演算法與檢舉機制未實作 | PRD §6.2 列為 M08 範圍的「評論權重算法 (過濾水軍)」「檢舉機制」，程式碼中查無任何對應邏輯或端點（見 §8 與 FRD §6D.7） |
| 本文件撰寫前自動化守門未覆蓋此模組 | `ApiRouteDocDriftTest` 的 `GUARDED` 清單先前只含 M03～M07，本文件完成後新增 `ReviewController`／`BookingReviewController`（DEF-372 範圍內的一環） |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.1／§6.2 模組總覽（評價與社交互動，P2）、§8.1.6 資料表（`reviews`／`booking_reviews`）、§9.9 API 群組摘要、§13.5／§14.4 MoSCoW／Phase 3（Could Have） |
| FRD | §6D 評價與社交互動（BR-M08-01～10） |
| 相關規格 | [API_Error_Codes.md](../API_Error_Codes.md) |
| 實作 | `ReviewController`、`BookingReviewController`（`api/controller/`）；`ReviewService`、`ReviewReplyService`、`BookingReviewService`（`core/review/`）；`Review`、`ReviewReply`、`BookingReview`（`domain/model/review/`） |
| 契約測試 | [ReviewServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewServiceTest.java)、[ReviewServiceSearchTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewServiceSearchTest.java)、[ReviewReplyServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewReplyServiceTest.java)、[BookingReviewServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/BookingReviewServiceTest.java)、[ReviewAuthorizationRealDbIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewAuthorizationRealDbIntegrationTest.java)、[ReviewSearchRealDbIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewSearchRealDbIntegrationTest.java)、[ReviewServiceCacheIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewServiceCacheIntegrationTest.java)、[M08ReviewIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewIntegrationTest.java)、[M08ReviewImageIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewImageIntegrationTest.java)、[M08ReviewHelpfulVotingIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewHelpfulVotingIntegrationTest.java)、[M08ReviewStatsIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewStatsIntegrationTest.java) |
| 歷史文件（已知與本文件編號衝突，見 §7） | [TC_M08_Review.md](../../03_testing/TC_M08_Review.md)（Sprint 244 起已標記過期，保留原樣） |

---

**文件狀態**：v1.1（Sprint 264：DEF-373／374 已修復；Sprint 263 新建，DEF-365，v1.0）。
