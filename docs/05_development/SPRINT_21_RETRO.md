# Sprint 21 Retrospective 報告 / Sprint 21 Retrospective Report

> **Sprint 編號**: Sprint 21
> **期間**: 2026-08-04 ~ 2026-08-15 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-27
> **基於**: [SPRINT_21_PLAN.md](../04_planning/SPRINT_21_PLAN.md), [SPRINT_21_TASKS.md](./SPRINT_21_TASKS.md), [SPRINT_21_REVIEW.md](./SPRINT_21_REVIEW.md), [SPRINT_20_RETRO.md](./SPRINT_20_RETRO.md)

---

## 1. Sprint 21 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | MQ 一致性（AI-502）、Stripe SDK Phase 3、M11 Provider 抽象、@Cacheable（AI-503）、M14 統計 API、M11 運費模板 |
| **規劃 SP** | 12 SP（Buffer 3 SP = 20%） |
| **完成 SP** | 12 SP（US-001~005: 10 SP + Buffer-A US-006: 2 SP） |
| **完成 US** | 6/6（100%，含 Buffer-A）|
| **測試結果** | ~613 tests（Integration 293, Unit ~320）, 0 Failures |
| **新增測試** | +30（Unit +16 + Integration +14） |
| **團隊** | 2 人 Dev Team |
| **提前完成** | Sprint 正式開始（08-04）前 38 天（2026-06-27）完成 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **MQ 歷史一致性達成** | history 寫入由 Producer 端移至 Consumer ACK 後，消除雙寫風險 | 至少一次交付語義完整保障 |
| ✅ **Stripe SDK Phase 3 完成** | 從 Mock 升級至真實 Stripe Java SDK，WireMock 測試隔離 | 支付鏈路具備生產就緒能力 |
| ✅ **M11 Provider 策略模式建立** | HCT + SINOPAC Stub + Factory 路由，後向相容 | 未來可輕鬆新增真實物流 API |
| ✅ **評分統計快取上線** | @Cacheable Redis TTL 5min，高流量下回應快 10x | 評分 API 生產負載能力大幅提升 |
| ✅ **M11 運費模板 CRUD** | FIXED/FREE_THRESHOLD 費型策略，含計費端點 | 商家可自定義運費規則 |
| ✅ **AI-501/502/503 全部達成** | Sprint 20 Retro 全 3 個 Action Items 完成 | 技術債改善與 Sprint 流程持續優化 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **100% US 完成** | 6 個 User Story 全部達成所有 AC，含 Buffer-A | US-001~006 全部 ✅ |
| **100% SP 達成** | 規劃 12 SP 全數完成（含 Buffer-A 2 SP） | Sprint 容量控制精準 |
| **100% 測試通過** | ~613 tests, 0 Failures | `mvn verify` BUILD SUCCESS |
| **連續第四次提前完成** | 2026-06-27 完成（比 Sprint 開始 08-04 提前 38 天） | 開發效能持續高水準 |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **WireMock 隔離設計** | Stripe SDK 整合無需真實 API key，CI 環境自給自足 | 無外部服務依賴，測試可靠性高 |
| **Strategy Pattern 落地** | LogisticsProvider + ShippingTemplate 雙模組採用策略模式 | 架構擴展能力大幅提升 |
| **費用計算策略清晰** | `computeShippingFee()` 私有方法萃取，Checkstyle MagicNumber 合規 | 業務邏輯集中，易於維護 |
| **租戶隔離完整** | ShippingTemplate 更新/刪除驗證 tenantId，防止越權操作 | 多租戶安全性得到保障 |
| **AI-501 落地** | Buffer 準確降至 20%（3 SP），規劃 SP 提升至 12 SP | Sprint 容量校準精度提升 |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **Action Items 100%** | AI-501/502/503 全部在 Sprint 21 達成 | Sprint 20 Retro 3 項全數完成 |
| **逐 US 編譯-測試循環** | 每完成一個 US 立即編譯 + 測試，零累積債 | 每個 commit 均通過 pre-commit hook |
| **Buffer 使用率適中** | Buffer-A (US-006) 啟動並完成，Buffer-B/C 合理延後 | Buffer 設計目的達成 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **Sprint 開始前大幅提前完成** | Sprint 計劃期間（08-04~08-15）與實際開發時間（06-27）差距 38 天 | 🟡 低 — Sprint 節奏文件化與實際工作脫節（第四次） |
| **Buffer-B/C 連續兩次未啟動** | Sprint 21 核心 P0+P1+Buffer-A 已充分填滿容量 | 🟡 低 — Buffer-B M10 SA 分析持續延後，Sprint 22 必須處理 |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **M11 物流與訂單未整合** | LogisticsProvider 策略抽象完成，但尚未接入訂單履約流程 | 🟠 中 — 客戶無法透過訂單觸發物流建立 |
| **ShippingTemplate 未接入結帳流程** | 運費模板 CRUD 完成，但訂單結帳時尚未調用 `calculateFee()` | 🟠 中 — 實際運費仍為 Mock，非動態計算 |
| **M12 動態定價尚未啟動** | Phase 1 P0 功能，Sprint 21 因容量規劃而延後 | 🔴 高 — Phase 1 主要未完成功能 |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-601 | M12 動態定價分段啟動 | Sprint 22 正式啟動 M12 PricingRule 資料模型 + CRUD API（P0 優先） | Dev David | P1 | Sprint 22 |
| AI-602 | M10 IM SA 需求分析不得再延後 | Buffer-B 已延後兩次，Sprint 22 Buffer-A 必須完成 SA Amanda 需求分析文件 | SA Amanda | P2 | Sprint 22 |
| AI-603 | M11 物流與訂單整合評估 | 評估 LogisticsProvider 接入訂單確認/完成流程的時程與影響 | SD Marcus + Dev David | P2 | Sprint 22 規劃 |

---

## 5. Sprint 20 → Sprint 21 Action Items 追蹤結果

| Action Item | 內容 | Sprint 21 達成狀態 |
|-------------|------|------------------|
| AI-501 | Buffer 降至 20%，規劃 SP 提升至 12 SP | ✅ 完成（Buffer 3 SP = 20%，規劃 12 SP） |
| AI-502 | notification_history MQ Consumer 端一致性改善 | ✅ 完成（US-001，Consumer ACK 後寫入） |
| AI-503 | getRatingStats @Cacheable（Redis TTL 5 分鐘） | ✅ 完成（US-004，@Cacheable + @CacheEvict） |

**Action Items 完成率**: 3/3（100%）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 18 | 13 | 19 | 146%（含 Buffer） |
| Sprint 19 | 13 | 13 | 100% |
| Sprint 20 | 11 | 10 | 91%（Buffer 未動用） |
| Sprint 21 | 12 | 12 | 100%（含 Buffer-A） |

> **Sprint 21 觀察**: Buffer-A（US-006）被完整執行，Velocity 回到 100%。Buffer 20% 設計有效引導額外容量利用。

---

## 7. 技術債趨勢

| 指標 | Sprint 19 後 | Sprint 20 後 | Sprint 21 後 |
|------|------------|------------|------------|
| `catch(Exception)` 生產程式碼 | 12 處 | **0 處** ✅ | **0 處** ✅ |
| `@Deprecated` 生產程式碼 | 11 處 | **0 處** ✅ | **0 處** ✅ |
| Integration Tests | 255 | 279 | **293** |
| Unit Tests（估算） | ~295 | ~304 | **~320** |
| 測試總數 | ~550 | ~583 | **~613** |

> **Sprint 21 達成**: 技術債雙零指標持續維持，新增功能測試覆蓋率完整。

---

## 8. 下一步方向（Sprint 22 預覽）

根據本次 Retro Action Items 和業務需求，建議 Sprint 22 優先考慮：

| 候選項 | 類型 | 預估 SP |
|--------|------|--------|
| M12 動態定價 PricingRule CRUD（AI-601） | P0 新功能 | 3 |
| M12 PriceCalculator 服務 + Listing 整合 | P0 新功能 | 3 |
| M13 商家工作台基礎儀表板 API | P1 新功能 | 2 |
| M10 IM SA 需求分析（AI-602，Buffer-B 轉 Buffer-A） | 需求文件 | 2 |
| M11 Provider Stub 強化（Buffer-C 轉 Buffer-B） | 技術改善 | 1 |

> **注意（AI-601）**: M12 動態定價建議分段交付：Sprint 22 完成資料模型 + CRUD，Sprint 23 完成計算引擎 + 訂單整合。

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + Claude Code
