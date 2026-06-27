# Deferred Items Tracker / 延後項目追蹤器

> **用途**: 追蹤所有被延後到未來 Sprint 的工作項目
> **更新原則**: 每個 Sprint Review 後必須更新此文件
> **審查時機**: Sprint Planning 前必須先閱讀此文件

---

## 活躍延後項目 / Active Deferred Items

### 🔴 高優先級 - 下一 Sprint 應優先處理

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 | 最後更新 |
|----|------|-------------|----------|----------|---------|------|----------|
| DEF-005 | M10 IM SA 需求分析（文件，不開發） | Sprint 21 Buffer-B | 核心 US 填滿容量，已連續延後兩次 | 無 | 2 SP | 🔴 Sprint 22 必須執行（AI-602） | 2026-06-27 |

---

### 🟡 中優先級 - 未來 Sprint 處理

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 | 最後更新 |
|----|------|-------------|----------|----------|---------|------|----------|
| DEF-006 | M11 第三方 Provider API Stub 強化 | Sprint 21 Buffer-C | 核心 US 填滿容量，Buffer-C 未啟動 | 無 | 1 SP | 🟡 Sprint 22 Buffer | 2026-06-27 |

---

### 🟢 低優先級 - 長期規劃

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 | 最後更新 |
|----|------|-------------|----------|----------|---------|------|----------|
| DEF-007 | M11 物流與訂單履約流程整合 | Sprint 21 未規劃 | LogisticsProvider 策略完成但未接入訂單流程 | M12 定價穩定後 | 3 SP | 🟢 Sprint 23+ | 2026-06-27 |
| DEF-008 | ShippingTemplate 接入訂單結帳流程 | Sprint 21 未規劃 | 運費模板 CRUD 完成但未接入訂單結帳計算 | DEF-007 之後 | 2 SP | 🟢 Sprint 23+ | 2026-06-27 |

---

## 已完成延後項目 / Completed Deferred Items

| ID | 標題 | 移出 Sprint | 完成 Sprint | 備註 |
|----|------|-------------|-------------|------|
| DEF-001 | Booking E2E 完整預訂流程測試 | Sprint 5 | Sprint 6 | 已實作統一端點 + Feature Toggle + 測試資料，E2E 測試通過 |
| DEF-004 | listings.tags 欄位類型修復 | Sprint 7 | Sprint 10 | V8__Fix_Listings_Tags_Column_Type.sql migration 已建立，測試通過 (6/6) |

---

## Sprint 歷史紀錄

### Sprint 21 (2026-06-27)

**新增延後**:
- DEF-005: M10 IM SA 需求分析（Buffer-B 連續兩次延後，Sprint 22 必須執行）
- DEF-006: M11 Provider Stub 強化（Buffer-C 未啟動）
- DEF-007: M11 物流與訂單整合（Sprint 21 未規劃，Sprint 23+）
- DEF-008: ShippingTemplate 接入結帳流程（Sprint 21 未規劃，Sprint 23+）

**移除延後**:
- (無)

**更新**:
- Sprint 21 完成，6/6 US 全數達成（含 Buffer-A US-006）
- Sprint 21 Integration Tests: 293 tests, 0 Failures
- Sprint 21 v2026.08.15-01 已發布
- Sprint 22 開始規劃（M12 動態定價 + M13 商家工作台）

---

### Sprint 12 (2026-06-15)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 12 完成，M18 知識管理 Phase 2-A + M07 Payment Mock + M09 通知模板已交付
- Sprint 12 v12.0.0 已發布 (release/v2026.06.15-01)
- Sprint 13 開始規劃 (M18 Phase 2-B + M08 評價系統)

### Sprint 11 (2026-06-01)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 11 完成，M04 購物車 + M06 預訂完整化已交付
- Sprint 11 QA 驗證完成 (2026-05-14)
- 374 tests PASS
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 10 (2026-05-18)

**新增延後**:
- (無)

**移除延後**:
- DEF-004: listings.tags 欄位類型修復 ✅ 已完成 (V8__Fix_Listings_Tags_Column_Type.sql)

**更新**:
- Sprint 10 完成，M16 ERP 進銷存模組已交付
- Sprint 10 進入發布評審階段 (2026-05-13)
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 9 (2026-05-06)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- DEF-001: Sprint 6 完成，移至已完成延後項目

---

## 使用說明

### 添加新延後項目

1. 在「活躍延後項目」區塊新增列
2. 填寫所有欄位（ID、標題、原始 Sprint、延後原因、前置需求、預估 SP）
3. 在「Sprint 歷史紀錄」區塊新增 entry
4. 狀態標記為 ⚠️ 待處理

### 完成延後項目

1. 將項目從「活躍延後項目」移到「已完成延後項目」
2. 填寫完成 Sprint
3. 狀態改為 ✅ 已完成

### Sprint Planning 前檢查清單

- [ ] 閱讀本文件
- [ ] 確認所有 ⚠️ 待處理 項目是否已具備執行條件
- [ ] 將具備條件的項目納入 Sprint Plan
- [ ] 更新本文件的狀態欄位

---

**文件版本**: v1.5
**最後更新**: 2026-06-27
**下次審查**: Sprint 22 Planning（須納入 DEF-005 M10 SA 分析）
