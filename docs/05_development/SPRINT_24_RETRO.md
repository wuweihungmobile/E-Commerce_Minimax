# Sprint 24 Retrospective 報告 / Sprint 24 Retrospective Report

> **Sprint 編號**: Sprint 24
> **期間**: 2026-09-15 ~ 2026-09-26 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-29
> **基於**: [SPRINT_24_PLAN.md](../04_planning/SPRINT_24_PLAN.md), [SPRINT_24_TASKS.md](./SPRINT_24_TASKS.md), [SPRINT_24_REVIEW.md](./SPRINT_24_REVIEW.md), [SPRINT_23_RETRO.md](./SPRINT_23_RETRO.md)

---

## 1. Sprint 24 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | TestSecurityContextHelper 標準化 + M10 WebSocket STOMP 後端 + M13 Dashboard Redis TTL |
| **規劃 SP（承諾）** | 7 SP（P0+P1，US-001~003） |
| **完成 SP** | 7 SP（US-001~003 全部完成） |
| **完成 US** | 3/3 承諾 US（100%）；Buffer US-004/005 未啟動 |
| **測試結果** | `@Test` 靜態計數 652，0 退步 |
| **新增測試** | US-002 STOMP 廣播 + US-003 RedisCacheConfig（+4） |
| **團隊** | 2 人 Dev Team |
| **計畫外工作** | E2E/CI 穩定性救火 9 個 commit（Flyway V48~V55 + Playwright + Docker 清理） |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **整合測試 SecurityContext 標準化** | `TestSecurityContextHelper` 建立，固化 Sprint 23 的 `UserPrincipal` 修正模式為可複用工具 | 後續整合測試不再誤用 `@WithMockUser` |
| ✅ **M10 IM 進入即時通訊** | WebSocket STOMP 後端完成，訊息可即時廣播至訂閱頻道 | M10 從 REST 輪詢進化為即時推送（Phase 2） |
| ✅ **M13 Dashboard Redis TTL** | dashboardStats 切 Redis + 5 分鐘 TTL + 3 個 @CacheEvict 觸發點 | 商家儀表板快取從「應用生命週期」進化為「可控過期 + 即時失效」 |
| 🔴 **E2E schema 漂移大規模修復** | Flyway V48~V55（8 個 migration）補齊缺漏建表 + 統一 ARRAY→jsonb，修復 GitHub E2E backend 啟動 | 暴露本地 CI 與 GitHub E2E 的 schema 驗證落差（核心檢討） |
| ✅ **Docker / E2E 工具鏈瘦身** | 清理 LLM 殘留工具鏈、playwright-report 停版控（合計 -2500+ 行） | 倉庫與 CI 流程精簡 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **承諾範圍 100% 完成** | US-001~003（P0+P1）全部達成所有 AC | US-001~003 全部 ✅ |
| **AI-801/803 落地** | Sprint 23 Retro 的 2 項 P1/P2 Action Item 完成 | TestSecurityContextHelper + Redis TTL |
| **技術債維持零** | catch(Exception)=0、@Deprecated=0 維持 | 靜態掃描確認 |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **TestSecurityContextHelper 抽象恰當** | 將一次性修正（Sprint 23 `c5a76bb`）提煉為共用工具，含完整版/快速版/clear 三方法 | 符合 Rule 2，且後續整合測試直接複用 |
| **RedisCacheManagerBuilderCustomizer 取捨正確** | 不用顯式 CacheManager bean，避免 integration-test（simple cache）的 Redis NPE | 一個設計同時滿足 dev（Redis）與 test（simple） |
| **STOMP 例外精準捕捉** | `catch (JwtException \| IllegalArgumentException e)` 而非 catch(Exception) | 維持生產程式碼 catch(Exception)=0 紀律 |
| **ARRAY→jsonb 全庫統一** | V55 消除全庫最後一個 ARRAY 映射，型別策略一致化 | 杜絕同類 schema 漂移再次發生 |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **逐 US 編譯-測試循環** | 每個 US 完成即 compile + test，零累積債 | 每個 commit 通過 pre-commit hook |
| **救火一次定位根因** | 將 8 個 schema 漂移點分批修復（型別 / 缺表 / ARRAY 統一），逐一 push 驗證 | V48~V55 commit 序列 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **🔴 本地 CI 無法偵測 schema 驗證錯誤** | 本地 `act` 使用 `ddl-auto=update`（自動補欄位/建表），GitHub E2E 使用 schema 驗證。entity 與 migration 的型別漂移、缺漏建表在本地完全不報錯，只在 GitHub E2E backend 啟動時爆發 | 🔴 高 — 直接導致 9 個非計畫救火 commit，吃掉 Buffer 容量 |
| **承諾完成後 Buffer 全數未啟動** | E2E 救火佔用 Sprint 後段全部容量 | 🟡 中 — US-004/005 延續 Sprint 25，但承諾範圍未受影響 |
| **Sprint 排期與真實開發週期持續脫節（第七次）** | 文件排期（09-15~09-26）與實際開發日（06-29）差距約 78 天 | 🟡 低 — 慢性問題，不影響交付品質 |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **多模組 migration 與 entity 長期漂移** | 歷史上多個模組（knowledge / media / rooms / cms / inventory / logistics / notifications）entity 欄位與 migration 不同步，依賴 `ddl-auto=update` 遮蔽 | 🔴 高 — 本 Sprint 一次清償 8 點，但需機制防止復發 |
| **Conversation 無 tenant_id 欄位（延續）** | ChatService 最初實作未考慮多租戶隔離 | 🟠 中 — AI-802 延續，WebSocket 廣播後租戶隔離議題更需評估 |
| **M11 物流未實作 `cancelShipment()`（延續）** | 取消/退貨流程業務規則未定義 | 🟡 低 — Buffer-B 延後 Sprint 25 |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-901 | **本地補 schema 驗證關卡** | 在本地 `act` / `make validate` 增加一個以 `ddl-auto=validate`（或 Flyway validate + 啟動驗證）的 backend 啟動檢查，讓 schema 漂移在 push 前就被攔下，不再到 GitHub E2E 才爆發 | Dev David | **P0** | Sprint 25 |
| AI-902 | **entity ↔ migration 一致性盤點** | 一次性盤點所有 `@Entity` 欄位型別與 migration DDL 是否吻合，建立對照清單，杜絕 ARRAY/jsonb、tags 型別等漂移復發 | Dev David + SD Marcus | P1 | Sprint 25 |
| AI-802 | Conversation tenant_id 評估（延續） | 評估補 `tenant_id` 欄位及 Migration，WebSocket 廣播後租戶隔離更關鍵 | SD Marcus + Dev David | P1 | Sprint 25 |
| AI-804 | SSH timeout pre-push hook 優化（延續，Buffer-A 未啟動） | 評估 ControlMaster SSH 複用或 HTTPS push 避免 act CI 期間 SSH 超時 | Dev David | P2 | Sprint 25 |
| AI-903 | M11 物流取消流程業務規則確認（Buffer-B 前置） | 與 PM 確認 SHIPPING→CANCELLED 業務規則後再排入 | PM Victoria + Dev David | P2 | Sprint 25 |

---

## 5. Sprint 23 → Sprint 24 Action Items 追蹤結果

| Action Item | 內容 | Sprint 24 達成狀態 |
|-------------|------|------------------|
| AI-801 | 整合測試 SecurityContext 標準化 | ✅ 完成（US-001 TestSecurityContextHelper） |
| AI-802 | Conversation tenant_id 評估 | ⏸️ 暫緩 → 延續 Sprint 25（原訂 P2） |
| AI-803 | M13 Dashboard Redis TTL 設計 | ✅ 完成（US-003） |
| AI-804 | SSH timeout pre-push hook 優化 | ⏸️ 未啟動 → 延續 Sprint 25（Buffer-A 未動用） |

**Action Items 完成率**: 2/4（AI-801/803 完成；AI-802/804 延續）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 20 | 11 | 10 | 91%（Buffer 未動用） |
| Sprint 21 | 12 | 12 | 100%（含 Buffer-A） |
| Sprint 22 | 12→8 | 8 | 100%（含 Buffer-A+B，SP 調降） |
| Sprint 23 | 12 | 12 | 100%（含 Buffer-A+B） |
| Sprint 24 | 7（承諾） | 7 | 100%（承諾範圍，Buffer 未動用） |

> **Sprint 24 觀察**: 承諾範圍 100% 達成，但 Buffer 容量（3 SP）被計畫外 E2E schema 救火完全佔用。
> 這是連續 3 個 Sprint 以來首次 Buffer 0% 啟動，原因為技術債（schema 漂移）一次性清償，屬健康的「還債」而非規劃失準。

---

## 7. 技術債趨勢

| 指標 | Sprint 23 後 | Sprint 24 後 |
|------|------------|------------|
| `catch(Exception)` 生產程式碼 | 0 處 ✅ | **0 處** ✅ |
| `@Deprecated` 生產程式碼 | 0 處 ✅ | **0 處** ✅ |
| `@Test` 方法數（靜態） | ~643 | **652** |
| Flyway 最新版本 | V47 | **V55**（+8，全為 schema 漂移修復） |
| 全庫 ARRAY 欄位映射 | 仍有殘留 | ✅ 0（V55 消除最後一個） |
| 整合測試 SecurityContext 模式 | 一次性修正（c5a76bb） | ✅ 標準化工具（TestSecurityContextHelper） |
| M10 IM 即時通訊 | 僅 REST | ✅ WebSocket STOMP 後端 |
| 技術債（新增/延續） | Conversation 無 tenant_id | Conversation tenant_id（AI-802）；本地缺 schema 驗證關卡（AI-901，新增） |

---

## 8. 下一步方向（Sprint 25 預覽）

根據本次 Retro Action Items，Sprint 25 建議：

| 候選項 | 類型 | 預估 SP | 依據 |
|--------|------|--------|------|
| 本地補 schema 驗證關卡（ddl-auto=validate 啟動檢查） | DevOps / 品質 | 2 | **AI-901（P0）**，防止 schema 漂移再到 GitHub E2E 才爆發 |
| entity ↔ migration 一致性盤點 | 技術改善 | 2 | AI-902（P1），杜絕漂移復發 |
| Conversation tenant_id 補齊 | 技術債 | 2 | AI-802（P1），WebSocket 廣播後租戶隔離更關鍵 |
| M10 WebSocket 前端整合（@stomp/stompjs） | P1 新功能 | 3 | M10 IM Phase 2 前端 |
| SSH pre-push 優化 / M11 取消流程 | DevOps / P2 | 1~2 | AI-804 / AI-903（Buffer） |

> **Sprint 25 重點建議**: AI-901（本地 schema 驗證關卡）列為 **P0** — 這是本 Sprint 最大教訓，必須先補上守門機制，避免下個 Sprint 重蹈 9 個救火 commit 的覆轍。

---

**文件版本**: v1.0
**建立日期**: 2026-06-29
**建立者**: Dev David + SD Marcus + Claude Code
