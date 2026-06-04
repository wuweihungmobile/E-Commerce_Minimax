package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * 預訂評價 DTO (BookingReview)
 */
public class BookingReviewDto {

    // Validation constraints
    private static final int CONTENT_MAX_LENGTH = 2000;
    private static final int TITLE_MAX_LENGTH = 100;

    // ========== Create Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "Booking ID is required")
        private UUID bookingId;

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        private Integer rating;

        @Size(max = TITLE_MAX_LENGTH, message = "Title must be at most 100 characters")
        private String title;

        @NotBlank(message = "Content is required")
        @Size(max = CONTENT_MAX_LENGTH, message = "Content must be at most 2000 characters")
        private String content;

        private List<String> images;

        private Boolean isAnonymous;
    }

    // ========== Host Reply Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostReplyRequest {
        @NotBlank(message = "Reply content is required")
        @Size(max = 1000, message = "Reply must be at most 1000 characters")
        private String reply;
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingReviewResponse {
        private UUID reviewId;
        private UUID bookingId;
        private UUID userId;
        private String userFullName;
        private String userAvatarUrl;
        private Integer rating;
        private String title;
        private String content;
        private List<String> images;
        private Boolean isAnonymous;
        private String hostReply;
        private Instant hostRepliedAt;
        private Instant createdAt;
        private Instant updatedAt;
    }
}