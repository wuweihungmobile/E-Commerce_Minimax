# Sprint 75 計劃 / Sprint 75 Plan

> **Sprint 編號**: Sprint 75
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_74_PLAN.md`/`SPRINT_74_RETRO.md` §7「後續 Sprint 待處理清單」——`ChatService`
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 74 收尾狀態 | ✅ 已收尾並 push | `CmsService` 測試強化 + `DEF-032`/`DEF-033` 跨租戶修復完成，`DEF-034` 記錄待決策 |
| `ChatService` 範圍探查 | ✅ 完成 | 1 個 Service（6 個 public 方法、319 行）+ `ChatController`（114 行）+ WebSocket/STOMP 基礎設施（`WebSocketConfig`、`StompAuthChannelInterceptor`） |
| 探查中發現的擁有權/租戶隔離問題 | ✅ 已標記並確認：`ChatService` 本身 6 個 public 方法**皆已有正確的 participant-scoping 檢查**（無缺口）；但依指示特別留意的 WebSocket/STOMP 即時通訊層發現 1 項新缺口（`DEF-035`，SUBSCRIBE 授權缺失），已確認並修復 | 詳見「1. 既有覆蓋現況」、US-002（`DEF-035`） |
| 探查誠實揭露 | `ChatService` 是本多 Sprint 測試強化計劃中**首個「Service 本身擁有權檢查已全數正確」**的案例——6 個方法皆透過 `ConversationRepository.findByIdAndUserId`（initiator OR recipient）做 participant-scoping，且既有單元測試（`ChatServiceStompBroadcastTest`/`ChatServiceTenantResolutionTest`）+ 整合測試（`M10ChatIntegrationTest`）+ 跨租戶隔離測試（`M10ChatTenantIsolationIntegrationTest`）已完整覆蓋；真正的缺口出現在 REST/Service 層以外的 STOMP 訊息代理層 | 依 Rule 12「大聲失敗」誠實揭露：本 Sprint **未新增 `ChatService` 本身的單元測試**，因既有覆蓋已完整、不做重複測試 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

### 1.1 `ChatService`（`core/chat/ChatService.java`，319 行，6 個 public 方法）

| 方法 | 既有測試涵蓋 | 擁有權/租戶檢查現況 |
|---|---|---|
| `createConversation` | 單元（`ChatServiceTenantResolutionTest`）+ 整合（IT-CHAT-001/002） | ✅ 正常，`resolveTenantId` 依 listing/order 推導租戶，無關聯退回 System Tenant |
| `sendMessage` | 單元（`ChatServiceStompBroadcastTest`）+ 整合（IT-CHAT-004） | ✅ 正常，`ConversationRepository.findByIdAndUserId`（initiator OR recipient）擋非參與者 |
| `getUserConversations` | 整合（IT-CHAT-003、IT-CHAT-TEN-002） | ✅ 正常，query 已用 `(initiatorId OR recipientId) = :userId` |
| `getMessages` | 整合（IT-CHAT-005、IT-CHAT-TEN-001） | ✅ 正常，`findByIdAndUserId` 擋非參與者 |
| `markAsRead` | 整合（IT-CHAT-006） | ✅ 正常，`findByIdAndUserId` |
| `deleteConversation` | 整合（IT-CHAT-007） | ✅ 正常，`findByIdAndUserId` |

**結論**：6 個 public 方法擁有權檢查皆正確，比對 `DEF-018/019/023/024/026/027/028/029/030/032/033` 歷史模式後未發現同類缺口。REST 層由 `ChatController.java`（`/v2/chat/**`）暴露，皆 `@PreAuthorize("isAuthenticated()")`。

### 1.2 WebSocket/STOMP 即時通訊層（依指示特別留意）

`ChatService.sendMessage` 發送訊息後會透過 `SimpMessagingTemplate.convertAndSend` 廣播到 `/queue/conversations/{id}/messages`（`WebSocketConfig` 設定 `enableSimpleBroker("/topic", "/queue")`）。深入檢查負責 STOMP 連線驗證的 `StompAuthChannelInterceptor`（`api/filter/`）後發現：

- 該攔截器**僅在 CONNECT 時驗證 JWT**，對後續 **SUBSCRIBE** frame 完全沒有檢查
- `/queue/conversations/{id}/messages` 只是命名慣例，並非 Spring 的 per-user 目的地（需要 `/user/**` 前綴才有此語意）——任何已連線者都能訂閱**任意** conversationId 的 queue
- CONNECT 時若 JWT 缺失/無效，程式碼僅記錄警告仍放行連線（不拒絕），故**匿名連線也能訂閱**
- 前端 `useChatSocket.ts` 只會訂閱使用者自己的對話（正常使用不會觸發），純粹是後端授權缺口

**結論**：❌ **缺口（`DEF-035`）**——任何人取得或猜到一個 conversationId（UUID）即可用 raw STOMP client 竊聽該對話的即時訊息，完全繞過 REST 層已正確實作的 participant-scoping 保護。此攔截器**零測試覆蓋**（無任何既有 `StompAuthChannelInterceptorTest`）。

---

## 2. Sprint 75 目標

> **主題**: `ChatService` 範圍探查確認既有覆蓋完整 + STOMP SUBSCRIBE 授權缺失修復（`DEF-035`，新發現）

`ChatService` 本身探查後確認 6 個方法擁有權檢查與測試覆蓋皆已完整，無需重複補測試；依指示特別留意的 WebSocket/STOMP 即時通訊層則發現一項新的、先前未被任何 Sprint 觸及的授權缺口並修復。

---

## 3. User Story

### US-001：`ChatService` 範圍探查與既有覆蓋確認

> **SP**: 1 | **優先級**: P2 | **狀態**: ✅ 完成

**AC-001-1**：列出 `ChatService` 全部 6 個 public 方法，逐一確認既有測試涵蓋（單元/整合/E2E）與擁有權/租戶檢查現況。

**AC-001-2**：以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」角度逐一審視，比對 `DEF-018/019/023/024/026/027/028/029/030/032/033` 歷史模式。

**AC-001-3**：確認 `ChatService` 本身無缺口後，依指示延伸檢查其依賴的 WebSocket/STOMP 基礎設施（`WebSocketConfig`、`StompAuthChannelInterceptor`）。

---

### US-002：修復 STOMP SUBSCRIBE 授權缺失（`DEF-035`，新發現，已確認）

> **SP**: 3 | **優先級**: P0（安全） | **狀態**: ✅ 完成

**背景**：`StompAuthChannelInterceptor` 僅在 CONNECT 驗證 JWT，SUBSCRIBE 完全未檢查目的地授權，任何連線者（含匿名）皆可訂閱任意對話的即時訊息 queue，屬 CWE-862 Missing Authorization，與 `ChatService` REST 層已正確實作的 participant-scoping 保護不一致。

**AC-002-1**：先在 `StompAuthChannelInterceptorTest.java`（新檔）新增**修復前會失敗（紅燈）**測試：非參與者訂閱、匿名連線訂閱、訂閱不存在的對話，驗證修復前皆未被攔截。

**AC-002-2**：`StompAuthChannelInterceptor` 新增 SUBSCRIBE 攔截邏輯，比對目的地 pattern `/queue/conversations/{id}/messages`，注入 `ConversationRepository` 查詢訂閱者是否為該對話 initiator/recipient，不符或未認證則回傳 `null` 阻斷訂閱；不影響既有 CONNECT 邏輯與其餘目的地的訂閱。

**AC-002-3**：AC-002-1 測試轉綠；新增 initiator/recipient 訂閱自己對話應放行、與對話無關目的地不受影響的對照測試，避免修復矯枉過正。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | `ChatService` 範圍探查與既有覆蓋確認 | 1 | P2 |
| US-002 | 修復 STOMP SUBSCRIBE 授權缺失（`DEF-035`） | 3 | P0（安全） |
| **合計** | | **4** | |

> **Velocity 參考**：明顯低於 Sprint 66-74（8-14 SP 量級），因探查確認 `ChatService` 本身既有測試覆蓋已完整、無需重複補測試，實際工作量集中在 STOMP 層單一新缺口的修復，與 Sprint 70（3 SP，單一緊急安全修復）量級相近。

---

## 5. Definition of Done

- [x] US-001：完整探查 `ChatService` 6 個 public 方法，確認既有測試覆蓋與擁有權檢查現況，誠實記錄「無需新增測試」的判斷依據
- [x] US-002：新增 `StompAuthChannelInterceptorTest.java`（6 個測試）→ 紅燈確認失敗（3 案例未被攔截）→ 修復 `StompAuthChannelInterceptor` → 轉綠（6 tests 0 fail）
- [x] 開發-編譯-測試循環：每完成一批測試立即編譯 + 執行驗證，未累積
- [x] 因本 Sprint 修改生產程式碼（US-002），完成後執行全量回歸 `mvn verify -Pintegration-test`，**1083 tests 0 fail**（單元 741 + 整合 342）
- [x] `make validate-schema` 無漂移，EXIT_CODE=0
- [x] `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-035`（記入「已完成延後項目」）
- [x] Sprint 75 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `StompAuthChannelInterceptorTest.java`（新檔，6 個測試） |
| 生產程式碼修復 | `StompAuthChannelInterceptor.java`（`DEF-035`，新增 SUBSCRIBE 授權邏輯 + `ConversationRepository` 依賴） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-035`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 76+ 規劃參考：

1. `LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`
2. 待決策事項：`DEF-034`（`CmsService` 公開端點租戶範圍設計）、`ADMIN` 角色權限邊界（租戶內 vs 全域）盤點（延續自 Sprint 73 Retro）
3. 新觀察（本 Sprint 發現）：STOMP/WebSocket 是本專案除 REST 之外唯一的即時通訊管道，未來若新增其他 `@MessageMapping`/訂閱目的地，應比照 `DEF-035` 修復模式在設計階段就檢查 SUBSCRIBE 授權，避免重演「REST 層有檢查、訊息代理層沒有」的落差

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
