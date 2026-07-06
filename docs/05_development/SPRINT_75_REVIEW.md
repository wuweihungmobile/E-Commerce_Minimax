# Sprint 75 Review / Sprint 75 評審會議

> **Sprint 編號**: Sprint 75
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `ChatService` 範圍探查確認既有覆蓋完整 + STOMP SUBSCRIBE 授權缺失修復（`DEF-035`，新發現）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `ChatService` 範圍探查與既有覆蓋確認 | 1 | ✅ 完成 |
| US-002 | 修復 STOMP SUBSCRIBE 授權缺失（`DEF-035`） | 3 | ✅ 完成 |

**4 SP 全數完成**。本 Sprint 先完整探查 `ChatService`（6 個 public 方法），依使用者要求主動以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者/當前租戶」角度逐一審視，確認 `ChatService` 本身**首次出現「Service 擁有權檢查已全數正確、既有測試已完整覆蓋」**的結果；依指示延伸檢查 WebSocket/STOMP 即時通訊層後，發現並修復一項先前 5 個 Sprint（68/70/72/73/74）皆未觸及的新型態授權缺口。

---

## 2. 交付內容

### 探查結論（US-001）

- `ChatService`（`core/chat/ChatService.java`，319 行）6 個 public 方法 `createConversation`/`sendMessage`/`getUserConversations`/`getMessages`/`markAsRead`/`deleteConversation` 皆透過 `ConversationRepository.findByIdAndUserId`（`initiatorId OR recipientId = :userId`）正確實作 participant-scoping，且已有既有單元測試（`ChatServiceStompBroadcastTest`、`ChatServiceTenantResolutionTest`）+ 整合測試（`M10ChatIntegrationTest`，IT-CHAT-001~008）+ 跨租戶隔離測試（`M10ChatTenantIsolationIntegrationTest`，IT-CHAT-TEN-001~003）完整覆蓋。比對 `DEF-018/019/023/024/026/027/028/029/030/032/033` 歷史模式後，**未發現同類缺口**，本 Sprint 未新增 `ChatService` 本身的測試（無重複測試必要）。

### 新發現並修復：`DEF-035`（STOMP SUBSCRIBE 授權缺失）

- **問題**：`StompAuthChannelInterceptor`（`api/filter/`）僅在 STOMP CONNECT 時驗證 JWT，對後續 SUBSCRIBE frame 完全未檢查目的地授權。`WebSocketConfig` 用 `enableSimpleBroker("/topic", "/queue")`，`/queue/conversations/{id}/messages` 只是命名慣例並非 Spring per-user 目的地（需要 `/user/**` 前綴才有此語意），任何已連線者皆可訂閱**任意** conversationId 的 queue；且 CONNECT 時 JWT 缺失/無效僅記錄警告仍放行連線，匿名連線亦可訂閱。任何人取得或猜到一個 conversationId（UUID）即可用 raw STOMP client 竊聽該對話的即時訊息，完全繞過 REST 層 `ChatService` 已正確實作的 participant-scoping 保護（CWE-862 Missing Authorization）。此攔截器先前**零測試覆蓋**。
- **紅燈證明**：新增 `StompAuthChannelInterceptorTest.java`（新檔，6 個測試）。先為 `StompAuthChannelInterceptor` 加入 `ConversationRepository` 依賴但**尚未加 SUBSCRIBE 授權邏輯**時執行 → TC-STOMP-SUB-003（非參與者）、004（匿名連線）、005（對話不存在）三案例皆**未被攔截**（`preSend` 回傳非 null，訊息正常放行），實測證實漏洞成立；另兩個「應放行」案例（initiator/recipient 訂閱自己對話）因程式碼當時根本不查 `ConversationRepository`，觸發 Mockito `UnnecessaryStubbingException`（預期中的現象，非漏洞）。
- **修復**：`StompAuthChannelInterceptor` 新增 SUBSCRIBE 攔截邏輯：比對目的地 pattern `^/queue/conversations/([0-9a-fA-F-]{36})/messages$`，取出 conversationId 後查 `ConversationRepository.findById`，確認訂閱者的 `UserPrincipal.userId` 為該對話的 initiator 或 recipient 才放行，否則回傳 `null` 阻斷訂閱（Spring `ChannelInterceptor` 慣例：`preSend` 回傳 `null` 即不再傳遞給 broker，訂閱不會被註冊）；未修改既有 CONNECT 邏輯與其餘目的地（如 `/topic/**`）的訂閱行為。
- **轉綠**：修復後重跑，TC-STOMP-SUB-003/004/005 通過；但 TC-STOMP-SUB-004（匿名）因修復後在查 repository 前就因取不到 `userId` 而短路攔截，導致先前為了讓程式碼跑到後段而加的 `when(conversationRepository.findById(...))` stub 變成多餘（Mockito `UnnecessaryStubbingException`），依規範移除該 stub 後，`StompAuthChannelInterceptorTest` **6 tests 0 fail**。

### 文件

- **`SPRINT_75_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有測試覆蓋現況、US-001/002 完整 AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-035`（已完成，直接記入「已完成延後項目」）。
- **`RELEASE_NOTES_v2028.09.09-01.md`**（新檔）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 加入 `ConversationRepository` 依賴（未加邏輯）→ 執行紅燈測試確認失敗 → 加入 SUBSCRIBE 授權邏輯 → 執行轉綠測試確認通過 → 移除變多餘的 stub → 重跑確認 |
| `StompAuthChannelInterceptorTest` 修復前（紅燈階段） | 🔴 6 tests，**3 Failures + 2 Errors**（TC-STOMP-SUB-003/004/005 為 AssertionError「expected null but was 非 null」，正確反映「修復前完全未攔截」的漏洞本質；TC-STOMP-SUB-001/002 為 `UnnecessaryStubbingException`，因程式碼當時不查 repository），其餘 1 test（TC-STOMP-SUB-006，無關目的地）正常通過 |
| `StompAuthChannelInterceptorTest` 修復後 | ✅ 6 tests，0 fail |
| 後端單元回歸（`mvn verify -Pintegration-test` 單元階段） | ✅ **741 tests，0 fail** |
| 全量回歸（`mvn verify -Pintegration-test`） | ✅ **BUILD SUCCESS**：單元 741 + 整合（failsafe）342 = **1083 tests，0 fail**（含既有 `M10ChatIntegrationTest`/`M10ChatTenantIsolationIntegrationTest`，本次未遇到已知的 STOMP flaky 問題） |
| `make validate-schema` | ✅ 無漂移（本 Sprint 無 entity/migration 變更），EXIT_CODE=0 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint 修改生產程式碼（`StompAuthChannelInterceptor.java`），故執行全量 `mvn verify -Pintegration-test`，非僅 `mvn test` |

---

## 4. 誠實揭露（Rule 12）

1. **`ChatService` 本身未新增任何測試**：與 Sprint 66-74 每個 Sprint 皆為目標 Service 新增數十個單元測試不同，本 Sprint 探查後確認 `ChatService` 6 個方法的擁有權檢查與測試覆蓋皆已完整，判斷「重複補測試」不符合 Rule 2（簡潔優先），故本 Sprint 未新增 `ChatServiceTest.java`，僅在 STOMP 層新增測試。此決策已於探查階段完整記錄依據（見 `SPRINT_75_PLAN.md` §1.1），非略過或遺漏。
2. **`DEF-035` 的修復位置不在 `ChatService` 本身**：雖然本 Sprint 主題是 `ChatService`，但實際的授權缺口位於 `api/filter/StompAuthChannelInterceptor.java`（訊息代理層基礎設施），不是 `ChatService` 或 `ChatController`。這是本多 Sprint 測試強化計劃中首次「目標 Service 本身無缺口，缺口出現在其依賴的基礎設施」的案例，依使用者指示「特別注意 WebSocket/STOMP」才得以發現，若僅侷限於 `ChatService`/`ChatController` 範圍將會遺漏此缺口。
3. **修復範圍精準，未擴大處理相鄰問題**：探查過程中另觀察到 `StompAuthChannelInterceptor` 對 CONNECT 時 JWT 缺失/無效仍放行連線（不拒絕）的設計，這雖然是造成「匿名連線可訂閱」的部分原因，但本次 SUBSCRIBE 層授權檢查已能完整阻斷此風險（未認證的訂閱者 `userId` 為 `null`，必然無法匹配任何對話的 initiator/recipient），故未變更 CONNECT 邏輯本身，避免無依據擴大修改範圍（Rule 3）。
4. **`createConversation` 的 `recipientId` 未驗證收件人是否存在/跨租戶**：探查過程中觀察到 `createConversation` 未驗證 `request.getRecipientId()` 對應的使用者是否存在，也未限制發起對話的雙方是否需同租戶——但這與聊天本質上是跨租戶（買方 ↔ 賣方）的既有設計一致（`M10ChatTenantIsolationIntegrationTest` 已明確記載此為既定設計），且不涉及資料外洩/竄改風險，非本 Sprint 鎖定的 IDOR 類缺口模式，故未列為 `DEF` 追蹤，僅在此誠實記錄觀察。

---

## 5. Demo 重點

- **首次驗證「Service 本身已無缺口」的判斷紀律**：延續 Sprint 68/70/72/73/74 建立的主動審視方法，本 Sprint 得出與先前 5 個 Sprint 不同的結論——`ChatService` 本身經逐一審視後確認無缺口，證明此審視方法不是「為了找漏洞而找漏洞」，而是每次都基於實際程式碼行為判斷。
- **審視範圍延伸至依賴的基礎設施而非侷限於單一 Service 類別**：依使用者指示特別留意 WebSocket/STOMP，主動延伸檢查 `ChatService` 所依賴的 `WebSocketConfig`/`StompAuthChannelInterceptor`，發現 REST 層與訊息代理層存在授權落差——REST 層的 participant-scoping 保護未同步套用到即時訊息廣播的訂閱端。
- **紅燈測試的失敗型態正確對應漏洞本質**：3 個攻擊案例（非參與者/匿名/對話不存在）的紅燈皆為「回傳值不為預期的 null」（訊息被放行），精準對應「修復前完全沒有攔截」的事實；2 個正向案例因程式碼當時根本不查 repository 而觸發 `UnnecessaryStubbingException`，修復後又因短路邏輯而需移除一個變多餘的 stub，皆與規劃時預期的 Mockito 行為一致。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
