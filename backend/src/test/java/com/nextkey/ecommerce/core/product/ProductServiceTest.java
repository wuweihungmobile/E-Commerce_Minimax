package com.nextkey.ecommerce.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * ProductService 單元測試（Sprint 66 US-001）。
 *
 * <p>背景：{@code getProducts} 的 {@code keyword} 分支先前是死碼——呼叫的方法與無篩選的
 * else 分支完全相同，keyword 從未被實際使用，等同搜尋永遠失效、直接回傳全部上架商品。
 * 本測試聚焦驗證修正後 keyword 真正依 {@code searchByTenantIdAndKeyword} 過濾。
 */
@DisplayName("ProductService 單元測試（關鍵字搜尋）")
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private FeatureToggleService featureToggleService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductService productService;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final UUID LISTING_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product buildProduct(String title) {
        Listing listing = Listing.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .listingType(Listing.ListingType.PRODUCT)
                .title(title)
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BigDecimal.valueOf(100))
                .currency("TWD")
                .build();
        return Product.builder().listing(listing).category("electronics").build();
    }

    private Product buildProductForListing(final UUID listingId, final UUID tenantId) {
        Listing listing = Listing.builder()
                .id(listingId)
                .tenantId(tenantId)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Test Product")
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BigDecimal.valueOf(100))
                .currency("TWD")
                .build();
        return Product.builder().listing(listing).category("electronics").build();
    }

    @Test
    @DisplayName("getProducts：有關鍵字 → 呼叫 searchByTenantIdAndKeyword 而非無篩選查詢")
    void getProducts_withKeyword_callsSearchByKeyword() {
        Product product = buildProduct("藍牙耳機");
        when(productRepository.searchByTenantIdAndKeyword(eq(TENANT), eq("耳機"), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        Page<ProductDto.ListResponse> result =
                productService.getProducts(null, null, "耳機", 0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("藍牙耳機");
        verify(productRepository).searchByTenantIdAndKeyword(eq(TENANT), eq("耳機"), any());
        verify(productRepository, org.mockito.Mockito.never())
                .findByListingTenantIdAndListingStatus(any(), any(), any());
    }

    @Test
    @DisplayName("getProducts：關鍵字不命中 → 回傳空結果（而非誤回全部商品）")
    void getProducts_keywordNoMatch_returnsEmpty() {
        when(productRepository.searchByTenantIdAndKeyword(eq(TENANT), eq("不存在的關鍵字"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ProductDto.ListResponse> result =
                productService.getProducts(null, null, "不存在的關鍵字", 0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("getProducts：無任何篩選條件 → 回傳全部上架商品")
    void getProducts_noFilters_returnsAllActiveProducts() {
        Product product = buildProduct("無篩選商品");
        when(productRepository.findByListingTenantIdAndListingStatus(
                eq(TENANT), eq(Listing.ListingStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        Page<ProductDto.ListResponse> result =
                productService.getProducts(null, null, null, 0, 20, "createdAt", "DESC");

        assertThat(result.getContent()).hasSize(1);
        verify(productRepository, org.mockito.Mockito.never())
                .searchByTenantIdAndKeyword(any(), any(), any());
    }

    @Test
    @DisplayName("DEF-344：getProducts 的 sortBy 不在白名單 → 拋 BusinessException(E_9000)，不查詢（先前原樣傳給 JPA 排序，落入 500）")
    void getProducts_invalidSortBy_throwsE9000() {
        assertThatThrownBy(() -> productService.getProducts(null, null, null, 0, 20, "passwordHash", "DESC"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_9000);
        verify(productRepository, never()).findByListingTenantIdAndListingStatus(any(), any(), any());
    }

    @Test
    @DisplayName("DEF-344：getProducts 的 sortDir 不是 asc/desc → 拋 BusinessException(E_9000)")
    void getProducts_invalidSortDir_throwsE9000() {
        assertThatThrownBy(() -> productService.getProducts(null, null, null, 0, 20, "createdAt", "sideways"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_9000);
        verify(productRepository, never()).findByListingTenantIdAndListingStatus(any(), any(), any());
    }

    @Test
    @DisplayName("UT-PRODUCT-004（DEF-041）: updateProduct 同租戶 → 正常更新")
    void updateProduct_sameTenant_updatesSuccessfully() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());
        when(productRepository.save(product)).thenReturn(product);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().brand("NewBrand").build();

        ProductDto.Response response = productService.updateProduct(LISTING_ID, request, false);

        assertThat(response.getBrand()).isEqualTo("NewBrand");
    }

    @Test
    @DisplayName("DEF-321(b)（Sprint 252）：updateProduct 設定 isFeatured=true → Listing 寫入並反映於回應")
    void updateProduct_setIsFeaturedTrue_persistsAndReturnsInResponse() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());
        when(productRepository.save(product)).thenReturn(product);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().isFeatured(true).build();

        ProductDto.Response response = productService.updateProduct(LISTING_ID, request, false);

        assertThat(response.getIsFeatured()).isTrue();
        assertThat(product.getListing().getIsFeatured()).isTrue();
    }

    @Test
    @DisplayName("DEF-321(b)（Sprint 252）：updateProduct 不帶 isFeatured（null）→ 維持原值，不被覆蓋為 false")
    void updateProduct_isFeaturedOmitted_keepsExistingValue() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        product.getListing().setIsFeatured(true);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());
        when(productRepository.save(product)).thenReturn(product);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().brand("NewBrand").build();

        ProductDto.Response response = productService.updateProduct(LISTING_ID, request, false);

        assertThat(response.getIsFeatured()).isTrue();
    }

    @Test
    @DisplayName("Sprint 161：updateProduct 非法 status 拋出 BusinessException（E_9000），而非未攔截的 IllegalArgumentException")
    void updateProduct_invalidStatus_throwsBusinessException() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().status("NOT_A_REAL_STATUS").build();

        assertThatThrownBy(() -> productService.updateProduct(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9000);
    }

    @Test
    @DisplayName("DEF-207（Sprint 261 複核修復）：updateProduct 非 ACTIVE 狀態 + 非法 status 字串"
            + " → 拋 BusinessException(E_9000)，不被 isActivatingListing 內未攔截的 valueOf 搶先炸掉")
    void updateProduct_nonActiveStatusWithInvalidNewStatus_throwsBusinessException() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        product.getListing().setStatus(Listing.ListingStatus.INACTIVE);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().status("NOT_A_REAL_STATUS").build();

        assertThatThrownBy(() -> productService.updateProduct(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9000);
    }

    @Test
    @DisplayName("UT-PRODUCT-005（DEF-041）: updateProduct 跨租戶（非 SUPER_ADMIN）→ 拋 E_1007，不寫入")
    void updateProduct_crossTenant_throwsForbidden() {
        Product product = buildProductForListing(LISTING_ID, OTHER_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().brand("NewBrand").build();

        assertThatThrownBy(() -> productService.updateProduct(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class);
        assertThat(product.getBrand()).isNotEqualTo("NewBrand");
    }

    @Test
    @DisplayName("UT-PRODUCT-006（DEF-041）: updateProduct 跨租戶但 SUPER_ADMIN → 放行")
    void updateProduct_crossTenantSuperAdmin_allowed() {
        Product product = buildProductForListing(LISTING_ID, OTHER_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());
        when(productRepository.save(product)).thenReturn(product);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().brand("NewBrand").build();

        ProductDto.Response response = productService.updateProduct(LISTING_ID, request, true);

        assertThat(response.getBrand()).isEqualTo("NewBrand");
    }

    @Test
    @DisplayName("UT-PRODUCT-007（DEF-041）: deleteProduct 同租戶 → 正常軟刪除")
    void deleteProduct_sameTenant_deletesSuccessfully() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        lenient().when(listingRepository.save(product.getListing())).thenReturn(product.getListing());

        productService.deleteProduct(LISTING_ID, false);

        assertThat(product.getListing().getStatus()).isEqualTo(Listing.ListingStatus.DELETED);
    }

    @Test
    @DisplayName("UT-PRODUCT-008（DEF-041）: deleteProduct 跨租戶（非 SUPER_ADMIN）→ 拋 E_1007，不刪除")
    void deleteProduct_crossTenant_throwsForbidden() {
        Product product = buildProductForListing(LISTING_ID, OTHER_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct(LISTING_ID, false))
                .isInstanceOf(BusinessException.class);
        assertThat(product.getListing().getStatus()).isEqualTo(Listing.ListingStatus.ACTIVE);
    }

    @Test
    @DisplayName("UT-PRODUCT-009（DEF-041）: deleteProduct 跨租戶但 SUPER_ADMIN → 放行")
    void deleteProduct_crossTenantSuperAdmin_allowed() {
        Product product = buildProductForListing(LISTING_ID, OTHER_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());

        productService.deleteProduct(LISTING_ID, true);

        assertThat(product.getListing().getStatus()).isEqualTo(Listing.ListingStatus.DELETED);
    }

    // ========== Sprint 147：MAX_PRODUCTS 數量配額強制執行 ==========

    @Test
    @DisplayName("Sprint 147: createProduct 已達 MAX_PRODUCTS 配額 → 拋 BusinessException，未建立 Listing")
    void createProduct_quotaExceeded_doesNotCreateListing() {
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                TENANT, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE))
                .thenReturn((long) AppConstants.QUOTA_MAX_PRODUCTS);
        doThrow(new BusinessException(ErrorCode.E_2009))
                .when(featureToggleService)
                .checkQuotaNotExceeded(AppConstants.QUOTA_MAX_PRODUCTS, AppConstants.QUOTA_MAX_PRODUCTS);

        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
                .title("超額商品").basePrice(BigDecimal.valueOf(100)).build();

        assertThatThrownBy(() -> productService.createProduct(request, false))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).save(any());
        verify(tenantRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Sprint 147: createProduct 配額未達上限 → 正常建立，且以正確的目前數量呼叫配額檢查")
    void createProduct_underQuota_createsSuccessfullyAndChecksQuota() {
        UUID userId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                TENANT, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE))
                .thenReturn(3L);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(Tenant.builder().id(TENANT).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
                .title("新商品").basePrice(BigDecimal.valueOf(100)).build();

        ProductDto.Response response = productService.createProduct(request, false);

        assertThat(response.getTitle()).isEqualTo("新商品");
        verify(featureToggleService).checkQuotaNotExceeded(AppConstants.QUOTA_MAX_PRODUCTS, 3L);
    }

    @Test
    @DisplayName("Sprint 147: updateProduct 由 INACTIVE 轉 ACTIVE 且已達配額 → 拋 BusinessException，不寫入")
    void updateProduct_reactivateOverQuota_throwsAndDoesNotSave() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        product.getListing().setStatus(Listing.ListingStatus.INACTIVE);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                TENANT, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE))
                .thenReturn((long) AppConstants.QUOTA_MAX_PRODUCTS);
        doThrow(new BusinessException(ErrorCode.E_2009))
                .when(featureToggleService)
                .checkQuotaNotExceeded(AppConstants.QUOTA_MAX_PRODUCTS, AppConstants.QUOTA_MAX_PRODUCTS);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().status("ACTIVE").build();

        assertThatThrownBy(() -> productService.updateProduct(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).save(any());
        assertThat(product.getListing().getStatus()).isEqualTo(Listing.ListingStatus.INACTIVE);
    }

    @Test
    @DisplayName("Sprint 147: updateProduct 更新非狀態欄位（維持 ACTIVE）→ 不觸發配額檢查")
    void updateProduct_nonStatusChangeWhileActive_doesNotCheckQuota() {
        Product product = buildProductForListing(LISTING_ID, TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());
        when(productRepository.save(product)).thenReturn(product);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().brand("NewBrand").build();

        productService.updateProduct(LISTING_ID, request, false);

        verify(featureToggleService, never()).checkQuotaNotExceeded(anyInt(), anyLong());
        verify(listingRepository, never()).countByTenantIdAndListingTypeAndStatus(any(), any(), any());
    }

    // ========== Sprint 148（DEF-184）：RETAIL_ENABLED 檢查搬移至 Service 層 ==========

    @Test
    @DisplayName("Sprint 148（DEF-184）: createProductFromDashboard 的 RETAIL_ENABLED 停用 → 拋 BusinessException，未建立 Listing")
    void createProductFromDashboard_retailDisabled_throwsAndDoesNotCreateListing() {
        doThrow(new BusinessException(ErrorCode.E_2004, "Feature 'RETAIL_ENABLED' is disabled for this tenant"))
                .when(featureToggleService).checkFeatureEnabled("RETAIL_ENABLED");

        com.nextkey.ecommerce.api.dto.CreateListingRequest request =
                com.nextkey.ecommerce.api.dto.CreateListingRequest.builder()
                        .listingType("PRODUCT").name("停用開關商品").price(BigDecimal.valueOf(100)).build();

        assertThatThrownBy(() -> productService.createProductFromDashboard(request, false))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).save(any());
        verify(listingRepository, never()).countByTenantIdAndListingTypeAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("Sprint 148（DEF-184）: createProductFromDashboard 的 RETAIL_ENABLED 啟用 → 正常建立，且確實檢查了開關")
    void createProductFromDashboard_retailEnabled_createsSuccessfullyAndChecksToggle() {
        UUID userId = UUID.randomUUID();
        TenantContext.setCurrentUser(userId);
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                TENANT, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE))
                .thenReturn(1L);
        when(tenantRepository.findById(TENANT)).thenReturn(Optional.of(Tenant.builder().id(TENANT).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        com.nextkey.ecommerce.api.dto.CreateListingRequest request =
                com.nextkey.ecommerce.api.dto.CreateListingRequest.builder()
                        .listingType("PRODUCT").name("Dashboard新商品").price(BigDecimal.valueOf(100)).build();

        ProductDto.Response response = productService.createProductFromDashboard(request, false);

        assertThat(response.getTitle()).isEqualTo("Dashboard新商品");
        verify(featureToggleService).checkFeatureEnabled("RETAIL_ENABLED");
    }

    // ========== Sprint 304（DEF-415）：沒有店鋪的呼叫者不得管理系統租戶（平台自營）的商品 ==========
    //
    // 沒有店鋪者（含沒有店鋪的 ADMIN）的租戶是系統租戶佔位值，平台自營商品也蓋成同一個值；
    // 舊檢查只比「租戶相等」，所以下列案例的呼叫者與商品「同租戶」——守門要擋的正是這種看起來合法的相等。

    private static final UUID SYSTEM_TENANT = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    @Test
    @DisplayName("DEF-415: updateProduct 沒有店鋪的呼叫者（租戶是系統租戶）改系統租戶商品 → E_1007，且不寫入")
    void updateProduct_storelessCallerOnSystemTenantProduct_throwsForbiddenAndDoesNotSave() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Product product = buildProductForListing(LISTING_ID, SYSTEM_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().title("hijacked").build();

        assertThatThrownBy(() -> productService.updateProduct(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(listingRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        assertThat(product.getListing().getTitle()).as("被擋之後標題不得被改").isEqualTo("Test Product");
    }

    @Test
    @DisplayName("DEF-415: deleteProduct 沒有店鋪的呼叫者刪系統租戶商品 → E_1007，狀態不變")
    void deleteProduct_storelessCallerOnSystemTenantProduct_throwsForbiddenAndKeepsStatus() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Product product = buildProductForListing(LISTING_ID, SYSTEM_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct(LISTING_ID, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(listingRepository, never()).save(any());
        assertThat(product.getListing().getStatus()).isEqualTo(Listing.ListingStatus.ACTIVE);
    }

    @Test
    @DisplayName("DEF-415: SUPER_ADMIN（租戶同為系統租戶）仍可管理系統租戶商品——守門不得連平台管理員一起擋")
    void updateProduct_superAdminOnSystemTenantProduct_stillAllowed() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        Product product = buildProductForListing(LISTING_ID, SYSTEM_TENANT);
        when(productRepository.findByListingId(LISTING_ID)).thenReturn(Optional.of(product));
        when(listingRepository.save(product.getListing())).thenReturn(product.getListing());
        when(productRepository.save(product)).thenReturn(product);
        ProductDto.UpdateRequest request = ProductDto.UpdateRequest.builder().brand("NewBrand").build();

        ProductDto.Response response = productService.updateProduct(LISTING_ID, request, true);

        assertThat(response.getBrand()).isEqualTo("NewBrand");
    }

    @Test
    @DisplayName("DEF-415: createProduct 沒有店鋪的呼叫者 → E_1007，且在功能開關、配額、租戶查詢之前就擋下（先判斷再查詢）")
    void createProduct_storelessCaller_throwsForbiddenBeforeAnyLookup() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
                .title("平台自營商品").basePrice(BigDecimal.valueOf(100)).build();

        assertThatThrownBy(() -> productService.createProduct(request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(featureToggleService, never()).checkFeatureEnabled(any());
        verify(featureToggleService, never()).checkQuotaNotExceeded(anyInt(), anyLong());
        verify(tenantRepository, never()).findById(any());
        verify(listingRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-415: createProductFromDashboard 沒有店鋪的呼叫者 → E_1007，且在功能開關、配額、租戶查詢之前就擋下")
    void createProductFromDashboard_storelessCaller_throwsForbiddenBeforeAnyLookup() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        com.nextkey.ecommerce.api.dto.CreateListingRequest request =
                com.nextkey.ecommerce.api.dto.CreateListingRequest.builder()
                        .listingType("PRODUCT").name("平台自營商品").price(BigDecimal.valueOf(100)).build();

        assertThatThrownBy(() -> productService.createProductFromDashboard(request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);
        verify(featureToggleService, never()).checkFeatureEnabled(any());
        verify(featureToggleService, never()).checkQuotaNotExceeded(anyInt(), anyLong());
        verify(tenantRepository, never()).findById(any());
        verify(listingRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-415: SUPER_ADMIN 仍可建立商品，且蓋成系統租戶（平台自營）")
    void createProduct_superAdminInSystemTenant_createsSystemTenantListing() {
        UUID userId = UUID.randomUUID();
        TenantContext.setCurrentTenant(SYSTEM_TENANT);
        TenantContext.setCurrentUser(userId);
        when(listingRepository.countByTenantIdAndListingTypeAndStatus(
                SYSTEM_TENANT, Listing.ListingType.PRODUCT, Listing.ListingStatus.ACTIVE)).thenReturn(0L);
        when(tenantRepository.findById(SYSTEM_TENANT))
                .thenReturn(Optional.of(Tenant.builder().id(SYSTEM_TENANT).build()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(listingRepository.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(ProductDto.CreateRequest.builder()
                .title("平台自營商品").basePrice(BigDecimal.valueOf(100)).build(), true);

        ArgumentCaptor<Listing> saved = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(SYSTEM_TENANT);
    }
}
