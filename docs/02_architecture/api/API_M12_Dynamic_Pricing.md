# API 規格文件 - M12 動態定價引擎 / API Specification - M12 Dynamic Pricing Engine

> **文件編號**: API-M12
> **文件版本**: v2.8（Sprint 311：DEF-426 依使用者裁決修復〔日曆手動改價 `calendar/price`、`override` 在呼叫者租戶的動態定價開關關閉時回 `E-2004`、不建立規則〕，§1.2 功能開關列、§4.5、§4.8、§6、§7 同步）；v2.7（Sprint 309：DEF-424 依使用者裁決修復〔動態定價開關改由定價引擎以房源／商品所屬店鋪把關，關閉即時失效，購物車／訂房／價格試算一致〕，§1.2 功能開關列、§4.6、§4.8、§7 同步；登記 DEF-426〔日曆手動改價在開關關閉時不生效〕）；v2.6（Sprint 308：補測 DEF-423 的整條鏈〔規則→購物車→訂單金額，真實 PostgreSQL＋Redis〕，§7 DEF-423 列的『讀碼推論』改為實測數字；登記 DEF-424〔店鋪層開關只管建立、不管套用，待使用者裁決〕，§1.2 功能開關列與 §7 同步；只改文件，沒有改程式）；v2.5（Sprint 307：DEF-417 已修復〔上限與重疊檢核改以規則掛載的房源／商品為範圍，原本是全平台〕、DEF-423 已修復〔建立規則時 `roomListingId` 與 `listingId` 兩個 id 都驗證擁有權〕，§1.2、§4.1、§7 同步；另更正各端點錯誤表與 §6 把 `E-4001`～`E-4003` 記成 422 的錯誤——`GlobalExceptionHandler` 的對應表是 400）；v2.4（Sprint 306：DEF-421 的全棧護欄改由後端 Flyway 整合測試承接，§7 DEF-416 列的測試敘述同步更新）；v2.3（Sprint 304：DEF-415 已修復〔建立／更新／刪除／覆蓋／日曆改價先要求呼叫者有真正的店鋪，連不綁房源的規則也一樣〕；v2.2 為 Sprint 303：DEF-411 已修復〔讀取端點排除系統租戶佔位值〕、DEF-414 已修復〔三個寫入端點原本一律 500〕、DEF-416 已修復〔帶房源 id 的全部規則列表 findAll() 全表掃描致 500〕；v2.1 為 Sprint 300 更正 §4.8 誤記的「無租戶擁有權檢查」；v2.0 為 Sprint 266 依實作改寫）
> **建立日期**: 2026-04-09（v1.0）
> **最後更新**: 2026-10-28
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`PricingController`、`DashboardPricingController`（`api/controller/`）、`PricingService`（`core/pricing/`）、`PricingRule`（`domain/model/room/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[PricingServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/pricing/PricingServiceTest.java)、[M12PricingIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M12PricingIntegrationTest.java)、[M12PricingProductIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M12PricingProductIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **取代關係**: 本文件**取代** v1.0 全文。v1.0 的回應封包（`code: 200`／`timestamp`／`requestId` 固定出現）、規則類型列舉（`WEEKDAY`／`WEEKEND`／`HOLIDAY`／`PEAK_SEASON` 等 8 種）、`adjustment{type,value}` 請求/回應結構、API-M12-006 的「陣列覆蓋」請求格式，與實作完全不符，詳見下方修訂註記
> **本文件撰寫範圍說明**：`GET /v2/listings/{id}/price`（`ListingController`）是即時計價在商品/房源詳情頁的呼叫入口，直接呼叫本模組的 `PricingService.calculatePrice`、回應形狀與 §4.6 的 `/calculate` 相同，但該端點本身屬 `ListingController`（M01 範疇），規格見 [API_M01_Product_Center.md](API_M01_Product_Center.md)／[API_M02_Listing_Center.md](API_M02_Listing_Center.md)，不屬於本文件機械防漂移範圍（`ApiRouteDocDriftTest` 只登記 `PricingController`／`DashboardPricingController`）

> **⚠️ 修訂註記（Sprint 266）**：v1.0 與實作有五處重大落差，本版已更正：
> 1. **回應封包整體錯誤**：v1.0 每個範例都寫 `"code": 200`（數字）＋固定出現的 `timestamp`／`requestId`，但實際 `ApiResponse` 是 `success: true` + `data`，`code` 是字串錯誤碼且只在失敗時出現，`requestId` 只在錯誤回應才帶入。此問題同時存在於 [API_M17_Tenant.md](API_M17_Tenant.md) 的舊章節，是同一批 2026-04-09 原始文件共有的落差；
> 2. **規則類型列舉整組錯誤**：v1.0 寫 `WEEKDAY`／`WEEKEND`／`HOLIDAY`／`PEAK_SEASON`／`EARLY_BIRD`／`LAST_MINUTE`／`LONG_STAY`／`OVERRIDE` 共 8 種，實際 `PricingRuleType` enum（`PricingDto.java`）只有 6 種：`WEEKDAY_WEEKEND`（平假日合併為一種類型，不分開）、`SEASONAL`、`EARLY_BIRD`、`LONG_STAY`、`MANUAL_OVERRIDE`、`LAST_MINUTE`；沒有 `HOLIDAY`、`PEAK_SEASON` 這兩種類型；
> 3. **規則的請求/回應結構錯誤**：v1.0 寫 `adjustment: {type, value}` 巢狀物件，實際是扁平的 `config: Map<String, Object>`（鍵名依規則類型自由決定，如 `discountPercent`／`multiplier`／`price`／`minNights`／`minDaysAhead`，有範圍驗證，DEF-268）；
> 4. **API-M12-006「手動覆蓋」的請求格式錯誤**：v1.0 寫一次送一個「日期陣列」`overrides: [{date, price, reason}]`，實際 `RuleOverrideRequest` 是**單一日期範圍**（`startDate`／`endDate`／`overridePrice`／`reason`），不支援不連續日期各自不同價格的批次請求；
> 5. **取消手動覆蓋（原 §6.4）的端點不存在**：v1.0 寫 `DELETE /api/v2/dashboard/pricing/overrides/:date`，全庫查無此路由。覆蓋本質是一筆 `PricingRule`（`ruleType=MANUAL_OVERRIDE`），取消覆蓋的實際做法是對該規則呼叫既有的 `DELETE /v2/dashboard/pricing/rules/{ruleId}`（見 §4.4），詳見 §7 已知限制的欄位落差說明

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M12 |
| **模組名稱** | 動態定價引擎 / Dynamic Pricing Engine |
| **描述** | 房東/賣家為房源/商品設定定價規則（平假日、季節、早鳥、長住、最後一刻、手動覆蓋），依規則即時計算價格；房東可預覽未來定價日曆、對單日設定手動價格 |
| **API 前綴** | 後端的 context path 是 `/api`，所以下列路徑的完整網址是 `/api/v2/dashboard/pricing/...` |
| **功能開關** | **兩道，看的是不同店鋪**（Sprint 309／DEF-424，使用者以互動選項裁決「關閉後立即失效」）。①**建立規則**：`POST /rules`、`POST /calendar/price`、`POST /rules/{ruleId}/override` 這三個會建出規則的端點，第一步都檢查呼叫者租戶（店主＝自己的店鋪）的 `DYNAMIC_PRICING_ENABLED`，未啟用回 `E-2004`、不建立任何規則（日曆手動改價兩個端點自 Sprint 311／DEF-426 起；修復前它們不檢查，開關關閉時回 `200` 卻建出一條不會生效的規則）；`updateRule`／`getRules`／`deleteRule`／`calendar` 本身**不檢查**此開關。②**套用規則**：定價引擎 `PricingService.calculatePrice`／`getEffectivePrice` 以**房源／商品所屬店鋪**（`listing.tenantId`）的開關把關——關閉（含管理員撤銷）時規則一律不套用、連規則都不查，價格就是基準價；重新開啟立即恢復（規則沒有被刪除或停用，查詢端沒有快取）；各店鋪互不影響；平台自營的房源／商品屬於系統租戶，由系統租戶的開關決定。購物車（`RedisCartService`）、訂房計價與整月日曆（`BookingService`）、`GET /v2/listings/{id}/price`、`GET /v2/listings/{id}/effective-price`、`POST /calculate` 都經過這兩個方法，所以處處一致，呼叫端不再自己查開關（原本購物車與訂房查的是**呼叫者租戶**的開關：購物車端點只有 `BUYER` 持有權限，他們的租戶都是系統租戶佔位值，其開關在 Flyway 種子 V1、V6 為開啟，店鋪自己的開關形同虛設）。**連帶**：日曆手動改價（`calendar/price`、`override`）建出的 `MANUAL_OVERRIDE` 也是規則；`DEF-426`（Sprint 311，使用者以互動選項裁決）已讓它們比照 `POST /rules` 在開關關閉時回 `E-2004`。管理員（`SUPER_ADMIN`）代操作時檢查的仍是**呼叫者租戶（系統租戶）**的開關，與 `POST /rules` 一致，不是房源所屬店鋪的開關——所以管理員替開關關閉的店鋪建出的規則，仍可能得到 `200` 卻不生效（未處理，三個端點同一個落差）。實測：Java E2E `StoreToggleGatesApplicationE2ETest`（真實 PostgreSQL，三條價格路徑 × 三種呼叫者）、Playwright `at-pricing-cart-checkout-real.spec.ts`（Flyway 資料庫＋真實 Redis，購物車與訂單金額） |
| **多租戶隔離** | `PricingRule.tenantId` 取自建立者當下租戶。除 `getRules`（Sprint 185／DEF-255 補上）外，建立/更新/刪除/覆蓋皆驗證房源所屬租戶或規則所屬租戶與呼叫者一致（`checkListingTenantOwnership`／`checkRuleTenantOwnership`，Sprint 不詳／DEF-242），`SUPER_ADMIN` 不受限。讀取路徑（`getRules`、定價日曆預覽）另外要求呼叫者有真正的店鋪（`TenantContext.isStoreTenant`，Sprint 303／DEF-411）；寫入端點（建立／更新／刪除／覆蓋／日曆改價）同樣要求呼叫者有真正的店鋪，連不綁房源的規則也一樣（Sprint 304／DEF-415；Sprint 303 為讀取端專用的 `checkListingReadAccess` 已併入 `checkListingTenantOwnership`） |
| **範圍外** | `GET /v2/listings/{id}/price`（M01，見文件頂部範圍說明）；訂單/購物車結帳時的最終生效價格計算（`EffectivePriceResponse`／`getEffectivePrice`，屬 M04/M05 範疇） |

### 1.2 設計重點

1. **規則可綁 `roomListingId` 或 `listingId`，兩者語意不同但共用同一張表**：`PricingRule` 同時有 `roomListingId`／`listingId` 兩個欄位，建立時由呼叫端決定填哪一個（兩者皆可為 null，DTO 未強制至少一個必填）；建立時 `roomListingId` 與 `listingId` **帶了哪個就驗證哪個的擁有權，兩個都帶就兩個都要屬於呼叫者**（`checkListingTenantOwnership` 對每個 id 各呼叫一次；Sprint 307／DEF-423，原本只驗證「`roomListingId`、缺省才用 `listingId`」其中一個，見 §7）。
2. **每間房最多 50 條啟用中規則，同類型時間重疊預設拒絕**（PRD §5.5.1）：新規則與現有同類型規則的 `validFrom`～`validTo` 重疊時，預設回 `E-4001` 要求先編輯舊規則；帶 `confirmOverride: true` 才會**僅軟刪除真正重疊的同類型舊規則**（不重疊的同類型規則各自保留）。**「每間房」的比較範圍是規則掛載的目標**（Sprint 307／DEF-417）：帶 `roomListingId` 以那間房源為單位；沒帶 `roomListingId` 但帶 `listingId`（商品規則）以那個商品為單位，沿用同一個 50 條上限與重疊檢查（PRD §5.5.1 只定義了房源）；兩者都沒帶的規則只和呼叫者自己租戶『同樣不綁』的規則比較。已停用（`isActive=false`）的規則不計入上限也不算重疊。
3. **`config` 是自由的鍵值對，但特定鍵有範圍驗證**（DEF-268）：`discountPercent`（0~100 不含兩端）、`multiplier`／`weekendMultiplier`（0~10）、`price`（>0）、`minNights`／`minDaysAhead`（≥1），違反回 `E-8001`；非這些鍵名的欄位不受驗證，計價時若既存規則的 `config` 違反範圍會被跳過（記 warn log，不中斷整體計價）。
4. **「手動覆蓋」實際有三條互相獨立的建立路徑**，計價時統一只認 `ruleType=MANUAL_OVERRIDE` 的規則：
   - `POST /rules`（`ruleType: MANUAL_OVERRIDE`）：通用規則建立介面的一種類型，走完整的 50 條上限＋重疊檢查；
   - `POST /calendar/price`（§4.8）：針對單一日期，不檢查上限/重疊，直接新建一筆當日規則（可能與其他 `MANUAL_OVERRIDE` 規則重疊，計價時由 priority＋建立時間排序取最新一筆生效）；
   - `POST /rules/{ruleId}/override`（§4.5）：針對一段日期範圍，若範圍內任一天已被預訂（`RoomCalendar.status=BOOKED`）直接擋掉（`E-4002`）。
5. **取消覆蓋沒有專用端點**：三條路徑建立的覆蓋規則都用既有的 `DELETE /rules/{ruleId}` 刪除（軟刪除，`isActive=false`）；但只有 `override`（路徑 3）的回應會回傳新建規則的 `ruleId`，`calendar/price`（路徑 2）的回應**沒有** `ruleId` 欄位，呼叫端需另外呼叫 `GET /rules` 自行比對 `roomListingId`＋日期找到要刪除的規則（見 §7 已知限制）。
6. **計算每日價格只套用「第一個命中的規則」，不疊加多規則**：按 `priority` 降序（同優先級新建立者優先）逐一檢查，命中即套用並停止，不會把早鳥折扣和週末加成同時疊加。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M12-001 | 房東/賣家建立、查詢、更新、刪除定價規則 | `POST`／`GET`／`PUT`／`DELETE /v2/dashboard/pricing/rules` | ✅ 已實作 |
| US-M12-002 | 買家/房東即時計算指定日期區間的價格 | `POST /v2/dashboard/pricing/calculate` | ✅ 已實作 |
| US-M12-003 | 房東預覽未來定價日曆 | `GET /v2/dashboard/pricing/calendar` | ✅ 已實作 |
| US-M12-004 | 房東對單日或日期範圍設定手動覆蓋價格 | `POST /v2/dashboard/pricing/calendar/price`、`POST /v2/dashboard/pricing/rules/{ruleId}/override` | ✅ 已實作 |

FRD 對應 US-M12 編號目前僅概略描述，未逐端點列 AC；上表依實作現況整理。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M12-001 | POST | `/v2/dashboard/pricing/rules` | 建立定價規則 | `room:create` 或 `product:create` |
| API-M12-002 | PUT | `/v2/dashboard/pricing/rules/{ruleId}` | 更新定價規則 | `room:update` 或 `product:update` |
| API-M12-003 | GET | `/v2/dashboard/pricing/rules` | 定價規則列表 | `room:read` 或 `product:read` |
| API-M12-004 | DELETE | `/v2/dashboard/pricing/rules/{ruleId}` | 刪除（軟刪除）定價規則 | `room:delete` 或 `product:delete` |
| API-M12-005 | POST | `/v2/dashboard/pricing/rules/{ruleId}/override` | 以日期範圍手動覆蓋價格 | `room:update` |
| API-M12-006 | POST | `/v2/dashboard/pricing/calculate` | 計算價格 | `room:read` 或 `product:read` |
| API-M12-007 | GET | `/v2/dashboard/pricing/calendar` | 房東預覽定價日曆 | `room:read` 或 `product:read` |
| API-M12-008 | POST | `/v2/dashboard/pricing/calendar/price` | 設定單日手動覆蓋價格 | `room:update` |

共 8 個端點（`PricingController` 7 個＋`DashboardPricingController` 1 個）。**編號不沿用 v1.0**：v1.0 的 `API-M12-001`（`GET /listings/:id/price`）不屬於本文件機械防漂移範圍（見文件頂部說明），其餘 v1.0 編號描述的請求/回應格式與實作不符（見上方修訂註記），故全部重新編號，不與 v1.0 編號對應。

---

## 3. 資料模型 / Data Model

### 3.1 `PricingRule`（表 `pricing_rules`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | 建立者當下租戶 |
| `roomListingId` | UUID | 選填，房源 |
| `listingId` | UUID | 選填，商品（兩者可同時為 null，見 §1.2 第 1 點） |
| `ruleType` | enum `PricingRuleType` | `WEEKDAY_WEEKEND`／`SEASONAL`／`EARLY_BIRD`／`LONG_STAY`／`MANUAL_OVERRIDE`／`LAST_MINUTE` |
| `ruleName` | String | |
| `priority` | Integer | 數字越大越優先，預設 0 |
| `config` | `Map<String,Object>` | 自由鍵值對，特定鍵有範圍驗證（見 §1.2 第 3 點） |
| `validFrom` / `validTo` | LocalDate | 有效期間（含兩端） |
| `isActive` | Boolean | 軟刪除旗標 |
| `createdAt` / `updatedAt` | Instant | |

### 3.2 計價回應的每日明細（`PriceBreakdown`，非獨立資料表，見 §4.6）

`date`／`basePrice`／`adjustedPrice`／`appliedRuleName`／`adjustmentType`／`adjustmentValue`。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/dashboard/pricing/rules` — 建立定價規則

**權限**：`room:create` 或 `product:create`，且限 `roomListingId`／`listingId` **各自**所屬的租戶（兩個都帶就兩個都要屬於呼叫者，Sprint 307／DEF-423；`SUPER_ADMIN` 不受限），呼叫者必須有真正的店鋪（沒有店鋪的呼叫者——含沒有店鋪的 `ADMIN`——一律 `E-1007`，Sprint 304／DEF-415）：連不綁任何房源的規則也一樣，因為它會蓋成呼叫者的租戶，沒有店鋪者建出來的就是系統租戶（平台自營）的規則；這個判斷在查詢房源之前。另檢查 `DYNAMIC_PRICING_ENABLED` feature toggle。

**請求**（`CreateRuleRequest`）：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `roomListingId` / `listingId` | UUID | 選填 | 至少建議填一個，DTO 未強制 |
| `ruleType` | enum | 必填 | 見 §3.1 |
| `ruleName` | String | 必填 | |
| `priority` | Integer | 選填 | 預設 0 |
| `config` | `Map<String,Object>` | 必填 | 見 §1.2 第 3 點 |
| `validFrom` / `validTo` | LocalDate | 必填 | `validTo` 不可早於 `validFrom` |
| `isActive` | Boolean | 選填 | 預設 true |
| `confirmOverride` | Boolean | 選填 | 同類型規則時間重疊時，是否確認軟刪除舊規則，見 §1.2 第 2 點 |

**回應**：`RuleResponse`（`ruleId`／`tenantId`／`roomListingId`／`listingId`／`ruleType`／`ruleName`／`priority`／`config`／`validFrom`／`validTo`／`isActive`／`createdAt`／`updatedAt`）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 功能開關未啟用 | 403 | `E-2004` |
| 房源/商品不存在 | 404 | `E-4000` |
| 非房源/商品所屬租戶，或呼叫者沒有店鋪（含不綁房源的規則，DEF-415） | 403 | `E-1007` |
| `validTo` 早於 `validFrom` | 400 | `E-4003` |
| `config` 鍵值超出範圍 | 422 | `E-8001` |
| 已達 50 條上限（以該房源／該商品的啟用中規則計，DEF-417） | 400 | `E-4001` |
| 同類型時間重疊且未確認覆蓋（只和同一個房源／商品的啟用中規則比，DEF-417） | 400 | `E-4001` |

### 4.2 `PUT /v2/dashboard/pricing/rules/{ruleId}` — 更新定價規則

**權限**：`room:update` 或 `product:update`，且限規則所屬租戶，呼叫者必須有真正的店鋪（沒有店鋪的呼叫者——含沒有店鋪的 `ADMIN`——一律 `E-1007`，Sprint 304／DEF-415）。**不檢查功能開關**（見 §1.2 開頭）。僅提供的欄位會被更新（部分更新語意）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 規則不存在 | 404 | `E-8000` |
| 非規則所屬租戶，或呼叫者沒有店鋪（DEF-415） | 403 | `E-1007` |
| 更新後 `validTo` 早於 `validFrom` | 400 | `E-4003` |
| `config` 鍵值超出範圍 | 422 | `E-8001` |

### 4.3 `GET /v2/dashboard/pricing/rules` — 定價規則列表

**權限**：`room:read` 或 `product:read`。查詢參數：`roomListingId`（選填，帶入時檢查該房源所屬租戶，Sprint 185／DEF-255）、`listingId`（選填，同上）、`activeOnly`（選填，預設 `false`）。兩者皆未帶時，回呼叫者當下租戶的全部規則。**Sprint 303（DEF-411）**：沒有店鋪的呼叫者（租戶是系統租戶佔位值，例如一般買家）不是任何店鋪的店家——兩者皆未帶時回空陣列（不查資料庫），帶 `roomListingId`／`listingId` 時回 `E-1007`（先判斷、不查房源）；`SUPER_ADMIN`（其租戶同為系統租戶）不受限，仍列得出系統租戶名下的規則。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 帶 `roomListingId`/`listingId` 但非該資源所屬租戶，或呼叫者沒有店鋪（DEF-411） | 403 | `E-1007` |

### 4.4 `DELETE /v2/dashboard/pricing/rules/{ruleId}` — 刪除（軟刪除）定價規則

**權限**：`room:delete` 或 `product:delete`，且限規則所屬租戶，呼叫者必須有真正的店鋪（沒有店鋪的呼叫者——含沒有店鋪的 `ADMIN`——一律 `E-1007`，Sprint 304／DEF-415）。設定 `isActive=false`，不實際刪除資料列。**這是取消任何「手動覆蓋」規則的唯一方式**（見 §1.2 第 5 點、§7）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 規則不存在 | 404 | `E-8000` |
| 非規則所屬租戶，或呼叫者沒有店鋪（DEF-415） | 403 | `E-1007` |

### 4.5 `POST /v2/dashboard/pricing/rules/{ruleId}/override` — 以日期範圍手動覆蓋價格

**權限**：`room:update`，且限規則所屬租戶，呼叫者必須有真正的店鋪（沒有店鋪的呼叫者——含沒有店鋪的 `ADMIN`——一律 `E-1007`，Sprint 304／DEF-415）。`ruleId` 是**既有規則的 ID**（覆蓋的目標關聯，非新建規則的依據）。

**請求**（`RuleOverrideRequest`）：`startDate`(必填)、`endDate`(必填，不可早於 `startDate`)、`overridePrice`(必填，須 >0)、`reason`(選填)

**回應**：`RuleOverrideResponse`（`ruleId`——**新建覆蓋規則的 ID**、`roomListingId`、`startDate`、`endDate`、`overridePrice`、`reason`、`status`、`createdAt`）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者租戶的動態定價開關未啟用（Sprint 311／DEF-426；第一個被檢查） | 403 | `E-2004` |
| `endDate` 早於 `startDate` | 400 | `E-4003` |
| 規則不存在 | 404 | `E-8000` |
| 非規則所屬租戶，或呼叫者沒有店鋪（DEF-415） | 403 | `E-1007` |
| 範圍內任一天已被預訂（`RoomCalendar.status=BOOKED`） | 400 | `E-4002` |

### 4.6 `POST /v2/dashboard/pricing/calculate` — 計算價格

**權限**：`room:read` 或 `product:read`。**無租戶擁有權檢查**（與其餘端點不同，任何通過權限字串的使用者皆可對任意 `roomListingId` 計價，供買家端詳情頁即時試算使用）。**房源所屬店鋪的動態定價開關關閉時規則一律不套用**：`adjustedTotal == baseTotal`、`discount = 0`、`breakdown` 每日都沒有規則名（Sprint 309／DEF-424，見 §1.2「功能開關」）；店主自己的預覽也一樣，不會預覽一個不會被收的折扣價。

**請求**（`CalculatePriceRequest`）：`roomListingId`(必填)、`checkInDate`(必填)、`checkOutDate`(必填)、`guestCount`(選填，**目前計價邏輯未使用此欄位**)、`bookingDate`(選填，早鳥/最後一刻判斷的基準日，未填則取今日)

**回應**（`CalculatePriceResponse`）：`roomListingId`／`checkInDate`／`checkOutDate`／`nights`／`baseTotal`／`adjustedTotal`／`discount`／`currency`／`breakdown: List<PriceBreakdown>`（見 §3.2）

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 房源不存在或無 `basePrice` | 404 | `E-4000` |
| `checkOutDate` 不晚於 `checkInDate` | 400 | `E-4003` |

### 4.7 `GET /v2/dashboard/pricing/calendar` — 房東預覽定價日曆

**權限**：`room:read` 或 `product:read`，且限房源所屬租戶（`SUPER_ADMIN` 不受限）；呼叫者必須有真正的店鋪——沒有店鋪的呼叫者即使房源的租戶也是系統租戶也回 `E-1007`（Sprint 303／DEF-411；日曆內容與買家端 `GET /v2/bookings/calendar` 相同，這裡修的是擁有權檢查的一致性，不是外洩）。查詢參數：`roomListingId`(必填)、`startDate`／`endDate`(必填，ISO-8601)，`startDate` 不可早於今天。

**回應**：`List<CalendarResponse>`（`date`／`status`：`AVAILABLE`／`BOOKED`／`BLOCKED`／`MAINTENANCE`／`NOT_OPEN`／`price`／`bookingId`／`originalPrice`／`appliedRuleName`／`priceAdjustmentType`：`DISCOUNT`／`MARKUP`／`NONE`）——僅 `AVAILABLE` 的日期會套用動態定價計算，且依賴**房源所屬店鋪**的 `DYNAMIC_PRICING_ENABLED`（Sprint 309／DEF-424，見 §1.2「功能開關」）：未啟用則規則不套用，`price` 就是基準價，`originalPrice`／`appliedRuleName`／`priceAdjustmentType` 為 `null`（先前這句寫成「`price`＝`originalPrice`」，與實際行為不符——沒有調整時這三欄本來就是 `null`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `startDate` 早於今天 | 400 | `E-4001` |
| 非房源所屬租戶，或呼叫者沒有店鋪 | 403 | `E-1007` |

### 4.8 `POST /v2/dashboard/pricing/calendar/price` — 設定單日手動覆蓋價格

**權限**：`room:update`，且驗證房源所屬租戶與呼叫者一致（`PricingService.setCalendarPrice` 第一步 `checkRuleTenantOwnership`，DEF-242；`SUPER_ADMIN` 不受限；呼叫者須有真正的店鋪，沒有店鋪的 `ADMIN` 一律 `E-1007`（DEF-415）；單元測試 `PricingServiceTest.setCalendarPrice_roomBelongsToDifferentTenant_throwsE1007`）。**（v2.1 更正：v2.0 誤記為「無租戶擁有權檢查」，Sprint 300 讀碼更正，見 §7。）** 不檢查 50 條上限或時間重疊，直接新建一筆當日 `MANUAL_OVERRIDE` 規則（可能與既有覆蓋規則重疊，由 priority/建立時間排序取最新一筆生效，見 §1.2 第 4 點）。

**請求**（`SetCalendarPriceRequest`）：`roomListingId`(必填)、`date`(必填)、`price`(必填，須 >0)、`reason`(選填)

**回應**（`CalendarPriceResponse`）：`roomListingId`／`date`／`price`／`priceType`（固定回 `"MANUAL"`）／`appliedRuleName`／`updatedAt`。**⚠️ 沒有 `ruleId` 欄位**，見 §7。**開關**：第一步檢查呼叫者租戶的 `DYNAMIC_PRICING_ENABLED`，未啟用回 `403 E-2004` 且不建立規則（Sprint 311／DEF-426，比照 `POST /rules`；修復前回 `200` 卻建出一條店鋪開關關閉時不生效的規則，見 §7）。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | 房源/商品所屬租戶 | 任意已登入（持有對應權限字串） | SUPER_ADMIN |
|------|:----:|:----:|:----:|
| 建立規則（§4.1） | ✅ | ❌ | ✅（跨租戶） |
| 更新/刪除規則（§4.2、4.4） | ✅（限規則所屬租戶） | ❌ | ✅（跨租戶） |
| 列表（§4.3） | ✅（帶 id 查詢時） | ✅（不帶 id 查詢時，回自己租戶；沒有店鋪的使用者回空陣列、帶 id 查詢回 `E-1007`，Sprint 303／DEF-411） | ✅（跨租戶） |
| 日期範圍覆蓋（§4.5） | ✅（限規則所屬租戶） | ❌ | ✅（跨租戶） |
| 計算價格（§4.6） | — | ✅（無租戶限制） | ✅ |
| 定價日曆（§4.7） | ✅ | ❌ | ✅（跨租戶） |
| 單日覆蓋（§4.8） | ✅ | ❌ | ✅（跨租戶） |

上表「房源/商品所屬租戶」欄指**有真正店鋪**的呼叫者。沒有店鋪的呼叫者——包含沒有店鋪的 `ADMIN`（租戶內角色，持有 `room:*`／`product:*` 寫入權限，但租戶是系統租戶佔位值）——對所有寫入端點（§4.1、4.2、4.4、4.5、4.8）一律 `E-1007`；有店鋪的 `ADMIN` 管自己店鋪的規則照常（Sprint 304／DEF-415，使用者於 Sprint 303 收尾裁決「比照 Sprint 234 全部擋下」）。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-2004` | 403 | `DYNAMIC_PRICING_ENABLED` 功能開關未啟用（§4.1 建立規則、§4.5 覆蓋、§4.8 單日改價） |
| `E-4000` | 404 | 找不到房源/商品（§4.1、4.6） |
| `E-4001` | 400 | 已達 50 條規則上限、同類型時間重疊未確認覆蓋（§4.1）、日曆查詢 `startDate` 早於今天（§4.7）；`GlobalExceptionHandler.mapErrorCodeToStatus` 把 `E-4001`～`E-4003` 歸在 `BAD_REQUEST`（400），v2.4 以前記載的 422 是錯的（見 §7） |
| `E-4002` | 400 | 覆蓋日期範圍內已有預訂（§4.5） |
| `E-4003` | 400 | 日期區間無效（`validTo`/`endDate` 早於起始日，或 `checkOutDate` 不晚於 `checkInDate`） |
| `E-8000` | 404 | 找不到定價規則 |
| `E-8001` | 422 | `config` 鍵值超出範圍 |

另使用以下跨模組共用錯誤碼：`E-1007`（權限/擁有權不足）。

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `calendar/price` 回應缺少 `ruleId` | 透過此端點建立的覆蓋規則無法從回應直接取得 `ruleId`，取消時需呼叫 `GET /rules` 自行比對 `roomListingId`＋日期找出目標規則再呼叫 `DELETE /rules/{ruleId}`（見 §1.2 第 5 點）。若未來要補一個「依日期取消」的專用端點，屬產品/工程決策，非本文件撰寫範圍 |
| `calculate` 刻意無租戶擁有權檢查（供買家端試算）；**`calendar/price` 有擁有權檢查（v2.0 誤記為「無」，Sprint 300 更正）** | §4.6 計算價格刻意不限租戶，供買家詳情頁即時試算。§4.8 設定單日覆蓋先前被記載為「同樣無檢查、可能是跨租戶寫入漏洞，建議另案登記調查」——Sprint 300 讀碼確認 `PricingService.setCalendarPrice` 第一步就是 `checkRuleTenantOwnership(room.getListing().getTenantId(), isSuperAdmin)`（DEF-242），並有單元測試 `PricingServiceTest.setCalendarPrice_roomBelongsToDifferentTenant_throwsE1007`；所以 v2.0 的記載（含 §4.8 權限說明與權限矩陣）是錯的，已更正。**只讀碼與單元測試，沒有實測 HTTP 層** |
| **擁有權檢查沒有排除系統租戶佔位值（DEF-411）——Sprint 303 已修復（讀取端點）** | Sprint 300 登記時是讀碼推論；Sprint 303 先用真實 PostgreSQL＋完整 HTTP／JWT 鏈實測：管理員（`SUPER_ADMIN`，沒有店鋪歸屬）登入簽發的租戶是系統租戶、他建立的規則蓋成系統租戶；修復前，沒有店鋪的買家與沒有店鋪的 `ADMIN` 對 `GET /rules` 列得出該規則、帶系統租戶房源 id 查詢回 200（應 403）、預覽系統租戶房源日曆回 200（應 403）。修法：`getRules` 無參數分支對「沒有店鋪的非管理員」回空；帶 id 的分支與日曆預覽先要求呼叫者有真正的店鋪，再沿用租戶相等檢查。`/calculate` 刻意不限租戶（買家端的 `GET /v2/listings/{id}/price` 同樣只要求 `room:read`／`product:read`，呼叫的是同一個 `calculatePrice`）不受影響。測試：`PricingSystemTenantIsolationE2ETest`（真實 PostgreSQL，6 個案例）、`PricingServiceTest.StorelessCallerTests`、`BookingServiceOwnerCalendarTest`；13 個突變全數擊殺 |
| **三個寫入端點原本一律 500（DEF-414，Sprint 303 發現並修復）** | `POST /rules`、`POST /calendar/price`、`POST /rules/{ruleId}/override` 建立 `PricingRule` 時只設了影子欄位 `tenantId`（`insertable = false`），INSERT 依據的是 `tenant` 關聯物件，`pricing_rules.tenant_id`（NOT NULL）收到 NULL → `DataIntegrityViolationException` → HTTP 500。既有測試全部把 Repository 換成 mock（`M12PricingIntegrationTest`、`PricingServiceTest`），INSERT 從未真的執行過，所以一直沒被看到；Sprint 303 寫 `DEF-411` 的真實 DB 測試時，固件存檔先撞到同一個約束才發現。修法：三處改設 `.tenant(...)`（`createRule` 先以 `TenantRepository` 查出 `Tenant`）。測試：`PricingRulePersistenceE2ETest`（真實 PostgreSQL，3 個案例，店主視角）。**在測試庫（Hibernate 產生的 schema）與 Flyway 建出的資料庫都重現**：後者是把修復拿掉的等價突變（三個端點皆回 500），不是真正的修復前 JAR |
| **寫入端點的擁有權檢查原本只比「租戶相等」，沒有店鋪的 `ADMIN` 對系統租戶的資源等於同租戶（DEF-415）——Sprint 304 已修復（使用者決定比照 Sprint 234 全部擋下）** | `updateRule`／`deleteRule`／`overridePrice`／`setCalendarPrice`（`checkRuleTenantOwnership`）、`createRule`（`checkListingTenantOwnership`）與 `RoomService`／`ProductService`／`ProductSkuService`／`RoomCalendarService` 的同名檢查，原本都只比「資源的租戶 == 呼叫者的租戶」。BUYER 沒有寫入權限不受影響；沒有店鋪的 SELLER／HOST 在簽發 token 時已降為 BUYER（Sprint 240）；剩下沒有店鋪的 `ADMIN`（租戶內角色，持有 `room:*`／`product:*` 寫入權限）的租戶也是系統租戶佔位值，所以對平台自營資料等於擁有者。**實測（真實 PostgreSQL＋完整 HTTP／JWT 鏈，`PlatformSelfRunWriteIsolationE2ETest`）：修復前 18 個案例全紅**——沒有店鋪的 `ADMIN` 對這五個服務的寫入操作都回 200／201（包含建立不綁房源的規則）。修法：兩個擁有權檢查先要求呼叫者有真正的店鋪（`TenantContext.isStoreTenant`），`SUPER_ADMIN` 照常；`checkListingTenantOwnership` 把這個判斷放在「`targetListingId` 為 null 就略過」**之前**，不綁房源的規則也擋；Sprint 303 讀取端專用的 `checkListingReadAccess` 併入同一個方法。回應訊息一律是錯誤碼的預設訊息「權限不足」（`BusinessException` 的細節依 AI-2418 不外洩），所以測試以「有店鋪的 `ADMIN` 對自己店鋪的同一個操作成功」證明擋下的是擁有權檢查、不是權限註解。測試：`PlatformSelfRunWriteIsolationE2ETest`（18 個案例）、`PricingServiceTest.StorelessCallerWriteTests`（9 個）。 |
| **上限與重疊檢核的範圍原本是全平台，而不是這間房源／這個商品（DEF-417）——Sprint 307 已修復** | `enforceRuleLimitAndConflict` 原本一律用 `findByRoomListingIdAndIsActiveTrue(request.getRoomListingId())`；商品規則只帶 `listingId`、`roomListingId` 是 null，Spring Data JPA 的衍生查詢對 null 參數會產生 `room_listing_id IS NULL`，於是比對範圍變成『全平台所有租戶、所有不綁房源的啟用中規則』。Sprint 303 登記時是讀碼推論；Sprint 307 用真實 PostgreSQL＋完整 HTTP／JWT 鏈實測（`PricingRuleScopeIsolationE2ETest` 第一版 10 個案例對**未修**的程式：**7 個紅燈、3 個對照維持綠燈**）：兩間店鋪各在自己的商品建立同類型、期間重疊的規則，第二間被擋（400 `E-4001`）；**第二間帶 `confirmOverride=true` 時，第一間店的規則被軟刪除（跨租戶寫入）**；同一家店的兩個商品互相衝突，覆蓋會停用另一個商品的規則；『50 條上限』實際是全平台合計；不綁房源也不綁商品的規則跨租戶比較。修法：比較範圍改為規則掛載的目標（見 §1.2 第 2 點），新增 `PricingRuleRepository.findActiveUnboundRulesByTenantId`（`IS NULL` 明寫在查詢裡，避免再被衍生查詢的隱含行為咬一次）。測試：`PricingRuleScopeIsolationE2ETest`（13 個案例，真實 PostgreSQL）、`PricingServiceTest.RuleCreationScopeTests`（9 個案例，以 `verify(never())` 擋住『商品規則又走無房源查詢』）；16 個突變全數擊殺 |
| **建立規則只驗證 `roomListingId ?: listingId` 其中一個，兩個 id 同時帶入時 `listingId` 沒有被驗證（DEF-423）——Sprint 307 讀 `createRule` 時發現並修復** | 帶自己的房源 id、同時夾帶別家店的商品 id，`createRule` 回 201 並把規則掛在別家店的商品上（DEF-242 的另一條路徑；DTO 對兩個 id 沒有任何限制）。**實測（修復前）：別家商品的 `GET /v2/listings/{id}/effective-price` 從 100.00 變成 1.0**（規則 `MANUAL_OVERRIDE`、`price=1`）；三項斷言（狀態碼應為 403、別家商品不得有規則、有效售價不變）全紅。購物車在呼叫者租戶的 `DYNAMIC_PRICING_ENABLED` 開啟時以同一個 `getEffectivePrice` 重算商品單價、結帳用購物車小計算金額。**Sprint 308 補測到訂單為止**（真實 PostgreSQL＋Redis，`frontend/e2e/at-pricing-cart-checkout-real.spec.ts`）：把 `listingId` 的擁有權檢查拿掉（對『兩個 id 並存』的請求等同修復前）重跑，夾帶請求回 **201**，一位全新消費者的購物車單價、購物車總額、訂單明細單價、訂單總額**全是 1**（原價 1000），購物車的 `appliedRuleName` 是夾帶的那條規則；修復後同一個請求回 403 `E-1007`，購物車與訂單都是原價。付款金額取自訂單總額是讀碼推論，沒有跑到付款。前端建立表單只送 `roomListingId`，不受影響；全庫掃描沒有其他請求 DTO 有同型的雙 id 欄位。修法：兩個 id 只要帶了就都驗證擁有權（單一 id 的行為不變）。測試：`PricingRuleScopeIsolationE2ETest.ruleCannotTargetAnotherStoresProductAlongsideMyOwnRoom`、兩個單一 id 的對照（DEF-242 原路徑，先前沒有真實 DB 層測試）、`PricingServiceTest.RuleCreationScopeTests` |
| **店鋪層的 `DYNAMIC_PRICING_ENABLED` 原本只管『建立規則』，不管『套用』——關閉後既有規則仍套用在消費者身上（DEF-424，Sprint 308 發現並實測，Sprint 309 依使用者裁決修復）** | **原行為**：套用端（購物車 `RedisCartService`、訂房 `BookingService`）查的是 `FeatureToggleService.isFeatureEnabled`，它讀 `TenantContext` 的**呼叫者租戶**。購物車端點只有 `BUYER` 持有（`RolePermissionMapping` 第 41～43 行），所以購物車使用者的租戶都是系統租戶佔位值，其 `DYNAMIC_PRICING_ENABLED` 在 Flyway 種子（V1 第 521 行、V6 第 58 行）為開啟——實質上是全平台的『套用』總開關，店鋪自己的開關只擋新建規則。這**不違反當時的 PRD**（權限矩陣只寫『Service 層在建規則前查詢』），是規格沒定義的產品語意，所以 Sprint 308 只記錄、不擅自改。**裁決**（Sprint 309，使用者以互動選項選 (B)『關閉後立即失效』；另兩個選項是 (A) 維持現狀、(C) 撤銷時順便停用既有規則）：規則只在『房源／商品所屬店鋪』的開關開啟時套用。**做法**：定價引擎一處把關——`PricingService.calculatePrice`、`getEffectivePrice` 以 `listing.getTenantId()` 查 `isFeatureEnabledForTenant`，關閉時規則清單直接是空的（連規則都不查），回應形狀與『沒有規則生效』相同；購物車與訂房**移除**自己查呼叫者租戶開關的檢查（`RedisCartService`、`BookingService` 的 `FeatureToggleService` 欄位因此成為孤兒一併移除），`GET /listings/{id}/price`、`effective-price`、`POST /calculate` 自動一致（否則詳情頁會顯示一個不會被收的折扣價）。**測試**：`PricingServiceTest.StoreToggleGatesApplicationTests`（8 案例：店鋪 id 刻意與呼叫者租戶不同，呼叫者租戶的開關用 `lenient` 誘餌並 `verify(never())` 證明沒被讀）、`StoreToggleGatesApplicationE2ETest`（真實 PostgreSQL，5 案例；三條消費者價格路徑各對三種呼叫者比價——呼叫者租戶開關開啟、關閉、系統租戶）、Playwright `at-pricing-cart-checkout-real.spec.ts` E2E-PCART-03／04（Flyway 資料庫＋真實 Redis：關閉後購物車與訂單回原價、重新開啟立即恢復、另一家店鋪不受影響）；突變驗證 5 個單元／E2E 突變與 1 個 JAR 層突變全數擊殺。**連帶影響**：①日曆手動改價建出的 `MANUAL_OVERRIDE` 規則也隨店鋪開關（`DEF-426`）；②平台管理員替店鋪商品建立的規則（規則的租戶是系統租戶）同樣受該店鋪的開關約束；③系統租戶的開關不再是全平台總開關，只決定平台自營房源／商品；④Hibernate 測試庫的系統租戶開關是**關閉**（`TestDatabaseInitializer`）、Flyway 種子是**開啟**——兩種資料庫的前提不同，後端 Java E2E 因此不依賴它 |
| **日曆手動改價（`calendar/price`、`override`）在店鋪動態定價開關關閉時建立得成功，但建出的 `MANUAL_OVERRIDE` 規則不生效（DEF-426，Sprint 309 連帶發現；Sprint 311 依使用者裁決修復）** | **原行為**：這兩個端點不檢查開關，而 `DEF-424` 讓所有規則——包含它們建出的手動覆蓋——只在店鋪開關開啟時套用，所以開關關閉的店鋪呼叫 `calendar/price` 會得到 `200`，價格卻沒有變（建立一條不會生效的規則等於騙人）。**修復（使用者選 (a)）**：`PricingService.setCalendarPrice`／`overridePrice` 第一步比照 `createRule` 檢查呼叫者租戶的 `DYNAMIC_PRICING_ENABLED`，未啟用回 `403 E-2004` 且不建立規則、不記稽核。測試：`PricingServiceTest.CalendarManualOverrideToggleTests`（2，開關關閉時不查房源、不寫規則）、`StoreToggleGatesApplicationE2ETest.calendarManualOverrideEndpoints_followTheOwnersStoreToggle`（真實 PostgreSQL；同一位店主、同一個房源、同一組請求，只翻轉開關就從 `403` 變 `200`）。**代價**：預設關閉的新店鋪從此不能用 API 改單日價（前端本來就沒有這兩個端點的入口，畫面不受影響）。**限制**：`updateRule`／`deleteRule` 仍不查開關（本題不涵蓋）；`SUPER_ADMIN` 代操作時檢查的是呼叫者租戶（系統租戶）的開關，與 `POST /rules` 一致，不是房源所屬店鋪的開關（§1.2 末段） |
| `E-4001`／`E-4002`／`E-4003` 的 HTTP 狀態碼（Sprint 307 更正：先前記為 422 且推論『與 §4.7 的 400 不一致』，是文件記錯，不是行為不一致） | v2.4 以前的記載說 `GlobalExceptionHandler` 把 `E-4001` 歸為 422 群組、與 §4.7 的 400 不一致。**讀 `GlobalExceptionHandler.mapErrorCodeToStatus`（第 223～226 行）並實測**：`E-4001`、`E-4002`、`E-4003` 都在 `BAD_REQUEST`（400）群組，只有 `E-8001` 是 422；`POST /rules` 的上限／重疊衝突實測回 400（Sprint 307 `PricingRuleScopeIsolationE2ETest` 以 `statusCode(400)` 斷言；PRD TC-PR-003 與 `M12PricingIntegrationTest` 也是 400）。本文件各端點錯誤表與 §6 原先把這三個碼寫成 422，已更正（`E-4002`／`E-4003` 在其他端點只讀了對應表，沒有逐端點實測 HTTP）。仍屬命名怪異：`E-4001` 的預設訊息是「房源日曆衝突」，用在「不支援過去日期」「規則數量上限」的語意不完全貼切 |
| `createRule` 的 `roomListingId`／`listingId` 未強制至少一個必填 | DTO 沒有 `@AssertTrue` 等跨欄位驗證，兩者皆為 null 時可以建立一筆「不綁定任何房源/商品」的規則，實務上是否有意義未明（這類規則的上限與重疊檢核只和呼叫者自己租戶同樣不綁的規則比較，Sprint 307／DEF-417） |
| **`getRules` 的 `activeOnly=false`（預設）分支走 `findAll()` 全表掃描（DEF-416）——Sprint 303 已修復，不只是效能問題** | 帶 `roomListingId` 查詢且未指定 `activeOnly=true` 時，原本 `findAll()` 把**全表所有租戶**的規則載入記憶體再過濾；Flyway 建出的資料庫原本帶著 `V5__Test_Data_Init.sql` 的一列 `rule_type = 'WEEKEND_SURCHARGE'`（不在 Java 列舉 `PricingRuleType` 內；**Sprint 305 的 `V94` 之後新建的資料庫已不再有它，尚未套用 `V94` 的既有資料庫仍有**），只要載入到它，整個請求就 `IllegalArgumentException` → HTTP 500，任何店主以房源篩選查「全部規則」都會失敗。Hibernate 產生的測試庫對 `rule_type` 有 CHECK 約束放不進這種值，所以後端的真實 PostgreSQL 整合測試重現不出來；Sprint 303 在 Flyway 全棧上跑 DEF-414 的驗證時才第一次看到（後端日誌的堆疊指向 `PricingService.getRules` 的 `findAll()`）。修法：新增衍生查詢 `PricingRuleRepository.findByRoomListingId`，只載入這間房源的規則。測試：`PricingServiceTest.RuleListingQueryTests`（`verify(never()).findAll()`）、真實全棧 `frontend/e2e/at-pricing-rules-real.spec.ts`（在 Flyway 資料庫上；`V94` 之後它在新建的 Flyway 資料庫上已沒有壞資料列可觸發，不再負責這個回歸）、`PricingRulesCorruptRowFlywayIntegrationTest`（Sprint 306，`DEF-421`：在 Flyway 實跑的獨立資料庫裡自己種一列列舉外的 `rule_type`，走真實 HTTP 驗證房源、商品、店鋪三種列表查詢都不被拖垮；六個分支各退回 `findAll()` 的突變全數擊殺） |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件改寫後新增 `PricingController`／`DashboardPricingController` 至 `GUARDED` 清單，從此有常態機械防漂移 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §5.5.1（定價規則上限與衝突規則） |
| 相關規格 | [API_Error_Codes.md](../API_Error_Codes.md)、[API_M01_Product_Center.md](API_M01_Product_Center.md)（`GET /listings/{id}/price` 的呼叫入口） |
| 實作 | `PricingController`、`DashboardPricingController`（`api/controller/`）；`PricingService`（`core/pricing/`）；`PricingRule`（`domain/model/room/`） |
| 契約測試 | [PricingServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/pricing/PricingServiceTest.java)、[M12PricingIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M12PricingIntegrationTest.java)、[M12PricingProductIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M12PricingProductIntegrationTest.java)、真實 PostgreSQL：[PricingSystemTenantIsolationE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/PricingSystemTenantIsolationE2ETest.java)（DEF-411）、[PricingRulePersistenceE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/PricingRulePersistenceE2ETest.java)、[PricingRuleScopeIsolationE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/PricingRuleScopeIsolationE2ETest.java)（DEF-417／DEF-423）、Flyway 資料庫：[PricingRulesCorruptRowFlywayIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/PricingRulesCorruptRowFlywayIntegrationTest.java)（DEF-421）（DEF-414）；真實全棧（Flyway 資料庫）：[at-pricing-rules-real.spec.ts](../../../frontend/e2e/at-pricing-rules-real.spec.ts)（DEF-414／416／411）；真實 PostgreSQL（店鋪開關把關套用，DEF-424）：[StoreToggleGatesApplicationE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/StoreToggleGatesApplicationE2ETest.java)；真實 PostgreSQL：[PlatformSelfRunWriteIsolationE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/PlatformSelfRunWriteIsolationE2ETest.java)（DEF-415，涵蓋房源、商品、SKU、定價、日曆五個服務的寫入）；真實全棧（規則→購物車→訂單金額，真實 Redis）：[at-pricing-cart-checkout-real.spec.ts](../../../frontend/e2e/at-pricing-cart-checkout-real.spec.ts)（DEF-423 整條鏈、DEF-424 關閉後立即失效） |

---

**文件狀態**：v2.8（Sprint 311：DEF-426 修復）；v2.7（Sprint 309：DEF-424 修復、新增 DEF-426）；v2.6（Sprint 308：DEF-423 整條鏈實測、登記 DEF-424，只改文件）；v2.5（Sprint 307：DEF-417 上限與重疊檢核改以房源／商品為範圍、DEF-423 建立規則兩個 id 都驗證擁有權）；v2.4（Sprint 306：DEF-421，DEF-416 的回歸護欄改放後端 Flyway 整合測試）；v2.3（Sprint 304：DEF-415 修復、§7 DEF-415 改為已修復；v2.2 為 Sprint 303：DEF-411／DEF-414／DEF-416 修復；v2.0 為 Sprint 266 依實作全面改寫，DEF-368，取代 v1.0 全文）。
