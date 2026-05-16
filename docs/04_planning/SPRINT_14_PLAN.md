# Sprint 14 計劃 / Sprint 14 Plan

> **Sprint 編號**: Sprint 14
> **期間**: 2026-06-29 ~ 2026-07-10 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-16
> **基於**: Sprint 13 完成 + PRD v1.0 Phase 2 範圍

---

## 🔴 前置條件確認

**Sprint 13 發布**: ✅ **RELEASED** - 2026-06-16 v13.0.0 已發布

| 項目 | 確認結果 |
|------|----------|
| Sprint 13 開發完成 | ✅ M18 版本控制 + M08 評價系統 Phase 1 |
| Sprint 13 IT 測試通過 | ✅ 16 tests exist (M18/M08) |
| Release Tag | ✅ v2026.05.16-01 |
| Sprint 14 開始日期 | ✅ **2026-06-29** |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 14 |
| **開始日期** | 2026-06-29 (週一) |
| **結束日期** | 2026-07-10 (週五) |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 待規劃 |
| **Buffer** | ~13% |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M09 通知系統 Phase 2（MQ 非同步發送機制）+ M07 金流 Phase 2-B 準備（Stripe 整合介面），為平台生產環境上線做準備。

### 具體目標

#### M09 訊息通知中心 Phase 2

| 功能 | 優先級 | 說明 |
|------|--------|------|
| MQ 非同步發送 | P0 | RabbitMQ/Redis Stream 實現 |
| 通知模板系統升級 | P1 | 變量語法增強、模板版本管理 |
| 多元通知管道 | P1 | Email, SMS, App Push 整合 |

#### M07 金流 Phase 2-B

| 功能 | 優先級 | 說明 |
|------|--------|------|
| Stripe 整合介面 | P0 | Payment Intent API 預留 |
| LinePay 整合介面 | P1 | 統一支付介面包裝 |
| 退款機制完善 | P1 | 全額/部分退款流程 |

#### M18 知識管理 Phase 2-C

| 功能 | 優先級 | 說明 |
|------|--------|------|
| FAQ 進階功能 | P1 | 置頂排序、關鍵字高亮 |
| 標籤聚合搜尋 | P2 | 文章標籤統計、標籤雲 |

---

## 3. PRD v1.0 Phase 完成狀態

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

### Phase 2 進行中模組

| 模組 | 名稱 | Sprint | 狀態 |
|------|------|--------|------|
| M18 | 知識管理 Phase 2-A/B | Sprint 12-13 | ✅ Phase 2-B COMPLETED |
| M08 | 評價系統 Phase 1 | Sprint 13 | ✅ Phase 1 COMPLETED |
| M07 | 金流 Phase 2-A (Mock) | Sprint 12 | ✅ Phase 2-A COMPLETED |
| M09 | 通知系統 Phase 1 | Sprint 12 | ✅ Phase 1 COMPLETED |

### Phase 2-B 規劃中模組

| 模組 | 名稱 | 優先級 | Sprint 目標 |
|------|------|--------|-------------|
| **M09** | 通知系統 Phase 2 | **P1** | M09 Phase 2 開始 (MQ) |
| **M07** | 金流 Phase 2-B | **P1** | Stripe/LinePay 介面準備 |
| M18 | 知識管理 Phase 2-C | P2 | FAQ 進階功能 |

### Phase 2+ 模組（未來規劃）

| 模組 | 名稱 | Phase |
|------|------|-------|
| M10 | 即時通訊 (IM) | Phase 2+ |
| M11 | 物流追蹤 | Phase 2+ |
| M08 | 評價系統 Phase 2 | Phase 2+ |

---

## 4. Sprint 14 User Stories

### US-001: 通知系統 MQ 非同步發送

**描述**:
作為系統，我需要實現通知的 MQ 非同步發送機制，以便提升系統效能和使用者體驗。

**驗收標準**:
- [ ] AC-001: 通知發送改為非同步處理
- [ ] AC-002: 支援 RabbitMQ/Redis Stream 作為訊息佇列
- [ ] AC-003: 通知發送失敗時有重試機制

**技術備註**:
- 需要評估 RabbitMQ vs Redis Stream 的適用場景
- 訊息持久化配置
- Dead Letter Queue 處理

**依賴**:
- M09 通知系統 Phase 1 已完成

---

### US-002: 支付系統 Stripe 整合準備

**描述**:
作為系統，我需要預留 Stripe 支付整合介面，以便 Phase 2-B 生產環境上線。

**驗收標準**:
- [ ] AC-001: 建立 Stripe Payment Intent API 介面包裝
- [ ] AC-002: 統一支付介面（Stripe/LinePay）抽象層
- [ ] AC-003: Webhook 處理機制預留

**技術備註**:
- Stripe API Key 配置管理
- 支付安全驗證
- 交易紀錄持久化

**依賴**:
- M07 金流 Phase 2-A (Payment Mock) 已完成

---

### US-003: 知識庫 FAQ 進階功能

**描述**:
作為 StoreOwner，我希望能對 FAQ 文章進行置頂排序和關鍵字高亮，以便提供更好的使用者體驗。

**驗收標準**:
- [ ] AC-001: 支援 FAQ 文章置頂排序
- [ ] AC-002: 支援搜尋關鍵字高亮顯示
- [ ] AC-003: FAQ 分類統計 API

**技術備註**:
- 置頂排序邏輯實作
- 搜尋結果關鍵字標記

**依賴**:
- M18 知識管理 Phase 2-A 已完成

---

### US-004: 退款機制完善

**描述**:
作為系統，我需要完善退款機制，支援全額和部分退款流程。

**驗收標準**:
- [ ] AC-001: 支援全額退款
- [ ] AC-002: 支援部分退款
- [ ] AC-003: 退款狀態追蹤

**技術備註**:
- Stripe Refund API 整合
- 退款金額驗證

**依賴**:
- US-002 Stripe 整合準備已完成

---

## 5. 技術可行性評估

### 架構影響分析

| US | 複雜度 | 架構影響 | 技術風險 |
|----|--------|----------|----------|
| US-001 | 高 | 中 | MQ 技術選型 |
| US-002 | 中 | 中 | Stripe API 熟悉度 |
| US-003 | 低 | 低 | 現有程式碼延伸 |
| US-004 | 中 | 低 | Stripe Refund API |

### 新技術依賴

| 技術 | 用途 | 風險 |
|------|------|------|
| RabbitMQ / Redis Stream | MQ 非同步發送 | 中 - 需要額外基礎設施 |
| Stripe SDK | 支付整合 | 低 - 文件完善 |

---

## 6. Story Points 估算

| US ID | 標題 | 複雜度 | 不確定性 | SP |
|-------|------|--------|----------|-----|
| US-001 | 通知系統 MQ 非同步發送 | 高 | 中 | 8 |
| US-002 | 支付系統 Stripe 整合準備 | 中 | 低 | 5 |
| US-003 | 知識庫 FAQ 進階功能 | 低 | 低 | 3 |
| US-004 | 退款機制完善 | 中 | 中 | 5 |

**Story Points 估算參考**:
- 1: 半天內完成
- 2: 一天內完成
- 3: 2-3 天
- 5: 一週
- 8: 需拆分
- 13: 必須拆分

---

## 7. Sprint 承諾

**Sprint 目標**:
> 完成 M09 通知系統 Phase 2（MQ 非同步發送機制）+ M07 金流 Phase 2-B 準備（Stripe 整合介面），為平台生產環境上線做準備。

**承諾的 User Stories**:

| 優先級 | US ID | 標題 | SP | 負責人 |
|--------|-------|------|----|-------|
| P0 | US-001 | 通知系統 MQ 非同步發送 | 8 | Dev |
| P0 | US-002 | 支付系統 Stripe 整合準備 | 5 | Dev |
| P1 | US-003 | 知識庫 FAQ 進階功能 | 3 | Dev |
| P1 | US-004 | 退款機制完善 | 5 | Dev |

**總 SP**: 21 SP
**團隊容量**: 30 SP
**填充率**: 70%

**風險**:
1. MQ 技術選型（RabbitMQ vs Redis Stream）需要事先評估 - 緩解措施：參考現有基礎設施
2. Stripe API 需要時間熟悉 - 緩解措施：預留文件閱讀時間

---

## 8. 測試規劃

### 測試範圍

| US ID | 測試重點 |
|-------|----------|
| US-001 | MQ 訊息發送、非同步處理、重試機制 |
| US-002 | Stripe API Mock、統一介面包裝 |
| US-003 | 置頂排序、關鍵字高亮 |
| US-004 | 退款流程、狀態追蹤 |

### 測試類型

- [ ] 單元測試 (Dev)
- [ ] 整合測試 (Dev/QA)
- [ ] E2E 測試 (QA)

---

## 9. Sprint 14 Definition of Done

- [ ] 代碼完成且通過 Review
- [ ] 單元測試覆蓋率 >= 80%
- [ ] 整合測試通過
- [ ] MQ 訊息佇列運作正常
- [ ] Stripe API 介面包裝完成
- [ ] API 文件更新

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-16
**驗證人**: Claude Code (AI Assistant)
**Sprint 14 狀態**: 🔴 **規劃中**