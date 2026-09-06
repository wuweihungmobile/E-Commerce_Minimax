package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.room.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    @Query("SELECT r FROM Room r WHERE r.listing.tenant.id = :tenantId")
    List<Room> findByTenantId(@Param("tenantId") UUID tenantId);

    Optional<Room> findByListingId(UUID listingId);

    boolean existsByListingId(UUID listingId);

    @Query("SELECT COUNT(r) FROM Room r WHERE r.listing.tenant.id = :tenantId")
    int countByListingTenantId(@Param("tenantId") UUID tenantId);

    List<Room> findAllByListingId(UUID listingId);
}