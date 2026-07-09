package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客服工單相關 DTO 集中檔（PRD §6.10 M18 Phase 2-B，Sprint 91），供買家/店家/平台三層角色共用
 */
public class SupportTicketDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketResponse {
        private UUID id;
        private UUID tenantId;
        private String ticketNumber;
        private String category;
        private String subject;
        private String description;
        private String status;
        private String priority;
        private UUID customerId;
        private UUID assignedTo;
        private UUID orderId;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant resolvedAt;
        /** 僅工單詳情端點（GET .../tickets/:id）回傳，列表端點為 null（PRD 未列出獨立的訊息列表端點）。 */
        private List<MessageResponse> messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketListResponse {
        private List<TicketResponse> tickets;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private UUID id;
        private UUID ticketId;
        private UUID senderId;
        private String senderType;
        private String message;
        private List<String> attachments;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTicketRequest {
        @NotBlank(message = "Category is required")
        private String category;

        @NotBlank(message = "Subject is required")
        @Size(max = 200, message = "Subject must not exceed 200 characters")
        private String subject;

        @NotBlank(message = "Description is required")
        private String description;

        private UUID orderId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateMessageRequest {
        @NotBlank(message = "Message is required")
        private String message;

        private List<String> attachments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateTicketStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;

        private String priority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignTicketRequest {
        @NotNull(message = "assignedTo is required")
        private UUID assignedTo;
    }
}
