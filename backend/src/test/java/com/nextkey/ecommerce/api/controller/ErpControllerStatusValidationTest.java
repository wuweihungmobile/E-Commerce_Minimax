package com.nextkey.ecommerce.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.core.erp.InventoryService;
import com.nextkey.ecommerce.core.erp.PurchaseOrderService;
import com.nextkey.ecommerce.core.erp.StockMovementService;
import com.nextkey.ecommerce.core.erp.SupplierService;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * DEF-385：{@code ErpController.listPurchaseOrders} 的 {@code status} 參數解析失敗時應回乾淨的
 * 422/E-7002（與既有的「無效的採購單狀態」同碼），而非未經處理的 {@code IllegalArgumentException}
 * 落入 catch-all 變 500。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DEF-385: ErpController 無效 status 參數處理")
class ErpControllerStatusValidationTest {

    @Mock private SupplierService supplierService;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private InventoryService inventoryService;
    @Mock private StockMovementService stockMovementService;
    @Mock private ListingRepository listingRepository;
    @Mock private ProductSkuRepository productSkuRepository;

    @InjectMocks
    private ErpController controller;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("無效的採購單狀態字串 → BusinessException(E_7002)，不是未經處理的 IllegalArgumentException")
    void listPurchaseOrders_invalidStatus_throwsBusinessExceptionE7002() {
        TenantContext.setCurrentTenant(java.util.UUID.randomUUID());

        assertThatThrownBy(() -> controller.listPurchaseOrders("NOT_A_REAL_STATUS", 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_7002);
    }
}
