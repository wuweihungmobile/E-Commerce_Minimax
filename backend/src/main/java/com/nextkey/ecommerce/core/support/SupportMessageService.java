package com.nextkey.ecommerce.core.support;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.SupportTicketDto.CreateMessageRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.MessageResponse;
import com.nextkey.ecommerce.domain.model.support.SupportMessage;
import com.nextkey.ecommerce.domain.model.support.SupportMessage.SenderType;
import com.nextkey.ecommerce.domain.model.support.SupportTicket;
import com.nextkey.ecommerce.domain.repository.support.SupportMessageRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服工單訊息串服務（PRD §6.10 M18 Phase 2-B，Sprint 91）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportMessageService {

    private final SupportMessageRepository messageRepository;
    private final SupportTicketRepository ticketRepository;

    /**
     * 買家在自己的工單發送訊息。工單擁有權以 {@code customerId} 比對。
     */
    @Transactional
    public MessageResponse postAsCustomer(
            final UUID ticketId, final CreateMessageRequest request, final UUID customerId) {
        ticketRepository.findByIdAndCustomerId(ticketId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        return post(ticketId, request, customerId, SenderType.CUSTOMER);
    }

    /**
     * 店家/平台在工單回覆訊息。非 SUPER_ADMIN 限自己租戶的工單。
     */
    @Transactional
    public MessageResponse postAsStaff(
            final UUID ticketId, final CreateMessageRequest request, final UUID senderId,
            final UUID callerTenantId, final boolean isSuperAdmin) {
        SupportTicket ticket = isSuperAdmin
                ? ticketRepository.findById(ticketId).orElseThrow(() -> new BusinessException(ErrorCode.E_8008))
                : ticketRepository.findByIdAndTenantId(ticketId, callerTenantId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        return post(ticket.getId(), request, senderId, SenderType.STAFF);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessagesAsCustomer(final UUID ticketId, final UUID customerId) {
        ticketRepository.findByIdAndCustomerId(ticketId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        return listMessages(ticketId);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessagesAsStaff(
            final UUID ticketId, final UUID callerTenantId, final boolean isSuperAdmin) {
        if (!isSuperAdmin) {
            ticketRepository.findByIdAndTenantId(ticketId, callerTenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        } else if (ticketRepository.findById(ticketId).isEmpty()) {
            throw new BusinessException(ErrorCode.E_8008);
        }
        return listMessages(ticketId);
    }

    private MessageResponse post(
            final UUID ticketId, final CreateMessageRequest request, final UUID senderId, final SenderType type) {
        SupportMessage message = SupportMessage.builder()
                .ticketId(ticketId)
                .senderId(senderId)
                .senderType(type)
                .message(request.getMessage())
                .attachments(request.getAttachments())
                .build();
        message = messageRepository.save(message);
        log.info("Support message posted: ticketId={}, senderType={}", ticketId, type);
        return toResponse(message);
    }

    private List<MessageResponse> listMessages(final UUID ticketId) {
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private MessageResponse toResponse(final SupportMessage message) {
        return MessageResponse.builder()
                .id(message.getId())
                .ticketId(message.getTicketId())
                .senderId(message.getSenderId())
                .senderType(message.getSenderType().name())
                .message(message.getMessage())
                .attachments(message.getAttachments())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
