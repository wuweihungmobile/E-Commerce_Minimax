# Sprint 72 計劃 / Sprint 72 Plan

> **Sprint 編號**: Sprint 72
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_71_PLAN.md` §7「後續 Sprint 待處理清單」——ERP 模組整體（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在）；範圍與拆分方式已於本 Sprint 開始前經使用者確認（詳見「前置條件確認」）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 71 收尾狀態 | ✅ 已收尾並 push | `RoomCalendarService`/`BookingService.createBooking`/`updateBooking` 日期變更測試強化完成 |
| ERP 模組範圍探查 | ✅ 完成，使用者已確認 | 4 個 Service（`SupplierService` 5 方法 / `StockMovementService` 4 方法 / `PurchaseOrderService` 7 方法 / `InventoryService` 3 方法，共 19 方法、881 行）與 Sprint 71（2 個 Service、18 方法、1141 行）同量級，**單一 Sprint 涵蓋、不拆分** |
| ⚠️ **前置認知落差誠實揭露** | 探勘證實：ERP 模組**單元測試層（`src/test/.../core/erp/`）確實完全不存在**，但**功能行為並非零覆蓋**——`M16ErpIntegrationTest.java`（37 個測試，IT-M16-001~306）與 `M16ErpE2ETest.java`（6 個測試，E2E-M16-001~006）已存在且全數通過，透過 MockMvc 對 `ErpController` 做完整業務流程驗證。本 Sprint 目標是補齊 Service 層純 Mockito 單元測試，既有整合/E2E 測試的業務情境可作為預期行為依據，不需重新猜測規格 | 依 Rule 12「大聲失敗」誠實揭露：「測試目錄不存在」精確地說是單元測試層空白，非完全零驗證 |
| 探查中發現的租戶隔離問題 | ✅ 已標記並經使用者決策：**併入本 Sprint 處理** | 詳見 US-002（A 項，已確認）、US-003（B 項，待驗證） |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

| Service | 路徑 | 行數 | public 方法 | 既有單元測試 | 既有整合/E2E 涵蓋 |
|---|---|---|---|---|---|
| `SupplierService` | `core/erp/SupplierService.java` | 160 | 5：`createSupplier`/`getSupplier`/`listSuppliers`/`searchSuppliers`/`updateSupplier` | 無 | IT-M16-001~008 |
| `StockMovementService` | `core/erp/StockMovementService.java` | 199 | 4：`createManualMovement`/`getMovementsBySku`/`getMovements`/`getMovementsByDateRange` | 無 | IT-M16-301~306 |
| `PurchaseOrderService` | `core/erp/PurchaseOrderService.java` | 350 | 7：`createPurchaseOrder`/`getPurchaseOrder`/`listPurchaseOrders`/`updatePurchaseOrder`/`submitPurchaseOrder`/`receivePurchaseOrder`/`cancelPurchaseOrder` | 無 | IT-M16-101~117、E2E-M16-001~006 |
| `InventoryService` | `core/erp/InventoryService.java` | 172 | 3：`getInventoryLedger`/`getInventoryBySku`/`getLowStockAlerts` | 無 | IT-M16-201~205 |

REST 層由單一 `ErpController.java` 暴露於 `/api/v2/dashboard/*`；3 個舊版分拆 controller（`*.bak`）為未追蹤於 git 的死碼殘留，未參與編譯，本 Sprint 不處理。

---

## 2. Sprint 72 目標

> **主題**: ERP 模組（Supplier/StockMovement/PurchaseOrder/Inventory）單元測試從零建立 + 2 項探查發現的跨租戶問題處理（1 項確認修復、1 項驗證後視結果決定）

為 ERP 模組 4 個 Service 建立純 Mockito 隔離的單元測試（正常路徑 + 邊界/錯誤路徑 + 租戶隔離），並處理探查階段發現的 2 項疑似跨租戶問題：`InventoryService.getInventoryBySku` 確認的讀取洩漏（先寫紅測試證明、再修復轉綠）、`PurchaseOrderService.createPurchaseOrder` 疑似庫存挪用（先寫測試驗證推論是否成立，成立才修，不成立則據實記錄澄清）。

---

## 3. User Story

### US-001：ERP 4 個 Service 單元測試從零建立

> **SP**: 8 | **優先級**: P1 | **狀態**: 待執行

**AC-001-1**：新增 `SupplierServiceTest.java`，涵蓋 `createSupplier`（正常路徑，租戶注入）、`getSupplier`（存在/不存在拋 `E_7000`）、`listSuppliers`（含/不含 status 篩選）、`searchSuppliers`（關鍵字模糊比對）、`updateSupplier`（部分欄位更新、不存在拋例外）。

**AC-001-2**：新增 `StockMovementServiceTest.java`，涵蓋 `createManualMovement`（正常路徑各 `MovementType` 分支：`ADJUSTMENT`/`TRANSFER_IN` 增加、`DAMAGE`/`TRANSFER_OUT`/`THEFT` 減少且庫存不足拋 `E_7004`、禁止 `PURCHASE_RECEIPT`/`SALE`/`RESERVATION`/`RELEASE` 拋 `E_7005`、無效 enum 字串拋 `E_7005`、SKU 不存在拋 `E_3003`、既有 DEF-017 租戶檢查：SKU 所屬 listing 非當前租戶拋 `E_1007`）、`getMovementsBySku`/`getMovements`/`getMovementsByDateRange`（租戶過濾正確傳遞）。

**AC-001-3**：新增 `PurchaseOrderServiceTest.java`，涵蓋 `createPurchaseOrder`（正常路徑、多品項金額加總、supplier 不存在/非本租戶拋 `E_7008`、PO Number 格式）、`getPurchaseOrder`/`listPurchaseOrders`（含/不含狀態篩選、租戶過濾）、`updatePurchaseOrder`（僅 DRAFT 可更新，非 DRAFT 拋 `E_7002`）、`submitPurchaseOrder`（DRAFT→SUBMITTED，非 DRAFT 拋 `E_7002`）、`receivePurchaseOrder`（全數收貨→RECEIVED、部分收貨→PARTIALLY_RECEIVED、品項不存在拋 `E_7007`、非 SUBMITTED/PARTIALLY_RECEIVED 拋 `E_7002`、收貨連動建立 `StockMovement` 且 `PURCHASE_RECEIPT` 類型/`referenceType=PURCHASE_ORDER`）、`cancelPurchaseOrder`（DRAFT/SUBMITTED 可取消，其餘拋 `E_7002`）。

**AC-001-4**：新增 `InventoryServiceTest.java`，涵蓋 `getInventoryLedger`（分頁、租戶過濾）、`getInventoryBySku`（存在回傳含異動記錄、不存在回傳 `null`——**正常路徑測試，跨租戶洩漏場景另見 US-002**）、`getLowStockAlerts`（`CRITICAL`/`LOW` 嚴重度判定邊界值）。

**AC-001-5**：測試風格比照既有 `BookingServiceOwnershipTest`/`PaymentStateServiceTest`：`@ExtendWith(MockitoExtension.class)` + `@Mock` repository + `@InjectMocks` service，`TenantContext.setCurrentTenant(...)` 於 `@BeforeEach`／`TenantContext.clear()` 於 `@AfterEach`，以既有 `M16ErpIntegrationTest.java`/`M16ErpE2ETest.java` 的業務情境（IT-M16-xxx/E2E-M16-xxx）作為預期行為依據，不重新猜測規格。

**AC-001-6**：過程中若發現生產程式碼問題（bug/擁有權缺口）則停止修改、記錄待決策，不自行假設修復（US-002/US-003 已知的 2 項除外，因已於本 Sprint 前經使用者決策）。

---

### US-002：修復 `InventoryService.getInventoryBySku` 跨租戶讀取洩漏（已確認）

> **SP**: 2 | **優先級**: P0（安全） | **狀態**: 待執行

**背景**：探查確認 `InventoryService.getInventoryBySku(skuId)`（`core/erp/InventoryService.java:57`）呼叫 `inventoryRepository.findBySkuId(skuId)` 未帶入 `tenantId` 過濾，即使 `Inventory` entity 已有 `tenantId` 欄位、`InventoryRepository` 也已提供 `findByTenantId` 系列方法。`ErpController.getInventoryDetail`（`GET /api/v2/dashboard/inventory/{skuId}`）雖取得 `tenantId` 但僅用於 log，未傳入 service 過濾。任何登入的 `STORE_OWNER`/`SELLER` 可讀取其他租戶的 SKU 庫存數量、儲位、安全庫存、再訂購點等商業資料（子清單 `movements` 已正確做租戶過濾，但主體庫存資料未過濾）。

**AC-002-1**：先在 `InventoryServiceTest.java`（或獨立 `InventoryServiceOwnershipTest.java`）新增一個**修復前會失敗（紅燈）**的測試：以租戶 A 的 `TenantContext` 呼叫 `getInventoryBySku(租戶B的skuId)`，驗證修復前會錯誤回傳租戶 B 的庫存資料。

**AC-002-2**：修復 `InventoryService.getInventoryBySku`，改用租戶過濾查詢（比照 `InventoryRepository.findByTenantId` 模式新增 `findBySkuIdAndTenantId` 或於 service 層加上 `tenantId` 比對後拋 `E_3003`/回傳 `null`，需與既有「不存在回傳 `null`」語意一致，避免破壞 `ErpController` 的 404 分支）。

**AC-002-3**：AC-002-1 測試在修復後轉綠；新增租戶 A 查詢自己 SKU 正常回傳的對照測試，避免修復矯枉過正。

**AC-002-4**：修復後需執行全量回歸 `mvn verify -Pintegration-test`，確認未破壞既有 `M16ErpIntegrationTest`/`M16ErpE2ETest`（IT-M16-201~205 等庫存相關案例）。

**AC-002-5**：於 `DEFERRED_ITEMS_TRACKER.md` 記錄本項為新發現並已修復的安全缺口（比照 `DEF-016`~`DEF-024` 命名慣例，編號 `DEF-026`），註記修復 Sprint 與 commit。

---

### US-003：驗證 `PurchaseOrderService.createPurchaseOrder` 疑似跨租戶庫存挪用（待驗證，視結果決定是否修復）

> **SP**: 3 | **優先級**: P1（安全驗證） | **狀態**: 待執行

**背景**：探查發現 `PurchaseOrderCreateRequest.PurchaseOrderItemRequest` 接受呼叫端直接傳入的 `listingId`/`skuId`，`PurchaseOrderService.createPurchaseOrder` 未驗證該 SKU/Listing 是否屬於當前租戶（對照同檔案 `StockMovementService.createManualMovement` 已有 DEF-017 修復的 `listing.getTenantId()` 檢查）。推論：租戶 A 若在建立採購單時填入租戶 B 的 `skuId`，後續 `receivePurchaseOrder` 收貨會呼叫 `createInboundMovement` → `productInventoryRepository.findById(item.getSkuId())` 直接對該 SKU 加庫存，可能導致「用租戶 A 的採購單，把庫存加到租戶 B 的商品上」。**此推論尚未寫測試驗證，需先確認是否成立**。

**AC-003-1**：新增測試（`PurchaseOrderServiceTest.java` 或獨立 `PurchaseOrderServiceCrossTenantTest.java`）：以租戶 A 的 `TenantContext` 呼叫 `createPurchaseOrder`，`items` 中的 `skuId` 指向租戶 B 的 `ProductInventory`（mock `productInventoryRepository`/`listingRepository` 回傳租戶 B 的資料），驗證建立/提交/收貨（`receivePurchaseOrder`）流程是否真的成功且將庫存加到租戶 B 的 `ProductInventory` 上。

**AC-003-2**：**若測試證實漏洞成立**（未被任何機制阻擋）：比照 `StockMovementService.createManualMovement` 的 DEF-017 修復模式，於 `createPurchaseOrder`（或 `validateSupplier` 旁）新增品項 `skuId`/`listingId` 租戶歸屬驗證，不符則拋 `BusinessException`（沿用 `E_1007` 或視情況新增對應 `ErrorCode`），並讓 AC-003-1 測試轉綠。

**AC-003-3**：**若測試證實有其他機制已阻擋**（例如前端/其他層已限制 `skuId` 只能來自該租戶自己的 listing、或資料庫層 FK/約束已排除跨租戶情境）：不修改生產程式碼，於本 Sprint 收尾文件與程式碼註解中據實記錄「已驗證非漏洞」的結論與依據，避免為不存在的問題硬修（比照 Rule 3「精準改動」）。

**AC-003-4**：無論 AC-003-2 或 AC-003-3 的結論，皆需於 `DEFERRED_ITEMS_TRACKER.md` 記錄驗證過程與結論（若修復則編號 `DEF-027`；若判定非漏洞則記錄為「已排查澄清」項目，不佔用 DEF 編號但保留可追溯紀錄）。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | ERP 4 個 Service 單元測試從零建立 | 8 | P1 |
| US-002 | 修復 `InventoryService.getInventoryBySku` 跨租戶讀取洩漏（已確認） | 2 | P0（安全） |
| US-003 | 驗證 `PurchaseOrderService.createPurchaseOrder` 疑似跨租戶庫存挪用 | 3 | P1（安全驗證） |
| **合計** | | **13** | |

> **Velocity 參考**：較 Sprint 66/67/69/71（皆 8 SP）略高，因本 Sprint 額外包含 2 項安全問題的測試先行驗證與可能的修復工作，不僅是純測試補強。

---

## 5. Definition of Done

- [x] US-001：新增 `SupplierServiceTest.java`（新檔，9 個測試）
- [x] US-001：新增 `StockMovementServiceTest.java`（新檔，17 個測試）
- [x] US-001：新增 `PurchaseOrderServiceTest.java`（新檔，18 個測試）
- [x] US-001：新增 `InventoryServiceTest.java`（新檔，6 個測試）
- [x] 開發-編譯-測試循環：每新增一個測試檔案後立即編譯 + 執行該檔驗證通過，才繼續下一檔，未累積
- [x] US-002：新增修復前紅燈測試（確認失敗）→ 修復 `InventoryService.getInventoryBySku` → 測試轉綠
- [x] US-003：新增驗證測試（確認失敗，證實漏洞成立）→ 修復 `PurchaseOrderService.createPurchaseOrder` → 測試轉綠
- [x] 因本 Sprint 修改生產程式碼（US-002 + US-003），完成後執行全量回歸 `mvn verify -Pintegration-test`，**1032 tests 0 fail**（單元 690 + 整合 342），BUILD SUCCESS
- [x] `make validate-schema` 無漂移，EXIT_CODE=0
- [x] `DEFERRED_ITEMS_TRACKER.md` 更新（`DEF-026`、`DEF-027` 皆已完成並記入「已完成延後項目」）
- [x] Sprint 72 Review / Retro / Release Notes（`RELEASE_NOTES_v2028.07.29-01.md`）+ trackers（`RELEASE_TRACKER.md`）
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `SupplierServiceTest.java`（新檔） |
| 後端測試 | `StockMovementServiceTest.java`（新檔） |
| 後端測試 | `PurchaseOrderServiceTest.java`（新檔） |
| 後端測試 | `InventoryServiceTest.java`（新檔） |
| 生產程式碼修復 | `InventoryService.java`（US-002，跨租戶讀取洩漏修復） |
| 生產程式碼修復（視結果而定） | `PurchaseOrderService.java`（US-003，若驗證成立則修復） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-026`，視結果新增 `DEF-027`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 73+ 規劃參考：

1. 其餘：`ReviewService`（14 方法）、`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
