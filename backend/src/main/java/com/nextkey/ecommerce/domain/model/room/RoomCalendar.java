package com.nextkey.ecommerce.domain.model.room;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.nextkey.ecommerce.domain.model.listing.Listing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room_calendar",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_listing_id", "calendar_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomCalendar {

    private static final int DECIMAL_PRECISION = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_listing_id", nullable = false)
    private Listing listing;

    @Column(name = "calendar_date", nullable = false)
    private LocalDate calendarDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomCalendarStatus status = RoomCalendarStatus.AVAILABLE;

    // 【已停用 / 保留欄位】Sprint 45 AI-2406：room_calendar.price 手動日價機制已停用
    // （寫入路徑 setDatePrice/setDatePriceBulk 為死碼已移除、恆 NULL、無讀取者）。
    // 每日基準價一律取 listing.basePrice，動態折扣由 PricingService 規則（含 MANUAL_OVERRIDE）套用。
    // 欄位暫保留以符合 ddl-auto=validate（schema 對齊）；DROP COLUMN 另立後續低風險任務。
    // 詳見 docs/06_quality/PRICING_MECHANISM_UNIFICATION.md。（不加 @Deprecated 以維持專案 @Deprecated=0 慣例）
    @Column(precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal price;

    @Column(name = "booking_id")
    private UUID bookingId;

    public enum RoomCalendarStatus {
        AVAILABLE, BOOKED, BLOCKED, MAINTENANCE
    }
}
