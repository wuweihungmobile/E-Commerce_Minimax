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

    // DEF-321(b)（Sprint 252）：店鋪前台 /products、/rooms 子頁——指定租戶、指定類型的公開上架清單
    Page<Listing> findByTenantIdAndListingTypeAndStatus(
            UUID tenantId, Listing.ListingType listingType, Listing.ListingStatus status, Pageable pageable);

    // DEF-321(b)（Sprint 252）：店鋪前台首頁「精選商品/房源」區塊
    Page<Listing> findByTenantIdAndStatusAndIsFeaturedTrue(
            UUID tenantId, Listing.ListingStatus status, Pageable pageable);

    @Query("SELECT l.id FROM Listing l WHERE l.tenant.id = :tenantId")
    List<UUID> findIdsByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM Listing l WHERE l.tenant.id = :tenantId")
    List<Listing> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM Listing l LEFT JOIN FETCH l.tenant WHERE l.id = :listingId")
    Optional<Listing> findByIdWithTenant(@Param("listingId") UUID listingId);

    // DEF-358(a)（Sprint 259）：跨店鋪公開瀏覽（/products、/rooms）——只回傳 ACTIVE 且所屬租戶也
    // ACTIVE 的項目（租戶停權/終止/待審核時其商品不應出現在全平台瀏覽），比照既有
    // StorefrontController.findActiveTenantBySlug 的既有慣例。租戶狀態過濾需在 DB 層完成（而非
    // 查完一頁再篩掉），否則分頁會因篩掉部分項目而少於 size、使用者看到的總頁數也會失準。
    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND l.tenant.status = 'ACTIVE'")
    Page<Listing> findPubliclyVisible(Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND l.tenant.status = 'ACTIVE' AND l.listingType = :type")
    Page<Listing> findPubliclyVisibleByType(@Param("type") Listing.ListingType type, Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND l.tenant.status = 'ACTIVE' AND " +
           "(LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(l.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Listing> searchPubliclyVisibleByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE' AND l.tenant.status = 'ACTIVE' AND l.listingType = :type AND " +
           "(LOWER(l.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(l.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Listing> searchPubliclyVisibleByTypeAndKeyword(@Param("type") Listing.ListingType type,
                                                         @Param("keyword") String keyword,
                                                         Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, Listing.ListingStatus status);

    // Sprint 147：MAX_PRODUCTS/MAX_ROOMS 數量配額強制執行需依 listingType 分別計數
    long countByTenantIdAndListingTypeAndStatus(
            UUID tenantId, Listing.ListingType listingType, Listing.ListingStatus status);
}