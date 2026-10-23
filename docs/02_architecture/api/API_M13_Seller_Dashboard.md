# API 規格文件 - M13 商家工作台 / API Specification - M13 Seller Dashboard

> **文件編號**: API-M13
> **文件版本**: v1.2（Sprint 301：DEF-407 已修復——「營收」口徑統一為已付款、未取消、未退款的五個狀態，§1.2／§3.1～3.3／§7 同步；並登記 DEF-412：`netRevenue` 與退款的算法有兩處錯；v1.1＝Sprint 300，§7「營收三種口徑」補上實測與 DEF-407，僅文件；v1.0 為 Sprint 275 首次建立，首次有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-10-23
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`SellerDashboardController`／`AnalyticsController`（`api/controller/`）、`SellerDashboardService`（`core/seller/`）、`AnalyticsService`（`core/analytics/`）、`TenantStripeConnectService`（`core/tenant/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[SellerDashboardServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/seller/SellerDashboardServiceTest.java)、[SellerDashboardServiceCacheTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/seller/SellerDashboardServiceCacheTest.java)、[AnalyticsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/analytics/AnalyticsServiceTest.java)、[AnalyticsRealDbIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/analytics/AnalyticsRealDbIntegrationTest.java)、[TenantStripeConnectServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/tenant/TenantStripeConnectServiceTest.java)、[M13SellerDashboardIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M13SellerDashboardIntegrationTest.java)、[M13SellerStripeConnectIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M13SellerStripeConnectIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **FRD 狀態**：FRD（`E-Commerce_FRD_v1.0.md`）完全無 M13 章節，僅 PRD §4（Operations）/§9.9 提及

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M13 |
| **模組名稱** | 商家工作台 / Seller Dashboard |
| **描述** | 店家經營儀表板摘要、營收/訂單/商品統計分析、Stripe Connect Express 收款帳戶開通與狀態查詢 |
| **API 前綴** | `/api/v2/seller/dashboard/...`、`/api/v2/dashboard/...`（`AnalyticsController`，與 M01/M02/M12 的 `/dashboard/*` 共用前綴但路徑其餘部分不重疊） |
| **多租戶隔離** | `SellerDashboardController`／`AnalyticsController` 兩者皆以 `TenantContext.getCurrentTenant()` 隔離；Stripe Connect 另在 Service 層強制「必須是真正店鋪租戶的 `STORE_OWNER`」（§1.2 第 4 點） |

### 1.2 設計重點

1. **兩個儀表板端點回傳不同形狀**：`GET /v2/seller/dashboard`（`SellerDashboardController`）回應 `DashboardResponse`（7/30 日訂單數、30 日已實現營收、活躍刊登數、待處理訂單數、最後下單時間，有 `@Cacheable`）；`GET /v2/dashboard/stats`（`AnalyticsController`）回應 `DashboardStats`（今日/昨日/本月/本年營收、營收成長率、今日/昨日/本月訂單數、待處理訂單、活躍刊登與房源數）。**「營收」的口徑自 Sprint 301 起統一**（DEF-407，使用者拍板）：`AnalyticsController` 底下所有營收數字都走同一個加總函式 `AnalyticsService.sumRevenue`（已付款、未取消、未退款的五個狀態）；`DashboardResponse.revenue30d` 是**刻意保留、名稱不同的另一個數字**「已實現營收」（僅 `COMPLETED`），不可與前者互相比較（見 §3.1、§7）。
2. **`SellerDashboardController.getDashboard` 的角色守門（DEF-326 已修復）**：核准開店後角色是 `STORE_OWNER`，自助註冊的 `SELLER`/`HOST` 角色若未建立店鋪則呼叫者租戶會落在系統租戶——Controller 另外明確擋下系統租戶呼叫（`TenantContext.isStoreTenant`），否則會讀到「全平台一般消費者訂單」當作自己的儀表板數字。
3. **`AnalyticsController` 用通用 `dashboard:read` 權限**（`STORE_OWNER`／`STORE_STAFF`／`ADMIN`），不像 `SellerDashboardController` 額外做租戶型別檢查——`ADMIN`／`SUPER_ADMIN` 呼叫時以自己的 `TenantContext` 當前租戶查詢（對平台管理者而言通常是系統租戶，不是「全平台彙總」）。
4. **Stripe Connect 權限比 Controller 宣告的更嚴格（DEF-327 已修復）**：Controller 層 `@PreAuthorize("hasRole('STORE_OWNER')")`，但 `TenantStripeConnectService.requireStoreOwner` 在 Service 層再次檢查「呼叫者必須是**資料庫裡**這間店鋪的 `TenantMember.StoreRole.STORE_OWNER`」且「租戶必須是真正的店鋪租戶」，雙重防禦，避免未來新增呼叫路徑繞過 Controller 層檢查。
5. **Stripe Connect 受 Feature Toggle 保護**：兩個端點呼叫前皆檢查 `STRIPE_CONNECT_ENABLED`（`FeatureToggleService.checkFeatureEnabled`），未啟用時回 `E-2004`（403）。
6. **`initiateOnboarding` 是「建立或複用」語意**：已有 `stripeConnectAccountId` 時不會重建帳戶，只重新產生一次性 `onboardingUrl`（account link 會逾期，可重複呼叫取得新連結）；首次呼叫才建立 Connect Express 帳戶並轉 `PENDING`。
7. **帳戶狀態同步有兩條路徑**：`GET /stripe-connect/status`（本文件端點，使用者手動觸發）與 webhook `account.updated`（`TenantStripeConnectService.syncAccountStatusFromWebhook`，非 REST 端點，屬 M07 金流 webhook 範圍）都會回填 `chargesEnabled`／`payoutsEnabled`／轉 `COMPLETE`，判斷條件相同（`detailsSubmitted && chargesEnabled && payoutsEnabled`）。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M13-001 | 店主/員工查看經營儀表板摘要 | `GET /v2/seller/dashboard` | ✅ 已實作 |
| US-M13-002 | 店主/員工查看營收/訂單/商品統計分析 | `GET /v2/dashboard/stats`、`/revenue`、`/orders`、`/listings`、`/activity` | ✅ 已實作 |
| AI-2413（Sprint 53） | 店主開通 Stripe Connect 收款帳戶並查詢狀態 | `POST /v2/seller/dashboard/stripe-connect/onboarding`、`GET .../status` | ✅ 已實作（僅帳戶開通，Phase D-2 分潤 transfer 非本模組範圍） |

FRD 完全無 M13 章節（見文件頂部狀態說明）。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M13-001 | GET | `/v2/seller/dashboard` | 經營儀表板摘要 | `hasAnyRole(STORE_OWNER, SELLER)` ＋ 真正店鋪租戶 |
| API-M13-002 | POST | `/v2/seller/dashboard/stripe-connect/onboarding` | 發起/重新產生 Stripe Connect onboarding link | `hasRole(STORE_OWNER)` ＋ Service 層本店店主檢查 |
| API-M13-003 | GET | `/v2/seller/dashboard/stripe-connect/status` | 查詢 Stripe Connect 帳戶狀態 | `hasRole(STORE_OWNER)` ＋ Service 層本店店主檢查 |
| API-M13-004 | GET | `/v2/dashboard/stats` | 儀表板統計 | `dashboard:read` |
| API-M13-005 | GET | `/v2/dashboard/revenue` | 營收統計（可選日期區間與 granularity） | `dashboard:read` |
| API-M13-006 | GET | `/v2/dashboard/orders` | 訂單統計 | `dashboard:read` |
| API-M13-007 | GET | `/v2/dashboard/listings` | Listing 統計 | `dashboard:read` |
| API-M13-008 | GET | `/v2/dashboard/activity` | 最近活動 | `dashboard:read` |

共 8 個端點。

---

## 3. 資料模型 / Data Model

### 3.1 `SellerDashboardDto.DashboardResponse`（`GET /v2/seller/dashboard`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `orderCount7d` / `orderCount30d` | long | 近 7／30 日建立的訂單數（依 `createdAt`，非下單時區調整） |
| `revenue30d` | BigDecimal | **已實現營收**：近 30 日**僅 `COMPLETED`** 狀態訂單金額加總。與 `AnalyticsDto` 的「營收」（已付款、未取消、未退款的五個狀態）是**刻意並存的不同數字**，不可互相比較（DEF-407，Sprint 301）；欄位名維持 `revenue30d` 以免破壞既有契約，「已實現營收」這個名稱只標在 DTO Javadoc 與本文件，前端目前沒有任何地方使用此欄位，所以沒有 UI 標籤可改 |
| `activeListingCount` | long | 本租戶 `ACTIVE` 狀態 Listing 數（商品+房源合計） |
| `pendingOrderCount` | long | `CREATED`／`PAID`／`CONFIRMED` 三狀態訂單數合計 |
| `lastOrderAt` | Instant | 最近一筆訂單建立時間，無訂單則 `null` |

`@Cacheable(value = "dashboardStats", key = "#tenantId")`：同租戶短期內重複呼叫會回快取值，非即時（快取逾期策略見 Spring Cache 設定，非本文件範圍）。

### 3.2 `AnalyticsDto.DashboardStats`（`GET /v2/dashboard/stats`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `todayRevenue`／`yesterdayRevenue`／`monthRevenue`／`yearRevenue` | BigDecimal | 只加 `PAID`／`CONFIRMED`／`SHIPPING`／`DELIVERED`／`COMPLETED` 五個狀態的訂單金額（與 `RevenueStats.totalRevenue` 同一口徑，Sprint 301，DEF-407；先前只排除 `CANCELLED`／`REFUNDED`，所以未付款的 `CREATED` 與等待退款的 `REFUNDING` 也被算進去） |
| `revenueGrowthPercent` | BigDecimal | `(今日-昨日)/昨日 * 100`；昨日為 0 時固定回 `0`（非除以零錯誤，而是提前判斷短路） |
| `todayOrders`／`yesterdayOrders`／`monthOrders` | Integer | 依 `createdAt` 區間計數，不排除任何狀態 |
| `pendingOrders` | Integer | 僅 `CREATED` 狀態（與 `DashboardResponse.pendingOrderCount` 口徑不同，見 §7） |
| `activeListings`／`totalProducts` | Integer | 兩欄位皆是「本租戶 `ACTIVE` 商品數」，**永遠相同值**（服務層同一變數賦值兩次） |
| `totalRooms` | Integer | 本租戶房源總數（**不限 `ACTIVE` 狀態**，與 `activeListings` 的「僅 ACTIVE」不一致） |

### 3.3 `AnalyticsDto.RevenueStats`（`GET /v2/dashboard/revenue`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `totalRevenue` | BigDecimal | 區間內 `PAID`／`CONFIRMED`／`SHIPPING`／`DELIVERED`／`COMPLETED` 五狀態加總。全模組唯一的營收加總是 `AnalyticsService.sumRevenue`——Sprint 301 起首頁今日／昨日／本月／本年與每日趨勢也走它，不會再各自分歧（DEF-407） |
| `totalOrders` | Integer | 區間內**全部**訂單數（含未付款、已取消），**不是**入帳訂單數 |
| `totalRefunds` | BigDecimal | 區間內訂單關聯付款為 `REFUNDED`（全額退款）狀態者的金額加總；`PARTIALLY_REFUNDED`（部分退款）不計入——見下一列 |
| `netRevenue` | BigDecimal | `totalRevenue - totalRefunds`。**⚠️ 已知算法缺陷（DEF-412，Sprint 301 實測登記，待使用者裁決，見 §7）**：全額退款的訂單已被排除在 `totalRevenue` 之外，它的付款金額卻又被 `totalRefunds` 再扣一次（實測單一 500 元銷售全額退款後 `netRevenue = -500`）；部分退款完全沒被扣（實測退 200 後 `netRevenue` 仍是 450） |
| `averageOrderValue` | BigDecimal | `totalRevenue / 入帳訂單數`（四捨五入 2 位）；入帳訂單數＝與 `totalRevenue` 同一組五個狀態的訂單筆數，為 0 時回 `0`（非除以零錯誤）。Sprint 301 起（DEF-407）；先前分母是區間內全部訂單（含未付款與已取消），與只加已付款類的分子口徑不同 |
| `dailyRevenue` | `RevenueByDay` | 依 `granularity` 分桶（見 §4.5） |
| `categoryRevenue` | `RevenueByCategory` | **`data` 永遠是空陣列**——欄位與巢狀型別都定義了，服務層從未實作依分類聚合，見 §7 已知限制 |

查詢參數：`startDate`／`endDate`（預設近 30 天）、`granularity`（`DAY`／`WEEK`／`MONTH`，大小寫不敏感，未知值靜默當作 `DAY`）。`AnalyticsRequest.listingId` 欄位存在但 Controller 從未設值、Service 從未讀取（死欄位）。

### 3.4 `AnalyticsDto.ListingStats`（`GET /v2/dashboard/listings`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `totalListings` | Integer | 本租戶全部 Listing 數（不限狀態） |
| `activeProducts`／`inactiveProducts`／`activeRooms`／`inactiveRooms` | Integer | 依型別＋是否 `ACTIVE` 分四類計數 |
| `topProducts`／`topRooms` | `List<TopListing>` | **永遠是空陣列**——`TopListing`（`listingId`／`title`／`type`／`orderCount`／`revenue`）型別定義完整，但服務層從未填值，見 §7 已知限制 |

### 3.5 `StripeConnectDto.OnboardingResponse` / `StatusResponse`

| 欄位 | 類型 | 說明 |
|------|------|------|
| `onboardingUrl` | String | 一次性 Stripe account link，會逾期 |
| `accountId` | String | Stripe Connect Express 帳戶 ID，未開通時為 `null` |
| `onboardingStatus` | enum 字串 | `NOT_STARTED`／`PENDING`／`COMPLETE` |
| `chargesEnabled`／`payoutsEnabled` | boolean | 來自 Stripe 帳戶即時查詢結果 |

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `GET /v2/seller/dashboard` — 經營儀表板摘要

**權限**：`hasAnyRole('STORE_OWNER', 'SELLER')`，且 Controller 額外擋下系統租戶（`TenantContext.isStoreTenant`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 呼叫者租戶是系統租戶（無店鋪） | 403 | `E-1007` |

### 4.2 `POST /v2/seller/dashboard/stripe-connect/onboarding` — 發起/重新產生 onboarding link

**權限**：`hasRole('STORE_OWNER')` ＋ Service 層再驗證真正店鋪租戶的本店店主；需 `STRIPE_CONNECT_ENABLED` 功能開關啟用。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 功能開關未啟用 | 403 | `E-2004` |
| 呼叫者非本店店主或非真正店鋪租戶 | 403 | `E-1007` |
| 租戶不存在（理論上不可達，`TenantContext` 來源即為已登入租戶） | 404 | `E-2000` |

### 4.3 `GET /v2/seller/dashboard/stripe-connect/status` — 查詢帳戶狀態

**權限**：同 §4.2。未開通過（`stripeConnectAccountId` 為 `null`）時直接回 `NOT_STARTED`／兩個 boolean 皆 `false`，不呼叫 Stripe API。已開通則即時查詢 Stripe 並回寫本地欄位。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 功能開關未啟用 | 403 | `E-2004` |
| 呼叫者非本店店主或非真正店鋪租戶 | 403 | `E-1007` |

### 4.4 `GET /v2/dashboard/stats` — 儀表板統計

**權限**：`dashboard:read`。無查詢參數。回應見 §3.2。

### 4.5 `GET /v2/dashboard/revenue` — 營收統計

**權限**：`dashboard:read`。查詢參數：`startDate`／`endDate`（`LocalDate`，選填，預設近 30 天）、`granularity`（選填，預設 `DAY`）。回應見 §3.3。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `startDate`／`endDate` 無法解析成日期 | 400 | `E-9000`（`@RequestParam LocalDate` 型別轉換失敗，全域 handler） |

### 4.6 `GET /v2/dashboard/orders` — 訂單統計

**權限**：`dashboard:read`。無查詢參數。回應 `OrderStats`（依狀態分組計數＋ `ordersByStatus` map，狀態名稱為 `Order.OrderStatus` 原始字串）。

### 4.7 `GET /v2/dashboard/listings` — Listing 統計

**權限**：`dashboard:read`。無查詢參數。回應見 §3.4。

### 4.8 `GET /v2/dashboard/activity` — 最近活動

**權限**：`dashboard:read`。查詢參數 `limit`（預設 20，上限 100）。回應 `RecentActivity`——目前**僅含訂單事件**（`type` 固定格式 `ORDER_{狀態}`），不含訂房／評價等其他活動類型，`description` 欄位直接拼接英文字串（非 i18n 訊息）。

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | SELLER（無店鋪） | STORE_OWNER | STORE_STAFF | ADMIN／SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|
| 經營儀表板摘要（§4.1） | ❌（系統租戶被擋） | ✅ | ❌（角色不符） | ❌（角色不符，`hasAnyRole` 未含 ADMIN） |
| Stripe Connect（§4.2～4.3） | ❌（角色不符） | ✅（限本店） | ❌ | ❌（角色不符） |
| 統計分析（§4.4～4.8） | ❌（無 `dashboard:read`） | ✅ | ✅ | ✅ |

`§4.1`／`§4.2`／`§4.3` 三個端點的角色檢查用 `hasAnyRole`／`hasRole`（角色字串），**不是**像其他模組常見的 `hasAuthority('xxx:read')` 權限字串，`ADMIN`／`SUPER_ADMIN` 因此**無法**呼叫經營儀表板摘要與 Stripe Connect 兩組端點（即使 `SUPER_ADMIN` 在 `RolePermissionMapping` 持有全部 `Permission`，這裡檢查的是 Spring Security 角色而非該權限枚舉）。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 發生在 |
|--------|:----:|--------|
| `E-1007` | 403 | §4.1 系統租戶被擋；§4.2／4.3 非本店店主 |
| `E-2000` | 404 | §4.2／4.3 租戶不存在（理論上不可達） |
| `E-2004` | 403 | §4.2／4.3 `STRIPE_CONNECT_ENABLED` 功能開關未啟用 |
| `E-9000` | 400 | §4.5 日期查詢參數型別轉換失敗 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| ~~兩個「儀表板」端點統計口徑互不一致~~（**DEF-407，Sprint 300 登記、Sprint 301 已修復**） | 原狀：`DashboardResponse.revenue30d` 只計 `COMPLETED`；`DashboardStats` 的今日／本月／本年營收與每日趨勢只排除 `CANCELLED`／`REFUNDED`，所以未付款的 `CREATED` 與等待退款的 `REFUNDING` 也被算進去；`RevenueStats.totalRevenue` 又是第三種（五個已付款類狀態）；`averageOrderValue` 的分母還是區間內全部訂單。Sprint 300 實測：已送達 2100＋已付款 500＋未付款 500，`/dashboard/stats` 回 `todayRevenue: 3100`、`/dashboard/revenue` 回 `totalRevenue: 2600`，差的正是未付款那張。**Sprint 301 依使用者的決定**（`AskUserQuestion` 中本人選「已付款且未取消」）：「營收」＝`PAID`／`CONFIRMED`／`SHIPPING`／`DELIVERED`／`COMPLETED` 五個狀態的 `totalAmount`，全模組只剩一個加總函式 `AnalyticsService.sumRevenue`（首頁今日／昨日／本月／本年、每日趨勢、報表總營收都走它）；平均客單價的分母改為同一組訂單的筆數（`totalOrders` 仍是區間內全部訂單數）；`revenue30d`（僅 `COMPLETED`）保留為「已實現營收」，名稱只標在 DTO Javadoc 與本文件（前端沒有使用此欄位）。**驗證**：`AnalyticsServiceTest` 新增 2 個並更新平均客單價案例的預期值（12→14）、`AnalyticsRealDbIntegrationTest` 新增 1 個真實 PostgreSQL 案例（2→3）、真實全棧 E2E `at-dashboard-revenue.spec.ts` 的 `E2E-REVENUE-02`／`03`（已付款 500＋未付款 500：首頁今日營收 $500、報表平均客單價 $500、訂單總數仍是 2 筆）。**不含訂房**：舊口徑與現在的統一口徑都只查 `Order`，PRD 對儀表板營收是否含訂房沒有明文，另案 |
| **`netRevenue` 與退款的算法有兩處錯（DEF-412，Sprint 301 實測登記，待使用者裁決）** | `netRevenue = totalRevenue - totalRefunds`，但 `totalRevenue` 只加五個已付款類狀態，全額退款的訂單（狀態 `REFUNDED`）本來就不在裡面，而 `totalRefunds` 加總的是區間內付款為 `REFUNDED` 者的金額——**同一筆全額退款被扣兩次**；另一頭，部分退款時付款是 `PARTIALLY_REFUNDED`、訂單狀態不變（`PaymentStateService` 的退款處理：達全額才把付款與訂單都轉 `REFUNDED`，否則付款轉 `PARTIALLY_REFUNDED`、訂單狀態不變），`totalRefunds` 只查 `REFUNDED`，所以**部分退款完全不被扣**。**實測（Sprint 301，真實端點＋真實登入；退款後的終態由 SQL 依應用程式的寫法寫入，不是走完真實退款流程，測完已還原）**：單一 500 元銷售全額退款 → `totalRevenue: 0`、`totalRefunds: 500`、`netRevenue: -500`（應為 0）；450 元訂單部分退款 200 → `totalRefunds: 0`、`netRevenue: 450`（應為 250）。「退款要怎麼算進營收」是產品決策，所以只登記不修 |
| `DashboardStats.pendingOrders` 與 `DashboardResponse.pendingOrderCount` 口徑不同 | 前者僅計 `CREATED`；後者計 `CREATED`＋`PAID`＋`CONFIRMED` 三狀態 |
| `DashboardStats.activeListings`／`totalProducts` 恆為同一數值 | 服務層用同一個 `activeProducts` 變數賦值給兩個欄位，`totalProducts` 這個名稱暗示「商品總數」，實際回傳的是「**ACTIVE** 商品數」 |
| `totalRooms` 與 `activeListings` 的「是否限 ACTIVE」不一致 | 同一回應物件內，房源計數不篩狀態、商品計數篩 `ACTIVE`，欄位命名未反映此差異 |
| `RevenueStats.categoryRevenue`／`ListingStats.topProducts`／`topRooms` 皆為定義了但永遠空陣列的欄位 | 回應型別（`CategoryRevenue`、`TopListing`）完整定義了欄位結構，但服務層目前直接回傳 `Collections.emptyList()`，未實作實際聚合邏輯 |
| `AnalyticsRequest.listingId` 是死欄位 | DTO 定義存在，但 Controller 從未設值、Service 從未讀取，無法依單一 Listing 篩選營收 |
| `granularity` 未知值靜默降級為 `DAY` | 不回驗證錯誤，與其他模組（如 `PageableUtils.sortOf`，DEF-339）統一拒絕非法排序字串的設計不一致，但影響有限（僅影響分桶粒度，不影響資料正確性） |
| `GET /v2/dashboard/activity` 僅含訂單事件 | `ActivityItem.type` 註解列出 `BOOKING_CREATED` 等其他可能類型，但目前實作只查詢 `OrderRepository`，不含訂房/評價/通知等其他模組事件 |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件完成後新增 `SellerDashboardController`／`AnalyticsController` 至 `GUARDED` 清單，從此有常態機械防漂移 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §4（Operations，M13 商家工作台）／§9.9 |
| FRD | 無章節（見文件頂部狀態說明） |
| 實作 | `SellerDashboardController`、`AnalyticsController`（`api/controller/`）；`SellerDashboardService`（`core/seller/`）；`AnalyticsService`（`core/analytics/`）；`TenantStripeConnectService`（`core/tenant/`） |
| 契約測試 | [SellerDashboardServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/seller/SellerDashboardServiceTest.java)、[SellerDashboardServiceCacheTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/seller/SellerDashboardServiceCacheTest.java)、[AnalyticsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/analytics/AnalyticsServiceTest.java)、[AnalyticsRealDbIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/analytics/AnalyticsRealDbIntegrationTest.java)、[TenantStripeConnectServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/tenant/TenantStripeConnectServiceTest.java)、[M13SellerDashboardIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M13SellerDashboardIntegrationTest.java)、[M13SellerStripeConnectIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M13SellerStripeConnectIntegrationTest.java)、[at-dashboard-revenue.spec.ts](../../../frontend/e2e/at-dashboard-revenue.spec.ts)（真實全棧 E2E，Sprint 301，DEF-407） |

---

**文件狀態**：v1.0（Sprint 275 新建，DEF-286 範圍）。
