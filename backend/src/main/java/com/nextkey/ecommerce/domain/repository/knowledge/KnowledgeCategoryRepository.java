package com.nextkey.ecommerce.domain.repository.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.knowledge.KnowledgeCategory;

@Repository
public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategory, UUID> {

    List<KnowledgeCategory> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    Optional<KnowledgeCategory> findByTenantIdAndSlug(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    Optional<KnowledgeCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}