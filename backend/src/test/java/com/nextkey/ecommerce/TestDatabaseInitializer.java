package com.nextkey.ecommerce;

import java.util.Optional;
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

            // Check by ID first (most reliable)
            if (!tenantRepository.existsById(systemTenantId)) {
                // Also check by slug to handle edge cases
                Optional<Tenant> existingBySlug = tenantRepository.findBySlug("system");
                if (existingBySlug.isEmpty()) {
                    log.info("Initializing System Tenant for tests...");
                    Tenant systemTenant = Tenant.builder()
                            .id(systemTenantId)
                            .name("System Tenant")
                            .slug("system")
                            .status(Tenant.TenantStatus.ACTIVE)
                            .description("System-level tenant for platform-wide feature toggles")
                            .contactEmail("system@nextkey.com")
                            .contactPhone("0000000000")
                            .metadata(java.util.Map.of("type", "SYSTEM"))
                            .build();
                    tenantRepository.saveAndFlush(systemTenant);
                    log.info("System Tenant initialized: {}", systemTenantId);
                } else {
                    log.debug("System Tenant already exists (by slug): {}", existingBySlug.get().getId());
                }
            } else {
                log.debug("System Tenant already exists: {}", systemTenantId);
            }
        };
    }
}