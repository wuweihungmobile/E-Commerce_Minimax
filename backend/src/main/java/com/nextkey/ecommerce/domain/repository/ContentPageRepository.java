package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.cms.ContentPage;

@Repository
public interface ContentPageRepository extends JpaRepository<ContentPage, UUID> {

    Optional<ContentPage> findBySlug(String slug);

    Page<ContentPage> findByStatusOrderBySortOrderAsc(ContentPage.ContentStatus status, Pageable pageable);

    Page<ContentPage> findByTenantIdAndStatusOrderBySortOrderAsc(UUID tenantId, ContentPage.ContentStatus status, Pageable pageable);

    List<ContentPage> findByTenantIdAndStatus(UUID tenantId, ContentPage.ContentStatus status);

    Page<ContentPage> findByPageTypeAndStatusOrderBySortOrderAsc(
            ContentPage.PageType pageType, ContentPage.ContentStatus status, Pageable pageable);
}
