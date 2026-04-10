package com.nextkey.ecommerce.domain.model.room;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

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
    private Integer maxGuests = 2;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
    private List<String> amenities;

    @Column(name = "check_in_time")
    @Builder.Default
    private LocalTime checkInTime = LocalTime.of(15, 0);

    @Column(name = "check_out_time")
    @Builder.Default
    private LocalTime checkOutTime = LocalTime.of(11, 0);

    @Column(name = "room_count")
    @Builder.Default
    private Integer roomCount = 1;

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
