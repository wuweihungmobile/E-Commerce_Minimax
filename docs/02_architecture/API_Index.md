# API Index / API 規格索引

> **文檔狀態**: Active
> **版本**: v1.3
> **建立日期**: 2026-04-09
> **最後更新**: 2026-10-03（Sprint 243）
> **作者**: Marcus (SD-Architect)
> **🔴 Sprint 16 US-008**: 收錄 Sprint 15 + Sprint 16 新增 API 端點
>
> **錯誤契約**: 所有 API 共用同一個回應封包與錯誤碼表，見 [API_Error_Codes.md](./API_Error_Codes.md)（Sprint 203）。
>
> **⚠️ 完整性未保證（Sprint 203 揭露）**: 本索引的多數模組最後更新於 2026-06-05，僅收錄 48 個端點；同日核對後端 controller 有約 305 個 `@*Mapping` 註解。**本索引不是完整端點清單**，見 DEF-286。
>
> **✅ 已完整且有守門的模組（Sprint 241／243）**: **M03 會員與權限**（[API_M03_Auth.md](./api/API_M03_Auth.md)，13 個端點）、**M04 購物車**（[API_M04_Cart.md](./API_M04_Cart.md)，9 個端點）、**M05 訂單履約**（[API_M05_Order.md](./api/API_M05_Order.md)，15 個端點，Sprint 243）與 **M06 預訂系統**（[API_M06_Booking.md](./API_M06_Booking.md)，11 個端點，Sprint 243）已依實作改寫，並納入 `ApiRouteDocDriftTest`：Controller 新增、刪除或改名路由而沒更新規格，測試會失敗。M05／M06 的回應形狀、狀態碼、錯誤碼與權限另有真實服務的契約測試（`OrderApiRealStackIntegrationTest`、`BookingApiRealStackIntegrationTest`）。其他模組將依序納入；納入前仍以規格文件與程式碼互相對照為準。

---

## 📋 API 索引總覽

### 認證模組 (M03)

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M03-001 | `/api/v2/auth/register` | POST | 會員註冊 | Guest | Phase 1 |
| API-M03-002 | `/api/v2/auth/login` | POST | 會員登入 | Guest | Phase 1 |
| API-M03-003 | `/api/v2/auth/refresh` | POST | 換發 Token（輪替；重放偵測） | Guest+ | Phase 1 |
| API-M03-004 | `/api/v2/auth/logout` | POST | 會員登出 | Buyer+ | Phase 1 |
| API-M03-005 | `/api/v2/auth/me` | GET | 取得當前用戶資訊 | Buyer+ | Phase 1 |
| API-M03-006 | `/api/v2/auth/password/forgot` | POST | 申請密碼重設連結（Sprint 204） | Guest | Phase 1 |
| API-M03-007 | `/api/v2/auth/password/reset` | POST | 以連結重設密碼（Sprint 204） | Guest | Phase 1 |
| API-M03-008 | `/api/v2/auth/email/verify` | POST | 以連結完成 Email 驗證（Sprint 204） | Guest | Phase 1 |
| API-M03-009 | `/api/v2/auth/email/verify/send` | POST | 重寄驗證信（Sprint 204） | Buyer+ | Phase 1 |
| API-M03-010 | `/api/v2/auth/oauth/login` | POST | OAuth 登入／註冊（Sprint 241 補記） | Guest | Phase 1 |
| API-M03-011 | `/api/v2/auth/oauth/link` | POST | 連結 OAuth 帳號（Sprint 241 補記） | Buyer+ | Phase 1 |
| API-M03-012 | `/api/v2/auth/me/data-export` | GET | 會員資料匯出（Sprint 241 補記） | Buyer+ | Phase 1 |
| API-M03-013 | `/api/v2/auth/me` | DELETE | 會員自助刪除帳戶（Sprint 241 補記） | Buyer | Phase 1 |

### 購物車 (M04)

規格：[API_M04_Cart.md](./API_M04_Cart.md)（v2.0，Sprint 241）。成功狀態碼一律 `200`；權限 `cart:read`／`cart:update`／`cart:delete`（只有 Buyer 持有）。

| 端點 | 方法 | 說明 | 權限 |
|------|------|------|------|
| `/api/v2/cart` | GET | 取得購物車（依店鋪分組、運費、優惠券折扣） | `cart:read` |
| `/api/v2/cart/count` | GET | 取得總件數 | `cart:read` |
| `/api/v2/cart/items` | POST | 加入購物車 | `cart:update` |
| `/api/v2/cart/items/{cartItemKey}` | PUT | 更新項目數量 | `cart:update` |
| `/api/v2/cart/items/{cartItemKey}` | DELETE | 移除項目 | `cart:update` |
| `/api/v2/cart` | DELETE | 清空購物車 | `cart:delete` |
| `/api/v2/cart/apply-promo` | POST | 在某家店鋪套用優惠券 | `cart:update` |
| `/api/v2/cart/promo` | DELETE | 移除某家店鋪的優惠券 | `cart:update` |
| `/api/v2/cart/validate-promo` | GET | 驗證優惠券（不套用） | `cart:read` |

### 商品中心 (M01)

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M01-001 | `/api/v2/listings` | GET | 商品/房源列表 | Guest+ | Phase 1 |
| API-M01-002 | `/api/v2/listings/:id` | GET | 商品/房源詳情 | Guest+ | Phase 1 |
| API-M01-003 | `/api/v2/listings/search` | GET | 關鍵字搜尋 | Guest+ | Phase 1 |
| API-M01-004 | `/api/v2/categories` | GET | 分類列表 | Guest+ | Phase 1 |
| API-M01-005 | `/api/v2/dashboard/listings` | GET | 店鋪商品列表 | Seller+ | Phase 1 |
| API-M01-006 | `/api/v2/dashboard/listings` | POST | 建立商品 | Seller+ | Phase 1 |
| API-M01-007 | `/api/v2/dashboard/listings/:id` | PUT | 更新商品 | Seller+ | Phase 1 |
| API-M01-008 | `/api/v2/dashboard/listings/:id` | DELETE | 下架商品 | Seller+ | Phase 1 |
| API-M01-009 | `/api/v2/dashboard/listings/:id/publish` | PUT | 發布商品 | Seller+ | Phase 1 |

### 房源中心 (M02)

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M02-001 | `/api/v2/listings?type=ROOM` | GET | 房源列表 | Guest+ | Phase 1 |
| API-M02-002 | `/api/v2/listings/:id` | GET | 房源詳情 | Guest+ | Phase 1 |
| API-M02-003 | `/api/v2/listings/:id/calendar` | GET | 日曆與價格查詢 | Guest+ | Phase 1 |
| API-M02-004 | `/api/v2/dashboard/listings` | GET | 店鋪房源列表 | Host+ | Phase 1 |
| API-M02-005 | `/api/v2/dashboard/listings` | POST | 建立房源 | Host+ | Phase 1 |
| API-M02-006 | `/api/v2/dashboard/listings/:id` | PUT | 更新房源 | Host+ | Phase 1 |
| API-M02-007 | `/api/v2/dashboard/listings/:id/status` | PUT | 更新房源狀態 | Host+ | Phase 1 |

### 訂單履約 (M05)

規格：[API_M05_Order.md](./api/API_M05_Order.md)（v2.0，Sprint 243）。訂單由**購物車**結帳產生；一次結一家店鋪；店鋪暫停營業不能下單、也不能付款（`E-2010`）。

| 端點 | 方法 | 說明 | 權限 |
|------|------|------|------|
| `/api/v2/orders` | POST | 從購物車建立訂單（可帶 `Idempotency-Key`） | `order:create` |
| `/api/v2/orders` | GET | 買家訂單列表（分頁） | `order:read` |
| `/api/v2/orders/{orderId}` | GET | 訂單詳情 | `order:read` |
| `/api/v2/orders/tenant` | GET | 店鋪收到的訂單列表 | `order:read` |
| `/api/v2/orders/{orderId}/status` | PATCH | 更新訂單狀態 | `order:update` |
| `/api/v2/orders/{orderId}/cancel` | POST | 取消訂單（本人或管理員） | 已登入 |
| `/api/v2/orders/{orderId}/logs` | GET | 訂單狀態日誌 | 已登入 |
| `/api/v2/checkout/mixed` | POST | 合併結帳（訂單＋訂房） | `order:create` ＋ `booking:create` |
| `/api/v2/orders/{orderId}/payment` | GET | 付款狀態 | `order:read` |
| `/api/v2/orders/{orderId}/pay` | POST | Mock 付款成功 | `order:create` 或 `order:update` |
| `/api/v2/orders/{orderId}/pay/fail` | POST | Mock 付款失敗 | `order:create` 或 `order:update` |
| `/api/v2/orders/{orderId}/refund` | POST | 退款（實際上只有管理員） | `order:update` ＋ 本人或管理員 |
| `/api/v2/orders/{orderId}/pay/checkout` | POST | 發起 Stripe Checkout | `order:create` 或 `order:update` |
| `/api/v2/orders/{orderId}/pay/checkout/return` | GET | Stripe 回跳確認 | `order:read` |
| `/api/v2/orders/bookings/{bookingId}/payment` | GET | 訂房付款狀態（屬 M06） | `booking:read` |

### 預訂系統 (M06)

規格：[API_M06_Booking.md](./API_M06_Booking.md)（v2.0，Sprint 243）。日曆以「晚」為單位；退款依 PRD Q14；店鋪暫停營業不能訂房、也不能付款（`E-2010`）。

| 端點 | 方法 | 說明 | 權限 |
|------|------|------|------|
| `/api/v2/bookings/availability` | GET | 檢查日期區間可用性與總價 | `booking:read` |
| `/api/v2/bookings/calendar` | GET | 房源日曆（有記錄的日期） | `booking:read` |
| `/api/v2/bookings` | POST | 建立訂房（可帶 `Idempotency-Key`） | `booking:create` |
| `/api/v2/bookings` | GET | 買家訂房列表（分頁） | `booking:read` |
| `/api/v2/bookings/{bookingId}` | GET | 訂房詳情 | `booking:read` |
| `/api/v2/bookings/{bookingId}` | PUT | 更新訂房 | `booking:update` |
| `/api/v2/bookings/{bookingId}/cancel` | POST | 取消訂房（含退款決定） | `booking:cancel` |
| `/api/v2/bookings/{bookingId}/pay` | POST | Mock 付款成功 | `booking:create` 或 `booking:update` |
| `/api/v2/bookings/{bookingId}/pay/checkout` | POST | 發起 Stripe Checkout | `booking:create` 或 `booking:update` |
| `/api/v2/bookings/{bookingId}/pay/checkout/return` | GET | Stripe 回跳確認 | `booking:read` |
| `/api/v2/dashboard/bookings` | GET | 店鋪收到的訂房列表 | `booking:read` |

### 動態定價 (M12)

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M12-001 | `/api/v2/listings/:id/price` | GET | 取得動態價格 | Guest+ | Phase 1 |
| API-M12-002 | `/api/v2/dashboard/pricing/rules` | GET | 定價規則列表 | StoreOwner+ | Phase 1 |
| API-M12-003 | `/api/v2/dashboard/pricing/rules` | POST | 建立定價規則 | StoreOwner+ | Phase 1 |
| API-M12-004 | `/api/v2/dashboard/pricing/rules/:id` | PUT | 更新定價規則 | StoreOwner+ | Phase 1 |
| API-M12-005 | `/api/v2/dashboard/pricing/rules/:id` | DELETE | 刪除定價規則 | StoreOwner+ | Phase 1 |
| API-M12-006 | `/api/v2/dashboard/pricing/rules/:id/override` | POST | 手動覆蓋價格 | StoreOwner+ | Phase 1 |

### 租戶管理 (M17)

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M17-001 | `/api/v2/tenants/apply` | POST | 申請開店 | Guest | Phase 1 |
| API-M17-002 | `/api/v2/tenants/my` | GET | 取得我的店鋪列表 | StoreOwner | Phase 1 |
| API-M17-003 | `/api/v2/tenants/:id` | GET | 店鋪詳情 | Guest+ | Phase 1 |
| API-M17-004 | `/api/v2/tenants/:id` | PUT | 更新店鋪資訊 | StoreOwner | Phase 1 |
| API-M17-005 | `/api/v2/dashboard/tenants/features` | GET | 取得功能開關狀態 | StoreOwner+ | Phase 1 |
| API-M17-006 | `/api/v2/dashboard/tenants/features/:feature` | PUT | 更新功能開關 | StoreOwner | Phase 1 |
| API-M17-007 | `/api/v2/admin/tenants` | GET | 平台店鋪列表 | SUPER_ADMIN | Phase 1 |
| API-M17-APP-001 | `/api/v2/admin/tenant-applications` | GET | **待審核開店申請列表**（`status = PENDING`） | SUPER_ADMIN | Phase 1 |
| API-M17-APP-002 | `/api/v2/admin/tenant-applications/:applicationId/approve` | POST | **核准開店申請**（建立 Tenant + 授予 StoreOwner） | SUPER_ADMIN | Phase 1 |
| API-M17-APP-003 | `/api/v2/admin/tenant-applications/:applicationId/reject` | POST | **駁回開店申請**（需帶 `reason`） | SUPER_ADMIN | Phase 1 |
| ~~API-M17-008~~ | `/api/v2/admin/tenants/:id/approve` | POST | ⚠️ 舊流程，生產不可達（見下方說明） | SUPER_ADMIN | Phase 1 |
| ~~API-M17-009~~ | `/api/v2/admin/tenants/:id/reject` | POST | ⚠️ 舊流程，生產不可達（見下方說明） | SUPER_ADMIN | Phase 1 |

> **⚠️ 開店審核的入口是 `API-M17-APP-001~003`**（PRD §4.3 / §9.10.2、FRD BR-M17-001）
>
> `API-M17-008/009` 操作的是既有 `Tenant` 且要求 `status == PENDING_REVIEW`，而生產環境沒有任何路徑會讓
> `Tenant` 進入該狀態（`Tenant` 只在核准申請時建立，且建立即 `ACTIVE`）。這兩個端點仍存在於後端、也仍被
> Sprint 03/03-A 的歷史測試案例引用，故保留編號不刪除，但**已無前端引用，新功能不得使用**。

### 評價系統 (M08) - 🆕 Sprint 15-16 新增

| API ID | 端點 | 方法 | 說明 | 角色 | Sprint |
|--------|------|------|------|------|--------|
| API-M08-001 | `/v2/reviews/{id}/replies` | POST | 商家回覆評價 | StoreOwner | Sprint 15 |
| API-M08-002 | `/v2/reviews/{id}/replies` | GET | 取得評價回覆列表 | 任意已登入 | Sprint 16 (US-001) |
| API-M08-003 | `/v2/reviews/{id}/handle` | PUT | 標記評價為已/未處理 | StoreOwner | Sprint 15 |
| API-M08-004 | `/v2/reviews/managed` | GET | 取得待處理評價列表 | StoreOwner | Sprint 15 |
| **🆕 API-M08-005** | `/v2/reviews/{id}/images` | POST | 新增評價圖片（總數不超過 9 張） | 評價本人 | Sprint 16 (US-006) |
| **🆕 API-M08-006** | `/v2/reviews/{id}/images/{imageIndex}` | DELETE | 刪除評價單張圖片 | 評價本人 | Sprint 16 (US-006) |
| **🆕 API-M08-007** | `/v2/reviews/{id}/images/order` | PUT | 重新排序評價圖片 | 評價本人 | Sprint 16 (US-006) |
| API-M08-101 | `/v2/booking-reviews/{id}/reply` | POST | 房東回覆預訂評價 | Host | Sprint 15 |

### 結算系統 (M07) - 🆕 Sprint 15-16 新增

| API ID | 端點 | 方法 | 說明 | 角色 | Sprint |
|--------|------|------|------|------|--------|
| **🆕 API-M07-S-001** | `/v2/settlements` | GET | 商家查詢結算單列表 | StoreOwner | Sprint 15 |
| **🆕 API-M07-S-002** | `/v2/settlements/{id}` | GET | 商家查詢結算單詳情 | StoreOwner | Sprint 15 |
| **🆕 API-M07-S-003** | `/v2/settlements/{id}/submit` | PUT | 商家提交結算單審核 | StoreOwner | Sprint 15 |
| **🆕 API-M07-S-004** | `/v2/admin/settlements/pending` | GET | Admin 取得待審核結算單列表 | Admin | Sprint 15 |
| **🆕 API-M07-S-005** | `/v2/admin/settlements/{id}/approve` | PUT | Admin 批准結算單 | Admin | Sprint 15 |
| **🆕 API-M07-S-006** | `/v2/admin/settlements/{id}/reject` | PUT | Admin 駁回結算單 | Admin | Sprint 15 |

---

## 🔗 詳細 API 規格連結

### M01 商品中心
- [API_M01_Product_Center.md](./api/API_M01_Product_Center.md)

### M02 房源中心
- [API_M02_Listing_Center.md](./api/API_M02_Listing_Center.md)

### M03 認證系統
- [API_M03_Auth.md](./api/API_M03_Auth.md)

### M04 購物車
- [API_M04_Cart.md](./API_M04_Cart.md)

### M05 訂單履約
- [API_M05_Order.md](./api/API_M05_Order.md)

### M06 預訂系統
- [API_M06_Booking.md](./API_M06_Booking.md)

### M12 動態定價
- [API_M12_Dynamic_Pricing.md](./api/API_M12_Dynamic_Pricing.md)

### M17 租戶管理
- [API_M17_Tenant.md](./api/API_M17_Tenant.md)

### M07 結算系統 (🆕 Sprint 15-16)
- `API_M07_Settlement.md` *(Sprint 16 規劃新增，連結待建立)*

### M08 評價系統 (🆕 Sprint 15-16)
- `API_M08_Review.md` *(Sprint 16 規劃新增，連結待建立)*
- 包含：評價回覆、標記、圖片管理 API

---

## 📝 通用錯誤碼

| 錯誤碼 | 說明 | HTTP 狀態 |
|--------|------|-----------|
| E-1001 | INVALID_TOKEN | 401 |
| E-1002 | EXPIRED_TOKEN | 401 |
| E-2003 | TENANT_CONTEXT_AMBIGUOUS | 403 |
| E-2020 | FEATURE_DISABLED_FOR_TENANT | 403 |
| E-3001 | EMAIL_ALREADY_EXISTS | 409 |
| E-3002 | INVALID_CREDENTIALS | 401 |
| E-4001 | VALIDATION_ERROR | 400 |
| E-5001 | SEARCH_TIMEOUT | 408 |
| E-7001 | INTERNAL_ERROR | 500 |

### 🆕 Sprint 16 新增錯誤碼

| 錯誤碼 | 模組 | 說明 | HTTP 狀態 |
|--------|------|------|-----------|
| E-1086 | M08 評價 | 評價已有回覆（重複回覆） | 409 Conflict |
| E-1088 | M08 評價 | 評價圖片數量超限（最多 9 張） | 400 Bad Request |
| E-1089 | M08 評價 | 評價圖片無效（媒體不存在） | 400 Bad Request |
| E-1090 | M08 評價 | 評價圖片不存在（index 越界） | 404 Not Found |
| E-1091 | M08 評價 | 非評價本人操作 | 403 Forbidden |

---

**文件結束**
