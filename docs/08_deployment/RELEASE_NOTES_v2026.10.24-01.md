# Release Notes - v2026.10.24-01 (Sprint 26)

**發布日期**: 2026-10-24（規劃）／實作完成 2026-07-01
**發布類型**: Minor（品質制度化 + 技術債清償 + CI 策略轉向）
**Sprint**: Sprint 26
**狀態**: ✅ **已 push origin/main（本地優先驗證全綠；雲端改手動觸發）**

> Sprint 26 主題：**Sprint 25 成果入庫驗證 + WS/即時品質制度化 + IM/M11 技術債清償**

---

## 新功能 ✨

- 無新業務功能（本 Sprint 聚焦品質、技術債與 CI 策略）。

## 改進 🚀

- **本地優先 CI 策略（US-001）**：三 workflow（`ci.yml` / `act-compat.yml` / `technical-debt-review.yml`）改為僅 `workflow_dispatch`，push 不再觸發雲端 Actions（省 private repo 費用）；雲端 e2e 由 `make validate-e2e`（host 全棧 + 乾淨 DB + Playwright）取代。
- **WS/即時功能 DoD 制度化（US-002）**：產出 `REALTIME_ASYNC_E2E_DOD.md`，明訂 WebSocket/STOMP 等「需握手+序列化」功能須有真實 client 端到端測試；盤點後端-only 非同步功能，登記 DEF-013（M09 MQ 通知）。
- **本地整合測試 DB 標準化（US-005）**：新增 `make test-db-up` / `test-db-down`（postgres:18-alpine + redis:7-alpine，對齊 integration-test profile）。
- **本地 e2e 守門 strict 化（DEF-014）**：`make validate-e2e` 修正 `NEXT_PUBLIC_API_URL`（移除雙 /v2）後改為 strict 預設，基準 27 passed / 5 skip / 0 fail。
- **pre-push 守門演進**：v4（host 分層提速）→ **v5（上 GIT = 完整測試程序 `make validate-release`，靠 push 降頻 + FULL 記錄快取攤平成本）**。

## Bug 修復 🐛

- **後端廣播 conversationId=null（US-003 / DEF-012）**：`ChatService.toMessageResponse` 改由 `getConversation().getId()` 取得 conversationId（fallback 唯讀鏡像欄位），使 STOMP 廣播 payload 不為 null，利於 mobile 等 client。
- **OrderStateMachine 一致性（US-004 / DEF-010）**：移除轉換表 `SHIPPING→CANCELLED`，與 `canCancel()`={CREATED,PAID,CONFIRMED} 對齊。
- **物流取消錯誤碼（US-004 / DEF-011）**：`cancelLogistics` 由誤用的 E_7000/E_7002 改為物流專用 **E_7500（not found）/ E_7502（delivered）**。

## 技術改善 🔧

- **Logistics jsonb 慣例統一（US-006 / DEF-009）**：`Logistics.logisticsData` 由 `String + columnDefinition="jsonb"` 統一為全庫慣例 `Map + @JdbcTypeCode(SqlTypes.JSON)`；`make validate-schema` 通過。

## 資料庫遷移 🗄️

- 本 Sprint **無新 Flyway migration**（最新仍為 V56）。US-006 jsonb 調整為 entity 映射層級，欄位本即 jsonb。

## 重大變更 ⚠️

- 無破壞性 API 變更。後端 STOMP destination 格式與 JWT 驗證機制不變；前端訂閱 id workaround 向後相容保留。
- **CI 流程變更**：push 不再自動觸發雲端 CI；本地 `make validate-release` / pre-push 為日常守門。雲端需手動 `gh workflow run`。

## 已知問題 / 後續（延 Sprint 27）

| ID | 問題 | 處置 |
|----|------|------|
| DEF-013 | M09 MQ 通知缺端到端驗證 | Sprint 27（套用 AI-1001 DoD） |
| DEF-015 | 前端 next/font/google 建置期外部抓取（離線 build 失敗） | Sprint 27（改 next/font/local） |

## 驗證狀態 ✅

- `@Test` 靜態計數：**668**（≥ 659，無退步；新增 ChatServiceStompBroadcastTest +2、OrderStateMachineTest +5、LogisticsServiceCancelTest +3）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0
- Checkstyle 0 violations；前端 eslint/build 通過
- `make validate-schema` → exit 0（US-006 動 entity 後驗證）
- 本地 e2e：`make validate-e2e` → 27 passed / 5 skip / 0 fail（strict）

## 升級指南

1. 後端：部署新版 JAR，Flyway 維持 V56（無新 migration）。
2. 前端：無新依賴；維持 `NEXT_PUBLIC_API_URL` 指向後端 `/api`（不含 /v2）。
3. CI/維運：改用本地 `make validate-release` 守門；雲端驗證改手動 `gh workflow run`。

## 內含 Commit（Sprint 26）

| US / 項目 | Commit |
|----------|--------|
| US-001（本地優先 CI） | `68eeb5c` |
| Sprint 25 收尾 + Sprint 26 規劃文件入庫 | `6937f35` |
| US-002/003/004（WS DoD + conversationId + M11 技術債） | `1f5bb41` |
| US-005/006 + DEF-014（test-db + jsonb + e2e strict） | `667546a` |
| pre-push v4（host 分層） | `42da063` |
| pre-push v5（完整測試程序） | `4170706` |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / BA Beatrice / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
