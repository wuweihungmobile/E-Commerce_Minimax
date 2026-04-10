package com.nextkey.ecommerce.domain.model.cms;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cms_banners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "link_url")
    private String linkUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type")
    private LinkType linkType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "button_text")
    private String buttonText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "banner_type", nullable = false)
    private BannerType bannerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false)
    private BannerPosition position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BannerStatus status = BannerStatus.DRAFT;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "target_audience")
    private String targetAudience; // ALL, MEMBERS, PREMIUM

    @Column(name = "impression_count")
    @Builder.Default
    private Integer impressionCount = 0;

    @Column(name = "click_count")
    @Builder.Default
    private Integer clickCount = 0;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
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

    public enum BannerStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }
}
