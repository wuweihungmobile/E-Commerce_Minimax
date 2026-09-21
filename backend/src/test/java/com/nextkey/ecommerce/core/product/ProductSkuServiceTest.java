package com.nextkey.ecommerce.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.nextkey.ecommerce.api.dto.SkuDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * ProductSkuService 單元測試（Sprint 178）。
 *
 * <p>背景：{@code product_skus}/{@code product_inventory} 整條鏈路先前在正式環境完全無法
 * 產生資料——全庫沒有任何程式碼建立 {@code ProductSku}，導致依賴 SKU 的既有 ERP 庫存子系統
 * （低庫存預警、DEF-050 三段式原子庫存操作、採購單收貨入庫）實質上從未真正運作過。
 * 本測試聚焦驗證新增的 SKU 建立/查詢/更新流程，以及建立 SKU 時一併建立
 * {@code total_qty=0} 的 {@link ProductInventory} 列，使既有庫存管線得以真正被觸發。
 */
@DisplayName("ProductSkuService 單元測試")
@ExtendWith(MockitoExtension.class)
class ProductSkuServiceTest {

    @Mock
    private ProductSkuRepository productSkuRepository;
    @Mock
    private ProductInventoryRepository productInventoryRepository;
    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ProductSkuService productSkuService;

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

    private Listing buildProductListing(final UUID tenantId) {
        return Listing.builder()
                .id(LISTING_ID)
                .tenantId(tenantId)
                .listingType(Listing.ListingType.PRODUCT)
                .title("測試商品")
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(BigDecimal.valueOf(100))
                .currency("TWD")
                .build();
    }

    private Listing buildRoomListing(final UUID tenantId) {
        return Listing.builder()
                .id(LISTING_ID)
                .tenantId(tenantId)
                .listingType(Listing.ListingType.ROOM)
                .title("測試房源")
                .status(Listing.ListingStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("建立 SKU：成功時一併建立 total_qty=0 的庫存列")
    void createSku_validRequest_createsSkuAndZeroQtyInventory() {
        Listing listing = buildProductListing(TENANT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(productSkuRepository.existsBySkuCode("SKU-001")).thenReturn(false);

        UUID skuId = UUID.randomUUID();
        when(productSkuRepository.save(any(ProductSku.class))).thenAnswer(invocation -> {
            ProductSku sku = invocation.getArgument(0);
            sku.setId(skuId);
            return sku;
        });
        when(productInventoryRepository.save(any(ProductInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder()
                .skuCode("SKU-001")
                .specName("紅色 / L")
                .priceOverride(BigDecimal.valueOf(150))
                .build();

        SkuDto.Response response = productSkuService.createSku(LISTING_ID, request, false);

        assertThat(response.getSkuCode()).isEqualTo("SKU-001");
        assertThat(response.getSpecName()).isEqualTo("紅色 / L");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getTotalQty()).isZero();
        assertThat(response.getReservedQty()).isZero();
        assertThat(response.getAvailableQty()).isZero();

        ArgumentCaptor<ProductInventory> inventoryCaptor = ArgumentCaptor.forClass(ProductInventory.class);
        verify(productInventoryRepository).save(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getValue().getSkuId()).isEqualTo(skuId);
        assertThat(inventoryCaptor.getValue().getTotalQty()).isZero();
    }

    @Test
    @DisplayName("建立 SKU：SKU 代碼重複時拋出 E-3005，不建立庫存列")
    void createSku_duplicateSkuCode_throwsConflict() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildProductListing(TENANT)));
        when(productSkuRepository.existsBySkuCode("SKU-001")).thenReturn(true);

        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("SKU-001").build();

        assertThatThrownBy(() -> productSkuService.createSku(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3005);

        verify(productSkuRepository, never()).save(any());
        verify(productInventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("建立 SKU：非 PRODUCT 類型 listing 拒絕，房源不適用 SKU 管理")
    void createSku_roomListing_throwsInvalidListingType() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildRoomListing(TENANT)));

        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("SKU-001").build();

        assertThatThrownBy(() -> productSkuService.createSku(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3001);

        verify(productSkuRepository, never()).save(any());
    }

    @Test
    @DisplayName("建立 SKU：非本租戶且非 SUPER_ADMIN 時拒絕（跨租戶 IDOR 防護）")
    void createSku_differentTenantNotSuperAdmin_throwsForbidden() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildProductListing(OTHER_TENANT)));

        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("SKU-001").build();

        assertThatThrownBy(() -> productSkuService.createSku(LISTING_ID, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_1007);

        verify(productSkuRepository, never()).save(any());
    }

    @Test
    @DisplayName("建立 SKU：SUPER_ADMIN 可跨租戶建立")
    void createSku_differentTenantButSuperAdmin_succeeds() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildProductListing(OTHER_TENANT)));
        when(productSkuRepository.existsBySkuCode("SKU-001")).thenReturn(false);
        when(productSkuRepository.save(any(ProductSku.class))).thenAnswer(invocation -> {
            ProductSku sku = invocation.getArgument(0);
            sku.setId(UUID.randomUUID());
            return sku;
        });
        when(productInventoryRepository.save(any(ProductInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SkuDto.CreateRequest request = SkuDto.CreateRequest.builder().skuCode("SKU-001").build();

        SkuDto.Response response = productSkuService.createSku(LISTING_ID, request, true);

        assertThat(response.getSkuCode()).isEqualTo("SKU-001");
    }

    @Test
    @DisplayName("查詢 SKU 列表：附帶庫存數量計算")
    void listSkus_returnsSkusWithInventoryQuantities() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildProductListing(TENANT)));

        UUID skuId = UUID.randomUUID();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-001")
                .specName("紅色 / L")
                .status("ACTIVE")
                .build();
        when(productSkuRepository.findByProductListingId(LISTING_ID)).thenReturn(List.of(sku));

        ProductInventory inventory = ProductInventory.builder()
                .skuId(skuId)
                .totalQty(20)
                .reservedQty(5)
                .build();
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventory));

        List<SkuDto.Response> result = productSkuService.listSkus(LISTING_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalQty()).isEqualTo(20);
        assertThat(result.get(0).getReservedQty()).isEqualTo(5);
        assertThat(result.get(0).getAvailableQty()).isEqualTo(15);
    }

    @Test
    @DisplayName("更新 SKU：只更新有帶入的欄位（partial update）")
    void updateSku_onlyUpdatesProvidedFields() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildProductListing(TENANT)));

        UUID skuId = UUID.randomUUID();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .productListingId(LISTING_ID)
                .skuCode("SKU-001")
                .specName("原始規格")
                .status("ACTIVE")
                .build();
        when(productSkuRepository.findById(skuId)).thenReturn(Optional.of(sku));
        when(productSkuRepository.save(any(ProductSku.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.empty());

        SkuDto.UpdateRequest request = SkuDto.UpdateRequest.builder().status("INACTIVE").build();

        SkuDto.Response response = productSkuService.updateSku(LISTING_ID, skuId, request, false);

        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        assertThat(response.getSpecName()).isEqualTo("原始規格");
    }

    @Test
    @DisplayName("更新 SKU：skuId 不屬於指定 listingId 時視為找不到（防止跨商品竄改）")
    void updateSku_skuBelongsToDifferentListing_throwsNotFound() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(buildProductListing(TENANT)));

        UUID skuId = UUID.randomUUID();
        UUID otherListingId = UUID.randomUUID();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .productListingId(otherListingId)
                .skuCode("SKU-001")
                .status("ACTIVE")
                .build();
        when(productSkuRepository.findById(skuId)).thenReturn(Optional.of(sku));

        SkuDto.UpdateRequest request = SkuDto.UpdateRequest.builder().status("INACTIVE").build();

        assertThatThrownBy(() -> productSkuService.updateSku(LISTING_ID, skuId, request, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3003);

        verify(productSkuRepository, never()).save(any());
    }
}
