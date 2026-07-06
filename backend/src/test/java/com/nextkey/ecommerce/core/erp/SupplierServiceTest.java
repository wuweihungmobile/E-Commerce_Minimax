package com.nextkey.ecommerce.core.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.nextkey.ecommerce.api.dto.erp.SupplierCreateRequest;
import com.nextkey.ecommerce.api.dto.erp.SupplierDto;
import com.nextkey.ecommerce.api.dto.erp.SupplierUpdateRequest;
import com.nextkey.ecommerce.domain.model.erp.Supplier;
import com.nextkey.ecommerce.domain.repository.SupplierRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * {@link SupplierService} 單元測試（Sprint 72 US-001）。
 *
 * <p>比照 {@code M16ErpIntegrationTest}（IT-M16-001~008）的業務情境作為預期行為依據，
 * 以純 Mockito 隔離 {@link SupplierRepository}，涵蓋 5 個 public 方法的正常路徑與錯誤路徑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierService 單元測試（Sprint 72）")
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierService supplierService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Supplier supplierOf(final UUID id, final UUID tid, final Supplier.SupplierStatus status) {
        return Supplier.builder()
                .id(id)
                .tenantId(tid)
                .name("Test Supplier")
                .contactPerson("John Doe")
                .email("supplier@example.com")
                .phone("+886-987654321")
                .address("Taipei, Taiwan")
                .status(status)
                .build();
    }

    // ── createSupplier ──────────────────────────────────────────

    @Test
    @DisplayName("createSupplier：正常路徑，注入當前租戶並預設 ACTIVE")
    void createSupplier_success_injectsTenantAndDefaultsActive() {
        SupplierCreateRequest request = SupplierCreateRequest.builder()
                .name("New Supplier")
                .contactPerson("Jane Doe")
                .email("new@example.com")
                .phone("+886-111222333")
                .address("Kaohsiung")
                .build();

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier s = invocation.getArgument(0);
            s.setId(supplierId);
            return s;
        });

        SupplierDto result = supplierService.createSupplier(request);

        verify(supplierRepository).save(captor.capture());
        Supplier saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getStatus()).isEqualTo(Supplier.SupplierStatus.ACTIVE);
        assertThat(saved.getName()).isEqualTo("New Supplier");

        assertThat(result.getId()).isEqualTo(supplierId);
        assertThat(result.getName()).isEqualTo("New Supplier");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    // ── getSupplier ──────────────────────────────────────────────

    @Test
    @DisplayName("getSupplier：存在時回傳 DTO")
    void getSupplier_found_returnsDto() {
        Supplier supplier = supplierOf(supplierId, tenantId, Supplier.SupplierStatus.ACTIVE);
        when(supplierRepository.findByIdAndTenantId(supplierId, tenantId)).thenReturn(Optional.of(supplier));

        SupplierDto result = supplierService.getSupplier(supplierId);

        assertThat(result.getId()).isEqualTo(supplierId);
        assertThat(result.getName()).isEqualTo("Test Supplier");
    }

    @Test
    @DisplayName("getSupplier：不存在時拋出 E_7000")
    void getSupplier_notFound_throwsE7000() {
        when(supplierRepository.findByIdAndTenantId(supplierId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.getSupplier(supplierId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7000));
    }

    // ── listSuppliers ────────────────────────────────────────────

    @Test
    @DisplayName("listSuppliers：不帶 status 時查詢全部")
    void listSuppliers_withoutStatus_returnsAll() {
        when(supplierRepository.findByTenantId(tenantId)).thenReturn(List.of(
                supplierOf(UUID.randomUUID(), tenantId, Supplier.SupplierStatus.ACTIVE),
                supplierOf(UUID.randomUUID(), tenantId, Supplier.SupplierStatus.INACTIVE)));

        List<SupplierDto> result = supplierService.listSuppliers(null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listSuppliers：帶 status 時依租戶+狀態過濾")
    void listSuppliers_withStatus_filtersByStatus() {
        when(supplierRepository.findByTenantIdAndStatus(tenantId, Supplier.SupplierStatus.ACTIVE))
                .thenReturn(List.of(supplierOf(supplierId, tenantId, Supplier.SupplierStatus.ACTIVE)));

        List<SupplierDto> result = supplierService.listSuppliers("active");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    // ── searchSuppliers ──────────────────────────────────────────

    @Test
    @DisplayName("searchSuppliers：依租戶+關鍵字模糊比對")
    void searchSuppliers_returnsMatching() {
        when(supplierRepository.searchByName(tenantId, "Test")).thenReturn(
                List.of(supplierOf(supplierId, tenantId, Supplier.SupplierStatus.ACTIVE)));

        List<SupplierDto> result = supplierService.searchSuppliers("Test");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Supplier");
    }

    // ── updateSupplier ───────────────────────────────────────────

    @Test
    @DisplayName("updateSupplier：只更新請求中有帶的欄位")
    void updateSupplier_partialFields_updatesOnlyProvided() {
        Supplier existing = supplierOf(supplierId, tenantId, Supplier.SupplierStatus.ACTIVE);
        when(supplierRepository.findByIdAndTenantId(supplierId, tenantId)).thenReturn(Optional.of(existing));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierUpdateRequest request = SupplierUpdateRequest.builder()
                .name("Updated Name")
                .build();

        SupplierDto result = supplierService.updateSupplier(supplierId, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
        // 未帶的欄位維持原值
        assertThat(result.getEmail()).isEqualTo("supplier@example.com");
        assertThat(result.getPhone()).isEqualTo("+886-987654321");
    }

    @Test
    @DisplayName("updateSupplier：status 更新為 INACTIVE")
    void updateSupplier_statusChange_updatesStatus() {
        Supplier existing = supplierOf(supplierId, tenantId, Supplier.SupplierStatus.ACTIVE);
        when(supplierRepository.findByIdAndTenantId(supplierId, tenantId)).thenReturn(Optional.of(existing));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierUpdateRequest request = SupplierUpdateRequest.builder()
                .status("inactive")
                .build();

        SupplierDto result = supplierService.updateSupplier(supplierId, request);

        assertThat(result.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("updateSupplier：不存在時拋出 E_7000")
    void updateSupplier_notFound_throwsE7000() {
        when(supplierRepository.findByIdAndTenantId(supplierId, tenantId)).thenReturn(Optional.empty());

        SupplierUpdateRequest request = SupplierUpdateRequest.builder().name("X").build();

        assertThatThrownBy(() -> supplierService.updateSupplier(supplierId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_7000));
    }
}
