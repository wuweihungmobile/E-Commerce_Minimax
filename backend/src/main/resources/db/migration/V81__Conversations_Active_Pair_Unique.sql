-- V81__Conversations_Active_Pair_Unique.sql
-- DEF-118（ChatService.createConversation，Sprint 143）
--
-- 問題：createConversation 的「檢查是否已有對話」與「建立新對話」之間沒有原子保護
-- （check-then-act TOCTOU）。conversations 表對 (initiator_id, recipient_id) 沒有任何
-- 唯一約束，兩個併發「發起聊天」請求可能都通過檢查各自成功 INSERT，同一對
-- (initiator, recipient) 產生兩筆同時 active 的 Conversation。
--
-- 修法：僅對 is_active = true 的列建立部分唯一索引（同一對 initiator/recipient 可以有
-- 多筆歷史上不再 active 的舊對話，但同時最多只能有一筆 active），與既有查詢
-- findByInitiatorIdAndRecipientIdAndIsActiveTrue 的語意完全對應。

CREATE UNIQUE INDEX idx_conversations_active_pair_unique
    ON conversations (initiator_id, recipient_id)
    WHERE is_active = true;
