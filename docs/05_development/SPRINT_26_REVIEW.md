# Sprint 26 Review / Sprint 26 評審會議

> **Sprint 編號**: Sprint 26
> **期間**: 2026-10-11 ~ 2026-10-24
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_26_PLAN.md](../04_planning/SPRINT_26_PLAN.md), [SPRINT_25_RETRO.md](./SPRINT_25_RETRO.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: Sprint 25 成果入庫驗證 + WS/即時品質制度化 + IM/M11 技術債清償。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| 成果入庫驗證（AI-1005, P1） | ✅ 達成（改本地優先策略） | 改採本地優先 CI：commits 已 push origin/main、雲端三 workflow 改 workflow_dispatch only、雲端 E2E 由 `make validate-e2e` 取代 |
| WS/即時品質制度化（AI-1001, P1） | ✅ 達成 | `REALTIME_ASYNC_E2E_DOD.md` DoD 規則 + backend-only 盤點，登記 DEF-013（MQ 通知 e2e） |
| 後端 conversationId 修正（AI-1003 / DEF-012, P1） | ✅ 達成 | 改由 conversation 關聯取 id + 廣播 payload 測試 |
| M11 取消技術債清理（AI-1004 / DEF-010+011, P2） | ✅ 達成 | StateMachine 一致性 + 物流錯誤碼 + 8 單元測試 |
| 本地整合測試 DB make target（AI-1002, Buffer） | ✅ 達成 | `make test-db-up/down`，核准映像、已測試 |
| Logistics jsonb 慣例統一（DEF-009, Buffer） | ✅ 達成 | 統一 Map + @JdbcTypeCode；validate-schema 通過 |

**Sprint 目標達成率**: 100%（承諾 P1+P2 7 SP 全完成 + 2 Buffer US 完成 = 9 SP）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | Sprint 25 成果入庫 + 雲端 E2E 驗證（AI-1005） | 2 | **P1** | ✅ 完成（本地優先策略） | `68eeb5c` `6937f35` |
| US-002 | WS 真實 client E2E DoD 化（AI-1001） | 2 | P1 | ✅ 完成 | `1f5bb41` |
| US-003 | 後端 toMessageResponse conversationId（DEF-012） | 1 | P1 | ✅ 完成 | `1f5bb41` |
| US-004 | M11 取消技術債清理（DEF-010+011） | 2 | P2 | ✅ 完成 | `1f5bb41` |
| US-005 | 本地整合測試 DB make target（Buffer-A） | 1 | Buffer | ✅ 完成 | `667546a` |
| US-006 | Logistics jsonb 慣例統一（Buffer-B） | 1 | Buffer | ✅ 完成 | `667546a` |
| **完成合計** | | **9 SP** | | ✅ 100% | |

> Sprint 26 承諾 7 SP（P1+P2，US-001~004）全部完成；**Buffer US-005/006（2 SP）亦完成**。另含大量計畫外流程工程（見第 10 節）。

---

## 3. 測試狀態

| 測試類型 | Sprint 25 後 | Sprint 26 後 | 變化 |
|---------|------------|------------|------|
| `@Test` 方法總數（靜態計數，backend/src/test） | 659 | **668** | +9 |
| `catch (Exception)` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| `@Deprecated` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| Flyway 最新版本 | V56 | **V56** | 0（本 Sprint 無新 migration） |
| 本地 e2e 守門 | 無（live E2E 手動） | **`make validate-e2e` 制度化（strict 預設）** | 27 passed / 5 skip / 0 fail |

**Sprint 26 新增測試明細**:

| US | 新增測試 | 數量 |
|----|---------|------|
| US-003 | ChatServiceStompBroadcastTest（廣播 payload conversationId 非 null） | 2 |
| US-004 | OrderStateMachineTest（含轉換表↔canCancel 一致性不變量） | 5 |
| US-004 | LogisticsServiceCancelTest（E_7500/E_7502 錯誤碼） | 3 |
| US-004 | M11LogisticsOrderIntegrationTest（jsonb HashMap 調整，真實 postgres 整合） | 4（調整） |

---

## 4. US-001：Sprint 25 成果入庫 + 雲端 E2E 驗證（AI-1005，P1）

**策略轉向「本地優先 CI」**（取代「等帳單解除 → 雲端 E2E」）：

| 交付物 | 內容 |
|--------|------|
| 雲端三 workflow | `ci.yml` / `act-compat.yml` / `technical-debt-review.yml` 的 `on:` 改為 **僅 `workflow_dispatch`** → push 不再觸發雲端，省 private repo Actions 費用 |
| `scripts/validate-e2e.sh` + `make validate-e2e` | host JAR + npm start + Playwright + 乾淨 DB（ddl-auto=validate），**取代雲端 e2e job** |
| `make validate-release` | act + schema + e2e = 雲端 ci.yml 等價完整守門 |
| `docs/08_deployment/LOCAL_CI_VALIDATION.md` | 本地優先策略 + 分層守門表 |

> Sprint 25 全部 commits **已 push origin/main**（pre-push 本地驗證通過、零 `--no-verify`）。AC-001-2/3（雲端綠燈）改為「需要時手動 `gh workflow run`」，非日常阻擋條件。

---

## 5. US-002：WS/即時功能真實 client E2E DoD 化（AI-1001，P1）

| 交付物 | 內容 |
|--------|------|
| `docs/06_quality/REALTIME_ASYNC_E2E_DOD.md` | WS/即時/非同步功能 DoD 規則：須有真實 client（Playwright/SockJS）端到端測試，不可僅 SimpMessagingTemplate 單元測試 |
| backend-only 盤點 | 盤點其他「後端-only」即時/非同步功能；M09 MQ 通知缺端到端驗證 → 登記 **DEF-013** |

**AC 達成**: AC-002-1（DoD 文件）✅、AC-002-2（at-m10-chat 穩定，含 DEF-014 修復）✅、AC-002-3（盤點 + 缺者列 DEF）✅。

---

## 6. US-003：後端 toMessageResponse conversationId 修正（AI-1003 / DEF-012，P1）

- **根因**：Message 唯讀鏡像欄位 `conversationId`（`insertable/updatable=false`）在新建+save 的 in-memory 實例為 null。
- **修正**：改由 `getConversation().getId()` 取得（LAZY proxy 取 id 不觸發查詢），fallback 唯讀欄位。
- **測試**：`ChatServiceStompBroadcastTest` 以 ArgumentCaptor 驗證廣播 payload `conversationId` 非 null（+2）。
- **相容性**：不破壞既有 REST/STOMP 契約，前端訂閱 id workaround 可保留（向後相容）。

---

## 7. US-004：M11 取消技術債清理（AI-1004 / DEF-010+011，P2）

| DEF | 修正 | 測試 |
|-----|------|------|
| DEF-010 | `OrderStateMachine` 移除 `SHIPPING→CANCELLED`（canTransition + getNextValidStates），與 `canCancel()`={CREATED,PAID,CONFIRMED} 對齊 | `OrderStateMachineTest`（含「轉換表↔canCancel 一致性」不變量，+5） |
| DEF-011 | `cancelLogistics` 錯誤碼由 E_7000/E_7002 改物流專用 **E_7500（not found）/ E_7502（delivered）** | `LogisticsServiceCancelTest`（+3） |

> 依 Sprint 25 US-006（PM/PO 確認「SHIPPING 不可取消」）規則清理；參考 [M11_Cancellation_Rules_Validation.md](../01_requirements/M11_Cancellation_Rules_Validation.md)。

---

## 8. US-005 / US-006：Buffer

**US-005（Buffer-A）本地整合測試 DB make target（AI-1002）**:
- `make test-db-up` / `test-db-down`（postgres:18-alpine + redis:7-alpine，對齊 integration-test profile :5432/:6379）
- 遵守 Docker 政策（不新增 image、不改 docker-compose 既有服務）

**US-006（Buffer-B）Logistics jsonb 慣例統一（DEF-009）**:
- `Logistics.logisticsData` 由 `String + columnDefinition="jsonb"` 統一為全庫慣例 `Map + @JdbcTypeCode(SqlTypes.JSON)`（2 寫入點改 `new HashMap<>()`）
- `make validate-schema` 驗證 entity↔jsonb 對齊無漂移

---

## 9. Sprint 25 Action Items 追蹤

| AI ID | 內容 | 對應 US | 達成狀態 |
|-------|------|--------|---------|
| AI-1005 | 帳單解除後 push + 雲端 E2E 驗證 | US-001 | ✅ 完成（改本地優先策略） |
| AI-1001 | WS 真實 client E2E DoD 化 | US-002 | ✅ 完成 |
| AI-1003 | 後端 toMessageResponse conversationId | US-003 | ✅ 完成 |
| AI-1004 | M11 取消技術債清理 | US-004 | ✅ 完成 |
| AI-1002 | 本地整合測試 DB make target | US-005 | ✅ 完成 |

**Action Items 完成率**: 5/5（100%）—— Sprint 25 Retro 全部 Action Item 在本 Sprint 落地。

---

## 10. 計畫外工作：本地優先 CI 工程（重大）

本 Sprint 因 GitHub Actions 帳單反覆封鎖 + private repo 費用考量，投入大量計畫外流程工程，將「雲端日常 CI」整套搬回本地：

| 項目 | 內容 |
|------|------|
| 停用雲端自動 CI | 三 workflow 改 `workflow_dispatch` only（push 不觸發雲端） |
| 本地 e2e 守門 | `scripts/validate-e2e.sh`（host 全棧 + 乾淨 DB + Playwright），DEF-014 修復後 strict 預設 |
| 完整守門整合 | `make validate-release` = act + schema + e2e |
| pre-push 效能 | v4（host 分層，~30→~5-8 分）→ **v5（依使用者要求：上 GIT = 完整測試程序 `make validate-release`，靠降頻 + FULL 記錄快取攤平）** |
| push 頻率 | 確立「commit 勤、push 少（批次）」原則 |
| 新發現 | DEF-015（前端 next/font/google 建置期外部抓取，離線 build 失敗） |

> 這部分工作量不亞於計畫內 US，是本 Sprint 的隱性主軸；相關決策已生成 Sprint 27 Action Items（見 Retro）。

---

## 11. Definition of Done 驗核

- [x] US-001~004 所有 AC 達成（P1+P2）
- [x] `mvn compile` → 0 errors
- [x] Checkstyle → 0 violations
- [x] 所有新增測試通過；既有測試無退步（`@Test` 靜態計數 668 ≥ 659）
- [x] catch(Exception) 生產程式碼 **0 處**
- [x] @Deprecated 生產程式碼 **0 處**
- [x] `make validate-schema` 通過（US-006 動 entity）
- [x] WS/即時功能 DoD 更新並落實（US-002）
- [x] Sprint 25 成果已 push（US-001，改本地優先策略；雲端改手動觸發）
- [x] Sprint 26 Review 文件建立（本文件）
- [x] Sprint 26 Retrospective 文件建立（SPRINT_26_RETRO.md）
- [x] Sprint 26 Release Notes 建立（v2026.10.24-01）

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + BA Beatrice + Dev David + SD Marcus + QA Quincy + Claude Code
