package com.nextkey.ecommerce.core.tenant;

import com.nextkey.ecommerce.api.dto.*;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenantService 單元測試
 *
 * 測試範圍：
 * - createApplication: 店鋪申請
 * - getTenantDetails: 取得店鋪詳情（PENDING/APPROVED 區分）
 * - updateTenant: 更新店鋪資訊（OWNER 角色檢查）
 * - getFeatureToggles: 取得功能開關
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TenantService 單元測試")
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantApplicationRepository tenantApplicationRepository;

    @Mock
    private TenantFeatureToggleRepository tenantFeatureToggleRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TenantService tenantService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    // ── createApplication Tests ─────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("createApplication: Guest 用戶（userId=null）可以成功申請")
    void createApplication_guestUser_shouldSucceed() {
        // Arrange
        TenantApplicationRequest request = TenantApplicationRequest.builder()
                .storeName("Test Store")
                .businessType("RETAIL_ONLY")
                .contactEmail("test@example.com")
                .build();

        TenantApplication savedApplication = TenantApplication.builder()
                .id(UUID.randomUUID())
                .userId(null)
                .storeName("Test Store")
                .businessType("RETAIL_ONLY")
                .status(TenantApplication.ApplicationStatus.PENDING)
                .submittedAt(Instant.now())
                .build();

        when(tenantApplicationRepository.save(any(TenantApplication.class)))
                .thenReturn(savedApplication);

        // Act
        TenantApplicationResponse response = tenantService.createApplication(request, null);

        // Assert
        assertNotNull(response);
        assertEquals("Test Store", response.getStoreName());
        assertEquals("PENDING", response.getStatus());
        verify(tenantApplicationRepository, times(1)).save(any(TenantApplication.class));
    }

    @Test
    @Order(2)
    @DisplayName("createApplication: 已認證用戶可以成功申請")
    void createApplication_authenticatedUser_shouldSucceed() {
        // Arrange
        TenantApplicationRequest request = TenantApplicationRequest.builder()
                .storeName("Authenticated Store")
                .businessType("RETAIL_ONLY")
                .contactEmail("auth@example.com")
                .build();

        TenantApplication savedApplication = TenantApplication.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .storeName("Authenticated Store")
                .businessType("RETAIL_ONLY")
                .status(TenantApplication.ApplicationStatus.PENDING)
                .submittedAt(Instant.now())
                .build();

        when(tenantApplicationRepository.existsByUserIdAndStatusIn(eq(TEST_USER_ID), anyList()))
                .thenReturn(false);
        when(tenantApplicationRepository.save(any(TenantApplication.class)))
                .thenReturn(savedApplication);

        // Act
        TenantApplicationResponse response = tenantService.createApplication(request, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        assertEquals("Authenticated Store", response.getStoreName());
        verify(tenantApplicationRepository, times(1)).existsByUserIdAndStatusIn(eq(TEST_USER_ID), anyList());
    }

    @Test
    @Order(3)
    @DisplayName("createApplication: 已認證用戶有 pending 申請時拋出異常")
    void createApplication_duplicatePending_shouldThrowException() {
        // Arrange
        TenantApplicationRequest request = TenantApplicationRequest.builder()
                .storeName("Duplicate Store")
                .businessType("RETAIL_ONLY")
                .contactEmail("dup@example.com")
                .build();

        when(tenantApplicationRepository.existsByUserIdAndStatusIn(eq(TEST_USER_ID), anyList()))
                .thenReturn(true);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> tenantService.createApplication(request, TEST_USER_ID));
        assertTrue(exception.getMessage().contains("Store application already exists"));
    }

    // ── updateTenant Tests ──────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("updateTenant: STORE_OWNER 角色可以更新店鋪")
    void updateTenant_ownerRole_shouldSucceed() {
        // Arrange
        UUID tenantId = TEST_TENANT_ID;
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .storeName("Updated Store Name")
                .build();

        Tenant existingTenant = Tenant.builder()
                .id(tenantId)
                .name("Original Store")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                eq(tenantId), eq(TEST_USER_ID), eq(TenantMember.StoreRole.STORE_OWNER)))
                .thenReturn(true);
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(existingTenant));
        when(tenantRepository.save(any(Tenant.class)))
                .thenReturn(existingTenant);

        // Act
        TenantUpdateResponse response = tenantService.updateTenant(tenantId, request, TEST_USER_ID);

        // Assert
        assertNotNull(response);
        verify(tenantMemberRepository, times(1))
                .existsByTenantIdAndUserIdAndStoreRole(tenantId, TEST_USER_ID, TenantMember.StoreRole.STORE_OWNER);
    }

    @Test
    @Order(4)
    @DisplayName("updateTenant：StoreOwner 可設定採購審批金額上限（Sprint 85，PRD §6.7.2）")
    void updateTenant_setsPurchaseOrderApprovalThreshold() {
        UUID tenantId = TEST_TENANT_ID;
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .purchaseOrderApprovalThreshold(java.math.BigDecimal.valueOf(100000))
                .build();

        Tenant existingTenant = Tenant.builder()
                .id(tenantId)
                .name("Original Store")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                eq(tenantId), eq(TEST_USER_ID), eq(TenantMember.StoreRole.STORE_OWNER)))
                .thenReturn(true);
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(existingTenant));
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenantUpdateResponse response = tenantService.updateTenant(tenantId, request, TEST_USER_ID);

        assertEquals(java.math.BigDecimal.valueOf(100000), response.getPurchaseOrderApprovalThreshold());
        assertEquals(java.math.BigDecimal.valueOf(100000), existingTenant.getPurchaseOrderApprovalThreshold());
    }

    @Test
    @Order(5)
    @DisplayName("updateTenant: STORE_STAFF 角色不能更新店鋪")
    void updateTenant_staffRole_shouldThrowException() {
        // Arrange
        UUID tenantId = TEST_TENANT_ID;
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .storeName("Updated Store Name")
                .build();

        when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                eq(tenantId), eq(TEST_USER_ID), eq(TenantMember.StoreRole.STORE_OWNER)))
                .thenReturn(false);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> tenantService.updateTenant(tenantId, request, TEST_USER_ID));
        assertTrue(exception.getMessage().contains("Not authorized"));
    }

    // ── getTenantDetails Tests ──────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("getTenantDetails: PENDING_REVIEW 狀態只返回基本資訊")
    void getTenantDetails_pendingStatus_returnsLimitedInfo() {
        // Arrange
        UUID tenantId = TEST_TENANT_ID;
        Tenant pendingTenant = Tenant.builder()
                .id(tenantId)
                .name("Pending Store")
                .status(Tenant.TenantStatus.PENDING_REVIEW)
                .description("Full description here")
                .contactEmail("contact@example.com")
                .logoUrl("http://logo.url")
                .metadata(Map.of("businessType", "RETAIL_ONLY"))
                .build();

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(pendingTenant));

        // Act
        TenantDetailsResponse response = tenantService.getTenantDetails(tenantId);

        // Assert
        assertNotNull(response);
        assertEquals("Pending Store", response.getStoreName());
        assertEquals("PENDING_REVIEW", response.getStatus());
        assertNull(response.getStoreDescription()); // Should be null for pending
        assertNull(response.getContactEmail());     // Should be null for pending
        assertNull(response.getLogoUrl());          // Should be null for pending
        assertNull(response.getMember());           // Should be null for pending
    }

    @Test
    @Order(7)
    @DisplayName("getTenantDetails: ACTIVE 狀態返回完整資訊")
    void getTenantDetails_activeStatus_returnsFullInfo() {
        // Arrange
        UUID tenantId = TEST_TENANT_ID;
        Tenant activeTenant = Tenant.builder()
                .id(tenantId)
                .name("Active Store")
                .status(Tenant.TenantStatus.ACTIVE)
                .description("Full description here")
                .contactEmail("contact@example.com")
                .logoUrl("http://logo.url")
                .metadata(Map.of("businessType", "RETAIL_ONLY"))
                .build();

        TenantMember ownerMember = TenantMember.builder()
                .tenantId(tenantId)
                .userId(TEST_USER_ID)
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .joinedAt(Instant.now())
                .build();

        com.nextkey.ecommerce.domain.model.user.User owner = com.nextkey.ecommerce.domain.model.user.User.builder()
                .id(TEST_USER_ID)
                .fullName("Store Owner")
                .avatarUrl("http://avatar.url")
                .build();

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(activeTenant));
        when(tenantMemberRepository.findByTenantId(tenantId))
                .thenReturn(List.of(ownerMember));
        when(userRepository.findById(TEST_USER_ID))
                .thenReturn(Optional.of(owner));

        // Act
        TenantDetailsResponse response = tenantService.getTenantDetails(tenantId);

        // Assert
        assertNotNull(response);
        assertEquals("Active Store", response.getStoreName());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("Full description here", response.getStoreDescription()); // Should have full info
        assertEquals("contact@example.com", response.getContactEmail());        // Should have full info
        assertNotNull(response.getMember());                                    // Should have member info
        assertEquals("Store Owner", response.getMember().getDisplayName());
    }
}