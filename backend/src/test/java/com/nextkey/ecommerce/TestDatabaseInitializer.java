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

            // 🔴 修復：同時檢查 ID 和 slug，確保 System Tenant 不重複建立
            // 原因：V1__Initial_Schema.sql 建立的 system tenant slug 是 'platform'
            // 只有當 ID 和 slug 都不存在時才建立，避免重複鍵衝突
            if (!tenantRepository.existsById(systemTenantId) && !tenantRepository.existsBySlug("platform")) {
                log.info("Initializing System Tenant for tests...");
                Tenant systemTenant = Tenant.builder()
                        .id(systemTenantId)
                        .name("System Tenant")
                        .slug("platform")  // 與 V1__Initial_Schema.sql 一致
                        .status(Tenant.TenantStatus.ACTIVE)
                        .description("System-level tenant for platform-wide feature toggles")
                        .contactEmail("system@nextkey.com")
                        .contactPhone("0000000000")
                        .metadata(java.util.Map.of("type", "SYSTEM"))
                        .build();
                tenantRepository.saveAndFlush(systemTenant);
                log.info("System Tenant initialized: {}", systemTenantId);
            } else {
                log.debug("System Tenant already exists (ID={} or slug=platform), skipping", systemTenantId);
            }
        };
    }
}