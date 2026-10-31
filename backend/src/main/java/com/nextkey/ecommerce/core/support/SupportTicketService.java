package com.nextkey.ecommerce.core.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
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
import com.nextkey.ecommerce.infrastructure.persistence.UniqueConflictRetry;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.util.PageableUtils;

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
    private final UniqueConflictRetry uniqueConflictRetry;

    /**
     * 買家提交工單。若帶 {@code orderId}，反查該訂單所屬租戶並驗證擁有權（僅能用自己的訂單建立關聯工單）；
     * 若無 {@code orderId}，視為平台工單（{@code tenantId} 為 null）。
     */
    public TicketResponse createTicket(final CreateTicketRequest request, final UUID customerId) {
        // 併發防護（DEF-139）：generateTicketNumber() 的 countByTicketNumberStartingWith 是
        // check-then-act，同一天同一 prefix 內兩個併發請求可能算出相同編號。DB 已有
        // ticket_number UNIQUE 約束兜底資料完整性。由於編號是系統產生、非使用者輸入，撞號時重新產生編號再試，
        // 對使用者而言完全無感，不需要手動重新送出表單。
        // DEF-441：原本在同一個交易裡 catch 後重新產生編號再 saveAndFlush，但 PostgreSQL 在唯一約束違反後整個交易已中止，
        // 重算編號的 count 查詢必然失敗（500 E-9900），「有界重試」從來沒有生效過。改成撞號時整個交易丟掉、在新交易重做
        // （重做時 count 已包含贏家那張工單，編號自然錯開）；元件說明見 UniqueConflictRetry。
        return uniqueConflictRetry.execute(() -> createTicketOnce(request, customerId));
    }

    private TicketResponse createTicketOnce(final CreateTicketRequest request, final UUID customerId) {
        UUID tenantId = null;
        if (request.getOrderId() != null) {
            Order order = orderRepository.findByIdAndUserId(request.getOrderId(), customerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_5000));
            tenantId = order.getTenantId();
        }

        TicketCategory category = parseEnum(TicketCategory.class, request.getCategory(), ErrorCode.E_8010);

        SupportTicket ticket = ticketRepository.saveAndFlush(SupportTicket.builder()
                .tenantId(tenantId)
                .ticketNumber(generateTicketNumber())
                .category(category)
                .subject(request.getSubject())
                .description(request.getDescription())
                .customerId(customerId)
                .orderId(request.getOrderId())
                .build());
        log.info("Support ticket created: id={}, ticketNumber={}, customerId={}", ticket.getId(),
                ticket.getTicketNumber(), customerId);
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketListResponse listMyTickets(final UUID customerId, final int page, final int size) {
        Page<SupportTicket> tickets = ticketRepository.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageableUtils.of(page, size, 100));
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
     *
     * <p>Sprint 234：沒有店鋪的呼叫者（租戶是系統租戶佔位值，見 {@link TenantContext#isStoreTenant}）回空頁。
     * 一般消費者的訂單蓋的正是這個租戶、工單的租戶取自訂單，原本直接拿它查，任何買家（{@code BUYER} 持有
     * {@code support_ticket:read}）都看得到所有消費者的客服工單。
     */
    @Transactional(readOnly = true)
    public TicketListResponse listTenantTickets(
            final UUID tenantId, final int page, final int size, final boolean isSuperAdmin) {
        if (!isSuperAdmin && !TenantContext.isStoreTenant(tenantId)) {
            return toListResponse(Page.empty(PageableUtils.of(page, size, 100)), page, size);
        }
        Page<SupportTicket> tickets = ticketRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId, PageableUtils.of(page, size, 100));
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
        Pageable pageable = PageableUtils.of(page, size, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
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
        // Sprint 234：沒有店鋪的呼叫者一律當作找不到（不洩漏工單是否存在），見 listTenantTickets 的說明
        if (!TenantContext.isStoreTenant(callerTenantId)) {
            throw new BusinessException(ErrorCode.E_8008);
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
