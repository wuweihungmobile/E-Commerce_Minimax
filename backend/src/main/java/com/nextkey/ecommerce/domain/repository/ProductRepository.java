package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
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
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByListing_TenantIdAndListing_Status(
            UUID tenantId,
            Listing.ListingStatus status,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.listing.status = 'ACTIVE'")
    Page<Product> findByCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.brand = :brand AND p.listing.status = 'ACTIVE'")
    Page<Product> findByBrand(@Param("brand") String brand, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.brand = :brand AND p.listing.status = 'ACTIVE'")
    Page<Product> findByCategoryAndBrand(
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
    int countByListing_TenantIdAndListing_Status(
            @Param("tenantId") UUID tenantId,
            @Param("status") Listing.ListingStatus status);
}