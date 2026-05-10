package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
