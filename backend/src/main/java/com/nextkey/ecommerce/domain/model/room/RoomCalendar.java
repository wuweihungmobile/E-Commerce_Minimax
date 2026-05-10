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

    @Column(precision = DECIMAL_PRECISION, scale = 2)
    private BigDecimal price;

    @Column(name = "booking_id")
    private UUID bookingId;

    public enum RoomCalendarStatus {
        AVAILABLE, BOOKED, BLOCKED, MAINTENANCE
    }
}
