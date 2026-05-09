# Sprint 6 計劃 / Sprint 6 Plan

> **Sprint 編號**: Sprint 6
> **期間**: 2026-05-11 ~ 2026-05-24 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.7
> **建立日期**: 2026-04-29
> **更新日期**: 2026-04-30 (v1.7 - Section 5.3 任務清單與 Section 4/8 一致性修正)
> **基於**: Sprint 5 完成 + Sprint 5 Review 建議 + Phase 1 規劃 + QA 審查修正

---

## 🔴 人機協作確認點結果

### Sprint 5 回顧摘要
**已完成**:
- FE-M17-005 功能開關頁面 (2 SP) ✅
- US-M17-009 Admin Feature Toggle 更新 (1 SP) ✅
- M04 Frontend 購物車頁面 (3 SP) ✅
- Booking E2E 測試 (延後至 Sprint 6) - 需要 ROOM Listing 環境

**Sprint 5 延後至 Sprint 6**:
- DEF-001: Booking E2E 完整預訂流程測試 (3 SP)

### Sprint 6 準備確認
**選擇**: ✅ Sprint 6 專注於 M01/M02 Listing 核心 + M12 動態定價 + 完整測試覆蓋 + DEF-001 環境建設

### QA 審查修正 (v1.1)
根據 QA (Quincy) 審查建議，納入以下修正：
- ✅ 新增 M01/M02 Backend API 測試 (6 SP)
- ✅ 新增 M12 UT/IT 測試 (3 SP)
- ✅ 新增 Feature Toggle 邏輯測試 (1 SP)
- ✅ 明確 Booking E2E AT 案例對應
- ✅ 更新 DoD 加入測試通過率要求

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 6 |
| **開始日期** | 2026-05-11 |
| **結束日期** | 2026-05-24 |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 24 SP |
| **Buffer** | 6 SP (✅ 充足安全範圍) |
| **Buffer 配置** | 測試環境 2 + Frontend 整合 2 + Bug 應急 2 |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M01/M02 Listing 核心功能 + M12 動態定價實作 + 完整測試覆蓋 + 建立 Booking E2E 測試環境，形成完整的商品/房源管理體驗。

### 具體目標

#### DEF-001 環境建設 (4 SP)
1. **統一 Listing 建立端點** - POST /v2/dashboard/listings
2. **Feature Toggle 檢查實作** - BOOKING_ENABLED / RETAIL_ENABLED
3. **測試資料建立** - STORE_OWNER 測試帳號
4. **Feature Toggle 邏輯測試** - 驗證 Toggle 正確生效
5. **Booking E2E 測試** - 執行 AT-M06-* P0 案例 (8 個)

#### M01/M02 Frontend 核心 (6 SP)
6. **商品管理頁面** - /dashboard/products
7. **房源管理頁面** - /dashboard/rooms
8. **建立/編輯表單 UI** - Listing CRUD 表單

#### M01/M02 Backend API 測試 (6 SP)
9. **M01 Backend API 測試** - IT + API E2E (P0 等級)
10. **M02 Backend API 測試** - IT + API E2E (P0 等級)

#### M12 動態定價 (8 SP)
11. **動態價格計算端點** - GET /api/v2/listings/:id/price
12. **API 路徑重構** - /v2/pricing/* → /api/v2/dashboard/pricing/*
13. **手動覆蓋端點** - POST .../rules/:id/override
14. **Frontend 定價頁面** - /dashboard/pricing/rules
15. **PricingService 單元測試** - 9 個 UT 案例
16. **Pricing 整合測試** - 5 個 IT 案例

---

## 3. User Stories

### 優先級定義
- **P0 (Critical)**: 此 Sprint 必要完成，系統核心功能，延期將阻礙主要流程
- **P1 (High)**: 此 Sprint 必要完成，重要功能但有 workaround

> **此 Sprint 所有 Stories 皆為必要完成，P0/P1 不影響交付範圍，僅作為開發排序依據。**

### 3.1 DEF-001 環境建設 Stories

| ID | 標題 | SP | 優先級 | Business Value | 對應 TC | 狀態 |
|----|------|-----|--------|----------------|---------|------|
| US-DEF-001 | 統一 Listing 建立端點 | 1 | P0 | 支援 ROOM/PRODUCT 統一建立 | - | 待實現 |
| US-DEF-002 | Feature Toggle 檢查 | 1 | P0 | 確保功能開關在建立商品/房源時生效 | - | 待實現 |
| US-DEF-003 | 測試資料建立 | 1 | P0 | 提供 Booking E2E 測試所需的 STORE_OWNER 帳號 | - | 待實現 |
| US-DEF-004 | Feature Toggle 邏輯測試 | 1 | P0 | 驗證 BOOKING_ENABLED/RETAIL_ENABLED 正確生效 | IT-M02-002, IT-M12-002 | 待實現 |
| AT-M06-001-P0-01 | Booking E2E: 建立預訂成功 | 0 | P0 | 買家可成功建立房源預訂 | AT-M06-001-P0-01 | 待執行 |
| AT-M06-001-P0-05 | Booking E2E: 日期衝突 (完整重疊) | 0 | P0 | 相同日期區間回傳 E-4001 | AT-M06-001-P0-05 | 待執行 |
| AT-M06-001-P0-06 | Booking E2E: 日期衝突 (部分重疊) | 0 | P0 | 部分重疊回傳 E-4001 | AT-M06-001-P0-06 | 待執行 |
| AT-M06-001-P0-07 | Booking E2E: 日期衝突 (包含) | 0 | P0 | 第二預訂包含第一預訂 | AT-M06-001-P0-07 | 待執行 |
| AT-M06-001-P0-09 | Booking E2E: 無效日期範圍 | 0 | P0 | check-out = check-in 回傳錯誤 | AT-M06-001-P0-09 | 待執行 |
| AT-M06-003-P0-01 | Booking E2E: 取消預訂成功 | 0 | P0 | 買家可成功取消未入住預訂 | AT-M06-003-P0-01 | 待執行 |
| AT-M06-ISOLATION-01 | Booking E2E: 多租戶隔離 | 0 | P0 | 買家 A 看不到買家 B 的預訂 | AT-M06-ISOLATION-01 | 待執行 |

**DEF-001 環境建設小計**: 4 SP (測試案例執行不計 SP)

### 3.2 M01/M02 Frontend Stories

| ID | 標題 | SP | 優先級 | Business Value | 狀態 |
|----|------|-----|--------|----------------|------|
| FE-M01-001 | 商品列表頁面 | 2 | P0 | StoreOwner 可查看商品列表 | 待實現 |
| FE-M01-002 | 商品建立/編輯表單 | 2 | P0 | StoreOwner 可建立和編輯商品 | 待實現 |
| FE-M02-001 | 房源列表頁面 | 1 | P1 | StoreOwner 可查看房源列表 | 待實現 |
| FE-M02-002 | 房源建立/編輯表單 | 1 | P1 | StoreOwner 可建立和編輯房源 | 待實現 |

**M01/M02 Frontend 小計**: 6 SP

### 3.3 M01/M02 Backend API 測試 Stories

| ID | 標題 | SP | 優先級 | 對應 TC | 狀態 |
|----|------|-----|--------|---------|------|
| BE-M01-001 | M01 Backend API IT 測試 | 2 | P0 | IT-M01-001~005 (5 個 IT) | 待實現 |
| BE-M01-002 | M01 Backend API E2E 測試 | 1 | P0 | API-M01-001~007 (7 個 API E2E) | 待實現 |
| BE-M02-001 | M02 Backend API IT 測試 | 2 | P0 | IT-M02-001,003~008 (7 個 IT) | 待實現 |
| BE-M02-002 | M02 Backend API E2E 測試 | 1 | P0 | API-M02-001~006 (6 個 API E2E) | 待實現 |

**M01/M02 Backend API 測試小計**: 6 SP

### 3.4 M12 動態定價 Stories

| ID | 標題 | SP | 優先級 | 對應 TC | 狀態 |
|----|------|-----|--------|---------|------|
| BE-M12-001 | 動態價格計算端點 | 2 | P0 | API-M12-001~003 | 待實現 |
| BE-M12-002 | API 路徑重構 | 1 | P0 | - | 待實現 |
| BE-M12-003 | 手動覆蓋價格端點 | 1 | P1 | API-M12-008 | 待實現 |
| FE-M12-001 | 定價規則管理頁面 | 1 | P1 | - | 待實現 |
| BE-M12-004 | PricingService 單元測試 | 2 | P0 | UT-M12-001~009 (9 UT) | 待實現 |
| BE-M12-005 | Pricing 整合測試 | 1 | P0 | IT-M12-001~008 (8 個 IT) | 待實現 |

**M12 動態定價小計**: 8 SP

---

## 4. Sprint 容量規劃 (v1.7 修正版)

| 類別 | 規劃 SP | 說明 | 對應 TC/AT 數量 |
|------|---------|------|-----------------|
| DEF-001 環境建設 | 4 SP | 端點 + Feature Toggle + 測試資料 + Toggle 測試 | 13 個 AT |
| M01/M02 Frontend | 6 SP | 商品/房源管理頁面 | - |
| M01/M02 Backend API 測試 | 6 SP | IT + API E2E (P0 等級) | 25 個 TC |
| M12 動態定價 | 8 SP | Backend 端點 + Frontend + UT + IT | 20 個 TC |
| **規劃 SP 合計** | **24 SP** | | **56 個 TC/AT** |
| Buffer | 6 SP | 測試環境 2 + Frontend 整合 2 + Bug 應急 2 | |
| **Sprint 容量** | **30 SP** | | |

> **說明**: 58 個 TC/AT = 13 AT + 25 TC (M01/M02) + 20 TC (M12)，不包含延後的 UT 和 API E2E。
> **v1.7 修正**: M01 API E2E 從 3→7, M02 IT 從 5→7, M02 API E2E 從 3→6，確保 Section 4/5.3/8 三處一致 (共 25 TC)

---

## 5. 詳細工作項目

### 5.1 DEF-001 環境建設任務

#### T-DEF-001-01: 統一 Listing 建立端點
- **負責**: Backend
- **輸入**: Sprint 5 Review, API_M02_Listing_Center.md
- **輸出**: POST /v2/dashboard/listings 端點
- **任務**:
  1. 在 ListingController 或新建 DashboardListingController 新增端點
  2. 根據 listingType 參數分別呼叫 ProductService 或 RoomService
  3. 實作 Request DTO: CreateListingRequest (含 listingType, name, price, categoryId, roomSpecificFields 等)
  4. 新增 API 文件: API_Listing_Unified_Create.md

#### T-DEF-001-02: Feature Toggle 檢查實作
- **負責**: Backend
- **輸入**: PRD, TenantService.java
- **輸出**: Feature Toggle 檢查邏輯
- **任務**:
  1. 在 RoomService.createRoom() 新增 BOOKING_ENABLED 檢查
  2. 在 ProductService.createProduct() 新增 RETAIL_ENABLED 檢查
  3. 禁用時拋出 BusinessException(ErrorCode.E_2020)
  4. 撰寫單元測試

#### T-DEF-001-03: 測試資料建立
- **負責**: Backend
- **輸入**: Phase 1 Execution Plan
- **輸出**: 測試資料初始化腳本
- **任務**:
  1. 建立測試 Tenant (nextkey-test-tenant)
  2. 建立測試 User (test-store-owner@nextkeytest.com)
  3. 建立 TenantMember (STORE_OWNER role)
  4. 建立測試 Listing (啟用 BOOKING_ENABLED)
  5. 驗證 Booking E2E 環境就緒

#### T-DEF-001-04: Feature Toggle 邏輯測試
- **負責**: QA
- **輸入**: TC_M02_Room.md, TC_M12_Pricing.md
- **輸出**: Feature Toggle 測試報告
- **對應 TC**:
  - IT-M02-002: 房源上架-BOOKING_ENABLED未啟用 → 預期 403 FEATURE_DISABLED
  - IT-M12-002: 建立規則-DYNAMIC_PRICING_ENABLED未啟用 → 預期 403 FEATURE_DISABLED
- **任務**:
  1. 驗證 BOOKING_ENABLED=false 時無法建立 ROOM Listing
  2. 驗證 RETAIL_ENABLED=false 時無法建立 PRODUCT Listing
  3. 驗證 DYNAMIC_PRICING_ENABLED=false 時無法建立定價規則

#### T-DEF-001-05: Booking E2E 測試執行
- **負責**: QA
- **輸入**: TC_M06_Booking.md
- **輸出**: E2E 測試報告
- **對應 AT 案例** (共 13 個 P0):
  - AT-M06-001-P0-01: 建立預訂成功
  - AT-M06-001-P0-04: 預訂資訊完整性
  - AT-M06-001-P0-05: 日期衝突 (完整重疊)
  - AT-M06-001-P0-06: 日期衝突 (部分重疊)
  - AT-M06-001-P0-07: 日期衝突 (包含)
  - AT-M06-001-P0-09: 無效日期範圍 (連續日期不衝突)
  - AT-M06-001-NFR-01: 並發預訂防超賣
  - AT-M06-002-P0-03: 其他預訂無法重疊鎖定日期
  - AT-M06-003-P0-01: 取消預訂成功
  - AT-M06-003-P0-03: 取消後日期格釋放
  - AT-M06-ISOLATION-01: 多租戶隔離 (買家 A 看不到買家 B 預訂)
  - AT-M06-ISOLATION-02: 多租戶隔離 (無法取消他人預訂)
  - AT-M06-NFR-01: Booking API 回應時間 < 300ms
- **任務**:
  1. 執行上述 AT 案例
  2. 記錄測試結果
  3. 更新 Deferred Items Tracker

### 5.2 M01/M02 Frontend 任務

#### T-M01-01: 商品列表頁面
- **負責**: Frontend
- **路徑**: /dashboard/products
- **任務**:
  1. 建立商品列表頁面元件
  2. 串接 GET /v2/products API
  3. 實作分頁、過濾、搜尋功能
  4. 實作刪除商品功能

#### T-M01-02: 商品建立/編輯表單
- **負責**: Frontend
- **路徑**: /dashboard/products/new, /dashboard/products/[id]/edit
- **任務**:
  1. 建立商品表單元件
  2. 串接 POST/PUT /v2/products API
  3. 實作表單驗證
  4. 處理 RETAIL_ENABLED disabled 錯誤訊息

#### T-M02-01: 房源列表頁面
- **負責**: Frontend
- **路徑**: /dashboard/rooms
- **任務**:
  1. 建立房源列表頁面元件
  2. 串接 GET /v2/rooms API
  3. 實作分頁、過濾功能
  4. 實作刪除房源功能

#### T-M02-02: 房源建立/編輯表單
- **負責**: Frontend
- **路徑**: /dashboard/rooms/new, /dashboard/rooms/[id]/edit
- **任務**:
  1. 建立房源表單元件 (含 roomType, maxOccupancy, amenities)
  2. 串接 POST/PUT /v2/rooms API
  3. 實作表單驗證
  4. 處理 BOOKING_ENABLED disabled 錯誤訊息

### 5.3 M01/M02 Backend API 測試任務

#### T-M01-03: M01 Backend API IT 測試
- **負責**: Backend/QA
- **輸入**: TC_M01_Product.md
- **輸出**: M01 IT 測試報告
- **對應 TC** (共 5 個 IT):
  - IT-M01-001: 商品上架-從草稿發布 (P0)
  - IT-M01-002: 商品上架-缺少必填欄位 (P0)
  - IT-M01-003: 商品上架-basePrice必須大於0 (P1)
  - IT-M01-004: 商品編輯-更新標題和價格 (P1)
  - IT-M01-005: 商品下架-改為INACTIVE (P1)
- **任務**:
  1. 實作 ProductService IT
  2. 執行 IT-M01-001 ~ IT-M01-005
  3. 記錄測試結果

#### T-M01-04: M01 Backend API E2E 測試
- **負責**: QA
- **輸入**: TC_M01_Product.md
- **輸出**: M01 API E2E 測試報告
- **對應 TC** (共 7 個 API E2E):
  - API-M01-001: GET /api/v2/listings-商品列表 (P0)
  - API-M01-002: GET /api/v2/listings/:id-商品詳情 (P0)
  - API-M01-003: GET /api/v2/listings/:id-不存在商品 (P1)
  - API-M01-004: GET /api/v2/listings/search-關鍵字搜尋 (P0)
  - API-M01-005: GET /api/v2/listings/search-空關鍵字 (P1)
  - API-M01-006: GET /api/v2/categories-分類列表 (P1)
  - API-M01-007: POST /api/v2/dashboard/listings-建立商品 (P0)
- **任務**:
  1. 執行 API-M01-001 ~ API-M01-007
  2. 記錄測試結果

#### T-M02-03: M02 Backend API IT 測試
- **負責**: Backend/QA
- **輸入**: TC_M02_Room.md
- **輸出**: M02 IT 測試報告
- **對應 TC** (共 7 個 IT，IT-M02-002 已移至 T-DEF-001-04):
  - IT-M02-001: 房源上架-成功 (P0)
  - IT-M02-003: 房源編輯-更新資訊 (P1)
  - IT-M02-004: 房源下架-改為INACTIVE (P1)
  - IT-M02-005: 日曆查詢-單日可用 (P0)
  - IT-M02-006: 日曆查詢-多日 (P1)
  - IT-M02-007: 預訂衝突-Redis鎖防範雙重預訂 (P0)
  - IT-M02-008: 預訂衝突-跨租戶隔離 (P1)
- **任務**:
  1. 實作 RoomService IT
  2. 執行 IT-M02-001, IT-M02-003 ~ IT-M02-008
  3. 記錄測試結果

#### T-M02-04: M02 Backend API E2E 測試
- **負責**: QA
- **輸入**: TC_M02_Room.md
- **輸出**: M02 API E2E 測試報告
- **對應 TC** (共 6 個 API E2E):
  - API-M02-001: GET /api/v2/listings?type=ROOM-房源列表 (P0)
  - API-M02-002: GET /api/v2/listings/:id-房源詳情 (P0)
  - API-M02-003: GET /api/v2/listings/:id-不存在 (P1)
  - API-M02-004: GET /api/v2/listings/:id/calendar-成功 (P0)
  - API-M02-005: GET /api/v2/listings/:id/calendar-含已預訂日期 (P0)
  - API-M02-006: GET /api/v2/listings/:id/calendar-日期範圍無效 (P1)
- **任務**:
  1. 執行 API-M02-001 ~ API-M02-006
  2. 記錄測試結果

### 5.4 M12 動態定價任務

#### T-M12-01: 動態價格計算端點
- **負責**: Backend
- **端點**: GET /api/v2/listings/:id/price?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD
- **任務**:
  1. 新增 PricingController 端點
  2. 實作動態價格計算邏輯 (平日/週末/旺季/早鳥/長住)
  3. 考量 MANUAL_OVERRIDE 最高優先級
  4. 對應 TC: API-M12-001, API-M12-002, API-M12-003

#### T-M12-02: API 路徑重構
- **負責**: Backend
- **任務**:
  1. 將 /v2/pricing/* 重構為 /api/v2/dashboard/pricing/*
  2. 更新 PricingController @RequestMapping
  3. 更新 PricingService 業務邏輯 (如有需要)
  4. 更新 API 文件

#### T-M12-03: 手動覆蓋價格端點
- **負責**: Backend
- **端點**: POST /api/v2/dashboard/pricing/rules/:id/override
- **任務**:
  1. 新增 override 端點
  2. 實作日期範圍價格覆蓋邏輯
  3. 確保 BOOKED 日期不可覆蓋
  4. 對應 TC: API-M12-008

#### T-M12-04: 定價規則管理頁面
- **負責**: Frontend
- **路徑**: /dashboard/pricing/rules
- **任務**:
  1. 建立定價規則列表頁面
  2. 串接 GET/POST/PUT/DELETE /api/v2/dashboard/pricing/rules
  3. 建立定價規則建立/編輯表單
  4. 實作定價日曆預覽元件 (未來 90 天)

#### T-M12-05: PricingService 單元測試
- **負責**: Backend
- **輸入**: TC_M12_Pricing.md
- **輸出**: PricingService UT 報告
- **對應 TC** (共 9 個 UT):
  - UT-M12-001: 價格計算-平日無折扣 (P0)
  - UT-M12-002: 價格計算-週末加成 (P0)
  - UT-M12-003: 價格計算-節日加成 (P0)
  - UT-M12-004: 價格計算-早鳥折扣 (P0)
  - UT-M12-005: 價格計算-長住折扣7天 (P1)
  - UT-M12-006: 價格計算-長住折扣30天 (P1)
  - UT-M12-007: 價格計算-最後一刻折扣 (P2)
  - UT-M12-008: 優先級-高優先級覆蓋低優先級 (P0)
  - UT-M12-009: 優先級-同優先級時後建立覆蓋 (P1)
- **任務**:
  1. 撰寫 PricingServiceTest
  2. 執行 UT-M12-001 ~ UT-M12-009
  3. 目標: 100% 通過率 (9/9)

#### T-M12-06: Pricing 整合測試
- **負責**: Backend/QA
- **輸入**: TC_M12_Pricing.md
- **輸出**: Pricing IT 測試報告
- **對應 TC** (共 8 個 IT):
  - IT-M12-001: 建立規則-成功 (P0)
  - IT-M12-002: 建立規則-DYNAMIC_PRICING_ENABLED未啟用 (P0)
  - IT-M12-003: 更新規則-成功 (P1)
  - IT-M12-004: 刪除規則-成功 (P1)
  - IT-M12-005: 查詢規則列表-依房源篩選 (P1)
  - IT-M12-006: 價格計算-週末+早鳥 (P0)
  - IT-M12-007: 價格計算-長住折扣套用 (P0)
  - IT-M12-008: 手動覆蓋-優先於規則 (P0)
- **任務**:
  1. 實作 Pricing IT
  2. 執行 IT-M12-001 ~ IT-M12-008
  3. 記錄測試結果

---

## 6. 風險與對策

| 風險 | 等級 | 對策 |
|------|------|------|
| 測試環境不穩定 | 🟡 中 | Buffer 配置 2 SP 應急 |
| Frontend 表單複雜度過高 | 🟡 中 | 先實作 MVP 版本，表單驗證簡化 |
| M12 價格計算邏輯變更 | 🟢 低 | 已與 BA 確認需求，變更機率低 |
| API 路徑重構影響現有呼叫 | 🟡 中 | 安排在 Sprint 開始時執行，預留回滾時間 |
| 測試案例數量過多 (48 個 TC) | 🔴 高 | 優先執行 P0 等級，P1/P2 按時間分配 |

---

## 7. 依賴關係

### 外部依賴
- **測試資料**: 需要測試環境 (192.168.1.133) 可正常運作
- **API 文件**: API_M01_Product_Center.md, API_M02_Listing_Center.md, API_M12_Dynamic_Pricing.md
- **測試案例**: TC_M01_Product.md, TC_M02_Room.md, TC_M06_Booking.md, TC_M12_Pricing.md

### 內部依賴
- **T-DEF-001-01 → T-DEF-001-02**: 統一端點實作後才能測試 Feature Toggle
- **T-DEF-001-01 → T-M01-03/T-M02-03**: 統一端點實作後才能執行 Backend API IT/E2E 測試
- **T-DEF-001-03 → T-DEF-001-05**: 測試資料就緒後才能執行 Booking E2E
- **T-M12-01 → T-M12-04**: Backend 端點就緒後 Frontend 才能串接
- **T-M12-01/02/03 → T-M12-05/06**: Backend 重構完成後才能執行 UT/IT

---

## 8. 測試覆蓋矩陣 (v1.7 修正版)

| 模組 | 測試類型 | P0 數量 | P1 數量 | P2 數量 | 小計 | Sprint 6 執行 |
|------|----------|---------|---------|---------|------|---------------|
| M01 Backend | UT | 3 | 3 | 2 | 8 | ❌ 延後 |
| M01 Backend | IT | 2 | 2 | 1 | 5 | ✅ T-M01-03 |
| M01 Backend | API E2E | 4 | 3 | 0 | 7 | ✅ T-M01-04 |
| M02 Backend | UT | 3 | 2 | 2 | 7 | ❌ 延後 |
| M02 Backend | IT | 3 | 4 | 0 | 7 | ✅ T-M02-03 |
| M02 Backend | API E2E | 4 | 2 | 0 | 6 | ✅ T-M02-04 |
| M06 Booking | API E2E | 13 | 5 | 0 | 18 | ✅ T-DEF-001-05 |
| M12 Pricing | UT | 4 | 3 | 2 | 9 | ✅ T-M12-05 |
| M12 Pricing | IT | 5 | 2 | 1 | 8 | ✅ T-M12-06 |
| M12 Pricing | API E2E | 4 | 3 | 1 | 8 | ❌ 延後 |
| **合計** | | 38 | 27 | 10 | **75** | **✅ 60 個 TC** |

### Sprint 6 測試覆蓋策略

| 優先級 | 執行數量 | 目標 |
|--------|----------|------|
| P0 | 36 個 | **全部執行** (100%) |
| P1 | 21 個 | **全部執行** (100%) |
| P2 | 8 個 | 按時間分配 (Buffer 預留) |

---

## 9. 驗收標準 (Definition of Done) (v1.3 強化版)

### Sprint 6 DoD
- [ ] 所有 24 SP 規劃項目已完成
- [ ] **Booking E2E P0 測試通過率 100%** (13/13 AT 案例)
- [ ] **M01 Backend IT+API P0 測試通過率 100%** (6/6 TC)
- [ ] **M02 Backend IT+API P0 測試通過率 100%** (7/7 TC) ✅ 修正：M02 IT P0=3 + API E2E P0=4 = 7 P0
- [ ] **M12 PricingService UT 通過率 100%** (9/9 整體，其中 P0 等級需 4/4)
- [ ] **M12 IT P0 測試通過率 100%** (5/5 TC) ✅ 修正：原 2/2 → 5/5 (含 IT-M12-006/007/008 價格計算)
- [ ] M01/M02 Frontend 頁面可正常 CRUD
- [ ] M12 Backend 端點已完成重構並通過 UT
- [ ] M12 Frontend 定價頁面可正常操作
- [ ] Feature Toggle 邏輯測試通過 (IT-M02-002, IT-M12-002)
- [ ] DEFERRED_ITEMS_TRACKER 已更新
- [ ] Phase 1 進度已更新

### 測試通過率計算方式

| 測試類型 | 總數 | P0 數量 | 通過數 | 通過率計算 |
|----------|------|---------|--------|------------|
| Booking E2E AT | 13 | 13 P0 | X | X/13 ≥ 100% (全部 P0) |
| M01 IT+API P0 | 6 | 6 P0 | X | X/6 ≥ 100% (全部 P0) |
| M02 IT+API P0 | 7 | 7 P0 | X | X/7 ≥ 100% (全部 P0) ✅ 修正：IT P0=3 + API E2E P0=4 |
| M12 UT | 9 | 4 P0 | X | X/9 ≥ 100% (整體)，X_P0/4 ≥ 100% (P0) |
| M12 IT P0 | 5 | 5 P0 | X | X/5 ≥ 100% ✅ 修正：原 2/2 → 5/5 (含 IT-M12-006/007/008) |

---

## 10. 產出文件

| 文件 | 位置 | 負責人 |
|------|------|--------|
| Sprint 6 Plan | docs/04_planning/SPRINT_06_PLAN.md | PM |
| Sprint 6 Tasks | docs/05_development/SPRINT_06_TASKS.md | Dev |
| Sprint 6 Review | docs/05_development/SPRINT_06_REVIEW.md | Dev |
| Sprint 6 Test Report | docs/03_testing/SPRINT_06_TEST_REPORT.md | QA |
| Deferred Items Tracker 更新 | docs/04_planning/DEFERRED_ITEMS_TRACKER.md | PM |
| API_Listing_Unified_Create.md (新) | docs/02_architecture/api/ | SA |

---

## 11. Sprint 目標自評 (v1.2)

| 目標 | SP | 預期結果 | TC 覆蓋 | 自評 |
|------|-----|----------|---------|------|
| DEF-001 環境建設 | 4 | Booking E2E 環境就緒，P0 AT 100% 通過 | 13 AT | 🟢 |
| M01/M02 Frontend | 6 | 商品/房源管理頁面完整可用 | - | 🟢 |
| M01/M02 Backend API 測試 | 6 | IT+API P0 100% 通過 | M01: 5 P0, M02: 9 P0 | 🟢 |
| M12 動態定價 | 8 | Backend 重構完成，UT 100% 通過 | 17 TC (UT 9 + IT 5 + API E2E 延後) | 🟢 |

**Sprint 6 總測試覆蓋**: 40 個 TC (P0 37 + P1 25 + P2 9) + 11 AT (Booking E2E) = 71 個 TC/AT

---

## 12. QA 審查記錄

| 版本 | 日期 | 審查員 | 主要修正 |
|------|------|--------|----------|
| v1.0 | 2026-04-29 | PM | 初始版本 |
| v1.1 | 2026-04-29 | QA (Quincy) | 新增 M01/M02 Backend API 測試 (+6SP)、M12 UT/IT 測試 (+3SP)、Feature Toggle 測試 (+1SP)、強化 DoD |
| v1.2 | 2026-04-29 | QA (Quincy) | 修正 DoD M12 IT 數字 (3/3→2/2)、說明 M12 UT P0 vs 整體差異、標記 M02 P0 待確認 |
| v1.3 | 2026-04-29 | QA (Quincy) | 依 TC_M02_Room.md 修正 M02 IT P0 (2→3) 和 API E2E P0 (2→6)，總 TC 數 65→69 |
| v1.4 | 2026-04-29 | QA (Quincy) | 依 TC_M06_Booking.md 補齊 T-DEF-001-05 AT 案例 (11→13)、修正矩陣 P0 總數 (35→33)、總 TC 數 69→67 |
| v1.5 | 2026-04-29 | SA (修復) | 修正 DoD M12 IT P0 (2→5)、Booking E2E AT (11→13)、M12 API E2E (6→8)、補充 Section 7 隱含依賴、統一 Section 4/8 TC 數量、修正 M01 API E2E (7→3)、M01/M02 TC (15→23)、合計 (46→54) |
| v1.6 | 2026-04-30 | SA (修復) | 修正 Section 8 矩陣 M01/M02 TC 數量與 Section 4 一致：M01 IT=5, M01 API E2E=7, M02 IT=5, M02 API E2E=6，總計 23 TC |
| v1.7 | 2026-04-30 | SA (緊急修復) | Section 5.3 任務清單與 Section 4/8 一致：T-M01-04 3→7 API E2E, T-M02-03 5→7 IT, T-M02-04 3→6 API E2E，總計 25 TC |
| v1.8 | 2026-04-30 | QA (Quincy) 修復 | Section 4 TC 數量 23→25, DoD M02 P0 9→7, T-M12-06 任務 5→8 IT, User Story TC 引用修正 |
| v1.9 | 2026-04-30 | QA (Quincy) 最終確認 | Section 4 TC 數量 25 (正確值)，驗證通過 |
| v2.0 | 2026-04-30 | SA (Marcus) 修復 | Section 8 矩陣 M01 Backend API E2E P0=3→4, P1=4→3 (依 Section 5.3 T-M01-04 實際 TC：API-M01-001/002/004/007 為 P0)，合計 P0 37→38, P1 28→27 |
| v2.1 | 2026-04-30 | SA (Marcus) 修復 | Section 9 DoD M01 IT+API P0 (5/5→6/6 TC)，依 Section 8 矩陣：M01 IT P0=2 + M01 API E2E P0=4 = 6 P0 |
| v2.2 | 2026-04-30 | SA (Marcus) 修復 | Section 9 測試通過率計算公式 M01 IT+API P0：X/5→X/6 (總數 6 P0，公式與其他行一致) |

---

**文件版本**: v2.2
**建立日期**: 2026-04-29
**最後更新**: 2026-04-30
**QA 審查狀態**: ✅ 已通過 QA 審查 (v1.9)
**計畫審核**: ✅ 待 PM/BA 確認 Sprint 6 Goals v1.9
**定稿狀態**: ✅ 已定稿
