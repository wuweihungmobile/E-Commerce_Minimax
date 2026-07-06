# Release Notes - v2028.09.09-01 (Sprint 75)

**發布日期**: 2028-09-09（規劃）／實作完成 2026-07-06
**發布類型**: 🔍 探查 + 🔴 安全修復（P0；schema-free；後端聚焦，無前端變動）
**Sprint**: Sprint 75（多 Sprint 測試強化計劃）
**狀態**: ✅ 已 push

> Sprint 75 主題：**`ChatService` 範圍探查**，確認既有 6 個 public 方法擁有權檢查與測試覆蓋皆已完整（本多 Sprint 測試強化計劃首次「目標 Service 本身無缺口」的結果）；依指示延伸檢查 WebSocket/STOMP 即時通訊層後，發現並修復先前 5 個 Sprint 皆未觸及的新型態授權缺口 `DEF-035`。

---

## 🔍 範圍探查（US-001）

- **`ChatService` 6 個 public 方法全數確認擁有權檢查正確**：`createConversation`/`sendMessage`/`getUserConversations`/`getMessages`/`markAsRead`/`deleteConversation` 皆透過 `ConversationRepository.findByIdAndUserId`（`initiatorId OR recipientId = :userId`）正確實作 participant-scoping，且既有單元測試（`ChatServiceStompBroadcastTest`/`ChatServiceTenantResolutionTest`）+ 整合測試（`M10ChatIntegrationTest`）+ 跨租戶隔離測試（`M10ChatTenantIsolationIntegrationTest`）已完整覆蓋。本 Sprint 未新增 `ChatService` 本身的測試（無重複測試必要）。

## 🔴 安全修復 P0：`DEF-035`（新發現）

- **`StompAuthChannelInterceptor` SUBSCRIBE 授權缺失，任何連線者可竊聽他人對話**：
  - **修復前**：僅在 STOMP CONNECT 時驗證 JWT，SUBSCRIBE frame 完全未檢查目的地授權；`/queue/conversations/{id}/messages` 並非 Spring per-user 目的地，任何已連線者（含匿名，因 CONNECT 缺失/無效 JWT 仍放行連線）皆可訂閱任意 conversationId 的即時訊息，完全繞過 REST 層已正確實作的 participant-scoping 保護（CWE-862 Missing Authorization）。
  - **紅燈證明**：新增 `StompAuthChannelInterceptorTest.java`（6 個測試），非參與者訂閱、匿名連線訂閱、訂閱不存在對話三案例，修復前執行**確認未被攔截**，實測證實漏洞存在。
  - **修復後**：新增 SUBSCRIBE 攔截邏輯，比對目的地 `/queue/conversations/{id}/messages`，查 `ConversationRepository` 確認訂閱者為該對話 initiator/recipient 才放行，否則阻斷訂閱；未變更既有 CONNECT 邏輯與其餘目的地。

## 測試 / 驗證 ✅

- **`StompAuthChannelInterceptorTest` 紅綠燈流程**：修復前 6 tests 3 Failures + 2 Errors（`DEF-035`×3 攻擊案例未攔截 + 2 個因程式碼當時不查 repository 觸發的 `UnnecessaryStubbingException`）→ 修復後 6 tests 0 fail。
- **後端單元回歸**：**741 tests，0 fail**。
- **後端全量回歸**（`mvn verify -Pintegration-test`）：**BUILD SUCCESS**，單元 741 + 整合（failsafe）342 = **1083 tests，0 fail**（含既有 `M10ChatIntegrationTest`/`M10ChatTenantIsolationIntegrationTest`，本次未遇到已知的 STOMP flaky 問題）。
- **schema 漂移守門**：`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。

## 技術決策 / 已知限制 ⚠️

- **未變更 STOMP CONNECT 邏輯**：CONNECT 時 JWT 缺失/無效仍放行連線的設計未變更，因 SUBSCRIBE 層新增的授權檢查已足以完整阻斷未認證訂閱者存取任何對話（`userId` 為 `null` 必然無法匹配 initiator/recipient），避免無依據擴大修改範圍。
- **`createConversation` 的 `recipientId` 未驗證跨租戶/存在性**：屬聊天本質上跨租戶（買方 ↔ 賣方）的既定設計，非本 Sprint 鎖定的 IDOR 類缺口模式，未列為 `DEF` 追蹤。
- **無前端變動**：本 Sprint 純後端探查與 STOMP 層修復。

## 資料庫遷移 🗄️

- 無（schema-free；純 Java 訊息攔截器邏輯，未新增/修改任何 Entity 或 Repository 方法）。

## 內含 Commit（Sprint 75）

| US / 項目 | 說明 |
|----------|------|
| Sprint 75 Plan | `ChatService` 範圍探查 + STOMP 授權缺失修復計劃（2 US / 4 SP）|
| US-001 | `ChatService` 範圍探查與既有覆蓋確認（無程式碼變更）|
| US-002 | `DEF-035` 修復：`StompAuthChannelInterceptor` SUBSCRIBE 授權缺失 |
| Sprint 75 收尾 | Review / Retro / Release Notes + trackers |

> 實際 commit hash 詳見 git log（依 Sprint 慣例於收尾 commit 訊息中記錄）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**基於**: AISDLC v0.09 Release Management Workflow
