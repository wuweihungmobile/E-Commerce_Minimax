package com.nextkey.ecommerce;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ActiveProfiles({"test", "integration-test"})
public class TestDatabaseInitializer {

    @Bean
    CommandLineRunner initializeTestDatabase(TenantRepository tenantRepository) {
        return args -> {
            // Initialize System Tenant if not exists
            var systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

            // 🔴 修復：只用 existsById 檢查 ID，不使用 slug 檢查
            // 原因：V1__Initial_Schema.sql 建立的 system tenant slug 是 'platform'，不是 'system'
            // 使用 ID 檢查是最可靠的方式，因為 ID 是唯一不變的標識符
            if (!tenantRepository.existsById(systemTenantId)) {
                log.info("Initializing System Tenant for tests...");
                Tenant systemTenant = Tenant.builder()
                        .id(systemTenantId)
                        .name("System Tenant")
                        .slug("platform")  // 🔴 修正：使用與 V1__Initial_Schema.sql 一致的 slug
                        .status(Tenant.TenantStatus.ACTIVE)
                        .description("System-level tenant for platform-wide feature toggles")
                        .contactEmail("system@nextkey.com")
                        .contactPhone("0000000000")
                        .metadata(java.util.Map.of("type", "SYSTEM"))
                        .build();
                tenantRepository.saveAndFlush(systemTenant);
                log.info("System Tenant initialized: {}", systemTenantId);
            } else {
                log.debug("System Tenant already exists: {}", systemTenantId);
            }
        };
    }
}