# API 規格文件 - M02 房源中心 / API Specification - M02 Listing Center

> **文件版本**: v2.0（Sprint 273 依實作全面改寫，DEF-286 範圍）
> **建立日期**: 2026-04-09（v1.0）
> **最後更新**: 2026-10-15
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`RoomController`／`RoomCalendarController`（`api/controller/`）、`RoomService`／`RoomCalendarService`（`core/room/`、`core/booking/`）、`RoomDto`／`BookingDto.MaintenanceRequest`（`api/dto/`）、`Room`／`RoomCalendar`（`domain/model/`）
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **取代關係**: 本文件**取代** v1.0 全文；v1.0 的端點、請求/回應格式與錯誤碼表皆為杜撰，與實作差距極大

> **⚠️ 修訂註記（Sprint 273）**：v1.0 與實作至少有以下落差，本版已更正：
> 1. **不存在的端點**：`GET /v2/listings/:id/calendar`（日曆查詢實際是 `GET /v2/bookings/calendar`，屬 M06，見 §1.2 第 3 點）、`PUT /v2/dashboard/listings/:id/status`（不存在任何「狀態更新」端點，更新狀態要用 `PUT /v2/rooms/{listingId}` 的泛用 `UpdateRequest.status`）；
> 2. **程式有、v1.0 完全沒提的端點**：型別專屬入口 `GET/POST/PUT/DELETE /v2/rooms`（v1.0 誤以為房源只能經統一入口 `/v2/dashboard/listings` 操作）、`DELETE /v2/rooms/{listingId}/open-window`（清除開放窗）、房源日曆維護窗口 `POST/DELETE /v2/dashboard/rooms/{roomListingId}/maintenance`；
> 3. **建立房源不會初始化任何 `room_calendar` 記錄**：v1.0／FRD §4.2 寫「建立 room_calendar 初始記錄（未來 365 天）」，但 `RoomService.createRoom`／`createRoomFromDashboard` 只建立 `Room` 本體，完全不觸碰 `room_calendar` 表；該表採「缺列視為可訂」的隱性語意（見 §3.2），詳見 §7 DEF-380；
> 4. **錯誤碼表全部換成實際的**：v1.0 的 `E-4001`／`E-2020`／`E-4041` 都不是後端真正會回的碼。

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M02 |
| **模組名稱** | 房源中心 / Listing Center |
| **描述** | 房源專屬的 CRUD／開放窗管理（`RoomController`）、日曆維護窗口（`RoomCalendarController`）。跨類型瀏覽（`/v2/listings`、`/v2/public/listings`）與 PRODUCT／ROOM 共用的統一建立入口屬 M01，見 [API_M01_Product_Center.md](./API_M01_Product_Center.md) |
| **API 前綴** | 完整網址為 `/api/v2/rooms/...`、`/api/v2/dashboard/rooms/{roomListingId}/maintenance` |
| **多租戶隔離** | `RoomController` 的所有端點（含 `getRooms` 的全部篩選分支）皆以呼叫者目前租戶為範圍，與 M01 的 `ProductController` 在 `category`/`brand` 篩選分支不受租戶限制（見 M01 §7 DEF-379）不同——本模組沒有相同的落差 |
| **範圍外** | 日曆/空房查詢與動態計價見 §1.2 第 3／4 點的交叉引用；房源建立/更新的統一入口（`POST /v2/dashboard/listings`）見 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md) |

### 1.2 設計重點

1. **Room 特化模型**：與 M01 共用 `listings` 表，房源專屬欄位（`location`／`latitude`／`longitude`／`maxGuests`／`amenities`／`checkInTime`／`checkOutTime`／`roomCount`／`openUntilDate`／`bookingWindowDays`）存在獨立的 `rooms` 表（`listingId` 同時是 PK／FK）。
2. **兩條平行的建立路徑，合約不同**：型別專屬 `POST /v2/rooms`（`RoomController`，`RoomDto.CreateRequest`，`title`／`location`／`basePrice` 必填，成功回 **201**）與統一入口 `POST /v2/dashboard/listings`（`DashboardListingController`，`CreateListingRequest`，成功回 **200**，見 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md)）。與 M01 商品側同款差異，見該文件 §1.2 第 2 點。
3. **日曆／空房查詢不在本模組**：`GET /v2/bookings/calendar`（整月日曆狀態）、`GET /v2/bookings/availability`（日期區間可用性）路由在 `BookingController`，屬 M06，詳見 [API_M06_Booking.md](../API_M06_Booking.md)。v1.0 誤以為日曆查詢是 `GET /v2/listings/:id/calendar`，該路徑不存在。
4. **動態計價不在本模組**：`GET /v2/listings/{id}/price`／`.../effective-price` 宣告在 M01 的 `ListingController`，計算規則屬 M12，見 [API_M01_Product_Center.md](./API_M01_Product_Center.md) §4.3～4.4、[API_M12_Dynamic_Pricing.md](./API_M12_Dynamic_Pricing.md)。
5. **建立時一律直接 `ACTIVE`，不會初始化日曆**：`createRoom`／`createRoomFromDashboard` 皆寫死 `.status(Listing.ListingStatus.ACTIVE)`，且**完全不建立任何 `room_calendar` 列**。`room_calendar` 採「缺列視為 `AVAILABLE`」的隱性語意（`isDateRangeAvailable`：沒有記錄即視為可訂），日曆列只在被訂房（`bookDateRange`）或房東主動標記封鎖/維護（本模組 §4）時才會真正寫入。FRD §4.2 描述的「建立時初始化未來 365 天日曆」與「DRAFT→發布」兩階段流程皆不存在，詳見 §7 DEF-380（與 M01 同一登記）。
6. **房源「不可訂」有三種互相獨立的機制**，彼此不是同一回事：

   | 機制 | 操作端點 | 資料語意 | 現況 |
   |------|----------|----------|------|
   | 日曆格狀態 `BLOCKED` | `RoomCalendarService.blockDateRange`／`unblockDateRange` | 逐日 `room_calendar.status` | **無任何 Controller 暴露，零生產呼叫點**（全庫 `grep` 確認），僅存在於 Service 層與測試，見 §7 |
   | 日曆格狀態 `MAINTENANCE` | 本模組 §4（`POST`／`DELETE /v2/dashboard/rooms/{roomListingId}/maintenance`） | 逐日 `room_calendar.status`，若該日已被預訂會連帶在對應 `Booking.statusFlags` 標記 `under_maintenance=true` | 唯一真正可由 API 觸發的日曆格封鎖機制 |
   | 開放窗 `openUntilDate`／`bookingWindowDays` | `RoomController` 的 `create`／`update`／`DELETE .../open-window` | `rooms` 表欄位，限制「多遠的未來」可被訂，與日曆格狀態無關 | 正常運作中（Sprint 47） |

7. **日曆維護的租戶擁有權檢查（DEF-256，Sprint 185）**：`markMaintenance`／`unmarkMaintenance` 會檢查 `roomListingId` 所屬租戶是否為呼叫者租戶（非 `SUPER_ADMIN` 時），避免任一持有 `room:update` 的使用者封鎖他人房源。
8. **缺列補齊（DEF-360，Sprint 255）**：`markMaintenance`／`blockDateRange` 對「從未被訂過、完全無 `room_calendar` 記錄」的房源，會先以 `fillMissingCalendarEntries` 補建預設 `AVAILABLE` 的列再轉為目標狀態，避免回 200 但實際靜默空操作。`unmarkMaintenance`／`unblockDateRange`／`releaseDateRange`（回復預設狀態）不適用此邏輯，缺列本身就等同已是 `AVAILABLE`。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M02-001 | Guest／Buyer 瀏覽房源列表與詳情 | `GET /v2/listings?type=ROOM`、`GET /v2/public/listings?type=ROOM`（屬 M01） | ✅ 已實作 |
| US-M02-002 | Host／StoreOwner 建立、更新、下架房源 | `POST/PUT/DELETE /v2/rooms`、`POST /v2/dashboard/listings` | ✅ 已實作（無 DRAFT/發布兩階段，見 §7） |
| US-M02-003 | Host 篩選自己店鋪的房源（人數/地區/關鍵字） | `GET /v2/rooms` | ✅ 已實作 |
| US-M02-004 | Host 設定開放窗（多遠的未來可被訂） | `POST/PUT /v2/rooms`、`DELETE /v2/rooms/{listingId}/open-window` | ✅ 已實作（Sprint 47） |
| US-M02-005 | Host 標記/解除房源維護狀態 | `POST/DELETE /v2/dashboard/rooms/{roomListingId}/maintenance` | ✅ 已實作 |
| US-M02-006 | 查詢房源日曆與空房 | 見 [API_M06_Booking.md](../API_M06_Booking.md) | ✅ 已實作（不在本文件範圍） |

對應 PRD §9.4（M02 房源中心）、§13.1（Phase 1 最小子集）；FRD §4 詳述功能需求（§4.6 端點表已依本文件同步更正，技術實現概要與驗收標準仍描述未實作的 DRAFT/發布與日曆初始化流程，見 FRD 修訂記錄與本文件 §7）。

---

## 2. API 端點總覽 / Endpoints Summary

| 方法 | 路徑（`/api` 之後） | 描述 | 權限 | 成功 |
|------|--------------------|------|------|:----:|
| **GET** | `/v2/rooms` | 店鋪房源列表（分頁／`maxGuests`／`location`／`keyword`／排序；一律限呼叫者租戶 + ACTIVE） | `room:read` | 200 |
| **GET** | `/v2/rooms/{listingId}` | 房源詳情 | `room:read` | 200 |
| **POST** | `/v2/rooms` | 建立房源（型別專屬入口，直接 `ACTIVE`） | `room:create` | 201 |
| **PUT** | `/v2/rooms/{listingId}` | 更新房源（含手動變更 `status`／開放窗） | `room:update` | 200 |
| **DELETE** | `/v2/rooms/{listingId}` | 軟刪除房源（`status → DELETED`） | `room:delete` | 200 |
| **DELETE** | `/v2/rooms/{listingId}/open-window` | 清除開放窗（恢復無限制） | `room:update` | 200 |
| **POST** | `/v2/dashboard/rooms/{roomListingId}/maintenance` | 標記日期區間為維護中 | `room:update` | 200 |
| **DELETE** | `/v2/dashboard/rooms/{roomListingId}/maintenance` | 解除維護狀態 | `room:update` | 200 |

跨類型瀏覽（`GET /v2/listings?type=ROOM`、`GET /v2/public/listings?type=ROOM`、動態定價）屬 M01；日曆/空房查詢屬 M06；統一建立入口（`POST /v2/dashboard/listings`）獨立記載於 [API_Listing_Unified_Create.md](./API_Listing_Unified_Create.md)。

未帶 token 回 `401`；已登入但沒有該權限字串或非自己租戶回 `403 E-1007`。

---

## 3. 資料模型 / Data Model

### 3.1 `Room`（表 `rooms`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `listingId` | UUID | PK，同時是 FK → `listings.id` |
| `location` | String | ≤200，建立時必填 |
| `latitude` / `longitude` | Double | 選填，-90~90 / -180~180 |
| `maxGuests` | Integer | 選填，未提供預設 **2** |
| `amenities` | `List<String>` | 選填 |
| `checkInTime` / `checkOutTime` | LocalTime | 選填，未提供預設 **15:00** / **11:00** |
| `roomCount` | Integer | 選填，未提供預設 **1** |
| `openUntilDate` | LocalDate | 開放窗（固定日期），`null` = 無限制（Sprint 47） |
| `bookingWindowDays` | Integer | 開放窗（滾動天數），`null` = 無限制（Sprint 47） |
| `createdAt` / `updatedAt` | Instant | |

無 `totalRooms`／`availableRooms`／`rating`（v1.0 杜撰）；可訂房間數與評分分屬 M06／M08。

### 3.2 `RoomCalendar`（表 `room_calendar`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `listingId` | UUID | FK → `listings.id` |
| `calendarDate` | LocalDate | |
| `status` | enum `RoomCalendarStatus` | `AVAILABLE`／`BOOKED`／`BLOCKED`／`MAINTENANCE`，見 §3.3 |
| `bookingId` | UUID | 僅 `BOOKED`／曾為 `BOOKED` 後轉 `MAINTENANCE` 時非空 |
| `price` | BigDecimal | **恆為 `NULL`**（Sprint 45 移除手動日價寫入路徑，死欄位；真正的人工覆蓋價走 M12 的 `PricingService`） |

**缺列視為 `AVAILABLE`**：這張表不是「每個房源每天都有一列」，而是只在發生狀態變化時才寫入；查詢可訂性的程式碼（`isDateRangeAvailable`）把「找不到記錄」視為可訂。

### 3.3 列舉 / Enums

| 列舉 | 值 | 說明 |
|------|-----|------|
| `RoomCalendarStatus` | `AVAILABLE`、`BOOKED`、`BLOCKED`、`MAINTENANCE` | `BLOCKED` 無任何 Controller 暴露，見 §1.2 第 6 點、§7 |
| `ListingStatus`（`Listing` 共用，見 M01 §3.5） | `DRAFT`、`ACTIVE`、`INACTIVE`、`DELETED` | `DRAFT` 無任何建立路徑會產生，見 §7 |

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `GET /v2/rooms` — 店鋪房源列表

**權限**：`room:read`。查詢參數：`maxGuests`（選填，`>=` 篩選）、`location`（選填，模糊比對）、`keyword`（選填，比對 `title`／`description`）、`page`（預設 0）、`size`（預設 20，上限 100）、`sortBy`（預設 `createdAt`，**無白名單**）、`sortDir`（預設 `DESC`）。**所有篩選組合皆以 `Specification` 疊加在「呼叫者目前租戶 + ACTIVE」的基礎條件上**（與 M01 的 `ProductController` 不同，見 §1.1）。

### 4.2 `GET /v2/rooms/{listingId}` — 房源詳情

**權限**：`room:read`。房源不存在或非 `ROOM` 型別回 `404 E-4000`。

### 4.3 `POST /v2/rooms` — 建立房源

**權限**：`room:create`。`BOOKING_ENABLED` 未啟用回 `403 E-2004`；租戶 `ACTIVE` 房源數已達 `AppConstants.QUOTA_MAX_ROOMS`（**20**）回 `400 E-2009`。請求：`title`（必填，≤200）、`location`（必填，≤200）、`basePrice`（必填，>0）、其餘見 §3.1（皆選填，套用預設值）。成功 **201**，`status` 直接為 `ACTIVE`，**不建立任何 `room_calendar` 列**（見 §1.2 第 5 點）。

### 4.4 `PUT /v2/rooms/{listingId}` — 更新房源

**權限**：`room:update`，且限同租戶（非 `SUPER_ADMIN` 時 `listing.tenantId` 須等於呼叫者租戶，否則 `403 E-1007`）。所有欄位選填、只更新有帶的欄位；`openUntilDate`／`bookingWindowDays` 沿用「非 `null` 才更新」慣例，**無法用此端點清除**（清除要用 §4.6）。`status` 可任意改成 `DRAFT`／`ACTIVE`／`INACTIVE`／`DELETED`。由非 `ACTIVE` 轉入 `ACTIVE` 時才檢查配額。

### 4.5 `DELETE /v2/rooms/{listingId}` — 軟刪除房源

**權限**：`room:delete`，租戶檢查同 §4.4。實作為 `status → DELETED`。

### 4.6 `DELETE /v2/rooms/{listingId}/open-window` — 清除開放窗

**權限**：`room:update`，租戶檢查同 §4.4。一次性把 `openUntilDate`／`bookingWindowDays` 都清回 `null`（無限制），`PUT`（§4.4）的「非 null 才更新」慣例無法達成此效果，故另立專屬端點（比照 `CartController.clearCart` 模式，Sprint 57）。

### 4.7 `POST /v2/dashboard/rooms/{roomListingId}/maintenance` — 標記維護狀態

**權限**：`room:update`，且限房源所屬租戶（檢查方式同 §4.4，`403` 時訊息為「Not authorized to manage this room's maintenance status」）。請求：`startDate`／`endDate`（皆必填，`endDate` 早於 `startDate` 回 `400 E-4003`）。把區間內每日 `room_calendar.status` 設為 `MAINTENANCE`；若某日原為 `BOOKED`，保留 `bookingId` 並在對應 `Booking.statusFlags` 標記 `under_maintenance=true`（供 Admin Dashboard 查詢，M09 通知系統上線前的替代方案）。區間內缺列的日期會先補建 `AVAILABLE` 再轉為 `MAINTENANCE`（DEF-360）。已是 `MAINTENANCE` 的日期跳過（idempotent）。

### 4.8 `DELETE /v2/dashboard/rooms/{roomListingId}/maintenance` — 解除維護狀態

**權限**：同 §4.7。查詢參數：`startDate`／`endDate`（皆必填）。區間內原為 `MAINTENANCE` 且保留 `bookingId` 的日期恢復 `BOOKED`（並清除 `under_maintenance` 標記）；否則恢復 `AVAILABLE`。非 `MAINTENANCE` 的日期跳過。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | Guest／Buyer | STORE_STAFF | HOST | SELLER | STORE_OWNER | ADMIN | SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|:----:|:----:|:----:|
| 房源列表/詳情（§4.1～4.2） | ❌（無 `room:read`） | ✅ | ✅ | ❌（無 `room:read`） | ✅ | ✅ | ✅ |
| 建立/更新/刪除房源（§4.3～4.5） | ❌ | ❌ | ✅（僅自己租戶） | ❌ | ✅（僅自己租戶） | ✅（僅自己租戶） | ✅（跨租戶） |
| 清除開放窗（§4.6） | ❌ | ❌ | ✅（僅自己租戶） | ❌ | ✅（僅自己租戶） | ✅（僅自己租戶） | ✅（跨租戶） |
| 維護狀態標記/解除（§4.7～4.8） | ❌ | ❌ | ✅（僅自己租戶） | ❌ | ✅（僅自己租戶） | ✅（僅自己租戶） | ✅（跨租戶） |

`SELLER`／`STORE_STAFF` 不持有 `room:create/update/delete`；`GUEST`／`BUYER` 不持有 `room:read`（與 M01 的 `product:read` 不同，`GUEST`／`BUYER` 確實持有 `room:read`，詳見下方更正）。

> **更正**：依 `RolePermissionMapping`，`GUEST`／`BUYER` 實際**持有** `ROOM_READ`（與 `PRODUCT_READ` 同組授予），上表「❌」指的是 `GET /v2/rooms` 即便技術上通過 `@PreAuthorize`，查詢範圍仍限呼叫者「目前租戶」（見 §4.1），對無店鋪的買家／訪客而言該租戶是系統租戶佔位值，實質上查不到有意義的資料，與 M01 `ProductController` 同一情況；房源瀏覽的實際公開入口是 `GET /v2/listings?type=ROOM`／`GET /v2/public/listings?type=ROOM`（屬 M01）。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-4000` | 404 | 找不到房源（§4.2、§4.4～4.8） |
| `E-4003` | 400 | 維護區間日期範圍無效（`endDate` 早於 `startDate`，§4.7） |
| `E-2004` | 403 | `BOOKING_ENABLED` 未對該租戶啟用（§4.3） |
| `E-2009` | 400 | 已達房源配額上限（§4.3、§4.4 轉為 `ACTIVE` 時） |
| `E-1007` | 403 | 跨租戶操作他人房源（§4.4～4.8） |
| `E-9000` | 400 | 列舉字串無法解析、或 Bean Validation 失敗 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| **`blockDateRange`／`unblockDateRange` 零生產呼叫點** | `RoomCalendarService.blockDateRange`／`unblockDateRange`（把日曆格設為／解除 `BLOCKED`）全庫搜尋沒有任何 Controller 呼叫它們，僅存在於 Service 層本身與測試（Sprint 260，DEF-363 修復悲觀鎖時意外發現）。`RoomCalendarStatus.BLOCKED` 列舉值因此在生產環境不可達；舊版 API 文件描述的「房東封鎖日期」功能目前只能透過 §4.7 的 `MAINTENANCE` 機制間接達成類似效果 |
| **`DRAFT`→發布兩階段上架流程從未實作（與 M01 同一登記，DEF-380，未修復）** | 見 M01 §7。本模組額外的落差：FRD §4.2「技術實現概要」寫「建立 room_calendar 初始記錄（可用日期，未來 365 天）」，但 `createRoom`／`createRoomFromDashboard` 完全不建立任何 `room_calendar` 列（§1.2 第 5 點），該敘述同樣失真 |
| `GET /v2/rooms` 的 `sortBy` 無白名單 | 與 M01 §7 同款落差，直接把使用者輸入交給 `Sort.by()`，未逐一驗證非法欄位名稱時的行為 |
| 開放窗與日曆格狀態是兩套獨立機制 | 見 §1.2 第 6 點比較表，維護/封鎖不會影響開放窗設定，反之亦然；前端若只檢查其中一種會誤判房源可訂性 |
| `room_calendar.price` 恆為 `NULL` | Sprint 45 移除的死欄位，手動日價調整唯一路徑是 M12 的 `PricingService`，見 §3.2 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §9.4（M02 房源中心）、§13.1（Phase 1 最小子集） |
| FRD | §4「M02 房源中心」（§4.6 端點表已同步本文件；技術實現概要與驗收標準的 DRAFT/發布/日曆初始化敘述與實作不符，見本文件 §7 DEF-380 與 FRD 修訂記錄） |
| 相關追蹤項目 | [DEF-286](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（API 文件完整性）、[DEF-372](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（`ApiRouteDocDriftTest` 覆蓋範圍，本文件新納入）、[DEF-380](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（DRAFT/發布與日曆初始化流程未實作）、[DEF-363](../../04_planning/DEFERRED_ITEMS_TRACKER.md)（`blockDateRange`／`markMaintenance` 併發鎖修復，Sprint 260） |
| 實作 | `RoomController`（`api/controller/`）；`RoomCalendarController`（`api/controller/`）；`RoomService`（`core/room/`）；`RoomCalendarService`（`core/booking/`） |
| 測試 | `RoomControllerTest`／`RoomServiceTest`／`RoomCalendarServiceTest`（單元）；本文件撰寫過程以讀碼逐行核對，未新增端對端整合測試（純文件工作，見 SPRINT_273_PLAN.md） |

---

**文件狀態**：v2.0（Sprint 273 全面改寫，DEF-286 範圍內補齊 M02）。
