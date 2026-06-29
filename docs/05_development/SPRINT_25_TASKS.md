# Sprint 25 任務分解 / Sprint 25 Tasks

> **Sprint 編號**: Sprint 25
> **期間**: 2026-09-27 ~ 2026-10-10（預估）
> **建立日期**: 2026-06-29
> **基於**: [SPRINT_25_PLAN.md](../04_planning/SPRINT_25_PLAN.md)

---

## 執行狀態總覽

| US ID | 標題 | SP | 優先級 | 狀態 |
|-------|------|----|--------|------|
| US-001 | 本地 schema 漂移守門關卡（AI-901） | 2 | P0 | ✅ 完成 |
| US-002 | entity ↔ migration 一致性盤點（AI-902） | 2 | P1 | ✅ 完成 |
| US-003 | Conversation tenant_id 補齊（AI-802） | 2 | P1 | ✅ 完成 |
| US-004 | M10 WebSocket 前端整合（@stomp/stompjs） | 3 | P1 | ✅ 完成（含 live E2E） |
| US-005 | SSH pre-push 優化（Buffer-A，AI-804） | 1 | Buffer | ⬜ 待評估 |
| US-006 | M11 取消流程業務規則確認（Buffer-B，AI-903） | 1 | Buffer | ⬜ 待評估 |

**當前進度**: **4/4 承諾 US 完成（US-001~004，9 SP / 9 SP）**。US-004 即時 E2E 已於本機完整 stack（postgres+redis+backend JAR+Next dev）實跑 **通過**。

---

## US-001：本地 schema 漂移守門關卡（AI-901）

> **SP**: 2 | **優先級**: P0 | **狀態**: ✅ 完成（2026-06-29）

### 完成交付物

- [x] **scripts/validate-schema.sh** — schema 漂移守門關卡腳本
  - 啟動乾淨 PostgreSQL（無 initdb，schema 全由 Flyway 建立）+ Redis
  - 以 `ddl-auto=validate` + `flyway.enabled=true` 啟動打包 JAR（專屬 port 18080）
  - 精確複製 GitHub E2E（ci.yml `e2e` job）啟動條件
  - 啟動前檢查 port 未被佔用，避免誤打既有 backend 造成「假通過」
  - 支援 `SCHEMA_GATE_SKIP_BUILD=1`（重用既有 JAR）等環境變數

- [x] **Makefile** — 新增 `validate-schema` target

- [x] **docs/08_deployment/SCHEMA_DRIFT_GATE.md** — 完整使用指南（原理、用法、退出碼、何時執行）
- [x] **docs/08_deployment/LOCAL_CI_VALIDATION.md** — 加入守門關卡指引（act 抓不到漂移的提醒）

### AC 驗證

- [x] **AC-001-1**: `make validate-schema` 以 `ddl-auto=validate` 對 Flyway-migrated 乾淨 DB 啟動 backend
- [x] **AC-001-2**: 負向測試通過 — 故意在 `Conversation` 加無對應 migration 的欄位，關卡以
  `Schema-validation: missing column [schema_gate_drift_probe] in table [conversations]` → **exit 1** 攔下（驗證後已還原）
- [x] **AC-001-3**: 文件化使用方式（SCHEMA_DRIFT_GATE.md）

### 驗證證據（雙向）

| 測試 | 條件 | 結果 |
|------|------|------|
| 正向 | 乾淨碼（Flyway V55 後），重建 JAR + port 18080 | ✅ exit 0，schema 對齊 |
| 負向 | 植入漂移欄位 | ✅ exit 1，missing column 明確指出 |

### 重要修正紀錄

- 初版 health check 寫死 `localhost:8080`，與本機既有 dev backend 衝突 → **假通過**（漂移漏接）。
  修正為專屬 port `18080` + 啟動前 port 佔用檢查，負向測試才正確攔下。

---

## US-002：entity ↔ migration 一致性盤點（AI-902）

> **SP**: 2 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-29）

**目標**: 盤點所有 `@Entity` 欄位型別 vs Flyway DDL，建立對照清單，杜絕漂移復發。

### 完成交付物

- [x] **docs/06_quality/ENTITY_MIGRATION_AUDIT.md** — 完整盤點報告（方法、基線、jsonb 對照、防漂移慣例）
- [x] 以 US-001 守門關卡取得權威基線：`make validate-schema` → **exit 0**（51 entity 全數通過 validate）
- [x] DEF-009 登記至 [DEFERRED_ITEMS_TRACKER.md](../04_planning/DEFERRED_ITEMS_TRACKER.md)（Logistics jsonb 慣例不一致，低優先技術債）

### 盤點結果

| 盤點項 | 結果 |
|--------|------|
| 守門關卡基線（validate + Flyway + 乾淨 DB） | ✅ exit 0，無硬漂移 |
| Entity 表名 → CREATE TABLE migration | ✅ 50/50，零孤兒表 |
| jsonb 集合欄位 → migration 支撐 | ✅ 22 欄位（21 entity）全部對齊 |
| 歷史 `TEXT[]` 欄位 | ✅ 3 個（rooms.amenities/V55、article_versions.tags/V48、media_assets.tags/V54）全部已轉 jsonb |
| `@JdbcTypeCode(SqlTypes.ARRAY)` 殘留 | ✅ 0 個 |
| 發現的慣例不一致 | ⚠️ 1 項（Logistics）→ DEF-009，不在本 US 動工 |

### 防漂移慣例（已固化於報告 §7）

集合欄位一律 jsonb（禁 `SqlTypes.ARRAY` / 原生 `TEXT[]`）；每 entity 必有 CREATE TABLE migration；改 entity/migration 後 push 前必跑 `make validate-schema`；enum 以字串 + CHECK 約束存放。

---

## US-003：Conversation tenant_id 補齊（AI-802）

> **SP**: 2 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-29）

**目標**: Conversation entity 補 `tenant_id` + V56 migration + service 租戶歸屬；以 participant-scoping 達成跨租戶隔離。

### 完成交付物

- [x] **Conversation.java** — 新增 `tenantId` 欄位（`@Column(name = "tenant_id", nullable = false)`）
- [x] **V56__Add_Tenant_Id_To_Conversations.sql** — 加欄位 → 依關聯實體回填（listing → order → System Tenant）→ 設 NOT NULL + FK（`fk_conversations_tenant`，對齊 V29 慣例）+ index
- [x] **ChatService.java** — `resolveTenantId(listingId, orderId)` 依關聯實體推導租戶（與 V56 回填規則一致），`createConversation` 建立時寫入
- [x] **ChatServiceTenantResolutionTest.java** — 5 個單元測試覆蓋推導規則（listing 優先、order 退路、查無退回 System Tenant）
- [x] **M10ChatTenantIsolationIntegrationTest.java** — 3 個整合測試（跨租戶隔離 + tenant_id 落地）

### AC 驗證

- [x] **AC-003-1**: Conversation 新增 `tenantId` + V56 migration（含回填策略：依 listing/order 推導，退回 System Tenant）
- [x] **AC-003-2**: `ChatService.createConversation` 建立時設定 `tenantId`
  - 設計決策（使用者於 Planning 確認）：採「依關聯實體推導」而非 `TenantContext`，因對話 `tenant_id` 應歸屬賣方（listing/order）租戶
- [x] **AC-003-3**: 跨租戶不可見 —— 採 **participant-scoping**（`initiatorId`/`recipientId`）為存取邊界，而非 `TenantContext` 查詢過濾
  - 理由：聊天本質跨租戶（買方租戶 ↔ 賣方租戶），以 `TenantContext` 過濾讀取會使買方看不到自己發起的對話（功能破壞）
  - STOMP 廣播維持 per-conversation destination（訂閱已受權限控管）

### 驗證證據

| 測試 | 結果 |
|------|------|
| `make validate-schema`（V56 ↔ entity 對齊） | ✅ exit 0，無漂移 |
| ChatServiceTenantResolutionTest（單元，5）| ✅ 全綠 |
| ChatServiceStompBroadcastTest（單元，1）| ✅ 全綠（新增 Listing/Order mock 後無退步）|
| M10ChatTenantIsolationIntegrationTest（整合，3）| ✅ 跨租戶非參與者讀訊息 → 400(E-9005)；列表為空；DIRECT tenant_id 落地 System Tenant |
| M10ChatIntegrationTest（整合，8，回歸）| ✅ 全綠，tenant_id 欄位無造成既有測試退步 |

### 設計決策紀錄（AC-003-3 取捨）

AC-003-3 字面要求「查詢以 tenantId 範圍隔離」與 AC-003-2 已確認的「tenant_id 推導自賣方」存在衝突：若對讀取查詢加 `AND tenant_id = 當前租戶`，買方（租戶 X）將查不到歸屬賣方租戶 Y 的對話。經使用者裁決採 **participant-scoping**（既有存取邊界，已涵蓋「非參與者不可見」之意圖），不新增會破壞跨租戶對話的讀取過濾。

---

## US-004：M10 WebSocket 前端整合（@stomp/stompjs）

> **SP**: 3 | **優先級**: P1 | **狀態**: ✅ 完成（含 live E2E 驗證，2026-06-29）

**目標**: 前端接上 Sprint 24 STOMP 後端，訂閱對話頻道即時收訊。

**範圍決策（使用者確認）**: 前端 chat 為完全 greenfield（無既有 chat UI/service），經確認採「完整 chat UI」（對話列表 + 訊息串 + 未讀 + 已讀回執 + 送出 + STOMP 即時），範圍大於原估 3 SP。

### 完成交付物

- [x] **依賴**: `@stomp/stompjs ^7.3`、`sockjs-client ^1.6`、`@types/sockjs-client`（AC-004-1）
- [x] **frontend/src/services/chat.ts** — REST client（建立/列表/訊息/送出/已讀/刪除）+ 型別對齊 ChatDto
- [x] **frontend/src/hooks/useChatSocket.ts** — STOMP client：SockJS（`${baseUrl}/ws`）+ CONNECT 帶 JWT frame header + 多對話訂閱 + 內建 `reconnectDelay` 自動重連（AC-004-2、AC-004-3）
- [x] **frontend/src/components/chat/**：ConversationList（未讀徽章）/ MessageThread（左右對齊 + 已讀回執 + 連線狀態 + 自動捲動）/ MessageComposer（Enter 送出）
- [x] **frontend/src/app/dashboard/chat/page.tsx** — orchestrator：載入對話/訊息、STOMP 接線、訊息去重（REST + 廣播）、標記已讀、發起對話、刪除對話（AC-004-3）
- [x] **frontend/e2e/at-m10-chat.spec.ts** — 雙使用者即時收發 happy path（AC-004-4，spec 已撰寫並通過編譯/可發現）

### AC 驗證

- [x] **AC-004-1**: 新增 `@stomp/stompjs` + `sockjs-client`（對應後端 SockJS endpoint）
- [x] **AC-004-2**: STOMP client CONNECT 以 STOMP frame native header 帶 `Authorization: Bearer {token}`，訂閱 `/queue/conversations/{id}/messages`
- [x] **AC-004-3**: 收訊即時更新 UI（去重避免 REST 與廣播重複）；斷線由 `reconnectDelay` 自動重連
- [x] **AC-004-4**: Playwright E2E happy path **已撰寫並於本機完整 stack 實跑通過**（雙 context A↔B 即時收發，1 passed 12.2s）

### 驗證證據

| 驗證 | 結果 |
|------|------|
| `npx eslint`（chat 全檔） | ✅ 0 error / 0 warning |
| `npm run build`（next build + TS 型別檢查） | ✅ Compiled successfully，`/dashboard/chat` 正常產出 |
| 後端 JAR 啟動（Flyway V1–V56 + `ddl-auto=validate`） | ✅ 56 migrations 套用、validate 通過（再證 US-003 對齊） |
| **`npx playwright test at-m10-chat`（live 雙使用者即時 E2E）** | ✅ **1 passed** —— B 經 STOMP 即時收到 A 的訊息 |

### 🔴 live E2E 揪出並修正的 Sprint 24 STOMP 後端缺口

> live E2E 是首個真實前端 WS client，揭露兩個過去從未被端到端測過的缺口（後端整合測試用 `SimpMessagingTemplate` 直送，繞過握手與序列化）：

1. **SecurityConfig 未放行 SockJS 握手**（後端修正）: `/api/ws/info` 回 **401**（`anyRequest().authenticated()` 擋住握手）。
   修法：`SecurityConfig` 新增 `.requestMatchers("/ws/**").permitAll()`；JWT 驗證仍於 STOMP CONNECT frame 由 `StompAuthChannelInterceptor` 處理 —— **修缺口、非改契約**。
2. **廣播 payload `conversationId` 為 null**（前端因應，不改後端）: `ChatService.toMessageResponse` 廣播時 `MessageResponse.conversationId=null`，導致 client 端無法路由。
   修法：`useChatSocket` 訂閱為 per-conversation，以訂閱的 `id` 為權威來源補上 `conversationId`。**建議後端後續修正 `toMessageResponse` 以利其他 client（列 follow-up）**。

### 後端契約限制（不變更後端，已於前端因應並記錄）

1. **每對話未讀計數**: `getUserConversations` 回傳的每對話 `unreadCount` 為 null（後端僅給列表級總數）→ 前端改以「多對話 STOMP 訂閱 + session 即時累計」呈現未讀徽章（非開啟對話收到訊息即 +1）。
2. **已讀回執非即時**: `markAsRead` 不廣播已讀事件 → 已讀回執（✓/✓✓）依訊息載入時的 `isRead` 顯示，不會即時翻轉。

### Next.js 16 破壞性 lint 規則學習（AGENTS.md 要求先讀 docs）

- `react-hooks/refs`: 禁止於 render 期間更新 ref（`ref.current = x`）→ 改於 effect 內同步。
- `react-hooks/set-state-in-effect`: 禁止於 effect body 同步 setState → 連線狀態改由 STOMP client callback（beforeConnect/onConnect/onClose）驅動，靜態狀態於 render 衍生。

---

## US-005（Buffer-A）：SSH pre-push 優化（AI-804）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

---

## US-006（Buffer-B）：M11 物流取消流程業務規則確認（AI-903）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估（僅調查，不含實作）
