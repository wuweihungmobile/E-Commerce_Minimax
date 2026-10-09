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
    private static final int REFUND_ENUM_LENGTH = 20;

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

    /**
     * 付款期限（Sprint 225，DEF-311）：CREATED 且超過此時間仍未付款的訂房由 {@code BookingTimeoutService} 取消。
     * 新訂房為建立時間＋24 小時；NULL＝歷史訂房（過去沒有付款入口），永不逾時。
     */
    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    /** 取消時間（Sprint 227，DEF-312）；NULL＝未取消，或歷史取消（當時沒有記錄）。 */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** 取消方（Sprint 227）：決定退款規則（PRD Q14：只有買家本人取消才看 24 小時門檻）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = REFUND_ENUM_LENGTH)
    private CancelledBy cancelledBy;

    /** 退款進度（Sprint 227，PRD §15.2.5 的 {@code refundStatus}）；排程把 PENDING 退完後轉 COMPLETED。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = REFUND_ENUM_LENGTH)
    @Builder.Default
    private RefundStatus refundStatus = RefundStatus.NONE;

    /** 取消時決定的應退金額（Sprint 227，PRD Q14）；{@link RefundStatus#NONE} 時為 null。 */
    @Column(name = "refund_amount", precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal refundAmount;

    /**
     * 訂房被哪一張結算單結算（Sprint 247，DEF-353；比照 {@code Order#settledStatementId}）。只有結算流程會寫這個欄位
     * （原生 UPDATE），此處對它是唯讀映射，避免後續的實體更新把記憶體中的舊值寫回、蓋掉併發的結算標記。
     */
    @Column(name = "settled_statement_id", insertable = false, updatable = false)
    private UUID settledStatementId;

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

    /** 取消方（PRD §15.2.5 的 {@code canceledBy}）：買家本人／商家（含管理員代為取消）／系統（逾時）。 */
    public enum CancelledBy {
        CUSTOMER, MERCHANT, SYSTEM
    }

    /** 退款進度（PRD §15.2.5 的 {@code refundStatus}）：不需退款／等待自動退款／已退回。 */
    public enum RefundStatus {
        NONE, PENDING, COMPLETED
    }

    // ===== 影子欄位的讀取（Sprint 243，DEF-338）=====
    // 下面的 tenantId／userId／roomListingId 是 insertable = false 的唯讀影子欄位：只有「從資料庫載入」時才有值，同一個持久化脈絡裡剛用
    // .tenant／user／roomListing(...) 建立的實體它是 null（Sprint 115 DEF-065 起已知的 JPA 陷阱：同交易內剛建立的實體，影子欄位是 null）。回應轉換直接讀它，
    // 建立訂單／訂房／合併結帳的回應（與冪等重放存下的回應）就把 tenantId／userId／roomListingId 回成 null。這裡的 getter 在影子欄位沒有值時退回
    // 關聯的 id，載入自資料庫的實體行為不變（影子欄位有值就用它）。

    public UUID getTenantId() {
        return tenantId != null ? tenantId : (tenant != null ? tenant.getId() : null);
    }

    public UUID getUserId() {
        return userId != null ? userId : (user != null ? user.getId() : null);
    }

    public UUID getRoomListingId() {
        return roomListingId != null ? roomListingId : (roomListing != null ? roomListing.getId() : null);
    }
}
