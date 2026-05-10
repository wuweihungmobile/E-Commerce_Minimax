package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

/**
 * CMS DTO
 */
public class CmsDto {

    // ========== Content Page ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePageRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Slug is required")
        private String slug;

        private String content;

        private Map<String, Object> metadata;

        @NotNull(message = "Page type is required")
        private PageType pageType;

        private String template;
        private String featuredImageUrl;
        private List<Map<String, Object>> sections;
        private Boolean isIndexable;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePageRequest {
        private String title;
        private String content;
        private Map<String, Object> metadata;
        private String template;
        private String featuredImageUrl;
        private List<Map<String, Object>> sections;
        private ContentStatus status;
        private Boolean isIndexable;
        private Integer sortOrder;
    }

    public enum PageType {
        LANDING_PAGE,
        ABOUT_US,
        CONTACT_US,
        FAQ,
        TERMS,
        PRIVACY,
        CUSTOM
    }

    public enum ContentStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    // ========== Page Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResponse {
        private UUID pageId;
        private String title;
        private String slug;
        private String content;
        private Map<String, Object> metadata;
        private String pageType;
        private String template;
        private String featuredImageUrl;
        private List<Map<String, Object>> sections;
        private String status;
        private Instant publishedAt;
        private UUID authorId;
        private UUID tenantId;
        private Boolean isIndexable;
        private Integer sortOrder;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== Banner ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBannerRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Image URL is required")
        private String imageUrl;

        private String linkUrl;
        private LinkType linkType;
        private String description;
        private String buttonText;
        private Map<String, Object> metadata;
        private LocalDate startDate;
        private LocalDate endDate;

        @NotNull(message = "Banner type is required")
        private BannerType bannerType;

        @NotNull(message = "Position is required")
        private BannerPosition position;

        private String targetAudience;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBannerRequest {
        private String title;
        private String imageUrl;
        private String linkUrl;
        private LinkType linkType;
        private String description;
        private String buttonText;
        private Map<String, Object> metadata;
        private LocalDate startDate;
        private LocalDate endDate;
        private BannerType bannerType;
        private BannerPosition position;
        private ContentStatus status;
        private String targetAudience;
        private Integer sortOrder;
    }

    public enum BannerType {
        HERO,
        PROMOTION,
        ANNOUNCEMENT,
        EMBEDDED_CARD
    }

    public enum BannerPosition {
        HOME_TOP,
        HOME_MIDDLE,
        HOME_BOTTOM,
        LISTING_PAGE,
        PRODUCT_PAGE,
        CHECKOUT_PAGE,
        SIDEBAR
    }

    public enum LinkType {
        URL,
        LISTING,
        PAGE,
        CATEGORY
    }

    // ========== Banner Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerResponse {
        private UUID bannerId;
        private String title;
        private String imageUrl;
        private String linkUrl;
        private String linkType;
        private String description;
        private String buttonText;
        private Map<String, Object> metadata;
        private LocalDate startDate;
        private LocalDate endDate;
        private String bannerType;
        private String position;
        private String status;
        private UUID tenantId;
        private String targetAudience;
        private Integer impressionCount;
        private Integer clickCount;
        private Integer sortOrder;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== Banner List ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BannerListResponse {
        private List<BannerResponse> banners;
        private String position;
    }

    // ========== Page List ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageListResponse {
        private List<PageResponse> pages;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    // ========== Publish Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishRequest {
        @NotNull(message = "Page ID is required")
        private UUID pageId;
    }
}
