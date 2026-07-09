# Sprint 88 Plan — PRODUCT 商品訂單結帳流程前端串接

**Sprint**: Sprint 88
**日期**: 2026-07-09
**主題**: 補齊 Sprint 87 retro 發現並提高優先度的既有缺口——前端完全沒有任何頁面呼叫 `POST /v2/orders` 建立 PRODUCT（商品）訂單，導致地址簿等既有後端能力缺乏真實購物流程可用場景。本 Sprint 為使用者已排定「三者最佳化順序」的第一項（優先於 M16/M07 前端表單、M18 客服工單子系統），理由：範圍最小、直接解鎖已完成的後端能力（地址簿、訂單/付款 API），且探查中發現的兩個關聯缺口（混合購物車誤處理、庫存從未扣減）若不趁本次補上，會隨著這條路徑變成真實可用而從「休眠風險」變成「真實生產風險」。

---

## 1. 探查結果

- **後端 `POST /v2/orders` 建立 PRODUCT 訂單的能力早已完整**（`OrderService.createOrderFromCart`），但前端 `services/order.ts` 完全沒有 `createOrder()` 方法，`API_ENDPOINTS.orders.create` 端點定義好了卻從未被使用；`frontend/src/app/(auth)/checkout/page.tsx` 硬編碼只服務 ROOM 訂房流程（直接查 cart API 過濾 ROOM 項目、呼叫 `/v2/bookings`，非 `/v2/orders`）。
- **付款串接方式已存在、無需引入新前端套件**：`services/payment.ts`（`OrderPaymentService`）已完整具備 `getPaymentState`/`pay`（mock）/`createCheckoutSession`/`confirmCheckoutReturn`（Stripe Hosted Checkout 整頁重定向）方法，不需要 Stripe.js/Elements。
- **修正探查誤判**：初步探查（Explore agent 報告）誤判 Stripe 回跳目標頁面 `/orders/{orderId}/payment/success`、`/orders/{orderId}/payment/cancel` 前端完全不存在，實際確認後發現**兩頁皆已存在且實作完整**（`frontend/src/app/(auth)/orders/[id]/payment/success/page.tsx`、`.../cancel/page.tsx`，含 `data-testid` 供既有 e2e 測試使用，成功頁採「直接讀 `window.location.search` 取 `session_id`」而非 `useSearchParams()`，刻意避開 Next.js Suspense 邊界要求），本 Sprint 不需重做，直接沿用。
- **🔴 探查中發現且判斷須併入本 Sprint 的缺口 A：混合購物車誤處理（正確性缺陷，本 Sprint 修復）**：購物車為 ROOM/PRODUCT 共用（`RedisCartService`，`CartItemResponse.listingType` 可為 `PRODUCT`/`ROOM`），但 `OrderService.createOrderFromCart` 的 PRODUCT 分支目前對 `cart.getItems()` **完全不過濾類型**，會把購物車內的 ROOM 項目一併當作商品項目建進 PRODUCT 訂單，且建立成功後呼叫 `cartService.clearCart()` **清空整個購物車**（包含尚未處理的 ROOM 項目）。此路徑因為前端從未呼叫而處於休眠狀態，本 Sprint 一旦補上前端串接即成為真實可觸發路徑，須同步修復：僅取用 `listingType == PRODUCT` 的購物車項目建單，建立成功後僅移除已處理的項目（`RedisCartService.removeItem(cartItemKey)`），保留其餘（如 ROOM）項目供使用者另外結帳。
- **🔴 探查中發現且經使用者裁示併入本 Sprint 的缺口 B：庫存從未扣減（超賣風險，已詢問使用者並取得「並入本 Sprint」裁示）**：`ProductInventory` entity（`totalQty`/`reservedQty`/`hasAvailableStock`/`reserve`/`release`/`deductStock`，含 `@Version` 樂觀鎖）設計完整，但目前只有 M16 ERP 採購/庫存異動模組（`PurchaseOrderService`/`StockMovementService`）在使用，`OrderService.createOrderFromCart`/`PaymentStateService` 完全沒有呼叫它做庫存檢查/扣減/釋放。同樣因為前端從未串接而處於休眠狀態，本 Sprint 補上後將成為真實可用的購物路徑，需同步接上：**訂單建立時檢查庫存並預扣（`reserve`）**，**付款成功時正式扣帳（`deductStock`）**，**訂單於未付款狀態被取消時釋放預扣（`release`）**。
- **已存在但未使用的錯誤碼 `E_3004`（庫存不足，400 Bad Request）**：定義好但目前無任何呼叫端使用，正好對應本次庫存檢查需求。
- **`OrderItem.skuId`/`listingId`/`orderId` 均為 `insertable=false, updatable=false` 影子欄位**（比照既有教訓，庫存服務一律使用 `item.getSku()` 真實關聯取得 SKU id，不使用影子欄位）。

## 2. 架構決策

- **庫存檢查/扣減範圍界定（PO 已裁示「並入本 Sprint：訂單建立時檢查+預扣庫存」）**：
  - SKU 若無對應 `ProductInventory` 資料列 → 視為「未啟用庫存追蹤」，不限量、略過檢查（許多既有 SKU 從未建立庫存列，向下相容既有訂單流程不受影響）。
  - **Reserve-at-creation, Deduct-at-payment, Release-on-cancel-before-payment** 三段式：
    1. `createOrderFromCart`（PRODUCT）：逐項檢查 `hasAvailableStock`，不足拋 `E_3004`（400，拒絕建單，交易回滾不留部分建立的訂單）；足夠則 `reserve()` 預扣 `reservedQty`。
    2. `PaymentStateService.mockPaymentSuccess` / `markStripePaymentSucceeded`（Mock 與 Stripe 兩付款成功路徑共用同一扣帳呼叫點）：呼叫 `deductStock()` 將預扣轉為正式扣帳（`totalQty` 與 `reservedQty` 同時扣除）。比照既有 `SettlementAdjustmentService` 呼叫慣例，包 try-catch，失敗僅記錄 log、不影響付款成功主流程。
    3. `OrderService.cancelOrder`：僅當訂單**取消前狀態為 `CREATED`**（尚未付款，仍屬預扣未扣帳狀態）才呼叫 `release()` 釋放預扣；若取消前已是 `PAID`（已扣帳），本 Sprint **不**做退款自動回補庫存（範圍外，見第 8 節，避免與 M07 結算/退款邏輯耦合過深）。
  - 新增 `core/product/ProductInventoryService.java`：`reserveForOrder(Order)`/`releaseForOrder(Order)`/`deductForOrder(Order)`，統一封裝上述三個操作，供 `OrderService`/`PaymentStateService` 呼叫。
- **混合購物車修復範圍界定**：僅修復 `OrderService.createOrderFromCart`（PRODUCT 分支）過濾邏輯，**不**修改既有 ROOM 訂房流程（`checkout/page.tsx` 呼叫 `/v2/bookings` 後前端直接 `DELETE /v2/cart` 整個清空購物車）——此為另一條已上線、與本次主題無直接關聯的既有缺口，記錄於第 8 節並列入 tracker，不在本 Sprint 修復。
- **前端結帳路由**：新增獨立路由 `frontend/src/app/(auth)/checkout/product/page.tsx`（不更動既有 `/checkout` ROOM 頁面）。購物車頁「前往結帳」按鈕依購物車內容分流：**只要購物車內有任何 PRODUCT 項目即優先導向新的 PRODUCT 結帳頁**（因新流程僅處理 PRODUCT 項目、不影響購物車內殘留的 ROOM 項目，使用者可事後再對 ROOM 項目單獨結帳）；僅有 ROOM 項目時維持導向既有 `/checkout`。
- **地址選擇**：PRODUCT 結帳頁重用 Sprint 87 完成的 `AddressService`，列出使用者已存地址供選擇（預設地址預選），亦保留手動輸入收件欄位作為後備（無地址簿資料時）。
- **付款分流**：建單成功後查詢 `GET /v2/orders/{orderId}/payment` 取得 `paymentProvider`；`mock`（預設）→ 直接呼叫 `pay()` 完成；`stripe` → 呼叫 `createCheckoutSession()` 取得 `sessionUrl` 後 `window.location.href` 整頁導頁（沿用既有 Hosted Checkout 慣例，不引入 Stripe.js）。

## 3. 資料庫變更

無新 migration（`ProductInventory`/`addresses` 等既有 schema 已足夠）。

## 4. 後端實作清單（依 CLAUDE.md 開發-編譯-測試循環，逐檔編譯+測試）

1. `core/product/ProductInventoryService.java`（新增）：`reserveForOrder`/`releaseForOrder`/`deductForOrder`
2. `OrderService.createOrderFromCart`（PRODUCT 分支）：過濾 `listingType == PRODUCT`、無商品項拋 `E_5004`、建單前呼叫 `reserveForOrder`、建單後僅移除已處理項目（`removeItem` 取代 `clearCart`）
3. `OrderService.cancelOrder`：取消前狀態為 `CREATED` 時呼叫 `releaseForOrder`
4. `PaymentStateService`：新增 `ProductInventoryService` 依賴；`mockPaymentSuccess`/`markStripePaymentSucceeded` 成功轉 PAID 後呼叫 `deductForOrder`（try-catch 不中斷主流程）

## 5. 前端實作清單

1. `services/order.ts`：新增 `createOrder(request): Promise<Order>`（`CreateOrderRequest` 型別：`orderType`/`addressId?`/`shippingAddress?`/`shippingRecipientName?`/`shippingPhone?`/`notes?`）
2. `frontend/src/app/(auth)/checkout/product/page.tsx`（新增）：撈購物車（過濾顯示 PRODUCT 項目）→ 選擇/手動輸入收件地址 → 建單 → 依 `paymentProvider` 分流付款
3. `frontend/src/app/(auth)/cart/page.tsx`：「前往結帳」按鈕依購物車內容分流導頁
4. ~~payment/success、payment/cancel 頁面~~：探查後確認已存在且實作完整，不需新增（見第 1 節「修正探查誤判」）

## 6. 測試計畫

- `ProductInventoryServiceTest`（新檔）：庫存足夠/不足、無庫存列（不限量略過）、reserve/release/deduct 正確性
- `OrderServiceTest`：混合購物車僅取 PRODUCT 項目、購物車僅有 ROOM 項目時拋錯、庫存不足拋 `E_3004` 且不建立訂單、取消 `CREATED` 訂單釋放預扣、取消 `PAID` 訂單不釋放
- `PaymentStateServiceTest`/`PaymentStateServiceStripeTest`：付款成功呼叫扣帳、扣帳失敗不影響付款成功結果
- 全量回歸 `mvn verify -Pintegration-test`
- 前端：`npm run build`/`eslint`；受限於工具集無瀏覽器自動化能力，不做人工互動式瀏覽器操作驗證（比照 Sprint 87 誠實揭露）

## 7. 依賴/串接對照表

| 前端動作 | 後端 API | 既有/新增 |
|---------|---------|----------|
| 建立商品訂單 | `POST /v2/orders` | 既有（前端新增呼叫方法） |
| 查詢付款狀態/提供者 | `GET /v2/orders/{id}/payment` | 既有 |
| Mock 付款 | `POST /v2/orders/{id}/pay` | 既有 |
| 建立 Stripe Checkout Session | `POST /v2/orders/{id}/pay/checkout` | 既有 |
| Stripe 回跳確認 | `GET /v2/orders/{id}/pay/checkout/return` | 既有 |
| 列出/選擇收件地址 | `GET /v2/addresses` | Sprint 87 既有 |

## 8. 範圍外（記錄待未來 Sprint，已加入 DEFERRED_ITEMS_TRACKER）

- ROOM 訂房結帳流程（`checkout/page.tsx`）建立預訂成功後呼叫 `DELETE /v2/cart` 清空整個購物車（含混雜其中的 PRODUCT 項目）——與本次修復的 PRODUCT 側同類缺陷，但屬於另一條已上線流程，範圍與風險評估需獨立評估，不在本 Sprint 處理。
- 已付款訂單（`PAID`/`REFUNDED`）退款/取消時的庫存自動回補（restock-on-refund）——與 M07 結算/退款邏輯有交互，需獨立設計，本 Sprint 僅處理「預扣→扣帳」單向流程。
- 地址簿以外的收件資訊驗證（地址格式/自動完成）沿用 Sprint 87 既有基本必填檢查，不擴充。
