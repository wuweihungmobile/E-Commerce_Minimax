package com.nextkey.ecommerce.domain.repository.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.media.MediaAsset;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Page<MediaAsset> findByTenantIdAndIsDeletedFalse(UUID tenantId, Pageable pageable);

    Page<MediaAsset> findByTenantIdAndCategoryIdAndIsDeletedFalse(UUID tenantId, UUID categoryId, Pageable pageable);

    @Query("SELECT m FROM MediaAsset m WHERE m.tenant.id = :tenantId AND m.isDeleted = false " +
           "AND (LOWER(m.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.altText) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<MediaAsset> searchByKeyword(@Param("tenantId") UUID tenantId,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    @Query("SELECT m FROM MediaAsset m WHERE m.tenant.id = :tenantId AND m.isDeleted = false " +
           "AND m.mimeType LIKE :mimeType%")
    Page<MediaAsset> findByMimeType(@Param("tenantId") UUID tenantId,
                                    @Param("mimeType") String mimeType,
                                    Pageable pageable);

    Optional<MediaAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT m FROM MediaAsset m WHERE m.id = :id AND m.tenant.id = :tenantId AND m.isDeleted = false")
    Optional<MediaAsset> findActiveByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<MediaAsset> findByTenantIdAndUsageCountGreaterThan(UUID tenantId, Integer minUsage);

    @Query("SELECT COUNT(m) FROM MediaAsset m WHERE m.tenant.id = :tenantId AND m.isDeleted = false")
    Long countByTenantId(@Param("tenantId") UUID tenantId);
}