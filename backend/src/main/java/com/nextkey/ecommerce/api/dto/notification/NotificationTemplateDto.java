package com.nextkey.ecommerce.api.dto.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * 通知模板 DTO
 */
public class NotificationTemplateDto {

    // ========== Create Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "Template code is required")
        @Size(max = 100, message = "Template code must not exceed 100 characters")
        private String templateCode;

        @NotNull(message = "Notification type is required")
        private String notificationType;

        @NotNull(message = "Channel is required")
        private String channel;

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must not exceed 200 characters")
        private String name;

        @Size(max = 500, message = "Subject must not exceed 500 characters")
        private String subject;

        @NotBlank(message = "Content template is required")
        private String contentTemplate;

        private List<String> variables;

        private Boolean isActive;

        private Integer priority;
    }

    // ========== Update Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {

        @Size(max = 100, message = "Template code must not exceed 100 characters")
        private String templateCode;

        private String notificationType;

        private String channel;

        @Size(max = 200, message = "Name must not exceed 200 characters")
        private String name;

        @Size(max = 500, message = "Subject must not exceed 500 characters")
        private String subject;

        private String contentTemplate;

        private List<String> variables;

        private Boolean isActive;

        private Integer priority;
    }

    // ========== Search Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String notificationType;
        private String channel;
        private Boolean isActive;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private UUID tenantId;
        private String templateCode;
        private String notificationType;
        private String channel;
        private String name;
        private String subject;
        private String contentTemplate;
        private List<String> variables;
        private Boolean isActive;
        private Integer priority;
        private UUID createdBy;
        private UUID updatedBy;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private List<Response> templates;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    // ========== Render Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RenderRequest {
        private String templateCode;
        private java.util.Map<String, String> variables;
    }

    // ========== Render Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RenderResponse {
        private String subject;
        private String content;
        private java.util.Map<String, String> variables;
    }
}