# Sprint 11 計劃 / Sprint 11 Plan

> **Sprint 編號**: Sprint 11
> **期間**: 2026-06-01 ~ 2026-06-12 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-05-13
> **更新日期**: 2026-05-13
> **基於**: Sprint 10 M16 ERP 完成 + PRD v1.0 Phase 1 範圍

---

## 🔴 前置條件確認

**Sprint 10 發布**: ✅ **IN REVIEW** - 2026-05-13 發布評審中

| 項目 | 確認結果 |
|------|----------|
| Sprint 10 開發完成 | ✅ COMPLETED - M16 ERP 進銷存核心功能完成 |
| Sprint 10 IT 測試通過 | ✅ 36/36 PASS |
| Sprint 10 E2E 測試通過 | ✅ 6/6 PASS |
| CI Pipeline 驗證 | ⏳ PENDING - 等待 GitHub 額度恢復 (2026-06-01) |
| Sprint 11 開始日期 | ✅ **2026-06-01** (週一) |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 11 |
| **開始日期** | 2026-06-01 (週一) |
| **結束日期** | 2026-06-12 (週四) |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 待規劃 |
| **Buffer** | ~13% |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M04 購物車核心功能 + M06 預訂完整化（Phase 2），為 C 端買家提供流暢的購物車體驗，並解鎖 M06 民宿預訂的完整建立流程。

### 具體目標

#### M04 購物車 Backend 功能

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 購物車 CRUD | P0 | Redis Hash 存儲購物車資料 |
| 加入商品到購物車 | P0 | POST /api/cart/items |
| 更新購物車項目數量 | P0 | PUT /api/cart/items/:id |
| 移除購物車項目 | P0 | DELETE /api/cart/items/:id |
| 清空購物車 | P0 | DELETE /api/cart |
| 優惠券套用 | P1 | 優惠券驗證與折扣計算 |
| 滿額折扣計算 | P1 | 滿額折扣/免運計算 |

#### M06 預訂完整化 Backend 功能

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 建立預訂 | P0 | POST /api/v2/bookings 含日期格鎖定 |
| Redis 分散式鎖 | P0 | 防止 Overbooking |
| 冪等性 Key | P0 | Idempotency-Key 防止重複預訂 |
| 修改已確認的預訂 | P2 | PUT /api/v2/bookings/:id |
| 預訂提醒 | P2 | POST /api/v2/bookings/:id/remind |

#### M13 商家工作台 Frontend 功能

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 營收概覽儀表板 | P1 | 顯示營收、訂單、庫存摘要 |
| 訂單處理列表 | P1 | 快速處理訂單 |
| 庫存預警視圖 | P1 | 低庫存商品提醒 |

---

## 3. PRD v1.0 Phase 1 完成狀態

### Phase 1 已完成模組

| 模組 | 名稱 | Sprint | 狀態 |
|------|------|--------|------|
| M01 | 商品中心 | Sprint 3-4 | ✅ COMPLETED |
| M02 | 房源中心 | Sprint 3-4 | ✅ COMPLETED |
| M03 | 認證服務 | Sprint 3 | ✅ COMPLETED |
| M05 | 零售訂單履約 | Sprint 5-6 | ✅ COMPLETED |
| M12 | 動態定價 | Sprint 8 | ✅ COMPLETED |
| M15 | 內容管理 | Sprint 9 | ✅ COMPLETED |
| M16 | ERP 進銷存 | Sprint 10 | ✅ COMPLETED |
| M17 | 租戶管理 | Sprint 9 | ✅ COMPLETED |

### Phase 1 待完成模組

| 模組 | 名稱 | 優先級 | 說明 |
|------|------|--------|------|
| **M04** | 購物車與促銷 | **P0** | 購物車快取、優惠券套用、滿額折扣/免運計算 |
| **M06** | 預訂與日曆鎖定 | **P0** | Phase 2：POST 建立預訂、Redis 分散式鎖防止 Overbooking |
| M13 | 商家工作台 | P1 | 零售賣家與民宿房東的儀表板 |

### Phase 2+ 模組（未來規劃）

| 模組 | 名稱 | Phase |
|------|------|-------|
| M07 | 金流 | Phase 2 |
| M08 | 評價系統 | Phase 3 |
| M09 | 通知系統 | Phase 2 |
| M10 | 即時通訊 | Phase 3 |
| M11 | 物流系統 | Phase 3 |
| M14 | 後台管理 | Phase 2+ |
| **M18** | 知識管理 | **Phase 2-A** |

---

## 4. User Stories 摘要

| US ID | 標題 | SP | 優先級 |
|-------|------|-----|--------|
| US-M11-001 | BE: 購物車 CRUD 與庫存連動 | 5 | P0 |
| US-M11-002 | BE: 優惠券驗證與折扣計算 | 3 | P1 |
| US-M11-003 | BE: M06 建立預訂含日期格鎖定 | 5 | P0 |
| US-M11-004 | BE: M06 Redis 分散式鎖防 Overbooking | 3 | P0 |
| US-M11-005 | FE: 商家工作台儀表板 | 5 | P1 |
| US-M11-006 | FE: 購物車頁面 | 3 | P0 |
| US-M11-007 | FE: M06 預訂建立流程 | 5 | P0 |
| **待補充** | 視評估調整 | | |

---

## 5. 任務分解（初步）

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 |
|---------|----------|-----|--------|--------|
| Task-M11-101 | Backend: 購物車 Service (Redis Hash) | 3 | Dev | P0 |
| Task-M11-102 | Backend: 購物車 API CRUD | 2 | Dev | P0 |
| Task-M11-103 | Backend: 優惠券驗證模組 | 3 | Dev | P1 |
| Task-M11-104 | Backend: M06 建立預訂 API | 3 | Dev | P0 |
| Task-M11-105 | Backend: M06 Redis 分散式鎖 | 2 | Dev | P0 |
| Task-M11-106 | FE: 購物車頁面 | 3 | FE Dev | P0 |
| Task-M11-107 | FE: M06 預訂建立流程 | 5 | FE Dev | P0 |
| Task-M11-108 | FE: 商家工作台儀表板 | 5 | FE Dev | P1 |
| Task-M11-109 | IT: 購物車整合測試 | 3 | QA | P0 |
| Task-M11-110 | IT: M06 預訂整合測試 | 3 | QA | P0 |
| Task-M11-111 | E2E: 購物車 + 結帳流程測試 | 2 | QA | P1 |
| **合計** | | **34 SP** | | |

> ⚠️ **注意**: 任務分解為初步估計，實際 SP 可能需要根據詳細設計調整

---

## 6. Sprint 11 前置準備檢查清單

### 6.1 Backend ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| Redis | ✅ 運行中 | Port 6379，含 REDIS_PASSWORD |
| M05 訂單履約 | ✅ 已存在 | 訂單狀態機、庫存扣減邏輯 |
| M06 預訂查詢 | ✅ 已存在 | GET /api/v2/bookings |
| M12 動態定價 | ✅ 已存在 | 定價規則、room_calendar |

### 6.2 Backend 需要新增

| 項目 | 狀態 | 備註 |
|------|------|------|
| CartService | ⏳ 待建立 | 購物車 Redis Hash 操作 |
| BookingService | ⏳ 待擴展 | 新增 POST 建立預訂邏輯 |
| Redis 分散式鎖 | ⏳ 待建立 | M06 Overbooking 防護 |
| Idempotency Key 驗證 | ⏳ 待建立 | Booking 建立時防止重複 |

### 6.3 Frontend ⏳ 待開始

| 項目 | 狀態 | 備註 |
|------|------|------|
| 購物車頁面 | ⏳ 待開始 | /cart |
| 結帳流程 | ⏳ 待開始 | /checkout |
| M06 預訂建立流程 | ⏳ 待開始 | /bookings/new |
| 商家工作台儀表板 | ⏳ 待開始 | /dashboard |

### 6.4 測試環境 ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| Backend | ✅ 運行中 | http://localhost:8080 |
| Frontend | ✅ 運行中 | http://localhost:3000 |
| PostgreSQL | ✅ 運行中 | Port 5432 |
| Redis | ✅ 運行中 | Port 6379 |

---

## 7. Phase 1 → Phase 2 過渡規劃

```
Phase 1 完成 ✅ (Sprint 1-10)
    ↓
Sprint 11: M04 購物車 + M06 預訂完整化
    ↓
Sprint 12: M07 金流 + M09 通知 (Saga Pattern)
```

### 7.1 M04 購物車技術考量

**購物車存儲策略**:
- 使用 Redis Hash 存儲：`cart:{tenant_id}:{user_id}`
- Key 結構：`{ cartId: "...", items: [...], appliedPromoCode: "..." }`
- TTL：30 天（未登入 7 天）

**庫存連動**:
- 加入購物車時**不扣減庫存**
- 結帳時（POST /api/v2/orders）才扣減庫存
- 防止超賣由 M05 訂單建立時的庫存檢核機制處理

### 7.2 M06 預訂技術考量

**Overbooking 防護**:
- Redis 分散式鎖：`lock:room:{roomId}:date:{date}`
- 鎖 TTL：30 秒（防止鎖死）
- Database Unique Key：`(room_id, date)` 確保底層互斥

**冪等性設計**:
- Header：`Idempotency-Key` (UUID v4)
- Redis 快取 24 小時內的重複請求

---

## 8. 風險追蹤

| 風險 ID | 等級 | 說明 | 緩解措施 | 狀態 |
|---------|------|------|----------|------|
| R-011-001 | 中 | M04 促銷規則引擎複雜度 | Phase 1 先實作基本折扣，進階規則延後 | ⏳ |
| R-011-002 | 中 | M06 Redis 分散式鎖效能 | 使用 Redisson，做好壓測 | ⏳ |
| R-011-003 | 低 | M06 預訂與 M05 訂單狀態同步 | 統一使用 Saga Pattern | ⏳ |

---

## 9. 成功標準

| 標準 | 目標 | 狀態 |
|------|------|------|
| M04 購物車 CRUD API | 可正常運作 | ⏳ |
| M06 建立預訂 API | 可正常運作，無 Overbooking | ⏳ |
| Redis 分散式鎖 | 高併發下不死鎖 | ⏳ |
| Backend IT 測試通過 | 15+/15+ | ⏳ |
| E2E 測試通過 | 5/5+ | ⏳ |
| 無 High 缺陷 | High = 0 | ⏳ |

---

## 10. 與前一 Sprint 的差異

| 項目 | Sprint 10 | Sprint 11 |
|------|---------|---------|
| **焦點** | M16 ERP 進銷存 | M04 購物車 + M06 預訂完整化 |
| **新技術** | StockMovement + 狀態機 | Redis Hash + 分散式鎖 |
| **測試** | IT 36 + E2E 6 | IT 15+ + E2E 5+ |
| **前端** | ERP Dashboard 頁面 | 購物車 + 預訂流程頁面 |

---

**文件狀態**: ✅ **COMPLETED** - 2026-05-14 QA 驗證完成
**下一步**:
1. ✅ Sprint 11 開發完成 (2026-05-14)
2. ✅ QA 驗證完成 - 11/11 Tasks 確認
3. ✅ 編譯通過 - 374 tests PASS
4. ⚠️ CI Pipeline 等待 GitHub Actions 帳單額度恢復 (2026-06-01)

**相關文件**:
- [SPRINT_10_PLAN.md](SPRINT_10_PLAN.md)
- [SPRINT_10_TASKS.md](../05_development/SPRINT_10_TASKS.md)
- [E-Commerce_PRD_v1.0_Final.md](../../01_requirements/E-Commerce_PRD_v1.0_Final.md) (§6.4 M04, §6.6 M06, §6.9 M12, §6.10 M18)
- [DEFERRED_ITEMS_TRACKER.md](../04_planning/DEFERRED_ITEMS_TRACKER.md)