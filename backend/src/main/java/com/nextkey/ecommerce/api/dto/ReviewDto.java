package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * 評價 DTO
 */
public class ReviewDto {

    // Validation constraints
    private static final int CONTENT_MAX_LENGTH = 2000;
    private static final int TITLE_MAX_LENGTH = 100;

    // 🔴 Sprint 16 US-005: 評價圖片上限 9 張
    public static final int MAX_REVIEW_IMAGES = 9;

    // ========== Create Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "Listing ID is required")
        private UUID listingId;

        private UUID orderId;
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

        // 🔴 Sprint 16 US-005: 評價圖片上限 9 張
        @Size(max = MAX_REVIEW_IMAGES, message = "評價圖片最多 9 張")
        private List<String> images;

        private Boolean isAnonymous;
    }

    // ========== Update Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        private Integer rating;

        @Size(max = TITLE_MAX_LENGTH, message = "Title must be at most 100 characters")
        private String title;

        @Size(max = CONTENT_MAX_LENGTH, message = "Content must be at most 2000 characters")
        private String content;

        // 🔴 Sprint 16 US-005: 評價圖片上限 9 張
        @Size(max = MAX_REVIEW_IMAGES, message = "評價圖片最多 9 張")
        private List<String> images;
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewResponse {
        private UUID reviewId;
        private UUID listingId;
        private String listingTitle;
        private UUID userId;
        private String userFullName;
        private String userAvatarUrl;
        private UUID orderId;
        private UUID bookingId;
        private String reviewType;
        private Integer rating;
        private String title;
        private String content;
        private List<String> images;
        private Integer helpfulCount;
        private Boolean isAnonymous;
        // sellerReply 已遷移到 ReviewReply 表（Sprint 16 US-001）
        // private String sellerReply;
        // private Instant sellerRepliedAt;
        private Boolean isHandled;
        private Instant handledAt;
        private UUID handledBy;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== Review List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewListResponse {
        private List<ReviewResponse> reviews;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private Double averageRating;
        private Integer totalReviews;
    }

    // ========== Rating Stats ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingStats {
        private UUID listingId;
        private Double averageRating;
        private Integer totalReviews;
        private Integer rating1Count;
        private Integer rating2Count;
        private Integer rating3Count;
        private Integer rating4Count;
        private Integer rating5Count;
        private Map<Integer, Integer> distribution; // rating -> count
    }

    // ========== Helpful Vote ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HelpfulVoteRequest {
        @NotNull(message = "Review ID is required")
        private UUID reviewId;
    }

    // ========== Review Summary ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewSummary {
        private UUID listingId;
        private String listingTitle;
        private Double averageRating;
        private Integer totalReviews;
        private Integer fiveStarReviews;
        private Integer fourStarReviews;
        private Integer threeStarReviews;
        private Integer twoStarReviews;
        private Integer oneStarReviews;
        private Double fiveStarPercent;
        private Double fourStarPercent;
        private Double threeStarPercent;
        private Double twoStarPercent;
        private Double oneStarPercent;
    }

    // ========== Image Management (Sprint 16 US-005, US-006) ==========

    /**
     * 新增單張圖片請求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddImageRequest {
        @NotBlank(message = "Image URL is required")
        @Size(max = 500, message = "Image URL must be at most 500 characters")
        private String imageUrl;
    }

    /**
     * 重新排序圖片請求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderImagesRequest {
        @NotNull(message = "Image URLs are required")
        @Size(min = 1, max = MAX_REVIEW_IMAGES, message = "評價圖片最多 9 張")
        private List<String> imageUrls;
    }
}
