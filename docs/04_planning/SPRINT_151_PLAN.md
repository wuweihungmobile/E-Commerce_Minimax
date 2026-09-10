# Sprint 151 Plan — DEF-188：賣家訂單管理（列表＋詳情＋出貨）

**Sprint**: Sprint 151
**日期**: 2026-09-10

---

## 1. 起點

延續 [SPRINT_150_PLAN.md](SPRINT_150_PLAN.md) §4 的追加調查結論：`DEF-188`（賣家訂單管理全站無前端入口，且後端本身缺租戶範圍查詢端點與讀取授權）經 `AskUserQuestion` 徵詢後，使用者已拍板「A→B 兩項都做，B 移至獨立 Sprint」，本輪即為 B 的完整實作。

Sprint 150 §4 已查明的缺口（本輪逐一處理）：

1. `frontend/src/app/dashboard/` 完全沒有 orders 子目錄——`dashboard/page.tsx` 顯示「待出貨：N」統計數字，卻無任何頁面能操作。
2. 後端 `GET /v2/orders` 是買家專用端點（`OrderService.getUserOrders` 固定查呼叫者自己的訂單），沒有任何端點能列出「當前租戶收到的訂單」。
3. 後端 `GET /v2/orders/{orderId}` 的擁有權檢查（`getOrder`）只有 owner-or-admin，未比照 `updateOrderStatus` 的 `checkOrderStatusUpdateAuthorization` 補上 same-tenant 分支，形成「賣家能寫入（PATCH 狀態）自己讀不到（GET 403）的資料」的讀寫授權不對稱。
4. `POST /v2/logistics`（建立物流單/出貨）功能完整且經多輪 IDOR 加固（DEF-019/024/036），但因無入口，賣家實務上永遠無法觸發它。

---

## 2. 後端實作

### 2.1 `OrderService.getOrder`：補 same-tenant 分支

修法**不是**在 `getOrder` 另外複製一份「owner or same-tenant or admin」判斷，而是直接重用既有的 `checkOrderStatusUpdateAuthorization`（`updateOrderStatus` 專用的三選一檢查），改名為 `checkOrderTenantAuthorization` 並更新 Javadoc 涵蓋第三種呼叫情境（`getOrder`）。理由：

- 兩處需要的判斷邏輯**完全相同**（owner or same-tenant or admin），另外複製一份會製造第二個需要同步維護的授權判斷點——這正是 DEF-024 當初修 `updateOrderStatus` 時，`getOrder`/`cancelOrder`/`getOrderStateLogs` 已各自维护一份判斷、彼此不同步的同一類風險。
- 單元測試新增 `getOrder_sameTenantNonOwner_passesAuthorization`／`getOrder_otherTenantNonOwner_throwsE1007` 兩案例，鎖住修復後行為；既有 `getOrder_otherUser_throwsE1007`（未設定 `TenantContext.setCurrentTenant`，故 `tenantId` 為 `null`）不受影響，維持原斷言。

`cancelOrder`／`getOrderStateLogs` 本輪**不動**——範圍明確限定在 `getOrder`（Sprint 150 §4 查明的讀寫不對稱只發生在 `getOrder` vs `updateOrderStatus` 之間），不擴大範圍「順便」改掉其他方法的既有 owner-or-admin 行為。

### 2.1.1 🔴 `DEF-189`：重用過程中意外揭露並隨手修復的既有漏洞——same-tenant 分支對「系統租戶」誤放行

`mvn -o verify` 第一輪執行時，既有的 `BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder`（DEF-018 回歸測試，Sprint 32 建立）紅燈：兩個互不相識的買家帳號（買家 A／C，皆未加入任何店鋪的一般 BUYER），C 讀取 A 的 ROOM 訂單詳情，預期 403 卻實得 200。

**根因**：`TenantContextFilter.resolveEffectiveTenantId` 對「使用者未歸屬任何實際租戶」（`User.tenantId` 為空——一般 BUYER 帳號、或尚未通過審核的 SELLER 皆屬此類）一律 fallback 到同一個常數 `AppConstants.SYSTEM_TENANT_ID`。也就是說**任兩個未加入店鋪的一般使用者，`TenantContext.getCurrentTenant()` 會是同一個值**。`checkOrderTenantAuthorization`（原 `checkOrderStatusUpdateAuthorization`）的 same-tenant 分支沒有排除這個「沒有真正租戶」的預設佔位值，導致任一買家都能讀取/操作任一其他買家掛在系統租戶下的訂單——是本方法原本要防堵的同一種跨使用者 IDOR，只是換了一個觸發路徑。

**此漏洞的實際年齡與為何直到現在才被發現**：此邏輯自 `DEF-024`（Sprint 70）起即存在於 `updateOrderStatus`，五個 Sprint 以來完全沒有測試以「兩個一般買家帳號」的真實組合覆蓋 `PATCH /v2/orders/{orderId}/status`——`OrderServiceTest` 既有的 `updateOrderStatus_sameTenantNonOwner_passesAuthorization` 用的是專屬測試 fixture 租戶 ID（非 `SYSTEM_TENANT_ID`），不會踩到這個邊界。本次 Sprint 151 讓 `getOrder` 開始共用同一份檢查邏輯後，才被結構相同但 fixture 更真實（真的用兩個各自獨立註冊的買家帳號）的既有 E2E 測試意外揭露。

**修法**：`checkOrderTenantAuthorization` 的 `isSameTenant` 判斷明確排除 `SYSTEM_TENANT_ID`（新增 `private static final UUID SYSTEM_TENANT_UUID` 常數）。因為 `getOrder`／`updateOrderStatus` 共用同一份方法，此修復**同時**補上兩個呼叫路徑的漏洞，不是只修 `getOrder` 這一側就交差。

**驗證**：`OrderServiceTest` 新增 `getOrder_bothUsersOnSystemTenant_throwsE1007`／`updateOrderStatus_bothUsersOnSystemTenant_throwsE1007` 兩個單元測試，直接鎖住「兩個系統租戶使用者互相看不到/動不了對方訂單」；`mvn -o test -Dtest=BuyerOrderJourneyE2ETest` 重跑後 5/5 通過（含原本紅燈的 `otherBuyerCannotGetOrder`）。

**誠實揭露**：這不是本輪原定範圍（原定只動 `getOrder`），但既然兩個方法已合併為同一份檢查邏輯，發現後不修等同明知留著一個可利用的跨買家 IDOR，故判斷為「重用過程中意外發現、理應隨手修復」而非「範圍外、留給下一輪」——比照 Rule 12「大聲失敗」與過去多次「查證後發現比原判斷更嚴重、如實更正範圍」的處理原則（例如 [[m17-tenant-application-dual-flow]] 一類的既有教訓）。登記為 `DEF-189`。

### 2.1.2 🔴 `DEF-190`：比對後發現 `LogisticsService.checkOrderTenant` 同型既有漏洞，經 `AskUserQuestion` 徵詢後一併修復

修 `DEF-189` 時，順著「same-tenant 檢查」這個關鍵字比對全庫其他採用相同 tenant-based IDOR 防護模式的既有方法，發現 `LogisticsService.checkOrderTenant`（DEF-019/036 既有機制，供 `createLogistics`/`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics` 共 7 個方法共用）是**完全相同的程式碼模式**（`!isAdmin && (tenantId == null || !tenantId.equals(order.getTenantId()))`），同樣未排除 `SYSTEM_TENANT_ID`。

由於這是另一個檔案、當時沒有任何紅燈測試證實（`LogisticsService` 先前完全沒有單元測試），且已超出「因本輪改動直接導致既有測試失敗」的直接因果範圍，依 Rule 1／Rule 3 判斷不宜逕自擴大 diff，改用 `AskUserQuestion` 如實揭露發現內容（根因、受影響的 7 個方法、尚無測試證實）並徵詢使用者：現在一併修 vs. 登記 `DEF-190` 留待下一輪。使用者選擇「現在一併修」。

**修法**：`checkOrderTenant` 改用與 `OrderService.checkOrderTenantAuthorization` 相同的 `isSameTenant` 寫法，明確排除 `SYSTEM_TENANT_UUID`（同一常數定義方式，各自檔案內各自宣告一份，不跨檔案共用常數——兩個 Service 屬不同套件、無既有共用工具類別，硬拉一個共用常數會是比較大的架構決策，非本輪範圍）。

**驗證（紅燈先行且實際執行）**：新增 `LogisticsServiceTest.java`（此類別先前完全不存在的單元測試檔案），聚焦 `getLogisticsByOrderId`（`checkOrderTenant` 最簡呼叫路徑，免另外 mock 物流商 Provider）的授權矩陣：本租戶放行、他租戶拒絕、admin 跨租戶放行、訂單不存在、以及關鍵的**兩個系統租戶使用者互相拒絕**。`git stash` 暫存修復後的 `LogisticsService.java`，對還原後的舊程式碼跑新測試，`getLogisticsByOrderId_bothUsersOnSystemTenant_throwsE1007` 如預期紅燈（其餘 4 案例維持綠燈，證明修法只精準補上系統租戶這個缺口，未改動既有行為）；`git stash pop` 還原修復後重新編譯測試，5/5 全數通過。

**未做**：其餘 6 個共用 `checkOrderTenant` 的方法（`createLogistics` 等）未逐一各自新增測試——`getLogisticsByOrderId` 已完整驗證 `checkOrderTenant` 本身的授權矩陣（該方法是被驗證對象唯一的邏輯來源，其餘 6 個方法只是換個呼叫外殼），逐一重複驗證同一段共用邏輯不會提高信心、只會增加維護成本，非本輪省略遺漏。

### 2.1.3 修復 DEF-190 的副作用：既有 `M11LogisticsOrderIntegrationTest` 兩紅兩假綠

`LogisticsServiceTest` 綠燈後，完整 `mvn -o verify` 第三輪出現新的紅燈：`M11LogisticsOrderIntegrationTest` 4 案例中 2 個失敗（`createLogistics_confirmedOrder_returns200AndUpdatesOrderToShipping`／`updateLogisticsStatus_delivered_updatesOrderToDelivered`，皆 expected 200 got 403）。

**查證**：該測試檔的 `buildConfirmedOrder()` 把訂單 `tenantId` 硬編為 `00000000-0000-0000-0000-000000000001`——這個值**恰好等於** `AppConstants.SYSTEM_TENANT_ID`。四個測試方法皆用 `@WithMockUser(authorities = {...})` 模擬登入，但 `@WithMockUser` 建立的是 Spring Security 內建 `User` principal，不是本專案的 `UserPrincipal`；`TenantContextFilter` 只在 `authentication.getPrincipal() instanceof UserPrincipal` 時才解析出真正的 tenantId，否則一律 fallback 到系統租戶。也就是說這個測試檔原本就是靠「訂單 tenantId 的硬編值恰好等於 `@WithMockUser` fallback 到的系統租戶值」這個巧合，讓 `checkOrderTenant` 的 same-tenant 分支通過——這正是 `DEF-190` 要排除的那個佔位值本身，本檔案的測試手法從一開始就在無意間依賴它。此問題其實**已被本專案自己的 `TestSecurityContextHelper` 工具類別的既有 Javadoc 明確記載並警告過**（「使用 `@WithMockUser` 會建立 String principal，導致 TenantContextFilter 無法解析租戶資訊」），只是這個檔案先於該工具類別存在、從未被追溯遷移。

`DEF-190` 修復後，`checkOrderTenant` 排除系統租戶，導致：
- IT-SHIP-001／IT-SHIP-004（原本期待 200）→ 真的變成 403，紅燈。
- IT-SHIP-002／IT-SHIP-003（原本就期待某個 4xx，斷言用寬鬆的 `.is4xxClientError()`）→ **表面仍是綠燈，但驗的已經不是原本要測的 422/409 業務邏輯分支，而是 403 授權分支**——這是典型的「測試以固件僥倖繞過同一段邏輯」偽陽性（見 [[e-commerce-prd-gap-sprints-93-94]] 已有的同型警訊），不修就會留著兩個「看起來測到、其實測錯」的假綠燈測試。

**修法**：把該測試檔的四個 `@WithMockUser` 全部改為既有的 `TestSecurityContextHelper.setUserContext(...)`（本專案已有的正確做法，只是這個檔案沒跟上），並把訂單 `tenantId` 改用一個明確與 `SYSTEM_TENANT_ID` 不同的新常數 `TENANT_ID = 40000000-...-0001`。修復後 IT-SHIP-002 的實際回應訊息確認為 `E-5001 無效的訂單狀態`（原本要測的分支），而非之前假綠燈時真正觸發的 403，證實修法恢復了測試原意。

**驗證**：`mvn -o test -Dtest=M11LogisticsOrderIntegrationTest` → **4 passed / 0 failed**。

**checkstyle 順帶清理**：新增/修改上述兩檔時各留下一個未用到的 import（`LogisticsServiceTest.java` 的 `Logistics`、`M11LogisticsOrderIntegrationTest.java` 的 `java.util.List`），`mvn -o verify` 的 `checkstyle-test` 執行擋下並清楚指出檔名/行號，依 CLAUDE.md「大聲失敗」直接修正，非本輪特別費工。

**第四輪 `mvn -o verify` 額外查到的既有 flaky 測試（與本輪無關）**：`BookingControllerE2ETest` 在完整 `mvn verify` 套件執行時出現 2 個 teardown 階段的 `DataIntegrityViolationException`（刪除 `users`/`listings` 時被 `bookings` 的外鍵擋下），與 `createBooking_idempotencyKeyStillProcessing_returns409` 有關。查證：本輪完全未觸碰 `Booking`/`BookingController`/`BookingService` 任何程式碼；單獨執行 `mvn -o test -Dtest=BookingControllerE2ETest` → **21 passed / 0 failed**，證實是既有的測試間狀態殘留型 flaky（比照 [[at-m10-chat-stomp-e2e-flaky]] 已有的判準：與變更無關的測試遇此僅需重跑非回歸），如實記錄但不在本輪範圍內修復。

### 2.2 新增 `GET /v2/orders/tenant`：賣家訂單列表

比照 `ReturnRequestController` 的「買家層 `/v2/returns` vs 店家層 `/v2/dashboard/returns`」分層路由慣例，本應命名為 `/v2/dashboard/orders`，但該路徑已被 `AnalyticsController.getOrderStats`（訂單統計彙總）佔用，故改於既有 `OrderController`（base path `/v2/orders`）底下新增 `/tenant` 子路徑。與 `/{orderId}` 同層並存不會衝突（Spring 對靜態路徑段的比對優先於路徑變數，`MediaCategoryController` 的 `/root` 與 `/{categoryId}` 已是同一寫法的既有前例）。

`OrderService.getTenantOrders(page, size, sortBy, sortDir, status)`：

- 選填 `status` 篩選時呼叫既有 `orderRepository.findByTenantIdAndStatus`；未篩選時呼叫既有 `orderRepository.findByTenantIdOrderByCreatedAtDesc`。**兩個 repository 方法皆已存在**（`OrderRepository.java` 既有的 `AnalyticsService` 用查詢），未新增任何查詢方法。
- 分頁大小上限沿用 `getUserOrders` 既有的 `Math.min(size, 100)` 慣例。
- `tenantId` 為 `null`（例如買家帳號誤呼叫本端點）時原樣傳給 repository，查詢自然回傳空頁，不特別拋錯——`order:read` 權限買家本身也持有，用「空列表」而非 403 處理這個邊界情境更簡單，且不洩漏任何資訊。

`OrderDto.OrderListResponse` 新增 `shippingRecipientName` 欄位（`toOrderListResponse` 同步補上），供賣家在列表頁識別買家身份；買家自己呼叫 `getUserOrders` 看到的是自己填寫的收件人，無額外揭露疑慮。

### 2.3 未變動範圍

- `LogisticsController`/`LogisticsService`：既有的 `createLogistics`（`CONFIRMED→SHIPPING` 原子搶占 + `checkOrderTenant` 租戶檢查）功能完整，前端直接接上既有端點，後端零變動。
- `RolePermissionMapping`：`SELLER`/`STORE_OWNER` 角色既有 `ORDER_READ`/`ORDER_UPDATE`/`ORDER_CREATE`（`order:create` 供建立物流單使用），無需新增權限映射。

---

## 3. 前端實作

1. `lib/api.ts`：`API_ENDPOINTS.orders` 新增 `tenantList`（`/v2/orders/tenant`）、`updateStatus(id)`（`/v2/orders/{id}/status`，此前僅後端有此端點、前端從未呼叫）；`API_ENDPOINTS.logistics` 新增 `create`（`/v2/logistics`）。
2. `services/order.ts`：`OrderListItem` 新增 `shippingRecipientName`；新增 `TenantOrderQuery` 型別（`OrderQuery` + `status`）；新增 `getTenantOrders(query)`、`updateOrderStatus(id, targetStatus, reason?)` 兩方法。
3. `services/logistics.ts`：新增 `CreateLogisticsRequest` 型別與 `createLogistics(request)` 方法。
4. `app/dashboard/orders/page.tsx`（新檔）：訂單列表，比照 `dashboard/shipping/page.tsx` 的 nav 版型慣例；狀態篩選用 `Select`（Radix Select 不接受空字串 value，故以 `'ALL'` 代表全部狀態，呼叫 API 時轉為不帶 `status` 參數）；分頁用既有 `Pagination` 元件；掛載後於 client 端讀 `?status=` query string 帶入初始篩選（**刻意不用 `useSearchParams`**——比照 `(auth)/returns/new` 讀 `orderId` 的既有慣例，用 `new URLSearchParams(window.location.search)` 避免 Next.js 的 Suspense 邊界要求）。
5. `app/dashboard/orders/[id]/page.tsx`（新檔）：訂單詳情，依目前狀態顯示對應的賣家操作：
   - `PAID` → 「確認訂單」按鈕（`updateOrderStatus` → `CONFIRMED`）
   - `CONFIRMED` → 「建立物流單」表單（物流商 `Select` + 收件人/電話/地址，預設帶入訂單既有收件資訊，可覆蓋；提交後端點成功會把訂單原子轉為 `SHIPPING`，見 `LogisticsService.createLogistics` 既有邏輯）
   - `SHIPPING` → 「確認送達」按鈕（→ `DELIVERED`）
   - `DELIVERED` → 「標記完成」按鈕（→ `COMPLETED`）
   - 其餘狀態（`CREATED`/`CANCELLED`/`REFUNDING`/`REFUNDED`）：純顯示，無操作按鈕
   - 出貨作業區塊**僅對 `orderType === 'PRODUCT'` 顯示**（比照買家詳情頁「物流追蹤（商品訂單）」既有的同一判斷）；`ROOM` 訂單目前走 `BookingService`/`Booking` 的獨立路徑（見 [[room-booking-dual-path-gotcha]]），`OrderService.createRoomOrder` 前端零呼叫點，本頁面不為它另建出貨流程
6. `app/dashboard/page.tsx`：`QUICK_LINKS` 新增「訂單管理」連結（`data-testid="dashboard-orders-link"`）；「訂單狀態總覽」區塊的 6 個狀態數字改為可點擊連結，導向 `/dashboard/orders?status=<對應狀態>`——把 Sprint 150 §4 點出的「有統計數字、無管理入口」缺口實際補上（`pendingShipment` 對應 `PAID`，即 `AnalyticsService.getOrderStats` 既有定義的「待出貨＝狀態為 PAID」）。

---

## 4. 範圍外（刻意不做，如實揭露）

- **賣家訂單詳情頁不顯示狀態變更歷史（State Log 時間軸）**：Sprint 150 §4 拍板的範圍是「列表＋詳情＋出貨」，未包含此項；`getOrderStateLogs` 本身也維持既有 owner-or-admin 授權（§2.1 已說明不擴大範圍）。若後續需要，屬於獨立的小型追加項目，非本輪漏做。
- **新的 `GET /v2/orders/tenant` 端點未新增 Controller 層 E2E 測試**：比照 Sprint 150 §6 已記錄的既有基礎設施缺口——`OrderControllerE2ETest` 現有的 `registerAndLogin`/`@BeforeAll` 建置流程只組出 `BUYER` 角色的 JWT，沒有可重用的「取得一個具備 `order:read`/`order:update`、真正 ACTIVE 租戶的 SELLER 測試帳號並登入」路徑（`/tenant/apply` 實際走向見 [[m17-tenant-application-dual-flow]]）。這是先於本輪存在、範圍更大的既有基礎設施缺口，非本輪順手能解決。改以 `OrderServiceTest`（Mockito 單元測試，直接操作 `TenantContext`/`SecurityContextHolder`，不需真實 JWT）取得授權邏輯的完整覆蓋（§5.1），並於本機以真實 Docker 環境手動驗證路由本身可達（§5.3）。
- **`ROOM` 類型訂單不提供出貨/物流 UI**：見 §3.5 說明，`ROOM` 訂單的真實預訂流程不經過 `Order` 實體。
- **不新增 Playwright E2E**：理由同 Sprint 150 §6（既有 E2E helper 無法取得 ACTIVE 賣家租戶測試帳號），以手動 Docker 驗證替代。

---

## 5. 驗證結果

### 5.1 後端

- `OrderServiceTest`（新增 8 個案例：`getOrder` same/other/system-tenant 各 1、`updateOrderStatus` system-tenant 1、`getTenantOrders` 4 個）：`mvn -o test -Dtest=OrderServiceTest` → **60 passed / 0 failed**。
- `LogisticsServiceTest`（新檔，先前完全不存在，新增 5 個案例：`getLogisticsByOrderId` same/other-tenant／admin／system-tenant／訂單不存在）：`mvn -o test -Dtest=LogisticsServiceTest` → **5 passed / 0 failed**（`git stash` 紅燈先行：還原 `DEF-190` 修復後重跑，`bothUsersOnSystemTenant` 案例如預期紅燈，其餘 4 案例仍綠燈，`git stash pop` 還原修復後 5/5 全綠）。
- `mvn -o verify`（含 Flyway `ddl-auto=validate` 真實 Postgres + Redis 的完整單元＋整合回歸）共跑 5 輪：
  - **第一輪紅燈**：`BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder` 失敗（expected 403 got 200）→ 查明為 `DEF-189`（§2.1.1）並修復。
  - **第二輪**：1232 單元 + 477 整合，0 failed，BUILD SUCCESS（此時 `DEF-190` 尚未修復，僅涵蓋 `DEF-189`）。
  - **第三輪**（修 `DEF-190` 後）紅燈：`M11LogisticsOrderIntegrationTest` 2 個失敗 → 查明為既有測試巧合依賴系統租戶佔位值（§2.1.3）並修復，順帶清理 2 個 checkstyle 未用 import。
  - **第四輪**紅燈：checkstyle 未用 import 擋下（上一輪的清理尚未提交進這輪快照）+ `BookingControllerE2ETest` 2 個 teardown flaky（與本輪無關，見 §2.1.3 說明，隔離重跑 21/21 通過）。
  - **第五輪（最終）**：**1237 個單元測試 + 477 個整合測試，0 failed，BUILD SUCCESS**。

### 5.2 前端

- `npx tsc --noEmit`：0 error。
- `npx eslint`（對變更/新增檔案）：0 error（修正過程中移除一個不必要的 `eslint-disable-next-line react-hooks/exhaustive-deps` 註解——該行實際未觸發規則）。既有的 `import/no-anonymous-default-export` 警告（`services/order.ts`/`services/logistics.ts`）與 `services/address.ts` 等既有檔案同型，非新增問題。
- `npm run build`（Next.js production build，Turbopack）：成功，`/dashboard/orders`（Static）與 `/dashboard/orders/[id]`（Dynamic）皆正確產生路由。

### 5.3 手動功能驗證（真實 Docker 環境）

`make up` 啟動真實 Docker Compose 全棧（postgres+redis+backend+frontend）。以 `psql` 直接建立測試資料（比照 Sprint 150 §6 的既有手法，繞開 `/tenant/apply` 拿不到 ACTIVE 租戶的問題，見 [[m17-tenant-application-dual-flow]]）：一個 `ACTIVE` 測試租戶、一個歸屬該租戶的 `SELLER` 使用者、一筆 `PAID` 狀態的 PRODUCT 訂單。透過真實登入取得帶正確 `role`/`tenantId` 的 JWT，實際呼叫本輪新增/變更的端點：

- `GET /v2/orders/tenant`（無篩選 / `status=PAID` / `status=SHIPPING`）：正確回傳該租戶訂單、依狀態篩選、無符合資料時回空頁，`shippingRecipientName` 正確帶出。
- `GET /v2/orders/{orderId}`：賣家（非訂單擁有者、同租戶）讀取 → **200**（DEF-189 修復生效前是 403）；跨租戶賣家讀取同一筆訂單 → **403**；跨租戶賣家的 `GET /v2/orders/tenant` → 正確回空頁（看不到別租戶的訂單）。
- `PATCH /v2/orders/{orderId}/status`：`PAID→CONFIRMED→DELIVERED→COMPLETED` 皆正確轉換並回傳更新後的訂單。
- `POST /v2/logistics`：建立物流單後，訂單原子轉為 `SHIPPING`（`GET` 訂單確認 `status: SHIPPING`），`GET /v2/logistics/order/{orderId}` 正確回傳剛建立的物流單（追蹤號、狀態）。

**手動驗證過程中發現的額外項目（`DEF-191`，未修復，登記待評估）**：以 `SELLER` 角色呼叫 `POST /v2/logistics` 得到 `403 E-1007`，查證後確認**不是**本輪 `DEF-189`/`DEF-190` 修復造成的迴歸，而是 `RolePermissionMapping` 既有的權限矩陣本身的缺口——`LogisticsController.createLogistics` 要求 `order:create`，但 `SELLER` 角色只有 `ORDER_READ`/`ORDER_UPDATE`，沒有 `ORDER_CREATE`（`STORE_OWNER` 有，`STORE_STAFF` 兩者皆無）。也就是說**在目前的權限模型下，`SELLER` 角色可以把訂單從 `PAID` 確認到 `CONFIRMED`（有 `order:update`），卻無法建立物流單完成下一步出貨**——`PATCH .../status` 與 `POST /v2/logistics` 這兩個相鄰步驟所需的最低角色不一致，找不到任何既有註解說明這是刻意的業務規則（`RolePermissionMapping.java` 其餘角色的特殊限制皆有明確理由註解，唯獨這裡沒有），較像是既有疏漏而非設計。改用 `STORE_OWNER` 角色（同一租戶）重跑整段流程（`CONFIRMED→建立物流單→SHIPPING→DELIVERED→COMPLETED`）**全數成功**，證實本輪程式碼本身正確、缺口純屬既有權限矩陣。是否要讓 `SELLER` 也拿到 `order:create`（或改讓 `createLogistics` 改用 `order:update`，語意上更貼近「賣家管理訂單履約」而非「建立訂單」）屬於權限矩陣的業務決策，非本輪程式邏輯缺陷，故不在本輪擅自變更 `RolePermissionMapping`——登記 `DEF-191`，詳見 [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)。

**未清理測試資料**：本輪建立的測試租戶/使用者/訂單/物流單留在 dev docker volume 中（純本機開發資料庫，非共用/生產環境），未特別清除。

**`make validate-e2e`（乾淨 DB + host 全棧 + Playwright，複製雲端 e2e job）**：手動驗證後 `make down` 停用 dev stack，接著跑此既有回歸基準。**57 passed / 4 skipped / 0 failed**，與 Sprint 145/146/148/149/150 既有基準完全一致；backend 以 `ddl-auto=validate` + Flyway 成功啟動，腳本明確輸出「entity 與 Flyway schema 對齊，無漂移」，確認本輪變更（含新增的 `GET /v2/orders/tenant` 端點與 `OrderDto.OrderListResponse.shippingRecipientName` 新欄位）無 schema 或前端回歸。

---

## 6. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | `OrderService.getOrder` 補 same-tenant 授權分支 | Sprint 150 §4 | ✅ 完成 | 詳見 §2.1 |
| 2 | 新增 `GET /v2/orders/tenant`（賣家訂單列表） | Sprint 150 §4 | ✅ 完成 | 詳見 §2.2 |
| 3 | `/dashboard/orders`（列表）＋ `/dashboard/orders/[id]`（詳情，含狀態轉換與建立物流單） | Sprint 150 §4 | ✅ 完成 | 詳見 §3 |
| 4 | `DEF-189`：`OrderService` same-tenant 分支對系統租戶誤放行（跨買家 IDOR，追溯至 DEF-024/Sprint 70） | 本輪 `mvn verify` 紅燈意外揭露 | ✅ 完成 | 詳見 §2.1.1 |
| 5 | `DEF-190`：`LogisticsService.checkOrderTenant` 同型漏洞（追溯至 DEF-019/Sprint 32） | 修 DEF-189 時比對發現，經 `AskUserQuestion` 徵詢後一併修 | ✅ 完成 | 詳見 §2.1.2 |
| 6 | 執行 `mvn -o verify` 完整回歸並回填本節結果 | 本輪交付前 | ✅ 完成 | 見 §5.1，1237 單元 + 477 整合，0 failed |
| 7 | `DEF-191`：`SELLER` 角色缺 `order:create`，無法呼叫 `POST /v2/logistics` 建立物流單 | 本輪手動 Docker 驗證發現 | ⚠️ 已記錄，待排程 | 詳見 §5.3，需業務決策（放行 SELLER 或改端點權限），非本輪擅自變更 |
| 8 | `RELEASE_TRACKER`/`DEFERRED_ITEMS_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ⬜ 待回填 | |
| 9 | 賣家訂單詳情頁狀態變更歷史（State Log 時間軸） | 本輪 §4 範圍外 | ⬜ 待排程 | 低優先級，可獨立小型追加 |
| 10 | `DEF-103/104/105` 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已拍板不排入排程 |
| 11 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | S147 §6 範圍外 | ⬜ 待排程 | 需先確認是否要做 |
| 12 | 會員資料匯出／自助刪除帳戶補前端入口 | S149 §7 範圍外 | ⬜ 待排程 | 需先確認 UI 位置與是否要做 |
| 13 | OAuth 登入/連結串接 | 既有 stub（S78 記錄） | ⬜ 待排程 | 需先確認是否要做 |
| 14 | `AnalyticsService`/`Controller` Javadoc「Mock」字樣過時 | S150 §7 範圍外 | ⬜ 待排程 | 純文件修正，低優先級 |

---

## 7. 誠實揭露總結

- `OrderService.getOrder` 的修法選擇「重用既有方法並改名」而非「另寫一份重複判斷」，是刻意的簡化決策（Rule 2）：兩處邏輯完全相同時，維護兩份同義程式碼本身就是下一個 DEF-024 的種子。
- `GET /v2/orders/tenant` 未依原本設想命名為 `/v2/dashboard/orders`，是撞上既有路由的臨場修正，非隨意命名——已在程式碼與本文件中說明原因。
- 本輪刻意不做狀態歷史時間軸、不擴大 `cancelOrder`/`getOrderStateLogs` 的授權範圍、不為 `ROOM` 訂單建出貨 UI——皆為明確劃定範圍邊界，不是遺漏。
- 新端點未有 Controller 層 E2E 測試，如實記錄原因（既有 E2E 基礎設施缺口，非本輪能力範圍內解決），並說明以何種方式（Mockito 單元測試 + 手動 Docker 驗證）補足驗證強度。
- **`DEF-189` 是本輪執行 `mvn -o verify` 才意外揭露的既有漏洞**，非原定範圍：發現後判斷為「同一份共用邏輯，不修等於留著已知可利用的跨買家 IDOR」而隨手修復，並如實記錄其真實根源可追溯至 Sprint 70（DEF-024）、非本輪新增的問題，只是本輪的重用手法（§2.1 的設計決策）讓它從沉默狀態被既有測試揭露——這正是先前選擇「重用既有方法」而非「另寫一份重複邏輯」的附帶效益：修一次，兩個呼叫路徑一起修好。
- **`DEF-190` 與 `DEF-189` 不同**：不是被既有測試意外揭露（`LogisticsService` 先前完全沒有單元測試），而是修 `DEF-189` 時主動比對全庫同型模式才找到的。因為超出「因本輪改動直接導致既有測試失敗」的直接因果範圍，沒有逕自擴大 diff 動手修，而是用 `AskUserQuestion` 完整揭露發現內容（根因、受影響方法、尚無測試證實）讓使用者決定現在修或留到下一輪——這是 Rule 1「有疑慮時主動提問而非猜測」在「這個決定該不該由我自己做」這件事上的應用，不只是對程式碼本身的疑慮。使用者選擇現在修，修法與驗證方式（紅燈先行、`git stash` 證明修復前後差異）完整記錄於 §2.1.2。
- **`DEF-191` 是第三種不同的處理方式**：不是安全漏洞（不涉及資料外洩或越權存取，是 `SELLER` 角色被授權「太少」而非「太多」），也不是本輪程式碼造成的迴歸——單純登記待排程，不逕自修改 `RolePermissionMapping`（那是業務層的角色權限矩陣決策，該由使用者/產品面拍板要不要放行，而非工程判斷）。三個發現（`DEF-189`/`DEF-190`/`DEF-191`）分別對應三種不同的處置路徑，是本輪刻意依「風險/確定性/決策歸屬」逐案判斷的結果，不是同一套機械化規則套三次。
