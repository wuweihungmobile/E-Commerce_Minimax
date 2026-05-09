package com.nextkey.ecommerce.domain.model.room;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_calendar",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_listing_id", "calendar_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomCalendar {

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

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "booking_id")
    private UUID bookingId;

    public enum RoomCalendarStatus {
        AVAILABLE, BOOKED, BLOCKED, MAINTENANCE
    }
}
