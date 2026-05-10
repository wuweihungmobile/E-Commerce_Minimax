package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

/**
 * 聊天 DTO
 */
public class ChatDto {

    // ========== Create Conversation ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateConversationRequest {
        @NotNull(message = "Recipient ID is required")
        private UUID recipientId;

        private UUID listingId;

        private UUID orderId;

        private ConversationType conversationType;

        private String initialMessage;
    }

    public enum ConversationType {
        DIRECT,
        LISTING_INQUIRY,
        ORDER_INQUIRY,
        BOOKING_INQUIRY
    }

    // ========== Send Message ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendMessageRequest {
        @NotNull(message = "Conversation ID is required")
        private UUID conversationId;

        @NotBlank(message = "Content is required")
        private String content;

        private MessageType messageType;

        private List<String> attachments;
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM
    }

    // ========== Conversation Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationResponse {
        private UUID conversationId;
        private UUID listingId;
        private UUID orderId;
        private String conversationType;
        private UUID initiatorId;
        private String initiatorName;
        private UUID recipientId;
        private String recipientName;
        private MessageResponse lastMessage;
        private Integer unreadCount;
        private Boolean isActive;
        private Instant lastMessageAt;
        private Instant createdAt;
    }

    // ========== Message Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private UUID messageId;
        private UUID conversationId;
        private UUID senderId;
        private String senderName;
        private String messageType;
        private String content;
        private List<String> attachments;
        private Boolean isRead;
        private Instant readAt;
        private Instant createdAt;
    }

    // ========== Conversation List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationListResponse {
        private List<ConversationResponse> conversations;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private int unreadCount;
    }

    // ========== Message List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageListResponse {
        private List<MessageResponse> messages;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    // ========== Mark Read ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarkReadRequest {
        @NotNull(message = "Conversation ID is required")
        private UUID conversationId;
    }

    // ========== WebSocket Events ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebSocketMessage {
        private String event; // MESSAGE_SENT, MESSAGE_READ, TYPING, ONLINE_STATUS
        private UUID conversationId;
        private MessageResponse payload;
        private Map<String, Object> data;
    }

    // ========== Typing Indicator ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypingIndicator {
        private UUID conversationId;
        private UUID userId;
        private String userName;
        private Boolean isTyping;
    }
}
