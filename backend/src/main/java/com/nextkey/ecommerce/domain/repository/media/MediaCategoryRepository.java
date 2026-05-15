package com.nextkey.ecommerce.domain.repository.media;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.media.MediaCategory;

@Repository
public interface MediaCategoryRepository extends JpaRepository<MediaCategory, UUID> {

    List<MediaCategory> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    List<MediaCategory> findByTenantIdAndParentIsNullOrderBySortOrderAsc(UUID tenantId);

    List<MediaCategory> findByTenantIdAndParentIdOrderBySortOrderAsc(UUID tenantId, UUID parentId);

    @Query("SELECT c FROM MediaCategory c WHERE c.tenant.id = :tenantId AND c.id = :id")
    Optional<MediaCategory> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndNameAndParentIsNull(UUID tenantId, String name);

    boolean existsByTenantIdAndNameAndParentId(UUID tenantId, String name, UUID parentId);
}