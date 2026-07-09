package com.nextkey.ecommerce.core.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.SupportTicketDto.AssignTicketRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.CreateTicketRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.TicketListResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.TicketResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.UpdateTicketStatusRequest;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.support.SupportTicket;
import com.nextkey.ecommerce.domain.model.support.SupportTicket.TicketCategory;
import com.nextkey.ecommerce.domain.model.support.SupportTicket.TicketPriority;
import com.nextkey.ecommerce.domain.model.support.SupportTicket.TicketStatus;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.support.SupportTicketRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服工單生命週期服務（PRD §6.10 M18 Phase 2-B，Sprint 91）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private static final DateTimeFormatter TICKET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SupportTicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final SupportMessageService messageService;

    /**
     * 買家提交工單。若帶 {@code orderId}，反查該訂單所屬租戶並驗證擁有權（僅能用自己的訂單建立關聯工單）；
     * 若無 {@code orderId}，視為平台工單（{@code tenantId} 為 null）。
     */
    @Transactional
    public TicketResponse createTicket(final CreateTicketRequest request, final UUID customerId) {
        UUID tenantId = null;
        if (request.getOrderId() != null) {
            Order order = orderRepository.findByIdAndUserId(request.getOrderId(), customerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_5000));
            tenantId = order.getTenantId();
        }

        SupportTicket ticket = SupportTicket.builder()
                .tenantId(tenantId)
                .ticketNumber(generateTicketNumber())
                .category(parseEnum(TicketCategory.class, request.getCategory(), ErrorCode.E_8010))
                .subject(request.getSubject())
                .description(request.getDescription())
                .customerId(customerId)
                .orderId(request.getOrderId())
                .build();
        ticket = ticketRepository.save(ticket);
        log.info("Support ticket created: id={}, ticketNumber={}, customerId={}", ticket.getId(),
                ticket.getTicketNumber(), customerId);
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketListResponse listMyTickets(final UUID customerId, final int page, final int size) {
        Page<SupportTicket> tickets = ticketRepository.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(page, size));
        return toListResponse(tickets, page, size);
    }

    @Transactional(readOnly = true)
    public TicketResponse getMyTicket(final UUID ticketId, final UUID customerId) {
        SupportTicket ticket = ticketRepository.findByIdAndCustomerId(ticketId, customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        TicketResponse response = toResponse(ticket);
        response.setMessages(messageService.listMessagesAsCustomer(ticketId, customerId));
        return response;
    }

    /**
     * 店家工單列表。非 SUPER_ADMIN 限自己租戶；SUPER_ADMIN 可指定 tenantId 查詢。
     */
    @Transactional(readOnly = true)
    public TicketListResponse listTenantTickets(
            final UUID tenantId, final int page, final int size) {
        Page<SupportTicket> tickets = ticketRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId, PageRequest.of(page, size));
        return toListResponse(tickets, page, size);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTenantTicket(final UUID ticketId, final UUID tenantId, final boolean isSuperAdmin) {
        SupportTicket ticket = findForStaff(ticketId, tenantId, isSuperAdmin);
        TicketResponse response = toResponse(ticket);
        response.setMessages(messageService.listMessagesAsStaff(ticketId, tenantId, isSuperAdmin));
        return response;
    }

    /**
     * 平台工單列表，跨租戶（僅 ADMIN/SUPER_ADMIN，Controller 已用 {@code support_ticket:manage:all} 鎖死）。
     */
    @Transactional(readOnly = true)
    public TicketListResponse listAllTickets(final int page, final int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SupportTicket> tickets = ticketRepository.findAll(pageable);
        return toListResponse(tickets, page, size);
    }

    /**
     * 更新工單狀態/優先級（店家或平台）。非 SUPER_ADMIN 限自己租戶。
     */
    @Transactional
    public TicketResponse updateStatus(
            final UUID ticketId, final UpdateTicketStatusRequest request,
            final UUID callerTenantId, final boolean isSuperAdmin) {
        SupportTicket ticket = findForStaff(ticketId, callerTenantId, isSuperAdmin);

        TicketStatus newStatus = parseEnum(TicketStatus.class, request.getStatus(), ErrorCode.E_8010);
        validateStatusTransition(ticket.getStatus(), newStatus);
        ticket.setStatus(newStatus);

        if (request.getPriority() != null) {
            ticket.setPriority(parseEnum(TicketPriority.class, request.getPriority(), ErrorCode.E_8010));
        }
        if (newStatus == TicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(Instant.now());
        }

        ticket = ticketRepository.save(ticket);
        log.info("Support ticket status updated: id={}, newStatus={}", ticketId, newStatus);
        return toResponse(ticket);
    }

    /**
     * 指派工單處理人（僅 ADMIN/SUPER_ADMIN，跨租戶，不做租戶篩選——比照 Sprint 85 M16 審批端點的既有慣例：
     * 存取邊界完全交給 Controller {@code @PreAuthorize("hasAuthority('support_ticket:manage:all')")}）。
     */
    @Transactional
    public TicketResponse assignTicket(final UUID ticketId, final AssignTicketRequest request) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        ticket.setAssignedTo(request.getAssignedTo());
        ticket = ticketRepository.save(ticket);
        log.info("Support ticket assigned: id={}, assignedTo={}", ticketId, request.getAssignedTo());
        return toResponse(ticket);
    }

    private SupportTicket findForStaff(final UUID ticketId, final UUID callerTenantId, final boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
        }
        return ticketRepository.findByIdAndTenantId(ticketId, callerTenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8008));
    }

    /**
     * 狀態機驗證：{@code OPEN -> IN_PROGRESS/CLOSED}、{@code IN_PROGRESS -> RESOLVED/CLOSED}、
     * {@code RESOLVED -> CLOSED}；{@code CLOSED} 為終態，不可再轉換。
     */
    private void validateStatusTransition(final TicketStatus from, final TicketStatus to) {
        boolean valid = switch (from) {
            case OPEN -> to == TicketStatus.IN_PROGRESS || to == TicketStatus.CLOSED;
            case IN_PROGRESS -> to == TicketStatus.RESOLVED || to == TicketStatus.CLOSED;
            case RESOLVED -> to == TicketStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.E_8010,
                    "Cannot transition ticket status from " + from + " to " + to);
        }
    }

    private <T extends Enum<T>> T parseEnum(final Class<T> enumType, final String value, final ErrorCode errorCode) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(errorCode, "Invalid value: " + value);
        }
    }

    private String generateTicketNumber() {
        String dateStr = Instant.now().atZone(ZoneOffset.UTC).format(TICKET_DATE_FORMAT);
        String prefix = "TK-" + dateStr + "-";
        long countToday = ticketRepository.countByTicketNumberStartingWith(prefix);
        return prefix + String.format("%03d", countToday + 1);
    }

    private TicketResponse toResponse(final SupportTicket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .tenantId(ticket.getTenantId())
                .ticketNumber(ticket.getTicketNumber())
                .category(ticket.getCategory().name())
                .subject(ticket.getSubject())
                .description(ticket.getDescription())
                .status(ticket.getStatus().name())
                .priority(ticket.getPriority().name())
                .customerId(ticket.getCustomerId())
                .assignedTo(ticket.getAssignedTo())
                .orderId(ticket.getOrderId())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .build();
    }

    private TicketListResponse toListResponse(final Page<SupportTicket> tickets, final int page, final int size) {
        List<TicketResponse> content = tickets.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return TicketListResponse.builder()
                .tickets(content)
                .page(page)
                .size(size)
                .totalElements(tickets.getTotalElements())
                .totalPages(tickets.getTotalPages())
                .build();
    }
}
