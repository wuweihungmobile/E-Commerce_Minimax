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
| **M09 MQ 通知** | Redis List（NotificationProducer→Consumer，`@Scheduled` 輪詢） | ✅ `NotificationProduceConsumeTest`（produce→佇列→consume→Notification+歷史，真實序列化） | ✅ Sprint 27 完成（DEF-013） |
| **Settlement 結算** | `@Scheduled` 批次 | Runbook + 整合測試 | 🟢 批次型，非即時推送，現況可接受 |

> **DEF-013（✅ Sprint 27 完成）**：補 M09 端到端測試時，揪出並修復 produce→consume 斷鏈真 bug —— producer 用 `opsForStream().add()`（Stream）、consumer 用 `opsForList().rightPop()`（List）讀同一 key，型別不相容（WRONGTYPE）致通知永不被消費；改為兩端一致 List。**驗證了本 DoD 的核心論點**：3 個既有測試（mock listOps / ReflectionTestUtils 直呼 / 整個 RedisTemplate mock）都繞過真實傳遞，唯有 produce→consume 端到端測試才抓得到。

---

## 4. e2e 已制度化為嚴格守門（DEF-014 已修復）

`make validate-e2e` 以 **Flyway 重建的乾淨 DB** 執行全部 spec，**預設嚴格模式（spec 失敗即阻擋）**。

**DEF-014 根因與修復（2026-06-30）**：先前 10 個 spec（`at-m10-chat`、`at-m17-*`、`at-m11-*`）在乾淨 DB 失敗，表象為「註冊回 401（Authentication required）」。經以 curl 對乾淨 DB backend 實測，**註冊 HTTP 201、登入 HTTP 200 全部正常**——並非產品/後端 bug。真正根因是 **`scripts/validate-e2e.sh` 誤將前端打包的 `NEXT_PUBLIC_API_URL` 設為 `.../api/v2`**：前端 `lib/api.ts` 的 `API_ENDPOINTS` 路徑已含 `/v2`（如 `/v2/auth/register`），故註冊 URL 變成 `/api/v2/v2/auth/register` → servlet path 不匹配任何 `permitAll` → 落入 `anyRequest().authenticated()` → 401。修為 `NEXT_PUBLIC_API_URL=http://localhost:8080/api`（與前端預設、雲端 `npm run build` 一致）後 → **27 passed / 5 conditional-skip / 0 failed**。

**現行政策**：
- `make validate-e2e` 預設 **strict**：e2e spec 失敗即守門失敗（阻擋）。schema 啟動驗證亦硬擋。
- 5 個 conditional-skip 為 spec 內 `test.skip()`（乾淨 DB 無 seed admin/租戶等資料時優雅跳過），非失敗。
- 環境異常（如建置期 Google Fonts 網路抓取失敗）需臨時放行：`E2E_GATE_STRICT=0 make validate-e2e`（不建議常態使用）。

> **DEF-015（✅ Sprint 27 完成）**：前端 `next/font/google`（layout.tsx 的 Geist/Geist Mono）在建置期向 Google Fonts 抓取，離線時 `npm run build` 失敗。經查 Geist 變數從未被 CSS/Tailwind 消費（死碼），直接移除 import → 離線 build 通過、零視覺影響。

---

## 5. 參考

- [LOCAL_CI_VALIDATION.md](../08_deployment/LOCAL_CI_VALIDATION.md) — 本地優先 CI、`make validate-e2e`
- [SPRINT_25_RETRO.md](../05_development/SPRINT_25_RETRO.md) — AI-1001 來源
- `frontend/e2e/at-m10-chat.spec.ts` — WS 真實 client E2E 範例
- `backend/.../ChatServiceStompBroadcastTest.java` — 廣播 payload 序列化斷言範例

---

**文件版本**: v1.0｜**建立日期**: 2026-06-30｜**建立者**: QA Quincy + Dev David + Claude Code
