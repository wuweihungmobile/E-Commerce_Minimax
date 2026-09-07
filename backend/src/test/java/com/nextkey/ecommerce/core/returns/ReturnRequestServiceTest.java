package com.nextkey.ecommerce.core.returns;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.returns.ReturnDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest.ReturnStatus;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.returns.ReturnRequestRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReturnRequestService 單元測試（Sprint 135，DEF-110：稽核日誌覆蓋率）。
 *
 * <p>範圍：approveReturn/rejectReturn 是否正確記錄稽核；不重新涵蓋整個服務（本輪範圍外）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnRequestService 單元測試（DEF-110）")
class ReturnRequestServiceTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductInventoryService productInventoryService;
    @Mock
    private ProductSkuRepository productSkuRepository;
    @Mock
    private AuditService auditService;

    private ReturnRequestService returnRequestService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID RETURN_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        returnRequestService = new ReturnRequestService(
                returnRequestRepository, orderRepository, productInventoryService, productSkuRepository, auditService);
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUser(REVIEWER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReturnRequest requestedReturn() {
        return ReturnRequest.builder()
                .id(RETURN_ID)
                .tenantId(TENANT_ID)
                .returnNumber("RMA-20260907-TEST0001")
                .status(ReturnStatus.REQUESTED)
                .build();
    }

    @Test
    @DisplayName("approveReturn：核准並記錄稽核")
    void approveReturn_success_recordsAudit() {
        ReturnRequest request = requestedReturn();
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(request));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnDto.Response response = returnRequestService.approveReturn(RETURN_ID);

        assertThat(response.getId()).isEqualTo(RETURN_ID);
        assertThat(request.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(REVIEWER_ID);
        verify(auditService).record(eq("RETURN_APPROVED"), eq("RETURN_REQUEST"), eq(RETURN_ID), eq(TENANT_ID),
                eq("REQUESTED"), eq("APPROVED"), any());
    }

    @Test
    @DisplayName("rejectReturn：駁回並記錄稽核與原因")
    void rejectReturn_success_recordsAuditWithReason() {
        ReturnRequest request = requestedReturn();
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(request));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnDto.RejectRequest body = ReturnDto.RejectRequest.builder().rejectionReason("已超過鑑賞期").build();
        ReturnDto.Response response = returnRequestService.rejectReturn(RETURN_ID, body);

        assertThat(response.getId()).isEqualTo(RETURN_ID);
        assertThat(request.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        verify(auditService).record(eq("RETURN_REJECTED"), eq("RETURN_REQUEST"), eq(RETURN_ID), eq(TENANT_ID),
                eq("REQUESTED"), eq("REJECTED"), eq("已超過鑑賞期"));
    }
}
