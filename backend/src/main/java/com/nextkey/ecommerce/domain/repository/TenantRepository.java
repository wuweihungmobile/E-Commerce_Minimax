package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByIdAndStatus(UUID id, Tenant.TenantStatus status);

    List<Tenant> findByStatus(Tenant.TenantStatus status);

    boolean existsBySlug(String slug);
}
