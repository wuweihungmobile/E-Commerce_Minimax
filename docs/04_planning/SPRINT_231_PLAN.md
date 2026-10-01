# Sprint 231 Plan — 商家端訂房管理入口（DEF-316，含 DEF-306 的 Host booking:cancel 子項）

**Sprint**: Sprint 231
**日期**: 2026-10-02

## 1. 起點

Sprint 227 查證 `DEF-312` 時發現：PRD「商家主動取消 → 全額退款」只有管理員走得到，商家端完全沒有訂房管理入口，登記為 `DEF-316`，連續三輪（227→229→230）等使用者決定。這輪使用者明確回覆「以上若需要我決策，請先以互動選擇方式讓我選擇決定」，以 `AskUserQuestion` 呈現四個帶優缺點評估的方案後，使用者選擇「全部做到位，含 HOST 權限」（含租戶範圍擁有權檢查、HOST 新增 `booking:cancel`、商家端列表/取消 API、`/dashboard/bookings` 前端頁面）。

## 2. 查證（現況，動手前讀程式碼確認，非憑記憶）

1. `BookingController.cancelBooking`／`getBooking` 的 `@PreAuthorize` 僅檢查 `booking:cancel`／`booking:read`，真正的擁有權在 `BookingService.checkBookingOwnership`：只放行買家本人（`userId.equals(booking.getUserId())`）或 `ROLE_ADMIN`/`ROLE_SUPER_ADMIN`，**完全沒有租戶範圍分支**。
2. `RolePermissionMapping`：`STORE_OWNER` 已有 `BOOKING_CANCEL`，但因上述第 1 點仍會被擋 403；`HOST` 只有 `BOOKING_READ`/`BOOKING_UPDATE`，完全沒有 `BOOKING_CANCEL`；`STORE_STAFF` 已有 `BOOKING_READ`（唯讀不用新增）。
3. PRD §7.3（RBAC 矩陣）M06 預訂管理列：Host `RX*`、StoreOwner `RX*`、StoreStaff `R*`——目標狀態明確，非需使用者裁定的歧義。PRD §9.16／§10 列 `GET /api/v2/dashboard/bookings`、`/dashboard/bookings`，皆不存在。
4. `BookingRepository.findByTenantIdOrderByCreatedAtDesc` 已存在（既有方法），`grep` 確認全庫零呼叫者。
5. 既有可直接複製的租戶範圍檢查模式：`OrderService.checkOrderTenantAuthorization`（`isOwner`／`isSameTenant`／排除 `SYSTEM_TENANT_UUID`）與 `OrderService.getTenantOrders`（`/v2/orders/tenant`）；`ReturnRequestController` 的「買家層 `/v2/returns`、店家層 `/v2/dashboard/returns`」分層路由慣例；`OrderDto.OrderListResponse.shippingRecipientName`（Sprint 151，DEF-188）把訂購人姓名加進共用列表 DTO 的既有先例。
6. `cancelBooking` 的退款與通知邏輯（Sprint 227/229）已經是「依是否為本人決定 `CUSTOMER`/`MERCHANT`」，不是依呼叫端點判斷，一旦擁有權放行，自動套用「商家取消一律全額退款＋通知買家」，不需要另外改動。

## 3. 設計

- **後端擁有權**：`checkBookingOwnership` 新增 `isSameTenant`（`getCurrentTenant().equals(booking.getTenantId())` 且排除 `SYSTEM_TENANT_UUID`），與既有 `isAdmin`／`isOwner` 同屬一個 `if (!isAdmin && !isOwner && !isSameTenant)` 放行判斷，不改動呼叫端。
- **權限**：`HOST` 新增 `Permission.BOOKING_CANCEL`。
- **商家端列表**：新增 `BookingService.getTenantBookings`（比照 `getUserBookings`，改走 `findByTenantIdOrderByCreatedAtDesc`）；新增 `DashboardBookingController`（`GET /v2/dashboard/bookings`，`booking:read`），比照 `ReturnRequestController` 分層慣例獨立開一個小型 Dashboard controller（比照既有 `DashboardListingController`/`DashboardPricingController` 的既有風格，而非擴充 `BookingController` 本身的路由前綴）。
- **取消動作**：沿用既有 `POST /v2/bookings/{id}/cancel`，不另開店家層取消端點——取消方（CUSTOMER／MERCHANT）已由 service 依身分自動判定，開兩個端點只是重複同一段邏輯。
- **列表 DTO**：`BookingListResponse` 新增 `guestName`（比照 `shippingRecipientName` 先例，買家與商家共用同一個 DTO，買家看自己填的姓名無額外揭露）。
- **前端**：新增 `/dashboard/bookings`（列表，比照 `/dashboard/returns` 風格）與 `/dashboard/bookings/[id]`（詳情＋取消面板，比照買家自己的 `(auth)/bookings/[id]` 取消 UX，拿掉買家專屬的付款卡與評價區塊）。UI 不做角色判斷顯示/隱藏取消鍵——比照 `/dashboard/returns/[id]` 既有慣例，交給後端 403 把關，避免前後端權限邏輯重複維護。

## 4. 實作

1. `RolePermissionMapping`：HOST 新增 `Permission.BOOKING_CANCEL`。
2. `BookingService.checkBookingOwnership`：新增 `isSameTenant` 分支＋`SYSTEM_TENANT_UUID` 常數（比照 `OrderService`）。
3. `BookingService.getTenantBookings`（新方法）＋`BookingDto.BookingListResponse.guestName`（新欄位）＋`toBookingListResponse` 填入 `guestName`。
4. 新增 `DashboardBookingController`（`GET /v2/dashboard/bookings`）。
5. 前端：`lib/api.ts` 新增 `dashboardBookings.list`；`services/booking.ts` 新增 `BookingListItem.guestName`＋`listTenantBookings`；新增 `app/dashboard/bookings/page.tsx`、`app/dashboard/bookings/[id]/page.tsx`；`app/dashboard/page.tsx` 加入「訂房管理」快速連結。

## 5. 驗證

**單元測試**（每支程式碼改完立即編譯＋測試，未累積）：
- `RolePermissionMappingTest` +1：HOST 持有 `BOOKING_CANCEL`。
- `BookingServiceOwnershipTest` +6：本租戶 STORE_OWNER／HOST 取消放行（取消方判定 MERCHANT）、他租戶 STORE_OWNER 取消仍 E_1007、兩個都在系統租戶的一般買家取消他人訂房仍 E_1007（比照 `OrderServiceTest.getOrder_bothUsersOnSystemTenant_throwsE1007` 的既有教訓）、`getBooking` 本租戶/他租戶對應兩案例。
- `BookingServiceRoomTitleTest` +4：`getUserBookings`/`getTenantBookings` 皆正確填入 `guestName`；`getTenantBookings` size 裁切 100、null tenant 傳遞。

**整合測試**（真實 PostgreSQL，`BookingCancellationRefundIntegrationTest` +3）：
- 本租戶 `STORE_OWNER`（非管理員、非訂房買家本人）取消入住前不足 24 小時的已付款訂房 → 仍一律全額退款（取消方 MERCHANT），排程退回後 `REFUNDED`/`COMPLETED`。
- 他租戶 `STORE_OWNER`（真正持久化的第二個租戶，非 mock）取消 → `BusinessException`/`E_1007`，訂房狀態不變。
- `getTenantBookings` 只回傳本租戶訂房，看不到他租戶的（驗證 `findByTenantIdOrderByCreatedAtDesc` 這條先前零呼叫者的查詢語意正確）。

**真實 HTTP 層**（`BookingControllerE2ETest` +1，`API-M06-018`）：單元與整合測試都是直接呼叫 `BookingService` 方法，不經過 `DashboardBookingController` 的 `@RequestMapping`／`@PreAuthorize`——這兩者是字串（路徑、SpEL），語法錯誤不保證在 Spring context 啟動時就失敗，只有真的送一次 HTTP 請求才會觸發。新案例把既有買家測試帳號升級為 `STORE_OWNER` 並重新登入，以真實 JWT 呼叫 `POST /v2/bookings` 建一筆訂房、再呼叫 `GET /v2/dashboard/bookings`，確認 200 且回應內容包含剛建立的訂房（非空頁掩蓋 404/403）。

**前端**：`tsc --noEmit`／`eslint`／`next build` 皆通過，新頁面 `/dashboard/bookings`、`/dashboard/bookings/[id]` 正確產生為動態路由。前端 UI 本身未經瀏覽器實際操作驗證——本次會談環境沒有可用的瀏覽器／computer-use 工具，以上述真實 HTTP 層測試替代，未做到 CLAUDE.md 要求的「瀏覽器內走一次」。

**全量**（`mvn -o clean verify`，含 `API-M06-018`，11 分 44 秒）：**單元 1975（+10）／整合 684（+4）／0 失敗**；checkstyle 0 違規（main+test）；PMD 通過；JAR 打包成功。

**`make validate-e2e`**（乾淨 PostgreSQL＋Flyway、`ddl-auto=validate`、打包 JAR＋`npm start`）：112 個測試，108 通過／4 略過（與 Sprint 230 基準相同，非回歸）／0 失敗，4.7 分鐘。

## 6. 決策與已知限制

- **控制器路由命名比照 `ReturnRequestController`，非擴充既有 `BookingController`**：`BookingController` 的 `@RequestMapping("/v2/bookings")` 已有多個方法與既有測試，改前綴風險與改動面都大於新開一個小型 `DashboardBookingController`（比照既有 `DashboardListingController`/`DashboardPricingController` 風格）。
- **取消動作未另開店家層端點**：`POST /v2/bookings/{id}/cancel` 本身已依身分自動判定取消方與退款規則（Sprint 227/229 既有邏輯），複製一個 `/v2/dashboard/bookings/{id}/cancel` 只是路由重複、邏輯不變，故不開。
- **前端取消鍵不做角色判斷顯示/隱藏**：比照 `/dashboard/returns/[id]` 既有慣例，STORE_STAFF（無 `booking:cancel`）點擊取消會收到後端 403，由既有的錯誤訊息顯示機制處理，不在前端重複維護一份權限表。
- **原本評估「不需要另寫 HTTP 層測試」，後來推翻**：一開始的理由是 `booking:read` 沿用既有、已被 4 個端點驗證過的權限字串，`@PreAuthorize`／`@RequestMapping` 語法寫錯會讓整合測試共用的 Spring context 啟動失敗，683 個整合測試全過已間接證明 context 正常載入。但這個理由不成立——單元與整合測試都是直接呼叫 `BookingService` 方法，完全繞過 `@PreAuthorize` 的 AOP 攔截（它只在真正的 HTTP 呼叫時才由 Spring Security 攔截並求值 SpEL），Spring context 啟動也不會驗證每個 `@PreAuthorize` 字串的語法正確性。改為在 `BookingControllerE2ETest` 新增 `API-M06-018`，以真實 JWT 走一次真正的 HTTP 請求（見第 5 節）。
- **前端 UI 未經瀏覽器實際驗證**：本次會談環境沒有可用的瀏覽器／computer-use 工具，無法依 CLAUDE.md 要求「在瀏覽器中走一次」。已用上述真實 HTTP 層測試驗證後端路由與資料正確性、`next build` 確認前端能正確產生路由，但 React 元件本身的畫面呈現（版面、按鈕互動、取消面板流程）只靠人工讀程式碼比對既有頁面樣式，未實際渲染確認。

## 7. 後續

- 等使用者決定：`DEF-306` 其餘子項（STORE_STAFF 商品/房源管理 PRD 為 RW*、生產僅 R；上傳媒體同型落差；STORE_OWNER／ADMIN 的 `order:create` 落差）——這次只處理了 `DEF-306` 裡與訂房相關的 Host `booking:cancel` 子項，其餘與本輪無關的落差原樣保留。
- `DEF-317`／`DEF-318`（通知管線其餘缺口，Sprint 229 登記）。
- Stripe 真實路徑仍待使用者人工在測試模式驗證，啟用前必須完成。
