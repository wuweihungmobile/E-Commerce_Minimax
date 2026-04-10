package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantFeatureToggleRepository extends JpaRepository<TenantFeatureToggle, UUID> {

    List<TenantFeatureToggle> findByTenantId(UUID tenantId);

    Optional<TenantFeatureToggle> findByTenantIdAndFeatureKey(UUID tenantId, String featureKey);

    boolean existsByTenantIdAndFeatureKey(UUID tenantId, String featureKey);

    void deleteByTenantIdAndFeatureKey(UUID tenantId, String featureKey);
}
