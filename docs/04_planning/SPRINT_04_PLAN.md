# Sprint 4 計劃 / Sprint 4 Plan

> **Sprint 編號**: Sprint 4
> **期間**: 2026-05-27 ~ 2026-06-09 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-27
> **基於**: Sprint 3 完成 + M17 租戶管理完成 + Phase 2 模組

---

## 🔴 人機協作確認點結果

### Sprint 3 回顧摘要
**已完成**:
- US-M17-007/008 Backend Admin APIs 全部完成 (7 SP)
- FE-M17-001 ~ FE-M17-004 Frontend 頁面完成 (12 SP)
- M17 功能開關 (Feature Toggle) Backend 完成
- 154 tests passing, 0 failures

**待完成 (Sprint 3 延後)**:
- FE-M17-005 功能開關頁面
- FE-M17-006 Admin 審核頁面
- US-M17-009 Admin Feature Toggle 更新

### Sprint 4 準備確認
**選擇**: ✅ Sprint 4 專注於 Phase 2 核心模組 (M04 購物車 + M06 預訂系統)

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 4 |
| **開始日期** | 2026-05-27 |
| **結束日期** | 2026-06-09 |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 10 SP |
| **Buffer** | 9 SP (✅ 充足安全範圍) |
| **Buffer 配置** | M06 驗收測試風險: 3 SP, M04 新實作風險: 3 SP, 緊急 Bug 應急: 3 SP |
| **團隊** | 2 人 Dev Team |

---

## ⚠️ 風險警示

| 風險 | 說明 | 緩解措施 |
|------|------|----------|
| **M06 已實作但需驗收** | Backend 已實作，需確認完整度 | 優先安排 IT/E2E 驗收 |
| **M04 ROOM 日期支援** | 新增 startDate/endDate 欄位 | 確保 CartDto 修改正確 |

## 📝 Sprint 4 修訂說明 (2026-04-28)

| 修訂項目 | 原始規劃 | 修訂後 | 原因 |
|----------|----------|--------|------|
| M06 預訂系統 | 7 SP (待實作) | 2 SP (驗收測試) | Backend 功能已實作 |
| FE-M17-006 | 0 SP (延後至 Sprint 5) | 3 SP (移回 Sprint 4) | 店鋪上線必經流程，阻塞新商戶入駐 |
| FE-M17-005 + US-M17-009 | 3 SP (Sprint 3 延後) | 0 SP (延後至 Sprint 5) | 讓出空間給 FE-M17-006 |
| Buffer | 8 SP | 8 SP | 維持安全範圍 |

## 2. Sprint 目標

> **目標**: 完成 M04 購物車核心功能 + M06 預訂系統完整版 (POST 建立預訂)，形成完整的商品/房源購買流程。

### 具體目標

#### M04 購物車 (5 SP)
1. **US-M04-001: 加入購物車** - 買家可將商品/房源加入購物車
2. **US-M04-002: 檢視購物車** - 買家可查看購物車內容
3. **US-M04-003: 更新數量** - 買家可調整商品數量
4. **US-M04-004: 移除商品** - 買家可移除購物車商品

#### M06 預訂系統完整版 (7 SP)
1. **US-M06-001: 建立預訂** - 買家可建立房源預訂 (Phase 1 只有唯讀)
2. **US-M06-002: 預訂日曆鎖定** - 建立預訂時自動鎖定日期格
3. **US-M06-003: 取消預訂** - 買家可取消未入住預訂

> **⚠️ 重要發現**: M06 預訂系統的 Backend 功能（BookingService + BookingController）**已實作完成**！Sprint 4 的 M06 工作應聚焦於：
> 1. 驗收測試（IT/E2E）確保功能正確
> 2. 確認前端串接無問題
> 3. 如有缺口，進行修正

#### 延後功能補充 (3 SP)
4. **FE-M17-005: 功能開關頁面** - StoreOwner 可管理 Feature Toggles
5. **US-M17-009: Admin Feature Toggle 更新** - Admin 可更新任意店鋪 Toggle

---

## 3. User Stories

### 3.1 M04 購物車 Stories

| ID | 標題 | SP | 優先級 | Business Value | 狀態 | 負責人 |
|----|------|-----|--------|----------------|------|--------|
| US-M04-001 | 加入購物車 | 2 | P0 | 提升轉換率 15%，減少跳出率 | 待實現 | Dev |
| US-M04-002 | 檢視購物車 | 1 | P0 | 提升用戶購物體驗，降低取消率 | 待實現 | Dev |
| US-M04-003 | 更新數量 | 1 | P0 | 支援用戶調整購買數量，提升訂單完成率 | 待實现 | Dev |
| US-M04-004 | 移除商品 | 1 | P0 | 支援用戶取消不需要的商品，提升用戶滿意度 | 待實現 | Dev |

### 3.2 M06 預訂系統 Stories

| ID | 標題 | SP | 優先級 | Business Value | 狀態 | 負責人 |
|----|------|-----|--------|----------------|------|--------|
| US-M06-001 | 建立預訂 | 3 | P0 | 閉環交易核心功能，電商平台必備 | 待實現 | Dev |
| US-M06-002 | 預訂日曆鎖定 | 2 | P0 | 防止超賣，提升房源管理效率 | 待實現 | Dev |
| US-M06-003 | 取消預訂 | 2 | P0 | 支援用戶取消需求，提升用戶體驗 | 待實現 | Dev |

### 3.3 Sprint 3 延後 Stories

| ID | 標題 | SP | 優先級 | Business Value | 狀態 | 負責人 |
|----|------|-----|--------|----------------|------|--------|
| FE-M17-005 | 功能開關頁面 | 2 | P1 | 提升營運效率，StoreOwner 可自主管理功能上線 | **延後至 Sprint 5** | Dev |
| US-M17-009 | Admin Feature Toggle 更新 | 1 | P1 | 支援 Admin 緊急停用功能，降低營運風險 | **延後至 Sprint 5** | Dev |
| FE-M17-006 | Admin 審核頁面 | 3 | P0 | 店鋪上線必經流程，阻塞新商戶入駐 | 待實現 | Dev |

### 3.4 Sprint 4 Total (修訂)

| 類別 | SP | 說明 |
|------|-----|------|
| M04 購物車 | 5 | US-M04-001: 2, US-M04-002: 1, US-M04-003: 1, US-M04-004: 1 |
| M06 預訂系統 | 2 | 驗收測試 + 修正（功能已實作） |
| FE-M17-006 | 3 | Admin 審核頁面（店鋪上線必經流程） |
| Sprint 3 延後 | 0 | FE-M17-005 + US-M17-009 **延後至 Sprint 5** |
| **總計** | **10 SP** | |
| **Buffer** | **8 SP** | 因 M06 已實作，釋放 7 SP + 保留 1 SP |

> **⚠️ Sprint 4 修訂 (2026-04-28)**: 
> - FE-M17-006 從 Sprint 5 移回 Sprint 4（店鋪上線流程阻塞）
> - FE-M17-005 + US-M17-009 延後至 Sprint 5
> - Buffer 維持 8 SP（仍充足）

---

## 4. 任務分解 / Task Breakdown

### 4.1 M04 購物車 Tasks

#### US-M04-001: 加入購物車 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M04-001-01 | 實作 POST /api/v2/cart/items 端點 | 1.5h | 待實現 |
| T-M04-001-02 | 購物車 Cart/CartItem 資料模型 | 0.5h | 待實現 |
| T-M04-001-03 | 整合測試：成功加入購物車 | 1h | 待實現 |

**驗收標準 (AC)**:
- [ ] AC-M04-001-1: 買家可將商品加入購物車
- [ ] AC-M04-001-2: 同一商品（相同 listingId + skuId + 日期範圍）多次加入會累加數量；ROOM 類型不同日期範圍視為不同項目
- [ ] AC-M04-001-3: 非 BUYER 角色無法加入購物車

#### US-M04-002: 檢視購物車 (1 SP)

**負責人**: Dev
**預估時間**: 1.5 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M04-002-01 | 實作 GET /api/v2/cart 端點 | 0.5h | 待實现 |
| T-M04-002-02 | 實作 GET /api/v2/cart/items 端點 | 0.5h | 待實現 |
| T-M04-002-03 | 整合測試：檢視購物車 | 0.5h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M04-002-1: 買家可查看自己的購物車
- [ ] AC-M04-002-2: 購物車包含商品列表（listingId, title, coverImageUrl）、數量、單價、小計、日期範圍（ROOM 類型）

#### US-M04-003: 更新數量 (1 SP)

**負責人**: Dev
**預估時間**: 1 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M04-003-01 | 實作 PUT /api/v2/cart/items/:id 端點 | 0.5h | 待實现 |
| T-M04-003-02 | 整合測試：更新數量 | 0.5h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M04-003-1: 買家可更新商品數量
- [ ] AC-M04-003-2: 數量為 0 時移除商品；數量為最大值（999）時不再累加

#### US-M04-004: 移除商品 (1 SP)

**負責人**: Dev
**預估時間**: 1 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M04-004-01 | 實作 DELETE /api/v2/cart/items/:id 端點 | 0.5h | 待實現 |
| T-M04-004-02 | 整合測試：移除商品 | 0.5h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M04-004-1: 買家可移除購物車商品
- [ ] AC-M04-004-2: 移除不存在商品回傳 404

### 4.2 M06 預訂系統 Tasks

#### US-M06-001: 建立預訂 (已實作 ✅)

**Backend 狀態**: ✅ BookingService.createBooking() 已實作

**Sprint 4 任務**: 驗收測試
| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M06-001-V | 驗收 BookingService.createBooking() | 1h | 待實現 |
| T-M06-001-IT | IT: 建立預訂成功流程 | 1h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M06-001-1: 買家可建立房源預訂
- [ ] AC-M06-001-2: 預訂包含入住/退房日期、 guest 數量
- [ ] AC-M06-001-3: 日期衝突回傳錯誤

#### US-M06-002: 預訂日曆鎖定 (已實作 ✅)

**Backend 狀態**: ✅ RoomCalendarService.lockDateRange() + bookDateRange() 已實作

**Sprint 4 任務**: 驗收測試
| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M06-002-V | 驗收 RoomCalendarService | 0.5h | 待實現 |
| T-M06-002-IT | IT: 日期格鎖定流程 | 0.5h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M06-002-1: 預訂成功後日期格鎖定
- [ ] AC-M06-002-2: 其他預訂無法重疊鎖定日期

#### US-M06-003: 取消預訂 (已實作 ✅)

**Backend 狀態**: ✅ BookingService.cancelBooking() + RoomCalendarService.releaseDateRange() 已實作

**Sprint 4 任務**: 驗收測試
| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M06-003-V | 驗收 BookingService.cancelBooking() | 0.5h | 待實现 |
| T-M06-003-IT | IT: 取消預訂 + 日期格釋放 | 0.5h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M06-003-1: 買家可取消 CREATED 或 CONFIRMED 狀態的預訂（尚未入住）
- [ ] AC-M06-003-2: 取消後日期格釋放（狀態變回 AVAILABLE，bookingId = null），可被新預訂

### 4.3 Sprint 3 延後功能 Tasks

#### FE-M17-005: 功能開關頁面 (2 SP)

**負責人**: Dev
**預估時間**: 3 小時
**頁面路由**: `/dashboard/tenants/[id]/features`

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-005-01 | 建立 `/dashboard/tenants/[id]/features` 頁面路由 | 0.5h | 待實現 |
| T-FE-005-02 | 實作 Feature Toggle 列表元件 | 1h | 待實现 |
| T-FE-005-03 | 串接 GET /v2/dashboard/tenants/features API | 0.5h | 待實现 |
| T-FE-005-04 | 串接 PUT /v2/dashboard/tenants/features/:feature API | 1h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-FE-005-1: StoreOwner 可查看店鋪所有 Feature Toggles
- [ ] AC-FE-005-2: StoreOwner 可啟用/停用需要審核的 Feature
- [ ] AC-FE-005-3: 非 Owner 角色無權限訪問

**Feature Toggle 分類**:
- `auto-approved`: StoreOwner 可直接啟用/停用
- `requires-admin-review`: 啟用後需 Admin 審核才生效（狀態為 PENDING）

**依賴**: FE-M17-003 (店鋪詳情頁面)

#### US-M17-009: Admin Feature Toggle 更新 (1 SP)

**負責人**: Dev
**預估時間**: 1.5 小時

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-M17-009-01 | 實作 Admin PUT /api/v2/admin/tenants/:id/features/:feature 端點 | 0.5h | 待實現 |
| T-M17-009-02 | 整合測試：Admin 更新 Feature Toggle | 1h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-M17-009-1: Admin 可更新任意店鋪的 Feature Toggle
- [ ] AC-M17-009-2: 非 Admin 角色無法呼叫此 API

**依賴**: FE-M17-005 (功能開關頁面需要此 API)

#### FE-M17-006: Admin 審核頁面 (3 SP)

> **✅ 已移回 Sprint 4 (2026-04-28 修訂)**

**負責人**: Dev
**預估時間**: 4 小時
**頁面路由**: `/admin/tenants/:id/review`

| 任務 | 描述 | 預估時間 | 狀態 |
|------|------|----------|------|
| T-FE-006-01 | 建立 `/admin/tenants` 和 `/admin/tenants/:id/review` 頁面路由 | 0.5h | 待實現 |
| T-FE-006-02 | 實作待審核店鋪列表元件 | 1h | 待實现 |
| T-FE-006-03 | 實作審核詳情元件（通過/駁回按鈕） | 1.5h | 待實现 |
| T-FE-006-04 | 串接 Admin APIs（GET /admin/tenants, POST /admin/tenants/:id/approve, POST /admin/tenants/:id/reject） | 1h | 待實现 |

**驗收標準 (AC)**:
- [ ] AC-FE-006-1: Admin 可查看所有待審核店鋪列表
- [ ] AC-FE-006-2: Admin 可審核通過店鋪（approve）
- [ ] AC-FE-006-3: Admin 可駁回店鋪並填寫原因（reject）
- [ ] AC-FE-006-4: 非 Admin 角色無法訪問此頁面

**依賴**: US-M17-007/008 (Admin Backend APIs 已完成 ✅)

**依賴**: US-M17-007/008 (Admin Backend APIs 已完成)

---

## 5. API 規格

### 5.1 M04 購物車 APIs

#### 加入購物車

**端點**: `POST /api/v2/cart/items`

**Request Body**:
```json
{
  "listingId": "uuid",
  "quantity": 1,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
```

**Response** (201 Created):
```json
{
  "code": 201,
  "message": "Item added to cart",
  "data": {
    "cartItemId": "uuid",
    "listingId": "uuid",
    "listingName": "房源名稱",
    "quantity": 1,
    "unitPrice": 1500,
    "subtotal": 1500
  }
}
```

#### 檢視購物車

**端點**: `GET /api/v2/cart`

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Cart retrieved",
  "data": {
    "cartId": "uuid",
    "userId": "uuid",
    "items": [
      {
        "cartItemId": "uuid",
        "listingId": "uuid",
        "listingName": "房源名稱",
        "quantity": 1,
        "unitPrice": 1500,
        "subtotal": 1500,
        "startDate": "2026-06-01",
        "endDate": "2026-06-03"
      }
    ],
    "totalAmount": 1500
  }
}
```

### 5.2 M06 預訂 APIs

#### 建立預訂

**端點**: `POST /api/v2/bookings`

**Request Body**:
```json
{
  "roomListingId": "uuid",
  "checkInDate": "2026-06-01",
  "checkOutDate": "2026-06-03",
  "guestCount": 2,
  "guestName": "王小明",
  "guestPhone": "0912345678",
  "guestEmail": "wang@example.com",
  "specialRequests": "需要嬰兒床"
}
```

**Response** (201 Created):
```json
{
  "code": 201,
  "message": "Booking created",
  "data": {
    "bookingId": "uuid",
    "status": "CONFIRMED",
    "listingId": "uuid",
    "listingName": "房源名稱",
    "checkInDate": "2026-06-01",
    "checkOutDate": "2026-06-03",
    "guestCount": 2,
    "totalPrice": 3000,
    "createdAt": "2026-05-27T10:30:00Z"
  }
}
```

**Error Codes**:
| Error Code | 描述 |
|------------|------|
| E-3000 | LISTING_NOT_FOUND - 房源不存在 |
| E-4001 | DATE_CONFLICT - 日期已被預訂 |
| E-4003 | INVALID_DATE_RANGE - 入住日期晚於退房日期 |

#### 取消預訂

**端點**: `POST /api/v2/bookings/:id/cancel`

**Response** (200 OK):
```json
{
  "code": 200,
  "message": "Booking cancelled",
  "data": {
    "bookingId": "uuid",
    "status": "CANCELLED",
    "cancelledAt": "2026-05-28T10:30:00Z"
  }
}
```

---

### 5.3 Error Code 對照

| API 文件定義 | ErrorCode.java 映射 | 語義確認 |
|-------------|---------------------|----------|
| E-3000 LISTING_NOT_FOUND | E_3000 "Listing not found" | ✅ 一致 |
| E-4001 ROOM_CALENDAR_CONFLICT | E_4001 "Room calendar conflict" | ✅ 一致 (2026-04-28 修正命名) |
| E-4003 INVALID_DATE_RANGE | E_4003 "Invalid date range" | ✅ 一致 |
| E-4007 BOOKING_CANNOT_CANCEL | E_4007 "Booking cannot be cancelled" | ✅ 一致 |

**✅ 已確認 (2026-04-28)**: E-4001 API 文件命名從 `DATE_CONFLICT` 修正為 `ROOM_CALENDAR_CONFLICT`，配合 ErrorCode.java 既有命名，避免修改既有程式碼。

---

## 6. 測試策略

### 6.1 測試類型分佈

| 測試類型 | 數量 | 負責人 |
|----------|------|--------|
| Backend UT | 8 | Dev |
| Backend IT | 6 | Dev/QA |
| API E2E (Backend) | 8 | QA |
| Frontend UT | 6 | Dev |
| Frontend IT | 4 | Dev/QA |
| E2E (Frontend) | 2 | QA |

### 6.2 測試優先級（修訂 2026-04-28）

| 優先級 | 測試案例 | 數量 | 說明 |
|--------|----------|------|------|
| P0 | M04 購物車 CRUD | 4 | |
| P0 | M06 建立/取消預訂 | 4 | |
| P0 | 日期衝突檢查 (E-4001) | 4 | **從 P1 升級** - 核心錯誤場景，需完整 API E2E 覆蓋 |
| P1 | 多租戶隔離 | 2 | |
| P1 | 負向場景 (E-4003, E-4007) | 3 | E-4003 (Invalid date range), E-4007 (cannot cancel) |

### 6.3 測試缺口補充（2026-04-28 新增）

| 缺口 | 嚴重性 | 補充數量 | 建議執行時機 |
|------|--------|----------|--------------|
| 日期衝突 API E2E | 🔴 高 | +2 | M06 IT/E2E 階段 |
| 負向場景 API E2E | 🔴 高 | +3 | M06 IT/E2E 階段 |
| Frontend E2E (M04/M06) | 🔴 高 | +4 | FE-M17-006 完成後 |
| 多租戶隔離 IT | 🟡 中 | +2 | M04/M06 IT 階段 |

> **Buffer 分配建議**：從 8 SP Buffer 中分配 2-3 SP 執行上述測試缺口彌補工作

---

## 7. Sprint 執行計劃

### 7.1 第一週 (2026-05-27 ~ 2026-06-02)

| 日期 | 重點任務 |
|------|----------|
| Day 1 (05/27) | Sprint Kickoff, M04-001 實作 |
| Day 2 (05/28) | M04-001 IT/E2E, M04-002 實作 |
| Day 3 (05/29) | M04-002/003 IT, M04-004 實作 |
| Day 4 (05/30) | M04 IT/E2E 完成, M06-001 框架 |
| Day 5 (05/31) | M06-001 實作 |
| Day 6-7 | Weekend |

### 7.2 第二週 (2026-06-03 ~ 2026-06-09)

| 日期 | 重點任務 |
|------|----------|
| Day 8 (06/03) | M06-001 IT/E2E, M06-002 實作 |
| Day 9 (06/04) | M06-002 IT, M06-003 實作 |
| Day 10 (06/05) | M06-003 IT, FE-M17-006 實作 |
| Day 11 (06/06) | US-M17-009 實作, Bug Fix |
| Day 12 (06/07) | Code Review, Sprint Review |
| Day 13 (06/08) | Buffer / Sprint 5 準備 |
| Day 14 (06/09) | Buffer |

---

## 8. 風險與依賴

### 8.1 風險

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|----------|
| M06 日期鎖定邏輯複雜度 | 中 | 中 | 預留 buffer，先實作基本流程 |
| M04 購物車需要關聯現有 Listing | 低 | 低 | 使用現有 M01/M02 Listing APIs |
| Redis session 依賴 | 中 | 中 | 確認 Redis 環境正常 |

### 8.2 依賴

| 依賴 | 類型 | 狀態 |
|------|------|------|
| M01/M02 Listing APIs | 基礎設施 | ✅ 已有 |
| Redis | 基礎設施 | ✅ 已有 |
| AuthService | 基礎設施 | ✅ 已有 |

---

## 9. Definition of Done (DoD)

| 項目 | 標準 | 狀態 |
|------|------|------|
| Backend 代碼完成 | M04 + M06 + FE-M17-006 實作完成 | - |
| Code Review | 通過團隊 Code Review | - |
| Backend UT 覆蓋率 | >= 80% (CartService, BookingService) | - |
| Backend IT 通過 | IT-M04-*, IT-M06-* 全部通過 | - |
| API E2E 通過 | API-M04-*, API-M06-* 全部通過 | - |
| 多租戶隔離驗證 | 買家只能看到自己的 Cart/Booking | - |
| 文檔更新 | API 規格更新 | - |

---

## 10. 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| Sprint 3 計劃 | `docs/04_planning/SPRINT_03_PLAN.md` | Sprint 3 完成狀態 |
| Sprint 3-A 計劃 | `docs/04_planning/SPRINT_03-A_PLAN.md` | Sprint 3-A 補漏 |
| User Stories | `docs/01_requirements/E-Commerce_FRD_v1.0.md` | Phase 1/2 User Stories |
| API 規格 | `docs/02_architecture/API_M04_Cart.md` | M04 API 規格 (待建立) |
| API 規格 | `docs/02_architecture/API_M06_Booking.md` | M06 API 規格 (待建立) |
| 測試案例 | `docs/03_testing/TC_M04_Cart.md` | M04 測試案例 (待建立) |
| 測試案例 | `docs/03_testing/TC_M06_Booking.md` | M06 測試案例 (待建立) |

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-04-28

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-27 | 初始版本（Sprint 4 計劃：M04 購物車 + M06 預訂系統 + Sprint 3 延後功能） | - |
| v1.1 | 2026-04-28 | 三 Agent 審查後修正：<br>1. API 欄位同步（listingId → roomListingId）<br>2. AC-M06-003-1 修正（未入住 → CREATED/CONFIRMED）<br>3. FE-M17-006 移回 Sprint 4（店鋪上線必經）<br>4. FE-M17-005 + US-M17-009 延後至 Sprint 5<br>5. Buffer 調整為 9 SP（緊急 Bug 應急 3 SP）<br>6. 日期衝突測試升級為 P0 | - |
| v1.2 | 2026-04-28 | **Sprint 4 Kickoff 準備修正**：<br>1. ✅ R-001 修正：CartController updateItem/removeItem 改用 `cartItemKey` (String)，符合 API 文件定義<br>2. ✅ E-4001 Error Code 命名確認：API 文件 `DATE_CONFLICT` → `ROOM_CALENDAR_CONFLICT`，配合 ErrorCode.java | - |
