package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.listing.Listing;

@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {

    Page<Listing> findByStatus(Listing.ListingStatus status, Pageable pageable);

    Page<Listing> findByTenantIdAndStatus(UUID tenantId, Listing.ListingStatus status, Pageable pageable);

    Page<Listing> findByListingTypeAndStatus(Listing.ListingType listingType, Listing.ListingStatus status, Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND " +
           "(LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(l.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Listing> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND l.listingType = :type " +
           "AND (LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(l.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Listing> searchByTypeAndKeyword(@Param("type") Listing.ListingType type,
                                          @Param("keyword") String keyword,
                                          Pageable pageable);

    List<Listing> findByOwnerIdAndStatus(UUID ownerId, Listing.ListingStatus status);

    List<Listing> findByOwnerId(UUID ownerId);

    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND l.tenant.id = :tenantId")
    Page<Listing> findActiveByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT l.id FROM Listing l WHERE l.tenant.id = :tenantId")
    List<UUID> findIdsByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM Listing l WHERE l.tenant.id = :tenantId")
    List<Listing> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM Listing l LEFT JOIN FETCH l.tenant WHERE l.id = :listingId")
    Optional<Listing> findByIdWithTenant(@Param("listingId") UUID listingId);

    long countByTenantIdAndStatus(UUID tenantId, Listing.ListingStatus status);

    // Sprint 147：MAX_PRODUCTS/MAX_ROOMS 數量配額強制執行需依 listingType 分別計數
    long countByTenantIdAndListingTypeAndStatus(
            UUID tenantId, Listing.ListingType listingType, Listing.ListingStatus status);
}