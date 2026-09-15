package com.nextkey.ecommerce.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.nextkey.ecommerce.api.dto.SupportTicketDto.CreateTicketRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.MessageResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.TicketResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.UpdateTicketStatusRequest;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.support.SupportTicket;
import com.nextkey.ecommerce.domain.model.support.SupportTicket.TicketStatus;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * SupportTicketService 單元測試（Sprint 91，PRD §6.10 M18 Phase 2-B）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SupportTicketService 單元測試（Sprint 91）")
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SupportMessageService messageService;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    private SupportTicketService newService() {
        return new SupportTicketService(ticketRepository, orderRepository, messageService);
    }

    private SupportTicket ticket(final TicketStatus status) {
        return SupportTicket.builder()
                .id(TICKET_ID)
                .tenantId(TENANT_ID)
                .ticketNumber("TK-20260709-001")
                .category(SupportTicket.TicketCategory.PRODUCT)
                .subject("商品問題")
                .description("測試描述")
                .status(status)
                .priority(SupportTicket.TicketPriority.NORMAL)
                .customerId(CUSTOMER_ID)
                .build();
    }

    @Test
    @DisplayName("createTicket：無 orderId 時 tenantId 為 null（平台工單）")
    void createTicket_withoutOrderId_hasNullTenantId() {
        SupportTicketService service = newService();
        when(ticketRepository.countByTicketNumberStartingWith(anyString())).thenReturn(0L);
        when(ticketRepository.saveAndFlush(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTicketRequest request = CreateTicketRequest.builder()
                .category("TECHNICAL").subject("帳號問題").description("無法登入").build();

        TicketResponse response = service.createTicket(request, CUSTOMER_ID);

        assertThat(response.getTenantId()).isNull();
        verify(orderRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("createTicket：帶 orderId 時反查訂單所屬租戶")
    void createTicket_withOrderId_derivesTenantFromOrder() {
        SupportTicketService service = newService();
        Order order = Order.builder().id(ORDER_ID).tenantId(TENANT_ID).userId(CUSTOMER_ID).build();
        when(orderRepository.findByIdAndUserId(ORDER_ID, CUSTOMER_ID)).thenReturn(Optional.of(order));
        when(ticketRepository.countByTicketNumberStartingWith(anyString())).thenReturn(0L);
        when(ticketRepository.saveAndFlush(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTicketRequest request = CreateTicketRequest.builder()
                .category("PRODUCT").subject("商品瑕疵").description("收到商品破損").orderId(ORDER_ID).build();

        TicketResponse response = service.createTicket(request, CUSTOMER_ID);

        assertThat(response.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("createTicket：orderId 非自己的訂單應拒絕（回 E_5000，IDOR-safe 不洩漏他人訂單存在）")
    void createTicket_orderNotOwned_throwsE5000() {
        SupportTicketService service = newService();
        when(orderRepository.findByIdAndUserId(ORDER_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        CreateTicketRequest request = CreateTicketRequest.builder()
                .category("PRODUCT").subject("x").description("y").orderId(ORDER_ID).build();

        assertThatThrownBy(() -> service.createTicket(request, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5000));
    }

    @Test
    @DisplayName("DEF-139：ticketNumber 併發撞唯一約束 → 重新產生編號並重試，對使用者無感成功")
    void createTicket_ticketNumberCollision_retriesAndSucceeds() {
        SupportTicketService service = newService();
        when(ticketRepository.countByTicketNumberStartingWith(anyString())).thenReturn(0L);
        when(ticketRepository.saveAndFlush(any(SupportTicket.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateTicketRequest request = CreateTicketRequest.builder()
                .category("TECHNICAL").subject("帳號問題").description("無法登入").build();

        TicketResponse response = service.createTicket(request, CUSTOMER_ID);

        assertThat(response).isNotNull();
        verify(ticketRepository, times(2)).saveAndFlush(any(SupportTicket.class));
    }

    @Test
    @DisplayName("DEF-139：連續撞約束超過重試上限 → 原樣拋出，不無限重試")
    void createTicket_ticketNumberCollisionExceedsRetries_throws() {
        SupportTicketService service = newService();
        when(ticketRepository.countByTicketNumberStartingWith(anyString())).thenReturn(0L);
        when(ticketRepository.saveAndFlush(any(SupportTicket.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreateTicketRequest request = CreateTicketRequest.builder()
                .category("TECHNICAL").subject("帳號問題").description("無法登入").build();

        assertThatThrownBy(() -> service.createTicket(request, CUSTOMER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(ticketRepository, times(3)).saveAndFlush(any(SupportTicket.class));
    }

    @Test
    @DisplayName("getMyTicket：非本人工單應拒絕（回 E_8008）")
    void getMyTicket_notOwner_throwsE8008() {
        SupportTicketService service = newService();
        when(ticketRepository.findByIdAndCustomerId(TICKET_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyTicket(TICKET_ID, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8008));
    }

    @Test
    @DisplayName("getMyTicket：本人工單成功並含訊息串")
    void getMyTicket_owner_succeedsWithMessages() {
        SupportTicketService service = newService();
        when(ticketRepository.findByIdAndCustomerId(TICKET_ID, CUSTOMER_ID)).thenReturn(Optional.of(ticket(TicketStatus.OPEN)));
        when(messageService.listMessagesAsCustomer(TICKET_ID, CUSTOMER_ID)).thenReturn(List.of(MessageResponse.builder().build()));

        TicketResponse response = service.getMyTicket(TICKET_ID, CUSTOMER_ID);

        assertThat(response.getMessages()).hasSize(1);
    }

    @Test
    @DisplayName("getTenantTicket：非 SUPER_ADMIN 查詢別租戶工單應拒絕（回 E_8008）")
    void getTenantTicket_wrongTenant_throwsE8008() {
        SupportTicketService service = newService();
        UUID otherTenantId = UUID.randomUUID();
        when(ticketRepository.findByIdAndTenantId(TICKET_ID, otherTenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTenantTicket(TICKET_ID, otherTenantId, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8008));
    }

    @Test
    @DisplayName("getTenantTicket：SUPER_ADMIN 跨租戶查詢不受租戶篩選限制")
    void getTenantTicket_superAdmin_bypassesTenantFilter() {
        SupportTicketService service = newService();
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket(TicketStatus.OPEN)));
        when(messageService.listMessagesAsStaff(TICKET_ID, null, true)).thenReturn(List.of());

        TicketResponse response = service.getTenantTicket(TICKET_ID, null, true);

        assertThat(response.getId()).isEqualTo(TICKET_ID);
        verify(ticketRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("updateStatus：OPEN → IN_PROGRESS 為合法轉換")
    void updateStatus_openToInProgress_succeeds() {
        SupportTicketService service = newService();
        when(ticketRepository.findByIdAndTenantId(TICKET_ID, TENANT_ID)).thenReturn(Optional.of(ticket(TicketStatus.OPEN)));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTicketStatusRequest request = UpdateTicketStatusRequest.builder().status("IN_PROGRESS").build();
        TicketResponse response = service.updateStatus(TICKET_ID, request, TENANT_ID, false);

        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("updateStatus：CLOSED 為終態，再轉換應拒絕（回 E_8010）")
    void updateStatus_fromClosed_throwsE8010() {
        SupportTicketService service = newService();
        when(ticketRepository.findByIdAndTenantId(TICKET_ID, TENANT_ID)).thenReturn(Optional.of(ticket(TicketStatus.CLOSED)));

        UpdateTicketStatusRequest request = UpdateTicketStatusRequest.builder().status("OPEN").build();

        assertThatThrownBy(() -> service.updateStatus(TICKET_ID, request, TENANT_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8010));
    }

    @Test
    @DisplayName("updateStatus：轉為 RESOLVED 時寫入 resolvedAt")
    void updateStatus_toResolved_setsResolvedAt() {
        SupportTicketService service = newService();
        when(ticketRepository.findByIdAndTenantId(TICKET_ID, TENANT_ID))
                .thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS)));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTicketStatusRequest request = UpdateTicketStatusRequest.builder().status("RESOLVED").build();
        TicketResponse response = service.updateStatus(TICKET_ID, request, TENANT_ID, false);

        assertThat(response.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("listTenantTickets：分頁結果正確轉換")
    void listTenantTickets_returnsListResponse() {
        SupportTicketService service = newService();
        Page<SupportTicket> page = new PageImpl<>(List.of(ticket(TicketStatus.OPEN)));
        when(ticketRepository.findByTenantIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        var response = service.listTenantTickets(TENANT_ID, 0, 20);

        assertThat(response.getTickets()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sprint 164: listMyTickets size 帶超大值時，實際查詢頁面大小上限為 100（買家自助端點，避免資源耗盡）")
    void listMyTickets_hugeSize_cappedAt100() {
        SupportTicketService service = newService();
        when(ticketRepository.findByCustomerIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMyTickets(CUSTOMER_ID, 0, 999999999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findByCustomerIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(CUSTOMER_ID), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Sprint 164: listTenantTickets size 帶超大值時，實際查詢頁面大小上限為 100（避免資源耗盡）")
    void listTenantTickets_hugeSize_cappedAt100() {
        SupportTicketService service = newService();
        when(ticketRepository.findByTenantIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listTenantTickets(TENANT_ID, 0, 999999999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findByTenantIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Sprint 164: listAllTickets size 帶超大值時，實際查詢頁面大小上限為 100（平台跨租戶端點，避免資源耗盡）")
    void listAllTickets_hugeSize_cappedAt100() {
        SupportTicketService service = newService();
        when(ticketRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listAllTickets(0, 999999999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
