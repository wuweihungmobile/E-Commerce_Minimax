package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.cms.post.PostCategory;
import com.nextkey.ecommerce.domain.model.cms.post.PostEmbed;

import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

        @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
                message = "Featured image URL must not use javascript/data/vbscript/file protocol")
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

        @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
                message = "Featured image URL must not use javascript/data/vbscript/file protocol")
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
        /** 店鋪的網址代稱（Sprint 336，DEF-483）：文章的公開網址是 /stores/{tenantSlug}/blog/{slug}，列表頁要靠它組連結。 */
        private String tenantSlug;
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

        public static PostResponse from(final Post post) {
            return PostResponse.builder()
                    .id(post.getId())
                    .tenantId(post.getTenant().getId())
                    .tenantName(post.getTenant().getName())
                    .tenantSlug(post.getTenant().getSlug())
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

        public static PostEmbedResponse from(final PostEmbed embed) {
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

        public static CategoryResponse from(final PostCategory category) {
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
}