# Sprint 189 Plan — 跨 listingType 統一端點的型別/權限錯配審查（DEF-263）

**Sprint**: Sprint 189
**日期**: 2026-09-23

## 1. 起點

延伸 Sprint 188「背景排程/非 REST 呼叫路徑審查」的主題精神——本輪改查另一類容易被忽略的角落：**跨 listingType 的「統一」Dashboard 端點**是否存在型別與權限錯配。`PRODUCT`/`ROOM` 兩種 listing 類型在本專案分別有獨立的權限碼（`product:create`/`room:create` 等），但部分端點為求共用單一 API，把兩種權限以 `or` 合併在類別級 `@PreAuthorize`。這類「方便共用」的寫法若下游沒有針對實際操作類型逐一驗證對應權限，就會出現「持有其中一種權限即可越權操作另一種類型」的錯配。

自選掃描範圍：先前多輪未特別檢視過的 4 個 Dashboard 端點——`AnalyticsController`、`SellerDashboardController`、`DashboardPricingController`、`DashboardListingController`（皆為體積小、可由主控 session 直接逐檔審查的檔案，未派背景 agent）。

## 2. 調查方法與結果

- `AnalyticsController`/`AnalyticsService`：5 個方法皆一致透過 `TenantContext.getCurrentTenant()` 取得租戶範圍，`@PreAuthorize("hasAuthority('dashboard:read')")` 為單一權限碼，無跨型別問題。**確認無缺陷**。
- `SellerDashboardController`：3 個端點皆 `@PreAuthorize("hasRole('SELLER')")`，單一角色檢查，且方法本身不分流型別。**確認無缺陷**。
- `DashboardPricingController.overridePrice`：`@PreAuthorize("hasAuthority('room:update')")` 為單一權限碼（非 OR），`isSuperAdmin` 分支已在先前 Sprint 建立。**確認無缺陷**。
- `DashboardListingController.createListing`：**發現 🟠 `DEF-263`**——類別級 `@PreAuthorize("hasAuthority('product:create') or hasAuthority('room:create')")` 是 OR 條件，但方法內部依請求體 `listingType`（完全由呼叫者宣告，無需對應既有資源）分流呼叫 `ProductService.createProductFromDashboard`/`RoomService.createRoomFromDashboard`，兩者皆是「僅供本 Controller 呼叫、完全信任呼叫端已做好權限檢查」的內部方法，不會重新驗證權限。對照它們各自的單一用途端點——`ProductController.createProduct` 只要求 `product:create`、`RoomController.createRoom` 只要求 `room:create`——`DashboardListingController` 這個「統一」版本形成明顯不對稱：僅有 `room:create`（無 `product:create`）的 `HOST` 角色，可呼叫本端點並宣告 `listingType=PRODUCT`，繞過角色權限模型建立商品 listing（連帶消耗 `RETAIL_ENABLED` feature toggle 與 `MAX_PRODUCTS` 配額，這些原本是 `product:create` 持有者才該碰到的業務規則）；反之僅有 `product:create` 的 `SELLER` 角色可宣告 `listingType=ROOM` 建立房源 listing。

### 2.1 同型查證：`PricingController`/`ReviewController`/`ListingController` 的類似 OR 寫法

`grep` 全庫發現另有 8 處 `room:*` OR `product:*` 權限寫法（`PricingController` 5 處、`ReviewController` 3 處、`ListingController` 4 處讀取端點）。逐一核對後判定**性質與 `DEF-263` 不同**，不構成同型漏洞：

- `ListingController` 的 4 處皆為 `*:read`，讀取兩種類型皆為低風險設計選擇（公開瀏覽場景常見）。
- `ReviewController`/`PricingController` 的 update/delete/read 端點皆操作**既有資源**（`ruleId`/`reviewId` 等路徑變數），實際型別由資料庫查出而非呼叫者宣告，不存在「憑空宣告型別」的攻擊面。
- 唯一形態上與 `DEF-263` 相近的是 `PricingController.createRule`（同為「建立」動作，`roomListingId`/`listingId` 由請求體指定），但其下游 `PricingService.createRule` 已呼叫 `checkListingTenantOwnership`（`DEF-242` 修復）限定只能對**呼叫者自己租戶**的既有 listing 建立規則。進一步追查 `SELLER`/`HOST` 角色的建立管道：兩者皆僅能透過各自單一用途端點（`ProductController`/`RoomController`，各自只要求對應權限）在自己租戶下建立 listing；`STORE_STAFF` 邀請流程固定指派 `STORE_STAFF` 角色（無任一 create 權限）；`STORE_OWNER`/`ADMIN`/`SUPER_ADMIN` 本就同時持有兩種權限。也就是說，在 `DEF-263` 修復之前，一個 `SELLER`/`HOST` 帳號名下的租戶要出現「型別與角色不符」的 listing，唯一管道正是 `DEF-263` 本身；`DEF-263` 修復後，這個管道已被堵住，`PricingController.createRule` 的 OR 寫法因此不構成可獨立利用的新風險。查證後判定**不排入排程**（非草率略過，已交叉核對角色/租戶/邀請流程三個面向）。

## 3. 修復範圍與實作

`DashboardListingController`：

- 在 `PRODUCT`/`ROOM` 分支開頭各自新增 `requireAuthority("product:create")`/`requireAuthority("room:create")` 呼叫。
- 新增私有方法 `requireAuthority(String authority)`：透過 `SecurityContextHolder.getContext().getAuthentication()` 讀取當前使用者的 `GrantedAuthority` 集合，比對是否含有指定權限碼；不符時拋 `org.springframework.security.access.AccessDeniedException`（比照本專案 `OrderService`/`BookingService`/`PaymentService`/`PaymentStateService` 既有讀取 `SecurityContextHolder` 的慣例寫法，非新引入的模式）。
- `AccessDeniedException` 已有既有的 `GlobalExceptionHandler.handleAccessDeniedException` 統一處理（回 403、`ErrorCode.E_1007`），與 `@PreAuthorize` 檢查失敗時的行為完全一致，前端不需區分兩種 403 的來源。

未改動 `ProductService.createProductFromDashboard`/`RoomService.createRoomFromDashboard` 本身——它們是僅供本 Controller 呼叫的內部方法，維持「信任呼叫端」的既有設計，修法收斂在唯一入口 Controller 層即可，避免多處重複檢查。

## 4. 測試

新增 `DashboardListingControllerAuthorizationIntegrationTest`（此 Controller 先前完全沒有任何測試檔案覆蓋），採用與 `M12PricingProductIntegrationTest` 相同的 `@SpringBootTest`+`@AutoConfigureMockMvc`+`@WithErpSecurity` 慣例（`@WithErpSecurity` 可同時設定 `UserPrincipal` 與 `TenantContext`，並支援自訂 `authorities()`），`@MockBean` 隔離 `ProductService`/`RoomService`：

- IT-DEF263-01：僅有 `room:create` 的使用者建立 `PRODUCT` listing → 403
- IT-DEF263-02：僅有 `product:create` 的使用者建立 `ROOM` listing → 403
- IT-DEF263-03：僅有 `product:create` 的使用者建立 `PRODUCT` listing → 200（既有合法路徑不可回歸）
- IT-DEF263-04：僅有 `room:create` 的使用者建立 `ROOM` listing → 200（既有合法路徑不可回歸）

**紅燈先行（修復前對未修改的程式碼實際執行，非事後回想）**：4 案例對修復前的程式碼執行，IT-DEF263-01/02 如期失敗（`Status expected:<403> but was:<200>`），IT-DEF263-03/04 通過，證實缺陷存在且僅影響越權路徑、既有合法路徑本來就正常。修復後 4 案例全數通過。

## 5. 驗證結果

`mvn -o clean verify`（真實 postgres/redis）：詳見下方版本歷史記錄的測試統計。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-263`：✅ 已修復（Sprint 189）。

## 7. 誠實揭露總結

- 本輪未派出背景唯讀 agent——延續 Sprint 188 的判斷，範圍明確限定在 4 個體積小的 Dashboard 端點檔案，直接讀碼比協調 agent 更有效率。
- `AnalyticsController`/`SellerDashboardController`/`DashboardPricingController` 三者審查後**確認無缺陷**，是本輪負向結果。
- §2.1 的同型查證特別交叉核對了角色/租戶/邀請流程三個面向（而非只看程式碼表面相似），確認 `PricingController.createRule` 等 8 處類似 OR 寫法在目前的角色權限模型下不構成可獨立利用的新風險，因此不排入排程；若未來角色權限模型變更（例如允許同一租戶混合經營 PRODUCT/ROOM 兩種業務、或開放更細緻的 staff 角色指派 `product:create`/`room:create` 其中之一），這個判斷需要重新檢視。
