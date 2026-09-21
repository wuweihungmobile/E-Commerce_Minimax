# Sprint 178 Plan — 商品規格（SKU）管理功能補建（DEF-230）

**Sprint**: Sprint 178
**日期**: 2026-09-21

## 1. 起點

Sprint 177 §9 誠實揭露：「create 有驗證、update 沒有」的契約掃描角度已連續五輪（DEF-225~229），候選集持續收斂，建議下一輪切換到全新角度。經 `AskUserQuestion` 徵詢，使用者選擇「前後端契約漂移全掃」，鎖定尚未被獨立命名掃描過的模組：ERP 庫存/盤點、核心商品/訂房/定價/結帳流程、Analytics/Chat/Settlement/Address。

## 2. 掃描方法與結果

派出 3 個背景唯讀調查 agent 分別掃描三個模組群，主控 session 事後逐一獨立驗證（不直接採信 agent 報告）：

- **Analytics/Chat/Settlement/Address**：未發現契約漂移。
- **核心商品/訂房/定價/結帳流程**：未發現典型契約漂移（僅一項邊緣情況：`confirmOverride` 欄位前端未宣告，但有替代路徑，非阻斷）。
- **ERP 採購單模組**：找到真實缺口——`PurchaseOrderCreateRequest.items` 缺 `skuId`，`listing-options` 選擇器不提供 SKU 選項，`PurchaseOrderService.receivePurchaseOrder` 的 `item.getSkuId() != null` 判斷式永遠不成立，收貨後庫存不會真正入帳。

## 3. 深入查證：根因比原始報告更深

逐行追查確認：**全庫沒有任何程式碼會建立 `ProductSku`**——`grep` 找不到 `ProductSku.builder()`、`productSkuRepository.save()`、對應的 REST 端點、資料庫觸發器、或 migration 種子資料。連帶地 `product_inventory`（PK 即 `sku_id`）也永遠不會有真實資料。

這代表建立在這兩張表之上、跨越多個 Sprint 投入心力的 ERP 庫存子系統——低庫存預警（`DEF-063~066`）、`DEF-050` 三段式原子庫存操作、採購單收貨入庫——在正式環境**全部不可能被真實觸發**。既有測試（`M16ErpIntegrationTest` 等）全靠 `JdbcTemplate` 直接 `INSERT INTO product_skus` 繞過這一步才「看起來」綠燈，是本專案已多次記錄的「測試以固件繞過同一段邏輯」模式的又一實例（同類見 `DEF-145~147` M17 死流程）。

購物車/訂單本身不受影響——`RedisCartService`/`OrderService` 把 `skuId` 當選填處理，一般商城購買流程正常運作，只有 SKU 層級的庫存追蹤與變體（`spec_name`）管理完全是空的。

**PRD 範圍查證**：`docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` 明確把「SKU 多規格管理」列在「Won't Have — Phase 2+ 明確排除」表格（第 14 項），§13 也寫明「Phase 1 不含：SKU 管理」。但同一份 PRD 中，ERP 模組（M16）本身同樣標記 Phase 2+，卻早已在過去上百輪 Sprint 中完整建置。經 `AskUserQuestion` 二次徵詢（先告知不做的選項，再告知 PRD 範圍脈絡重新確認），使用者兩次都選擇「做完整 SKU 管理功能」。

## 4. 修法決策

- **不做完整多規格變體管理系統**（如組合包、規格矩陣），只補齊「建立 SKU」這個唯一缺少的入口，讓既有已建置的庫存管線變得可達。
- SKU 建立時**一併建立 `total_qty=0` 的 `ProductInventory` 列**，之後數量變化一律透過既有的 `ProductInventoryRepository` 原子 UPDATE 方法（採購入庫、盤點、訂單扣帳），不重複實作。
- 不提供硬刪除端點，只提供 `ACTIVE`/`INACTIVE` 狀態切換（比照 `SupplierDto` 既有慣例）——`stock_movements.sku_id` 一旦有異動記錄即無法刪除（FK 無 `ON DELETE CASCADE`）。
- SKU 管理限定 `PRODUCT` 類型 listing（`ROOM` 類型用日期可用性管理，非 SKU 庫存概念）。
- 權限複用既有 `product:read`/`product:update`（管理自己商品的規格視同管理商品本身）。

## 5. 修復內容

**後端**：
- 新增 `SkuDto.java`（`CreateRequest`/`UpdateRequest`/`Response`），`skuCode`/`specName` 的 `@Size` 上限對齊 V1 schema `VARCHAR(50)`/`VARCHAR(100)`。
- 新增 `ProductSkuService.java`：`createSku`/`listSkus`/`updateSku`，租戶擁有權檢查比照 `ProductService.checkListingTenantOwnership`（DEF-041 既有模式）。
- `ProductController.java` 新增 `POST/GET /v2/products/{listingId}/skus`、`PUT /v2/products/{listingId}/skus/{skuId}`。
- `ErpController.listTenantListingsForPurchaseOrder`／`ListingOptionDto` 附上每個商品已建立的 SKU（`skus[]`），供採購單品項選擇器使用。
- 複用既有的 `E_3003`（找不到 SKU）／`E_3005`（SKU 代碼重複）錯誤碼——這兩碼原本已定義在 `ErrorCode` 枚舉且已對應好 HTTP 狀態（404/409），卻從未被任何程式碼拋出過，是另一個「等待這個功能」的既有痕跡。

**前端**：
- 新增 `services/sku.ts`。
- 新增 `components/product/SkuManager.tsx`（規格列表 + 建立表單 + 啟用/停用切換），掛載於商品編輯頁 `dashboard/products/[id]/edit`。
- `services/erp/purchaseOrder.ts` 的 `ListingOption` 新增 `skus[]`，`PurchaseOrderCreateRequest.items` 新增必要的 `skuId`。
- `components/erp/PurchaseOrderForm.tsx`：新增「規格（SKU）」選擇欄位，換商品時重置/若僅一個 SKU 自動帶入；商品尚未建立規格時顯示明確提示並阻擋送出；建立採購單前驗證每個品項都已選規格。

## 6. 過程中修復的兩個 Hibernate 陷阱

實作時端到端整合測試先出現非預期的 500，逐一排查修復（非本輪原始範圍，屬實作正確性的必要修正，如實記錄）：

1. `ProductInventory.id` 透過 `@MapsId` 衍生自 `sku` 關聯物件本身，僅設定 `skuId` 純量欄位不足以讓 Hibernate 產生 ID，會拋 `IdentifierGenerationException`——修法：`.sku(sku)` 與 `.skuId(sku.getId())` 一併設定。
2. `ProductInventory.version`（`@Version`）帶 `@Builder.Default` 預設值 `0L`：Spring Data JPA 對 `@Version` 為非原生型別的實體，`isNew()` 判斷式是「`version == null`」而非「`id == null`」，沿用預設值會讓 `save()` 誤判為既有列而呼叫 `entityManager.merge()`，在 `@MapsId` 關聯的識別子尚未真正持久化時直接拋 `org.hibernate.AssertionFailure: null identifier`——修法：建立新列時明確覆寫 `.version(null)`。

## 7. 測試

- **端到端整合測試**（`SkuManagementIntegrationTest`，2 案例）：刻意不用 raw SQL 塞資料，走真實的「建立 SKU → 建立供應商 → 建立採購單（帶入真實 skuId）→ 送出 → 收貨」HTTP 流程；斷言 `product_inventory.total_qty` 確實從 0 增加到 10（先前恆為 0），以及重複 SKU 代碼回傳 409/`E-3005`。
- **單元測試**（`ProductSkuServiceTest`，8 案例）：建立時一併建立零庫存列、SKU 代碼重複拒絕、非 `PRODUCT` 類型拒絕、跨租戶拒絕（含 `SUPER_ADMIN` 例外）、列表附帶庫存計算、partial update、跨商品竄改防護。
- **Bean Validation 測試**（`SkuDtoValidationTest`，8 案例）：`skuCode` 必填/長度上限、`specName` 長度上限、`priceOverride` 正數限制、`UpdateRequest` 全選填語意。
- **回歸修復**：既有 `M08ReviewStatsIntegrationTest`（`@WebMvcTest(controllers={ProductController.class})` 窄範圍切片）因 `ProductController` 新增建構子相依（`ProductSkuService`）導致 ApplicationContext 載入失敗，補上對應 `@MockBean`，3 案例轉綠。
- 前端：`tsc --noEmit` 零錯誤（過程中抓到一處遺漏 `skuId` 的既有 edit-mode 資料映射，`grep` 未覆蓋到，比照既有教訓「改契約後 tsc 比 grep 可靠」）；`eslint` 零新增錯誤（既有警告不在本輪範圍）；`npm run build` 全站 57 個路由成功建置。

## 8. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1585 個單元測試（+16）+ 484 個整合測試（+2），0 failed**；checkstyle（main+test）**0 違規**。

**未執行 `make validate-e2e` 撰寫新 Playwright spec**：全庫查證確認採購單建立流程先前完全沒有 E2E 覆蓋（`grep` 全庫零命中），本輪以後端端到端整合測試（`SkuManagementIntegrationTest`）作為主要正確性證明；push 前的 `make validate-push`/`validate-release` 守門仍會跑既有 E2E 套件驗證全站未回歸。

## 9. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-230`：狀態直接記為「✅ 已修復（Sprint 178）」，發現與修復同輪完成。

---

## 10. 誠實揭露總結

- 這是本輪自「前後端契約漂移全掃」角度發現的**唯一**真實缺口；Analytics/Chat/Settlement/Address 與核心商品/訂房/定價/結帳流程兩大模組群經獨立驗證後未發現契約漂移，符合「50+ 輪技術債清理後核心流程已大量修復」的背景。
- 本輪修復規模遠超一般契約漂移項目——從「前端漏送一個欄位」的表層症狀，往下追查到「整條資料表鏈路的建立入口從未存在」的結構性缺口，且涉及 PRD 明文排除的功能範圍，因此**兩次**使用 `AskUserQuestion` 徵詢使用者（先確認要做完整功能還是輕量版，再告知 PRD 排除脈絡後重新確認），未逕自擴大範圍。
- 修復過程中新增的 `@Size` 驗證（`skuCode`/`specName`）不屬於本輪掃描角度（Bean Validation 已於 Sprint 173~177 收斂），但為新功能必要的輸入邊界防護，隨手一併補上並附測試，非另開新一輪掃描角度。
- 前後端契約漂移這個掃描角度本身尚未耗盡——ERP 模組僅查了採購單子模組，庫存台帳/低庫存預警/供應商 CRUD 的契約本身是乾淨的（本輪三個背景 agent 之一已確認），但下一輪若要延續此角度，ERP 模組內仍可能有更細的頁面未覆蓋到；核心商品/訂房/定價/結帳流程則相對可信已收斂。
