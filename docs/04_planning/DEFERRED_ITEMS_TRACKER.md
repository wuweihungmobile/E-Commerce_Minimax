# Deferred Items Tracker / 延後項目追蹤器

> **用途**: 追蹤所有被延後到未來 Sprint 的工作項目
> **更新原則**: 每個 Sprint Review 後必須更新此文件
> **審查時機**: Sprint Planning 前必須先閱讀此文件

---

## 活躍延後項目 / Active Deferred Items

### 🔴 高優先級 - 下一 Sprint 應優先處理

*目前無高優先級延後項目。*

---

### 🟡 中優先級 - 未來 Sprint 處理

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 |
|----|------|-------------|---------|---------|---------|------|
| DEF-009 | Logistics.logisticsData jsonb 映射慣例統一 | Sprint 25（US-002 盤點發現） | 低優先：現況 `String + columnDefinition="jsonb"` 功能正常、validate 通過，與全庫 `Map + @JdbcTypeCode(SqlTypes.JSON)` 慣例不一致，但無漂移風險。為避免擴大變更面（Rule 3）暫不修改 | 待 Logistics 需結構化讀寫該欄位時一併處理 | 1 | ⚠️ 待處理（低） |
| DEF-010 | OrderStateMachine 一致性清理（SHIPPING→CANCELLED） | Sprint 25（US-006 規則確認發現） | PM/PO 確認 SHIPPING 不可取消，但 OrderStateMachine 轉換表仍允許 SHIPPING→CANCELLED，與 canCancel() 矛盾。US-006 為調查型不含實作（Rule 3） | 無（cancelOrder 已以 canCancel() 守門，實務不受影響） | 1 | ⚠️ 待處理（低） |
| DEF-011 | cancelLogistics 錯誤碼修正（E_7000/E_7002 → E_7500 系列） | Sprint 25（US-006 規則確認發現） | cancelLogistics 誤用 Supplier/PO 錯誤碼，應改物流專用 E_7500 系列；US-006 不含實作 | 一併補物流取消整合測試 | 1 | ⚠️ 待處理（低） |
| DEF-012 | ChatService.toMessageResponse 廣播 conversationId 補正 | Sprint 25（US-004 live E2E 發現） | STOMP 廣播 payload 的 conversationId 為 null，前端已以訂閱 id 補正；建議後端修正以利其他 client（mobile 等） | 無 | 1 | ⚠️ 待處理（低） |

---

## 已完成延後項目 / Completed Deferred Items

| ID | 標題 | 移出 Sprint | 完成 Sprint | 備註 |
|----|------|-------------|-------------|------|
| DEF-001 | Booking E2E 完整預訂流程測試 | Sprint 5 | Sprint 6 | 已實作統一端點 + Feature Toggle + 測試資料，E2E 測試通過 |
| DEF-004 | listings.tags 欄位類型修復 | Sprint 7 | Sprint 10 | V8__Fix_Listings_Tags_Column_Type.sql migration 已建立，測試通過 (6/6) |
| DEF-005 | M10 IM SA 需求分析 | Sprint 21 Buffer-B | Sprint 22 | M10_IM_REQUIREMENTS.md 建立，PM/PO Victoria APPROVED |
| DEF-006 | M11 Provider Stub 強化 | Sprint 21 Buffer-C | Sprint 22 | HCT/TCAT 追蹤號改為 {Provider}-{yyyyMMdd}-{HEX8} 格式 |
| DEF-007 | M11 物流與訂單履約流程整合 | Sprint 21 | Sprint 23 | createLogistics 前置驗證 + 訂單狀態同步 SHIPPING/DELIVERED，4 個整合測試通過 |
| DEF-008 | ShippingTemplate 接入訂單結帳流程 | Sprint 21 | Sprint 23 | V47 Migration + shippingFee 欄位 + 免運門檻邏輯，3 個整合測試通過 |

---

## Sprint 歷史紀錄

### Sprint 25 (2026-06-29，進行中)

**新增延後**:
- DEF-009（🟡 中優先，實際低急迫）: Logistics.logisticsData jsonb 映射慣例統一 — US-002 全庫盤點發現的唯一慣例不一致，列為技術債，不在本 US 動工

**移除延後（已完成）**:
- （無）

**更新**:
- US-001（P0，AI-901）✅ 完成：建立 `make validate-schema` schema 漂移守門關卡，雙向驗證（正向 exit 0 / 負向 exit 1 攔下 missing column）
- US-002（P1，AI-902）✅ 完成：[ENTITY_MIGRATION_AUDIT.md](../06_quality/ENTITY_MIGRATION_AUDIT.md) — 51 entity 全數通過 validate，零孤兒表、零 `SqlTypes.ARRAY` 殘留、3 個歷史 `TEXT[]` 全部封閉；固化防漂移慣例

### Sprint 24 (2026-06-29)

**新增延後**:
- （無正式 DEF 項目）Buffer US-004（SSH pre-push 優化）+ US-005（M11 物流取消流程）未啟動，改以 Retro Action Items 追蹤（AI-804 / AI-903，延續 Sprint 25）

**移除延後（已完成）**:
- （無，本 Sprint 無活躍 DEF 項目）

**更新**:
- Sprint 24 承諾範圍 100% 完成：US-001~003（7 SP，P0+P1）
- AI-801（TestSecurityContextHelper）+ AI-803（M13 Redis TTL）完成；AI-802 / AI-804 延續 Sprint 25
- **計畫外重大事件**：GitHub E2E 暴露 backend 啟動失敗，投入 9 個 commit 修復 schema 漂移（Flyway V48~V55：補齊 5 張缺漏建表 + 統一 ARRAY→jsonb）
- **根因**：本地 act 用 `ddl-auto=update`，GitHub E2E 用 schema 驗證 → entity/migration 漂移本地偵測不到（已記入 Retro AI-901 P0）
- Buffer 容量（3 SP）被 E2E 救火完全佔用，連續 3 Sprint 以來首次 Buffer 0% 啟動
- Sprint 24 v2026.09.26-01 發布
- **新增 Action Items（Sprint 25）**：AI-901 本地 schema 驗證關卡（P0）、AI-902 entity↔migration 一致性盤點（P1）、AI-903 M11 取消流程業務規則確認

---

### Sprint 23 (2026-06-27)

**新增延後**:
- （無新增延後項目）

**移除延後（已完成）**:
- DEF-007: M11 物流與訂單履約整合 ✅ Sprint 23 US-004 完成（createLogistics + 狀態同步，4 個整合測試）
- DEF-008: ShippingTemplate 接入結帳流程 ✅ Sprint 23 US-005 Buffer-A 完成（V47 + shippingFee + 免運邏輯，3 個整合測試）

**更新**:
- Sprint 23 完成，6/6 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 23 Integration Tests: ~317 tests, 0 Failures（新增 +15）
- Sprint 23 Unit Tests: ~326（新增 +3）
- act CI（make validate-all）整體通過（2026-06-27 19:48:47）
- Sprint 23 v2026.09.12-01 發布
- Sprint 24 開始規劃（TestSecurityContextHelper + Redis Cache TTL + M10 WebSocket 評估）
- **DEF 清零**: 所有活躍延後項目（DEF-007/008）全數完成，無新增 DEF

---

### Sprint 22 (2026-06-27)

**新增延後**:
- DEF-007（升級為 🔴 高優先級）: M11 物流與訂單整合 → Sprint 23 P1（AI-704 延續 AI-603）
- DEF-008: ShippingTemplate 接入結帳流程 → Sprint 23 Buffer

**移除延後（已完成）**:
- DEF-005: M10 IM SA 需求分析 ✅ Sprint 22 US-004 完成，Victoria APPROVED
- DEF-006: M11 Provider Stub 強化 ✅ Sprint 22 US-005 完成

**更新**:
- Sprint 22 完成，5/5 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 22 Integration Tests: ~302 tests, 0 Failures（新增 +9）
- Sprint 22 Unit Tests: ~323（新增 +3）
- Sprint 22 v2026.08.29-01 發布
- Sprint 23 開始規劃（M10 IM 後端 REST + M11 訂單整合）

---

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

**文件版本**: v1.9
**最後更新**: 2026-06-29（Sprint 25 US-001/US-002 完成，新增 DEF-009）
**下次審查**: Sprint 25 Review（追蹤 DEF-009 + 剩餘 US-003/004）
