# Sprint 180 Plan — 全庫「多關聯 ID 只驗證其中一個」IDOR 模式掃描（DEF-237~241）

**Sprint**: Sprint 180
**日期**: 2026-09-22

## 1. 起點

Sprint 179 修復 `DEF-231` 後，`DEFERRED_ITEMS_TRACKER.md` 只剩兩項低優先級「不排入排程」項目。`DEF-231` 本身是本專案已多次記錄的「同一套 IDOR 模式在新程式碼裡重演」的又一實例（同型見 `DEF-017`/`DEF-018`/`DEF-024`/`DEF-027`/`DEF-189`/`DEF-190`）：某個 Service 方法驗證主要資源（如 `listingId`）屬於呼叫者租戶，但請求裡還帶了另一個關聯 ID（如 `skuId`），這個關聯 ID 卻完全沒被驗證是否也屬於同一個租戶/父資源。本輪選定延伸此模式，系統性掃描全庫是否還有其他端點有同型缺口。

## 2. 掃描方法與結果

派出 2 個背景唯讀調查 agent 分別掃描：

- **Agent 1（訂單/訂房/購物車/退貨/物流/評論）**：找到本輪最嚴重的缺陷（`DEF-237`）——`RedisCartService.addItem`/`OrderService.buildProductOrder` 從未驗證購物車/訂單品項的 `skuId` 是否屬於同一個 `listingId`。訂房/退貨/物流查證後排除（皆已有正確的歸屬驗證或不存在第二個需驗證的子資源 ID）；評論模組的既有疑點（`DEF-031`）現況與記錄時一致，使用者先前已決定擱置，維持原狀。
- **Agent 2（客服/CMS/通知/結算/聊天/ERP 其餘部分）**：找到三項缺陷——`NotificationService.sendNotification`（`DEF-238`）、`KnowledgeBaseService`（`DEF-239`）、`PostService`（`DEF-240`）；另發現一項潛伏但目前不可觸發的缺口（`DEF-241`）。客服工單/結算/聊天/ERP 其餘部分查證後排除。

主控 session 對每一項發現都實際讀完整段相關程式碼後才決定是否修復，不直接採信 agent 報告。

## 3. 修復內容

### 3.1 DEF-237：購物車/訂單的 SKU-Listing 關聯驗證缺失（🔴 最高優先級）

**攻擊鏈**：`GET /v2/products/{listingId}/skus` 是刻意設計的公開端點（比照商品詳情本身即為公開資訊），任何登入使用者可查到「他租戶」商品的真實 SKU UUID。`RedisCartService.addItem`（`request.getListingId()`/`request.getSkuId()` 各自獨立查表）與 `OrderService.buildProductOrder`（`cartItem.getListingId()`/`cartItem.getSkuId()` 同樣各自查表）都從未檢查兩者的關聯，攻擊者可用自己的 `listingId` 搭配他租戶的 `skuId` 加入購物車並結帳。`ProductInventoryService.reserveForOrder`/`deductForOrder` 純粹依 `item.getSku().getId()` 做原子扣減/預留，不管這個 SKU 是否真的屬於這筆訂單的租戶——他租戶的真實庫存因此被竄改，且 `stock_movements` 流水帳的 `tenantId` 會誤記為攻擊者租戶。

此路徑比 `DEF-231`（限 ERP 後台採購單）影響範圍更廣：一般 `BUYER`/`GUEST` 角色的日常購物結帳流程即可觸發，不需要任何後台權限。

**修法**：兩處都在取得 `sku` 後驗證 `sku.getProductListingId()` 是否等於品項的 `listingId`。與 `DEF-231` 的拋例外作法不同，這裡比照既有的「`skuId` 找不到」容錯語意（`ProductInventoryService` 本就把 `sku == null` 視為「未啟用庫存追蹤」正常放行）：不屬於此 listing 的 `skuId` 一律視為未選規格，靜默退回無 SKU 狀態，不拋例外、不阻斷結帳——因為這是買家結帳主線，容錯優先於嚴格拒絕；ERP 後台建單則是店家自己的管理操作，拋例外要求重新選擇更合理。`OrderService.buildProductOrder` 端額外加一層縱深防禦：Redis 購物車 TTL 長達 30 天，即使 `addItem` 已修復，修復前已寫入的舊資料仍可能在接下來一個月內流入結帳。

**踩坑記錄**：第一版測試斷言 `response.getItems().get(0).getSkuId()`，但 `OrderItem.skuId` 是 `insertable=false`/`updatable=false` 的影子欄位，純 Mockito mock（無真實持久化）永遠回不填，不論修復與否恆為 `null`，斷言不出行為差異（測試會「假綠燈」通過）。改用 `ArgumentCaptor<Order>` 驗證實際傳入 `orderRepository.save` 的 `OrderItem.getSku()`（關聯物件本身，非影子欄位），比照本測試檔既有處理 `Order.tenantId` 影子欄位的手法，重新驗證後才正確轉紅。

### 3.2 DEF-238：通知發送的租戶邊界與收件人覆寫

`NotificationService.sendNotification` 對目標 `userId` 完全不做租戶範圍檢查；`notification:create` 權限先前授予一般 `STORE_OWNER`/`ADMIN`（非僅 `SUPER_ADMIN`），任一店主可對系統內任何使用者發送通知。更嚴重的是 `request.getRecipient()` 允許呼叫端任意指定收件 email/電話，完全脫鉤於該使用者真實登記的聯絡方式——等於把平台自己可信的通知管線變成內容可控的開放中繼站（釣魚/垃圾訊息）。

**查證**：此端點（`POST /v2/notifications/send`）前端零呼叫點（`grep` 全庫確認），但 `@PreAuthorize` 已授權給一般 `STORE_OWNER`，任何店主可直接呼叫 API 觸發，不需要 UI 存在。

**修法**：
1. 收件人覆寫部分無條件修復（不涉及業務範圍判斷）：一律改用 `getRecipientForChannel(user, channel)` 推導，不再信任呼叫端輸入。
2. 「`STORE_OWNER` 可以通知哪些使用者」屬業務語意不明確（無限制、無既有 UI 可參考意圖），經 `AskUserQuestion` 徵詢使用者，選擇最嚴格方案：`notification:create` 從 `STORE_OWNER`/`ADMIN` 移除，`NotificationController.sendNotification` 的 `@PreAuthorize` 收斂為僅 `hasRole('SUPER_ADMIN')`（比照既有 `broadcastNotification` 端點寫法）。

### 3.3 DEF-239：知識庫文章的分類租戶隔離缺失（含連帶阻斷服務）

`KnowledgeBaseService.createArticle`/`updateArticle` 用不分租戶的 `categoryRepository.findById` 驗證 `categoryId`，與同檔案 `getCategory`/`updateCategory`/`deleteCategory` 皆已正確使用 `findByIdAndTenantId` 形成明顯不一致。連帶後果比典型跨租戶資料洩漏更嚴重：`deleteCategory` 的文章引用檢查（`articleRepository.findByCategoryId`）同樣不分租戶，受害租戶會因攻擊者掛上去的文章而**永久無法刪除自己的分類**——這是跨租戶阻斷服務，且服務全程用 `TenantContext` 取值，沒有 `SUPER_ADMIN` 旁路可以介入修復。

**修法**：`createArticle`/`updateArticle` 改用 `categoryRepository.findByIdAndTenantId`。縱深防禦：新增 `KnowledgeArticleRepository.findByCategoryIdAndTenantId`，`deleteCategory` 的引用檢查一併改用租戶範圍查詢，避免既有髒資料或未來新寫入路徑重蹈覆轍。

### 3.4 DEF-240：CMS 貼文的分類租戶隔離缺失

`PostService.createPost`/`updatePost` 同 `DEF-239` 用不分租戶的 `findById` 驗證 `categoryId`，與同模組的 `PostCategoryService`（`category.getTenant().getId().equals(tenantId)` 檢查）形成同一模組內的不一致慣例。後果較 `DEF-239` 輕：`PostCategoryService.deleteCategory` 的引用檢查本就同時過濾 `tenantId`+`categoryId`（`findByTenantIdAndCategoryId`），不會被跨租戶阻斷；主要影響是分類名稱外洩到本租戶的公開貼文回應。

**修法**：新增 `PostCategoryRepository.findByIdAndTenantId`，`createPost`/`updatePost` 改用此方法。

### 3.5 DEF-241：通知範本全域模板的權限提升缺口（不排入排程）

`NotificationTemplateService.getTemplate`/`updateTemplate`/`deleteTemplate` 對 `template.getTenantId() == null`（全域模板）完全不做租戶比對。查證確認全庫 `createTemplate` 一律強制帶入呼叫者 `tenantId`，沒有任何寫入路徑會產生 `tenantId == null` 的模板記錄，migration 也無 seed 全域模板資料——目前經由應用程式本身沒有可觸發的攻擊路徑，屬潛伏設計缺陷，記錄供未來若新增全域範本管理功能時優先參考，本輪不修復。

## 4. 測試

- **後端單元測試**：`RedisCartServiceTest`/`OrderServiceTest`/`NotificationServiceTest`/`KnowledgeBaseServiceTest`/`PostServiceTest`/`RolePermissionMappingTest` 各新增 1 個紅燈先行案例（`RolePermissionMappingTest` 為既有兩個案例改斷言方向，非新增）。既有測試中因新增租戶範圍查詢/`saveAndFlush` 等呼叫而需同步更新 mock stub 的案例（`RedisCartServiceTest`/`KnowledgeBaseServiceTest`/`PostServiceTest` 各數個），行為本身不變、僅方法呼叫簽章改變。
- **紅燈先行踩坑**（見 §3.1）：`OrderService` 相關測試因影子欄位無法在純 Mockito mock 情境下觀察到差異，改用 `ArgumentCaptor` 驗證實際傳入 repository 的關聯物件本身，而非只斷言回應 DTO 的欄位值。
- **紅燈先行的技術細節**：DEF-237/238/239/240 皆以 `git stash` 隔離對應主程式碼檔案、保留新增測試，證實修復前後行為差異；因部分修復同時涉及新增的 repository 方法（純加法，不影響既有方法），stash 範圍限定在 Service/Controller/RolePermissionMapping 等「消費」新方法的檔案，不含 repository 介面本身，避免 stash 造成編譯失敗而非真正的行為紅燈（過程中一度誤把 repository 介面也 stash 進去，`mvn -o clean test-compile` 直接編譯失敗而非測試失敗，重新調整 stash 範圍後才得到正確的紅燈）。
- **前端**：本輪四項修復皆為後端邏輯/權限修正，不改變任何 API 契約（合法呼叫端的行為不受影響），未涉及前端程式碼異動，故未執行 `tsc`/`eslint`/`npm run build`。
- **未新增整合測試**：四項修復皆有對應的單元測試紅燈先行覆蓋，判斷已足夠證明修復有效；`make validate-push`/`validate-release` 既有整合測試+E2E 套件仍會驗證全站未回歸。

## 5. 過程中的環境踩坑

第一次執行 `mvn -o clean test-compile` 驗證紅燈時，把新增的 repository 方法（`ProductSkuRepository`/`KnowledgeArticleRepository`/`PostCategoryRepository` 的新增方法屬純加法）也一併 `git stash`，導致測試檔引用的 mock 方法在介面上不存在，直接編譯失敗（而非預期的行為紅燈）。調整 stash 範圍為只隔離消費新方法的 Service/Controller/RolePermissionMapping 檔案後，重新編譯通過並得到正確的紅燈結果。

## 6. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1596 個單元測試（+5）+ 484 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-237`/`DEF-238`/`DEF-239`/`DEF-240`：狀態直接記為「✅ 已修復（Sprint 180）」，發現與修復同輪完成。
- 新增 `DEF-241`：⚠️ 已記錄，不排入排程（潛伏但目前不可觸發）。

## 8. 誠實揭露總結

- `DEF-237` 再次證實「同一套 IDOR 模式在新程式碼裡重演」的判準有效——`DEF-231` 修復時只查了 ERP 採購單這一條路徑，沒有回頭檢查購物車/訂單這條更早、更常用的既有路徑是否有同型缺口，直到本輪專門延伸此角度才發現。這提示未來修一個 IDOR 缺陷時，應該同時檢查「同一個 SKU/子資源概念」在其他呼叫路徑（尤其是更早、更核心的路徑）是否也有同型問題，而不只是查同一個 Service 內部。
- `DEF-238` 的範圍決策（收斂為僅限 `SUPER_ADMIN`）是保守選擇：這個端點目前零前端呼叫點，沒有真實業務使用場景佐證「店主通知任意使用者」的需求存在，若未來真的需要此功能，應該由產品需求驅動重新設計（例如限定通知自己的客戶或員工），而非現在盲目猜測一個可能錯誤的業務規則。
- `DEF-239`/`DEF-240` 是同一類缺陷在兩個不同模組的重複發生，後果嚴重度因下游查詢是否也做了租戶過濾而有明顯落差（`DEF-239` 的阻斷服務 vs `DEF-240` 單純資料洩漏）——這提示「檢查建立/更新端有沒有驗證關聯資源租戶」不能只看寫入端本身，還要往下追查所有讀取這個關聯的下游查詢是否也做了對應過濾，否則即使寫入端修好，既有的下游查詢邏輯仍可能因為錯誤資料而產生非預期後果。
- `DEFERRED_ITEMS_TRACKER.md` 目前僅剩 `DEF-235`/`DEF-236`/`DEF-241` 三項低優先級「不排入排程」項目，Sprint 181 開工時仍需自選新掃描角度。「同一請求內多個關聯 ID 只驗證其中一個」這個角度經兩輪（`DEF-231` + 本輪）掃描後，本輪兩個背景 agent 已覆蓋訂單/訂房/購物車/退貨/物流/評論/客服/CMS/通知/結算/聊天/ERP 共 12 個模組，候選集可能已大幅收斂，下一輪若要延續此角度建議聚焦尚未系統性檢查過的角落（如 Analytics、Address、Return 的更細部欄位）。
