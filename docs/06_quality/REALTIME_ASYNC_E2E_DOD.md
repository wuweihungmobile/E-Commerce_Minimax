# WS / 即時 / 非同步功能的 E2E DoD 標準

> **文件類型**: 品質標準（Definition of Done 補充）
> **建立日期**: 2026-06-30
> **來源**: Sprint 25 Retro AI-1001（US-002）
> **適用**: 所有 WebSocket/STOMP、MQ 非同步、Server-Push 類功能

---

## 1. 背景與教訓

Sprint 24 的 M10 STOMP 後端「僅以 `SimpMessagingTemplate` 單元測試」即標記完成，繞過了 **SockJS 握手** 與 **JSON 序列化** 兩個真實環節。Sprint 25 的 live E2E（Playwright）一次揪出 **2 個潛伏缺口**：

1. SockJS 握手被 SecurityConfig 擋（401）——單元測試完全測不到。
2. STOMP 廣播 payload `conversationId=null`（序列化/欄位映射問題，DEF-012）。

**結論**：「需握手 + 序列化 + 跨程序傳遞」的功能，後端單元測試無法證明端到端正確性。

---

## 2. DoD 規則（強制）

對於以下類型的功能，**Definition of Done 必須包含一個「真實 client 端到端測試」**，不可僅以後端單元/Mock 測試結案：

| 功能類型 | 必要 E2E | 範例 |
|----------|----------|------|
| **WebSocket / STOMP / SockJS** | 真實瀏覽器 client（Playwright + `@stomp/stompjs`）建立連線、握手、收發 | M10 IM `at-m10-chat` |
| **Server-Sent Events / Long-polling** | 真實 client 訂閱並收到推送 | （未來） |
| **MQ 非同步副作用**（通知、事件） | 端到端驗證：觸發事件 → 消費 → 可觀測結果（DB/通知歷史/client） | M09 MQ 通知 |
| **需序列化跨程序傳遞的 payload** | 斷言 client 實際收到的 payload 欄位正確（非 null、型別正確） | 廣播 `conversationId` |

### 落實方式
- WS/即時功能：新增或擴充 `frontend/e2e/*.spec.ts`，於 `make validate-e2e`（本地）執行。
- 後端 payload 正確性：補單元測試斷言序列化後欄位（如 `ChatServiceStompBroadcastTest` 驗證 `conversationId` 非 null）。
- 驗收前：`make validate-e2e` 之 schema 啟動須通過；對應 spec 須綠（或於 advisory 模式下明確標註已知失敗與原因）。

---

## 3. backend-only 非同步/即時功能盤點（AC-002-3）

| 功能 | 機制 | 真實 client / 端到端 E2E | 狀態 |
|------|------|--------------------------|------|
| **M10 IM 即時聊天** | STOMP over SockJS | `at-m10-chat`（Playwright 雙使用者） | ✅ 已具備 |
| **M09 MQ 通知** | RabbitMQ（NotificationProducer→Consumer） | ❌ 無端到端（目前後端單元/整合為主） | 🟡 **列 DEF-013** |
| **Settlement 結算** | `@Scheduled` 批次 | Runbook + 整合測試 | 🟢 批次型，非即時推送，現況可接受 |

> **DEF-013（新增）**：M09 MQ 通知缺端到端驗證（觸發 → 消費 → 通知歷史/可觀測結果）。優先級低，待需求觸發或併入通知相關 Sprint。

---

## 4. 已知限制：乾淨 DB 的 E2E（at-m10-chat 等）

`make validate-e2e` 以 **Flyway 重建的乾淨 DB**（無 seed/租戶）執行全部 spec，目前 10 個 spec（含 `at-m10-chat`、`at-m17-*`、`at-m11-*`）失敗，根因為**多租戶註冊流程在乾淨 DB 回 401（E_1000）**——屬既有問題，且雲端 `ci.yml` 的 e2e job 本就 `continue-on-error`（從未強制）。

**處置**：
- `make validate-e2e` 對 e2e spec 失敗預設 **advisory**（對齊雲端 continue-on-error），schema 啟動驗證則硬擋。
- 要讓 e2e 真正成為阻擋性 DoD（`E2E_GATE_STRICT=1`），需先修復「乾淨 DB 註冊/seed」，**列 DEF-014**。

> **DEF-014（新增）**：e2e 在乾淨 DB 下註冊流程回 401，致 10 個 spec 失敗；需補測試 seed（租戶/帳號）或修正註冊流程，方能將 e2e 設為 strict DoD。

---

## 5. 參考

- [LOCAL_CI_VALIDATION.md](../08_deployment/LOCAL_CI_VALIDATION.md) — 本地優先 CI、`make validate-e2e`
- [SPRINT_25_RETRO.md](../05_development/SPRINT_25_RETRO.md) — AI-1001 來源
- `frontend/e2e/at-m10-chat.spec.ts` — WS 真實 client E2E 範例
- `backend/.../ChatServiceStompBroadcastTest.java` — 廣播 payload 序列化斷言範例

---

**文件版本**: v1.0｜**建立日期**: 2026-06-30｜**建立者**: QA Quincy + Dev David + Claude Code
