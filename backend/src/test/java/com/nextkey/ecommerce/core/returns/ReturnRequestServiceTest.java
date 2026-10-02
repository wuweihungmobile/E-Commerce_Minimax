package com.nextkey.ecommerce.core.returns;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.dto.returns.ReturnDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest.ReturnStatus;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.returns.ReturnRequestRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReturnRequestService 單元測試（Sprint 135 DEF-110：稽核日誌覆蓋率；Sprint 141
 * DEF-130/131/132/133：併發防護守衛）。
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
    /** 沒有店鋪的使用者（一般買家）的租戶：系統租戶佔位值，不是 null，且所有這類使用者共用。 */
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

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
        SecurityContextHolder.clearContext();
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
        when(returnRequestRepository.reviewIfStatus(eq(RETURN_ID), eq(ReturnStatus.REQUESTED),
                eq(ReturnStatus.APPROVED), any(), any(), isNull())).thenReturn(1);
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
        when(returnRequestRepository.reviewIfStatus(eq(RETURN_ID), eq(ReturnStatus.REQUESTED),
                eq(ReturnStatus.REJECTED), any(), any(), eq("已超過鑑賞期"))).thenReturn(1);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnDto.RejectRequest body = ReturnDto.RejectRequest.builder().rejectionReason("已超過鑑賞期").build();
        ReturnDto.Response response = returnRequestService.rejectReturn(RETURN_ID, body);

        assertThat(response.getId()).isEqualTo(RETURN_ID);
        assertThat(request.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        verify(auditService).record(eq("RETURN_REJECTED"), eq("RETURN_REQUEST"), eq(RETURN_ID), eq(TENANT_ID),
                eq("REQUESTED"), eq("REJECTED"), eq("已超過鑑賞期"));
    }

    @Test
    @DisplayName("approveReturn：併發搶占失敗（已被另一併發請求轉為終態）→ E_5017（DEF-130）")
    void approveReturn_concurrentClaimLost_throwsE5017() {
        ReturnRequest request = requestedReturn();
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(request));
        when(returnRequestRepository.reviewIfStatus(eq(RETURN_ID), eq(ReturnStatus.REQUESTED),
                eq(ReturnStatus.APPROVED), any(), any(), isNull())).thenReturn(0);

        assertThatThrownBy(() -> returnRequestService.approveReturn(RETURN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5017);
    }

    @Test
    @DisplayName("rejectReturn：併發搶占失敗（已被另一併發請求轉為終態）→ E_5017（DEF-133）")
    void rejectReturn_concurrentClaimLost_throwsE5017() {
        ReturnRequest request = requestedReturn();
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(request));
        when(returnRequestRepository.reviewIfStatus(eq(RETURN_ID), eq(ReturnStatus.REQUESTED),
                eq(ReturnStatus.REJECTED), any(), any(), isNull())).thenReturn(0);

        ReturnDto.RejectRequest body = ReturnDto.RejectRequest.builder().build();
        assertThatThrownBy(() -> returnRequestService.rejectReturn(RETURN_ID, body))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5017);
    }

    // ── cancelReturnRequest（DEF-131） ───────────────────────────

    private ReturnRequest requestedReturnOfCustomer() {
        return ReturnRequest.builder()
                .id(RETURN_ID)
                .tenantId(TENANT_ID)
                .customerId(REVIEWER_ID)
                .returnNumber("RMA-20260907-TEST0001")
                .status(ReturnStatus.REQUESTED)
                .build();
    }

    @Test
    @DisplayName("cancelReturnRequest：買家撤回成功")
    void cancelReturnRequest_success_cancelsSuccessfully() {
        ReturnRequest request = requestedReturnOfCustomer();
        Set<ReturnStatus> cancellable = EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(request));
        when(returnRequestRepository.cancelIfStatusIn(RETURN_ID, cancellable, ReturnStatus.CANCELLED))
                .thenReturn(1);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnDto.Response response = returnRequestService.cancelReturnRequest(RETURN_ID);

        assertThat(response.getId()).isEqualTo(RETURN_ID);
        assertThat(request.getStatus()).isEqualTo(ReturnStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelReturnRequest：併發搶占失敗（已被店家 approve/reject 或 receive）→ E_5017")
    void cancelReturnRequest_concurrentClaimLost_throwsE5017() {
        ReturnRequest request = requestedReturnOfCustomer();
        Set<ReturnStatus> cancellable = EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(request));
        when(returnRequestRepository.cancelIfStatusIn(RETURN_ID, cancellable, ReturnStatus.CANCELLED))
                .thenReturn(0);

        assertThatThrownBy(() -> returnRequestService.cancelReturnRequest(RETURN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_5017);
    }

    // ── createReturnRequest（DEF-132） ───────────────────────────

    /**
     * DEF-132：{@code createReturnRequest} 必須用悲觀鎖版本的查詢載入訂單，序列化
     * 「讀各品項已申請退貨總量→比對可退量→建立新退貨單」，否則兩個併發申請可能都讀到
     * 彼此 INSERT 之前的舊總量，使同一訂單品項的退貨申請總量超過實際購買量。守衛：一旦
     * 有人改回不上鎖的 {@code findById}，這個測試會立刻失敗。
     */
    @Test
    @DisplayName("createReturnRequest：必須用悲觀鎖查詢載入訂單，不得退回不上鎖的查詢（DEF-132）")
    void createReturnRequest_usesLockedLookup() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId).userId(REVIEWER_ID).tenantId(TENANT_ID)
                .status(Order.OrderStatus.DELIVERED)
                .items(List.of(OrderItem.builder().id(orderItemId).quantity(2).build()))
                .build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(returnRequestRepository.sumActiveRequestedQtyByOrderItem(orderItemId)).thenReturn(0);
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnDto.CreateRequest request = ReturnDto.CreateRequest.builder()
                .orderId(orderId).reason("不合適")
                .items(List.of(ReturnDto.CreateItem.builder().orderItemId(orderItemId).quantity(1).build()))
                .build();

        returnRequestService.createReturnRequest(request);

        verify(orderRepository).findByIdForUpdate(orderId);
        verify(orderRepository, never()).findById(orderId);
    }

    // ========== Sprint 234：沒有店鋪的呼叫者（系統租戶）不得碰到店家層退貨申請 ==========
    //
    // 一般消費者的退貨申請蓋的是訂單的租戶（系統租戶），BUYER 又持有 return:read；店家層端點原本用「呼叫者的租戶」
    // 查詢與比對，任何買家都能列出所有消費者的退貨申請，也能憑 id 讀取（系統租戶彼此互為「同租戶」）。

    private ReturnRequest returnOfSystemTenant(final UUID customerId) {
        return ReturnRequest.builder()
                .id(RETURN_ID)
                .tenantId(SYSTEM_TENANT_ID)
                .customerId(customerId)
                .returnNumber("RMA-20261002-SYS00001")
                .status(ReturnStatus.REQUESTED)
                .build();
    }

    @Test
    @DisplayName("Sprint 234：getTenantReturnRequests 系統租戶的非 admin 呼叫者 → 空頁，完全不查詢")
    void getTenantReturnRequests_systemTenantNonAdmin_returnsEmptyWithoutQuerying() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        Pageable pageable = PageRequest.of(0, 20);

        Page<ReturnDto.Response> result = returnRequestService.getTenantReturnRequests(pageable);

        assertThat(result.getContent()).isEmpty();
        verify(returnRequestRepository, never()).findByTenantIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("Sprint 234：getTenantReturnRequests 真正的店鋪租戶照常查詢本租戶的退貨申請")
    void getTenantReturnRequests_storeTenant_stillQueries() {
        Pageable pageable = PageRequest.of(0, 20);
        when(returnRequestRepository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(requestedReturn())));

        Page<ReturnDto.Response> result = returnRequestService.getTenantReturnRequests(pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Sprint 234：getTenantReturnRequests admin 在系統租戶脈絡下維持原行為（平台端檢視）")
    void getTenantReturnRequests_adminInSystemTenant_stillQueries() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Pageable pageable = PageRequest.of(0, 20);
        when(returnRequestRepository.findByTenantIdOrderByCreatedAtDesc(SYSTEM_TENANT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(returnOfSystemTenant(UUID.randomUUID()))));

        Page<ReturnDto.Response> result = returnRequestService.getTenantReturnRequests(pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Sprint 234：getReturnRequest 兩個都在系統租戶的一般買家，不得讀取對方的退貨申請（E_1007）")
    void getReturnRequest_otherBuyerInSystemTenant_throwsE1007() {
        UUID owner = UUID.randomUUID();
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        TenantContext.setCurrentUser(UUID.randomUUID());
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnOfSystemTenant(owner)));

        assertThatThrownBy(() -> returnRequestService.getReturnRequest(RETURN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }

    @Test
    @DisplayName("Sprint 234：getReturnRequest 退貨申請的提出人本人，在系統租戶下仍可讀取自己的申請")
    void getReturnRequest_owner_inSystemTenant_stillWorks() {
        UUID owner = UUID.randomUUID();
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        TenantContext.setCurrentUser(owner);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnOfSystemTenant(owner)));

        ReturnDto.Response response = returnRequestService.getReturnRequest(RETURN_ID);

        assertThat(response.getId()).isEqualTo(RETURN_ID);
    }

    @Test
    @DisplayName("Sprint 234：approveReturn 系統租戶的非 admin 呼叫者 → E_1007（店家層載入也要求真正的店鋪租戶）")
    void approveReturn_systemTenantCaller_throwsE1007() {
        TenantContext.setCurrentTenant(SYSTEM_TENANT_ID);
        when(returnRequestRepository.findById(RETURN_ID)).thenReturn(Optional.of(returnOfSystemTenant(UUID.randomUUID())));

        assertThatThrownBy(() -> returnRequestService.approveReturn(RETURN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1007);
    }
}
