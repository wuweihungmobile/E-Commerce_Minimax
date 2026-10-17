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
import com.nextkey.ecommerce.domain.model.product.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByListingTenantIdAndListingStatus(
            UUID tenantId,
            Listing.ListingStatus status,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.listing.tenant.id = :tenantId AND p.listing.status = 'ACTIVE' AND "
            + "(LOWER(p.listing.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.listing.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchByTenantIdAndKeyword(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);

    // DEF-379（Sprint 280）：先前無 tenantId 條件，持有 product:read 的使用者用 category／brand
    // 篩選時會看到其他租戶的 ACTIVE 商品。比照 searchByTenantIdAndKeyword 與
    // RoomService.getRooms 的既有慣例，一律先比對呼叫者租戶。
    @Query("SELECT p FROM Product p WHERE p.listing.tenant.id = :tenantId AND p.listing.status = 'ACTIVE' "
            + "AND p.category = :category")
    Page<Product> findByTenantIdAndListingStatusAndCategory(
            @Param("tenantId") UUID tenantId,
            @Param("category") String category,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.listing.tenant.id = :tenantId AND p.listing.status = 'ACTIVE' "
            + "AND p.brand = :brand")
    Page<Product> findByTenantIdAndListingStatusAndBrand(
            @Param("tenantId") UUID tenantId,
            @Param("brand") String brand,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.listing.tenant.id = :tenantId AND p.listing.status = 'ACTIVE' "
            + "AND p.category = :category AND p.brand = :brand")
    Page<Product> findByTenantIdAndListingStatusAndCategoryAndBrand(
            @Param("tenantId") UUID tenantId,
            @Param("category") String category,
            @Param("brand") String brand,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.listing.tenant.id = :tenantId AND p.category = :category")
    List<Product> findByTenantIdAndCategory(
            @Param("tenantId") UUID tenantId,
            @Param("category") String category);

    @Query("SELECT p FROM Product p WHERE p.listing.id = :listingId AND p.listing.status != 'DELETED'")
    Optional<Product> findByListingId(@Param("listingId") UUID listingId);

    boolean existsByListingId(UUID listingId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.listing.tenant.id = :tenantId AND p.listing.status = :status")
    int countByListingTenantIdAndListingStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") Listing.ListingStatus status);
}