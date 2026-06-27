# Sprint 23 Retrospective 報告 / Sprint 23 Retrospective Report

> **Sprint 編號**: Sprint 23
> **期間**: 2026-09-01 ~ 2026-09-12 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-27
> **基於**: [SPRINT_23_PLAN.md](../04_planning/SPRINT_23_PLAN.md), [SPRINT_23_TASKS.md](./SPRINT_23_TASKS.md), [SPRINT_23_REVIEW.md](./SPRINT_23_REVIEW.md), [SPRINT_22_RETRO.md](./SPRINT_22_RETRO.md)

---

## 1. Sprint 23 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | M10 IM Migration 補齊 + 整合測試、DEF-007 物流履約整合、DEF-008 運費接入、M13 @Cacheable |
| **規劃 SP** | 12 SP |
| **完成 SP** | 12 SP（US-001~006 全部完成，含 Buffer-A + Buffer-B）|
| **完成 US** | 6/6（100%，含 Buffer-A + Buffer-B）|
| **測試結果** | ~643 tests, 0 Failures |
| **新增測試** | +18（Integration +15 + Unit +3）|
| **團隊** | 2 人 Dev Team |
| **提前完成** | Sprint 正式開始（09-01）前 66 天（2026-06-27）完成 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **M10 IM 技術債清零** | DEF-005/DEF-006 已於 Sprint 22 關閉；Sprint 23 補齊 V45/V46 Migration，8 個整合測試全通過 | M10 IM 模組從「有程式碼無 DB」進入可用狀態 |
| ✅ **DEF-007 M11 物流履約整合** | createLogistics 前置驗證 + 訂單狀態同步 SHIPPING/DELIVERED，4 個整合測試通過 | 物流與訂單生命週期正式連通 |
| ✅ **DEF-008 運費接入結帳流程** | V47 Migration + shippingFee 欄位 + 免運門檻邏輯，3 個整合測試通過 | 訂單 totalAmount 納入運費，結帳邏輯完整 |
| ✅ **M13 @Cacheable 優化** | SellerDashboardService 加快取，Simple Cache + 3 個 Unit Tests | 商家儀表板高頻查詢效能提升 |
| ✅ **act CI 修正** | `@WithMockUser` → `UserPrincipal` SecurityContext 修正；integration-test cache.type: simple | act CI 環境整合測試全數通過 |
| ✅ **Sprint 20~23 全數 push** | 約 25 個 commits 成功推送 GitHub（原被 SSH timeout 阻擋，改用驗證記錄機制） | 所有 Sprint 工作上傳，CI pipeline 可驗證 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **100% US 完成** | 6 個 US 全部達成所有 AC，含 Buffer-A + Buffer-B | US-001~006 全部 ✅ |
| **100% Buffer 啟動（第二次）** | Buffer-A（DEF-008 運費）+ Buffer-B（@Cacheable）雙雙完成 | 連續第二次 Buffer 100% 利用率 |
| **100% Action Items 完成** | Sprint 22 的 AI-701/702/703/704 全部完成 | 4/4 ✅ |
| **100% 測試通過** | ~643 tests, 0 Failures，act CI 通過 | `make validate-all` ✅ |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **AI-701 現況調查（關鍵節省）** | Sprint 開始前調查 M10 現況，發現 ChatService/Controller 早已存在，僅缺 Migration | 6 US 開發節省超過 50% 時間 |
| **UserPrincipal 整合測試修正** | `TenantContextFilter` 要求 `UserPrincipal` 而非 String principal；改用 `SecurityContextHolder.getContext().setAuthentication()` | 正確的整合測試 SecurityContext 設置模式，後續可複用 |
| **act CI cache 隔離** | `application-integration-test.yml` 加入 `spring.cache.type: simple`，隔離 Redis 認證問題 | act CI Redis 無密碼問題永久修復 |
| **BigDecimal JSON 斷言** | `.value(int)` 取代 `Matchers.closeTo(double, double)`，解決 Integer/Double 型別不符 | 正確的 JSON BigDecimal 斷言模式 |
| **免運門檻邏輯簡潔** | `ShippingTemplateService.calculateFeeForTenant()` 3 行邏輯完整處理：有模板/達門檻/無模板三路 | 符合 Rule 2（簡潔優先） |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **逐 US 編譯-測試循環** | 每個 US 完成後立即 compile + test，零累積債 | 每個 commit 均通過 pre-commit hook |
| **CI 驗證記錄機制有效** | SSH timeout 期間 act CI 仍順利完成，驗證記錄存在 → 10 分鐘內重新 push 成功 | `c5a76bb` 2026-06-27 19:48:47 push |
| **測試修正一次到位** | 發現 3 個 act CI 失敗根因（SecurityContext + Cache + BigDecimal），單 commit 修正全部 | `c5a76bb` 修正 3 個問題 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **Sprint 開始前大幅提前完成（第六次）** | 實際工作時間（06-27）與 Sprint 排期（09-01~09-12）持續脫節 66 天 | 🟡 低 — Sprint 文件節奏與真實開發週期不同步（慢性問題） |
| **act CI 失敗需事後修正** | `@WithMockUser` 在本地整合測試通過，但 act CI 中 TenantContextFilter 行為不同 | 🟠 中 — 整合測試設計時未考慮 act CI 的 SecurityContext 差異，多一個 fix commit |
| **SSH timeout 造成 push 失敗** | act CI 執行約 60 分鐘，SSH 連線在 CI 期間超時（exit 141） | 🟡 低 — 驗證記錄機制彌補，但需在 10 分鐘內重新 push |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **Conversation 無 tenant_id 欄位** | ChatService 最初實作未考慮多租戶隔離，tenant 依靠 user 間接過濾 | 🟠 中 — 技術債，需評估是否在 Sprint 24+ 補齊 |
| **M13 @Cacheable 使用 Simple Cache，無 TTL** | Sprint 23 為首期實作，Redis TTL 設計留到後期 | 🟡 低 — 應用重啟即清 Cache，高頻更新場景下 Cache 效益有限 |
| **M11 物流未實作 `cancelShipment()`** | Sprint 23 僅實作 createShipment + 狀態同步，取消流程未定義 | 🟡 低 — 完整物流生命週期尚缺取消/退貨流程 |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-801 | 整合測試 SecurityContext 標準化 | 建立 `TestSecurityContextHelper` 工具類，統一 `UserPrincipal` SecurityContext 設置，避免 `@WithMockUser` 誤用 | Dev David | P1 | Sprint 24 |
| AI-802 | Conversation tenant_id 評估 | 評估是否補 `tenant_id` 欄位（及相應 Migration），確保多租戶隔離不依賴 userId 過濾 | SD Marcus + Dev David | P2 | Sprint 25 |
| AI-803 | M13 Dashboard Redis TTL 設計 | 將 `dashboardStats` Cache 切換為 Redis，設定 TTL 5 分鐘 + @CacheEvict 觸發點（新訂單/上下架） | Dev David | P2 | Sprint 24 |
| AI-804 | SSH timeout pre-push hook 優化 | 評估 `ControlMaster` SSH 複用或 HTTPS push 以避免 act CI 期間 SSH 超時 | Dev David | P2 | Sprint 24 |

---

## 5. Sprint 22 → Sprint 23 Action Items 追蹤結果

| Action Item | 內容 | Sprint 23 達成狀態 |
|-------------|------|------------------|
| AI-701 | Sprint 規劃前執行現況調查 | ✅ 完成（Sprint 23 啟動即進行 M10 現況調查） |
| AI-702 | M10 IM SD 架構設計 + REST API 實作 | ✅ 完成（現況調查發現已實作，改為補 Migration） |
| AI-703 | M13 儀表板 @Cacheable 優化 | ✅ 完成（Buffer-B，Simple Cache） |
| AI-704 | M11 物流與訂單整合 | ✅ 完成（createLogistics 整合 + 狀態同步） |

**Action Items 完成率**: 4/4（100%）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 19 | 13 | 13 | 100% |
| Sprint 20 | 11 | 10 | 91%（Buffer 未動用） |
| Sprint 21 | 12 | 12 | 100%（含 Buffer-A） |
| Sprint 22 | 12→8 | 8 | 100%（含 Buffer-A+B，SP 調降） |
| Sprint 23 | 12 | 12 | 100%（含 Buffer-A+B） |

> **Sprint 23 觀察**: 首次在規劃 SP 不調降的情況下仍完成 100%，且 Buffer 全部啟動。
> AI-701 現況調查流程已成效：Sprint 開始即調查，節省大量開發時間。

---

## 7. 技術債趨勢

| 指標 | Sprint 22 後 | Sprint 23 後 |
|------|------------|------------|
| `catch(Exception)` 生產程式碼 | **0 處** ✅ | **0 處** ✅ |
| `@Deprecated` 生產程式碼 | **0 處** ✅ | **0 處** ✅ |
| Integration Tests | ~302 | **~317** |
| Unit Tests（估算） | ~323 | **~326** |
| 測試總數 | ~625 | **~643** |
| Flyway 最新版本 | V44 | **V47** |
| 模組 DB Migration 完整度 | M10 缺 conversations/messages 表 | ✅ M10 V45/V46 補齊 |
| 延後項目（DEF）數 | DEF-007/008 待處理 | ✅ 全部關閉 |
| 技術債（新增）| — | Conversation 無 tenant_id（AI-802 追蹤） |

---

## 8. 下一步方向（Sprint 24 預覽）

根據本次 Retro Action Items 和業務需求，Sprint 24 建議：

| 候選項 | 類型 | 預估 SP | 依據 |
|--------|------|--------|------|
| TestSecurityContextHelper 標準化 | 技術改善 | 1 | AI-801，整合測試品質提升 |
| M13 Dashboard Redis TTL 設計 | 效能優化 | 1 | AI-803，評估 Redis @Cacheable TTL |
| M10 WebSocket/STOMP 即時訊息（可選） | P1 新功能 | 4 | M10 IM 第二階段 |
| M11 物流取消/退貨流程 | P2 新功能 | 2 | M11 完整生命週期 |
| SSH timeout pre-push 優化 | DevOps | 1 | AI-804 |

> **Sprint 24 重點建議**: TestSecurityContextHelper + Redis Cache 技術改善為 P1，
> M10 WebSocket 為重要業務功能，可視容量決定是否啟動。

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + SD Marcus + Claude Code
