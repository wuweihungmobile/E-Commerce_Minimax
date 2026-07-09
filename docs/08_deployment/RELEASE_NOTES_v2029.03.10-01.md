# Release Notes - v2029.03.10-01 (Sprint 88)

**發布日期**: 2029-03-10（規劃）／實作完成 2026-07-09
**發布類型**: ✨ 新功能（PRODUCT 商品訂單結帳流程前端串接）+ 🐛 缺陷修復（混合購物車誤處理、庫存超賣風險）
**Sprint**: Sprint 88（承接 Sprint 87 retro 提高優先度的候選項目，「三者最佳化順序」第一項）

> Sprint 88 主題：補齊前端從未呼叫 `POST /v2/orders` 建立 PRODUCT 訂單的既有缺口，解鎖地址簿等既有後端能力的真實購物流程使用場景。探查中發現並經使用者裁示併入的兩項關聯缺口（混合購物車誤處理、庫存從未扣減）一併修復。

---

## ✨ 新功能：PRODUCT 商品訂單結帳流程前端串接

- **新頁面 `/checkout/product`**：撈取購物車（僅顯示 PRODUCT 項目）→ 選擇已存收件地址（沿用 Sprint 87 地址簿）或手動輸入 → 建立訂單 → 依付款提供者（`mock`/`stripe`）分流付款（mock 直接完成；stripe 導向既有 Hosted Checkout Session）。
- **`services/order.ts` 新增 `createOrder()`**：對齊既有 `POST /v2/orders` 端點（此前已定義但從未被前端呼叫）。
- **購物車頁「前往結帳」按鈕依內容分流**：購物車內有任何 PRODUCT 項目即導向新的商品結帳頁；僅有 ROOM 項目維持導向既有訂房結帳頁。
- **確認既有 Stripe 回跳頁面已完整可用**：`/orders/{id}/payment/success`、`.../cancel` 探查後確認已存在且實作完整，未重做。

## 🐛 缺陷修復：混合購物車誤處理

購物車為 ROOM/PRODUCT 共用，`OrderService.createOrderFromCart`（PRODUCT 分支）此前對購物車項目完全不過濾類型，會把 ROOM 項目誤併入 PRODUCT 訂單，且建單後清空整個購物車（連同尚未處理的 ROOM 項目）。因前端從未呼叫此路徑而處於休眠狀態，本次補上前端串接後修復：僅取用 `PRODUCT` 類型項目建單，建單後僅移除已處理項目，保留其餘項目供另外結帳。

## 🐛 缺陷修復：商品庫存從未扣減（超賣風險）

`ProductInventory` 庫存原語（`reserve`/`release`/`deductStock`/`hasAvailableStock`，含樂觀鎖）此前僅 M16 ERP 模組使用，訂單/付款流程從未呼叫，因前端從未串接而處於休眠風險。新增 `ProductInventoryService`，串接三段式流程：

- **訂單建立時**：檢查庫存並預扣（不足拋 `E_3004`，拒絕建單）。
- **付款成功時**（mock 與 Stripe 共用同一扣帳呼叫點）：預扣正式轉為扣帳。
- **訂單於未付款狀態取消時**：釋放先前預扣的庫存。

SKU 若無庫存資料列，視為未啟用追蹤、不限量、略過檢查（向下相容既有 SKU）。

## 🔍 探查中發現、記錄但不在本次範圍處理的既有缺口

- **DEF-043**：ROOM 訂房結帳流程（前端直接呼叫 `DELETE /v2/cart`）與本次修復的 PRODUCT 側同類缺陷，範圍需獨立評估。
- **DEF-044**：已付款訂單退款/取消時庫存不會自動回補，需業務決策回補時點與供應鏈整合方式。

## 🗄️ 資料庫變更

無新 migration（沿用既有 `ProductInventory`/`addresses` schema）。

## 測試 / 驗證 ✅

- **`ProductInventoryServiceTest`**（新檔）：6 tests（庫存足夠/不足/無庫存列/無 SKU 項目、reserve/release/deduct 正確性）。
- **`OrderServiceTest`**：+5 tests（混合購物車僅取 PRODUCT 項目、僅有 ROOM 項目時拋錯、庫存不足拋錯不建立訂單、取消 CREATED 釋放預扣、取消 PAID 不釋放）。
- **`PaymentStateServiceTest`**：+2 tests（付款成功扣帳、扣帳失敗不影響付款成功結果）。
- **`PaymentStateServiceStripeTest`**：+1 test（Stripe 付款成功轉 PAID 後扣帳）。
- **全量回歸**（`mvn verify -Pintegration-test`）：**單元 936 + 整合 367 = 1303 tests，0 failures，0 errors，BUILD SUCCESS**（過程中發現並修復 `M11ShippingFeeIntegrationTest` 3 個既有測試因 fixture 未設定 `listingType` 被新的 PRODUCT 過濾邏輯排除而回歸失敗，補上 `listingType("PRODUCT")`/`cartItemKey` 後轉綠）。
- **schema 漂移守門**：`make validate-schema` 無漂移。
- **前端**：`npm run build`（含 TypeScript 型別檢查，`/checkout/product` 路由成功掛載）通過，`eslint` 無新增 error；`curl` 確認 `/checkout/product`、`/cart`、`/addresses` 於本機 dev server 皆回 200（無伺服器崩潰）；受限於工具集無瀏覽器自動化能力，未做人工互動式瀏覽器操作驗證。

## 內含 Commit（Sprint 88）

| 項目 | 說明 |
|------|------|
| Sprint 88 Plan | PRODUCT 結帳串接規劃（含探查中發現的兩項關聯缺口裁示記錄） |
| 核心實作 | `ProductInventoryService` 新增、`OrderService`/`PaymentStateService` 串接、前端 `createOrder`/`/checkout/product`/購物車分流 |
| Sprint 88 收尾 | Retro / Release Notes + `DEFERRED_ITEMS_TRACKER.md`（AI-2422 移至已完成，新增 DEF-043/DEF-044） |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**基於**: AISDLC v0.09 Release Management Workflow
