# Sprint 12 計劃 / Sprint 12 Plan

> **Sprint 編號**: Sprint 12
> **期間**: 2026-06-01 ~ 2026-06-12 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-05-14
> **更新日期**: 2026-05-14
> **基於**: Sprint 11 M04+M06 完成 + PRD v1.0 Phase 2-A 範圍

---

## 🔴 前置條件確認

**Sprint 11 發布**: ✅ **IN REVIEW** - 2026-05-14 QA 驗證完成

| 項目 | 確認結果 |
|------|----------|
| Sprint 11 開發完成 | ✅ COMPLETED - M04 購物車 + M06 預訂完成 |
| Sprint 11 IT 測試通過 | ✅ 374 tests PASS |
| Sprint 11 E2E 測試通過 | ✅ CartControllerE2ETest 12/12 |
| CI Pipeline 驗證 | ⏳ PENDING - 等待 GitHub 額度恢復 (2026-06-01) |
| Sprint 12 開始日期 | ✅ **2026-05-14** (今天立即開始) |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 12 |
| **開始日期** | 2026-05-14 (今天) |
| **結束日期** | 2026-05-25 (週一) |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 待規劃 |
| **Buffer** | ~13% |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M18 知識管理系統（Phase 2-A）媒體中心功能 + M07 金流前期準備（Payment Mock），為平台提供基礎設施能力。

### 具體目標

#### M18 知識管理 Backend 功能 (Phase 2-A)

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 媒體中心 Backend | P0 | 媒體上傳、分類、管理 API |
| 知識庫文章 Backend | P1 | 文章 CRUD、分類、發布 |
| FAQ 管理 Backend | P1 | FAQ 分類、解答、管理 |

#### M07 金流前期準備

| 功能 | 優先級 | 說明 |
|------|--------|------|
| Payment Mock | P1 | 支付模擬，為 Phase 2-B 金流做準備 |
| 訂單支付流程 | P2 | 支付狀態機 |

#### M09 通知系統準備

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 通知模板系統 | P1 | 通知模板定義與管理 |
| 通知服務基礎建設 | P2 | 異步通知基礎設施 |

---

## 3. PRD v1.0 Phase 1 完成狀態

### Phase 1 已完成模組

| 模組 | 名稱 | Sprint | 狀態 |
|------|------|--------|------|
| M01 | 商品中心 | Sprint 3-4 | ✅ COMPLETED |
| M02 | 房源中心 | Sprint 3-4 | ✅ COMPLETED |
| M03 | 認證服務 | Sprint 3 | ✅ COMPLETED |
| M04 | 購物車與促銷 | Sprint 11 | ✅ COMPLETED |
| M05 | 零售訂單履約 | Sprint 5-6 | ✅ COMPLETED |
| M06 | 預訂與日曆鎖定 | Sprint 11 | ✅ COMPLETED |
| M12 | 動態定價 | Sprint 8 | ✅ COMPLETED |
| M15 | 內容管理 | Sprint 9 | ✅ COMPLETED |
| M16 | ERP 進銷存 | Sprint 10 | ✅ COMPLETED |
| M17 | 租戶管理 | Sprint 9 | ✅ COMPLETED |

### Phase 2-A 進行中模組

| 模組 | 名稱 | 優先級 | 說明 |
|------|------|--------|------|
| **M18** | 知識管理 | **P0** | 媒體中心 + 知識庫 + FAQ |
| **M07** | 金流 | **P1** | Payment Mock 準備 |

### Phase 2+ 模組（未來規劃）

| 模組 | 名稱 | Phase |
|------|------|-------|
| M07 | 金流（完整） | Phase 2-B |
| M08 | 評價系統 | Phase 3 |
| M09 | 通知系統 | Phase 2 |
| M10 | 即時通訊 | Phase 3 |
| M11 | 物流系統 | Phase 3 |
| M13 | 商家工作台 | Phase 2+ |

---

## 4. User Stories 摘要

| US ID | 標題 | SP | 優先級 |
|-------|------|-----|--------|
| US-M18-001 | BE: 媒體中心 Backend | 5 | P0 |
| US-M18-002 | BE: 知識庫文章 Backend | 3 | P1 |
| US-M18-003 | BE: FAQ 管理 Backend | 3 | P1 |
| US-M07-001 | BE: Payment Mock 準備 | 3 | P1 |
| US-M07-002 | BE: 訂單支付狀態機 | 5 | P2 |
| US-M09-001 | BE: 通知模板系統 | 3 | P1 |
| **待補充** | 視評估調整 | | |

---

## 5. 任務分解（初步）

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 |
|---------|----------|-----|--------|--------|
| Task-M18-101 | Backend: 媒體中心 Service + API | 5 | Dev | P0 |
| Task-M18-102 | Backend: 知識庫文章 Service + API | 3 | Dev | P1 |
| Task-M18-103 | Backend: FAQ 管理 Service + API | 3 | Dev | P1 |
| Task-M07-101 | Backend: Payment Mock | 3 | Dev | P1 |
| Task-M07-102 | Backend: 訂單支付狀態機 | 5 | Dev | P2 |
| Task-M09-101 | Backend: 通知模板系統 | 3 | Dev | P1 |
| Task-M18-104 | FE: 媒體中心頁面 | 3 | FE Dev | P0 |
| Task-M18-105 | FE: 知識庫頁面 | 3 | FE Dev | P1 |
| Task-M09-102 | FE: 通知管理頁面 | 3 | FE Dev | P1 |
| Task-M18-106 | IT: M18 媒體中心整合測試 | 3 | QA | P0 |
| Task-M07-103 | IT: M07 Payment Mock 測試 | 3 | QA | P1 |
| Task-M12-101 | IT: M09 通知模板測試 | 2 | QA | P1 |
| **合計** | | **38 SP** | | |

> ⚠️ **注意**: 任務分解為初步估計，實際 SP 可能需要根據詳細設計調整

---

## 6. Sprint 12 前置準備檢查清單

### 6.1 Backend ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| Redis | ✅ 運行中 | Port 6379，含 REDIS_PASSWORD |
| S3/MinIO | ⏳ 待確認 | 媒體上傳需要物件儲存 |
| M03 認證 | ✅ 已存在 | JWT + Refresh Token |

### 6.2 Backend 需要新增

| 項目 | 狀態 | 備註 |
|------|------|------|
| MediaService | ⏳ 待建立 | 媒體上傳、下載、管理 |
| KnowledgeBaseService | ⏳ 待建立 | 知識庫文章 CRUD |
| FaqService | ⏳ 待建立 | FAQ 管理 |
| PaymentMockService | ⏳ 待建立 | 支付模擬 |
| NotificationTemplateService | ⏳ 待建立 | 通知模板 |

### 6.3 Frontend ⏳ 待開始

| 項目 | 狀態 | 備註 |
|------|------|------|
| 媒體中心頁面 | ⏳ 待開始 | /dashboard/media |
| 知識庫頁面 | ⏳ 待開始 | /dashboard/knowledge |
| 通知管理頁面 | ⏳ 待開始 | /dashboard/notifications |

### 6.4 測試環境 ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| Backend | ✅ 運行中 | http://localhost:8080 |
| Frontend | ✅ 運行中 | http://localhost:3000 |
| PostgreSQL | ✅ 運行中 | Port 5432 |
| Redis | ✅ 運行中 | Port 6379 |

---

## 7. Phase 2-A 過渡規劃

```
Phase 1 完成 ✅ (Sprint 1-11)
    ↓
Sprint 12: M18 知識管理 + M07 Payment Mock
    ↓
Sprint 13: M07 金流完整版 + M09 通知系統
```

### 7.1 M18 媒體中心技術考量

**媒體存儲策略**:
- Phase 1 使用本地存儲或 MinIO（S3 相容）
- 支援圖片（JPG/PNG/GIF/WebP）、影片（MP4/MOV）、文檔（PDF）
- 按 Tenant 分隔：`/{tenant_id}/media/*`

**資料模型**:
- `media_categories` - 分類表（支援階層）
- `media_assets` - 媒體資產表（擴展現有）

### 7.2 M07 金流技術考量

**Payment Mock 策略**:
- 模擬支付成功/失敗場景
- 為 Phase 2-B Stripe/LinePay 整合預留介面
- 使用 State Pattern 實作支付狀態機

### 7.3 M09 通知模板技術考量

**通知模板格式**:
- 使用 Handlebars-like 語法 `{{variable}}`
- 支援多語言（i18n key）
- 區分 Email/SMS/Push 模板

---

## 8. 風險追蹤

| 風險 ID | 等級 | 說明 | 緩解措施 | 狀態 |
|---------|------|------|----------|------|
| R-012-001 | 中 | 媒體上傳檔案大小限制 | 設定合理限制（如 10MB）並提示 | ⏳ |
| R-012-002 | 中 | M07 支付狀態機複雜度 | 先做 Payment Mock 再擴展 | ⏳ |
| R-012-003 | 低 | M18 知識庫搜尋功能 | Phase 1 先做基礎搜尋 | ⏳ |

---

## 9. 成功標準

| 標準 | 目標 | 狀態 |
|------|------|------|
| M18 媒體中心 Backend | 可正常運作 | ⏳ |
| M07 Payment Mock | 可正常運作 | ⏳ |
| M09 通知模板系統 | 可正常運作 | ⏳ |
| Backend IT 測試通過 | 15+/15+ | ⏳ |
| E2E 測試通過 | 5/5+ | ⏳ |
| 無 High 缺陷 | High = 0 | ⏳ |

---

## 10. 與前一 Sprint 的差異

| 項目 | Sprint 11 | Sprint 12 |
|------|---------|---------|
| **焦點** | M04 購物車 + M06 預訂 | M18 知識管理 + M07 金流準備 |
| **新技術** | Redis Hash + 分散式鎖 | 媒體上傳 + 支付模擬 |
| **測試** | IT 10 + E2E 12 | IT 15+ + E2E 5+ |
| **前端** | 購物車 + 預訂流程頁面 | 媒體中心 + 知識庫頁面 |

---

**文件狀態**: 📝 草稿 - 待 Sprint Planning 會議確認
**下一步**:
1. 🔴 Sprint Planning 會議確認 Sprint 12 目標和範圍
2. ✅ Backlog 精煉完成
3. ⏳ 等待 Sprint 11 CI 驗證完成 (2026-06-01)

**相關文件**:
- [SPRINT_11_PLAN.md](SPRINT_11_PLAN.md)
- [SPRINT_11_TASKS.md](../05_development/SPRINT_11_TASKS.md)
- [E-Commerce_PRD_v1.0_Final.md](../../01_requirements/E-Commerce_PRD_v1.0_Final.md) (§6.2 M07, §6.9 M12, §6.10 M18)
- [M18_Knowledge_Management_SPEC.md](../../01_requirements/M18_Knowledge_Management_SPEC.md)
- [DEFERRED_ITEMS_TRACKER.md](../04_planning/DEFERRED_ITEMS_TRACKER.md)