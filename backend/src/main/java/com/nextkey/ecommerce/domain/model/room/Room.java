package com.nextkey.ecommerce.domain.model.room;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nextkey.ecommerce.domain.model.listing.Listing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 🔴 併發防護（DEF-136）：見 {@link Listing} 同一段 {@code @DynamicUpdate} 說明。 */
@Entity
@Table(name = "rooms")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    // Default values
    private static final int DEFAULT_MAX_GUESTS = 2;
    private static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(15, 0);
    private static final LocalTime DEFAULT_CHECK_OUT_TIME = LocalTime.of(11, 0);
    private static final int DEFAULT_ROOM_COUNT = 1;

    @Id
    @Column(name = "listing_id")
    private UUID listingId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "listing_id")
    private Listing listing;

    private String location;

    private Double latitude;

    private Double longitude;

    @Column(name = "max_guests")
    @Builder.Default
    private Integer maxGuests = DEFAULT_MAX_GUESTS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> amenities;

    @Column(name = "check_in_time")
    @Builder.Default
    private LocalTime checkInTime = DEFAULT_CHECK_IN_TIME;

    @Column(name = "check_out_time")
    @Builder.Default
    private LocalTime checkOutTime = DEFAULT_CHECK_OUT_TIME;

    @Column(name = "room_count")
    @Builder.Default
    private Integer roomCount = DEFAULT_ROOM_COUNT;

    // 開放窗（Sprint 47 AI-2202e）：開放至某固定日；NULL = 無此限制
    @Column(name = "open_until_date")
    private LocalDate openUntilDate;

    // 開放窗（Sprint 47 AI-2202e）：開放未來 N 天（滾動，相對下單/查詢當日）；NULL = 無此限制
    @Column(name = "booking_window_days")
    private Integer bookingWindowDays;

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

    /**
     * 開放窗有效上限（Sprint 47 AI-2202e）：取已設定約束中「最早生效者」。
     * 固定截止 {@code openUntilDate} 與滾動視窗 {@code referenceDate + bookingWindowDays}
     * 皆為上限，實際可訂上限為兩者取最小（最早）；兩者皆未設定則無限制。
     *
     * @param referenceDate 滾動視窗基準日（calendar 用今日、availability/booking 用下單日）
     * @return 有效開放至（含當日）；{@code null} = 無限制（維持現狀「無記錄=可訂」）
     */
    public LocalDate resolveOpenUntil(final LocalDate referenceDate) {
        LocalDate byWindow = (bookingWindowDays != null && referenceDate != null)
                ? referenceDate.plusDays(bookingWindowDays)
                : null;
        LocalDate result = openUntilDate;
        if (byWindow != null && (result == null || byWindow.isBefore(result))) {
            result = byWindow;
        }
        return result;
    }

    /**
     * 指定日期是否超出開放窗（未開放預訂）。
     *
     * @param date          目標日期
     * @param referenceDate 滾動視窗基準日
     * @return true = 超出開放窗（未開放）；開放上限為 null（無限制）時恆 false
     */
    public boolean isBeyondOpenWindow(final LocalDate date, final LocalDate referenceDate) {
        LocalDate openUntil = resolveOpenUntil(referenceDate);
        return openUntil != null && date.isAfter(openUntil);
    }
}
