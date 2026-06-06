package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 評價回覆 DTO
 *
 * @author Sprint 16 (US-001)
 */
public class ReviewReplyDto {

    // ========== Create Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReplyRequest {
        @NotBlank(message = "Reply content is required")
        @Size(max = 1000, message = "Reply must be at most 1000 characters")
        private String content;
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyResponse {
        private UUID replyId;
        private UUID reviewId;
        private UUID replierId;
        private String content;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyListResponse {
        private List<ReplyResponse> replies;
        private int totalCount;
    }
}
