package com.nextkey.ecommerce.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.SupportTicketDto.CreateMessageRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.MessageResponse;
import com.nextkey.ecommerce.domain.model.support.SupportMessage;
import com.nextkey.ecommerce.domain.model.support.SupportMessage.SenderType;
import com.nextkey.ecommerce.domain.model.support.SupportTicket;
import com.nextkey.ecommerce.domain.repository.support.SupportMessageRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * SupportMessageService 單元測試（Sprint 91，PRD §6.10 M18 Phase 2-B）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SupportMessageService 單元測試（Sprint 91）")
class SupportMessageServiceTest {

    @Mock
    private SupportMessageRepository messageRepository;

    @Mock
    private SupportTicketRepository ticketRepository;

    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();

    private SupportMessageService newService() {
        return new SupportMessageService(messageRepository, ticketRepository);
    }

    private SupportTicket ticket() {
        return SupportTicket.builder().id(TICKET_ID).tenantId(TENANT_ID).customerId(CUSTOMER_ID).build();
    }

    @Test
    @DisplayName("postAsCustomer：非本人工單應拒絕（回 E_8008）")
    void postAsCustomer_notOwner_throwsE8008() {
        SupportMessageService service = newService();
        when(ticketRepository.findByIdAndCustomerId(TICKET_ID, CUSTOMER_ID)).thenReturn(Optional.empty());

        CreateMessageRequest request = CreateMessageRequest.builder().message("hi").build();

        assertThatThrownBy(() -> service.postAsCustomer(TICKET_ID, request, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8008));
    }

    @Test
    @DisplayName("postAsCustomer：本人工單成功，sender_type 為 CUSTOMER")
    void postAsCustomer_owner_succeeds() {
        SupportMessageService service = newService();
        when(ticketRepository.findByIdAndCustomerId(TICKET_ID, CUSTOMER_ID)).thenReturn(Optional.of(ticket()));
        when(messageRepository.save(any(SupportMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMessageRequest request = CreateMessageRequest.builder().message("有問題").build();
        MessageResponse response = service.postAsCustomer(TICKET_ID, request, CUSTOMER_ID);

        assertThat(response.getSenderType()).isEqualTo(SenderType.CUSTOMER.name());
        assertThat(response.getMessage()).isEqualTo("有問題");
    }

    @Test
    @DisplayName("postAsStaff：非 SUPER_ADMIN 回覆別租戶工單應拒絕（回 E_8008）")
    void postAsStaff_wrongTenant_throwsE8008() {
        SupportMessageService service = newService();
        UUID otherTenantId = UUID.randomUUID();
        when(ticketRepository.findByIdAndTenantId(TICKET_ID, otherTenantId)).thenReturn(Optional.empty());

        CreateMessageRequest request = CreateMessageRequest.builder().message("回覆").build();

        assertThatThrownBy(() -> service.postAsStaff(TICKET_ID, request, STAFF_ID, otherTenantId, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_8008));
    }

    @Test
    @DisplayName("postAsStaff：自己租戶工單成功，sender_type 為 STAFF")
    void postAsStaff_ownTenant_succeeds() {
        SupportMessageService service = newService();
        when(ticketRepository.findByIdAndTenantId(TICKET_ID, TENANT_ID)).thenReturn(Optional.of(ticket()));
        when(messageRepository.save(any(SupportMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMessageRequest request = CreateMessageRequest.builder().message("已處理").build();
        MessageResponse response = service.postAsStaff(TICKET_ID, request, STAFF_ID, TENANT_ID, false);

        assertThat(response.getSenderType()).isEqualTo(SenderType.STAFF.name());
    }

    @Test
    @DisplayName("listMessagesAsCustomer：依時間排序回傳完整訊息串")
    void listMessagesAsCustomer_returnsOrderedMessages() {
        SupportMessageService service = newService();
        when(ticketRepository.findByIdAndCustomerId(TICKET_ID, CUSTOMER_ID)).thenReturn(Optional.of(ticket()));
        SupportMessage msg = SupportMessage.builder()
                .id(UUID.randomUUID()).ticketId(TICKET_ID).senderId(CUSTOMER_ID)
                .senderType(SenderType.CUSTOMER).message("m1").build();
        when(messageRepository.findByTicketIdOrderByCreatedAtAsc(TICKET_ID)).thenReturn(List.of(msg));

        List<MessageResponse> messages = service.listMessagesAsCustomer(TICKET_ID, CUSTOMER_ID);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getMessage()).isEqualTo("m1");
    }
}
