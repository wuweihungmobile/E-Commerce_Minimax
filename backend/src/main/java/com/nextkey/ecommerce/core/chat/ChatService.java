package com.nextkey.ecommerce.core.chat;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.model.chat.Message;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.MessageRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天服務 (Mock Implementation)
 * Phase 2 預留 WebSocket 整合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    /**
     * 建立對話
     */
    @Transactional
    public ChatDto.ConversationResponse createConversation(ChatDto.CreateConversationRequest request, UUID userId) {
        // 檢查是否已有對話
        Conversation existing = conversationRepository
                .findByInitiatorIdAndRecipientIdAndIsActiveTrue(userId, request.getRecipientId())
                .orElse(null);

        if (existing != null) {
            return toConversationResponse(existing);
        }

        Conversation conversation = Conversation.builder()
                .listingId(request.getListingId())
                .orderId(request.getOrderId())
                .conversationType(request.getConversationType() != null
                        ? Conversation.ConversationType.valueOf(request.getConversationType().name())
                        : Conversation.ConversationType.DIRECT)
                .initiatorId(userId)
                .recipientId(request.getRecipientId())
                .initiatorUnreadCount(0)
                .recipientUnreadCount(0)
                .isActive(true)
                .build();

        conversation = conversationRepository.save(conversation);

        // 發送初始消息
        if (request.getInitialMessage() != null && !request.getInitialMessage().isBlank()) {
            Message message = Message.builder()
                    .conversation(conversation)
                    .senderId(userId)
                    .messageType(Message.MessageType.TEXT)
                    .content(request.getInitialMessage())
                    .isRead(false)
                    .build();
            message = messageRepository.save(message);

            conversation.setLastMessageId(message.getId());
            conversation.setLastMessagePreview(request.getInitialMessage().substring(0, Math.min(50, request.getInitialMessage().length())));
            conversation.setLastMessageAt(message.getCreatedAt());
            conversation = conversationRepository.save(conversation);
        }

        log.info("Conversation created: id={}, initiator={}, recipient={}",
                conversation.getId(), userId, request.getRecipientId());

        return toConversationResponse(conversation);
    }

    /**
     * 發送消息
     */
    @Transactional
    public ChatDto.MessageResponse sendMessage(UUID userId, ChatDto.SendMessageRequest request) {
        Conversation conversation = conversationRepository.findByIdAndUserId(request.getConversationId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_9005, "Conversation not found"));

        Message message = Message.builder()
                .conversation(conversation)
                .senderId(userId)
                .messageType(request.getMessageType() != null
                        ? Message.MessageType.valueOf(request.getMessageType().name())
                        : Message.MessageType.TEXT)
                .content(request.getContent())
                .attachments(request.getAttachments())
                .isRead(false)
                .build();

        message = messageRepository.save(message);

        // 更新對話狀態
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessagePreview(request.getContent().substring(0, Math.min(50, request.getContent().length())));
        conversation.setLastMessageAt(message.getCreatedAt());

        // 增加未讀計數
        if (conversation.getInitiatorId().equals(userId)) {
            conversation.setRecipientUnreadCount(conversation.getRecipientUnreadCount() + 1);
        } else {
            conversation.setInitiatorUnreadCount(conversation.getInitiatorUnreadCount() + 1);
        }

        conversationRepository.save(conversation);

        log.info("Message sent: conversationId={}, messageId={}, sender={}",
                request.getConversationId(), message.getId(), userId);

        return toMessageResponse(message);
    }

    /**
     * 取得用戶對話列表
     */
    @Transactional(readOnly = true)
    public ChatDto.ConversationListResponse getUserConversations(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50));

        Page<Conversation> conversations = conversationRepository
                .findByUserIdOrderByLastMessageAtDesc(userId, pageRequest);

        int unreadCount = conversationRepository.countUnreadConversations(userId);

        List<ChatDto.ConversationResponse> responses = conversations.getContent().stream()
                .map(this::toConversationResponse)
                .collect(Collectors.toList());

        return ChatDto.ConversationListResponse.builder()
                .conversations(responses)
                .page(page)
                .size(size)
                .totalElements(conversations.getTotalElements())
                .totalPages(conversations.getTotalPages())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 取得消息歷史
     */
    @Transactional(readOnly = true)
    public ChatDto.MessageListResponse getMessages(UUID userId, UUID conversationId, int page, int size) {
        // 驗證權限
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_9005, "Conversation not found"));

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50));

        Page<Message> messages = messageRepository
                .findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId, pageRequest);

        List<ChatDto.MessageResponse> responses = messages.getContent().stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        return ChatDto.MessageListResponse.builder()
                .messages(responses)
                .page(page)
                .size(size)
                .totalElements(messages.getTotalElements())
                .totalPages(messages.getTotalPages())
                .build();
    }

    /**
     * 標記已讀
     */
    @Transactional
    public void markAsRead(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_9005, "Conversation not found"));

        int updated = messageRepository.markAsRead(conversationId, userId, Instant.now());

        // 重置未讀計數
        if (conversation.getInitiatorId().equals(userId)) {
            conversation.setInitiatorUnreadCount(0);
        } else {
            conversation.setRecipientUnreadCount(0);
        }
        conversationRepository.save(conversation);

        log.info("Messages marked as read: conversationId={}, count={}", conversationId, updated);
    }

    /**
     * 刪除對話
     */
    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_9005, "Conversation not found"));

        conversation.setIsActive(false);
        conversationRepository.save(conversation);

        log.info("Conversation deleted: id={}, userId={}", conversationId, userId);
    }

    // ========== Helper Methods ==========

    private ChatDto.ConversationResponse toConversationResponse(Conversation conversation) {
        Message lastMessage = null;
        if (conversation.getLastMessageId() != null) {
            lastMessage = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId()).orElse(null);
        }

        User initiator = userRepository.findById(conversation.getInitiatorId()).orElse(null);
        User recipient = userRepository.findById(conversation.getRecipientId()).orElse(null);

        return ChatDto.ConversationResponse.builder()
                .conversationId(conversation.getId())
                .listingId(conversation.getListingId())
                .orderId(conversation.getOrderId())
                .conversationType(conversation.getConversationType().name())
                .initiatorId(conversation.getInitiatorId())
                .initiatorName(initiator != null ? initiator.getFullName() : null)
                .recipientId(conversation.getRecipientId())
                .recipientName(recipient != null ? recipient.getFullName() : null)
                .lastMessage(lastMessage != null ? toMessageResponse(lastMessage) : null)
                .unreadCount(null) // Will be filled by caller
                .isActive(conversation.getIsActive())
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private ChatDto.MessageResponse toMessageResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);

        return ChatDto.MessageResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(sender != null ? sender.getFullName() : null)
                .messageType(message.getMessageType().name())
                .content(message.getContent())
                .attachments(message.getAttachments())
                .isRead(message.getIsRead())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
