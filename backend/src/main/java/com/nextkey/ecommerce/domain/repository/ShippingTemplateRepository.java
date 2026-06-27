package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;

@Repository
public interface ShippingTemplateRepository extends JpaRepository<ShippingTemplate, UUID> {

    List<ShippingTemplate> findByTenantId(UUID tenantId);
}
