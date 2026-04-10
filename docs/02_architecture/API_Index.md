# API Index / API 規格索引

> **文檔狀態**: Draft
> **版本**: v1.0
> **建立日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)

---

## 📋 API 索引總覽

### 認證模組 (M03)

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M03-001 | `/api/v2/auth/register` | POST | 會員註冊 | Guest | Phase 1 |
| API-M03-002 | `/api/v2/auth/login` | POST | 會員登入 | Guest | Phase 1 |
| API-M03-003 | `/api/v2/auth/refresh` | POST | 刷新 Access Token | Guest+ | Phase 1 |
| API-M03-004 | `/api/v2/auth/logout` | POST | 會員登出 | Buyer+ | Phase 1 |
| API-M03-005 | `/api/v2/auth/me` | GET | 取得當前用戶資訊 | Buyer+ | Phase 1 |

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

| API ID | 端點 | 方法 | 說明 | 角色 | Phase |
|--------|------|------|------|------|-------|
| API-M05-001 | `/api/v2/orders` | POST | 建立訂單 | Buyer+ | Phase 1 |
| API-M05-002 | `/api/v2/orders` | GET | 買家訂單列表 | Buyer+ | Phase 1 |
| API-M05-003 | `/api/v2/orders/:id` | GET | 訂單詳情 | Buyer+ | Phase 1 |
| API-M05-004 | `/api/v2/orders/:id/cancel` | POST | 取消訂單 | Buyer+ | Phase 1 |
| API-M05-005 | `/api/v2/dashboard/orders` | GET | 賣家訂單列表 | Seller+ | Phase 1 |
| API-M05-006 | `/api/v2/dashboard/orders/:id/status` | PUT | 更新訂單狀態 | Seller+ | Phase 1 |

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
| API-M17-002 | `/api/v2/tenants` | GET | 取得我的店鋪列表 | StoreOwner | Phase 1 |
| API-M17-003 | `/api/v2/tenants/:id` | GET | 店鋪詳情 | Guest+ | Phase 1 |
| API-M17-004 | `/api/v2/tenants/:id` | PUT | 更新店鋪資訊 | StoreOwner | Phase 1 |
| API-M17-005 | `/api/v2/dashboard/tenants/features` | GET | 取得功能開關狀態 | StoreOwner+ | Phase 1 |
| API-M17-006 | `/api/v2/dashboard/tenants/features/:feature` | PUT | 更新功能開關 | StoreOwner | Phase 1 |
| API-M17-007 | `/api/v2/admin/tenants` | GET | 平台店鋪列表 | Admin | Phase 1 |
| API-M17-008 | `/api/v2/admin/tenants/:id/approve` | POST | 審核通過店鋪 | Admin | Phase 1 |
| API-M17-009 | `/api/v2/admin/tenants/:id/reject` | POST | 駁回店鋪申請 | Admin | Phase 1 |

---

## 🔗 詳細 API 規格連結

### M01 商品中心
- [API_M01_Product_Center.md](./api/API_M01_Product_Center.md)

### M02 房源中心
- [API_M02_Listing_Center.md](./api/API_M02_Listing_Center.md)

### M03 認證系統
- [API_M03_Auth.md](./api/API_M03_Auth.md)

### M05 訂單履約
- [API_M05_Order.md](./api/API_M05_Order.md)

### M12 動態定價
- [API_M12_Dynamic_Pricing.md](./api/API_M12_Dynamic_Pricing.md)

### M17 租戶管理
- [API_M17_Tenant.md](./api/API_M17_Tenant.md)

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

---

**文件結束**
