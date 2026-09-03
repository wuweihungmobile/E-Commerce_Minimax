package com.nextkey.ecommerce.core.returns;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.returns.ReturnDto;
import com.nextkey.ecommerce.core.product.ProductInventoryService;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.order.OrderItem;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequest.ReturnStatus;
import com.nextkey.ecommerce.domain.model.returns.ReturnRequestItem;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.returns.ReturnRequestRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 退貨申請服務（Sprint 118，DEF-044）。
 *
 * <p><b>解決的問題</b>：已付款訂單退款後，先前正式扣除的庫存不會回補——那批貨在系統裡永遠消失。
 * 使用者拍板的規則是「**店家實際收到貨、確認可售後才回補**」：退錢與收貨是兩件事，
 * 退款當下就把庫存加回可售池，等於在賣還沒拿回來的東西。
 *
 * <p><b>狀態機</b>：
 * <pre>
 *   REQUESTED ──核准──→ APPROVED ──收貨確認──→ RECEIVED（🔴 唯一會動庫存的一步）
 *       │                   │
 *       ├──駁回──→ REJECTED  │
 *       └──買家撤回──→ CANCELLED ←──買家撤回──┘
 * </pre>
 *
 * <p>🔴 <b>與退款流程完全獨立</b>（使用者決策）：本服務不讀也不寫 payments，不改訂單付款狀態。
 * 可以只退錢不收貨（單價低於運費時貼還不如），也可以只收貨不退錢（換貨）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnRequestService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final ProductInventoryService productInventoryService;

    /**
     * 可申請退貨的訂單狀態：貨得先送到買家手上，才談得上退回來。
     *
     * <p>PRD 對退貨沒有規格（§13.2 反而把逆向物流列為 Phase 1 排除範圍，但那張清單已經過時
     * ——同一張清單的「不支援退款」與「模擬支付」都早已實作）。此處為本輪的明示假設。
     */
    private static final Set<Order.OrderStatus> RETURNABLE_ORDER_STATUSES =
            EnumSet.of(Order.OrderStatus.DELIVERED, Order.OrderStatus.COMPLETED);

    private static final DateTimeFormatter RETURN_NUMBER_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    // ── 買家 ────────────────────────────────────────────────────

    /** 買家提出退貨申請。 */
    @Transactional
    public ReturnDto.Response createReturnRequest(final ReturnDto.CreateRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));

        // 買家限本人訂單（admin 放行），比照 PaymentStateService.checkOrderOwnership
        if (!isAdmin() && !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to return this order");
        }
        if (!RETURNABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.E_5019,
                    "Order status does not allow returns: " + order.getStatus());
        }

        Map<UUID, OrderItem> orderItems = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        ReturnRequest returnRequest = ReturnRequest.builder()
                .tenantId(order.getTenantId())
                .returnNumber(nextReturnNumber())
                .orderId(order.getId())
                .customerId(order.getUserId())
                .status(ReturnStatus.REQUESTED)
                .reason(request.getReason())
                .build();

        for (ReturnDto.CreateItem line : request.getItems()) {
            OrderItem orderItem = orderItems.get(line.getOrderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.E_5003,
                        "Order item does not belong to this order: " + line.getOrderItemId());
            }
            // 同一件貨不得重複申請：已在「未被駁回／取消」的退貨單裡佔用的數量要先扣掉
            int alreadyRequested =
                    returnRequestRepository.sumActiveRequestedQtyByOrderItem(orderItem.getId());
            int remaining = orderItem.getQuantity() - alreadyRequested;
            if (line.getQuantity() > remaining) {
                throw new BusinessException(ErrorCode.E_5018, String.format(
                        "Return quantity exceeds returnable quantity: requested=%d, remaining=%d",
                        line.getQuantity(), remaining));
            }
            returnRequest.addItem(ReturnRequestItem.builder()
                    .orderItemId(orderItem.getId())
                    .skuId(orderItem.getSku() != null ? orderItem.getSku().getId() : null)
                    .requestedQty(line.getQuantity())
                    .build());
        }

        ReturnRequest saved = returnRequestRepository.save(returnRequest);
        log.info("Return request created: returnNumber={}, orderId={}, items={}",
                saved.getReturnNumber(), order.getId(), saved.getItems().size());
        return toResponse(saved);
    }

    /** 買家撤回申請（尚未收貨前皆可）。 */
    @Transactional
    public ReturnDto.Response cancelReturnRequest(final UUID returnId) {
        ReturnRequest request = loadForCustomer(returnId);
        requireStatus(request, EnumSet.of(ReturnStatus.REQUESTED, ReturnStatus.APPROVED));

        request.setStatus(ReturnStatus.CANCELLED);
        log.info("Return request cancelled by customer: returnNumber={}", request.getReturnNumber());
        return toResponse(returnRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public Page<ReturnDto.Response> getMyReturnRequests(final Pageable pageable) {
        UUID userId = TenantContext.getCurrentUser();
        return returnRequestRepository.findByCustomerIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    // ── 店家 ────────────────────────────────────────────────────

    /** 店家核准。**不動庫存**——核准只是同意收貨。 */
    @Transactional
    public ReturnDto.Response approveReturn(final UUID returnId) {
        ReturnRequest request = loadForTenant(returnId);
        requireStatus(request, EnumSet.of(ReturnStatus.REQUESTED));

        request.setStatus(ReturnStatus.APPROVED);
        request.setReviewedBy(TenantContext.getCurrentUser());
        request.setReviewedAt(Instant.now());
        log.info("Return request approved: returnNumber={}", request.getReturnNumber());
        return toResponse(returnRequestRepository.save(request));
    }

    /** 店家駁回。 */
    @Transactional
    public ReturnDto.Response rejectReturn(final UUID returnId, final ReturnDto.RejectRequest body) {
        ReturnRequest request = loadForTenant(returnId);
        requireStatus(request, EnumSet.of(ReturnStatus.REQUESTED));

        request.setStatus(ReturnStatus.REJECTED);
        request.setRejectionReason(body != null ? body.getRejectionReason() : null);
        request.setReviewedBy(TenantContext.getCurrentUser());
        request.setReviewedAt(Instant.now());
        log.info("Return request rejected: returnNumber={}", request.getReturnNumber());
        return toResponse(returnRequestRepository.save(request));
    }

    /**
     * 店家收貨確認——🔴 <b>唯一會動到庫存的一步</b>。
     *
     * <p>可售數量回補（{@code RETURN} 流水帳），不可售數量另寫一筆 {@code SCRAP} 抵銷，
     * 淨效果是「不可售的不回補」而台帳看得到全貌。細節見
     * {@code ProductInventoryService.applyReturnReceipt}。
     */
    @Transactional
    public ReturnDto.Response receiveReturn(final UUID returnId, final ReturnDto.ReceiveRequest body) {
        ReturnRequest request = loadForTenant(returnId);
        requireStatus(request, EnumSet.of(ReturnStatus.APPROVED));

        Map<UUID, ReturnRequestItem> items = request.getItems().stream()
                .collect(Collectors.toMap(ReturnRequestItem::getId, Function.identity()));

        for (ReturnDto.ReceiveItem line : body.getItems()) {
            ReturnRequestItem item = items.get(line.getItemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.E_5003,
                        "Return item does not belong to this return: " + line.getItemId());
            }
            int received = line.getSellableQty() + line.getUnsellableQty();
            // 買家可能少寄，但不得多於申請數量——多出來的沒有對應的訂單品項可回補
            if (received > item.getRequestedQty()) {
                throw new BusinessException(ErrorCode.E_5018, String.format(
                        "Received quantity exceeds requested: received=%d, requested=%d",
                        received, item.getRequestedQty()));
            }
            item.setSellableQty(line.getSellableQty());
            item.setUnsellableQty(line.getUnsellableQty());
        }

        // 未在請求中列出的品項視為完全沒收到（0/0），避免留下 null 讓後續無從分辨「沒收到」與「還沒處理」
        for (ReturnRequestItem item : request.getItems()) {
            if (item.getSellableQty() == null) {
                item.setSellableQty(0);
                item.setUnsellableQty(0);
            }
        }

        request.setStatus(ReturnStatus.RECEIVED);
        request.setReceivedBy(TenantContext.getCurrentUser());
        request.setReceivedAt(Instant.now());
        ReturnRequest saved = returnRequestRepository.save(request);

        // 庫存變動排在狀態寫入之後：兩者同一交易，任一失敗全部回滾
        for (ReturnRequestItem item : saved.getItems()) {
            if (item.getSkuId() != null) {
                productInventoryService.applyReturnReceipt(saved, item, saved.getReceivedBy());
            }
        }

        log.info("Return request received: returnNumber={}, items={}",
                saved.getReturnNumber(), saved.getItems().size());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ReturnDto.Response> getTenantReturnRequests(final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();
        return returnRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReturnDto.Response getReturnRequest(final UUID returnId) {
        ReturnRequest request = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5016, "Return request not found"));
        // 買家看自己的、店家看本租戶的、admin 全放行
        UUID userId = TenantContext.getCurrentUser();
        UUID tenantId = TenantContext.getCurrentTenant();
        boolean ownedByCustomer = userId != null && userId.equals(request.getCustomerId());
        boolean sameTenant = tenantId != null && tenantId.equals(request.getTenantId());
        if (!isAdmin() && !ownedByCustomer && !sameTenant) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to access this return request");
        }
        return toResponse(request);
    }

    // ── 內部 ────────────────────────────────────────────────────

    private ReturnRequest loadForCustomer(final UUID returnId) {
        ReturnRequest request = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5016, "Return request not found"));
        UUID userId = TenantContext.getCurrentUser();
        if (!isAdmin() && !userId.equals(request.getCustomerId())) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to modify this return request");
        }
        return request;
    }

    private ReturnRequest loadForTenant(final UUID returnId) {
        ReturnRequest request = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5016, "Return request not found"));
        UUID tenantId = TenantContext.getCurrentTenant();
        // 擁有權檢查先於狀態檢查（承 DEF-036：避免向未授權者洩漏退貨單狀態）
        if (!isAdmin() && !request.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "Return request does not belong to current tenant");
        }
        return request;
    }

    private void requireStatus(final ReturnRequest request, final Set<ReturnStatus> allowed) {
        if (!allowed.contains(request.getStatus())) {
            throw new BusinessException(ErrorCode.E_5017, String.format(
                    "Return request is %s; expected one of %s", request.getStatus(), allowed));
        }
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    /** 退貨單號：RMA-yyyyMMdd-八碼亂數。與採購單號同樣是給人看的識別。 */
    private String nextReturnNumber() {
        return "RMA-" + RETURN_NUMBER_DATE.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private ReturnDto.Response toResponse(final ReturnRequest request) {
        List<ReturnDto.ItemResponse> items = request.getItems().stream()
                .map(item -> ReturnDto.ItemResponse.builder()
                        .id(item.getId())
                        .orderItemId(item.getOrderItemId())
                        .skuId(item.getSkuId())
                        .requestedQty(item.getRequestedQty())
                        .sellableQty(item.getSellableQty())
                        .unsellableQty(item.getUnsellableQty())
                        .build())
                .collect(Collectors.toList());

        return ReturnDto.Response.builder()
                .id(request.getId())
                .returnNumber(request.getReturnNumber())
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .tenantId(request.getTenantId())
                .status(request.getStatus().name())
                .reason(request.getReason())
                .rejectionReason(request.getRejectionReason())
                .reviewedAt(request.getReviewedAt())
                .receivedAt(request.getReceivedAt())
                .createdAt(request.getCreatedAt())
                .items(items)
                .build();
    }
}
