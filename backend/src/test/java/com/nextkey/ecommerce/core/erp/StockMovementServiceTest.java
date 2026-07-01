package com.nextkey.ecommerce.core.erp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.product.ProductInventory;
import com.nextkey.ecommerce.domain.model.product.ProductSku;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductInventoryRepository;
import com.nextkey.ecommerce.domain.repository.StockMovementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * StockMovementService 單元測試（Sprint 28 US-004）。
 *
 * <p>US-004 修復先前 no-op 的租戶擁有權檢查（原 getTenantListings 回傳 tenantId、且 if body 為空，
 * 手動庫存異動未把關租戶隔離）。本測試鎖住修復：SKU 所屬 listing 不屬於當前租戶 → E_4031；
 * listing 不存在 → E_3003。
 */
@DisplayName("StockMovementService 單元測試（租戶擁有權把關）")
@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private ProductInventoryRepository productInventoryRepository;
    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private StockMovementService stockMovementService;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createManualMovement：SKU 所屬 listing 非當前租戶 → E_4031（租戶隔離把關）")
    void createManualMovement_listingOtherTenant_throwsForbidden() {
        UUID skuId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        StockMovementRequest request = org.mockito.Mockito.mock(StockMovementRequest.class);
        when(request.getSkuId()).thenReturn(skuId);

        ProductInventory inventory = org.mockito.Mockito.mock(ProductInventory.class);
        ProductSku sku = org.mockito.Mockito.mock(ProductSku.class);
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventory));
        when(inventory.getSku()).thenReturn(sku);
        when(sku.getProductListingId()).thenReturn(listingId);

        Listing listing = org.mockito.Mockito.mock(Listing.class);
        when(listing.getTenantId()).thenReturn(UUID.randomUUID()); // 不同租戶
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_4031);
    }

    @Test
    @DisplayName("createManualMovement：SKU 所屬 listing 不存在 → E_3003")
    void createManualMovement_listingNotFound_throws() {
        UUID skuId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        StockMovementRequest request = org.mockito.Mockito.mock(StockMovementRequest.class);
        when(request.getSkuId()).thenReturn(skuId);

        ProductInventory inventory = org.mockito.Mockito.mock(ProductInventory.class);
        ProductSku sku = org.mockito.Mockito.mock(ProductSku.class);
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventory));
        when(inventory.getSku()).thenReturn(sku);
        when(sku.getProductListingId()).thenReturn(listingId);
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_3003);
    }
}
