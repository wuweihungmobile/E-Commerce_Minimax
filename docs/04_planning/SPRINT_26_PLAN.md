# Sprint 26 計劃 / Sprint 26 Plan

> **Sprint 編號**: Sprint 26
> **期間**: 2026-10-11 ~ 2026-10-24 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-30
> **基於**: [SPRINT_25_RETRO.md](../05_development/SPRINT_25_RETRO.md) + [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（sprint-planning skill）

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 25 承諾 US 完成 | ✅ 4/4（9 SP）+ 2/2 Buffer（2 SP）= 11 SP | US-001~006 全達成 |
| Sprint 25 收尾 | ✅ Review/Retro/Release Notes 完成 | 收尾 docs **未提交**（使用者檢視中） |
| Sprint 25 commits | ⚠️ US-001~004 已 commit（領先 origin 6），**尚未 push** | GitHub Actions 帳單封鎖 |
| 🔴 GitHub Actions 帳單 | 🔴 **封鎖中**（帳號層級，2026-06-29 復發） | 阻擋雲端 CI/E2E 驗證與 push 入庫 |
| 活躍 DEF | DEF-009/010/011/012（皆低優先技術債） | 帶入 Sprint 26 |
| Sprint 25 Retro Action Items | AI-1001~1005 共 5 項 | 本 Sprint 主要來源 |
| Product Backlog / Roadmap | ⚠️ 無正式 backlog/roadmap 文件 | Sprint 26 候選來自 Retro AIs + DEF |
| CI Playwright E2E | ✅ ci.yml `npx playwright test` 已自動跑全部 specs（含 at-m10-chat） | AI-1001 重點在 DoD 制度化 + 雲端穩定性 |

---

## 1. Sprint 26 目標

> **主題**: Sprint 25 成果入庫驗證 + WS/即時品質制度化 + IM/M11 技術債清償

Sprint 25 完成大量功能與守門機制，但因 GitHub 帳單封鎖，成果尚未 push 入庫、雲端 E2E 未驗證；同時 live E2E 揭露 WS 類功能「後端-only 即標記完成」的流程缺口，並留下 4 項低優先技術債（DEF-009~012）。Sprint 26 三大重點：

1. **成果入庫驗證**（AI-1005，**P1**）：帳單解除後 push Sprint 25 成果並確認 GitHub 雲端 E2E 綠燈
2. **WS/即時品質制度化**（AI-1001，P1）：將「真實 client E2E」納入 WS/即時功能 DoD，確保 at-m10-chat 在雲端 E2E 穩定通過
3. **技術債清償**（DEF-012/010/011/009）：後端 conversationId、M11 取消一致性與錯誤碼、Logistics jsonb 慣例

---

## 2. Sprint 目標對齊

| 目標 | 對應 Action Item / DEF | 類型 |
|------|----------------------|------|
| Sprint 25 成果 push + 雲端 E2E 驗證 | AI-1005（P1） | DevOps / 入庫 |
| WS 真實 client E2E DoD 化 | AI-1001（P1） | 品質 / 流程 |
| 後端 toMessageResponse conversationId 修正 | AI-1003 / DEF-012（P1） | 技術債 |
| M11 取消技術債清理 + 測試 | AI-1004 / DEF-010+011（P2） | 技術債 |
| 本地整合測試 DB 標準 make target | AI-1002（Buffer） | DevOps |
| Logistics jsonb 慣例統一 | DEF-009（Buffer） | 技術改善 |

---

## 3. Deferred Items 審查

| 狀態 | 說明 |
|------|------|
| 高優先級 DEF | 無 |
| 中/低優先級 DEF | DEF-009（Logistics jsonb）、DEF-010（StateMachine 一致性）、DEF-011（cancelLogistics 錯誤碼）、DEF-012（廣播 conversationId） |
| 本 Sprint 處理 | DEF-012（US-003）、DEF-010+011（US-004）、DEF-009（Buffer US-006） |

---

## 4. User Stories

### US-001：Sprint 25 成果入庫 + 雲端 E2E 驗證（AI-1005，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: ✅ 已達成（改以本地優先策略）

**🔄 2026-06-30 收斂（策略轉向本地優先）**: 原 US 假設「等帳單解除 → push → 雲端 E2E 綠燈」。實際採行**本地優先 CI**：
- Sprint 25 全部 commits **已 push 至 origin/main**（pre-push 完整本地 act 通過、零 --no-verify）。
- 雲端三 workflow 已改 `workflow_dispatch` only（push 不再觸發雲端，省 private repo Actions 費用）。
- 雲端 E2E 由本地 `make validate-e2e`（host JAR + Playwright + 乾淨 DB + ddl-auto=validate）**取代**。
- AC-001-2/3（雲端綠燈）改為「需要時手動 `gh workflow run`」，非日常阻擋條件。詳見 [LOCAL_CI_VALIDATION.md](../08_deployment/LOCAL_CI_VALIDATION.md)。

**目標（原）**: 帳單解除後，commit Sprint 25 收尾 docs、push 全部 commits，確認 GitHub 雲端 CI/E2E（含 at-m10-chat）綠燈，使 Sprint 25 成果正式入庫。

**AC-001-1**: Sprint 25 收尾 docs commit；全部 commits push 至 origin/main（pre-push 本地 act 通過、🔴 不得 --no-verify）
**AC-001-2**: GitHub Actions 全 pipeline 綠燈（Layer 0/1 + E2E）
**AC-001-3**: GitHub E2E 的 at-m10-chat（WS 即時）於雲端通過，確認 SecurityConfig /ws 修正在雲端生效

**風險**: 帳單未解除則本 US 阻擋 → 緩解：本地 act + 644 測試 + live E2E 已全綠作為臨時保證；若仍封鎖則順延 Sprint 27 並持續以本地驗證。

---

### US-002：WS/即時功能真實 client E2E DoD 化（AI-1001，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: ✅ 完成（DoD 文件 + 盤點，at-m10-chat 乾淨 DB 限制列 DEF-014）

**目標**: 制度化本 Sprint 最大教訓 —— WebSocket/STOMP 等「需握手+序列化」功能，DoD 必須包含真實 client E2E（不可僅 SimpMessagingTemplate 單元測試）。
**產出**: [REALTIME_ASYNC_E2E_DOD.md](../06_quality/REALTIME_ASYNC_E2E_DOD.md)（DoD 規則 + backend-only 盤點，新增 DEF-013 MQ 通知 e2e、DEF-014 乾淨 DB 註冊 401）。

**AC-002-1**: 更新 DoD 標準文件（docs/06_quality 或 Document_Quality_Checklist），明訂 WS/即時功能須有真實 client（Playwright/SockJS）E2E
**AC-002-2**: 確認 at-m10-chat 在 GitHub E2E job 穩定通過（必要時補強 selector / 等待策略，沿用 Sprint 25 經驗）
**AC-002-3**: 盤點其他「後端-only」即時/非同步功能（如 MQ 通知）是否需補真實 client/端到端驗證，缺者列 DEF

---

### US-003：後端 toMessageResponse conversationId 修正（AI-1003 / DEF-012，P1）

> **SP**: 1 | **優先級**: P1 | **狀態**: ✅ 完成（改由 conversation 關聯取 id + 廣播 payload 測試）

**目標**: 修正 `ChatService.toMessageResponse` 使廣播 payload 的 `conversationId` 不為 null，利於 mobile 等未來 client（前端目前以訂閱 id 補正，屬 workaround）。
**實作**: 根因為 Message 唯讀鏡像欄位 `conversationId`（insertable/updatable=false）在新建+save 的 in-memory 實例為 null；改由 `getConversation().getId()` 取得（LAZY proxy 取 id 不觸發查詢），fallback 唯讀欄位。新增 `ChatServiceStompBroadcastTest` 廣播 payload conversationId 非 null 測試。

**AC-003-1**: `toMessageResponse` 正確填入 `conversationId`（由 message → conversation 取得）
**AC-003-2**: 補單元測試驗證廣播/回應 payload `conversationId` 非 null
**AC-003-3**: 不破壞既有 REST/STOMP 契約；前端 workaround 可保留（向後相容）
**依賴**: 建議與 US-001 push 後雲端 E2E 一併回歸

---

### US-004：M11 取消技術債清理（AI-1004 / DEF-010+011，P2）

> **SP**: 2 | **優先級**: P2 | **狀態**: ✅ 完成（StateMachine 一致性 + 錯誤碼 + 8 單元測試）

**目標**: 依 Sprint 25 US-006 確認的規則（SHIPPING 不可取消）清理 M11 取消相關技術債。
**實作**: DEF-010 移除 OrderStateMachine 的 SHIPPING→CANCELLED（canTransition + getNextValidStates），新增 OrderStateMachineTest（含「轉換表↔canCancel 一致性」不變量）；DEF-011 cancelLogistics 改用 E_7500（not found）/E_7502（delivered），新增 LogisticsServiceCancelTest 3 測試。

**AC-004-1**（DEF-010）: `OrderStateMachine` 轉換表移除/限縮 `SHIPPING→CANCELLED`，與 `canCancel()`={CREATED,PAID,CONFIRMED} 對齊
**AC-004-2**（DEF-011）: `cancelLogistics` 錯誤碼由 E_7000/E_7002 改為物流專用 E_7500 系列
**AC-004-3**: 補物流取消整合測試（涵蓋允許/拒絕狀態），驗證規則
**參考**: [M11_Cancellation_Rules_Validation.md](../01_requirements/M11_Cancellation_Rules_Validation.md)

---

### US-005（Buffer-A）：本地整合測試 DB 標準 make target（AI-1002，P2）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

**目標**: 解決 Sprint 25 觀察到的「整合測試 DB 容器 churn」—— 提供統一的測試 DB 生命週期管理。

**AC-005-1**: 新增 `make test-db-up` / `make test-db-down`（postgres+redis，對齊 integration-test profile）
**AC-005-2**: 文件化：整合測試（`mvn verify -Pintegration-test`）與 pre-commit 核心測試所需 DB 的啟動方式
**AC-005-3**: 遵守 Docker 政策（不新增 image、不改 docker-compose 既有服務）

---

### US-006（Buffer-B）：Logistics jsonb 映射慣例統一（DEF-009，低）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

**目標**: 將 `Logistics.logisticsData` 由 `String + columnDefinition="jsonb"` 統一為全庫慣例 `Map + @JdbcTypeCode(SqlTypes.JSON)`（若評估有結構化讀寫需求）。

**AC-006-1**: 評估改動面與相容性；若低風險則統一映射，`make validate-schema` 通過
**AC-006-2**: 若改動面過大或無實際讀寫需求，維持現況並更新 DEF-009 結論

---

## 5. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | Sprint 25 成果入庫 + 雲端 E2E 驗證（AI-1005） | 2 | **P1** |
| US-002 | WS 真實 client E2E DoD 化（AI-1001） | 2 | P1 |
| US-003 | 後端 toMessageResponse conversationId（DEF-012） | 1 | P1 |
| US-004 | M11 取消技術債清理（DEF-010+011） | 2 | P2 |
| US-005 | 本地整合測試 DB make target（Buffer-A） | 1 | Buffer |
| US-006 | Logistics jsonb 慣例統一（Buffer-B） | 1 | Buffer |
| **P1+P2 合計** | | **7 SP** | |
| **含 Buffer 合計** | | **9 SP** | |

> Velocity 對齊：S21=12 / S22=8 / S23=12 / S24=7 / S25=11。Sprint 26 承諾 7 SP（含 1 項外部依賴 US-001），保留 2 SP Buffer，總 9 SP 略保守 —— 因 US-001 受帳單外部依賴影響，預留緩衝。

---

## 6. 執行順序建議

```
US-001（成果入庫，AI-1005）  → 帳單解除即執行（最高優先，使 S25 成果正式入庫）
US-003（conversationId 修正）→ 小修，與 US-001 雲端回歸一併驗證
US-002（WS E2E DoD 化）      → 制度化教訓，確保雲端 at-m10-chat 穩定
US-004（M11 取消技術債）      → 依 US-006 規則清理 + 補測試
US-005（Buffer：test-db）     → Buffer，改善開發體驗
US-006（Buffer：jsonb）       → Buffer，最後評估容量
```

---

## 7. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| 🔴 GitHub Actions 帳單未解除 | 高 | 高 | US-001 阻擋；以本地 act + 644 測試 + live E2E 為臨時保證，必要時順延 Sprint 27 |
| 多 Sprint 未 push 致 commit 堆積、衝突風險 | 中 | 中 | 帳單解除後優先 US-001 一次 push；本地單一 main 分支降低衝突 |
| OrderStateMachine 改動影響既有訂單流程 | 低 | 中 | DEF-010 僅移除未使用的 SHIPPING→CANCELLED 路徑；補測試驗證 canCancel 行為不變 |
| Logistics jsonb 改映射造成讀寫相容問題 | 中 | 中 | US-006 為 Buffer，先評估；無實際結構化需求則維持現況 |

---

## 8. 測試規劃（QA Quincy）

| US | 測試重點 | 類型 |
|----|---------|------|
| US-001 | GitHub 雲端 CI/E2E 全綠（含 at-m10-chat） | 雲端 E2E 回歸 |
| US-002 | at-m10-chat 穩定性；DoD 文件 | E2E / 流程 |
| US-003 | 廣播/回應 payload conversationId 非 null | 單元 |
| US-004 | 物流取消允許/拒絕狀態轉換 | 整合 |
| US-005 | make test-db-up/down 可用性 | 手動驗證 |

---

## 9. Definition of Done（Sprint 26）

- [ ] US-001~004 所有 AC 達成（P1+P2）
- [ ] `mvn compile` → 0 errors；Checkstyle → 0 violations
- [ ] 所有新增測試通過；既有測試無退步（`@Test` 靜態計數 ≥ 659）
- [ ] catch(Exception) 生產 **0 處**、@Deprecated 生產 **0 處**
- [ ] `make validate-schema` 通過（若動 entity/migration）
- [ ] **WS/即時功能 DoD 更新並落實**（US-002）
- [ ] Sprint 25 成果已 push 且 GitHub 雲端 E2E 綠燈（US-001，若帳單解除）
- [ ] Sprint 26 Review / Retrospective / Release Notes 建立

---

## 10. Action Items 追蹤（來自 Sprint 25 Retro）

| AI ID | 內容 | Sprint 26 對應 US |
|-------|------|-----------------|
| AI-1005 | 帳單解除後 push + 雲端 E2E 驗證 | US-001（P1） |
| AI-1001 | WS 真實 client E2E DoD 化 | US-002（P1） |
| AI-1003 | 後端 toMessageResponse conversationId（DEF-012） | US-003（P1） |
| AI-1004 | M11 取消技術債清理（DEF-010+011） | US-004（P2） |
| AI-1002 | 本地整合測試 DB make target | US-005（Buffer-A） |

---

**文件版本**: v1.0
**建立日期**: 2026-06-30
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
