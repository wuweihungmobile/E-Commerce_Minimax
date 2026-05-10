package com.nextkey.ecommerce.domain.model.room;

import java.time.Instant;
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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nextkey.ecommerce.domain.model.listing.Listing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rooms")
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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
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
}
