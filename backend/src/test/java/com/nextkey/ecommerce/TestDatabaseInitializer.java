package com.nextkey.ecommerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.shared.constants.AppConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ActiveProfiles({"test", "integration-test"})
public class TestDatabaseInitializer {

    /**
     * 預設 Feature Toggle 預設值（與 AdminService.initializeFeatureToggles 保持一致）
     *
     * 🔴 修復 act 環境 E2E 測試失敗：
     * 問題 1：act workflow 會重置資料庫，導致 system tenant 缺少預設 feature toggles
     * 進而導致 ProductService.createProduct() 拋出 E_2004（Feature 'RETAIL_ENABLED' is disabled）
     * 最終映射為 403 Forbidden
     *
     * 問題 2：使用 JPA Entity + saveAndFlush 創建 system tenant 時，
     * 因為 @GeneratedValue(strategy = GenerationType.UUID) 會覆蓋明確設定的 ID，
     * 導致實際存入的 ID 與 AppConstants.SYSTEM_TENANT_ID 不一致
     * （例如 Hibernate 自動生成 60dece7d-... 而非 00000000-...）
     * 結果：AppConstants.SYSTEM_TENANT_ID 查詢時找不到 feature toggles
     *
     * 修復方案：使用 JdbcTemplate 直接 SQL INSERT，明確指定 ID = 00000000-0000-0000-0000-000000000001
     */
    private static final Map<String, Boolean> DEFAULT_FEATURE_TOGGLES = Map.of(
            "RETAIL_ENABLED", true,
            "BOOKING_ENABLED", false,
            "CMS_ENABLED", true,
            "ERP_ENABLED", true,
            "DYNAMIC_PRICING_ENABLED", false,
            "PROMO_ENABLED", false
    );

    @Bean
    CommandLineRunner initializeTestDatabase(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            // Use raw SQL INSERT to ensure exact ID control
            // JPA @GeneratedValue would override the explicit ID
            var systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

            // 1) 安裝 PostgreSQL Trigger：當新 tenant 建立時，自動為其補上預設 feature toggles
            //    解決 E2E 測試建立 test tenant 後未呼叫 approveTenant() 導致無 toggles → 403
            installAutoFeatureToggleTrigger(jdbcTemplate);

            // 2) 為所有現有的 ACTIVE tenant 補上預設 feature toggles
            //    包含 system tenant 以及測試期間已建立的 test tenant
            backfillAllTenantsFeatureToggles(jdbcTemplate);

            // 3) 確保 system tenant 存在（向後相容）
            // 檢查 system tenant 是否已存在（依 ID）
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenants WHERE id = ?",
                    Integer.class,
                    systemTenantId
            );

            if (count == null || count == 0) {
                log.info("Initializing System Tenant for tests (using raw SQL to ensure correct ID)...");
                // 先刪除可能存在的同 slug 紀錄（避免 UNIQUE 衝突）
                jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'platform'");
                jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'system'");

                Timestamp now = Timestamp.from(Instant.now());
                jdbcTemplate.update(
                        "INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, " +
                                "connect_onboarding_status, connect_charges_enabled, connect_payouts_enabled, metadata, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                        systemTenantId,
                        "System Tenant",
                        "platform",
                        "ACTIVE",
                        "System-level tenant for platform-wide feature toggles",
                        "system@nextkey.com",
                        "0000000000",
                        "NOT_STARTED",
                        false,
                        false,
                        "{\"type\": \"SYSTEM\"}",
                        now,
                        now
                );
                log.info("System Tenant initialized: {}", systemTenantId);
            } else {
                log.debug("System Tenant already exists: {}", systemTenantId);
            }

            // 為 system tenant 補上預設 feature toggles
            initializeDefaultFeatureToggles(jdbcTemplate, systemTenantId);
        };
    }

    /**
     * 安裝 PostgreSQL Trigger：當 tenants 表新增一筆時，自動為其補上預設 feature toggles。
     * 這樣 E2E 測試動態建立 tenant 時，不需要額外呼叫 approveTenant() 也能有 feature toggles。
     *
     * 注意：使用 IF NOT EXISTS 而非 ON CONFLICT，因為 tenant_feature_toggles 表的
     * UNIQUE(tenant_id, feature_key) 約束可能因 Flyway migration 順序問題而未被建立。
     */
    private void installAutoFeatureToggleTrigger(final JdbcTemplate jdbcTemplate) {
        try {
            // 建立 trigger function
            jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION auto_init_tenant_feature_toggles() " +
                "RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "  IF NOT EXISTS (SELECT 1 FROM tenant_feature_toggles WHERE tenant_id = NEW.id AND feature_key = 'RETAIL_ENABLED') THEN " +
                "    INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                "    VALUES (gen_random_uuid(), NEW.id, 'RETAIL_ENABLED', true, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}'::jsonb); " +
                "  END IF; " +
                "  IF NOT EXISTS (SELECT 1 FROM tenant_feature_toggles WHERE tenant_id = NEW.id AND feature_key = 'BOOKING_ENABLED') THEN " +
                "    INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                "    VALUES (gen_random_uuid(), NEW.id, 'BOOKING_ENABLED', false, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}'::jsonb); " +
                "  END IF; " +
                "  IF NOT EXISTS (SELECT 1 FROM tenant_feature_toggles WHERE tenant_id = NEW.id AND feature_key = 'CMS_ENABLED') THEN " +
                "    INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                "    VALUES (gen_random_uuid(), NEW.id, 'CMS_ENABLED', true, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}'::jsonb); " +
                "  END IF; " +
                "  IF NOT EXISTS (SELECT 1 FROM tenant_feature_toggles WHERE tenant_id = NEW.id AND feature_key = 'ERP_ENABLED') THEN " +
                "    INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                "    VALUES (gen_random_uuid(), NEW.id, 'ERP_ENABLED', true, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}'::jsonb); " +
                "  END IF; " +
                "  IF NOT EXISTS (SELECT 1 FROM tenant_feature_toggles WHERE tenant_id = NEW.id AND feature_key = 'DYNAMIC_PRICING_ENABLED') THEN " +
                "    INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                "    VALUES (gen_random_uuid(), NEW.id, 'DYNAMIC_PRICING_ENABLED', false, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}'::jsonb); " +
                "  END IF; " +
                "  IF NOT EXISTS (SELECT 1 FROM tenant_feature_toggles WHERE tenant_id = NEW.id AND feature_key = 'PROMO_ENABLED') THEN " +
                "    INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                "    VALUES (gen_random_uuid(), NEW.id, 'PROMO_ENABLED', false, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}'::jsonb); " +
                "  END IF; " +
                "  RETURN NEW; " +
                "END; " +
                "$$ language 'plpgsql'"
            );

            // 先刪除已存在的 trigger，再重新建立（保證冪等）
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_auto_init_tenant_feature_toggles ON tenants");
            jdbcTemplate.execute(
                "CREATE TRIGGER trg_auto_init_tenant_feature_toggles " +
                "AFTER INSERT ON tenants " +
                "FOR EACH ROW EXECUTE FUNCTION auto_init_tenant_feature_toggles()"
            );

            // BEFORE DELETE trigger: 刪除 tenant 前先清空其 feature toggles
            // 避免 E2E 測試 @AfterEach 刪除 test tenant 時因外鍵約束失敗
            jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION auto_cleanup_tenant_feature_toggles() " +
                "RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "  DELETE FROM tenant_feature_toggles WHERE tenant_id = OLD.id; " +
                "  RETURN OLD; " +
                "END; " +
                "$$ language 'plpgsql'"
            );
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_auto_cleanup_tenant_feature_toggles ON tenants");
            jdbcTemplate.execute(
                "CREATE TRIGGER trg_auto_cleanup_tenant_feature_toggles " +
                "BEFORE DELETE ON tenants " +
                "FOR EACH ROW EXECUTE FUNCTION auto_cleanup_tenant_feature_toggles()"
            );

            log.info("Auto-init feature toggles trigger installed successfully");
        } catch (Exception e) {
            log.warn("Failed to install auto-init trigger (may not be PostgreSQL): {}", e.getMessage());
        }
    }

    /**
     * 為所有現有 ACTIVE tenant 補上預設 feature toggles。
     * 解決測試啟動前已有的 tenant 缺少 toggles 的問題。
     */
    private void backfillAllTenantsFeatureToggles(final JdbcTemplate jdbcTemplate) {
        try {
            List<UUID> tenantIds = jdbcTemplate.queryForList(
                "SELECT id FROM tenants WHERE status = 'ACTIVE'",
                UUID.class
            );
            log.info("Backfilling feature toggles for {} existing ACTIVE tenants", tenantIds.size());
            for (UUID tenantId : tenantIds) {
                initializeDefaultFeatureToggles(jdbcTemplate, tenantId);
            }
        } catch (Exception e) {
            log.warn("Failed to backfill feature toggles: {}", e.getMessage());
        }
    }

    /**
     * 為指定的 Tenant 初始化預設 Feature Toggles（若尚未存在）
     * 與 AdminService.initializeFeatureToggles 邏輯一致，但用於測試環境
     *
     * 重要：使用 raw SQL 而非 JPA entity 來確保 tenant_id 欄位正確
     */
    private void initializeDefaultFeatureToggles(final JdbcTemplate jdbcTemplate, final UUID tenantId) {
        List<String> enabledFeatures = new java.util.ArrayList<>();

        for (Map.Entry<String, Boolean> entry : DEFAULT_FEATURE_TOGGLES.entrySet()) {
            String featureKey = entry.getKey();
            Boolean isEnabled = entry.getValue();

            // 檢查是否已存在
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tenant_feature_toggles WHERE tenant_id = ? AND feature_key = ?",
                    Integer.class,
                    tenantId,
                    featureKey
            );

            if (existing != null && existing > 0) {
                log.debug("Feature toggle already exists: tenantId={}, featureKey={}", tenantId, featureKey);
                continue;
            }

            Timestamp now = Timestamp.from(Instant.now());
            UUID toggleId = UUID.randomUUID();
            Timestamp enabledAt = isEnabled ? now : null;
            Timestamp disabledAt = isEnabled ? null : now;

            jdbcTemplate.update(
                    "INSERT INTO tenant_feature_toggles (id, tenant_id, feature_key, is_enabled, enabled_at, disabled_at, created_at, updated_at, config) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                    toggleId,
                    tenantId,
                    featureKey,
                    isEnabled,
                    enabledAt,
                    disabledAt,
                    now,
                    now,
                    "{}"
            );

            if (isEnabled) {
                enabledFeatures.add(featureKey);
            }
        }

        log.info("Initialized default feature toggles for tenant {}: enabled={}",
                tenantId, enabledFeatures);
    }
}
