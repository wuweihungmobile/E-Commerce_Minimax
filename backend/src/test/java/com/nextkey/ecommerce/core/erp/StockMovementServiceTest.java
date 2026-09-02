package com.nextkey.ecommerce.core.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.erp.StockMovementDto;
import com.nextkey.ecommerce.api.dto.erp.StockMovementRequest;
import com.nextkey.ecommerce.domain.model.inventory.StockMovement;
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
 * {@link StockMovementService} 單元測試（Sprint 72 US-001）。
 *
 * <p>比照 {@code M16ErpIntegrationTest}（IT-M16-301~306）業務情境。特別涵蓋 {@code createManualMovement}
 * 既有的 DEF-017 租戶檢查（SKU 所屬 listing 非當前租戶須拒絕），避免本 Sprint 誤刪或誤改此既有安全修復。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StockMovementService 單元測試（Sprint 72）")
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductInventoryRepository productInventoryRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private StockMovementService stockMovementService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();
    private final UUID listingId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ProductSku skuOf() {
        ProductSku sku = new ProductSku();
        sku.setId(skuId);
        sku.setProductListingId(listingId);
        return sku;
    }

    private ProductInventory inventoryOf(final int totalQty) {
        return ProductInventory.builder()
                .skuId(skuId)
                .sku(skuOf())
                .totalQty(totalQty)
                .reservedQty(0)
                .build();
    }

    private Listing listingOf(final UUID tid) {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setTenantId(tid);
        return listing;
    }

    // ── createManualMovement：租戶隔離（DEF-017） ──────────────

    @Test
    @DisplayName("createManualMovement：SKU 屬於他租戶時拒絕（DEF-017 既有修復回歸）")
    void createManualMovement_skuBelongsToOtherTenant_throwsE1007() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(otherTenantId)));

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("ADJUSTMENT")
                .quantity(10)
                .build();

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("createManualMovement：SKU 不存在時拋出 E_3003")
    void createManualMovement_skuNotFound_throwsE3003() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.empty());

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("ADJUSTMENT")
                .quantity(10)
                .build();

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_3003));
    }

    @Test
    @DisplayName("createManualMovement：listing 不存在時拋出 E_3003")
    void createManualMovement_listingNotFound_throwsE3003() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("ADJUSTMENT")
                .quantity(10)
                .build();

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_3003));
    }

    // ── createManualMovement：異動類型分支 ──────────────────────

    @Test
    @DisplayName("createManualMovement：ADJUSTMENT 委派原子 UPDATE 增加庫存，異動記錄取回讀後的數量")
    void createManualMovement_adjustment_increasesStock() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));
        when(productInventoryRepository.findTotalQtyBySkuId(skuId)).thenReturn(120);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> {
            StockMovement m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("ADJUSTMENT")
                .quantity(20)
                .notes("盤盈")
                .build();

        StockMovementDto result = stockMovementService.createManualMovement(request, userId);

        verify(productInventoryRepository).increaseTotalQty(skuId, 20);
        // before 由「回讀值 − 本次帶號變化量」反推，故 after 來自 DB、before 與之必然自洽
        assertThat(result.getBeforeTotalQty()).isEqualTo(100);
        assertThat(result.getAfterTotalQty()).isEqualTo(120);
        assertThat(result.getMovementType()).isEqualTo("ADJUSTMENT");
        assertThat(result.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("createManualMovement：DEF-051 守衛——不得退回讀後寫，也不得沿用訂單出貨的扣帳敘述")
    void createManualMovement_neverWritesThroughEntityOrOrderFlowQuery() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));
        when(productInventoryRepository.decreaseTotalQtyIfSufficient(skuId, 10)).thenReturn(1);
        when(productInventoryRepository.findTotalQtyBySkuId(skuId)).thenReturn(90);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("DAMAGE")
                .quantity(10)
                .build();

        stockMovementService.createManualMovement(request, userId);

        // 讀後寫的入口：實體 setter + save()。留一個守衛，避免無徵兆退回 Sprint 113 之前的寫法
        verify(productInventoryRepository, never()).save(any(ProductInventory.class));
        // deductReserved 是訂單出貨的敘述（同時扣 reserved_qty）。ERP 報廢／盤虧／調撥出庫依
        // PRD §6.7.4 只該動 total_qty——共用那條敘述正是修復前把買家已預留的貨放回可售池的原因
        verify(productInventoryRepository, never()).deductReserved(any(UUID.class), anyInt());
    }

    @Test
    @DisplayName("createManualMovement：TRANSFER_IN 增加庫存")
    void createManualMovement_transferIn_increasesStock() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(50)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));
        when(productInventoryRepository.findTotalQtyBySkuId(skuId)).thenReturn(80);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("TRANSFER_IN")
                .quantity(30)
                .build();

        StockMovementDto result = stockMovementService.createManualMovement(request, userId);

        verify(productInventoryRepository).increaseTotalQty(skuId, 30);
        assertThat(result.getAfterTotalQty()).isEqualTo(80);
    }

    @Test
    @DisplayName("createManualMovement：DAMAGE 扣減庫存，庫存足夠時成功")
    void createManualMovement_damage_sufficientStock_decreasesStock() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));
        when(productInventoryRepository.decreaseTotalQtyIfSufficient(skuId, 40)).thenReturn(1);
        when(productInventoryRepository.findTotalQtyBySkuId(skuId)).thenReturn(60);
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("DAMAGE")
                .quantity(40)
                .build();

        StockMovementDto result = stockMovementService.createManualMovement(request, userId);

        assertThat(result.getBeforeTotalQty()).isEqualTo(100);
        assertThat(result.getAfterTotalQty()).isEqualTo(60);
    }

    @ParameterizedTest
    @EnumSource(value = StockMovement.MovementType.class, names = {"DAMAGE", "TRANSFER_OUT", "THEFT"})
    @DisplayName("createManualMovement：DAMAGE/TRANSFER_OUT/THEFT 庫存不足時拋出 E_7004")
    void createManualMovement_deductTypes_insufficientStock_throwsE7004(final StockMovement.MovementType type) {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(5)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));
        // Sprint 113（DEF-051）：庫存足夠與否下沉到 SQL 的 WHERE，0 筆＝總量不足
        // （庫存列的存在性已由 findById 確認），錯誤訊息裡的當前數量另行回讀
        when(productInventoryRepository.decreaseTotalQtyIfSufficient(skuId, 10)).thenReturn(0);
        when(productInventoryRepository.findTotalQtyBySkuId(skuId)).thenReturn(5);

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType(type.name())
                .quantity(10)
                .build();

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7004));
    }

    @ParameterizedTest
    @EnumSource(value = StockMovement.MovementType.class,
            names = {"PURCHASE_RECEIPT", "SALE", "RESERVATION", "RELEASE"})
    @DisplayName("createManualMovement：禁止手動使用 PURCHASE_RECEIPT/SALE/RESERVATION/RELEASE，拋出 E_7005")
    void createManualMovement_forbiddenTypes_throwsE7005(final StockMovement.MovementType type) {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType(type.name())
                .quantity(10)
                .build();

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7005));
    }

    @Test
    @DisplayName("createManualMovement：無效異動類型字串拋出 E_7005")
    void createManualMovement_invalidMovementType_throwsE7005() {
        when(productInventoryRepository.findById(skuId)).thenReturn(Optional.of(inventoryOf(100)));
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listingOf(tenantId)));

        StockMovementRequest request = StockMovementRequest.builder()
                .skuId(skuId)
                .movementType("NOT_A_REAL_TYPE")
                .quantity(10)
                .build();

        assertThatThrownBy(() -> stockMovementService.createManualMovement(request, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7005));
    }

    // ── 查詢方法：租戶過濾傳遞 ───────────────────────────────────

    @Test
    @DisplayName("getMovementsBySku：依 SKU+租戶過濾")
    void getMovementsBySku_filtersBySkuAndTenant() {
        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .skuId(skuId)
                .movementType(StockMovement.MovementType.ADJUSTMENT)
                .quantity(10)
                .build();
        when(stockMovementRepository.findBySkuIdAndTenantIdOrderByCreatedAtDesc(skuId, tenantId))
                .thenReturn(List.of(movement));

        List<StockMovementDto> result = stockMovementService.getMovementsBySku(skuId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSkuId()).isEqualTo(skuId);
    }

    @Test
    @DisplayName("getMovements：分頁依租戶過濾")
    void getMovements_paginatedByTenant() {
        Pageable pageable = PageRequest.of(0, 10);
        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .skuId(skuId)
                .movementType(StockMovement.MovementType.ADJUSTMENT)
                .quantity(5)
                .build();
        Page<StockMovement> page = new PageImpl<>(List.of(movement), pageable, 1);
        when(stockMovementRepository.findByTenantId(tenantId, pageable)).thenReturn(page);

        Page<StockMovementDto> result = stockMovementService.getMovements(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMovementsByDateRange：依租戶+時間範圍過濾")
    void getMovementsByDateRange_filtersByTenantAndRange() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-31T23:59:59Z");
        when(stockMovementRepository.findByTenantIdAndDateRange(tenantId, start, end)).thenReturn(List.of());

        List<StockMovementDto> result = stockMovementService.getMovementsByDateRange(start, end);

        assertThat(result).isEmpty();
    }
}
