package com.nextkey.ecommerce.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.nextkey.ecommerce.api.dto.ProductDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.Product;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
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
}
