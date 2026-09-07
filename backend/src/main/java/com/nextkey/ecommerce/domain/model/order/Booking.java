package com.nextkey.ecommerce.domain.model.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 🔴 併發防護（DEF-136）：{@code @DynamicUpdate} 讓 Hibernate 只把本次交易內實際被 setter
 * 改動過的欄位包進 UPDATE 語句。BookingService.updateBooking 是「部分欄位選填→整包讀出→
 * save()」的 PATCH 語意，若無此註解，交易 A 改日期（連帶重算金額）與交易 B 併發只改其他欄位時，
 * 後 commit 者會用自己交易一開始讀到的舊快照把先寫入者已提交的日期/金額悄悄覆蓋回去。
 */
@Entity
@Table(name = "bookings")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    private static final int DECIMAL_PRECISION = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_listing_id", nullable = false)
    private Listing roomListing;

    @Column(name = "room_listing_id", insertable = false, updatable = false)
    private UUID roomListingId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Column(name = "total_guests")
    @Builder.Default
    private Integer totalGuests = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.CREATED;

    @Column(name = "total_amount", nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal totalAmount;

    /** 下單當下套用的促銷碼快照（Sprint 124，DEF-047／PRD US-010）；null 表示未使用優惠券 */
    @Column(name = "promo_code")
    private String promoCode;

    /** 下單當下的折扣金額（Sprint 124）；{@link #totalAmount} 已為扣除本欄位後的應付金額 */
    @Column(name = "discount_amount", nullable = false, precision = DECIMAL_PRECISION, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_flags", columnDefinition = "jsonb")
    private Map<String, Object> statusFlags;

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

    public enum BookingStatus {
        CREATED, PAID, CONFIRMED, CHECKED_IN, CHECKED_OUT, COMPLETED, CANCELLED
    }
}
