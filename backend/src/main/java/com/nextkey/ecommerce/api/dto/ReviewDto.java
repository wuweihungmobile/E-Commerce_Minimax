package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 評價 DTO
 */
public class ReviewDto {

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

        @Size(max = 100, message = "Title must be at most 100 characters")
        private String title;

        @NotBlank(message = "Content is required")
        @Size(max = 2000, message = "Content must be at most 2000 characters")
        private String content;

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

        @Size(max = 100, message = "Title must be at most 100 characters")
        private String title;

        @Size(max = 2000, message = "Content must be at most 2000 characters")
        private String content;

        private List<String> images;
    }

    // ========== Seller Reply Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerReplyRequest {
        @NotBlank(message = "Reply content is required")
        @Size(max = 1000, message = "Reply must be at most 1000 characters")
        private String reply;
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
        private String sellerReply;
        private Instant sellerRepliedAt;
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
}
