# API 規格文件 - M11 物流追蹤 / API Specification - M11 Logistics

> **文件編號**: API-M11
> **文件版本**: v1.1（Sprint 287，DEF-330a：運費模板列表改用專屬權限 `shipping_template:read`；同時更正 v1.0 對 `BUYER`/`GUEST` 的 `product:read` 誤述，見 §5／§7）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-10-19
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`LogisticsController`／`ShippingTemplateController`（`api/controller/`）、`LogisticsService`／`ShippingTemplateService`（`core/logistics/`）、`Logistics`／`ShippingTemplate`（`domain/model/logistics/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[LogisticsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceTest.java)、[LogisticsServiceCancelTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceCancelTest.java)、[LogisticsServiceOwnershipTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceOwnershipTest.java)、[LogisticsServiceTenantAccessTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceTenantAccessTest.java)、[ShippingTemplateServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/ShippingTemplateServiceTest.java)、[ShippingTemplateControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/ShippingTemplateControllerE2ETest.java)、[M11LogisticsOrderIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M11LogisticsOrderIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **FRD 狀態**：FRD（`E-Commerce_FRD_v1.0.md`）完全無 M11 章節，僅 PRD §9.9（Phase 2）提及；`docs/01_requirements/M11_Cancellation_Rules_Validation.md` 有物流取消業務規則的獨立確認報告（非 API 規格）

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M11 |
| **模組名稱** | 物流追蹤 / Logistics |
| **描述** | 訂單出貨後建立物流單（Mock Provider，無真實黑貓/新竹串接）、查詢與追蹤配送狀態、取消；另含商家運費模板 CRUD 與試算 |
| **API 前綴** | `/api/v2/logistics/...`、`/api/v2/shipping-templates/...` |
| **多租戶隔離** | 物流單無 `tenantId` 欄位，存取控制靠**反查訂單的 `tenantId`**（`checkOrderTenant`／`checkLogisticsTenant`）；運費模板有 `tenantId`，CRUD 三個方法皆以此隔離，唯獨**試算端點刻意不隔離**（§1.2 第 4 點） |
| **Mock 說明** | 兩個 Controller 的 Javadoc 皆明寫「Mock Implementation」：物流商 API（追蹤事件、簽收地點）與運費模板計算皆為假資料／規則運算，無對接黑貓/新竹/來源物流商真實 API |

### 1.2 設計重點

1. **建立物流單即出貨**：`POST /v2/logistics` 要求訂單狀態為 `CONFIRMED`，以原子 CAS（`updateStatusIfCurrent`）搶占轉為 `SHIPPING`，同一交易內扣庫存（`ProductInventoryService.deductOnShipment`，Sprint 218 DEF-303）、提交後通知買家已出貨（`notifyOrderShipped`）。搶輸（訂單已被併發推走）回 `E-5001`。
2. **一張訂單至多一筆「作用中」物流單**：建立前查詢 `findByOrderId`，若存在狀態非 `DELIVERED`/`RETURNED` 的物流單則拒絕（詳見 §7 DEF-381，拋出的錯誤碼已於 Sprint 278 修正）。
3. **租戶擁有權檢查一律先於狀態檢查**（DEF-019/036 既有修法，IDOR 正確順序）：`SUPER_ADMIN`／`ADMIN` 不受限；其餘呼叫者須 `TenantContext.isStoreTenant(tenantId) && tenantId.equals(order.getTenantId())`，否則 403 `E-1007`。`getLogistics`／`getLogisticsByOrderId`／`trackLogistics`／`getTrackingDetail`／`updateLogisticsStatus`／`cancelLogistics` 六個讀寫端點皆比對擁有權。
4. **運費試算刻意不做租戶過濾（DEF-037，Sprint 107 已拍板結案）**：`calculateFee` 只憑 `templateId` 即可試算，支援買家跨店比價；回應只含計算參數，無 PII、無寫入路徑，**請勿「順手」加上租戶過濾**（已三度被掃描誤判為缺漏並結案）。
5. **狀態機**（`LogisticsStatus`）：`PENDING → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`，`FAILED`／`RETURNED` 為另外兩個終態。`updateLogisticsStatus` 不驗證轉換合法性（可任意指定任何 `LogisticsStatus`，包括倒退），僅在轉為 `DELIVERED` 時記錄 `deliveryTime`、轉為 `PICKED_UP` 時記錄 `pickupTime`，並嘗試同步訂單狀態為 `DELIVERED`（原子 CAS，搶輸只記 warning log，不拋例外）。
6. **取消規則**：`DELIVERED` 的物流單無法取消（`E-7502`）；原子 CAS（`cancelIfNotStatus`）防止與併發的「標記已送達」競態（DEF-158）；取消會把狀態改為 `RETURNED`（**沒有獨立的 `CANCELLED` 狀態**，取消與「退貨」共用同一個終態值）。
7. **運費計算規則**（`ShippingTemplate.FeeType`）：`FIXED` 固定收取 `fixedAmount`；`FREE_THRESHOLD` 訂單金額達 `freeThreshold` 即免運，否則收 `fixedAmount`。`fixedAmount`／`freeThreshold` 皆可為 `null`，計算時以 `BigDecimal.ZERO` 代入。`calculateFeeForTenant`（供其他模組如結帳流程內部呼叫，非 REST 端點）取該租戶**第一個**模板計算，無模板時免運。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M11-001 | 店家對已確認訂單建立物流單（即出貨） | `POST /v2/logistics` | ✅ 已實作 |
| US-M11-002 | 查詢物流單詳情／依訂單查詢／追蹤狀態與歷史 | `GET /v2/logistics/{id}`、`GET /v2/logistics/order/{orderId}`、`GET /v2/logistics/{id}/track`、`GET /v2/logistics/{id}/tracking-detail` | ✅ 已實作 |
| US-M11-003 | 更新物流狀態（Mock 推進）、取消物流 | `PUT /v2/logistics/{id}/status`、`POST /v2/logistics/cancel` | ✅ 已實作 |
| US-006 (PRD) | 商家設定固定運費／免運門檻模板（CRUD） | `POST`／`GET`／`PUT`／`DELETE /v2/shipping-templates` | ✅ 已實作 |
| US-M11-004 | 試算運費（買家跨店比價用） | `GET /v2/shipping-templates/{id}/calculate-fee` | ✅ 已實作 |

FRD 完全無 M11 章節（見文件頂部狀態說明）。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M11-001 | POST | `/v2/logistics` | 建立物流單（即出貨） | `order:update` |
| API-M11-002 | GET | `/v2/logistics/{logisticsId}` | 物流單詳情 | `order:read` |
| API-M11-003 | GET | `/v2/logistics/order/{orderId}` | 依訂單查物流列表 | `order:read` |
| API-M11-004 | GET | `/v2/logistics/{logisticsId}/track` | 追蹤目前狀態 | `order:read` |
| API-M11-005 | GET | `/v2/logistics/{logisticsId}/tracking-detail` | 追蹤歷史事件 | `order:read` |
| API-M11-006 | PUT | `/v2/logistics/{logisticsId}/status` | 更新物流狀態（Mock） | `order:update` |
| API-M11-007 | POST | `/v2/logistics/cancel` | 取消物流 | `order:update` |
| API-M11-008 | POST | `/v2/shipping-templates` | 建立運費模板 | `product:create` |
| API-M11-009 | GET | `/v2/shipping-templates` | 取得本租戶所有運費模板 | `shipping_template:read`（Sprint 287 起，見 §4.9） |
| API-M11-010 | PUT | `/v2/shipping-templates/{id}` | 更新運費模板 | `product:update` |
| API-M11-011 | DELETE | `/v2/shipping-templates/{id}` | 刪除運費模板 | `product:delete` |
| API-M11-012 | GET | `/v2/shipping-templates/{id}/calculate-fee` | 試算運費 | `product:read` |

共 12 個端點。

**⚠️ 路徑變數命名提醒**：`ShippingTemplateController` 的路徑樣板為 `{id}`，Java 方法參數同名 `id`；`LogisticsController` 為 `{logisticsId}`／`{orderId}`，與方法參數同名。

---

## 3. 資料模型 / Data Model

### 3.1 `Logistics`（表 `logistics`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `orderId` | UUID | not null |
| `logisticsProvider` | enum | `HCT`／`TCAT` |
| `trackingNumber` | String | 由 Mock Provider 產生 |
| `status` | enum `LogisticsStatus` | 見 §1.2 第 5 點，預設 `PENDING` |
| `pickupTime` / `deliveryTime` | LocalDateTime | 分別於轉 `PICKED_UP`／`DELIVERED` 時寫入 |
| `shippingAddress` / `receiverName` / `receiverPhone` | String | 建立時未帶則取訂單的對應欄位 |
| `logisticsData` | Map（JSONB） | 建立時初始化為空 Map，**全庫無任何路徑寫入內容**，亦不出現在任何回應 DTO——保留但未使用的欄位 |
| `createdAt` / `updatedAt` | Instant | |

`@DynamicUpdate`：只送出本次交易真正被 setter 改動的欄位（DEF-158/159），避免 `updateLogisticsStatus` 與 `cancelLogistics` 併發互相覆寫。無唯一約束在 `order_id` 上（見 §7 已知限制）。

### 3.2 `ShippingTemplate`（表 `shipping_templates`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` | UUID | PK |
| `tenantId` | UUID | not null（另有 `insertable/updatable=false` 唯讀鏡像欄位，DEF-332 系列慣例） |
| `name` | String | not null，上限 100 字 |
| `feeType` | enum `FeeType` | `FIXED`／`FREE_THRESHOLD`，not null |
| `fixedAmount` / `freeThreshold` | BigDecimal(10,2) | 皆可為 `null` |
| `createdAt` / `updatedAt` | Instant | not null |

一個租戶可建立多筆模板；`calculateFeeForTenant`（非 REST，供其他模組呼叫）固定取**第一筆**（無排序保證的 `findByTenantId` 結果順序），非「依商品/訂單挑選適用模板」。

---

## 4. 端點規格 / Endpoint Specifications

### 4.1 `POST /v2/logistics` — 建立物流單

**權限**：`order:update`（`SELLER`／`STORE_OWNER`／`STORE_STAFF`？—— 見 §5；`STORE_STAFF` 僅有 `order:read`，**無法**建立物流單）。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|:--:|------|
| `orderId` | UUID | 必填 | |
| `logisticsProvider` | enum | 必填 | `HCT`／`TCAT` |
| `receiverName` / `receiverPhone` / `shippingAddress` | String | 選填 | 未帶則取訂單對應欄位 |

**回應**：`LogisticsResponse`（200）。成功後訂單狀態原子轉為 `SHIPPING`，扣庫存，非同步通知買家已出貨。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `orderId`／`logisticsProvider` 缺失 | 400 | `E-9000` |
| 訂單不存在 | 404 | `E-5000` |
| 呼叫者非訂單所屬租戶且非 admin | 403 | `E-1007` |
| 訂單狀態非 `CONFIRMED`（含已被併發搶走） | 422 | `E-5001` |
| 該訂單已有作用中物流單（非 `DELIVERED`/`RETURNED`） | 404 | `E-7501`（Sprint 278／DEF-381 修復前誤用採購單的 `E-7001`；`E-7501` 本身維持既有的 404 對應，語意上更像衝突但非本輪調整範圍，見 §7） |

### 4.2 `GET /v2/logistics/{logisticsId}` — 物流單詳情

**權限**：`order:read`，且限訂單所屬租戶或 admin（反查訂單執行 `checkOrderTenant`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 物流單不存在 | 404 | `E-7500`（Sprint 278／DEF-381 修復前誤用供應商的 `E-7000`，見 §7） |
| 非擁有租戶且非 admin | 403 | `E-1007` |

### 4.3 `GET /v2/logistics/order/{orderId}` — 依訂單查物流列表

**權限**：`order:read`，且限訂單所屬租戶或 admin。回應 `List<LogisticsResponse>`（可能為空陣列，一張訂單理論上只會有一筆，見 §7 已知限制）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 訂單不存在 | 404 | `E-5000` |
| 非擁有租戶且非 admin | 403 | `E-1007` |

### 4.4 `GET /v2/logistics/{logisticsId}/track` — 追蹤目前狀態

**權限**：`order:read`，且限訂單所屬租戶或 admin。回應 `StatusResponse`（含 Mock 的 `statusMessage`／`location`，依 provider 固定回「黑貓物流中心-台北」或「新竹物流中心-新竹」）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 物流單不存在 | 404 | `E-7500`（Sprint 278／DEF-381 修復前誤用 `E-7000`，見 §7） |
| 非擁有租戶且非 admin | 403 | `E-1007` |

### 4.5 `GET /v2/logistics/{logisticsId}/tracking-detail` — 追蹤歷史事件

**權限**：`order:read`，且限訂單所屬租戶或 admin。回應 `TrackingDetail`（Mock 事件列表，依目前狀態回推 1~4 筆固定時間差的事件，無真實歷史紀錄表）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 物流單不存在 | 404 | `E-7500`（Sprint 278／DEF-381 修復前誤用 `E-7000`，見 §7） |
| 非擁有租戶且非 admin | 403 | `E-1007` |

### 4.6 `PUT /v2/logistics/{logisticsId}/status` — 更新物流狀態（Mock）

**權限**：`order:update`，且限訂單所屬租戶或 admin。查詢參數 `status`（字串，需能解析成 `LogisticsStatus`）。**不驗證狀態轉換合法性**（可任意指定，包括邏輯上的倒退）。轉為 `DELIVERED` 時嘗試同步訂單狀態（搶輸僅記 log，不影響本次回應）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `status` 無法解析成列舉 | 422 | `E-7505`（Sprint 278／DEF-385 修復：Controller 新增 try-catch 將 `LogisticsStatus.valueOf(status)` 的 `IllegalArgumentException` 轉譯為此專屬碼，修復前落入 catch-all 變 500／`E-9900`，見 §7） |
| 物流單不存在 | 404 | `E-7500`（Sprint 278／DEF-381 修復前誤用 `E-7000`，見 §7） |
| 非擁有租戶且非 admin | 403 | `E-1007` |

### 4.7 `POST /v2/logistics/cancel` — 取消物流

**權限**：`order:update`，且限訂單所屬租戶或 admin。

**請求**：`logisticsId`（UUID，必填）、`reason`（String，選填）。

**回應**：`LogisticsResponse`（200），狀態改為 `RETURNED`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `logisticsId` 缺失 | 400 | `E-9000` |
| 物流單不存在 | 404 | `E-7500`（本端點自 DEF-011 起即用此正確碼；Sprint 278／DEF-381 已讓 §4.2/4.4/4.5/4.6 同步改用，全端點一致） |
| 非擁有租戶且非 admin | 403 | `E-1007` |
| 已是 `DELIVERED`（含被併發推進為 `DELIVERED`） | 400 | `E-7502` |

### 4.8 `POST /v2/shipping-templates` — 建立運費模板

**權限**：`product:create`。

**請求**：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|:--:|------|
| `name` | String | 必填（`@NotBlank`） | |
| `feeType` | enum | 必填 | `FIXED`／`FREE_THRESHOLD` |
| `fixedAmount` / `freeThreshold` | BigDecimal | 選填 | |

**回應**：`TemplateResponse`（200）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `name`／`feeType` 缺失 | 400 | `E-9000` |
| 呼叫者租戶不存在 | 404 | `E-2000` |

### 4.9 `GET /v2/shipping-templates` — 取得本租戶所有運費模板

**權限**：`shipping_template:read`（Sprint 287，DEF-330a：原借用 `product:read`——`BUYER`／`GUEST` 亦持有，見 §5 更正說明——與同端點 create/update/delete 已使用的 `product:create/update/delete` 範圍不一致，改用專屬權限收斂可見範圍）。回應 `List<TemplateResponse>`（可能為空陣列），僅本租戶（呼叫者自己的 `TenantContext`，無跨租戶旁路，即使 `ADMIN`／`SUPER_ADMIN` 亦然）。

### 4.10 `PUT /v2/shipping-templates/{id}` — 更新運費模板

**權限**：`product:update`，且限本租戶（`findById` 後 `filter(tenantId::equals)`）。局部更新語意：僅非 `null` 欄位會被覆寫（`UpdateRequest` 無 `@NotNull`/`@NotBlank`，全部選填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 模板不存在或非本租戶 | 404 | `E-7504` |

### 4.11 `DELETE /v2/shipping-templates/{id}` — 刪除運費模板

**權限**：`product:delete`，且限本租戶。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 模板不存在或非本租戶 | 404 | `E-7504` |

### 4.12 `GET /v2/shipping-templates/{id}/calculate-fee` — 試算運費

**權限**：`product:read`。**刻意不做租戶過濾**（§1.2 第 4 點，DEF-037 已拍板）——任何持有 `product:read` 的已登入使用者皆可對任意 `templateId` 試算，不限本租戶。查詢參數 `orderAmount`（`BigDecimal`，必填）。

**回應**：`FeeCalculationResponse`（`templateId`／`feeType`／`orderAmount`／`shippingFee`）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `orderAmount` 缺失或無法解析 | 400 | `E-9000` |
| 模板不存在（任何租戶） | 404 | `E-7504` |

---

## 5. 權限矩陣 / Permission Matrix

| 端點 | BUYER | SELLER | HOST | STORE_OWNER | STORE_STAFF | ADMIN／SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|:----:|:----:|
| 建立物流單（§4.1） | ❌ | ✅（限本租戶訂單） | ❌ | ✅（限本租戶訂單） | ❌（僅 `order:read`） | ✅（跨租戶） |
| 物流查詢/追蹤（§4.2～4.5） | ❌ | ✅（限本租戶） | ❌ | ✅（限本租戶） | ✅（限本租戶） | ✅（跨租戶） |
| 更新狀態/取消（§4.6～4.7） | ❌ | ✅（限本租戶） | ❌ | ✅（限本租戶） | ❌（僅 `order:read`） | ✅（跨租戶） |
| 運費模板 建立／更新／刪除（§4.8、4.10～4.11） | ❌ | ✅（限本租戶，`product:*`） | ❌ | ✅（限本租戶） | ❌（僅 `product:read`） | ✅（僅限自己的租戶，無跨租戶旁路；多數管理員無店鋪，實務上操作的是系統租戶名下模板） |
| 運費模板 列表（§4.9） | ❌（Sprint 287 起；見下方更正說明） | ✅（限本租戶，`shipping_template:read`） | ❌ | ✅（限本租戶） | ✅（限本租戶，唯讀） | ✅（僅限自己的租戶，無跨租戶旁路，同上） |
| 試算運費（§4.12） | ✅（不限租戶；見下方更正說明） | ✅（不限租戶） | ❌ | ✅（不限租戶） | ✅（不限租戶） | ✅（不限租戶） |

`HOST` 無 `order:*`／`product:*`／`shipping_template:*` 相關權限，本模組全部端點皆不可呼叫。**更正（Sprint 287 複核）**：原文件誤稱「`BUYER`／`HOST`／`GUEST` 無 `product:*` 相關權限」——`RolePermissionMapping.java:33`／`:27` 顯示 `BUYER`／`GUEST` 皆持有 `PRODUCT_READ`（僅 `HOST` 真的沒有）。故試算運費（§4.12）對 `BUYER`／`GUEST` 從一開始即可呼叫，§1.2 第 4 點「買家跨店比價」設計意圖確實達成，§7 原「已知限制」一項已更正、不再視為待辦。運費模板**列表**（§4.9）原同樣借用 `product:read`，`BUYER`／`GUEST` 原亦可呼叫；Sprint 287（DEF-330a）改用專屬的 `shipping_template:read` 後收緊，`BUYER`／`GUEST` 不再持有，現在才真的不可呼叫列表端點。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 官方定義 | 本模組實際用途 |
|--------|:----:|---------|----------------|
| `E-1007` | 403 | 權限不足 | 跨租戶存取物流/訂單被拒 |
| `E-2000` | 404 | 找不到租戶 | 建立運費模板時租戶不存在 |
| `E-5000` | 404 | 找不到訂單 | §4.1／§4.3 |
| `E-5001` | 422 | 無效的訂單狀態 | §4.1 訂單非 `CONFIRMED` |
| `E-7500` | 404 | 找不到物流記錄 | §4.2／4.4／4.5／4.6／4.7，全端點一致（Sprint 278／DEF-381 修復前 §4.2/4.4/4.5/4.6 誤用供應商的 `E-7000`，只有 §4.7 自 DEF-011 起正確） |
| `E-7501` | 404 | 已存在進行中的物流記錄 | §4.1（Sprint 278／DEF-381 修復前誤用採購單的 `E-7001`） |
| `E-7502` | 400 | 已送達的物流無法取消 | §4.7 |
| `E-7504` | 404 | 找不到運費範本 | §4.10／4.11／4.12 |
| `E-7505` | 422 | 無效的物流狀態 | §4.6（Sprint 278／DEF-385 新增，修復前 `status` 解析失敗落入 catch-all 變 500／`E-9900`） |
| `E-9000` | 400 | 驗證錯誤 | 所有 `@Valid`／必填查詢參數缺失 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| **`updateLogisticsStatus` 的 `status` 解析未捕捉例外（DEF-385，已於 Sprint 278 修復）** | §4.6 原以 `@RequestParam String` 收參數後手動 `valueOf()`，無 try-catch，無效字串會落入 catch-all 變 500／`E-9900`。已補上 try-catch，轉譯為新增的專屬碼 `E-7505`（422）。同一缺陷模式的另兩個實例（M16 ERP 的 `ErpController.listPurchaseOrders`、`SupplierService.listSuppliers`）亦已一併修復，見 [API_M16_ERP.md](./API_M16_ERP.md) |
| **物流錯誤碼大量誤用（DEF-381，已於 Sprint 278 修復）** | `LogisticsService` 原只有 `cancelLogistics`（DEF-011）使用正確的 `E_7500`；`getLogistics`／`trackLogistics`／`getTrackingDetail`／`updateLogisticsStatus` 四個方法原沿用 ERP 模組的 `E_7000`；`createLogistics` 的衝突情境原誤用 `E_7001`。現已全數改為 `E_7500`／`E_7501`，`LogisticsServiceTest` 新增 5 個單元測試斷言實際錯誤碼 |
| 一張訂單至多一筆作用中物流單是應用層檢查，無資料庫唯一約束 | `logistics` 表的 `order_id` 無唯一索引兜底，§1.2 第 2 點的檢查與建立之間雖有狀態 CAS 防護（DEF-123），但理論上地基完全依賴應用層邏輯，非資料庫不變量 |
| `logisticsData`（JSONB）是保留但未使用欄位 | 建立時初始化為空 Map，全庫無任何寫入實際內容的路徑，也不出現在任何回應 DTO |
| ~~試算運費端點的角色矩陣與「買家跨店比價」設計意圖不一致~~（**Sprint 287 複核更正：此限制從未成立**） | 本文件 v1.0（Sprint 274）原誤稱「`BUYER` 角色從未被授予 `product:read`」，據此推論試算運費的買家跨店比價設計意圖未達成。實際讀碼（`RolePermissionMapping.java:33`）確認 `BUYER` 確實持有 `PRODUCT_READ`，試算運費端點從一開始即可被 `BUYER`／`GUEST` 正常呼叫，設計意圖本就已達成，不存在此限制。保留此項作為文件曾經失準的記錄，不再視為待辦事項 |
| `updateLogisticsStatus` 不驗證狀態轉換合法性 | 可將任何物流單任意指定為任何 `LogisticsStatus`（包含邏輯倒退，如 `DELIVERED` 改回 `PENDING`），純 Mock 端點用於測試推進，非生產物流商回呼對接 |
| `calculateFeeForTenant`（非 REST，內部呼叫） | 固定取租戶「第一筆」模板，若店家建立多筆模板，使用哪一筆不可預期（`findByTenantId` 無排序保證） |
| `ApiRouteDocDriftTest` 先前未覆蓋 | 本文件完成後新增 `LogisticsController`／`ShippingTemplateController` 至 `GUARDED` 清單，從此有常態機械防漂移 |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §9.9（M11 物流追蹤，Phase 2）；US-006（運費模板） |
| FRD | 無章節（見文件頂部狀態說明） |
| 相關規格 | `docs/01_requirements/M11_Cancellation_Rules_Validation.md`（物流取消業務規則確認報告） |
| 實作 | `LogisticsController`、`ShippingTemplateController`（`api/controller/`）；`LogisticsService`、`ShippingTemplateService`（`core/logistics/`）；`Logistics`、`ShippingTemplate`（`domain/model/logistics/`） |
| 契約測試 | [LogisticsServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceTest.java)、[LogisticsServiceCancelTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceCancelTest.java)、[LogisticsServiceOwnershipTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceOwnershipTest.java)、[LogisticsServiceTenantAccessTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/LogisticsServiceTenantAccessTest.java)、[ShippingTemplateServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/logistics/ShippingTemplateServiceTest.java)、[ShippingTemplateControllerE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/ShippingTemplateControllerE2ETest.java)、[M11LogisticsOrderIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M11LogisticsOrderIntegrationTest.java) |

---

**文件狀態**：v1.0（Sprint 274 新建，DEF-286 範圍）。
