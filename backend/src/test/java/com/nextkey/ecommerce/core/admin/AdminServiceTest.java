package com.nextkey.ecommerce.core.admin;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.domain.model.audit.AuditLog;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.domain.repository.audit.AuditLogRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomCalendarRepository roomCalendarRepository;

    @Mock
    private TenantApplicationRepository tenantApplicationRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

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

    // ── reviewTenant Tests（DEF-106：稽核日誌覆蓋率）────────────────────

    @Nested
    @DisplayName("reviewTenant()")
    class ReviewTenant {

        @Test
        @DisplayName("reviewTenant_approve_transitionsToActiveAndRecordsAudit")
        void reviewTenant_approve_transitionsToActiveAndRecordsAudit() {
            Tenant tenant = buildTenant();
            tenant.setStatus(Tenant.TenantStatus.PENDING_REVIEW);
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminDto.TenantReviewRequest request = AdminDto.TenantReviewRequest.builder()
                    .tenantId(TEST_TENANT_ID)
                    .decision("APPROVE")
                    .build();

            AdminDto.TenantReviewResponse response = adminService.reviewTenant(request);

            assertThat(response.getStatus()).isEqualTo("ACTIVE");

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditCaptor.capture());
            AuditLog saved = auditCaptor.getValue();
            assertThat(saved.getAction()).isEqualTo("TENANT_APPROVED");
            assertThat(saved.getEntityType()).isEqualTo("TENANT");
            assertThat(saved.getEntityId()).isEqualTo(TEST_TENANT_ID);
            assertThat(saved.getOldValue()).isEqualTo("PENDING_REVIEW");
            assertThat(saved.getNewValue()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("reviewTenant_reject_transitionsToRejectedAndRecordsAudit")
        void reviewTenant_reject_transitionsToRejectedAndRecordsAudit() {
            Tenant tenant = buildTenant();
            tenant.setStatus(Tenant.TenantStatus.PENDING_REVIEW);
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminDto.TenantReviewRequest request = AdminDto.TenantReviewRequest.builder()
                    .tenantId(TEST_TENANT_ID)
                    .decision("REJECT")
                    .build();

            AdminDto.TenantReviewResponse response = adminService.reviewTenant(request);

            assertThat(response.getStatus()).isEqualTo("REJECTED");

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAction()).isEqualTo("TENANT_REJECTED");
            assertThat(auditCaptor.getValue().getOldValue()).isEqualTo("PENDING_REVIEW");
            assertThat(auditCaptor.getValue().getNewValue()).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("reviewTenant_invalidDecision_throwsAndNeverSavesOrAudits")
        void reviewTenant_invalidDecision_throwsAndNeverSavesOrAudits() {
            Tenant tenant = buildTenant();
            tenant.setStatus(Tenant.TenantStatus.PENDING_REVIEW);
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));

            AdminDto.TenantReviewRequest request = AdminDto.TenantReviewRequest.builder()
                    .tenantId(TEST_TENANT_ID)
                    .decision("MAYBE")
                    .build();

            assertThatThrownBy(() -> adminService.reviewTenant(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_9000));

            verify(tenantRepository, never()).save(any());
            verify(auditLogRepository, never()).save(any());
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

        @Test
        @DisplayName("getPlatformStats_pendingReviewsCountsApplicationsNotTenants")
        void getPlatformStats_pendingReviewsCountsApplicationsNotTenants() {
            // 刻意讓「租戶」那一側也存在一筆 PENDING_REVIEW 記錄：若待審核數被改回以
            // Tenant.status 計數，這個測試會拿到 1 而不是 3，直接擋下回歸（DEF-059）。
            Tenant pendingTenant = Tenant.builder()
                    .id(TEST_TENANT_ID)
                    .name("Pending Tenant")
                    .slug("pending-tenant")
                    .status(Tenant.TenantStatus.PENDING_REVIEW)
                    .build();
            when(tenantRepository.count()).thenReturn(2L);
            when(tenantRepository.findAll()).thenReturn(java.util.List.of(buildTenant(), pendingTenant));
            when(tenantApplicationRepository.countByStatus(TenantApplication.ApplicationStatus.PENDING))
                    .thenReturn(3L);

            // Act
            AdminDto.PlatformStatsResponse response = adminService.getPlatformStats();

            // Assert：待審核數來自 tenant_applications，與 tenants 表的狀態無關
            assertThat(response.getPendingTenantReviews()).isEqualTo(3);
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

    // ── Purchase Order Approval Tests（Sprint 85，PRD §6.7.2）────────────

    @Nested
    @DisplayName("採購單審批（approvePurchaseOrder / rejectPurchaseOrder / getPendingApprovalPurchaseOrders）")
    class PurchaseOrderApproval {

        private static final UUID PO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
        private static final UUID REVIEWER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

        @BeforeEach
        void setUp() {
            TenantContext.setCurrentUser(REVIEWER_ID);
        }

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        private PurchaseOrder buildPendingApprovalPo() {
            return PurchaseOrder.builder()
                    .id(PO_ID)
                    .tenantId(TEST_TENANT_ID)
                    .poNumber("PO-20260709-100001")
                    .status(PurchaseOrder.POStatus.PENDING_APPROVAL)
                    .totalAmount(BigDecimal.valueOf(50000))
                    .currency("TWD")
                    .submittedAt(Instant.parse("2026-07-09T00:00:00Z"))
                    .build();
        }

        @Test
        @DisplayName("approvePurchaseOrder：PENDING_APPROVAL → APPROVED，寫入審批人與稽核紀錄")
        void approvePurchaseOrder_pendingApproval_transitionsToApproved() {
            PurchaseOrder po = buildPendingApprovalPo();
            when(purchaseOrderRepository.findById(PO_ID)).thenReturn(Optional.of(po));
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminDto.PurchaseOrderSummaryResponse response = adminService.approvePurchaseOrder(PO_ID);

            assertThat(response.getStatus()).isEqualTo("APPROVED");
            assertThat(response.getReviewedBy()).isEqualTo(REVIEWER_ID);
            assertThat(response.getReviewedAt()).isNotNull();

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAction()).isEqualTo("PURCHASE_ORDER_APPROVED");
            assertThat(auditCaptor.getValue().getTenantId()).isEqualTo(TEST_TENANT_ID);
        }

        @Test
        @DisplayName("approvePurchaseOrder：非 PENDING_APPROVAL 狀態拋出 E_7002")
        void approvePurchaseOrder_nonPendingApprovalStatus_throwsE7002() {
            PurchaseOrder po = buildPendingApprovalPo();
            po.setStatus(PurchaseOrder.POStatus.DRAFT);
            when(purchaseOrderRepository.findById(PO_ID)).thenReturn(Optional.of(po));

            assertThatThrownBy(() -> adminService.approvePurchaseOrder(PO_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
            verify(purchaseOrderRepository, never()).save(any());
        }

        @Test
        @DisplayName("approvePurchaseOrder：採購單不存在拋出 E_7001")
        void approvePurchaseOrder_notFound_throwsE7001() {
            when(purchaseOrderRepository.findById(PO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.approvePurchaseOrder(PO_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7001));
        }

        @Test
        @DisplayName("rejectPurchaseOrder：PENDING_APPROVAL → REJECTED，寫入駁回原因與稽核紀錄")
        void rejectPurchaseOrder_pendingApproval_transitionsToRejected() {
            PurchaseOrder po = buildPendingApprovalPo();
            when(purchaseOrderRepository.findById(PO_ID)).thenReturn(Optional.of(po));
            when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            AdminDto.PurchaseOrderRejectRequest request = AdminDto.PurchaseOrderRejectRequest.builder()
                    .reason("超出年度採購預算").build();

            AdminDto.PurchaseOrderSummaryResponse response = adminService.rejectPurchaseOrder(PO_ID, request);

            assertThat(response.getStatus()).isEqualTo("REJECTED");
            assertThat(response.getRejectionReason()).isEqualTo("超出年度採購預算");
            assertThat(response.getReviewedBy()).isEqualTo(REVIEWER_ID);

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAction()).isEqualTo("PURCHASE_ORDER_REJECTED");
            assertThat(auditCaptor.getValue().getReason()).isEqualTo("超出年度採購預算");
        }

        @Test
        @DisplayName("rejectPurchaseOrder：非 PENDING_APPROVAL 狀態拋出 E_7002")
        void rejectPurchaseOrder_nonPendingApprovalStatus_throwsE7002() {
            PurchaseOrder po = buildPendingApprovalPo();
            po.setStatus(PurchaseOrder.POStatus.APPROVED);
            when(purchaseOrderRepository.findById(PO_ID)).thenReturn(Optional.of(po));

            AdminDto.PurchaseOrderRejectRequest request = AdminDto.PurchaseOrderRejectRequest.builder()
                    .reason("重複審批").build();

            assertThatThrownBy(() -> adminService.rejectPurchaseOrder(PO_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7002));
        }

        @Test
        @DisplayName("getPendingApprovalPurchaseOrders：跨租戶查詢待審清單（不做租戶篩選）")
        void getPendingApprovalPurchaseOrders_returnsAcrossTenants() {
            PurchaseOrder poOfOtherTenant = PurchaseOrder.builder()
                    .id(UUID.randomUUID())
                    .tenantId(UUID.randomUUID())
                    .poNumber("PO-20260709-200002")
                    .status(PurchaseOrder.POStatus.PENDING_APPROVAL)
                    .totalAmount(BigDecimal.valueOf(80000))
                    .currency("TWD")
                    .build();
            Page<PurchaseOrder> page = new PageImpl<>(
                    java.util.List.of(buildPendingApprovalPo(), poOfOtherTenant), PageRequest.of(0, 20), 2);
            when(purchaseOrderRepository.findByStatus(eq(PurchaseOrder.POStatus.PENDING_APPROVAL), any()))
                    .thenReturn(page);

            AdminDto.PurchaseOrderPendingListResponse response = adminService.getPendingApprovalPurchaseOrders(0, 20);

            assertThat(response.getPurchaseOrders()).hasSize(2);
            assertThat(response.getPurchaseOrders())
                    .extracting(AdminDto.PurchaseOrderSummaryResponse::getTenantId)
                    .containsExactlyInAnyOrder(TEST_TENANT_ID, poOfOtherTenant.getTenantId());
            assertThat(response.getTotalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getMaintenanceWarnings（PRD §5.5.3，Sprint 96）")
    class MaintenanceWarningsTests {

        private RoomCalendar maintenanceCalendar(UUID bookingId) {
            return RoomCalendar.builder()
                    .status(RoomCalendar.RoomCalendarStatus.MAINTENANCE)
                    .bookingId(bookingId)
                    .build();
        }

        @Test
        @DisplayName("入住日期在明日以內 → urgent=true")
        void getMaintenanceWarnings_checkInWithinWindow_marksUrgent() {
            UUID bookingId = UUID.randomUUID();
            Booking booking = Booking.builder()
                    .id(bookingId)
                    .checkInDate(LocalDate.now())
                    .guestEmail("guest@example.com")
                    .build();
            when(roomCalendarRepository.findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus.MAINTENANCE))
                    .thenReturn(List.of(maintenanceCalendar(bookingId)));
            when(bookingRepository.findAllById(List.of(bookingId))).thenReturn(List.of(booking));

            AdminDto.MaintenanceWarningListResponse response = adminService.getMaintenanceWarnings();

            assertThat(response.getWarnings()).hasSize(1);
            assertThat(response.getWarnings().get(0).getBookingId()).isEqualTo(bookingId);
            assertThat(response.getWarnings().get(0).getGuestEmail()).isEqualTo("guest@example.com");
            assertThat(response.getWarnings().get(0).isUrgent()).isTrue();
        }

        @Test
        @DisplayName("入住日期在一週後 → urgent=false")
        void getMaintenanceWarnings_checkInFarAway_notUrgent() {
            UUID bookingId = UUID.randomUUID();
            Booking booking = Booking.builder()
                    .id(bookingId)
                    .checkInDate(LocalDate.now().plusDays(7))
                    .guestEmail("guest2@example.com")
                    .build();
            when(roomCalendarRepository.findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus.MAINTENANCE))
                    .thenReturn(List.of(maintenanceCalendar(bookingId)));
            when(bookingRepository.findAllById(List.of(bookingId))).thenReturn(List.of(booking));

            AdminDto.MaintenanceWarningListResponse response = adminService.getMaintenanceWarnings();

            assertThat(response.getWarnings().get(0).isUrgent()).isFalse();
        }

        @Test
        @DisplayName("同一 Booking 跨多天 MAINTENANCE 日期 → 僅回傳一筆（去重）")
        void getMaintenanceWarnings_multiNightBooking_deduplicates() {
            UUID bookingId = UUID.randomUUID();
            Booking booking = Booking.builder()
                    .id(bookingId)
                    .checkInDate(LocalDate.now().plusDays(3))
                    .guestEmail("guest3@example.com")
                    .build();
            when(roomCalendarRepository.findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus.MAINTENANCE))
                    .thenReturn(List.of(maintenanceCalendar(bookingId), maintenanceCalendar(bookingId)));
            when(bookingRepository.findAllById(List.of(bookingId))).thenReturn(List.of(booking));

            AdminDto.MaintenanceWarningListResponse response = adminService.getMaintenanceWarnings();

            assertThat(response.getWarnings()).hasSize(1);
        }

        @Test
        @DisplayName("無 MAINTENANCE 日期 → 回傳空清單")
        void getMaintenanceWarnings_noneUnderMaintenance_returnsEmptyList() {
            when(roomCalendarRepository.findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus.MAINTENANCE))
                    .thenReturn(List.of());

            AdminDto.MaintenanceWarningListResponse response = adminService.getMaintenanceWarnings();

            assertThat(response.getWarnings()).isEmpty();
            verify(bookingRepository, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("TenantApplication 審核（PRD §7.4.1，Sprint 97）")
    class TenantApplicationReviewTests {

        private static final UUID APPLICATION_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655440010");
        private static final UUID APPLICANT_USER_ID = UUID.fromString("880e8400-e29b-41d4-a716-446655440011");
        private static final UUID REVIEWER_ID = UUID.fromString("990e8400-e29b-41d4-a716-446655440012");

        private TenantApplication buildPendingApplication() {
            return TenantApplication.builder()
                    .id(APPLICATION_ID)
                    .userId(APPLICANT_USER_ID)
                    .storeName("阿明的雜貨店")
                    .businessType("RETAIL_ONLY")
                    .status(TenantApplication.ApplicationStatus.PENDING)
                    .build();
        }

        @Test
        @DisplayName("getPendingTenantApplications：回傳 PENDING 狀態的申請清單")
        void getPendingTenantApplications_returnsSummaries() {
            when(tenantApplicationRepository.findByStatus(TenantApplication.ApplicationStatus.PENDING))
                    .thenReturn(java.util.List.of(buildPendingApplication()));

            AdminDto.TenantApplicationListResponse response = adminService.getPendingTenantApplications();

            assertThat(response.getApplications()).hasSize(1);
            assertThat(response.getApplications().get(0).getStoreName()).isEqualTo("阿明的雜貨店");
        }

        @Test
        @DisplayName("approveTenantApplication：建立 ACTIVE Tenant、StoreOwner 成員，申請狀態變 APPROVED")
        void approveTenantApplication_success_createsTenantAndStoreOwner() {
            TenantApplication application = buildPendingApplication();
            when(tenantApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
            when(tenantRepository.existsBySlug(any())).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
                Tenant t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            when(tenantRepository.findById(any())).thenAnswer(inv -> Optional.of(
                    Tenant.builder().id(inv.getArgument(0)).build()));
            when(tenantApplicationRepository.save(any(TenantApplication.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            com.nextkey.ecommerce.domain.model.user.User applicantUser =
                    com.nextkey.ecommerce.domain.model.user.User.builder()
                            .id(APPLICANT_USER_ID)
                            .role(com.nextkey.ecommerce.domain.model.user.User.UserRole.BUYER)
                            .build();
            when(userRepository.findById(APPLICANT_USER_ID)).thenReturn(Optional.of(applicantUser));

            AdminDto.TenantApplicationApproveResponse response =
                    adminService.approveTenantApplication(APPLICATION_ID, REVIEWER_ID);

            assertThat(response.getStatus()).isEqualTo("APPROVED");
            assertThat(response.getTenantId()).isNotNull();

            verify(tenantRepository).save(argThat(t ->
                    t.getStatus() == Tenant.TenantStatus.ACTIVE && "阿明的雜貨店".equals(t.getName())));
            verify(tenantMemberRepository).save(argThat(m ->
                    m.getStoreRole() == TenantMember.StoreRole.STORE_OWNER
                            && APPLICANT_USER_ID.equals(m.getUserId())));
            verify(userRepository).save(argThat(u ->
                    u.getRole() == com.nextkey.ecommerce.domain.model.user.User.UserRole.STORE_OWNER));
            verify(tenantApplicationRepository).save(argThat(a ->
                    a.getStatus() == TenantApplication.ApplicationStatus.APPROVED
                            && a.getReviewedBy().equals(REVIEWER_ID)
                            && a.getTenantId() != null));
        }

        @Test
        @DisplayName("approveTenantApplication：申請不存在 → E-2006")
        void approveTenantApplication_notFound_throwsE2006() {
            when(tenantApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.approveTenantApplication(APPLICATION_ID, REVIEWER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2006));
        }

        @Test
        @DisplayName("approveTenantApplication：非 PENDING 狀態 → E-2007")
        void approveTenantApplication_notPending_throwsE2007() {
            TenantApplication application = buildPendingApplication();
            application.setStatus(TenantApplication.ApplicationStatus.APPROVED);
            when(tenantApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> adminService.approveTenantApplication(APPLICATION_ID, REVIEWER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2007));

            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("approveTenantApplication：Guest 申請（userId 為 null）→ E-2008")
        void approveTenantApplication_guestApplication_throwsE2008() {
            TenantApplication application = buildPendingApplication();
            application.setUserId(null);
            when(tenantApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> adminService.approveTenantApplication(APPLICATION_ID, REVIEWER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2008));

            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejectTenantApplication：成功駁回，不建立 Tenant")
        void rejectTenantApplication_success_doesNotCreateTenant() {
            TenantApplication application = buildPendingApplication();
            when(tenantApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
            when(tenantApplicationRepository.save(any(TenantApplication.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AdminDto.TenantApplicationRejectRequest request = AdminDto.TenantApplicationRejectRequest.builder()
                    .reason("資料不完整").build();

            AdminDto.TenantApplicationRejectResponse response =
                    adminService.rejectTenantApplication(APPLICATION_ID, REVIEWER_ID, request);

            assertThat(response.getStatus()).isEqualTo("REJECTED");
            verify(tenantRepository, never()).save(any());
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejectTenantApplication：非 PENDING 狀態 → E-2007")
        void rejectTenantApplication_notPending_throwsE2007() {
            TenantApplication application = buildPendingApplication();
            application.setStatus(TenantApplication.ApplicationStatus.REJECTED);
            when(tenantApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

            AdminDto.TenantApplicationRejectRequest request = AdminDto.TenantApplicationRejectRequest.builder()
                    .reason("重複審核").build();

            assertThatThrownBy(() -> adminService.rejectTenantApplication(APPLICATION_ID, REVIEWER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2007));
        }
    }
}