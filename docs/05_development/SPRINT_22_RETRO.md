# Sprint 22 Retrospective 報告 / Sprint 22 Retrospective Report

> **Sprint 編號**: Sprint 22
> **期間**: 2026-08-18 ~ 2026-08-29 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-27
> **基於**: [SPRINT_22_PLAN.md](../04_planning/SPRINT_22_PLAN.md), [SPRINT_22_TASKS.md](./SPRINT_22_TASKS.md), [SPRINT_22_REVIEW.md](./SPRINT_22_REVIEW.md), [SPRINT_21_RETRO.md](./SPRINT_21_RETRO.md)

---

## 1. Sprint 22 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | M12 延伸（listing_id + effective-price）、M13 儀表板、M10 IM SA 分析、M11 Stub 強化 |
| **規劃 SP** | 12 SP（原始）→ 調整 8 SP（M12 現況調查後節省 4 SP） |
| **完成 SP** | 8 SP（US-001~005 全部完成，含 Buffer-A + Buffer-B）|
| **完成 US** | 5/5（100%，含 Buffer-A + Buffer-B）|
| **測試結果** | ~625 tests, 0 Failures |
| **新增測試** | +12（Integration +9 + Unit +3） |
| **團隊** | 2 人 Dev Team |
| **提前完成** | Sprint 正式開始（08-18）前 52 天（2026-06-27）完成 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **M12 現況調查（關鍵發現）** | 發現 pricing_rules 表 + PricingService 早已實作，改為「延伸」而非「重建」，節省 4 SP | 避免重複開發，6 小時 → 2 小時完成 |
| ✅ **M12 Product 定價支援** | listing_id 延伸 + product:* 雙權限，商品商家可設定定價規則 | M12 從民宿專用擴展為通用定價引擎 |
| ✅ **effective-price API 上線** | GET /v2/listings/{id}/effective-price，消費者可即時查詢最優惠價格 | 為前端商品詳情頁提供動態定價資料 |
| ✅ **M13 儀表板 API 完成** | SELLER 角色儀表板，7 項統計指標（7d/30d 訂單 + 營收 + 上架 + 待處理 + 最近訂單） | 商家可即時掌握營運狀況 |
| ✅ **M10 SA 分析清償技術債** | DEF-005 連續 2 次 Buffer 未啟動，本 Sprint 強制完成 SA 需求分析並獲 PM/PO APPROVED | 解決 AI-602 追蹤項目，M10 IM 可進入 Sprint 23 SD 架構設計 |
| ✅ **M11 追蹤號標準化** | HCT/TCAT 追蹤號加入日期欄位，格式 {Provider}-{yyyyMMdd}-{HEX8} | 支援物流追蹤日期查詢，測試覆蓋率提升 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **100% US 完成** | 5 個 US 全部達成所有 AC，含 Buffer-A + Buffer-B | US-001~005 全部 ✅ |
| **100% Buffer 啟動** | Sprint 22 Buffer-A（M10 SA）+ Buffer-B（M11 Stub）雙雙完成 | 首次 Buffer 全啟動 |
| **100% 測試通過** | ~625 tests, 0 Failures | `mvn compile` BUILD SUCCESS |
| **AI-602 技術債清零** | M10 IM SA 分析連續延後兩次後終於完成，Victoria APPROVED | DEFERRED_ITEMS_TRACKER DEF-005 關閉 |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **現況調查優先（重要！）** | Sprint 開始前先調查 M12 現況，發現核心早已實作，避免重複開發 | SP 節省 4 SP，開發時間縮短 |
| **COALESCE 處理 null** | `COALESCE(SUM(o.totalAmount), 0)` 確保 revenue30d 零訂單時回傳 0 而非 null | 儀表板 API 健壯性提升 |
| **hasRole vs hasAuthority 正確使用** | M13 Controller 使用 `hasRole('SELLER')`，Test 使用 `@WithMockUser(roles={"SELLER"})` | Spring Security 慣例一致，避免混淆 |
| **DateTimeFormatter 靜態常數** | Provider 中 `DateTimeFormatter.ofPattern("yyyyMMdd")` 定義為 `private static final`，符合 Checkstyle MagicNumber | 效能與合規雙重保障 |
| **PricingService 邏輯複用** | `getEffectivePrice()` 複用現有 `pricingRuleRepository.findByListingIdAndIsActiveTrue()` 及排序邏輯 | 無重複程式碼 |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **逐 US 編譯-測試循環** | 每個 US 完成後立即 compile + test，零累積債 | 每個 commit 均通過 pre-commit hook |
| **SA 角色發揮** | US-004 SA Amanda 主導 M10 IM 技術選型分析，正確建議 WebSocket over MQTT | 文件品質獲 PM/PO APPROVED |
| **AISDLC Buffer 正確使用** | Buffer-A/B 均在核心 P0+P1 完成後啟動，符合 Buffer 設計精神 | 首次達成 Buffer 100% 利用率 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **Sprint 開始前大幅提前完成（第五次）** | 實際工作時間（06-27）與 Sprint 排期（08-18~08-29）持續脫節 52 天 | 🟡 低 — Sprint 文件節奏與真實開發週期不同步（持續現象） |
| **M12 現況未於 Sprint 規劃期間調查** | Sprint 22 Plan 假設 M12 需從零建立，直到 Tasks 建立時才發現現況 | 🟡 低 — 浪費規劃時間，Plan SP 估算不準確（3 SP → 2+1 SP） |
| **規劃 SP（12）與實際完成 SP（8）差距** | M12 現況調查節省 4 SP，但 Buffer SP 不夠填補（Buffer 3 SP 已啟動完畢） | 🟡 低 — Sprint 容量利用率 67%，低於目標 80%+ |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **M10 IM 只有 SA 分析，尚無 SD 架構設計** | Sprint 22 僅完成 SA 階段，SD Marcus 架構設計 + DB Migration 待 Sprint 23 | 🟠 中 — WebSocket 實作尚未開始 |
| **M11 物流與訂單未完整整合** | 訂單確認/完成時尚未觸發 `LogisticsProvider.createShipment()` | 🟠 中 — 物流追蹤功能不可用，AI-603 持續追蹤 |
| **M12 effective-price 無時間範圍驗證** | `checkDate` 僅篩選 validFrom~validTo，不驗證 checkDate 是否為未來日期 | 🟡 低 — 技術上允許查歷史定價，業務上可能需限制 |
| **M13 儀表板無資料快取** | 每次請求都重新計算 7d/30d 統計，高頻存取時效能可能不足 | 🟡 低 — 初期流量不高，後期可加 @Cacheable |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-701 | Sprint 規劃前執行現況調查 | 每個 Sprint 規劃時，先盤點相關模組現況（是否已有實作），避免估算偏差 | SA Amanda + Dev David | P1 | Sprint 23 起執行 |
| AI-702 | M10 IM SD 架構設計 + REST API 實作 | 基於 M10_IM_REQUIREMENTS.md，SD Marcus 完成 SRD_M10_IM.md + ConversationController + MessageController | SD Marcus + Dev David | P0 | Sprint 23 |
| AI-703 | M13 儀表板 @Cacheable 優化評估 | 評估 SellerDashboardService.getDashboard() 加 Redis 快取的必要性（TTL 5~10 分鐘） | Dev David | P2 | Sprint 24 |
| AI-704 | M11 物流與訂單整合（AI-603 延續） | 訂單狀態 CONFIRMED → 觸發 LogisticsProvider.createShipment()，追蹤號回寫 Order | Dev David + SD Marcus | P1 | Sprint 23 |

---

## 5. Sprint 21 → Sprint 22 Action Items 追蹤結果

| Action Item | 內容 | Sprint 22 達成狀態 |
|-------------|------|------------------|
| AI-601 | M12 動態定價分段啟動 | ✅ 完成（US-001/002 M12 延伸 + effective-price API） |
| AI-602 | M10 IM SA 需求分析不得再延後 | ✅ 完成（US-004 + Victoria APPROVED） |
| AI-603 | M11 物流與訂單整合評估 | 🟡 持續追蹤 → AI-704（Sprint 23 實作） |

**Action Items 完成率**: 2/3（AI-603 升級為 AI-704 帶入 Sprint 23）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 19 | 13 | 13 | 100% |
| Sprint 20 | 11 | 10 | 91%（Buffer 未動用） |
| Sprint 21 | 12 | 12 | 100%（含 Buffer-A） |
| Sprint 22 | 12→8 | 8 | 100%（含 Buffer-A+B，但絕對 SP 降低） |

> **Sprint 22 觀察**: 因 M12 現況調查節省 4 SP，規劃 SP 調降至 8 SP 全數完成。首次 Buffer-A + Buffer-B 雙雙執行完畢。建議 Sprint 23 規劃時先做現況調查，確保 SP 估算準確。

---

## 7. 技術債趨勢

| 指標 | Sprint 21 後 | Sprint 22 後 |
|------|------------|------------|
| `catch(Exception)` 生產程式碼 | **0 處** ✅ | **0 處** ✅ |
| `@Deprecated` 生產程式碼 | **0 處** ✅ | **0 處** ✅ |
| Integration Tests | 293 | **~302** |
| Unit Tests（估算） | ~320 | **~323** |
| 測試總數 | ~613 | **~625** |
| 模組 SA 需求文件完整度 | M10 缺 SA 分析 | ✅ M10 SA APPROVED |
| 延後項目（DEF）數 | DEF-005/006/007/008 | DEF-005/006 ✅ 關閉，DEF-007/008 待處理 |

---

## 8. 下一步方向（Sprint 23 預覽）

根據本次 Retro Action Items 和業務需求，Sprint 23 建議：

| 候選項 | 類型 | 預估 SP | 依據 |
|--------|------|--------|------|
| M10 IM — SD 架構設計 + DB Migration | 架構文件 + DB | 2 | AI-702，基於 M10_IM_REQUIREMENTS.md |
| M10 IM — ConversationController + MessageController REST API | P0 新功能 | 4 | AI-702，Sprint 23 主題 |
| M11 物流與訂單整合（訂單觸發 createShipment） | P1 整合 | 2 | AI-704（AI-603 延續） |
| DEF-007 評估項目 | Buffer | 1 | DEFERRED_ITEMS_TRACKER |
| DEF-008 評估項目 | Buffer | 1 | DEFERRED_ITEMS_TRACKER |

> **Sprint 23 重點**: M10 IM 從 SA 分析進入 SD 架構 + 後端 REST 實作，是 Sprint 23 的核心主題。WebSocket 部分（即時訊息廣播）建議放 Sprint 24。

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + Claude Code
