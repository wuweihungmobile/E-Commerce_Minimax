# Sprint 179 Plan — Sprint 178 新增 SKU 功能自我審查（DEF-231~234）

**Sprint**: Sprint 179
**日期**: 2026-09-22

## 1. 起點

Sprint 178 完成後，`DEFERRED_ITEMS_TRACKER.md` 已無待排程項目。Sprint 178 §10 誠實揭露「前後端契約漂移這個掃描角度本身尚未耗盡——ERP 模組僅查了採購單子模組」，且 Sprint 178 剛新增的 SKU 管理功能是全新程式碼，尚未被納入任何一輪既有的系統性掃描（契約漂移全掃、Sprint 136 併發競態全掃）。本輪選擇對 Sprint 178 自己的產出做自我審查：派出 3 個背景唯讀調查 agent，分別掃描（1）SKU 功能本身的邊界情況（併發、租戶隔離、驗證、刪除流程、前後端契約）、（2）ERP 模組尚未系統性檢查過的頁面、（3）新 SKU 建立路徑的併發安全性，延伸 Sprint 136 建立的方法論。

## 2. 掃描結果

三個 agent 全數完成，主控 session 逐一核實（不直接採信報告）：

- **Agent 1（併發安全）**：`ProductSkuService.createSku` 的 SKU 代碼重複檢查與寫入之間有 check-then-act 競態，DB 有 UNIQUE 約束擋底但應用層未接住 `DataIntegrityViolationException`（→ `DEF-232`）；另發現 `updateSku` 無 `@Version` 的 lost-update 風險（→ `DEF-235`，未修復）。
- **Agent 2（SKU 邊界情況）**：發現最嚴重的一項——`PurchaseOrderService.createPurchaseOrder` 未驗證 `skuId` 是否屬於同品項已驗證過的 `listingId`（→ `DEF-231`）；獨立交叉驗證出與 Agent 1 相同的併發競態（`DEF-232`）；發現 `status` 欄位無驗證（→ `DEF-233`）。
- **Agent 3（ERP 契約漂移）**：發現採購單「提交/取消/收貨」的前端 handler 函式已完整實作但從未接上 UI（→ `DEF-234`）；另發現 ERP 模組導覽入口缺失、庫存明細頁不可達（→ `DEF-236`，未修復，屬導覽可達性而非契約錯誤）。

## 3. 範圍決策

`DEF-231`/`DEF-232`/`DEF-233` 屬安全性/正確性缺陷，直接修復，不需徵詢範圍。`DEF-234`（採購單 UI 入口缺失）性質上是獨立的功能完整性問題，經 `AskUserQuestion` 徵詢使用者是否一併納入本輪（修法本身不大：接上既有 handler + 加收貨頁連結），使用者選擇「一併納入本輪修復」。

`DEF-235`（`updateSku` 缺 `@Version`）與 `DEF-236`（ERP 導覽可達性）判斷為低優先級，僅登記不修復：前者與既有 `read-modify-write-race-playbook` 記錄的全庫模式一致（59 個實體僅 2 個帶 `@Version`），影響範圍限於低頻異動欄位；後者功能本身可用（手動輸入網址即可），非阻斷性缺陷。

## 4. 修復內容

### 4.1 DEF-231：跨租戶庫存竄改（IDOR，🔴 最高優先級）

**攻擊鏈還原**：Sprint 178 新增的 `GET /v2/products/{listingId}/skus` 刻意設計為公開讀取（比照商品詳情本身即為公開資訊，`product:read` 連 GUEST 角色都有）。攻擊者可對任意「他租戶」`listingId` 查到真實 SKU UUID；再用「自己」合法的 `listingId`（通過 `validateListingOwnership`）搭配該 SKU UUID 建立採購單並送出、收貨；`receivePurchaseOrder` → `createInboundMovement` → `ProductInventoryRepository.increaseTotalQty` 是不分租戶的原生 UPDATE，直接把他租戶 SKU 的 `total_qty` 加上攻擊者指定的數量。

**修法**：新增 `ProductSkuRepository.existsByIdAndProductListingId`；`PurchaseOrderService.createPurchaseOrder` 在品項迴圈內、`skuId` 非 null 時呼叫新增的 `validateSkuOwnership`，驗證其屬於同品項已驗證過的 `listingId`。不符時回應 `E_3003`「SKU not found」——比照既有 `ProductSkuService.updateSku` 的既有作法，不區分「真的不存在」與「屬於別的 listing」，避免藉錯誤訊息差異洩漏他租戶 SKU 是否存在。

**紅燈先行且實際執行**：`git stash` 暫存修復後的 `PurchaseOrderService.java`，對還原後的舊程式碼跑新增的 `createPurchaseOrder_skuNotBelongingToListing_mustBeRejected`，實際結果是完全不攔截、直接往下跑到 `log.info(..., saved.getId(), ...)` 因 `save()` 未 mock（測試預期會在此之前拋例外）而 `NullPointerException`——證實修復前這個檢查根本不存在，而非「存在但寬鬆」。`git stash pop` 還原修復後重新測試，轉為預期的 `E_3003`。

### 4.2 DEF-232：SKU 代碼併發建立競態

`existsBySkuCode` 檢查與 `save()` 之間的 TOCTOU 視窗：兩個併發請求都可能在對方 commit 前通過檢查。`product_skus.sku_code` 有 DB 層 `UNIQUE` 約束擋底（資料本身不會重複），但先前未捕捉 `DataIntegrityViolationException`，落入全域 `handleGenericException` 回應無語意的 500，而非設計中的 409/`E-3005`。

**修法**：比照 Sprint 137 `DEF-141` 既有模式（`TenantService` 等已用過的 catch-and-translate），`save()` 改為 `saveAndFlush()` 並包 `try/catch(DataIntegrityViolationException)`，轉譯為 `BusinessException(E_3005)`。

**紅燈先行**：`ProductSkuServiceTest` 新增 `createSku_concurrentDuplicateSkuCode_racesPastCheckThenActWindow_throwsConflictNot500`，mock `saveAndFlush` 拋出 `DataIntegrityViolationException`。`git stash` 還原後（舊碼呼叫 `save()`，未被 mock，回傳 null）直接 NPE，證實修復前無此防護。

### 4.3 DEF-233：SKU status 欄位缺驗證

`SkuDto.UpdateRequest.status` 無任何驗證，`ProductSku.status` 為純 `String`（非 Java enum，DB 亦無 `CHECK` 約束），可寫入任意字串。實際影響範圍評估：目前沒有任何後端邏輯依賴此欄位值判斷分支，前端顯示邏輯對非 `ACTIVE` 值一律顯示「已停用」，不會崩潰——屬資料完整性缺口而非功能性當機。

**修法**：比照 `SupplierService.updateSupplier` 既有的狀態白名單驗證模式，新增 `ErrorCode.E_3008`（422 UNPROCESSABLE_ENTITY，同組 `E_3001`/`E_7010`），`ProductSkuService.updateSku` 對非 `ACTIVE`/`INACTIVE`（大小寫不拘，正規化為大寫寫入）的值一律拒絕。同步更新 `GlobalExceptionHandlerTest` 窮舉期望表——`DEF-223`（Sprint 171）建立的完全窮舉 `switch` 機制首次因新增 `ErrorCode` 而發揮編譯期防護作用。

**紅燈先行**：`ProductSkuServiceTest` 新增 `updateSku_invalidStatus_throwsUnprocessable`（帶入 `"FOOBAR"`）與 `updateSku_lowercaseStatus_normalizesToUppercase`（帶入 `"inactive"` 驗證正規化）。`git stash` 還原後前者不拋例外直接寫入未驗證字串、後者維持小寫未正規化，兩者皆證實缺口存在。

### 4.4 DEF-234：採購單提交/取消/收貨 UI 入口缺失

`PurchaseOrderForm.tsx` 的 `handleSubmit`/`handleCancel` 函式邏輯完整，但 `view` 模式的 `CardFooter` 只有「返回列表」一顆按鈕，且全站沒有任何連結指向收貨確認頁 `/dashboard/erp/purchase-orders/[id]/receive`。實際後果：採購單建立後永遠停留在 `DRAFT`，先前 Sprint 71/76/95/127（`DEF-070`/`DEF-071`/`DEF-076~079`）修好的收貨流程完全無法從網頁觸達，唯一能推進狀態機的方式是繞過前端直接呼叫 API。

**修法**：狀態轉換條件鏡射後端 `PurchaseOrder.canSubmit()`/`canCancel()`/`canReceive()`（`domain/model/inventory/PurchaseOrder.java`）：

- `canSubmit`：`orderStatus === 'DRAFT'`
- `canCancel`：`DRAFT`/`SUBMITTED`/`PENDING_APPROVAL`/`APPROVED`
- `canReceive`：`SUBMITTED`/`PARTIALLY_RECEIVED`/`APPROVED`

`view` 模式 `CardFooter` 依這三個條件顯示「提交訂單」按鈕（呼叫既有 `handleSubmit`）、「前往收貨」連結（導向收貨頁）、「取消訂單」按鈕（呼叫既有 `handleCancel`）。未新增任何後端邏輯，純粹是把已存在的函式接上 UI。

## 5. 測試

- **後端單元測試**：`PurchaseOrderServiceTest` 新增 2 案例（`skuNotBelongingToListing_mustBeRejected`、`nullSkuId_skipsSkuOwnershipCheck`，確認 `skuId` 為 null 時不誤擋既有「尚未選規格」的合法情境）；`ProductSkuServiceTest` 新增 3 案例（併發競態、無效 status、小寫 status 正規化）；`GlobalExceptionHandlerTest` 窮舉期望表同步新增 `E_3008`（該測試對 `ErrorCode.values()` 的完整覆蓋斷言確保不會漏配）。三支測試檔既有案例中，3 個因新增 `validateSkuOwnership`/`saveAndFlush` 呼叫而需同步補上對應 mock stub（不影響其原本驗證的行為）。
- **紅燈先行**：DEF-231/232/233 皆以 `git stash` 隔離對應 `main` 原始碼檔案、保留新增測試，證實修復前後行為差異後 `git stash pop` 還原。
- **前端**：`tsc --noEmit` 0 錯誤；`eslint` 0 新增錯誤（`handleSubmit`/`handleCancel` 先前的 `no-unused-vars` 警告因接上按鈕而自然消失，其餘警告皆為既有、不在本輪範圍）；`npm run build` 全站成功建置。
- **未新增 e2e spec**：沿用 Sprint 178 已確立的判準——全庫查證 `erp`/`purchase-order` 相關 `*.spec.ts` 零覆蓋，`DEF-234` 屬既有無測試基礎設施的純 UI 接線；push 前 `make validate-push`/`validate-release` 既有 E2E 套件仍會驗證全站未回歸。

## 6. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1591 個單元測試（+6）+ 484 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-231`/`DEF-232`/`DEF-233`/`DEF-234`：狀態直接記為「✅ 已修復（Sprint 179）」，發現與修復同輪完成。
- 新增 `DEF-235`（`updateSku` 缺 `@Version`）/`DEF-236`（ERP 導覽可達性）：⚠️ 已記錄，不排入排程。
- **一併回填缺漏**：發現 Sprint 178 完成時未同步更新版本歷史區塊（「文件版本」停在 v2.67/Sprint 177），本輪開工核對時發現並回填為 v2.68，新版本設為 v2.69。

## 8. 誠實揭露總結

- 這是本專案首次對「上一輪自己剛新增的功能」做專門的自我審查掃描，而非延續既有的橫向契約漂移/併發全掃角度。結果證實這個角度有效——4 個真實缺陷（含 1 個嚴重的跨租戶安全漏洞）在 Sprint 178 驗證時完全沒有被發現，因為 Sprint 178 的測試（`SkuManagementIntegrationTest`）驗證的是「功能本身正確運作」的正向路徑，沒有涵蓋惡意輸入或併發情境。
- `DEF-231` 是本專案已多次記錄的「同一套 IDOR 模式在新程式碼裡重演」的又一實例（同型見 `DEF-017`/`DEF-018`/`DEF-024`/`DEF-027`/`DEF-189`/`DEF-190`）——即使 `PurchaseOrderService.createPurchaseOrder` 的既有註解明確寫著「比照 `StockMovementService` 的 `DEF-017` 模式」，實作時仍只抄了註解、沒有真正複製對 `skuId` 的驗證邏輯，值得未來新增品項/子資源型欄位時作為檢查清單提醒。
- `DEF-235`/`DEF-236` 未修復，留待未來評估：前者若要系統性處理，適合併入下一輪併發全掃（類似 Sprint 136 規模）而非零星修補；後者是單純的 UI/UX 導覽改善，不影響功能正確性，優先級低於本輪聚焦的安全性/正確性/完整性三項。
- `DEFERRED_ITEMS_TRACKER.md` 目前僅剩 `DEF-235`/`DEF-236` 兩項低優先級「不排入排程」項目，Sprint 180 開工時仍需自選新掃描角度。
