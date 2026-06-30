# Release Notes - v2026.10.10-01 (Sprint 25)

**發布日期**: 2026-10-10（規劃）／實作完成 2026-06-30
**發布類型**: Minor（新功能 + 品質守門 + 技術債清償）
**Sprint**: Sprint 25
**狀態**: 🟡 **已準備（本地全綠）；push / 部署待 GitHub Actions 帳單解除**

> Sprint 25 主題：**schema 漂移防線（品質守門）+ 多租戶隔離技術債清償 + M10 即時通訊前端化**

---

## 新功能 ✨

- **M10 IM 前端即時通訊閉環（US-004）**：前端（Next.js 16 + React 19）新增完整 chat UI —— 對話列表、訊息串、輸入框、未讀計數、已讀回執，透過 `@stomp/stompjs` + SockJS 連接後端 STOMP，CONNECT 帶 JWT、訂閱 `/queue/conversations/{id}/messages` 即時收訊、斷線自動重連。雙使用者即時 E2E（Playwright）**live 通過**。
- **本地 schema 漂移守門關卡（US-001, P0）**：`make validate-schema` —— 以 `ddl-auto=validate` + Flyway 對乾淨 PostgreSQL 啟動 backend，於 push 前攔截 entity↔migration 漂移（複製 GitHub E2E 啟動條件）。

## 改進 🚀

- **多租戶隔離技術債清償（US-003）**：`Conversation` 補 `tenant_id`（V56 migration，含依關聯實體回填 + FK + index），對話以 participant-scoping 達成跨租戶隔離。
- **entity ↔ migration 一致性盤點（US-002）**：51 entity 全數 validate 通過，jsonb/ARRAY 全庫盤點，產出 `ENTITY_MIGRATION_AUDIT.md`。
- **SSH pre-push keepalive（US-005）**：repo-local `core.sshCommand` 加 `ServerAliveInterval`，消除 act CI 長時間執行導致 `git push` SIGPIPE（exit 141）的根因。
- **M11 物流取消業務規則確認（US-006）**：PM/PO 確認「SHIPPING 不可取消、改走退貨/退款」，產出規則驗證報告（調查型，實作延 Sprint 26）。

## Bug 修復 🐛

- **SockJS 握手被 Security 擋（401）**：`SecurityConfig` 放行 `/ws/**` SockJS 握手（JWT 仍於 STOMP CONNECT frame 驗證）。此為 Sprint 24 STOMP 後端隱性缺口，由 US-004 live E2E 揪出。

## 資料庫遷移 🗄️

| Migration | 內容 |
|-----------|------|
| **V56** | `conversations` 新增 `tenant_id`（回填 listing→order→System Tenant + FK `fk_conversations_tenant` + index） |

> 升級需執行 Flyway migrate 至 V56。守門關卡 `make validate-schema` 已驗證 V1–V56 + validate 通過。

## 重大變更 ⚠️

- 無破壞性 API 變更。後端 STOMP destination 格式與 JWT 驗證機制不變。

## 已知問題 / 後續（延 Sprint 26）

| ID | 問題 | 處置 |
|----|------|------|
| DEF-010 | OrderStateMachine 轉換表允許 SHIPPING→CANCELLED，與 canCancel() 不一致（實務不受影響，cancelOrder 以 canCancel 守門） | Sprint 26 清理 |
| DEF-011 | cancelLogistics 錯誤碼誤用（E_7000/7002 → 應 E_7500 系列） | Sprint 26 修正 |
| DEF-012 | STOMP 廣播 payload conversationId=null（前端已以訂閱 id 補正） | Sprint 26 後端修 |
| DEF-009 | Logistics jsonb 映射慣例不一致 | 待需求觸發 |

## 驗證狀態 ✅

- 後端測試：單元 321 + 整合 323 = **644 通過 / 0 失敗**
- `@Test` 靜態計數：**659**（≥ 652，無退步）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0
- Checkstyle 0 violations；前端 eslint/build 通過
- schema 守門關卡：`make validate-schema` → exit 0
- 前端 live E2E：`at-m10-chat` 1 passed（雙使用者即時收發）

## 升級指南

1. 後端：部署新版 JAR，Flyway 自動 migrate 至 V56（生產 `ddl-auto=validate`）。
2. 前端：`npm install`（新增 `@stomp/stompjs`、`sockjs-client`）後 build 部署。
3. 確認 `NEXT_PUBLIC_API_URL` 指向後端，前端 SockJS 連線 `${API_BASE}/ws`。

## 部署狀態

- 🟡 **尚未 push / 部署**：GitHub Actions 帳單封鎖中（帳號層級，2026-06-29 復發）。本地 act + 644 測試 + live E2E 已全綠。
- ⏭️ 帳單解除後：push → GitHub E2E 驗證 → 依 RELEASE_CHECKLIST 部署（AI-1005）。

## 內含 Commit（Sprint 25）

| US | Commit |
|----|--------|
| US-001 | `be0d9a3` |
| US-002 | `4b7bfb3` |
| US-003 | `3c30f04` |
| US-004 | `617fea4` |
| US-005/006 + 收尾 | （收尾 commit） |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / BA Beatrice / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-06-30
**基於**: AISDLC v0.09 Release Management Workflow
