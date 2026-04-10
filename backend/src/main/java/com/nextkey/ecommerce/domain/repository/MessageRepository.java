package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.chat.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversationId = :conversationId AND m.isRead = false AND m.senderId != :userId")
    List<Message> findUnreadByConversationIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true, m.readAt = :readAt, m.readBy = :userId WHERE m.conversationId = :conversationId AND m.senderId != :userId AND m.isRead = false")
    int markAsRead(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId, @Param("readAt") Instant readAt);

    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversationId = :conversationId AND m.senderId != :userId AND m.isRead = false")
    int countUnreadMessages(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
