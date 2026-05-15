package com.nextkey.ecommerce.domain.repository.faq;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.faq.FaqCategory;

@Repository
public interface FaqCategoryRepository extends JpaRepository<FaqCategory, UUID> {

    List<FaqCategory> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    Optional<FaqCategory> findByTenantIdAndSlug(UUID tenantId, String slug);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    Optional<FaqCategory> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}