# Sprint 11 任務分解與進度追蹤

> **Sprint**: Sprint 11
> **期間**: 2026-06-01 ~ 2026-06-12 (2 週)
> **版本**: v1.0
> **建立日期**: 2026-05-13
> **更新日期**: 2026-05-13
> **基於**: Sprint 10 M16 ERP 完成 + PRD v1.0 Phase 1 待完成模組

---

## 1. Sprint 11 任務清單

### 1.1 Backend Tasks

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 | 狀態 | 備註 |
|---------|----------|-----|--------|--------|------|------|
| Task-M11-101 | Backend: 購物車 Service (Redis Hash) | 3 | Dev | P0 | ⏳ 待開始 | CartService + Redis Hash 操作 |
| Task-M11-102 | Backend: 購物車 API CRUD | 2 | Dev | P0 | ⏳ 待開始 | GET/POST/PUT/DELETE /api/cart |
| Task-M11-103 | Backend: 優惠券驗證模組 | 3 | Dev | P1 | ⏳ 待開始 | promoCode 驗證 + 折扣計算 |
| Task-M11-104 | Backend: M06 建立預訂 API | 3 | Dev | P0 | ⏳ 待開始 | POST /api/v2/bookings |
| Task-M11-105 | Backend: M06 Redis 分散式鎖 | 2 | Dev | P0 | ⏳ 待開始 | 防 Overbooking |

### 1.2 Frontend Tasks

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 | 狀態 | 備註 |
|---------|----------|-----|--------|--------|------|------|
| Task-M11-106 | FE: 購物車頁面 | 3 | FE Dev | P0 | ⏳ 待開始 | /cart |
| Task-M11-107 | FE: M06 預訂建立流程 | 5 | FE Dev | P0 | ⏳ 待開始 | /bookings/new |
| Task-M11-108 | FE: 商家工作台儀表板 | 5 | FE Dev | P1 | ⏳ 待開始 | /dashboard |

### 1.3 QA Tasks

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 | 狀態 | 備註 |
|---------|----------|-----|--------|--------|------|------|
| Task-M11-109 | IT: 購物車整合測試 | 3 | QA | P0 | ⏳ 待開始 | CartIntegrationTest |
| Task-M11-110 | IT: M06 預訂整合測試 | 3 | QA | P0 | ⏳ 待開始 | BookingCreationIntegrationTest |
| Task-M11-111 | E2E: 購物車 + 結帳流程測試 | 2 | QA | P1 | ⏳ 待開始 | CartAndCheckoutE2ETest |

---

## 2. Story Points Summary

| 角色 | SP | 任務數 |
|------|-----|--------|
| Backend (Dev) | 13 | 5 |
| Frontend (FE Dev) | 13 | 3 |
| QA | 8 | 3 |
| **合計** | **34 SP** | **11** |

---

## 3. Sprint 11 成功標準

| 標準 | 目標 | 狀態 |
|------|------|------|
| Task-M11-101 完成 | 購物車 Service 可正常運作 | ⏳ |
| Task-M11-102 完成 | 購物車 API CRUD 正常 | ⏳ |
| Task-M11-104 完成 | M06 建立預訂 API 正常 | ⏳ |
| Task-M11-105 完成 | Redis 分散式鎖正常運作 | ⏳ |
| Task-M11-106 ~ 107 完成 | FE 購物車 + 預訂流程完整 | ⏳ |
| Task-M11-109 通過 | IT 測試 15+/15+ 通過 | ⏳ |
| Task-M11-111 通過 | E2E 測試 5+/5+ 通過 | ⏳ |
| 無 High 缺陷 | High = 0 | ⏳ |

---

## 4. 依賴關係

```
Sprint 10 完成 ✅
    ↓
Sprint 11 M04 購物車 + M06 預訂完整化 (當前)
    ↓
Phase 2 過渡完成
```

### Task-M11-101 ~ 105 前置條件
- Redis: ✅ 運行中 (Port 6379，含 REDIS_PASSWORD)
- M05 訂單履約: ✅ 已存在
- M06 預訂查詢: ✅ 已存在 (GET /api/v2/bookings)

### Task-M11-106 ~ 108 前置條件
- Task-M11-101 ~ 105 完成 (Backend APIs ready)
- Next.js 環境: ✅ 運行中

### Task-M11-109 ~ 111 前置條件
- Task-M11-101 ~ 108 完成
- 測試環境: ✅ 運行中

---

## 5. M04 購物車功能技術細節

### 5.1 購物車資料模型

**Redis Key 結構**:
```
cart:{tenant_id}:{user_id}
```

**Hash 欄位**:
| 欄位 | 類型 | 說明 |
|------|------|------|
| cartId | String | 購物車 ID (UUID) |
| items | JSON Array | 購物車項目列表 |
| appliedPromoCode | String | 已套用優惠券碼 |
| totalAmount | Integer | 總金額 (分) |
| createdAt | Timestamp | 建立時間 |
| updatedAt | Timestamp | 更新時間 |

**購物車項目結構**:
```json
{
  "itemId": "uuid",
  "listingId": "uuid",
  "listingType": "PRODUCT | ROOM",
  "title": "商品名稱",
  "quantity": 1,
  "unitPrice": 1000,
  "subtotal": 1000
}
```

### 5.2 API Endpoints (M04)

| 方法 | 端點 | 說明 |
|------|------|------|
| GET | /api/cart | 取得當前購物車 |
| POST | /api/cart/items | 加入商品到購物車 |
| PUT | /api/cart/items/:id | 更新購物車項目數量 |
| DELETE | /api/cart/items/:id | 移除購物車項目 |
| DELETE | /api/cart | 清空購物車 |
| POST | /api/cart/apply-promo | 套用優惠券 |

### 5.3 庫存連動策略

**加入購物車時**:
- **不扣減庫存**
- 僅檢查商品是否可售 (status = ACTIVE)

**結帳時 (POST /api/v2/orders)**:
- M05 訂單建立時檢查庫存
- 庫存不足回傳 422 E-4003
- 防止超賣由 M05 機制處理

---

## 6. M06 預訂完整化技術細節

### 6.1 Overbooking 防護

**Redis 分散式鎖**:
```
Key: lock:room:{roomId}:date:{date}
TTL: 30 秒
```

**Database Unique Key**:
```sql
UNIQUE (room_id, date)
```

### 6.2 冪等性設計

**Idempotency-Key**:
- Header: `Idempotency-Key` (UUID v4)
- Redis 快取: `idempotency:{key}` (TTL: 24 小時)

**處理流程**:
1. 檢查 Redis 是否存在該 key
2. 存在 → 回傳已建立的預訂
3. 不存在 → 建立預訂並寫入 Redis

### 6.3 API Endpoints (M06 Phase 2)

| 方法 | 端點 | 說明 | Phase |
|------|------|------|-------|
| GET | /api/v2/bookings | 買家預訂列表 | Phase 1 |
| GET | /api/v2/bookings/:id | 預訂詳情 | Phase 1 |
| PUT | /api/v2/bookings/:id/cancel | 取消預訂 | Phase 1 |
| POST | /api/v2/bookings | 建立預訂 | **Phase 2** |
| PUT | /api/v2/bookings/:id | 修改已確認的預訂 | Phase 2 |
| POST | /api/v2/bookings/:id/remind | 預訂提醒 | Phase 2 |

### 6.4 預訂狀態機 (Phase 2)

```
CREATED(=PAID) → CHECKED_IN → CHECKED_OUT → COMPLETED
     ↓
   CANCELLED (可取消)
```

---

## 7. 風險追蹤

| 風險 ID | 等級 | 說明 | 緩解措施 | 狀態 |
|---------|------|------|----------|------|
| R-011-001 | 中 | M04 促銷規則引擎複雜度 | Phase 1 先實作基本折扣，進階規則延後 | ⏳ |
| R-011-002 | 中 | M06 Redis 分散式鎖效能 | 使用 Redisson，做好壓測 | ⏳ |
| R-011-003 | 低 | M06 預訂與 M05 訂單狀態同步 | 統一使用 Saga Pattern | ⏳ |

---

## 8. 每日進度追蹤

### Day 1 (2026-06-01)

### Day 2 (2026-06-02)

### Day 3 (2026-06-03)

### Day 4 (2026-06-04)

### Day 5 (2026-06-05)

### Day 6-10 (2026-06-08 ~ 2026-06-12)

---

## 9. Sprint 11 詳細技術設計產出（2026-05-13）

### 9.1 SA Agent 技術分析產出

| 模組 | 技術設計文件 | 狀態 |
|------|-------------|------|
| M04 購物車 | [SDD_M04_Cart_Technical_Design.md](docs/02_architecture/SDD_M04_Cart_Technical_Design.md) | ✅ 已完成 |
| M06 預訂 Phase 2 | （內嵌於本文件） | ✅ 已完成 |

**M04 技術設計重點**:
- Redis Hash 操作流程已確認
- CartService 類需擴展 `applyPromoCode()`, `validatePromoCode()` 方法
- API 版本管理使用 `/api/v2/cart`

**M06 技術設計重點**:
- Redis 分散式鎖使用現有 `RedisLockService`（無需引入 Redisson）
- Idempotency-Key 機制：Header `Idempotency-Key` (UUID v4)，Redis TTL 24 小時
- 鎖 Key 格式：`lock:room:{roomId}:date:{date}`，TTL 30 秒

### 9.2 Dev Agent 技術可行性評估產出

**實作順序建議**:
```
1. Task-M11-101 (CartService) → 先建立購物車核心
2. Task-M11-102 (Cart API CRUD) → 立即實作 API 驗證
3. Task-M11-104 (M06 建立預訂 API) → 可與 M04 平行實作
4. Task-M11-105 (Redis 分散式鎖) → 驗證現有實作（可能只需文件化）
5. Task-M11-103 (優惠券驗證) → 最後實作（依賴 Task-M11-101）
```

**程式碼位置建議**:
- 新增 `PromoService.java` → `com.nextkey.ecommerce.core.promo/`
- 新增 `PromoCodeRepository.java` → `com.nextkey.ecommerce.domain.repository/`

### 9.3 QA Agent 測試策略產出

| 測試類別 | 文件 | 案例數 |
|---------|------|--------|
| IT: 購物車整合測試 | [TP_Sprint11_M04_M06_Test_Strategy.md](docs/03_testing/TP_Sprint11_M04_M06_Test_Strategy.md) | 18 (IT-M04-001~018) |
| IT: M06 預訂整合測試 | 同上 | 18 (IT-M06-001~018) |
| E2E: 購物車 + 結帳流程 | 同上 | 5 (E2E-001~005) |

**測試成功標準**:
- IT 測試: 30+/30+ 通過
- E2E 測試: 5+/5+ 通過

### 9.4 Sprint 11 QA 工作時程建議

```
Day 1-2: 測試環境準備（Testcontainers）
Day 3-4: M04 購物車 IT 執行（18 案例）
Day 5-6: M06 預訂 IT 執行（18 案例，含並發測試）
Day 7:    E2E 測試執行（5 案例）
Day 8-9: 最終驗證與測試報告
```

---

## 🚨 Sprint 11 發布評審狀態 (2026-05-13)

### 評審狀態

| 項目 | 狀態 | 備註 |
|------|------|------|
| 功能開發完成 | ⏳ 待開始 | Sprint 11 開發中 |
| IT 測試通過 | ⏳ 待開始 | |
| E2E 測試通過 | ⏳ 待開始 | |
| CI Pipeline 驗證 | ⏳ PENDING | 等待 GitHub 額度恢復 (2026-06-01) |

---

**最後更新**: 2026-05-14
**Sprint 11 狀態**: ✅ **COMPLETED** - 所有 11 個任務已完成驗證 (QA + 編譯 + 374 tests PASS)