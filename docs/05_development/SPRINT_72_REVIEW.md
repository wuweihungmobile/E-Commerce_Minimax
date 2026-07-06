# Sprint 72 Review / Sprint 72 評審會議

> **Sprint 編號**: Sprint 72
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: ERP 模組（Supplier/StockMovement/PurchaseOrder/Inventory）單元測試從零建立 + 2 項探查發現的跨租戶問題處理

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | ERP 4 個 Service 單元測試從零建立 | 8 | ✅ 完成 |
| US-002 | 修復 `InventoryService.getInventoryBySku` 跨租戶讀取洩漏（已確認） | 2 | ✅ 完成（`DEF-026`） |
| US-003 | 驗證 `PurchaseOrderService.createPurchaseOrder` 疑似跨租戶庫存挪用 | 3 | ✅ 完成（驗證成立並修復，`DEF-027`） |

**13 SP 全數完成**。本 Sprint 開始前先完整探查 ERP 模組範圍（見 `SPRINT_72_PLAN.md`「前置條件確認」），確認與 Sprint 71 同量級可單一 Sprint 涵蓋，經使用者確認範圍與 2 項安全問題的處理方式後才動手實作，未重演 Sprint 66 之前「規劃前未探查、規模超出預期」的狀況。

---

## 2. 交付內容

### 新增測試（US-001）

- **`SupplierServiceTest.java`**（新檔，9 個測試）：涵蓋 `createSupplier`/`getSupplier`/`listSuppliers`/`searchSuppliers`/`updateSupplier` 全部 5 個方法的正常路徑與 `E_7000` 錯誤路徑。
- **`StockMovementServiceTest.java`**（新檔，17 個測試）：涵蓋 `createManualMovement`（含既有 DEF-017 租戶檢查回歸測試、各 `MovementType` 分支、庫存不足 `E_7004`、禁止類型 `E_7005`）與 3 個查詢方法。
- **`InventoryServiceTest.java`**（新檔，6 個測試）：涵蓋 `getInventoryLedger`/`getInventoryBySku`/`getLowStockAlerts` 正常路徑，含 US-002 安全測試。
- **`PurchaseOrderServiceTest.java`**（新檔，18 個測試）：涵蓋 7 個方法的完整狀態機（DRAFT→SUBMITTED→RECEIVED/PARTIALLY_RECEIVED/CANCELLED）、收貨連動庫存異動、`E_7001`/`E_7002`/`E_7007`/`E_7008` 各錯誤路徑，含 US-003 安全測試。
- **四檔合計新增 50 個單元測試**，比照既有 `M16ErpIntegrationTest`（IT-M16-001~306）/`M16ErpE2ETest`（E2E-M16-001~006）業務情境作為預期行為依據。

### 生產程式碼修復（US-002：`DEF-026`）

- **問題**：`InventoryService.getInventoryBySku(skuId)` 呼叫 `InventoryRepository.findBySkuId(skuId)` 未帶入 `tenantId` 過濾，任何登入的 `STORE_OWNER`/`SELLER` 可讀取他租戶 SKU 的庫存數量/儲位/安全庫存/再訂購點。`ErpController.getInventoryDetail` 雖取得 `tenantId` 但僅用於 log，未傳入 service 過濾。
- **紅燈證明**：新增測試 `InventoryServiceTest.getInventoryBySku_crossTenantSku_mustNotLeakOtherTenantData`，斷言「跨租戶查詢不得洩漏他租戶的庫存資料」。**修復前執行此測試確認失敗**（`Tests run: 6, Failures: 1`），實測證實漏洞存在，非僅程式碼推論。
- **修復**：新增 `InventoryRepository.findBySkuIdAndTenantId(UUID skuId, UUID tenantId)`；`InventoryService.getInventoryBySku` 改用此方法查詢，跨租戶查無結果回傳 `null`（維持既有「不存在回傳 `null`」語意，`ErpController` 404 分支不受影響）。
- **轉綠**：修復後重跑，`InventoryServiceTest` 6 個測試全數通過（含正向對照測試：本租戶查詢自己 SKU 正常回傳，避免修復矯枉過正）。

### 生產程式碼修復（US-003：`DEF-027`）

- **問題**：探查階段發現 `PurchaseOrderService.createPurchaseOrder` 接受品項的 `listingId`/`skuId` 未驗證是否屬於當前租戶，與同檔案已注入但標記 `@SuppressWarnings("unused")` 的 `ListingRepository`（從未被使用）形成明顯對照，疑似驗證缺漏。
- **驗證測試（先驗證是否成立，而非直接假設修復）**：新增測試 `PurchaseOrderServiceTest.createPurchaseOrder_crossTenantListing_mustBeRejected`，模擬「攻擊者租戶使用自己合法的 supplier，但品項 `listingId` 指向受害租戶的 listing」情境，斷言應拋出 `BusinessException`。**修復前執行確認失敗**（未拋出任何例外，`createPurchaseOrder` 成功建立），**實測證實漏洞成立**（非「已有其他機制阻擋」的情況）。同時發現正常路徑測試因此對 `listingRepository.findById` 的 mock 產生 `UnnecessaryStubbingException`，交叉佐證生產程式碼確實從未呼叫此依賴。
- **修復**：比照 `StockMovementService.createManualMovement` 既有的 `DEF-017` 修復模式，新增 `validateListingOwnership(listingId, tenantId)`，於 `createPurchaseOrder` 建立每個品項前驗證其 `listing.getTenantId()` 與當前租戶相符，不符拋 `E_1007`；移除 `ListingRepository` 欄位的 `@SuppressWarnings("unused")`（欄位終於被實際使用）。
- **轉綠**：修復後重跑，`PurchaseOrderServiceTest` 18 個測試全數通過（含正常路徑成功建立採購單、跨租戶品項正確拒絕且未呼叫 `purchaseOrderRepository.save`）。

### 文件

- **`SPRINT_72_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有 M16 整合/E2E 測試覆蓋現況、US-001~003 完整 AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-026`、`DEF-027`（皆已完成，直接記入「已完成延後項目」）。
- **`RELEASE_TRACKER.md`**：新增 Sprint 72 列。
- **`RELEASE_NOTES_v2028.07.29-01.md`**（新檔）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 每完成一個測試檔案立即編譯 + 執行該檔驗證通過，才撰寫下一檔（`SupplierServiceTest` → `StockMovementServiceTest` → `InventoryServiceTest`〔含 US-002 紅燈→修復→轉綠〕→ `PurchaseOrderServiceTest`〔含 US-003 紅燈→修復→轉綠〕依序），未累積 |
| `SupplierServiceTest` 單獨執行 | ✅ 9 tests，0 fail |
| `StockMovementServiceTest` 單獨執行 | ✅ 17 tests，0 fail |
| `InventoryServiceTest` 修復前 | 🔴 6 tests，**1 fail**（`getInventoryBySku_crossTenantSku_mustNotLeakOtherTenantData`，實測證實 `DEF-026`） |
| `InventoryServiceTest` 修復後 | ✅ 6 tests，0 fail |
| `PurchaseOrderServiceTest` 修復前 | 🔴 18 tests，**1 failure + 1 error**（`createPurchaseOrder_crossTenantListing_mustBeRejected` 斷言失敗證實 `DEF-027`；`createPurchaseOrder_success_...` 因 `UnnecessaryStubbingException` 交叉佐證 `listingRepository` 確實未被使用） |
| `PurchaseOrderServiceTest` 修復後 | ✅ 18 tests，0 fail |
| ERP 4 檔測試合計 | ✅ 50 tests，0 fail |
| 後端單元回歸（`mvn test`，`test-db-up` 後） | ✅ **690 tests，0 fail** |
| 全量回歸（`mvn verify -Pintegration-test`） | ✅ **BUILD SUCCESS**：單元 690 + 整合（failsafe）342 = **1032 tests，0 fail** |
| `make validate-schema` | ✅ 無漂移（本 Sprint 僅新增 repository 查詢方法，無 entity/migration 變更），EXIT_CODE=0 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint 修改生產程式碼（`InventoryService`/`InventoryRepository`/`PurchaseOrderService`），故執行全量 `mvn verify -Pintegration-test`，非僅 `mvn test` |

---

## 4. 誠實揭露（Rule 12）

1. **首次 `mvn test` 因測試 DB 未啟動出現 3 個無關錯誤**：`SellerDashboardServiceCacheTest`（`@ActiveProfiles("integration-test")` + `@SpringBootTest`，與本 Sprint 變更完全無關）因本機測試 DB（`make test-db-up`）未啟動導致 `ApplicationContext` 載入失敗。執行 `make test-db-up` 後重跑確認為環境前置條件問題，非本 Sprint 迴歸，延續 Sprint 71 已記錄的同類環境陷阱經驗。
2. **US-003 的驗證方式聚焦於 `createPurchaseOrder` 建立時點，未逐一走完整 `receivePurchaseOrder` 收貨流程**：因 `ProductInventory`（實際加庫存的實體）本身不帶 `tenantId` 欄位，唯一可驗證品項租戶歸屬的時點是建立時的 `listingId`（`PurchaseOrderCreateRequest` 的必填欄位）。修復點放在建立時攔截，一旦建立時已擋下跨租戶品項，後續 `receivePurchaseOrder` 便不會再處理到非法品項，故未另外在 `receivePurchaseOrder` 重複建構跨租戶收貨情境測試。
3. **`PurchaseOrderItemRepository` 欄位仍保留 `@SuppressWarnings("unused")`**：探查時發現同檔案有兩個欄位標記未使用（`purchaseOrderItemRepository`、`listingRepository`），本 Sprint 僅處理與 US-003 直接相關的 `listingRepository`（已改為實際使用並移除標記）。`purchaseOrderItemRepository` 是否為死碼、是否需要清理，超出本 Sprint 範圍，未評估亦未處理，留待後續視需要另行盤點。

---

## 5. Demo 重點

- **ERP 模組單元測試從零到 50 個測試**：`SupplierService`/`StockMovementService`/`PurchaseOrderService`/`InventoryService` 首次獲得純 Mockito 隔離的單元測試保護，與既有 M16 整合/E2E 測試（43 個）互補形成完整測試金字塔。
- **2 項安全問題皆以「先寫測試驗證，才動手修復」的紀律處理**：US-002（已知確認的漏洞）與 US-003（探查階段僅是「疑似」的推論）都先透過實際執行測試取得紅燈證據，避免「自行假設是漏洞就動手改」或「自行假設不是漏洞就不驗證」兩種極端，兩案最終都證實漏洞成立並修復。
- **`@SuppressWarnings("unused")` 作為漏洞線索**：US-003 修復前，`PurchaseOrderService` 的 `ListingRepository` 欄位帶著 `@SuppressWarnings("unused")` 標記——這正是「本該用來做租戶驗證、卻從未真正使用」的具體證據，修復後此標記已移除。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
