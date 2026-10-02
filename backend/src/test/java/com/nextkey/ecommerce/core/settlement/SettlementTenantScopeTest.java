package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;

/**
 * 結算單店家層端點的租戶範圍（Sprint 234）。
 *
 * <p>{@code GET /v2/settlements}、{@code /{id}}、{@code PUT /{id}/submit} 只要求 {@code order:read}，
 * 而 {@code BUYER} 持有它；每週結算又會替每個 ACTIVE 租戶（含系統租戶）產生結算單。原本直接拿
 * 「呼叫者的租戶」查詢，沒有店鋪的買家（租戶是系統租戶佔位值）就讀得到平台的 GMV、抽成與退款，
 * 並能把結算單推進到待審核。以下案例斷言：沒有店鋪的呼叫者，repository 完全不會被查詢。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Sprint 234: 結算單店家層端點的租戶範圍")
class SettlementTenantScopeTest {

    @Mock private SettlementStatementRepository settlementRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SettlementAdjustmentRepository adjustmentRepository;
    @Mock private SettlementMapper mapper;
    @Mock private TransferService transferService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private static final UUID STORE_TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    /** 沒有店鋪的使用者（一般買家）的租戶：系統租戶佔位值，不是 null，且所有這類使用者共用。 */
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    private SettlementGenerator generator;
    private SettlementReviewer reviewer;

    @BeforeEach
    void setUp() {
        generator = new SettlementGenerator(settlementRepository, tenantRepository, orderRepository,
                paymentRepository, adjustmentRepository, new SettlementCalculator(), mapper);
        reviewer = new SettlementReviewer(settlementRepository, mapper, transferService, userRepository,
                auditService, orderRepository, adjustmentRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getStatementsByTenant：系統租戶的呼叫者 → 空清單，完全不查詢")
    void getStatementsByTenant_systemTenant_returnsEmptyWithoutQuerying() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);

        SettlementStatementListResponse response = generator.getStatementsByTenant(0, 20);

        assertThat(response.getStatements()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        verify(settlementRepository, never()).findByTenantIdOrderByPeriodStartDesc(any(), any());
    }

    @Test
    @DisplayName("getStatementsByTenant：真正的店鋪租戶照常查詢本租戶的結算單")
    void getStatementsByTenant_storeTenant_stillQueries() {
        TenantContext.setCurrentTenant(STORE_TENANT_ID);
        when(settlementRepository.findByTenantIdOrderByPeriodStartDesc(any(UUID.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        SettlementStatementListResponse response = generator.getStatementsByTenant(0, 20);

        assertThat(response.getStatements()).isEmpty();
        verify(settlementRepository).findByTenantIdOrderByPeriodStartDesc(any(UUID.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getStatementById：系統租戶的呼叫者 → 找不到（E_5013），完全不查詢")
    void getStatementById_systemTenant_throwsE5013WithoutQuerying() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);

        assertThatThrownBy(() -> generator.getStatementById(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5013);

        verify(settlementRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("submitForReview：系統租戶的呼叫者 → 找不到（E_5013），不查詢、不改任何結算單狀態")
    void submitForReview_systemTenant_throwsE5013WithoutQuerying() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);

        assertThatThrownBy(() -> reviewer.submitForReview(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5013);

        verify(settlementRepository, never()).findByIdAndTenantId(any(), any());
        verify(settlementRepository, never()).save(any(SettlementStatement.class));
    }
}
