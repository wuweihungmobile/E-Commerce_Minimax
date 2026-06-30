# Sprint 25 Retrospective 報告 / Sprint 25 Retrospective Report

> **Sprint 編號**: Sprint 25
> **期間**: 2026-09-27 ~ 2026-10-10 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-30
> **基於**: [SPRINT_25_PLAN.md](../04_planning/SPRINT_25_PLAN.md), [SPRINT_25_TASKS.md](./SPRINT_25_TASKS.md), [SPRINT_25_REVIEW.md](./SPRINT_25_REVIEW.md), [SPRINT_24_RETRO.md](./SPRINT_24_RETRO.md)

---

## 1. Sprint 25 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | schema 漂移防線（守門）+ 多租戶隔離技術債清償 + M10 即時通訊前端化 |
| **規劃 SP（承諾）** | 9 SP（P0+P1，US-001~004） |
| **完成 SP** | 11 SP（承諾 9 + Buffer 2，US-001~006 全部完成） |
| **完成 US** | 4/4 承諾 + 2/2 Buffer（100%+） |
| **測試結果** | 後端 644 通過 / 0 失敗；`@Test` 靜態 659（+7）；前端 live E2E 1 passed |
| **團隊** | 2 人 Dev Team + AISDLC Agents（PM/BA/SD/Dev/QA + Explore） |
| **計畫外工作** | live E2E 揪出並修正 2 個 Sprint 24 STOMP 後端缺口 |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **schema 守門關卡上線（AI-901, P0）** | `make validate-schema`：ddl-auto=validate + Flyway 對乾淨 DB 啟動，push 前攔漂移 | 杜絕 Sprint 24「9 個 schema 救火 commit」覆轍 |
| ✅ **守門關卡首次實戰** | US-003 改 entity + V56 migration 後 `make validate-schema` → exit 0 | 防線立即發揮作用 |
| ✅ **多租戶隔離技術債清償** | Conversation 補 tenant_id（AI-802），全庫少數無 tenant_id 的 entity 補齊 | WebSocket 廣播後租戶隔離議題收斂 |
| ✅ **M10 IM 前端閉環** | greenfield 完整 chat UI + STOMP 即時收訊，live E2E 通過 | M10 從後端 STOMP 進化為端到端即時通訊 |
| 🔴 **live E2E 揪出 Sprint 24 隱性缺口** | SockJS 握手被 Security 擋（401）+ 廣播 conversationId=null | 證明「後端-only 功能」需真實 client E2E 才算完成 |
| ✅ **Buffer 全數啟動（AI-804/903）** | SSH keepalive + M11 取消規則確認，與 Sprint 24 Buffer 0% 對比 | Action Items 5/5 全清 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **承諾 + Buffer 全完成** | US-001~006 全部達成所有 AC | 11 SP / 100%+ |
| **Sprint 24 Action Items 5/5 落地** | AI-901/902/802/804/903 全數完成 | 連續性執行 |
| **技術債維持零** | catch(Exception)=0、@Deprecated=0 維持 | 靜態掃描確認 |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **schema 守門關卡設計正確** | 專屬 port 18080 + port 佔用檢查，避免誤打既有 backend 的假通過 | 負向測試真實攔截漂移 |
| **participant-scoping 取捨正確** | 識破 AC-003-3 字面「TenantContext 過濾」會破壞跨租戶 buyer↔seller 對話，改以參與者邊界 | 避免寫出破壞功能的隔離邏輯 |
| **live E2E 揪出真實整合 bug** | SockJS 握手 401 + conversationId null，後端單元測試完全抓不到 | 高價值：真實 client 才能驗證 WS 端到端 |
| **Next.js 16 破壞性 lint 即時因應** | react-hooks/refs、set-state-in-effect 連續踩到後依規重構 | 遵循 frontend AGENTS.md「先讀 docs」 |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **逐單元編譯-測試循環** | 每個 US 完成即 compile + test + （改 schema 時）守門關卡 | 全程通過 pre-commit hook，零 --no-verify |
| **AISDLC Agent/Skill 協作** | Explore（M11 調查）+ ba-analyst（規則確認）+ pm-planning（收尾）+ release-management | 角色分工清晰 |
| **誠實揭露與更正** | 對「pre-commit 只用 H2」的錯誤認知即時更正（實為部分 core 測試需真實 postgres） | Rule 12 大聲失敗 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **🔴 後端-only 功能無真實 client 驗證即標記完成** | Sprint 24 STOMP 後端僅以 SimpMessagingTemplate 單元測試，繞過 SockJS 握手與序列化，導致 2 個缺口（401 握手、conversationId null）潛伏至 Sprint 25 才被 live E2E 抓到 | 🔴 高 — WS/即時類功能應有真實 client E2E 才算 DoD |
| **本地整合測試 DB 容器 churn 過多** | 整合測試需真實 postgres+redis，但無單一標準啟動方式，session 內反覆手動起不同名稱容器，造成「重複部署」觀感 | 🟡 中 — 應有標準 make target 統一管理測試 DB 生命週期 |
| **GitHub Actions 帳單封鎖，無法 push 驗證雲端 CI** | 帳號層級帳單問題（2026-06-29 復發） | 🟡 中 — 本地 act + 644 測試 + live E2E 已全綠，待帳單解除即可 push |
| **Sprint 排期與真實開發週期持續脫節（第八次）** | 文件排期（09-27~10-10）與實際開發日（06-30）差距約 100+ 天 | 🟢 低 — 慢性問題，不影響交付品質 |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **ChatService.toMessageResponse 廣播 conversationId=null** | toMessageResponse 未填 conversationId | 🟡 低（前端已補正） | DEF-012（Sprint 26 後端修） |
| **OrderStateMachine SHIPPING→CANCELLED 不一致** | 轉換表與 canCancel() 矛盾 | 🟢 低（cancelOrder 以 canCancel 守門） | DEF-010 |
| **cancelLogistics 錯誤碼誤用（E_7000/7002）** | 沿用 Supplier/PO 錯誤碼 | 🟢 低 | DEF-011 |
| **Logistics jsonb 映射慣例不一致** | 歷史 String+columnDefinition vs 全庫 Map+@JdbcTypeCode | 🟢 低 | DEF-009 |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1001 | **WS/即時功能納入真實 client E2E 為 DoD** | 後端 WebSocket/STOMP 等「需握手+序列化」的功能，DoD 應包含一個真實 client（Playwright/SockJS）端到端測試，不可僅以 SimpMessagingTemplate 單元測試結案 | QA Quincy + Dev David | **P1** | Sprint 26 |
| AI-1002 | **本地整合測試 DB 標準 make target** | 新增 `make test-db-up` / `test-db-down`（postgres+redis），統一整合測試與 pre-commit 所需 DB 生命週期，避免容器 churn；評估納入 pre-commit 前置 | Dev David | P2 | Sprint 26 |
| AI-1003 | **後端修 toMessageResponse conversationId** | DEF-012，廣播 payload 補 conversationId 以利 mobile 等 client | Dev David | P2 | Sprint 26 |
| AI-1004 | M11 取消技術債清理 | DEF-010（StateMachine 一致性）+ DEF-011（錯誤碼）+ 補物流取消測試 | Dev David | P2 | Sprint 26 |
| AI-1005 | GitHub Actions 帳單解除後補 push + 雲端 E2E 驗證 | 帳單恢復後將 Sprint 25 commits 推上並確認 GitHub E2E 綠燈 | PM Victoria | P1 | Sprint 26 |

---

## 5. Sprint 24 → Sprint 25 Action Items 追蹤結果

| Action Item | 內容 | Sprint 25 達成狀態 |
|-------------|------|------------------|
| AI-901 | 本地補 schema 驗證關卡 | ✅ 完成（US-001, P0） |
| AI-902 | entity ↔ migration 一致性盤點 | ✅ 完成（US-002） |
| AI-802 | Conversation tenant_id 評估 | ✅ 完成（US-003） |
| AI-804 | SSH timeout pre-push hook 優化 | ✅ 完成（US-005） |
| AI-903 | M11 物流取消流程業務規則確認 | ✅ 完成（US-006，調查；實作延 Sprint 26） |

**Action Items 完成率**: 5/5（100%）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 21 | 12 | 12 | 100%（含 Buffer-A） |
| Sprint 22 | 12→8 | 8 | 100%（含 Buffer-A+B） |
| Sprint 23 | 12 | 12 | 100%（含 Buffer-A+B） |
| Sprint 24 | 7（承諾） | 7 | 100%（承諾範圍，Buffer 未動用） |
| Sprint 25 | 9（承諾）+2 Buffer | **11** | **100%+（承諾 + Buffer 全完成）** |

> **Sprint 25 觀察**: 承諾 9 SP 全達成，且 Buffer 2 SP 亦完成（Sprint 24 因 schema 救火 Buffer 0%，本 Sprint 因守門關卡上線、無救火，容量回穩）。

---

## 7. 技術債趨勢

| 指標 | Sprint 24 後 | Sprint 25 後 |
|------|------------|------------|
| `catch(Exception)` 生產程式碼 | 0 處 ✅ | **0 處** ✅ |
| `@Deprecated` 生產程式碼 | 0 處 ✅ | **0 處** ✅ |
| `@Test` 方法數（靜態） | 652 | **659** |
| Flyway 最新版本 | V55 | **V56** |
| schema 漂移守門 | 無（AI-901 待做） | ✅ `make validate-schema` 上線 |
| Conversation tenant_id | 無（AI-802 延續） | ✅ 補齊（V56） |
| M10 IM | 僅後端 STOMP | ✅ 前端閉環 + live E2E |
| 活躍 DEF | DEF-009 | DEF-009/010/011/012（皆低優先） |

---

## 8. 下一步方向（Sprint 26 預覽）

| 候選項 | 類型 | 預估 SP | 依據 |
|--------|------|--------|------|
| GitHub Actions 帳單解除 + push + 雲端 E2E 驗證 | DevOps | 1 | AI-1005（P1） |
| WS/即時功能真實 client E2E DoD 化 | 品質 | 1~2 | AI-1001（P1） |
| M11 取消技術債清理（DEF-010/011 + 測試） | 技術債 | 2 | AI-1004 |
| 後端 toMessageResponse conversationId 修正（DEF-012） | 技術債 | 1 | AI-1003 |
| 本地整合測試 DB 標準 make target | DevOps | 1 | AI-1002 |

> **Sprint 26 重點建議**: AI-1005（帳單解除後 push + 雲端 E2E）與 AI-1001（WS 真實 client E2E DoD 化）優先 —— 前者確保 Sprint 25 成果正式驗證入庫，後者制度化本 Sprint 最大教訓。

---

**文件版本**: v1.0
**建立日期**: 2026-06-30
**建立者**: PM Victoria + BA Beatrice + Dev David + SD Marcus + QA Quincy + Claude Code
