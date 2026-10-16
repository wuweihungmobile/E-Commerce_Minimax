# API 規格文件 - M16 進銷存管理 / API Specification - M16 ERP (Inventory & Purchasing)

> **文件編號**: API-M16
> **文件版本**: v1.0（Sprint 276，首次建立；首次有獨立規格文件，DEF-286 範圍）
> **建立日期**: 2026-10-16
> **最後更新**: 2026-10-16
> **負責人**: SD (Marcus)
> **Framework**: AISDLC v0.09
> **依據**: 後端實際行為——`ErpController`（`api/controller/`，17 個端點）、`AdminController`（`api/controller/`，採購單審批 3 個端點）、`SupplierService`／`PurchaseOrderService`／`InventoryService`／`StockMovementService`（`core/erp/`）；回應形狀、狀態碼、錯誤碼與權限皆逐行讀碼核對，並有真實測試佐證：[SupplierServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/SupplierServiceTest.java)、[PurchaseOrderServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/PurchaseOrderServiceTest.java)、[InventoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/InventoryServiceTest.java)、[StockMovementServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/StockMovementServiceTest.java)、[M16ErpE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/M16ErpE2ETest.java)、[M16ErpIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpIntegrationTest.java)、[M16ErpInventoryConcurrencyIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpInventoryConcurrencyIntegrationTest.java)、[M16ErpInventoryLedgerIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpInventoryLedgerIntegrationTest.java)、[M16ErpStockMovementDisplayIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpStockMovementDisplayIntegrationTest.java)
> **回應封包與錯誤碼**: 一律以 [API_Error_Codes.md](../API_Error_Codes.md) 為準（扁平封包 `success`／`code`／`message`／`data`／`errors`）
> **FRD 狀態**：FRD（`E-Commerce_FRD_v1.0.md`）完全無 M16 章節，PRD §6.7／§9.15 為主要依據
> **⚠️ 本文件撰寫範圍說明**：採購單審批（§4.18~4.20）實作在 `AdminController`，該類別混雜 M14／M17 端點，無法整個類別納入機械防漂移（見 [API_M14_Admin_Console.md](./API_M14_Admin_Console.md) §7、[API_M17_Tenant.md](./API_M17_Tenant.md) §7 同一限制）。本文件 §4.1~4.17（`ErpController`）**有**機械防漂移保護，§4.18~4.20（`AdminController`）**沒有**，見 §7

---

## 1. 模組概述 / Module Overview

### 1.1 基本資訊

| 欄位 | 內容 |
|------|------|
| **模組編號** | M16 |
| **模組名稱** | 進銷存管理 / ERP (Inventory & Purchasing) |
| **描述** | 供應商管理、採購單建立與收貨流程、庫存台帳與低庫存預警、手動庫存異動；跨租戶的採購單審批由平台管理員執行 |
| **API 前綴** | `/api/v2/dashboard/...`（`ErpController`，與 M01/M02/M12/M13 的 `/dashboard/*` 共用前綴但路徑其餘部分不重疊）、`/api/v2/admin/purchase-orders/...`（`AdminController`） |
| **多租戶隔離** | `ErpController` 全部端點以 `TenantContext.getCurrentTenant()` 隔離；採購單審批（`AdminController`）**跨租戶**，由 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 把關，Service 層不篩租戶 |
| **權限模型** | `ErpController` 用角色字面字串 `hasAuthority('STORE_OWNER')`／`hasAuthority('SELLER')`（非一般的 `xxx:read` 權限字串）——`RolePermissionMapping.getAuthorities` 刻意額外附加裸角色名稱供此類寫法使用（見 §1.2 第 2 點），`ADMIN`／`SUPER_ADMIN` 因此**無法**呼叫 `ErpController` 任何端點 |

### 1.2 設計重點

1. **庫存資料來源是 `product_inventory` 表，不是 `inventory` 表**：歷史上（Sprint 116 前，DEF-066）`InventoryService` 誤讀從未被任何生產程式碼寫入的空殼 `inventory` 表，導致庫存台帳／明細／預警三個端點永遠空白；修復後改讀真正累積異動的 `product_inventory`。本文件所有庫存相關端點皆以修復後的行為為準。
2. **`ErpController` 的角色檢查語法特殊**：`@PreAuthorize("hasAuthority('STORE_OWNER')")` 檢查的是 `RolePermissionMapping.getAuthorities()` 額外附加的裸角色名稱字串（該方法同時附加 `"ROLE_" + role.name()` 與 `role.name()` 本身），不是 `hasRole('STORE_OWNER')`（兩者在此專案等價，純粹是程式碼風格不統一，非缺陷）。
3. **採購單狀態機**（`PurchaseOrder.POStatus`）：`DRAFT →[submit]→ SUBMITTED` 或（超過租戶審批門檻）`PENDING_APPROVAL →[admin 審批]→ APPROVED／REJECTED`；`SUBMITTED／PARTIALLY_RECEIVED／APPROVED` 皆可收貨 →`RECEIVED`／`PARTIALLY_RECEIVED`；`DRAFT／SUBMITTED／PENDING_APPROVAL` 可取消 →`CANCELLED`。列舉另有 `ORDER`／`SHIPPED` 兩個值，**全庫無任何程式碼路徑會設定**，屬未使用的保留值。
4. **審批門檐是租戶層級設定**（`Tenant.purchaseOrderApprovalThreshold`，可為 `null`＝不啟用審批機制）：`submitPurchaseOrder` 比對採購單 `totalAmount` 是否超過門檻，超過則轉 `PENDING_APPROVAL`（需平台管理員審批），否則直接 `SUBMITTED`（視為已核准，可直接收貨）。
5. **手動庫存異動限制類型**：`StockMovementService.createManualMovement` 明確拒絕 `INBOUND`／`OUTBOUND`／`RESERVE`／`RELEASE`（這四型只能由採購單收貨／訂單出貨流程產生）與 `RETURN`（PRD §6.7.4 未定義其庫存語意，業務決策 DEF-044 尚未拍板，刻意拒絕而非靜默 no-op）；手動異動僅允許 `ADJUST_PLUS`／`ADJUST_MINUS`／`TRANSFER_IN`／`TRANSFER_OUT`／`SCRAP` 五型。
6. **低庫存預警嚴重度**：可售量（`availableQuantity`）≤ 門檻的 50% 時標記 `CRITICAL`，否則（但仍低於門檻）標記 `LOW`。門檻缺值時保底為 10（與 DDL 預設一致）。
7. **採購單品項的租戶/歸屬驗證有兩層**（DEF-027／DEF-231）：建立採購單時逐品項驗證 `listingId` 屬於呼叫者租戶，且（若有填 `skuId`）驗證該 SKU 真的屬於該 `listingId`——避免用自己的 listing 搭配他租戶的真實 SKU UUID 建立採購單，收貨時把庫存誤記到他租戶的 SKU 上。

### 1.3 User Stories

| ID | 標題 | 對應端點 | 狀態 |
|----|------|----------|------|
| US-M16-001 | 店家管理供應商 | §4.2～4.5 | ✅ 已實作 |
| US-M16-002 | 店家建立/管理採購單並確認收貨 | §4.6～4.12 | ✅ 已實作 |
| US-M16-003 | 店家查詢庫存台帳與低庫存預警 | §4.13～4.15 | ✅ 已實作 |
| US-M16-004 | 店家記錄手動庫存異動（盤盈/盤虧/調撥/報廢） | §4.16～4.17 | ✅ 已實作 |
| US-M16-005（PRD §6.7.2，Sprint 85） | 平台管理員審批超過門檐的採購單 | §4.18～4.20 | ✅ 已實作 |

FRD 完全無 M16 章節（見文件頂部狀態說明）。

---

## 2. 端點總覽 / Endpoint Overview

| 編號 | 方法 | 路徑 | 說明 | 權限 |
|------|------|------|------|------|
| API-M16-001 | GET | `/v2/dashboard/purchase-orders/listing-options` | 採購單品項選擇器（本租戶 ACTIVE Listing＋SKU） | `STORE_OWNER`／`SELLER` |
| API-M16-002 | GET | `/v2/dashboard/suppliers` | 供應商列表（可依狀態篩選） | `STORE_OWNER`／`SELLER` |
| API-M16-003 | GET | `/v2/dashboard/suppliers/{id}` | 供應商詳情 | `STORE_OWNER`／`SELLER` |
| API-M16-004 | POST | `/v2/dashboard/suppliers` | 建立供應商 | `STORE_OWNER` |
| API-M16-005 | PUT | `/v2/dashboard/suppliers/{id}` | 更新供應商 | `STORE_OWNER` |
| API-M16-006 | GET | `/v2/dashboard/purchase-orders` | 採購單列表（可依狀態篩選＋分頁） | `STORE_OWNER`／`SELLER` |
| API-M16-007 | POST | `/v2/dashboard/purchase-orders` | 建立採購單（DRAFT） | `STORE_OWNER` |
| API-M16-008 | GET | `/v2/dashboard/purchase-orders/{id}` | 採購單詳情 | `STORE_OWNER`／`SELLER` |
| API-M16-009 | PUT | `/v2/dashboard/purchase-orders/{id}` | 更新採購單（僅 DRAFT） | `STORE_OWNER` |
| API-M16-010 | PUT | `/v2/dashboard/purchase-orders/{id}/submit` | 提交採購單 | `STORE_OWNER` |
| API-M16-011 | PUT | `/v2/dashboard/purchase-orders/{id}/receive` | 確認收貨 | `STORE_OWNER` |
| API-M16-012 | PUT | `/v2/dashboard/purchase-orders/{id}/cancel` | 取消採購單 | `STORE_OWNER` |
| API-M16-013 | GET | `/v2/dashboard/inventory` | 庫存台帳（分頁） | `STORE_OWNER`／`SELLER` |
| API-M16-014 | GET | `/v2/dashboard/inventory/{skuId}` | 庫存明細（含異動記錄） | `STORE_OWNER`／`SELLER` |
| API-M16-015 | GET | `/v2/dashboard/inventory/alerts` | 低庫存預警列表 | `STORE_OWNER`／`SELLER` |
| API-M16-016 | POST | `/v2/dashboard/stock-movements` | 手動庫存異動 | `STORE_OWNER` |
| API-M16-017 | GET | `/v2/dashboard/stock-movements` | 庫存異動記錄（分頁） | `STORE_OWNER`／`SELLER` |
| API-M16-018 | GET | `/v2/admin/purchase-orders/pending` | 待審批採購單列表（跨租戶） | `hasRole('SUPER_ADMIN')` |
| API-M16-019 | POST | `/v2/admin/purchase-orders/{poId}/approve` | 核准採購單 | `hasRole('SUPER_ADMIN')` |
| API-M16-020 | POST | `/v2/admin/purchase-orders/{poId}/reject` | 駁回採購單 | `hasRole('SUPER_ADMIN')` |

共 20 個端點（17 個 `ErpController` ＋ 3 個 `AdminController`）。**§4.1~4.17 受 `ApiRouteDocDriftTest` 機械防漂移保護；§4.18~4.20 不受保護，見 §7。**

---

## 3. 資料模型 / Data Model

### 3.1 `Supplier`（表 `suppliers`）

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` / `tenantId` | UUID | |
| `name` | String | not null |
| `contactPerson` / `email` / `phone` / `address` | String | 皆選填 |
| `status` | enum `SupplierStatus` | `ACTIVE`／`INACTIVE`，建立時固定 `ACTIVE` |
| `createdAt` / `updatedAt` | Instant | |

### 3.2 `PurchaseOrder`（表 `purchase_orders`）＋ `PurchaseOrderItem`

| 欄位 | 類型 | 說明 |
|------|------|------|
| `id` / `tenantId` / `supplierId` | UUID | |
| `poNumber` | String | 格式 `PO-{yyyyMMdd}-{6 位隨機數}`，非原子產生（見 §7） |
| `status` | enum `POStatus` | 見 §1.2 第 3 點 |
| `totalAmount` | BigDecimal | 建立時由各品項 `unitCost × quantity` 加總 |
| `currency` | String | 固定 `"TWD"`，無其他幣別支援 |
| `expectedDeliveryDate` | LocalDate | 選填 |
| `submittedAt` / `receivedAt` / `reviewedBy` / `reviewedAt` / `rejectionReason` | — | 對應狀態轉換時回填 |
| `items[]` | `PurchaseOrderItem` | `listingId`／`skuId`（選填）／`quantity`／`orderedQty`／`receivedQuantity`（初始 0）／`unitCost`／`subtotal` |

### 3.3 庫存相關 DTO

| DTO | 欄位 | 說明 |
|------|------|------|
| `InventoryLedgerDto`／`InventoryDetailDto` | `skuId`／`skuCode`／`productName`／`quantity`（總量）／`reservedQuantity`／`availableQuantity`（= 總量−預留）／`lowStockThreshold`／`lastInboundDate`／`lastOutboundDate` | `InventoryDetailDto` 額外含 `movements[]` |
| `LowStockAlertDto` | `skuId`／`skuCode`／`productName`／`currentQuantity`（= `availableQuantity`）／`lowStockThreshold`／`severity`（`CRITICAL`／`LOW`） | 見 §1.2 第 6 點 |
| `StockMovementDto` | `id`／`tenantId`／`skuId`／`skuCode`／`productName`／`movementType`／`quantity`／`beforeTotalQty`／`afterTotalQty`／`referenceType`／`referenceId`／`referenceNumber`／`sourceDocument`／`notes`／`createdBy`／`createdAt` | `sourceDocument` 手動異動恆為 `null`（僅系統產生異動會填） |
| `ListingOptionDto` | `id`／`title`／`basePrice`／`currency`／`skus[]`（`id`／`skuCode`／`specName`） | 供 PO 建立時的品項選擇器 |

### 3.4 列舉

- `StockMovement.MovementType`：`INBOUND`／`OUTBOUND`／`RESERVE`／`RELEASE`（系統專用，手動建立會拒絕）、`ADJUST_PLUS`／`ADJUST_MINUS`／`TRANSFER_IN`／`TRANSFER_OUT`／`SCRAP`（手動可用）、`RETURN`（目前完全拒絕使用，見 §1.2 第 5 點）。
- `StockMovement.ReferenceType`：`PURCHASE_ORDER`／`ORDER`／`INVENTORY_CHECK`／`TRANSFER`／`MANUAL`。

---

## 4. 端點規格 / Endpoint Specifications

**§4.1~4.17 為 `ErpController` 端點（受機械防漂移保護）：**

### 4.1 `GET /v2/dashboard/purchase-orders/listing-options` — 採購單品項選擇器

**權限**：`hasAuthority('STORE_OWNER')` 或 `hasAuthority('SELLER')`。回傳本租戶前 200 筆 `ACTIVE` Listing（依標題排序），每筆附上已建立的 SKU 清單（無 SKU 回空陣列）。

### 4.2 `GET /v2/dashboard/suppliers` — 供應商列表

**權限**：同 §4.1。查詢參數 `status`（選填，`ACTIVE`／`INACTIVE`，大小寫不敏感）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `status` 無法解析成列舉 | 500 | `E-9900`（**非乾淨驗證錯誤**，見 §7 DEF-385——同一方法的 `updateSupplier` 已正確用 try-catch 轉譯為 `E-7010`，`listSuppliers` 未同步修復） |

### 4.3 `GET /v2/dashboard/suppliers/{id}` — 供應商詳情

**權限**：同 §4.1，且限本租戶。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 供應商不存在或非本租戶 | 404 | `E-7000` |

### 4.4 `POST /v2/dashboard/suppliers` — 建立供應商

**權限**：`hasAuthority('STORE_OWNER')`。請求：`name`（必填）、其餘皆選填。建立時狀態固定 `ACTIVE`（無法在建立時指定為 `INACTIVE`）。成功回應 HTTP **201**（本模組少數非 200 的成功狀態碼）。

### 4.5 `PUT /v2/dashboard/suppliers/{id}` — 更新供應商

**權限**：`hasAuthority('STORE_OWNER')`，限本租戶。局部更新語意，`status` 欄位有效值解析失敗會正確回 400（與 §4.2 的篩選參數不同，見 §7 DEF-385 對照）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 供應商不存在或非本租戶 | 404 | `E-7000` |
| `status` 無法解析成列舉 | 400 | `E-7010` |

### 4.6 `GET /v2/dashboard/purchase-orders` — 採購單列表

**權限**：同 §4.1。查詢參數 `status`（選填）、`page`（預設 0）、`size`（預設 20，上限 100）。**`status` 解析發生在 Controller 層**（非 Service），同樣無 try-catch。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| `status` 無法解析成 `POStatus` | 500 | `E-9900`（**非乾淨驗證錯誤**，見 §7 DEF-385） |

### 4.7 `POST /v2/dashboard/purchase-orders` — 建立採購單

**權限**：`hasAuthority('STORE_OWNER')`。需 `ERP_ENABLED` 功能開關啟用。

**請求**：`supplierId`（必填）、`expectedDeliveryDate`／`notes`（選填）、`items[]`（**無 `@NotNull`／`@NotEmpty` 驗證**，見 §7——`listingId`／`quantity`／`unitCost` 逐品項必填，`skuId` 選填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 功能開關未啟用 | 403 | `E-2004` |
| 供應商不存在或非本租戶 | 404 | `E-7008` |
| 品項的 `listingId` 不屬於本租戶 | 403 | `E-1007` |
| 品項的 `skuId` 不屬於該 `listingId` | 404 | `E-3003`（刻意與「SKU 真的不存在」同碼，避免洩漏他租戶 SKU 存在性） |
| `items` 為 `null` | 500 | `E-9900`（**`NullPointerException`**，見 §7 DEF-385——`items` 為空陣列 `[]` 則不會出錯，但會建立總額 0、零品項的空採購單） |

### 4.8 `GET /v2/dashboard/purchase-orders/{id}` — 採購單詳情

**權限**：同 §4.1，限本租戶。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在或非本租戶 | 404 | `E-7001` |

### 4.9 `PUT /v2/dashboard/purchase-orders/{id}` — 更新採購單

**權限**：`hasAuthority('STORE_OWNER')`，限本租戶，且**僅 `DRAFT` 狀態可更新**（悲觀鎖防併發）。可更新 `notes`／`expectedDeliveryDate`，不可修改品項。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在或非本租戶 | 404 | `E-7001` |
| 狀態非 `DRAFT` | 422 | `E-7002` |

### 4.10 `PUT /v2/dashboard/purchase-orders/{id}/submit` — 提交採購單

**權限**：`hasAuthority('STORE_OWNER')`，限本租戶，僅 `DRAFT` 可提交。超過租戶審批門檐（`purchaseOrderApprovalThreshold`，可為 `null`＝不啟用）轉 `PENDING_APPROVAL`，否則轉 `SUBMITTED`（視為已核准）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在或非本租戶 | 404 | `E-7001` |
| 狀態非 `DRAFT` | 422 | `E-7002` |

### 4.11 `PUT /v2/dashboard/purchase-orders/{id}/receive` — 確認收貨

**權限**：`hasAuthority('STORE_OWNER')`，限本租戶，狀態須為 `SUBMITTED`／`PARTIALLY_RECEIVED`／`APPROVED`。

**請求**：`items[]`（`itemId`／`receivedQuantity` 皆必填，可多次呼叫逐批收貨）。每品項累計收貨量不得超過訂購量；有 `skuId` 的品項會建立 `INBOUND` 庫存異動並原子增加總量。全部品項皆已收齊轉 `RECEIVED`，否則轉 `PARTIALLY_RECEIVED`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在或非本租戶 | 404 | `E-7001` |
| 狀態不可收貨 | 422 | `E-7002` |
| `itemId` 不屬於此採購單 | 404 | `E-7007` |
| 累計收貨量超過訂購量 | 400 | `E-7009` |
| SKU 不存在（理論上不可達，建立時已驗證） | 404 | `E-3003` |

### 4.12 `PUT /v2/dashboard/purchase-orders/{id}/cancel` — 取消採購單

**權限**：`hasAuthority('STORE_OWNER')`，限本租戶，狀態須為 `DRAFT`／`SUBMITTED`／`PENDING_APPROVAL`。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在或非本租戶 | 404 | `E-7001` |
| 狀態不可取消（已 `RECEIVED`／`PARTIALLY_RECEIVED`／`APPROVED`／`REJECTED`／`CANCELLED`） | 422 | `E-7002` |

### 4.13 `GET /v2/dashboard/inventory` — 庫存台帳

**權限**：同 §4.1。查詢參數 `page`（預設 0）、`size`（預設 50，上限 100）。資料來源 `product_inventory`（見 §1.2 第 1 點）。

### 4.14 `GET /v2/dashboard/inventory/{skuId}` — 庫存明細

**權限**：同 §4.1，限本租戶（經 SKU → Listing → tenantId 反查）。含該 SKU 的完整異動記錄列表。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| SKU 不存在或非本租戶 | 404 | `E-3003`（手寫 `ResponseEntity`，非 `BusinessException`，封包格式一致但不經過 `GlobalExceptionHandler`） |

### 4.15 `GET /v2/dashboard/inventory/alerts` — 低庫存預警列表

**權限**：同 §4.1。無查詢參數，回傳本租戶所有可售量低於門檻的 SKU（見 §1.2 第 6 點嚴重度判斷）。

### 4.16 `POST /v2/dashboard/stock-movements` — 手動庫存異動

**權限**：`hasAuthority('STORE_OWNER')`。需先驗證 SKU 存在且屬於本租戶（經 Listing 反查）。`movementType` 僅接受 §1.2 第 5 點列出的五種手動類型；此處的列舉解析**已正確**用 try-catch（與 §4.2／4.6 的篩選參數形成對照，見 §7 DEF-385）。

**請求**：`skuId`（必填）、`movementType`（必填字串）、`quantity`（必填，正整數）、`referenceNumber`／`notes`（選填）。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| SKU 不存在 | 404 | `E-3003` |
| SKU 不屬於本租戶 | 403 | `E-1007` |
| `movementType` 無法解析，或為 `INBOUND`／`OUTBOUND`／`RESERVE`／`RELEASE`／`RETURN` | 400 | `E-7005` |
| `ADJUST_MINUS`／`TRANSFER_OUT`／`SCRAP` 但庫存不足 | 400 | `E-7004` |

### 4.17 `GET /v2/dashboard/stock-movements` — 庫存異動記錄

**權限**：同 §4.1。查詢參數 `page`（預設 0）、`size`（預設 50，上限 100）。依建立時間降序。

---

**以下 §4.18~4.20 為 `AdminController` 端點（不受機械防漂移保護，見 §7）：**

### 4.18 待審批採購單列表

**端點（不受機械防漂移保護，見 §7）**：GET /v2/admin/purchase-orders/pending

**權限**：`hasRole('SUPER_ADMIN')`。跨租戶查詢全部 `PENDING_APPROVAL` 狀態採購單，依 `submittedAt` 升序（先提交先審）。查詢參數 `page`（預設 0）、`size`（預設 20，上限 100）。

### 4.19 核准採購單

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/purchase-orders/{poId}/approve

**權限**：`hasRole('SUPER_ADMIN')`。僅 `PENDING_APPROVAL` 可核准（`canReview()`），原子 CAS 防止與駁回或另一個核准請求併發競態，搶輸沿用同一錯誤碼。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在 | 404 | `E-7001` |
| 狀態非 `PENDING_APPROVAL`（含被併發搶先審批） | 422 | `E-7002` |

### 4.20 駁回採購單

**端點（不受機械防漂移保護，見 §7）**：POST /v2/admin/purchase-orders/{poId}/reject

**權限**：`hasRole('SUPER_ADMIN')`。**請求**：`reason`（必填）。其餘同 §4.19。

| 狀況 | HTTP | 錯誤碼 |
|------|:----:|--------|
| 採購單不存在 | 404 | `E-7001` |
| 狀態非 `PENDING_APPROVAL` | 422 | `E-7002` |

---

## 5. 權限矩陣 / Permission Matrix

| 端點群組 | SELLER | STORE_OWNER | STORE_STAFF | ADMIN／SUPER_ADMIN |
|------|:----:|:----:|:----:|:----:|
| 查詢類（供應商/採購單/庫存/異動列表與詳情，§4.1~4.3／4.6／4.8／4.13~4.15／4.17） | ✅（限本租戶） | ✅（限本租戶） | ❌（無 `STORE_OWNER`／`SELLER` 授權字串） | ❌（同左） |
| 寫入類（建立/更新供應商、採購單全部寫入操作、手動異動，§4.4／4.5／4.7／4.9~4.12／4.16） | ❌ | ✅（限本租戶） | ❌ | ❌ |
| 採購單審批（§4.18~4.20） | ❌ | ❌ | ❌ | ✅（僅 `SUPER_ADMIN`；`ADMIN` 角色**不可**，見 §5 補充） |

`AdminController` 的 `@PreAuthorize` 一律寫 `hasRole('SUPER_ADMIN')`，不含 `ADMIN`，故持有 `ADMIN` 角色但非 `SUPER_ADMIN` 的帳號無法審批採購單（與 [API_M14_Admin_Console.md](./API_M14_Admin_Console.md) §5 相同限制）。

---

## 6. 錯誤碼 / Error Codes

| 錯誤碼 | HTTP | 官方定義 | 本模組用途 |
|--------|:----:|---------|----------------|
| `E-1007` | 403 | 權限不足 | 品項/SKU 不屬於呼叫者租戶 |
| `E-2000` | 404 | 找不到租戶 | 審批門檐查詢（理論上不可達） |
| `E-2004` | 403 | 此租戶未啟用該功能 | `ERP_ENABLED` 功能開關未啟用 |
| `E-3003` | 404 | SKU 不存在 | §4.7／4.11／4.14／4.16 |
| `E-7000` | 404 | 找不到供應商 | §4.3（本模組唯一正確使用此碼之處） |
| `E-7001` | 404 | 找不到採購單 | §4.8～4.12／4.18～4.20（本模組正確使用；對照 M11 誤用同碼，見 [API_M11_Logistics.md](./api/API_M11_Logistics.md) §7 DEF-381） |
| `E-7002` | 422 | 無效的採購單狀態 | §4.9～4.12／4.19～4.20 |
| `E-7004` | 400 | 庫存不足 | §4.16（`ADJUST_MINUS`／`TRANSFER_OUT`／`SCRAP`） |
| `E-7005` | 400 | 無效的庫存調整 | §4.16（`movementType` 非法或被禁止的手動類型） |
| `E-7007` | 404 | 找不到採購單項目 | §4.11 |
| `E-7008` | 404 | 供應商已停用 | §4.7（訊息寫「not found or inactive」，實際是 `existsByIdAndTenantId` 的存在性檢查，與「已停用」無關，見 §7） |
| `E-7009` | 400 | 無效的收貨數量 | §4.11 |
| `E-7010` | 400 | 無效的供應商狀態 | §4.5（正確使用）；§4.2 本該用此碼卻直接 500，見 §7 DEF-385 |
| `E-9000` | 400 | 驗證錯誤 | `@Valid` 請求體驗證失敗 |
| `E-9900` | 500 | 內部伺服器錯誤 | §4.2／4.6／4.7 三處輸入驗證缺口誤觸，見 §7 DEF-385 |

---

## 7. 已知限制 / Known Limitations

| 項目 | 說明 |
|------|------|
| `ApiRouteDocDriftTest` 僅保護 §4.1~4.17 | `AdminController`（§4.18~4.20）混雜 M14／M17 端點，無法整個類別納入守門，理由與 [API_M14_Admin_Console.md](./API_M14_Admin_Console.md) §7、[API_M17_Tenant.md](./API_M17_Tenant.md) §7 相同 |
| **多處輸入驗證缺口，特定輸入觸發 500 而非乾淨的 4xx（DEF-385，新登記，跨模組）** | 三個實例：(1) §4.2 `listSuppliers` 的 `status` 篩選、(2) §4.6 `listPurchaseOrders` 的 `status` 篩選，皆以 `Enum.valueOf()` 手動解析查詢參數卻未包 try-catch，無法解析時拋出的 `IllegalArgumentException` 落入 `GlobalExceptionHandler` 的 catch-all（`GlobalExceptionHandler` 刻意不設全域 `IllegalArgumentException` handler，Sprint 161 已拍板，理由是此例外也可能源自非使用者輸入的程式邏輯，全域攔截有掩蓋真正錯誤的風險）；(3) §4.7 `createPurchaseOrder` 的 `items` 欄位無 `@NotNull`／`@NotEmpty`，傳入 `null` 時在 for-each 迴圈觸發 `NullPointerException`，同樣落入 catch-all。三者皆為 500／`E-9900`。對照：同一類別內 §4.5 `updateSupplier`、§4.16 `createManualMovement` 的列舉解析都正確包了 try-catch，顯示這是「部分端點修過、部分端點沒跟上」的不一致，不是刻意設計。另一相關實例（`LogisticsController.updateLogisticsStatus`，M11 模組）已記錄於 [API_M11_Logistics.md](./api/API_M11_Logistics.md) §7，三個實例合併登記同一個 DEF |
| `E-7008` 的訊息與官方定義不符 | 官方定義「供應商已停用」，但 `validateSupplier` 實際檢查的是 `existsByIdAndTenantId`（純存在性＋租戶歸屬），不检查 `status`——停用（`INACTIVE`）的供應商仍可用於建立採購單，訊息文字「not found or inactive」暗示會檢查停用狀態，但程式碼並未如此 |
| `PurchaseOrder.POStatus.ORDER`／`SHIPPED` 是未使用的保留列舉值 | 全庫無任何程式碼路徑會設定這兩個狀態，`canSubmit`／`canReceive`／`canCancel` 三個狀態機方法都不提及 |
| 採購單號非原子產生 | `PO-{日期}-{6 位隨機數}` 僅靠隨機數范围（900000 種組合）降低碰撞機率，無資料庫層級的重試保證；真實碰撞機率極低但非結構性保證（另見 [[po-number-random-collision-def297]] 類似的既有教訓，本模組未採用同類保護） |
| 審批門檐為租戶層級單一值 | `Tenant.purchaseOrderApprovalThreshold` 不分供應商／品類，無法設定「特定供應商一律需審批」等更細緻規則 |
| `GET /inventory/{skuId}` 的錯誤回應繞過 `GlobalExceptionHandler` | 直接 `ResponseEntity.status(404).body(ApiResponse.error(...))`，封包格式與其他端點一致，但不經過集中式例外處理（純風格差異，非功能缺陷） |

---

## 8. 追蹤性 / Traceability

| 來源 | 內容 |
|------|------|
| PRD | §6.7（進銷存流程）／§6.7.2（審批金額上限）／§9.15（ERP API） |
| FRD | 無章節（見文件頂部狀態說明） |
| 相關文件 | [API_M14_Admin_Console.md](./API_M14_Admin_Console.md)（`AdminController` 的 M14 範圍端點）、[API_M17_Tenant.md](./API_M17_Tenant.md)（`AdminController` 的 M17 範圍端點）、[API_M11_Logistics.md](./api/API_M11_Logistics.md)（DEF-385 其他實例） |
| 實作 | `ErpController`（`api/controller/`）；`AdminController`（`api/controller/`，僅 §4.18~4.20）；`SupplierService`／`PurchaseOrderService`／`InventoryService`／`StockMovementService`（`core/erp/`） |
| 契約測試 | [SupplierServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/SupplierServiceTest.java)、[PurchaseOrderServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/PurchaseOrderServiceTest.java)、[InventoryServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/InventoryServiceTest.java)、[StockMovementServiceTest](../../../backend/src/test/java/com/nextkey/ecommerce/core/erp/StockMovementServiceTest.java)、[M16ErpE2ETest](../../../backend/src/test/java/com/nextkey/ecommerce/api/controller/M16ErpE2ETest.java)、[M16ErpIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpIntegrationTest.java)、[M16ErpInventoryConcurrencyIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpInventoryConcurrencyIntegrationTest.java)、[M16ErpInventoryLedgerIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpInventoryLedgerIntegrationTest.java)、[M16ErpStockMovementDisplayIntegrationTest](../../../backend/src/test/java/com/nextkey/ecommerce/integration/M16ErpStockMovementDisplayIntegrationTest.java) |

---

**文件狀態**：v1.0（Sprint 276 新建，DEF-286 範圍）。
