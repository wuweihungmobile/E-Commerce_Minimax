package com.nextkey.ecommerce.core.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.model.chat.Message;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.MessageRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.util.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天服務
 * Phase 2: 發送訊息後透過 STOMP 廣播到 /queue/conversations/{id}/messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String STOMP_CONVERSATION_DEST = "/queue/conversations/%s/messages";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Message preview and page size limits
    private static final int MESSAGE_PREVIEW_MAX_LENGTH = 50;
    private static final int DEFAULT_PAGE_SIZE = 50;
    /** System Tenant ID（V9）；無 listing/order 關聯的 DIRECT 對話退回此租戶。 */
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 依關聯實體推導對話所屬租戶（Sprint 25 US-003 / AI-802）。
     * 規則：有 listing → 取 listing 租戶；否則有 order → 取 order 租戶；
     * 否則（純 DIRECT 或關聯實體不存在）→ 退回 System Tenant。與 V56 回填規則一致。
     */
    private UUID resolveTenantId(final UUID listingId, final UUID orderId) {
        if (listingId != null) {
            final UUID tenantId = listingRepository.findById(listingId)
                    .map(Listing::getTenantId).orElse(null);
            if (tenantId != null) {
                return tenantId;
            }
        }
        if (orderId != null) {
            final UUID tenantId = orderRepository.findById(orderId)
                    .map(Order::getTenantId).orElse(null);
            if (tenantId != null) {
                return tenantId;
            }
        }
        return SYSTEM_TENANT_ID;
    }

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
                .tenantId(resolveTenantId(request.getListingId(), request.getOrderId()))
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

        // 併發防護（DEF-118）：上面的檢查與這裡的 save() 之間是 TOCTOU，兩個併發「發起聊天」
        // 請求都可能通過檢查各自建立對話。V81 已對 (initiator_id, recipient_id) WHERE
        // is_active=true 建立部分唯一索引，這裡改用 saveAndFlush 捕捉違反約束；搶輸時比照
        // 上面「已有對話則直接回傳」的既有語意，重新查詢後回傳該筆（而非送出初始訊息或拋錯），
        // 不建立第二筆重複的 active 對話。
        try {
            conversation = conversationRepository.saveAndFlush(conversation);
        } catch (DataIntegrityViolationException e) {
            Conversation raceWinner = conversationRepository
                    .findByInitiatorIdAndRecipientIdAndIsActiveTrue(userId, request.getRecipientId())
                    .orElseThrow(() -> e);
            return toConversationResponse(raceWinner);
        }

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
            int previewLength = Math.min(MESSAGE_PREVIEW_MAX_LENGTH, request.getInitialMessage().length());
            conversation.setLastMessagePreview(request.getInitialMessage().substring(0, previewLength));
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

        // Sprint 125（DEF-056）：對話狀態改為單一原子 UPDATE——last_message_* 三欄是「後寫入者
        // 覆蓋」語意正確的絕對賦值，未讀計數則是「相對遞增」，兩者混在同一段 JPA 實體
        // 讀-改-寫（原本的寫法）會讓同一對話的併發訊息互相覆蓋未讀計數。發訊者是 initiator
        // 則遞增 recipient 未讀，反之遞增 initiator 未讀，兩者恰有一個為 1。
        String preview = request.getContent()
                .substring(0, Math.min(MESSAGE_PREVIEW_MAX_LENGTH, request.getContent().length()));
        boolean senderIsInitiator = conversation.getInitiatorId().equals(userId);
        conversationRepository.recordNewMessage(
                conversation.getId(), message.getId(), preview, message.getCreatedAt(),
                senderIsInitiator ? 0 : 1,
                senderIsInitiator ? 1 : 0);

        log.info("Message sent: conversationId={}, messageId={}, sender={}",
                request.getConversationId(), message.getId(), userId);

        ChatDto.MessageResponse messageResponse = toMessageResponse(message);

        // STOMP 廣播：通知對話中的訂閱者有新訊息
        String destination = String.format(STOMP_CONVERSATION_DEST, request.getConversationId());
        messagingTemplate.convertAndSend(destination, messageResponse);
        log.debug("[STOMP] Broadcast message to {}", destination);

        return messageResponse;
    }

    /**
     * 取得用戶對話列表
     */
    @Transactional(readOnly = true)
    public ChatDto.ConversationListResponse getUserConversations(UUID userId, int page, int size) {
        PageRequest pageRequest = PageableUtils.of(page, size, DEFAULT_PAGE_SIZE);

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

        PageRequest pageRequest = PageableUtils.of(page, size, DEFAULT_PAGE_SIZE);

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
    public void markAsRead(final UUID userId, final UUID conversationId) {
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
    public void deleteConversation(final UUID userId, final UUID conversationId) {
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

        // DEF-012: conversationId 唯讀鏡像欄位（@Column insertable/updatable=false）在「新建 + save」的
        // in-memory 實例上不會被填值（僅從 DB 載入時才有值），導致 STOMP 廣播 payload conversationId=null。
        // 改由關聯取得（LAZY proxy 取 id 不觸發查詢），fallback 唯讀欄位以相容「從 DB 載入」的路徑。
        UUID conversationId = message.getConversation() != null
                ? message.getConversation().getId()
                : message.getConversationId();

        return ChatDto.MessageResponse.builder()
                .messageId(message.getId())
                .conversationId(conversationId)
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
