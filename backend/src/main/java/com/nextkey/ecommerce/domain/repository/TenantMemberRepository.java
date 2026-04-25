package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    List<TenantMember> findByUserId(UUID userId);

    List<TenantMember> findByTenantId(UUID tenantId);

    Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndStoreRole(UUID tenantId, UUID userId, TenantMember.StoreRole storeRole);

    long countByTenantId(UUID tenantId);
}