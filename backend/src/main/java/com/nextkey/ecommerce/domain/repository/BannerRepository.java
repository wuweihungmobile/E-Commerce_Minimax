package com.nextkey.ecommerce.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.cms.Banner;

@Repository
public interface BannerRepository extends JpaRepository<Banner, UUID> {

    Page<Banner> findByStatusOrderBySortOrderAsc(Banner.BannerStatus status, Pageable pageable);

    @Query("SELECT b FROM Banner b WHERE b.status = :status AND b.tenantId = :tenantId ORDER BY b.sortOrder ASC")
    List<Banner> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") Banner.BannerStatus status);

    @Query("SELECT b FROM Banner b WHERE b.status = 'PUBLISHED' AND b.position = :position " +
            "AND (b.startDate IS NULL OR b.startDate <= :date) " +
            "AND (b.endDate IS NULL OR b.endDate >= :date) " +
            "ORDER BY b.sortOrder ASC")
    List<Banner> findActiveByPosition(@Param("position") Banner.BannerPosition position, @Param("date") LocalDate date);

    @Query("SELECT b FROM Banner b WHERE b.status = 'PUBLISHED' " +
            "AND (b.startDate IS NULL OR b.startDate <= :date) " +
            "AND (b.endDate IS NULL OR b.endDate >= :date) " +
            "ORDER BY b.sortOrder ASC")
    List<Banner> findAllActive(@Param("date") LocalDate date);

    @Query("SELECT b FROM Banner b WHERE b.status = 'PUBLISHED' AND b.position = :position AND b.tenantId = :tenantId " +
            "AND (b.startDate IS NULL OR b.startDate <= :date) " +
            "AND (b.endDate IS NULL OR b.endDate >= :date) " +
            "ORDER BY b.sortOrder ASC")
    List<Banner> findActiveByPositionAndTenantId(
            @Param("position") Banner.BannerPosition position, @Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("SELECT b FROM Banner b WHERE b.status = 'PUBLISHED' AND b.tenantId = :tenantId " +
            "AND (b.startDate IS NULL OR b.startDate <= :date) " +
            "AND (b.endDate IS NULL OR b.endDate >= :date) " +
            "ORDER BY b.sortOrder ASC")
    List<Banner> findAllActiveByTenantId(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE Banner b SET b.impressionCount = b.impressionCount + 1 WHERE b.id = :bannerId")
    void incrementImpressionCount(@Param("bannerId") UUID bannerId);

    @Modifying
    @Query("UPDATE Banner b SET b.clickCount = b.clickCount + 1 WHERE b.id = :bannerId AND b.tenantId = :tenantId")
    void incrementClickCountForTenant(@Param("bannerId") UUID bannerId, @Param("tenantId") UUID tenantId);
}
