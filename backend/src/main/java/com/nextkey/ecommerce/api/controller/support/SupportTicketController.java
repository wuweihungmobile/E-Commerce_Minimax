package com.nextkey.ecommerce.api.controller.support;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.AssignTicketRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.CreateMessageRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.CreateTicketRequest;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.MessageResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.TicketListResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.TicketResponse;
import com.nextkey.ecommerce.api.dto.SupportTicketDto.UpdateTicketStatusRequest;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.support.SupportMessageService;
import com.nextkey.ecommerce.core.support.SupportTicketService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服工單 REST API（PRD §6.10 M18 Phase 2-B，Sprint 91），涵蓋買家/店家/平台三層路由
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class SupportTicketController {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final SupportTicketService ticketService;
    private final SupportMessageService messageService;

    // ========== 買家層（/support/tickets） ==========

    @PostMapping("/support/tickets")
    @PreAuthorize("hasAuthority('support_ticket:create')")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTicketRequest request) {
        TicketResponse response = ticketService.createTicket(request, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Support ticket created", response));
    }

    @GetMapping("/support/tickets")
    @PreAuthorize("hasAuthority('support_ticket:read')")
    public ResponseEntity<ApiResponse<TicketListResponse>> listMyTickets(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TicketListResponse response = ticketService.listMyTickets(principal.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/support/tickets/{id}")
    @PreAuthorize("hasAuthority('support_ticket:read')")
    public ResponseEntity<ApiResponse<TicketResponse>> getMyTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID ticketId) {
        TicketResponse response = ticketService.getMyTicket(ticketId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/support/tickets/{id}/messages")
    @PreAuthorize("hasAuthority('support_ticket:create')")
    public ResponseEntity<ApiResponse<MessageResponse>> postMessageAsCustomer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody CreateMessageRequest request) {
        MessageResponse response = messageService.postAsCustomer(ticketId, request, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== 店家層（/dashboard/support/tickets） ==========

    @GetMapping("/dashboard/support/tickets")
    @PreAuthorize("hasAuthority('support_ticket:read')")
    public ResponseEntity<ApiResponse<TicketListResponse>> listTenantTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TenantContext.getCurrentTenant();
        TicketListResponse response = ticketService.listTenantTickets(tenantId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/dashboard/support/tickets/{id}")
    @PreAuthorize("hasAuthority('support_ticket:update')")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicketStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        TicketResponse response = ticketService.updateStatus(
                ticketId, request, TenantContext.getCurrentTenant(), isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Support ticket updated", response));
    }

    @PostMapping("/dashboard/support/tickets/{id}/messages")
    @PreAuthorize("hasAuthority('support_ticket:create')")
    public ResponseEntity<ApiResponse<MessageResponse>> postMessageAsStaff(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody CreateMessageRequest request) {
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        MessageResponse response = messageService.postAsStaff(
                ticketId, request, principal.getUserId(), TenantContext.getCurrentTenant(), isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== 平台層（/admin/support/tickets） ==========

    @GetMapping("/admin/support/tickets")
    @PreAuthorize("hasAuthority('support_ticket:manage:all')")
    public ResponseEntity<ApiResponse<TicketListResponse>> listAllTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TicketListResponse response = ticketService.listAllTickets(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/admin/support/tickets/{id}/assign")
    @PreAuthorize("hasAuthority('support_ticket:manage:all')")
    public ResponseEntity<ApiResponse<TicketResponse>> assignTicket(
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody AssignTicketRequest request) {
        TicketResponse response = ticketService.assignTicket(ticketId, request);
        return ResponseEntity.ok(ApiResponse.success("Support ticket assigned", response));
    }
}
