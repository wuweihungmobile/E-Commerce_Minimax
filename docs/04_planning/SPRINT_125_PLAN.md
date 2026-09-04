# Sprint 125 Plan — DEF-056：ChatService 未讀計數併發修復

**Sprint**: Sprint 125
**日期**: 2026-09-04
**主題**: 使用者授權依序處理三項待辦中的第三項，2 SP，無需業務決策。

---

## 1. 缺陷

`ChatService.sendMessage`（`ChatService.java:154-165`）對 `Conversation` 是「讀出實體 →
未讀計數 +1 → `save()`」的兩段式操作：

```java
conversation.setLastMessageId(message.getId());
conversation.setLastMessagePreview(...);
conversation.setLastMessageAt(message.getCreatedAt());
if (conversation.getInitiatorId().equals(userId)) {
    conversation.setRecipientUnreadCount(conversation.getRecipientUnreadCount() + 1);
} else {
    conversation.setInitiatorUnreadCount(conversation.getInitiatorUnreadCount() + 1);
}
conversationRepository.save(conversation);
```

同一對話的併發訊息會互相覆蓋未讀計數（lost update），收訊方的未讀徽章因此偏低。
**自癒型缺陷**：使用者開啟對話後計數歸零，錯誤不累積，故長期未被察覺，優先級記為低。

## 2. 修法：拆成「相對遞增 + 絕對賦值」的單一原子敘述

同一次 `save()` 混合了兩種不同語意的欄位，不能整段照搬 `PromoCodeRepository` 那種純相對
遞增的原子 UPDATE 寫法：

- `last_message_id`／`last_message_preview`／`last_message_at`：**後寫入者覆蓋在語意上
  是正確的**（最後一則訊息本來就該是最後那則），適合絕對賦值。
- `initiator_unread_count`／`recipient_unread_count`：必須**相對遞增**，否則會遺漏併發
  訊息（本缺陷的成因）。

新增 `ConversationRepository.recordNewMessage`（原生 UPDATE，`@Modifying`），單一敘述同時
做到兩種語意：

```sql
UPDATE conversations
   SET last_message_id = :messageId,
       last_message_preview = :preview,
       last_message_at = :lastMessageAt,
       initiator_unread_count = COALESCE(initiator_unread_count, 0) + :initiatorDelta,
       recipient_unread_count = COALESCE(recipient_unread_count, 0) + :recipientDelta,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = :conversationId
```

`initiatorDelta`／`recipientDelta` 恰有一個為 1（視發訊者是 initiator 還是 recipient），
由 `ChatService.sendMessage` 呼叫端決定，取代原本的 `if/else` + 讀改寫。

## 3. 範圍外

- **`markAsRead` 的計數歸零**（`ChatService.java:244-249`）維持原樣：該方法是把計數
  **絕對設為 0**，不依賴讀到的值做運算，不是本缺陷描述的「相對遞增互相覆蓋」問題。
  `markAsRead` 與 `sendMessage` 之間仍存在極窄的交錯窗口（收訊方開啟對話的同時剛好有
  新訊息進來），但這不是 DEF-056 記錄的缺陷（其明確scope 是「同一對話的**併發訊息**」，
  即多筆 `sendMessage` 互相覆蓋），且後續訊息會讓計數再次正確累加（自癒），故不擴大
  本輪範圍一併處理。
- `PromoCodeRepository` 式「純相對遞增」的寫法不能整段照搬過來，已在 §2 說明並以混合
  敘述解決，非遺漏。

## 4. 測試

**負向測試證實有效**：暫時把 `sendMessage` 換回修復前的讀-改-寫寫法，20 條執行緒併發
送出訊息，`M10ChatUnreadCountConcurrencyIntegrationTest` 如預期失敗（**期望 20、實得
3**——17 次遞增被互相覆蓋）；換回原子 UPDATE 後測試通過（精準等於 20）。

新增：
- `M10ChatUnreadCountConcurrencyIntegrationTest`（真實 PostgreSQL，20 執行緒併發呼叫
  `chatService.sendMessage`，各自獨立交易——`sendMessage` 本身已是 `@Transactional`，
  不需像 `M11PromoConcurrencyIntegrationTest` 額外包 `TransactionTemplate`）：驗證
  `recipientUnreadCount` 精準等於併發訊息數。
- `ChatServiceStompBroadcastTest` 新增 2 個 mock 測試：initiator 發訊息 →
  `recordNewMessage` 遞增方向為 `(initiatorDelta=0, recipientDelta=1)`；recipient
  發訊息則相反。

既有測試調整：`ChatServiceStompBroadcastTest` 的 `setUp()` 移除已不再成立的
`conversationRepository.save(...)` stub（`sendMessage` 改呼叫 `recordNewMessage`，
不再呼叫 `save(Conversation)`），並補上 `@MockitoSettings(strictness = LENIENT)`
（對齊本專案同類測試檔的既有慣例，避免嚴格 stub 模式因測試方法內覆寫 `setUp()` 預設值
而誤判為多餘 stubbing）。

## 5. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `mvn -o compile` | BUILD SUCCESS |
| ② | `mvn -o checkstyle:check` | 0 violations |
| ③ | 紅燈：暫時還原讀後寫寫法，跑 `M10ChatUnreadCountConcurrencyIntegrationTest` | 🔴 如期失敗（期望 20、實得 3） |
| ④ | 綠燈：還原修復後重跑 | ✅ 通過（精準等於 20） |
| ⑤ | `mvn -o test`（`ChatServiceStompBroadcastTest`／`ChatServiceTenantResolutionTest`／新增整合測試） | 0 Failures / 0 Errors |
| ⑥ | `mvn -o verify`（全量含整合測試＋PMD＋checkstyle-test，**與 Sprint 124 合併跑一次**，見下方說明） | 見回填 |
| ⑦ | `make validate-schema` | ✅ 無漂移（本輪無 migration） |

ℹ️ **驗證效率考量**：本輪與 Sprint 124（DEF-047）背靠背完成，兩輪的 `backend/` 變更在
同一次 `mvn -o verify` 全量回歸中一併驗證（跑在同一乾淨 test DB、同一次 JVM 生命週期），
避免為兩個小改動各自付一次約 10 分鐘的整合測試冷啟動成本。**兩個 Sprint 仍各自獨立
commit**，此處僅共用驗證執行、不共用程式碼審查範圍——SPRINT_124_PLAN.md 與本文件各自
完整記錄各自的變更與測試新增項目。
