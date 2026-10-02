package com.nextkey.ecommerce.domain.model.listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 🔴 併發防護（DEF-136）：{@code @DynamicUpdate} 讓 Hibernate 只把本次交易內實際被 setter
 * 改動過的欄位包進 UPDATE 語句。RoomService.updateRoom/ProductService.updateProduct 皆為
 * 「部分欄位選填→整包讀出→save()」的 PATCH 語意，若無此註解，兩個併發請求各自只改不同欄位時，
 * 後 commit 者會用自己交易一開始讀到的舊快照把先寫入者已提交的欄位悄悄覆蓋回去。
 */
@Entity
@Table(name = "listings")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    private static final int DECIMAL_PRECISION = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Sprint 237（DEF-332）：nullable = false 只影響 Hibernate 產生的 DDL（測試庫）：這是 insertable = false 的唯讀影子欄位，
    // 沒有它的話 tenant 關聯的 nullable = false 會被同欄位的影子欄位（預設可為 NULL）蓋掉，測試庫的 listings.tenant_id 變成可為 NULL
    // （正式庫由 Flyway 建，是 NOT NULL），只設影子欄位的測試固件就會默默存成 NULL、直到有程式碼讀它才爆。
    @Column(name = "tenant_id", insertable = false, updatable = false, nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false)
    private ListingType listingType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ListingStatus status = ListingStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "owner_id", insertable = false, updatable = false, nullable = false)
    private UUID ownerId;

    @Column(name = "base_price", nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal basePrice;

    @Column(length = 3)
    @Builder.Default
    private String currency = "TWD";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

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

    public enum ListingType {
        PRODUCT, ROOM
    }

    public enum ListingStatus {
        DRAFT, ACTIVE, INACTIVE, DELETED
    }
}
