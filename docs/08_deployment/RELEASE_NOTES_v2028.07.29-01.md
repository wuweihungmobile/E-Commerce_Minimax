# Release Notes - v2028.07.29-01 (Sprint 72)

**發布日期**: 2028-07-29（規劃）／實作完成 2026-07-06
**發布類型**: 🧪 測試強化 + 🔴 安全修復（P0/P1；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 72（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 72 主題：**ERP 模組（Supplier/StockMovement/PurchaseOrder/Inventory）單元測試從零建立**，並處理範圍探查階段發現的 2 項跨租戶安全問題。依 `SPRINT_71_PLAN.md`/`SPRINT_66_PLAN.md` 排程建議執行，動手前先完整探查範圍並經使用者確認，確認與過去單一 Sprint（66/67/69/71，皆 8 SP）同量級，未拆分為多 Sprint。

---

## 🧪 測試強化（US-001）

- **ERP 模組單元測試從零建立**：`SupplierService`（5 方法）、`StockMovementService`（4 方法）、`PurchaseOrderService`（7 方法）、`InventoryService`（3 方法），共 19 個 public 方法、881 行程式碼，先前完全沒有單元測試目錄。
- 新增 4 個測試檔案，合計 **50 個單元測試**：`SupplierServiceTest.java`（9）、`StockMovementServiceTest.java`（17）、`InventoryServiceTest.java`（6）、`PurchaseOrderServiceTest.java`（18）。
- 涵蓋正常路徑、狀態機轉換（`PurchaseOrder` 的 DRAFT→SUBMITTED→RECEIVED/PARTIALLY_RECEIVED/CANCELLED）、收貨連動庫存異動、既有 `DEF-017` 租戶檢查回歸、各項錯誤路徑（`E_7000`/`E_7001`/`E_7002`/`E_7004`/`E_7005`/`E_7007`/`E_7008`）。
- 以既有 `M16ErpIntegrationTest`（IT-M16-001~306，37 個測試）與 `M16ErpE2ETest`（E2E-M16-001~006，6 個測試）的業務情境作為預期行為依據，形成單元/整合/E2E 三層完整測試金字塔。

## 🔴 安全修復 P0：`DEF-026`

- **`InventoryService.getInventoryBySku` 跨租戶讀取洩漏**：
  - **修復前**：呼叫 `InventoryRepository.findBySkuId(skuId)` 未帶入 `tenantId` 過濾，任何登入的 `STORE_OWNER`/`SELLER` 可讀取他租戶 SKU 的庫存數量/儲位/安全庫存/再訂購點等商業機密資料。`ErpController.getInventoryDetail` 雖取得 `tenantId` 但僅用於 log，未實際過濾。
  - **紅燈證明**：新增測試斷言「跨租戶查詢不得洩漏他租戶庫存資料」，修復前執行**確認失敗**，實測證實漏洞存在。
  - **修復後**：新增 `InventoryRepository.findBySkuIdAndTenantId`，`getInventoryBySku` 改用租戶過濾查詢，跨租戶查無結果回傳 `null`（維持既有語意，`ErpController` 404 分支不受影響）。

## 🔴 安全修復 P1：`DEF-027`

- **`PurchaseOrderService.createPurchaseOrder` 品項未驗證 listing 租戶歸屬**：
  - **背景**：範圍探查時發現本方法未驗證品項 `listingId`/`skuId` 是否屬於當前租戶，與同檔案已注入但標記 `@SuppressWarnings("unused")` 的 `ListingRepository`（從未被使用）形成明顯對照，判斷為疑似漏洞，決定先寫測試驗證是否成立。
  - **紅燈證明**：模擬「攻擊者租戶用自己合法的 supplier，品項 `listingId` 指向受害租戶 listing」情境，修復前執行**確認失敗**（未拋出例外、成功建立採購單），**實測證實漏洞成立**。
  - **修復後**：比照 `StockMovementService.createManualMovement` 既有 `DEF-017` 修復模式，新增 `validateListingOwnership`，於建立每個品項前驗證 `listing.getTenantId()` 與當前租戶相符，不符拋 `E_1007`；移除 `ListingRepository` 欄位的 `@SuppressWarnings("unused")`。

## 測試 / 驗證 ✅

- **ERP 4 檔測試合計**：50 tests，0 fail。
- **US-002/US-003 紅綠燈流程**：`InventoryServiceTest`（修復前 6 tests 1 fail → 修復後 6 tests 0 fail）、`PurchaseOrderServiceTest`（修復前 18 tests 1 failure + 1 error → 修復後 18 tests 0 fail）。
- **後端單元回歸**（`mvn test`）：**690 tests，0 fail**。
- **後端全量回歸**（`mvn verify -Pintegration-test`）：**BUILD SUCCESS**，單元 690 + 整合（failsafe）342 = **1032 tests，0 fail**。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 僅新增 repository 查詢方法，無 entity/migration 變更）。
- **開發-編譯-測試循環**：每完成一個測試檔案立即編譯 + 驗證通過才撰寫下一檔；US-002/US-003 額外執行「紅燈確認 → 修復 → 轉綠確認」雙重驗證節奏。

## 技術決策 / 已知限制 ⚠️

- **US-003 修復點放在建立時點（`createPurchaseOrder`），非收貨時點（`receivePurchaseOrder`）**：因實際加庫存的 `ProductInventory` 實體本身不帶 `tenantId` 欄位，唯一可驗證租戶歸屬的時點是建立採購單時的必填 `listingId`。建立時已攔截跨租戶品項，後續收貨流程便不會處理到非法品項。
- **`PurchaseOrderService.purchaseOrderItemRepository` 欄位仍保留 `@SuppressWarnings("unused")`**：本 Sprint 僅處理與 `DEF-027` 直接相關的 `listingRepository`，另一個標記未使用的欄位是否為死碼超出範圍，未評估。
- **無前端變動**：本 Sprint 純後端 Service 層測試與修復，ERP 前台/後台管理頁面不在範圍內。

## 資料庫遷移 🗄️

- 無（schema-free；純 Java service 層邏輯 + 新增 repository 查詢方法）。

## 內含 Commit（Sprint 72）

| US / 項目 | 說明 |
|----------|------|
| Sprint 72 Plan | ERP 模組測試強化 + 2 項安全問題處理計劃（3 US / 13 SP）|
| US-001 | ERP 4 個 Service 單元測試從零建立（50 個測試）|
| US-002 | `DEF-026` 修復：`InventoryService.getInventoryBySku` 跨租戶讀取洩漏 |
| US-003 | `DEF-027` 修復：`PurchaseOrderService.createPurchaseOrder` 跨租戶庫存挪用驗證與修復 |
| Sprint 72 收尾 | Review / Retro / Release Notes + trackers |

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
