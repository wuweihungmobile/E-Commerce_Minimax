package com.nextkey.ecommerce.core.tenant;

import com.nextkey.ecommerce.api.dto.*;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.domain.model.cms.post.Post;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.domain.repository.cms.PostRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @Mock
    private AuditService auditService;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private PostRepository postRepository;

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

        when(tenantApplicationRepository.saveAndFlush(any(TenantApplication.class)))
                .thenReturn(savedApplication);

        // Act
        TenantApplicationResponse response = tenantService.createApplication(request, null);

        // Assert
        assertNotNull(response);
        assertEquals("Test Store", response.getStoreName());
        assertEquals("PENDING", response.getStatus());
        verify(tenantApplicationRepository, times(1)).saveAndFlush(any(TenantApplication.class));
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
        when(tenantApplicationRepository.saveAndFlush(any(TenantApplication.class)))
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

    @Test
    @Order(4)
    @DisplayName("createApplication: 併發送出時 existsByUserIdAndStatusIn 快照通過，但唯一索引搶輸 → 拋出 E_4092")
    void createApplication_concurrentDuplicate_throwsE4092() {
        // 🔴 Sprint 137 DEF-141：模擬同一使用者幾乎同時送出兩次申請，都通過「快照檢查目前沒有 PENDING
        // 申請」，但只有一邊真正搶到 V80 新增的部分唯一索引，另一邊 saveAndFlush 拋出違反約束例外。
        TenantApplicationRequest request = TenantApplicationRequest.builder()
                .storeName("Racing Store")
                .businessType("RETAIL_ONLY")
                .contactEmail("racing@example.com")
                .build();

        when(tenantApplicationRepository.existsByUserIdAndStatusIn(eq(TEST_USER_ID), anyList()))
                .thenReturn(false);
        when(tenantApplicationRepository.saveAndFlush(any(TenantApplication.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> tenantService.createApplication(request, TEST_USER_ID));
        assertEquals(com.nextkey.ecommerce.shared.exception.ErrorCode.E_4092, exception.getErrorCode());
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
    @Order(4)
    @DisplayName("updateTenant：businessType 先前完全未被 applyTenantUpdates 讀取，本輪補上寫入 metadata（Sprint 146）")
    void updateTenant_writesBusinessTypeToMetadata() {
        UUID tenantId = TEST_TENANT_ID;
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .businessType("BOOKING_ONLY")
                .build();

        Tenant existingTenant = Tenant.builder()
                .id(tenantId)
                .name("Original Store")
                .status(Tenant.TenantStatus.ACTIVE)
                .metadata(Map.of("businessType", "RETAIL_ONLY"))
                .build();

        when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                eq(tenantId), eq(TEST_USER_ID), eq(TenantMember.StoreRole.STORE_OWNER)))
                .thenReturn(true);
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(existingTenant));
        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        tenantService.updateTenant(tenantId, request, TEST_USER_ID);

        assertEquals("BOOKING_ONLY", existingTenant.getMetadata().get("businessType"));
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
                .contactPhone("+886-912345678")
                .logoUrl("http://logo.url")
                .purchaseOrderApprovalThreshold(new BigDecimal("5000.00"))
                .metadata(Map.of("businessType", "RETAIL_ONLY"))
                .updatedAt(Instant.parse("2026-09-08T10:00:00Z"))
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
        // Sprint 146：contactPhone/updatedAt 先前完全沒有被 DTO/mapper 帶出，
        // 導致前端 TenantDetail.tsx 的「聯絡電話」「最後更新」永遠讀到 undefined
        assertEquals("+886-912345678", response.getContactPhone());
        assertEquals(Instant.parse("2026-09-08T10:00:00Z"), response.getUpdatedAt());
        assertEquals(new BigDecimal("5000.00"), response.getPurchaseOrderApprovalThreshold()); // Sprint 89: 已設定門檻須可見
        assertNotNull(response.getMember());                                    // Should have member info
        assertEquals("Store Owner", response.getMember().getDisplayName());
    }

    // ── getFeatureToggles Tests（DEF-167：數值配額不得混入開關清單）──────────────

    @Nested
    @DisplayName("getFeatureToggles()")
    class GetFeatureTogglesTests {

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("getFeatureToggles：只回傳布林功能開關，四個數值配額不得出現在清單中")
        void getFeatureToggles_excludesNumericQuotas() {
            // DEF-167：數值配額沒有 isEnabled 語意。過去它們一律以 booleanDefault(false) 回傳，
            // 使前端功能開關頁把它們渲染成四個永遠「關閉」的假開關，且按下即汙染成布林 toggle。
            // 本測試守的是「清單語意」——功能開關清單只能包含真正可切換的開關。
            when(tenantFeatureToggleRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());

            FeatureToggleResponse response = tenantService.getFeatureToggles(TEST_TENANT_ID);

            List<String> keys = response.getFeatures().stream()
                    .map(FeatureToggleResponse.FeatureInfo::getFeatureKey)
                    .toList();
            assertEquals(List.of("RETAIL_ENABLED", "BOOKING_ENABLED", "CMS_ENABLED",
                    "ERP_ENABLED", "DYNAMIC_PRICING_ENABLED", "PROMO_ENABLED"), keys);
            assertFalse(keys.contains("MAX_PRODUCTS"));
            assertFalse(keys.contains("MAX_ROOMS"));
            assertFalse(keys.contains("MAX_POSTS"));
            assertFalse(keys.contains("COMMISSION_RATE"));
        }

        @Test
        @DisplayName("getFeatureToggles：回傳前端實際讀取的 category/status/requiresAdminReview 三欄")
        void getFeatureToggles_providesFieldsFrontendActuallyReads() {
            // DEF-168：前端 features/page.tsx 讀 category（分組）、status + requiresAdminReview（徽章與
            // 審核提示）。後端過去從未提供這三欄，前端一律拿到 undefined：所有項目擠進 'other' 分組、
            // 徽章邏輯失效、「啟用後需管理員審核」提示永不出現。本測試守的是「後端必須提供前端讀的欄位」。
            when(tenantFeatureToggleRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());

            FeatureToggleResponse response = tenantService.getFeatureToggles(TEST_TENANT_ID);
            Map<String, FeatureToggleResponse.FeatureInfo> byKey = new HashMap<>();
            for (FeatureToggleResponse.FeatureInfo f : response.getFeatures()) {
                byKey.put(f.getFeatureKey(), f);
            }

            // category 必須是前端 getFeatureCategoryLabel 對照表裡的鍵，否則分組標題會退化成原始字串
            assertEquals("listing", byKey.get("RETAIL_ENABLED").getCategory());
            assertEquals("booking", byKey.get("BOOKING_ENABLED").getCategory());
            assertEquals("pricing", byKey.get("DYNAMIC_PRICING_ENABLED").getCategory());

            // requiresAdminReview 來自 FeatureDefinition.requiresApproval，需真的反映各功能設定
            assertFalse(byKey.get("RETAIL_ENABLED").getRequiresAdminReview());
            assertTrue(byKey.get("DYNAMIC_PRICING_ENABLED").getRequiresAdminReview());

            // status：預設啟用者為 ACTIVE；預設關閉且無紀錄者為 INACTIVE（尚未申請）
            assertEquals("ACTIVE", byKey.get("RETAIL_ENABLED").getStatus());
            assertEquals("INACTIVE", byKey.get("DYNAMIC_PRICING_ENABLED").getStatus());
        }

        @Test
        @DisplayName("Sprint 153（Sprint 147 §6 範圍外項目）：quotas 回傳三個數值配額的上限與目前用量，"
                + "計數口徑須與 checkQuotaNotExceeded 實際攔截條件一致（ACTIVE 商品/房源、PUBLISHED 貼文）")
        void getFeatureToggles_returnsQuotaUsageMatchingEnforcementCriteria() {
            when(tenantFeatureToggleRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());
            when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                    TEST_TENANT_ID, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE))
                    .thenReturn(37L);
            when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                    TEST_TENANT_ID, Listing.ListingType.ROOM, Listing.ListingStatus.ACTIVE))
                    .thenReturn(5L);
            when(postRepository.countByTenantIdAndStatus(TEST_TENANT_ID, Post.PostStatus.PUBLISHED))
                    .thenReturn(12L);

            FeatureToggleResponse response = tenantService.getFeatureToggles(TEST_TENANT_ID);

            Map<String, FeatureToggleResponse.QuotaInfo> byKey = new HashMap<>();
            for (FeatureToggleResponse.QuotaInfo q : response.getQuotas()) {
                byKey.put(q.getFeatureKey(), q);
            }
            assertEquals(3, byKey.size());
            assertEquals(100, byKey.get("MAX_PRODUCTS").getLimit());
            assertEquals(37L, byKey.get("MAX_PRODUCTS").getCurrentUsage());
            assertEquals(20, byKey.get("MAX_ROOMS").getLimit());
            assertEquals(5L, byKey.get("MAX_ROOMS").getCurrentUsage());
            assertEquals(50, byKey.get("MAX_POSTS").getLimit());
            assertEquals(12L, byKey.get("MAX_POSTS").getCurrentUsage());
        }
    }

    // ── getTenantsListByUser Tests（Sprint 146：getFeatureMap 與 DEF-167 同型寫法）──

    @Nested
    @DisplayName("getTenantsListByUser()")
    class GetTenantsListByUserTests {

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("getTenantsListByUser：features 只含布林功能開關，四個數值配額不得出現")
        void getTenantsListByUser_featuresExcludeNumericQuotas() {
            // Sprint 146：getFeatureMap 與 DEF-167 修復前的 getFeatureToggles 同一種寫法——
            // 把數值配額（MAX_PRODUCTS 等）以 booleanDefault(false) 塞進 Map<String, Boolean>。
            // 此端點（GET /v2/tenants/my）目前無任何前端呼叫點，但契約本身仍不應對外洩漏假布林值。
            TenantContext.setCurrentUser(TEST_USER_ID);

            TenantMember member = TenantMember.builder()
                    .tenantId(TEST_TENANT_ID)
                    .userId(TEST_USER_ID)
                    .storeRole(TenantMember.StoreRole.STORE_OWNER)
                    .build();
            Tenant tenant = Tenant.builder()
                    .id(TEST_TENANT_ID)
                    .name("Test Store")
                    .status(Tenant.TenantStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .build();

            when(tenantMemberRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of(member));
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantMemberRepository.countByTenantId(TEST_TENANT_ID)).thenReturn(1L);
            when(tenantFeatureToggleRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(List.of());

            List<TenantListResponse> result = tenantService.getTenantsListByUser();

            assertEquals(1, result.size());
            Map<String, Boolean> features = result.get(0).getFeatures();
            assertEquals(Set.of("RETAIL_ENABLED", "BOOKING_ENABLED", "CMS_ENABLED",
                    "ERP_ENABLED", "DYNAMIC_PRICING_ENABLED", "PROMO_ENABLED"), features.keySet());
            assertFalse(features.containsKey("MAX_PRODUCTS"));
            assertFalse(features.containsKey("MAX_ROOMS"));
            assertFalse(features.containsKey("MAX_POSTS"));
            assertFalse(features.containsKey("COMMISSION_RATE"));
        }
    }

    // ── updateFeatureToggle Tests（DEF-107：稽核日誌覆蓋率）───────────────────

    @Nested
    @DisplayName("updateFeatureToggle()")
    class UpdateFeatureToggleTests {

        @BeforeEach
        void setUp() {
            TenantContext.setCurrentUser(TEST_USER_ID);
        }

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("updateFeatureToggle：成功切換並記錄稽核")
        void updateFeatureToggle_success_recordsAudit() {
            when(tenantMemberRepository.existsByTenantIdAndUserId(TEST_TENANT_ID, TEST_USER_ID)).thenReturn(true);
            when(tenantFeatureToggleRepository.findByTenantIdAndFeatureKey(TEST_TENANT_ID, "RETAIL_ENABLED"))
                    .thenReturn(Optional.empty());
            when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TEST_TENANT_ID).build()));
            when(tenantFeatureToggleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            tenantService.updateFeatureToggle(TEST_TENANT_ID, "RETAIL_ENABLED", false);

            verify(auditService).record(eq("FEATURE_TOGGLE_SELF_SERVICE_UPDATED"), eq("FEATURE_TOGGLE"),
                    eq(TEST_TENANT_ID), eq(TEST_TENANT_ID),
                    eq("RETAIL_ENABLED=true"), eq("RETAIL_ENABLED=false"), isNull(), eq(TEST_USER_ID));
        }

        @Test
        @DisplayName("updateFeatureToggle：數值配額 key（MAX_PRODUCTS）→ 拒絕，不得寫入布林 toggle")
        void updateFeatureToggle_numericQuotaKey_rejected() {
            // DEF-167：MAX_PRODUCTS/MAX_ROOMS/MAX_POSTS/COMMISSION_RATE 的值存在 config JSONB 而非
            // isEnabled。若允許經布林開關 API 切換，同一個 featureKey 會同時具有數值與布林兩種語意，
            // 之後任何依 config 讀配額的程式都可能讀到一筆只有 isEnabled、沒有 config 的紀錄。
            when(tenantMemberRepository.existsByTenantIdAndUserId(TEST_TENANT_ID, TEST_USER_ID)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> tenantService.updateFeatureToggle(TEST_TENANT_ID, "MAX_PRODUCTS", true));

            // 🔴 必須斷言訊息：只斷言 BusinessException 會產生假綠燈——修復前此呼叫同樣會拋
            // BusinessException，但那是走到 buildNewFeatureToggle 時「Tenant not found」(E_2000)
            // 的無關例外，不是「數值配額不可切換」。此斷言在紅燈驗證中實際捕捉到了該假綠燈。
            assertTrue(ex.getMessage().contains("numeric quota"),
                    "應因數值配額而拒絕，實際訊息：" + ex.getMessage());

            verify(tenantFeatureToggleRepository, never()).save(any());
            verify(tenantFeatureToggleRepository, never()).saveAndFlush(any());
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("updateFeatureToggle：非店鋪成員呼叫 → 拒絕且不記錄稽核")
        void updateFeatureToggle_notMember_rejectedWithoutAudit() {
            when(tenantMemberRepository.existsByTenantIdAndUserId(TEST_TENANT_ID, TEST_USER_ID)).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> tenantService.updateFeatureToggle(TEST_TENANT_ID, "RETAIL_ENABLED", false));
            verify(tenantFeatureToggleRepository, never()).save(any());
            verify(tenantFeatureToggleRepository, never()).saveAndFlush(any());
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    // ── 邀請確認制成員管理（PRD §7.4/§8.2.3/§9.11，Sprint 98）──────────────────

    @Nested
    @DisplayName("邀請確認制成員管理（inviteMember / acceptInvite / declineInvite）")
    class MemberInviteTests {

        private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
        private static final UUID INVITEE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");

        @BeforeEach
        void setUp() {
            TenantContext.setCurrentUser(OWNER_ID);
        }

        @AfterEach
        void tearDown() {
            TenantContext.clear();
        }

        private User buildInvitee() {
            return User.builder().id(INVITEE_ID).fullName("Invitee").email("invitee@example.com").build();
        }

        @Test
        @DisplayName("inviteMember：成功建立 INVITED 狀態的成員紀錄")
        void inviteMember_success_createsInvitedRecord() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.empty());
            when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));

            TenantMemberResponse response = tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF", OWNER_ID);

            assertEquals("INVITED", response.getStatus());
            assertNull(response.getJoinedAt());
            verify(tenantMemberRepository).save(argThat(m ->
                    m.getStatus() == TenantMember.MemberStatus.INVITED
                            && m.getStoreRole() == TenantMember.StoreRole.STORE_STAFF));
            verify(auditService).record(eq("STORE_MEMBER_INVITED"), eq("TENANT_MEMBER"), any(), eq(TEST_TENANT_ID),
                    isNull(), eq("STORE_STAFF"), isNull(), eq(OWNER_ID));
        }

        @Test
        @DisplayName("inviteMember：非 StoreOwner 呼叫 → E_4031")
        void inviteMember_notOwner_throwsE4031() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF", OWNER_ID));
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("inviteMember：對象已是 ACTIVE 成員 → E_4092")
        void inviteMember_alreadyActiveMember_throwsE4092() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));
            TenantMember activeMember = TenantMember.builder()
                    .tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.ACTIVE).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(activeMember));

            assertThrows(BusinessException.class,
                    () -> tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF", OWNER_ID));
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("inviteMember：對象曾被移除（REMOVED）→ 更新既有紀錄重新邀請，而非新增")
        void inviteMember_previouslyRemoved_reusesExistingRecord() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));
            UUID existingId = UUID.randomUUID();
            TenantMember removedMember = TenantMember.builder()
                    .id(existingId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.REMOVED).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(removedMember));
            when(tenantMemberRepository.updateStatusIfCurrent(existingId,
                    TenantMember.MemberStatus.REMOVED, TenantMember.MemberStatus.INVITED)).thenReturn(1);
            when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));

            tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF", OWNER_ID);

            verify(tenantMemberRepository).save(argThat(m ->
                    existingId.equals(m.getId()) && m.getStatus() == TenantMember.MemberStatus.INVITED));
            verify(auditService).record(eq("STORE_MEMBER_INVITED"), eq("TENANT_MEMBER"), eq(existingId), eq(TEST_TENANT_ID),
                    isNull(), eq("STORE_STAFF"), isNull(), eq(OWNER_ID));
        }

        @Test
        @DisplayName("inviteMember：重新邀請時併發搶占失敗（快照仍是 REMOVED）→ E_4092，不寫入")
        void inviteMember_concurrentReinviteClaimLost_throwsE4092() {
            // 🔴 Sprint 137 DEF-165：模擬同一被移除成員被併發重新邀請兩次。
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));
            UUID existingId = UUID.randomUUID();
            TenantMember removedMember = TenantMember.builder()
                    .id(existingId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.REMOVED).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(removedMember));
            when(tenantMemberRepository.updateStatusIfCurrent(existingId,
                    TenantMember.MemberStatus.REMOVED, TenantMember.MemberStatus.INVITED)).thenReturn(0);

            assertThrows(BusinessException.class,
                    () -> tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF", OWNER_ID));
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("inviteMember：邀請角色為 STORE_OWNER → 拒絕")
        void inviteMember_roleStoreOwner_rejected() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));

            assertThrows(BusinessException.class,
                    () -> tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_OWNER", OWNER_ID));
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("acceptInvite：成功接受 → 狀態轉為 ACTIVE 並填入 joinedAt")
        void acceptInvite_success_activatesMembership() {
            TenantContext.setCurrentUser(INVITEE_ID);
            UUID memberRowId = UUID.randomUUID();
            TenantMember invited = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.INVITED).storeRole(TenantMember.StoreRole.STORE_STAFF).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(invited));
            when(tenantMemberRepository.updateStatusIfCurrent(memberRowId,
                    TenantMember.MemberStatus.INVITED, TenantMember.MemberStatus.ACTIVE)).thenReturn(1);
            when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));

            TenantMemberResponse response = tenantService.acceptInvite(TEST_TENANT_ID);

            assertEquals("ACTIVE", response.getStatus());
            assertNotNull(response.getJoinedAt());
            // Sprint 99：接受邀請需同步 User.role，否則下次登入 JWT 仍拿不到 StoreStaff 權限
            // （Sprint 97/98 對 StoreOwner 的同類修復，這裡是 StoreStaff 的孿生案例）。
            verify(userRepository).save(argThat(u -> u.getRole() == User.UserRole.STORE_STAFF));
        }

        @Test
        @DisplayName("acceptInvite：併發搶占失敗（快照仍是 INVITED，已被 declineInvite 搶先）→ E_2002")
        void acceptInvite_concurrentClaimLost_throwsE2002() {
            // 🔴 Sprint 137 DEF-140：模擬同一份邀請幾乎同時被接受與拒絕。
            TenantContext.setCurrentUser(INVITEE_ID);
            UUID memberRowId = UUID.randomUUID();
            TenantMember invited = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.INVITED).storeRole(TenantMember.StoreRole.STORE_STAFF).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(invited));
            when(tenantMemberRepository.updateStatusIfCurrent(memberRowId,
                    TenantMember.MemberStatus.INVITED, TenantMember.MemberStatus.ACTIVE)).thenReturn(0);

            assertThrows(BusinessException.class, () -> tenantService.acceptInvite(TEST_TENANT_ID));
            verify(tenantMemberRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("inviteMember：邀請角色為 STORE_MANAGER → 拒絕（User.UserRole 無對應值，接受後 JWT 永遠拿不到權限）")
        void inviteMember_roleStoreManager_rejected() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));

            assertThrows(BusinessException.class,
                    () -> tenantService.inviteMember(TEST_TENANT_ID, INVITEE_ID, "STORE_MANAGER", OWNER_ID));
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("acceptInvite：找不到邀請 → E_2002")
        void acceptInvite_noInvite_throwsE2002() {
            TenantContext.setCurrentUser(INVITEE_ID);
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> tenantService.acceptInvite(TEST_TENANT_ID));
        }

        @Test
        @DisplayName("declineInvite：成功拒絕 → 狀態轉為 REMOVED")
        void declineInvite_success_marksRemoved() {
            TenantContext.setCurrentUser(INVITEE_ID);
            UUID memberRowId = UUID.randomUUID();
            TenantMember invited = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.INVITED).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(invited));
            when(tenantMemberRepository.updateStatusIfCurrent(memberRowId,
                    TenantMember.MemberStatus.INVITED, TenantMember.MemberStatus.REMOVED)).thenReturn(1);
            when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));

            tenantService.declineInvite(TEST_TENANT_ID);

            verify(tenantMemberRepository).save(argThat(m -> m.getStatus() == TenantMember.MemberStatus.REMOVED));
        }

        @Test
        @DisplayName("declineInvite：併發搶占失敗（快照仍是 INVITED，已被 acceptInvite 搶先）→ E_2002")
        void declineInvite_concurrentClaimLost_throwsE2002() {
            // 🔴 Sprint 137 DEF-164：與 acceptInvite 同一原則，見該處說明。
            TenantContext.setCurrentUser(INVITEE_ID);
            UUID memberRowId = UUID.randomUUID();
            TenantMember invited = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.INVITED).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(invited));
            when(tenantMemberRepository.updateStatusIfCurrent(memberRowId,
                    TenantMember.MemberStatus.INVITED, TenantMember.MemberStatus.REMOVED)).thenReturn(0);

            assertThrows(BusinessException.class, () -> tenantService.declineInvite(TEST_TENANT_ID));
            verify(tenantMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("getMyPendingInvites：回傳目前使用者所有待確認邀請")
        void getMyPendingInvites_returnsInvites() {
            TenantContext.setCurrentUser(INVITEE_ID);
            TenantMember invited = TenantMember.builder()
                    .id(UUID.randomUUID()).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.INVITED).storeRole(TenantMember.StoreRole.STORE_STAFF)
                    .invitedAt(Instant.now()).build();
            when(tenantMemberRepository.findByUserIdAndStatus(INVITEE_ID, TenantMember.MemberStatus.INVITED))
                    .thenReturn(List.of(invited));
            when(tenantRepository.findById(TEST_TENANT_ID))
                    .thenReturn(Optional.of(Tenant.builder().id(TEST_TENANT_ID).name("測試店鋪").build()));

            List<TenantInviteResponse> invites = tenantService.getMyPendingInvites();

            assertEquals(1, invites.size());
            assertEquals("測試店鋪", invites.get(0).getTenantName());
        }

        @Test
        @DisplayName("removeMember：軟刪除，狀態轉為 REMOVED（保留紀錄而非硬刪除）")
        void removeMember_success_softDeletes() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            UUID memberRowId = UUID.randomUUID();
            TenantMember activeMember = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.ACTIVE).storeRole(TenantMember.StoreRole.STORE_STAFF).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(activeMember));
            when(tenantMemberRepository.updateStatusIfCurrent(memberRowId,
                    TenantMember.MemberStatus.ACTIVE, TenantMember.MemberStatus.REMOVED)).thenReturn(1);
            when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));

            tenantService.removeMember(TEST_TENANT_ID, INVITEE_ID);

            verify(tenantMemberRepository).save(argThat(m -> m.getStatus() == TenantMember.MemberStatus.REMOVED));
            verify(tenantMemberRepository, never()).delete(any());
            verify(auditService).record(eq("STORE_MEMBER_REMOVED"), eq("TENANT_MEMBER"), any(), eq(TEST_TENANT_ID),
                    eq("ACTIVE"), eq("REMOVED"), isNull(), eq(OWNER_ID));
        }

        @Test
        @DisplayName("removeMember：併發搶占失敗（狀態已被另一併發請求改變）→ E_2002，不記錄稽核")
        void removeMember_concurrentClaimLost_throwsE2002() {
            // 🔴 Sprint 137 DEF-142：模擬移除請求與另一併發操作（例如 acceptInvite）幾乎同時發生。
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            UUID memberRowId = UUID.randomUUID();
            TenantMember activeMember = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.ACTIVE).storeRole(TenantMember.StoreRole.STORE_STAFF).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(activeMember));
            when(tenantMemberRepository.updateStatusIfCurrent(memberRowId,
                    TenantMember.MemberStatus.ACTIVE, TenantMember.MemberStatus.REMOVED)).thenReturn(0);

            assertThrows(BusinessException.class, () -> tenantService.removeMember(TEST_TENANT_ID, INVITEE_ID));
            verify(tenantMemberRepository, never()).save(any());
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("updateMemberRole：成功變更角色並記錄稽核（DEF-107：稽核日誌覆蓋率）")
        void updateMemberRole_success_recordsAudit() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            UUID memberRowId = UUID.randomUUID();
            TenantMember staffMember = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.ACTIVE).storeRole(TenantMember.StoreRole.STORE_STAFF).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(staffMember));
            when(tenantMemberRepository.updateRoleIfNotRemoved(memberRowId, TenantMember.StoreRole.STORE_STAFF,
                    TenantMember.MemberStatus.REMOVED)).thenReturn(1);
            when(userRepository.findById(INVITEE_ID)).thenReturn(Optional.of(buildInvitee()));

            tenantService.updateMemberRole(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF");

            verify(auditService).record(eq("STORE_MEMBER_ROLE_CHANGED"), eq("TENANT_MEMBER"), eq(memberRowId), eq(TEST_TENANT_ID),
                    eq("STORE_STAFF"), eq("STORE_STAFF"), isNull(), eq(OWNER_ID));
        }

        @Test
        @DisplayName("updateMemberRole：併發搶占失敗（成員已被另一併發請求移除）→ E_2002，不記錄稽核")
        void updateMemberRole_concurrentlyRemoved_throwsE2002() {
            // 🔴 Sprint 137 DEF-144：模擬角色變更與另一併發的 removeMember 幾乎同時發生。
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            UUID memberRowId = UUID.randomUUID();
            TenantMember staffMember = TenantMember.builder()
                    .id(memberRowId).tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.ACTIVE).storeRole(TenantMember.StoreRole.STORE_STAFF).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(staffMember));
            when(tenantMemberRepository.updateRoleIfNotRemoved(memberRowId, TenantMember.StoreRole.STORE_STAFF,
                    TenantMember.MemberStatus.REMOVED)).thenReturn(0);

            assertThrows(BusinessException.class,
                    () -> tenantService.updateMemberRole(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF"));
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("updateMemberRole：試圖變更 StoreOwner 角色 → 拒絕且不記錄稽核")
        void updateMemberRole_targetIsOwner_rejectedWithoutAudit() {
            when(tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                    TEST_TENANT_ID, OWNER_ID, TenantMember.StoreRole.STORE_OWNER)).thenReturn(true);
            TenantMember ownerMember = TenantMember.builder()
                    .tenantId(TEST_TENANT_ID).userId(INVITEE_ID)
                    .status(TenantMember.MemberStatus.ACTIVE).storeRole(TenantMember.StoreRole.STORE_OWNER).build();
            when(tenantMemberRepository.findByTenantIdAndUserId(TEST_TENANT_ID, INVITEE_ID))
                    .thenReturn(Optional.of(ownerMember));

            assertThrows(BusinessException.class,
                    () -> tenantService.updateMemberRole(TEST_TENANT_ID, INVITEE_ID, "STORE_STAFF"));
            verify(tenantMemberRepository, never()).save(any());
            verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}