package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Page<Room> findByListing_TenantIdAndListing_Status(
            UUID tenantId,
            Listing.ListingStatus status,
            Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.maxGuests >= :guests AND r.listing.status = 'ACTIVE'")
    Page<Room> findByMinGuests(@Param("guests") Integer guests, Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.location LIKE %:location% AND r.listing.status = 'ACTIVE'")
    Page<Room> findByLocation(@Param("location") String location, Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.listing.tenant.id = :tenantId")
    List<Room> findByTenantId(@Param("tenantId") UUID tenantId);

    Optional<Room> findByListingId(UUID listingId);

    boolean existsByListingId(UUID listingId);

    @Query("SELECT COUNT(r) FROM Room r WHERE r.listing.tenant.id = :tenantId")
    int countByListing_TenantId(@Param("tenantId") UUID tenantId);

    List<Room> findAllByListingId(UUID listingId);
}