package com.nextkey.ecommerce;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

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
            var systemTenantId = java.util.UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
            if (!tenantRepository.existsById(systemTenantId)) {
                log.info("Initializing System Tenant for tests...");
                Tenant systemTenant = Tenant.builder()
                        .id(systemTenantId)
                        .name("System Tenant")
                        .slug("system")
                        .status(Tenant.TenantStatus.ACTIVE)
                        .description("System-level tenant for platform-wide feature toggles")
                        .contactEmail("system@nextkey.com")
                        .contactPhone("0000000000")
                        .metadata(Map.of("type", "SYSTEM"))
                        .build();
                tenantRepository.save(systemTenant);
                log.info("System Tenant initialized: {}", systemTenantId);
            } else {
                log.debug("System Tenant already exists: {}", systemTenantId);
            }
        };
    }
}