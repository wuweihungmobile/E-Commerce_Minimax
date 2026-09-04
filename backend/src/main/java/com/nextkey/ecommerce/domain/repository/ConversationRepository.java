package com.nextkey.ecommerce.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.chat.Conversation;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c WHERE (c.initiatorId = :userId OR c.recipientId = :userId) " +
            "AND c.isActive = true ORDER BY c.lastMessageAt DESC")
    Page<Conversation> findByUserIdOrderByLastMessageAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM Conversation c WHERE (c.initiatorId = :userId OR c.recipientId = :userId) " +
            "AND c.isActive = true AND c.listingId = :listingId ORDER BY c.lastMessageAt DESC")
    List<Conversation> findByUserIdAndListingId(@Param("userId") UUID userId, @Param("listingId") UUID listingId);

    Optional<Conversation> findByInitiatorIdAndRecipientIdAndIsActiveTrue(UUID initiatorId, UUID recipientId);

    @Query("SELECT c FROM Conversation c WHERE c.id = :conversationId AND (c.initiatorId = :userId OR c.recipientId = :userId)")
    Optional<Conversation> findByIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.recipientId = :userId AND c.isActive = true AND c.recipientUnreadCount > 0")
    int countUnreadConversations(@Param("userId") UUID userId);

    /**
     * 新訊息送達時原子更新對話狀態（Sprint 125，DEF-056）。
     *
     * <p>取代原本「讀出 {@code Conversation} → set 未讀計數 +1 → save()」的兩段式操作——
     * 同一對話的併發訊息會互相覆蓋（lost update），收訊方的未讀徽章因此偏低。與
     * {@code PromoCodeRepository.incrementUsageCountIfWithinLimit} 同一手法，差別在本敘述
     * 混合了兩種語意：{@code last_message_id}／{@code last_message_preview}／
     * {@code last_message_at} 是「後寫入者覆蓋」在語意上本就正確（最後一則訊息本來就該是
     * 最後那則），故用絕對賦值；未讀計數則必須相對遞增，才不會遺漏併發訊息。
     *
     * <p>{@code initiatorDelta}／{@code recipientDelta} 恰有一個為 1、另一個為 0
     * （視發訊者是 initiator 還是 recipient 而定），由呼叫端決定。
     *
     * @return 受影響筆數；0 表示該對話 id 不存在
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE conversations
               SET last_message_id = :messageId,
                   last_message_preview = :preview,
                   last_message_at = :lastMessageAt,
                   initiator_unread_count = COALESCE(initiator_unread_count, 0) + :initiatorDelta,
                   recipient_unread_count = COALESCE(recipient_unread_count, 0) + :recipientDelta,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :conversationId
            """, nativeQuery = true)
    int recordNewMessage(@Param("conversationId") UUID conversationId,
            @Param("messageId") UUID messageId,
            @Param("preview") String preview,
            @Param("lastMessageAt") Instant lastMessageAt,
            @Param("initiatorDelta") int initiatorDelta,
            @Param("recipientDelta") int recipientDelta);
}
