package com.nextkey.ecommerce.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.promo.PromoCode;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCodeIgnoreCaseAndTenantId(String code, UUID tenantId);

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndTenantId(String code, UUID tenantId);
}