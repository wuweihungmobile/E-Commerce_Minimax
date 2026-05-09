package com.nextkey.ecommerce.api.dto;

import com.nextkey.ecommerce.domain.model.cms.media.MediaAsset;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.cms.post.PostEmbed;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M15 CMS DTOs
 */
public class M15Dto {

    // ========== Post DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePostRequest {
        private String title;
        private String content;
        private UUID categoryId;
        private List<String> tags;
        private String featuredImageUrl;
        private Boolean autoPublish;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePostRequest {
        private String title;
        private String content;
        private UUID categoryId;
        private List<String> tags;
        private String featuredImageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostResponse {
        private UUID id;
        private UUID tenantId;
        private String tenantName;
        private UUID authorId;
        private String authorName;
        private String title;
        private String slug;
        private String content;
        private String excerpt;
        private String featuredImageUrl;
        private String status;
        private UUID categoryId;
        private String categoryName;
        private List<String> tags;
        private Integer viewCount;
        private Instant publishedAt;
        private Instant createdAt;
        private Instant updatedAt;
        private List<PostEmbedResponse> embeds;

        public static PostResponse from(Post post) {
            return PostResponse.builder()
                    .id(post.getId())
                    .tenantId(post.getTenant().getId())
                    .tenantName(post.getTenant().getName())
                    .authorId(post.getAuthor().getId())
                    .authorName(post.getAuthor().getFullName())
                    .title(post.getTitle())
                    .slug(post.getSlug())
                    .content(post.getContent())
                    .excerpt(post.getExcerpt())
                    .featuredImageUrl(post.getFeaturedImageUrl())
                    .status(post.getStatus().name())
                    .categoryId(post.getCategory() != null ? post.getCategory().getId() : null)
                    .categoryName(post.getCategory() != null ? post.getCategory().getName() : null)
                    .tags(post.getTags())
                    .viewCount(post.getViewCount())
                    .publishedAt(post.getPublishedAt())
                    .createdAt(post.getCreatedAt())
                    .updatedAt(post.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostListResponse {
        private List<PostResponse> posts;
        private Integer totalCount;
        private Integer page;
        private Integer size;
        private Integer totalPages;
    }

    // ========== PostEmbed DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostEmbedResponse {
        private UUID id;
        private UUID listingId;
        private String listingType;
        private Integer embedOrder;

        public static PostEmbedResponse from(PostEmbed embed) {
            return PostEmbedResponse.builder()
                    .id(embed.getId())
                    .listingId(embed.getListingId())
                    .listingType(embed.getListingType())
                    .embedOrder(embed.getEmbedOrder())
                    .build();
        }
    }

    // ========== Listing Card DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListingCardResponse {
        private UUID listingId;
        private String listingType;
        private String title;
        private String coverImageUrl;
        private java.math.BigDecimal basePrice;
        private java.math.BigDecimal currentPrice;
        private String currency;
        private AvailabilityInfo availability;
        private String tenantName;
        private String ctaUrl;
        private Boolean isActive;
        private String statusReason;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AvailabilityInfo {
            private Boolean inStock;
            private Integer availableQty;
            private Boolean available;
        }

        /**
         * 從 Listing 建立卡片回應
         */
        public static ListingCardResponse fromListing(Listing listing, java.math.BigDecimal currentPrice, Boolean inStock, Integer availableQty) {
            return ListingCardResponse.builder()
                    .listingId(listing.getId())
                    .listingType(listing.getListingType().name())
                    .title(listing.getTitle())
                    .coverImageUrl(listing.getCoverImageUrl())
                    .basePrice(listing.getBasePrice())
                    .currentPrice(currentPrice)
                    .currency("TWD")
                    .availability(AvailabilityInfo.builder()
                            .inStock(inStock)
                            .availableQty(availableQty)
                            .available(inStock)
                            .build())
                    .tenantName(listing.getTenant().getName())
                    .ctaUrl("/" + listing.getListingType().name().toLowerCase() + "s/" + listing.getId())
                    .isActive(listing.getStatus() == Listing.ListingStatus.ACTIVE)
                    .statusReason(listing.getStatus() != Listing.ListingStatus.ACTIVE ? "listing_inactive" : null)
                    .build();
        }
    }

    // ========== Category DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCategoryRequest {
        private String name;
        private String description;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCategoryRequest {
        private String name;
        private String description;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResponse {
        private UUID id;
        private UUID tenantId;
        private String name;
        private String slug;
        private String description;
        private Integer sortOrder;
        private Boolean isActive;
        private Instant createdAt;
        private Instant updatedAt;

        public static CategoryResponse from(PostCategory category) {
            return CategoryResponse.builder()
                    .id(category.getId())
                    .tenantId(category.getTenant().getId())
                    .name(category.getName())
                    .slug(category.getSlug())
                    .description(category.getDescription())
                    .sortOrder(category.getSortOrder())
                    .isActive(category.getIsActive())
                    .createdAt(category.getCreatedAt())
                    .updatedAt(category.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryListResponse {
        private List<CategoryResponse> categories;
        private Integer totalCount;
    }

    // ========== Media DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaUploadResponse {
        private UUID id;
        private String fileName;
        private String filePath;
        private Long fileSize;
        private String mimeType;
        private String fileType;
        private Instant uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaResponse {
        private UUID id;
        private UUID tenantId;
        private UUID uploaderId;
        private String uploaderName;
        private String fileName;
        private String originalName;
        private String filePath;
        private Long fileSize;
        private String formattedFileSize;
        private String mimeType;
        private String fileType;
        private Integer width;
        private Integer height;
        private Integer durationSeconds;
        private Instant createdAt;

        public static MediaResponse from(MediaAsset media) {
            return MediaResponse.builder()
                    .id(media.getId())
                    .tenantId(media.getTenant().getId())
                    .uploaderId(media.getUploader().getId())
                    .uploaderName(media.getUploader().getFullName())
                    .fileName(media.getFileName())
                    .originalName(media.getOriginalName())
                    .filePath(media.getFilePath())
                    .fileSize(media.getFileSize())
                    .formattedFileSize(media.getFormattedFileSize())
                    .mimeType(media.getMimeType())
                    .fileType(media.getFileType().name())
                    .width(media.getWidth())
                    .height(media.getHeight())
                    .durationSeconds(media.getDurationSeconds())
                    .createdAt(media.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaListResponse {
        private List<MediaResponse> items;
        private Integer totalCount;
        private Integer page;
        private Integer size;
        private Integer totalPages;
    }
}