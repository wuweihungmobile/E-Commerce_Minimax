# Sprint 25 Review / Sprint 25 評審會議

> **Sprint 編號**: Sprint 25
> **期間**: 2026-09-27 ~ 2026-10-10
> **評審日期**: 2026-06-30（AI-AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-06-30
> **基於**: [SPRINT_25_PLAN.md](../04_planning/SPRINT_25_PLAN.md), [SPRINT_25_TASKS.md](./SPRINT_25_TASKS.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: schema 漂移防線（品質守門）+ 多租戶隔離技術債清償 + M10 即時通訊前端化。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| 本地 schema 漂移守門關卡（AI-901, P0） | ✅ 達成 | `make validate-schema`：ddl-auto=validate + Flyway 對乾淨 DB 啟動，負向測試可攔漂移 |
| entity ↔ migration 一致性盤點（AI-902） | ✅ 達成 | 51 entity 全數 validate 通過，jsonb/ARRAY 盤點，DEF-009 登記 |
| Conversation tenant_id 補齊（AI-802） | ✅ 達成 | entity + V56 migration + participant-scoping 隔離 |
| M10 WebSocket 前端整合 | ✅ 達成 | 完整 chat UI + STOMP 即時，**live E2E 通過** |
| SSH pre-push 優化（AI-804, Buffer-A） | ✅ 達成 | core.sshCommand keepalive 消除 exit 141 根因 |
| M11 物流取消規則確認（AI-903, Buffer-B） | ✅ 達成 | BA 規則確認，PM/PO 簽核，實作延 Sprint 26（DEF-010/011） |

**Sprint 目標達成率**: 100%（承諾 P0+P1 9 SP 全完成 + 2 Buffer US 完成）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | 本地 schema 漂移守門關卡（AI-901） | 2 | **P0** | ✅ 完成 | `be0d9a3` |
| US-002 | entity ↔ migration 一致性盤點（AI-902） | 2 | P1 | ✅ 完成 | `4b7bfb3` |
| US-003 | Conversation tenant_id 補齊（AI-802） | 2 | P1 | ✅ 完成 | `3c30f04` |
| US-004 | M10 WebSocket 前端整合（@stomp/stompjs） | 3 | P1 | ✅ 完成（含 live E2E） | `617fea4` |
| US-005 | SSH pre-push 優化（Buffer-A） | 1 | Buffer | ✅ 完成 | （收尾 commit） |
| US-006 | M11 取消流程業務規則確認（Buffer-B） | 1 | Buffer | ✅ 完成（調查型） | （收尾 commit） |
| **完成合計** | | **11 SP** | | ✅ 100% | |

> Sprint 25 承諾 9 SP（P0+P1，US-001~004）全部完成；**Buffer US-005/006（2 SP）亦完成**（與 Sprint 24 Buffer 0% 啟動相比，本 Sprint 容量充裕）。

---

## 3. 測試狀態

| 測試類型 | Sprint 24 後 | Sprint 25 後 | 變化 |
|---------|------------|------------|------|
| `@Test` 方法總數（靜態計數，backend/src/test） | 652 | **659** | +7 |
| 後端測試執行（單元 321 + 整合 323） | — | **644 通過 / 0 失敗** | ✅ |
| `catch (Exception)` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| `@Deprecated` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| Flyway 最新版本 | V55 | **V56** | +1（Conversation tenant_id） |
| 前端 chat | 無 | **新增（service/hook/components/page + e2e）** | M10 前端閉環 |

**Sprint 25 新增測試明細**:

| US | 新增測試 | 數量 |
|----|---------|------|
| US-003 | ChatServiceTenantResolutionTest（租戶推導單元） | 5 |
| US-003 | M10ChatTenantIsolationIntegrationTest（跨租戶隔離整合） | 3 |
| US-004 | at-m10-chat.spec.ts（Playwright 雙使用者即時 E2E，live 通過） | 1 |

---

## 4. US-001：本地 schema 漂移守門關卡（AI-901, P0）

| 交付物 | 內容 |
|--------|------|
| `scripts/validate-schema.sh` | 乾淨 PostgreSQL + Flyway migrate + ddl-auto=validate 啟動（專屬 port 18080），複製 GitHub E2E 啟動條件 |
| `Makefile` | 新增 `validate-schema` target |
| `docs/08_deployment/SCHEMA_DRIFT_GATE.md` | 完整使用指南 |

**雙向驗證**：正向（乾淨碼 exit 0）、負向（植入漂移欄位 → exit 1 並指出 missing column）。修正初版 health check 寫死 8080 → 改專屬 18080 + port 佔用檢查（避免假通過）。

> 本 Sprint **首次實戰**：US-003 改 entity + V56 migration 後跑 `make validate-schema` → exit 0，守門關卡發揮作用。

---

## 5. US-002：entity ↔ migration 一致性盤點（AI-902）

- `docs/06_quality/ENTITY_MIGRATION_AUDIT.md` 完整盤點報告
- 守門基線 `make validate-schema` → exit 0（51 entity 全數 validate 通過）
- Entity 表名 → CREATE TABLE：50/50 零孤兒表；jsonb 集合欄位 22 個全對齊；`@JdbcTypeCode(SqlTypes.ARRAY)` 殘留 0
- 發現 1 項慣例不一致（Logistics jsonb）→ DEF-009（不在本 US 動工）

---

## 6. US-003：Conversation tenant_id 補齊（AI-802）

| 交付物 | 內容 |
|--------|------|
| `Conversation.java` | 新增 `tenantId`（NOT NULL） |
| `V56` migration | 加欄位 + 依關聯實體回填（listing→order→System Tenant）+ FK + index（對齊 V29） |
| `ChatService.java` | `resolveTenantId` 依關聯實體推導，createConversation 寫入 |

**設計決策（PM/PO 確認）**: AC-003-3 採 **participant-scoping** 為存取邊界，而非 TenantContext 查詢過濾 —— 因聊天本質跨租戶（買方↔賣方，對話歸屬賣方租戶），以 TenantContext 過濾會破壞買方存取。整合測試（M10ChatTenantIsolationIntegrationTest）驗證跨租戶非參與者不可見。

---

## 7. US-004：M10 WebSocket 前端整合（@stomp/stompjs）

| 交付物 | 內容 |
|--------|------|
| `services/chat.ts` | REST client（建立/列表/訊息/送出/已讀/刪除） |
| `hooks/useChatSocket.ts` | STOMP + SockJS + JWT(CONNECT frame) + 多對話訂閱 + 自動重連 |
| `components/chat/*` | ConversationList（未讀）/ MessageThread（已讀回執+連線狀態）/ MessageComposer |
| `app/dashboard/chat/page.tsx` | 即時收發、訊息去重、發起/刪除對話 |
| `e2e/at-m10-chat.spec.ts` | 雙使用者即時 E2E（**live 通過，1 passed**） |

**範圍決策（PM/PO 確認）**: 前端 chat 為完全 greenfield，採「完整 chat UI」（>原估 3 SP）。

### 🔴 live E2E 揪出並修正的 Sprint 24 STOMP 後端缺口

| 缺口 | 修正 |
|------|------|
| SockJS 握手 `/api/ws` 被 Security 擋成 401 | SecurityConfig 放行 `/ws/**`（JWT 仍於 STOMP CONNECT 驗證）—— 修缺口、非改契約 |
| 廣播 payload `conversationId=null` | 前端以訂閱 id 補正；建議後端後續修（DEF-012） |

> 這兩缺口後端整合測試抓不到（用 SimpMessagingTemplate 直送，繞過握手與序列化）—— 正是 live E2E 的價值。

---

## 8. US-005：SSH pre-push 優化（AI-804, Buffer-A）

- **根因**：`git push` 先連 SSH 取 refs → pre-push hook 跑 act（10–20 分鐘）→ SSH 閒置被關 → 傳資料時 SIGPIPE（exit 141）
- **方案**：repo-local `git config core.sshCommand "ssh -o ServerAliveInterval=60 -o ServerAliveCountMax=30"`（keepalive，最小侵入、可逆）
- HTTPS push 評估後不採用（需 PAT、侵入性高、未根治）
- 文件：`docs/08_deployment/SSH_PREPUSH_KEEPALIVE.md`

---

## 9. US-006：M11 物流取消流程業務規則確認（AI-903, Buffer-B，調查型）

- **PM/PO 決策**：SHIPPING（已出貨）**不可取消**，改走退貨/退款
- 可取消狀態（CREATED/PAID/CONFIRMED）皆在物流單建立前 → **不需** provider 取消 API、不需訂單↔物流連動，現況 `canCancel()` 正確
- 發現 2 項待清理（延 Sprint 26）：DEF-010（OrderStateMachine SHIPPING→CANCELLED 不一致）、DEF-011（cancelLogistics 錯誤碼誤用）
- 文件：`docs/01_requirements/M11_Cancellation_Rules_Validation.md`
- **依 Plan 為調查型，本 Sprint 不動程式碼**

---

## 10. Sprint 24 Action Items 追蹤

| AI ID | 內容 | 對應 US | 達成狀態 |
|-------|------|--------|---------|
| AI-901 | 本地補 schema 驗證關卡 | US-001（P0） | ✅ 完成 |
| AI-902 | entity ↔ migration 一致性盤點 | US-002 | ✅ 完成 |
| AI-802 | Conversation tenant_id 評估 | US-003 | ✅ 完成 |
| AI-804 | SSH timeout pre-push hook 優化 | US-005 | ✅ 完成 |
| AI-903 | M11 物流取消流程業務規則確認 | US-006 | ✅ 完成（調查，實作延 Sprint 26） |

**Action Items 完成率**: 5/5（100%）—— Sprint 24 Retro 全部 Action Item 在本 Sprint 落地。

---

## 11. Definition of Done 驗核

- [x] US-001~004 所有 AC 達成（P0+P1）
- [x] `mvn compile` → 0 errors
- [x] Checkstyle → 0 violations
- [x] 所有新增測試通過（後端 644 / 0 失敗；前端 live E2E 1 passed）
- [x] 既有測試無退步（`@Test` 靜態計數 659 ≥ 652）
- [x] catch(Exception) 生產程式碼 **0 處**
- [x] @Deprecated 生產程式碼 **0 處**
- [x] schema 守門關卡（US-001）可實際攔截漂移（負向測試通過）
- [x] US-005/006 Buffer 完成
- [x] Sprint 25 Review 文件建立（本文件）
- [x] Sprint 25 Retrospective 文件建立（SPRINT_25_RETRO.md）
- [x] Sprint 25 Release Notes 建立（v2026.10.10-01）
- [ ] push 至 GitHub（⏸️ 待 GitHub Actions 帳單解除；本地 644 測試 + live E2E 已全綠）

---

**文件版本**: v1.0
**建立日期**: 2026-06-30
**建立者**: PM Victoria + BA Beatrice + Dev David + SD Marcus + Claude Code
