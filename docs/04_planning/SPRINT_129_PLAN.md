# Sprint 129 Plan — DEF-075 權限碼孤兒 + DEF-076~079 ERP 採購單/供應商四頁修復

**Sprint**: Sprint 129
**日期**: 2026-09-05

---

## 1. 本輪範圍

承 Sprint 128 契約漂移全掃留下的 DEF-075~DEF-092（18 項待排程），使用者於本輪開工時點名兩組高優先級：

- **DEF-075**：13 個權限碼在 `Permission` 枚舉不存在，`dashboard`/`faq`/`knowledge`/`media` 四模組端點對所有角色（含 SUPER_ADMIN）必定 403。
- **DEF-076~079**：ERP 採購單建立/檢視/編輯與供應商檢視/編輯共四個頁面，同型於 Sprint 127 DEF-071——承 Sprint 127 只修了收貨頁，其餘頁面同樣從未真正運作過。

**AskUserQuestion 拍板紀錄**：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | Sprint 129 涵蓋範圍 | 僅此 5 項（DEF-075、076、077、078、079），其餘 13 項（080~092，扣除已修復）繼續登記待下輪 |
| 2 | DEF-076：採購單品項如何綁定平台 listing | 新增下拉選擇器，送出 `listingId`（比照供應商欄位既有寫法，另補後端租戶過濾端點） |
| 3 | DEF-075：dashboard/faq/knowledge/media 角色授權方案 | 採用建議方案：`*:read` → OWNER+STAFF+ADMIN；`create/update/delete` → OWNER+ADMIN（STAFF 唯讀，比照既有 NOTIFICATION_TEMPLATE/SUPPORT_TICKET/RETURN 先例） |
| 4 | DEF-092（cms:\*/notification:create）定性 | 判定為「忘記授權」而非刻意平台專屬，比照 `PostController` 授權開放——**但本輪範圍已鎖定 5 項，此決策記錄供下一輪直接執行，本輪不實作** |
| 5 | DEF-078：`expectedDeliveryDate` 修法 | 補進實體/mapper（新增欄位 + migration），而非前端改容忍 null |

---

## 2. DEF-075：dashboard/faq/knowledge/media 權限碼孤兒

**缺陷**：`Permission.java` 從未定義 `dashboard:read`、`faq:read/create/update/delete`、`knowledge:read/create/update/delete`、`media:read/create/update/delete` 共 13 個碼，但 `AnalyticsController`／`FaqArticleController`／`FaqCategoryController`／`KnowledgeArticleController`／`KnowledgeCategoryController`／`ArticleVersionController`／`MediaController`／`MediaCategoryController` 的 `@PreAuthorize` 皆引用這些碼。生產唯一授權來源 `RolePermissionMapping.getAuthorities()` 只發枚舉內已定義的碼，`SUPER_ADMIN` 的 `EnumSet.allOf(Permission.class)` 也給不了枚舉沒有的東西——**端點對所有角色（含 SUPER_ADMIN）必定 403**。

**角色分派**（比照 `NOTIFICATION_TEMPLATE_*`/`SUPPORT_TICKET_*`/`RETURN_*` 先例：owner 全 CRUD、staff 唯讀、admin 跨租戶全 CRUD、super_admin 經 `EnumSet.allOf` 自動取得）：

- `STORE_OWNER`：13 碼全授。
- `STORE_STAFF`：僅 `dashboard:read`／`faq:read`／`knowledge:read`／`media:read`。
- `ADMIN`：13 碼全授（跨租戶）。

**修復**：`Permission.java` 新增 13 常數；`RolePermissionMapping.java` 依上述分派；`PreAuthorizePermissionCoverageTest.KNOWN_UNMAPPED_PENDING_DEF_075` 清空（原本列這 13 碼，現已全數修復，清冊必須同步移除，否則違反「清冊不得包含已修好的碼」的既有防復發規則）。

**測試**：`RolePermissionMappingTest` 新增 4 案例（STORE_OWNER 全權、STORE_STAFF 唯讀無寫入權、ADMIN 全權、SUPER_ADMIN 經 `allOf` 自動取得）；`PreAuthorizePermissionCoverageTest` 新增 `def075CodesAreDefined` 案例斷言 13 碼已在枚舉中；既有 `pendingListContainsOnlyStillUnmappedCodes`／`everyReferencedAuthorityCodeIsDefined` 兩支測試在清冊清空後自然轉綠，作為獨立確認。

---

## 3. DEF-076 + DEF-077：ERP 建立採購單必定 400（兩個根因耦合，一起修）

**缺陷**：
- DEF-076：後端 `PurchaseOrderCreateRequest.PurchaseOrderItemRequest.listingId` 為 `@NotNull`，前端 `PurchaseOrderCreateRequest` 型別裡**完全沒有 `listingId` 欄位**，`PurchaseOrderForm.tsx` 建單表單也從未提供任何選擇平台 listing 的 UI。
- DEF-077：前端送 `unitPrice`，後端要求的欄位名是 `unitCost`（全庫無 Jackson `PropertyNamingStrategy`，欄位名須逐字相符），`unitPrice` 被靜默丟棄、`unitCost` 恆為 null，觸發 `@NotNull` 400（若繞過驗證則 `PurchaseOrderService.java:98` 對 null 呼叫 `.multiply()` 會 NPE）。

兩者共用同一段 `items[]` payload，必須一起修才能讓建單流程真正跑通。

**額外查證（決定實作方案的關鍵事實）**：
- 已有 `GET /v2/listings`，但**不接受 tenantId 參數、不過濾租戶**，會跨租戶列出所有 ACTIVE listing；且回傳的是裸 `Listing` 實體。
- `Listing.owner` 為 `@ManyToOne(fetch=LAZY)` 關聯到 `User`，`User.passwordHash` **沒有 `@JsonIgnore`**；專案未設定 Jackson Hibernate 模組、未關閉 `spring.jpa.open-in-view`（預設 `true`）——若序列化裸 `Listing` 實體，懶載入會在回應序列化時觸發並把整個 `User`（含密碼雜湊）序列化進 JSON。**這是 `ListingController.getListings()` 既有的潛在洩漏面**，超出本輪範圍不予修改，但為了不讓新端點重蹈覆轍，新端點刻意回傳輕量 DTO 而非裸實體（詳見 §7 一併登記為新缺陷）。
- 前端全庫沒有任何「可搜尋的下拉選擇/combobox」元件；最接近的既有寫法是同一份表單「供應商」欄位——載入全部清單後平舖成原生 `<select>`。
- `PurchaseOrderItem` 實體已有 `skuCode`／`productName` 兩個純文字欄位（無驗證約束），但 create request 沒有對應欄位可接收；`listingId` 在 DB 與 DTO 皆為必填，無「免綁定」的替代欄位。
- `Listing`／`PurchaseOrder` 皆有 `tenantId`，`ListingRepository` 已具備 `findByTenantIdAndStatus` 可用於租戶過濾。

**修復**：
- **後端**：`ErpController` 新增 `GET /v2/dashboard/purchase-orders/listing-options`（`STORE_OWNER`/`SELLER`），以 `TenantContext.getCurrentTenant()` + `ListingRepository.findByTenantIdAndStatus(tenantId, ACTIVE, ...)` 回傳新輕量 DTO `ListingOptionDto`（`id`/`title`/`basePrice`/`currency`），不夾帶 `owner`/`tenant` 關聯。路徑刻意避開 `/v2/dashboard/listings`——該路徑已被 `AnalyticsController#getListingStats()` 佔用（Ambiguous mapping，紅燈實測發現）。
- **前端**：`purchaseOrder.ts` 的 create-request items 型別改為 `{ listingId, quantity, unitCost }`；新增 `ListingOption` 型別與 `listListingOptions()` service 方法；`PurchaseOrderForm.tsx` create 模式下新增「商品」下拉選擇器（載入租戶 listing 清單），選擇後同步帶入品名作為顯示用；`handleCreate` payload 對齊新型別（`unitCost: item.unitPrice`），移除原本綁在自由文字輸入框上、從未對映到有效 UUID 的 `skuId` 送出邏輯（該輸入框過去打的是任意文字如「SKU001」，即使補上 `listingId`／`unitCost` 也會因 `skuId` 反序列化失敗而繼續 400）。

**測試**：`M16ErpIntegrationTest` 新增 IT-M16-012（listing 選項端點：回傳本租戶 ACTIVE listing、排除 DRAFT、排除他租戶 listing）；既有 `PurchaseOrderServiceTest`／`M16ErpIntegrationTest`／`M16ErpE2ETest` 建單案例本就使用正確的 Java DTO（非前端 JSON 字面），故不會因這兩個缺陷而變紅——這正是 DEF-076/077 能存活的原因（後端測試從未真正走過前端送出的 JSON 型狀）。前端以 `tsc --noEmit`（0 error）+ `eslint`（0 新增 warning）驗證契約對齊，未新增 Playwright E2E（純契約修正，比照 Sprint 127 DEF-071 同一判準）。

---

## 4. DEF-078：ERP 採購單檢視/編輯頁必定顯示「載入採購訂單失敗」

**缺陷**：`PurchaseOrderDto.expectedDeliveryDate` 有欄位宣告，但 `PurchaseOrder` 實體**根本沒有此欄位**、`toDto()` 從未賦值，回應恆為 `null`；前端 `PurchaseOrderForm.tsx` 對 `data.expectedDeliveryDate.split('T')[0]` 直接呼叫，null 觸發 TypeError，被同檔案的 catch 區塊吞掉轉譯成「載入採購訂單失敗」，整頁不可用。此外前端建單表單其實已經在收集 `expectedDeliveryDate` 並嘗試送出，但後端 `PurchaseOrderCreateRequest` 同樣沒有此欄位，值從建立當下就被 Jackson 靜默丟棄——不只是讀取端的問題，寫入端也從未真正生效過。

**修復**：`PurchaseOrder` 實體新增 `expectedDeliveryDate`（`LocalDate`，可為 null）；`V78__Add_Purchase_Order_Expected_Delivery_Date.sql` 新增對應欄位；`PurchaseOrderCreateRequest` 新增選填欄位；`PurchaseOrderService.createPurchaseOrder`／`toDto` 分別寫入與讀出（讀出時轉為 ISO 日期字串，與前端 `<input type="date">` 格式一致）。前端 `fetchOrder` 額外補上 null 防呆（`data.expectedDeliveryDate ? ... : ''`）——即使補齊 schema，修復前建立的既有採購單此欄位仍會是 null，防呆本身也是必要的，而非取代根因修復的權宜之計。

**範圍外**：`PurchaseOrderUpdateRequest` 依既有規格（DEF-090，登記待排程）刻意不接受 `expectedDeliveryDate`／`items` 變更，故編輯模式的「更新訂單」仍是既有的未實作按鈕，本輪不處理。

**測試**：`PurchaseOrderServiceTest` 新增 3 案例（建單帶日期時實體與回傳皆正確、不帶日期時保持 null、`getPurchaseOrder` 對已設定日期的既有實體正確轉為 ISO 字串）。`make validate-schema` 全綠（entity 與新 migration 對齊，無漂移）。

**文件同步**：`make validate-schema-doc`（pre-commit 掛鉤）攔下 PRD §8.2.8 `purchase_orders` 漏列新欄位——該表格恰好記載 Sprint 111 的一次更正（`v1.0.2`）：原文件的 `expected_at`「預計到貨日」欄位查證後**在實作中不存在**而被移除記載。本輪需明確區分：這次新增的是**不同名稱**的 `expected_delivery_date`，非重新引入已證實不存在的 `expected_at`；已於 PRD 補上欄位列 + `v1.0.3` 更正註記說明兩者關係。

---

## 5. DEF-079：ERP 供應商檢視/編輯頁必定失敗

**缺陷**：`ErpController` 的 supplier 區塊只有 `GET /suppliers`（列表）、`POST`、`PUT /{id}`，沒有 `GET /suppliers/{id}`（單筆）。前端 `SupplierForm.tsx` 檢視/編輯模式已正確呼叫 `SupplierService.getSupplier(id)`，`SupplierService.getSupplier()` 後端邏輯也早已存在，純粹是 controller 缺一支 mapping；因 `GlobalExceptionHandler` 的 catch-all 優先序高於 Spring 預設的 405 解析，實際回應是 500 而非預期的 405。

**修復**：`ErpController` 新增 `GET /suppliers/{id}`（`STORE_OWNER`/`SELLER`），呼叫既有 `SupplierService.getSupplier()`。前端無需變更。

**測試**：`M16ErpIntegrationTest` 新增 IT-M16-009（成功）、IT-M16-010（不存在回 404）、IT-M16-011（他租戶供應商回 404，租戶隔離）。

---

## 6. 本輪未修、已登記待排程的項目

DEF-080~092（扣除本輪決策已記錄的 DEF-092），詳見 `DEFERRED_ITEMS_TRACKER.md`。DEF-092 的產品定性已由使用者拍板（比照 `PostController` 開放），下一輪可直接依此實作，無需重新拍板。

## 7. 過程中意外發現、登記為新缺陷

**DEF-093（新登記）**：`ListingController.getListings()`／`getListing()` 直接序列化裸 `Listing` 實體，其 `owner`（`@ManyToOne(fetch=LAZY)` → `User`）在 open-in-view 預設開啟、且專案未設定 Jackson Hibernate 模組的情況下，序列化時會觸發懶載入並把整個 `User` 實體（**含 `passwordHash`，無 `@JsonIgnore`**）序列化進 API 回應。過程中查證 DEF-076 的既有 listing 查詢端點時發現，超出本輪範圍未修改（本輪新增的 listing 選擇器端點刻意改回傳輕量 DTO，未重蹈覆轍）。**建議下一輪優先評估**——屬敏感資料洩漏，非單純功能缺陷。

---

## 8. 驗證

- 新增/修改測試：`RolePermissionMappingTest`（+4）、`PreAuthorizePermissionCoverageTest`（+1，另清空 `KNOWN_UNMAPPED_PENDING_DEF_075`）、`PurchaseOrderServiceTest`（+3）、`M16ErpIntegrationTest`（+4：IT-M16-009~012）
- 後端全量回歸 `mvn -o verify`：**BUILD SUCCESS**，單元 **1090**（相對 Sprint 128 的 1082，+8，恰等於本輪新增測試數）、整合 **463**（相對 459，+4，恰等於本輪 `M16ErpIntegrationTest` 新增的 4 案例），0 Failures / 0 Errors；checkstyle（main+test）**0 violations**
- `make validate-schema` 通過（entity↔migration 對齊，`V78` 無漂移）
- 前端：`tsc --noEmit` 0 error、`eslint` 0 新增 warning（既有警告與本次改動無關，未動）
- 新增 migration：`V78__Add_Purchase_Order_Expected_Delivery_Date.sql`
