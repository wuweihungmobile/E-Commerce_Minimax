package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantApplicationRepository extends JpaRepository<TenantApplication, UUID> {

    List<TenantApplication> findByUserId(UUID userId);

    List<TenantApplication> findByStatus(TenantApplication.ApplicationStatus status);

    Optional<TenantApplication> findByUserIdAndStatus(UUID userId, TenantApplication.ApplicationStatus status);

    boolean existsByUserIdAndStatusIn(UUID userId, List<TenantApplication.ApplicationStatus> statuses);
}