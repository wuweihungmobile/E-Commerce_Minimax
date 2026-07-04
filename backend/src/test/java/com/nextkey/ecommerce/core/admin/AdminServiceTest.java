package com.nextkey.ecommerce.core.admin;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.domain.model.audit.AuditLog;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.domain.repository.audit.AuditLogRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AdminService 單元測試
 *
 * 測試範圍：
 * - updateTenantFeatureToggle()
 * - getTenantFeatureToggles()
 * - setFeatureToggle()
 * - getTenants()
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService: 功能開關管理")
class AdminServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantFeatureToggleRepository featureToggleRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminService adminService;

    // 測試資料
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TEST_TOGGLE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

    private Tenant buildTenant() {
        return Tenant.builder()
                .id(TEST_TENANT_ID)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
    }

    private TenantFeatureToggle buildFeatureToggle(Tenant tenant, String featureKey, Boolean isEnabled) {
        return TenantFeatureToggle.builder()
                .id(TEST_TOGGLE_ID)
                .tenant(tenant)
                .featureKey(featureKey)
                .isEnabled(isEnabled)
                .build();
    }

    // ── updateTenantFeatureToggle Tests ─────────────────────────────────

    @Nested
    @DisplayName("updateTenantFeatureToggle()")
    class UpdateTenantFeatureToggle {

        @Test
        @DisplayName("updateTenantFeatureToggle_enableFeature_success")
        void updateTenantFeatureToggle_enableFeature_success() {
            // Arrange
            Tenant tenant = buildTenant();
            TenantFeatureToggle toggle = buildFeatureToggle(tenant, "RETAIL_ENABLED", false);

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "RETAIL_ENABLED"))
                    .thenReturn(Optional.of(toggle));
            when(featureToggleRepository.save(any(TenantFeatureToggle.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            AdminDto.FeatureToggleResponse response = adminService.updateTenantFeatureToggle(
                    TEST_TENANT_ID, "RETAIL_ENABLED", true);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFeatureKey()).isEqualTo("RETAIL_ENABLED");
            assertThat(response.getIsEnabled()).isTrue();
            assertThat(response.getEnabledAt()).isNotNull();
            verify(featureToggleRepository).save(any(TenantFeatureToggle.class));
        }

        @Test
        @DisplayName("updateTenantFeatureToggle_disableFeature_success")
        void updateTenantFeatureToggle_disableFeature_success() {
            // Arrange
            Tenant tenant = buildTenant();
            TenantFeatureToggle toggle = buildFeatureToggle(tenant, "BOOKING_ENABLED", true);

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "BOOKING_ENABLED"))
                    .thenReturn(Optional.of(toggle));
            when(featureToggleRepository.save(any(TenantFeatureToggle.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            AdminDto.FeatureToggleResponse response = adminService.updateTenantFeatureToggle(
                    TEST_TENANT_ID, "BOOKING_ENABLED", false);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getIsEnabled()).isFalse();
            assertThat(response.getDisabledAt()).isNotNull();
            verify(featureToggleRepository).save(any(TenantFeatureToggle.class));
        }

        @Test
        @DisplayName("updateTenantFeatureToggle_tenantNotFound_throwsException")
        void updateTenantFeatureToggle_tenantNotFound_throwsException() {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> adminService.updateTenantFeatureToggle(
                    TEST_TENANT_ID, "RETAIL_ENABLED", true))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                        assertThat(bex.getMessage()).contains("Tenant not found");
                    });
        }

        @Test
        @DisplayName("updateTenantFeatureToggle_toggleNotFound_throwsException")
        void updateTenantFeatureToggle_toggleNotFound_throwsException() {
            // Arrange
            Tenant tenant = buildTenant();
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "UNKNOWN_FEATURE"))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> adminService.updateTenantFeatureToggle(
                    TEST_TENANT_ID, "UNKNOWN_FEATURE", true))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                        assertThat(bex.getMessage()).contains("Feature toggle not found");
                    });
        }

        @Test
        @DisplayName("updateTenantFeatureToggle_nullEnabled_setsFalse")
        void updateTenantFeatureToggle_nullEnabled_setsFalse() {
            // Arrange
            Tenant tenant = buildTenant();
            TenantFeatureToggle toggle = buildFeatureToggle(tenant, "RETAIL_ENABLED", true);

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "RETAIL_ENABLED"))
                    .thenReturn(Optional.of(toggle));
            when(featureToggleRepository.save(any(TenantFeatureToggle.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            AdminDto.FeatureToggleResponse response = adminService.updateTenantFeatureToggle(
                    TEST_TENANT_ID, "RETAIL_ENABLED", null);

            // Assert
            assertThat(response.getIsEnabled()).isFalse();
        }
    }

    // ── setFeatureToggle Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("setFeatureToggle()")
    class SetFeatureToggle {

        @Test
        @DisplayName("setFeatureToggle_newToggle_createsSuccessfully")
        void setFeatureToggle_newToggle_createsSuccessfully() {
            // Arrange
            Tenant tenant = buildTenant();
            AdminDto.FeatureToggleRequest request = AdminDto.FeatureToggleRequest.builder()
                    .tenantId(TEST_TENANT_ID)
                    .featureKey("NEW_FEATURE")
                    .isEnabled(true)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "NEW_FEATURE"))
                    .thenReturn(Optional.empty());
            when(featureToggleRepository.save(any(TenantFeatureToggle.class))).thenAnswer(i -> {
                TenantFeatureToggle saved = i.getArgument(0);
                saved.setId(TEST_TOGGLE_ID);
                return saved;
            });

            // Act
            AdminDto.FeatureToggleResponse response = adminService.setFeatureToggle(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getFeatureKey()).isEqualTo("NEW_FEATURE");
            assertThat(response.getIsEnabled()).isTrue();
        }

        @Test
        @DisplayName("setFeatureToggle_existingToggle_updatesSuccessfully")
        void setFeatureToggle_existingToggle_updatesSuccessfully() {
            // Arrange
            Tenant tenant = buildTenant();
            TenantFeatureToggle existingToggle = buildFeatureToggle(tenant, "EXISTING_FEATURE", false);
            AdminDto.FeatureToggleRequest request = AdminDto.FeatureToggleRequest.builder()
                    .tenantId(TEST_TENANT_ID)
                    .featureKey("EXISTING_FEATURE")
                    .isEnabled(true)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "EXISTING_FEATURE"))
                    .thenReturn(Optional.of(existingToggle));
            when(featureToggleRepository.save(any(TenantFeatureToggle.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            AdminDto.FeatureToggleResponse response = adminService.setFeatureToggle(request);

            // Assert
            assertThat(response.getIsEnabled()).isTrue();
            assertThat(response.getEnabledAt()).isNotNull();
        }

        @Test
        @DisplayName("setFeatureToggle_tenantNotFound_throwsException")
        void setFeatureToggle_tenantNotFound_throwsException() {
            // Arrange
            AdminDto.FeatureToggleRequest request = AdminDto.FeatureToggleRequest.builder()
                    .tenantId(TEST_TENANT_ID)
                    .featureKey("ANY_FEATURE")
                    .isEnabled(true)
                    .build();

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> adminService.setFeatureToggle(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                    });
        }
    }

    // ── getTenantFeatureToggles Tests ──────────────────────────────────

    @Nested
    @DisplayName("getTenantFeatureToggles()")
    class GetTenantFeatureToggles {

        @Test
        @DisplayName("getTenantFeatureToggles_returnsToggleList")
        void getTenantFeatureToggles_returnsToggleList() {
            // Arrange
            Tenant tenant = buildTenant();
            TenantFeatureToggle toggle1 = buildFeatureToggle(tenant, "RETAIL_ENABLED", true);
            TenantFeatureToggle toggle2 = buildFeatureToggle(tenant, "BOOKING_ENABLED", false);

            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(featureToggleRepository.findByTenantId(TEST_TENANT_ID))
                    .thenReturn(java.util.List.of(toggle1, toggle2));

            // Act
            AdminDto.TenantFeatureTogglesResponse response = adminService.getTenantFeatureToggles(TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getTenantId()).isEqualTo(TEST_TENANT_ID);
            assertThat(response.getTenantName()).isEqualTo("Test Tenant");
            assertThat(response.getToggles()).hasSize(2);
        }

        @Test
        @DisplayName("getTenantFeatureToggles_tenantNotFound_throwsException")
        void getTenantFeatureToggles_tenantNotFound_throwsException() {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> adminService.getTenantFeatureToggles(TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                    });
        }
    }

    // ── getTenants Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("getTenants()")
    class GetTenants {

        @Test
        @DisplayName("getTenants_returnsTenantList")
        void getTenants_returnsTenantList() {
            // Arrange
            Tenant tenant1 = buildTenant();
            Tenant tenant2 = Tenant.builder()
                    .id(UUID.randomUUID())
                    .name("Second Tenant")
                    .slug("second-tenant")
                    .status(Tenant.TenantStatus.ACTIVE)
                    .build();

            when(tenantRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Tenant>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(tenant1, tenant2)));
            when(userRepository.findByTenantId(any())).thenReturn(java.util.Collections.emptyList());
            when(listingRepository.findIdsByTenantId(any())).thenReturn(java.util.Collections.emptyList());

            // Act
            AdminDto.TenantListResponse response = adminService.getTenants(0, 10);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getTotalCount()).isEqualTo(2);
            assertThat(response.getTenants()).hasSize(2);
        }

        @Test
        @DisplayName("getTenants_emptyList_returnsEmptyResponse")
        void getTenants_emptyList_returnsEmptyResponse() {
            // Arrange
            when(tenantRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Tenant>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList()));

            // Act
            AdminDto.TenantListResponse response = adminService.getTenants(0, 10);

            // Assert
            assertThat(response.getTotalCount()).isEqualTo(0);
            assertThat(response.getTenants()).isEmpty();
        }

        @Test
        @DisplayName("getTenants_withStatusAndKeyword_returnsPagedResult")
        void getTenants_withStatusAndKeyword_returnsPagedResult() {
            Tenant tenant = buildTenant();
            when(tenantRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Tenant>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(
                            java.util.List.of(tenant),
                            org.springframework.data.domain.PageRequest.of(1, 5), 11));
            when(userRepository.findByTenantId(any())).thenReturn(java.util.Collections.emptyList());
            when(listingRepository.findIdsByTenantId(any())).thenReturn(java.util.Collections.emptyList());

            AdminDto.TenantListResponse response =
                    adminService.getTenants(1, 5, Tenant.TenantStatus.ACTIVE, "test");

            assertThat(response.getTenants()).hasSize(1);
            assertThat(response.getPage()).isEqualTo(1);
            assertThat(response.getSize()).isEqualTo(5);
            assertThat(response.getTotalElements()).isEqualTo(11);
            assertThat(response.getTotalPages()).isEqualTo(3);
        }
    }

    // ── getTenant Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("getTenant()")
    class GetTenant {

        @Test
        @DisplayName("getTenant_found_returnsTenantResponse")
        void getTenant_found_returnsTenantResponse() {
            // Arrange
            Tenant tenant = buildTenant();
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(userRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(java.util.Collections.emptyList());
            when(listingRepository.findIdsByTenantId(TEST_TENANT_ID)).thenReturn(java.util.Collections.emptyList());

            // Act
            AdminDto.TenantResponse response = adminService.getTenant(TEST_TENANT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getTenantId()).isEqualTo(TEST_TENANT_ID);
            assertThat(response.getName()).isEqualTo("Test Tenant");
        }

        @Test
        @DisplayName("getTenant_notFound_throwsException")
        void getTenant_notFound_throwsException() {
            // Arrange
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> adminService.getTenant(TEST_TENANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException bex = (BusinessException) ex;
                        assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_2000);
                        assertThat(bex.getMessage()).contains("Tenant not found");
                    });
        }
    }

    // ── Platform Stats Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("getPlatformStats()")
    class GetPlatformStats {

        @Test
        @DisplayName("getPlatformStats_returnsStats")
        void getPlatformStats_returnsStats() {
            // Arrange
            when(tenantRepository.count()).thenReturn(10L);
            when(tenantRepository.findAll()).thenReturn(java.util.List.of(buildTenant()));
            when(userRepository.count()).thenReturn(50L);
            when(listingRepository.count()).thenReturn(100L);
            when(orderRepository.count()).thenReturn(200L);

            // Act
            AdminDto.PlatformStatsResponse response = adminService.getPlatformStats();

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getTotalTenants()).isEqualTo(10);
            assertThat(response.getTotalUsers()).isEqualTo(50);
            assertThat(response.getTotalListings()).isEqualTo(100);
            assertThat(response.getTotalOrders()).isEqualTo(200);
            // GMV = orders * 1500
            assertThat(response.getTotalPlatformGMV()).isEqualTo(java.math.BigDecimal.valueOf(300000));
        }
    }

    // ── deleteFeatureToggle Tests ─────────────────────────────────────

    @Nested
    @DisplayName("deleteFeatureToggle()")
    class DeleteFeatureToggle {

        @Test
        @DisplayName("deleteFeatureToggle_deletesSuccessfully")
        void deleteFeatureToggle_deletesSuccessfully() {
            // Act
            adminService.deleteFeatureToggle(TEST_TENANT_ID, "SOME_FEATURE");

            // Assert
            verify(featureToggleRepository).deleteByTenantIdAndFeatureKey(TEST_TENANT_ID, "SOME_FEATURE");
        }
    }

    // ── getUsers Tests（Sprint 64 US-002）────────────────────────────────

    @Nested
    @DisplayName("getUsers()")
    class GetUsers {

        private com.nextkey.ecommerce.domain.model.user.User buildUser(String email, String fullName) {
            return com.nextkey.ecommerce.domain.model.user.User.builder()
                    .id(UUID.randomUUID())
                    .email(email)
                    .fullName(fullName)
                    .role(com.nextkey.ecommerce.domain.model.user.User.UserRole.BUYER)
                    .status("ACTIVE")
                    .build();
        }

        @Test
        @DisplayName("getUsers_returnsPagedResult")
        void getUsers_returnsPagedResult() {
            com.nextkey.ecommerce.domain.model.user.User user = buildUser("buyer@example.com", "Buyer One");
            when(userRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<
                            com.nextkey.ecommerce.domain.model.user.User>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(user)));

            AdminDto.UserListResponse response = adminService.getUsers(0, 20, null, null);

            assertThat(response.getUsers()).hasSize(1);
            assertThat(response.getUsers().get(0).getEmail()).isEqualTo("buyer@example.com");
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getPage()).isEqualTo(0);
            assertThat(response.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("getUsers_withStatusAndKeywordFilters_returnsPagedResult")
        void getUsers_withStatusAndKeywordFilters_returnsPagedResult() {
            when(userRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<
                            com.nextkey.ecommerce.domain.model.user.User>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(
                            java.util.List.of(),
                            org.springframework.data.domain.PageRequest.of(2, 10), 25));

            AdminDto.UserListResponse response =
                    adminService.getUsers(2, 10, TEST_TENANT_ID, "SELLER", "ACTIVE", "buyer");

            assertThat(response.getUsers()).isEmpty();
            assertThat(response.getPage()).isEqualTo(2);
            assertThat(response.getTotalElements()).isEqualTo(25);
            assertThat(response.getTotalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("getUsers_noResults_returnsEmptyList")
        void getUsers_noResults_returnsEmptyList() {
            when(userRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<
                            com.nextkey.ecommerce.domain.model.user.User>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList()));

            AdminDto.UserListResponse response = adminService.getUsers(0, 20, null, null);

            assertThat(response.getUsers()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }

    // ── getAuditLogs Tests（Sprint 61 US-001）────────────────────────────

    @Nested
    @DisplayName("getAuditLogs()")
    class GetAuditLogs {

        private AuditLog buildAuditLog() {
            return AuditLog.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TEST_TENANT_ID)
                    .action("TENANT_APPROVED")
                    .entityType("TENANT")
                    .entityId(TEST_TENANT_ID)
                    .createdAt(Instant.parse("2026-06-01T00:00:00Z"))
                    .build();
        }

        @Test
        @DisplayName("getAuditLogs_returnsPagedResult")
        void getAuditLogs_returnsPagedResult() {
            // Arrange
            Page<AuditLog> page = new PageImpl<>(java.util.List.of(buildAuditLog()), PageRequest.of(0, 20), 1);
            when(auditLogRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditLog>>any(),
                    any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

            // Act
            AdminDto.AuditLogListResponse response = adminService.getAuditLogs(0, 20, null, null, null);

            // Assert
            assertThat(response.getLogs()).hasSize(1);
            assertThat(response.getLogs().get(0).getAction()).isEqualTo("TENANT_APPROVED");
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getTotalPages()).isEqualTo(1);
            assertThat(response.getPage()).isEqualTo(0);
            assertThat(response.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("getAuditLogs_withFilters_returnsFilteredPage")
        void getAuditLogs_withFilters_returnsFilteredPage() {
            // Arrange：驗證篩選條件存在時 service 仍正確組裝分頁結果（Specification 內容由
            // JpaSpecificationExecutor 實際查詢時套用，屬 repository 層職責，這裡專注驗證
            // service 對 repository 回傳值的映射邏輯）
            Page<AuditLog> page = new PageImpl<>(java.util.Collections.emptyList());
            when(auditLogRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditLog>>any(),
                    any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

            // Act
            Instant start = Instant.parse("2026-06-01T00:00:00Z");
            Instant end = Instant.parse("2026-06-30T23:59:59Z");
            AdminDto.AuditLogListResponse response = adminService.getAuditLogs(0, 20, "TENANT_APPROVED", start, end);

            // Assert
            assertThat(response.getLogs()).isEmpty();
            verify(auditLogRepository).findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditLog>>any(),
                    any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        @DisplayName("getAuditLogs_noResults_returnsEmptyList")
        void getAuditLogs_noResults_returnsEmptyList() {
            // Arrange
            when(auditLogRepository.findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AuditLog>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new PageImpl<>(java.util.Collections.emptyList()));

            // Act
            AdminDto.AuditLogListResponse response = adminService.getAuditLogs(0, 20, null, null, null);

            // Assert
            assertThat(response.getLogs()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }
}