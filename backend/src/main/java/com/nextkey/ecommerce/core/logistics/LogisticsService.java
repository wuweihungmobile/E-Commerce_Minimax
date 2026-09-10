package com.nextkey.ecommerce.core.logistics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.LogisticsDto;
import com.nextkey.ecommerce.core.logistics.provider.LogisticsProvider;
import com.nextkey.ecommerce.core.logistics.provider.LogisticsProviderFactory;
import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * 物流服務 (Mock Implementation)
 * Phase 1 使用 Mock 物流，不需要串真實黑貓/新竹 API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsService {

    private final LogisticsRepository logisticsRepository;
    private final OrderRepository orderRepository;
    private final LogisticsProviderFactory logisticsProviderFactory;

    // Mock tracking event time constants
    private static final long IN_TRANSIT_HOURS_AGO = 12;
    private static final long OUT_FOR_DELIVERY_HOURS_AGO = 2;
    private static final long DELIVERED_HOURS_AGO = 1;

    /**
     * 建立物流單 (Mock)
     */
    @Transactional
    public LogisticsDto.LogisticsResponse createLogistics(LogisticsDto.CreateRequest request) {
        log.info("Creating logistics: orderId={}, provider={}",
                request.getOrderId(), request.getLogisticsProvider());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));

        // DEF-019：物流建立租戶擁有權檢查——賣家限本租戶訂單、admin 放行，越權回 403/E_1007。
        // 置於狀態檢查之前，避免向未授權者洩漏訂單狀態（IDOR 正確順序）。
        checkOrderTenant(order);

        // 訂單必須為 CONFIRMED 才能建立物流
        if (order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.E_5001,
                    "Order must be CONFIRMED to create logistics, current status: " + order.getStatus());
        }

        // 檢查是否已有物流單
        List<Logistics> existingLogistics = logisticsRepository.findByOrderId(request.getOrderId());
        boolean hasActiveLogistics = existingLogistics.stream()
                .anyMatch(l -> l.getStatus() != Logistics.LogisticsStatus.DELIVERED
                        && l.getStatus() != Logistics.LogisticsStatus.RETURNED);
        if (hasActiveLogistics) {
            throw new BusinessException(ErrorCode.E_7001, "Active logistics already exists for this order");
        }

        // 併發防護（DEF-123，claim-before-external-call）：上面「訂單狀態 + 無現存物流單」
        // 的檢查與下面呼叫物流商 API、建立 Logistics 之間沒有原子保護，兩個併發請求都可能
        // 通過檢查各自建立一筆 Logistics，違反「一張訂單至多一筆有效物流單」的業務不變量
        // （logistics 表對 order_id 無唯一約束兜底）。先原子搶占 CONFIRMED→SHIPPING，
        // 只有搶到的一方才繼續呼叫物流商 API 並建立 Logistics；搶輸沿用既有的 E_5001。
        if (orderRepository.updateStatusIfCurrent(order.getId(), Order.OrderStatus.CONFIRMED, Order.OrderStatus.SHIPPING) == 0) {
            throw new BusinessException(ErrorCode.E_5001,
                    "Order must be CONFIRMED to create logistics, current status: " + order.getStatus());
        }

        // 透過 Provider 策略取得追蹤號
        LogisticsProvider provider = logisticsProviderFactory.getProvider(request.getLogisticsProvider().name());
        LogisticsDto.ShipmentResult shipment = provider.createShipment(request);

        Logistics logistics = Logistics.builder()
                .orderId(order.getId())
                .logisticsProvider(Logistics.LogisticsProvider.valueOf(request.getLogisticsProvider().name()))
                .trackingNumber(shipment.getTrackingNumber())
                .status(Logistics.LogisticsStatus.PENDING)
                .receiverName(request.getReceiverName() != null ? request.getReceiverName() : order.getShippingRecipientName())
                .receiverPhone(request.getReceiverPhone() != null ? request.getReceiverPhone() : order.getShippingPhone())
                .shippingAddress(request.getShippingAddress() != null ? request.getShippingAddress() : order.getShippingAddress())
                .logisticsData(new HashMap<>())
                .build();

        logistics = logisticsRepository.save(logistics);

        // 訂單狀態 CONFIRMED → SHIPPING 已由上面的 updateStatusIfCurrent 原子完成，
        // 不再需要「讀 order → setStatus → save」的全欄位覆寫式寫入。
        log.info("Logistics created: logisticsId={}, trackingNumber={}, order status -> SHIPPING",
                logistics.getId(), logistics.getTrackingNumber());

        return toLogisticsResponse(logistics);
    }

    /**
     * 取得物流資訊
     */
    @Transactional(readOnly = true)
    public LogisticsDto.LogisticsResponse getLogistics(UUID logisticsId) {
        Logistics logistics = logisticsRepository.findById(logisticsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7000, "Logistics not found"));
        // DEF-036：物流查詢租戶擁有權檢查——比照 createLogistics 的 checkOrderTenant，
        // 杜絕任一持有 order:read 的租戶對他租戶物流單的跨租戶讀取（IDOR）。
        checkLogisticsTenant(logistics);
        return toLogisticsResponse(logistics);
    }

    /**
     * 依 orderId 取得物流列表
     */
    @Transactional(readOnly = true)
    public List<LogisticsDto.LogisticsResponse> getLogisticsByOrderId(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        // DEF-036：依訂單查物流列表亦須租戶擁有權檢查，理由同上。
        checkOrderTenant(order);
        return logisticsRepository.findByOrderId(orderId).stream()
                .map(this::toLogisticsResponse)
                .collect(Collectors.toList());
    }

    /**
     * 追蹤物流狀態 (Mock)
     */
    @Transactional(readOnly = true)
    public LogisticsDto.StatusResponse trackLogistics(UUID logisticsId) {
        Logistics logistics = logisticsRepository.findById(logisticsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7000));
        // DEF-036：追蹤查詢租戶擁有權檢查，理由同上。
        checkLogisticsTenant(logistics);

        // Mock 追蹤資訊
        String statusMessage = getStatusMessage(logistics.getStatus());
        String location = getMockLocation(logistics.getLogisticsProvider());

        return LogisticsDto.StatusResponse.builder()
                .logisticsId(logistics.getId())
                .trackingNumber(logistics.getTrackingNumber())
                .status(logistics.getStatus().name())
                .statusMessage(statusMessage)
                .location(location)
                .eventTime(logistics.getUpdatedAt() != null
                        ? LocalDateTime.ofInstant(logistics.getUpdatedAt(), java.time.ZoneId.systemDefault())
                        : null)
                .updatedAt(logistics.getUpdatedAt())
                .build();
    }

    /**
     * 取得詳細追蹤歷史 (Mock)
     */
    @Transactional(readOnly = true)
    public LogisticsDto.TrackingDetail getTrackingDetail(UUID logisticsId) {
        Logistics logistics = logisticsRepository.findById(logisticsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7000));
        // DEF-036：追蹤歷史查詢租戶擁有權檢查，理由同上。
        checkLogisticsTenant(logistics);

        // Mock 追蹤事件
        List<LogisticsDto.TrackingEvent> events = generateMockEvents(logistics);

        return LogisticsDto.TrackingDetail.builder()
                .logisticsId(logistics.getId())
                .trackingNumber(logistics.getTrackingNumber())
                .logisticsProvider(logistics.getLogisticsProvider().name())
                .currentStatus(logistics.getStatus().name())
                .shippingAddress(LogisticsDto.ShippingAddress.builder()
                        .receiverName(logistics.getReceiverName())
                        .phone(logistics.getReceiverPhone())
                        .address(logistics.getShippingAddress())
                        .build())
                .events(events)
                .updatedAt(logistics.getUpdatedAt())
                .build();
    }

    /**
     * 更新物流狀態 (Mock - 用於模拟状态推进)
     */
    @Transactional
    public LogisticsDto.LogisticsResponse updateLogisticsStatus(UUID logisticsId, Logistics.LogisticsStatus newStatus) {
        Logistics logistics = logisticsRepository.findById(logisticsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7000));
        // DEF-036：狀態更新為寫入操作，租戶擁有權檢查更為關鍵（可竄改他租戶訂單的物流/送達狀態）。
        checkLogisticsTenant(logistics);

        logistics.setStatus(newStatus);
        if (newStatus == Logistics.LogisticsStatus.DELIVERED) {
            logistics.setDeliveryTime(LocalDateTime.now());
        } else if (newStatus == Logistics.LogisticsStatus.PICKED_UP) {
            logistics.setPickupTime(LocalDateTime.now());
        }

        logistics = logisticsRepository.save(logistics);

        // 同步更新訂單狀態：物流 DELIVERED → 訂單 DELIVERED
        // 併發防護（DEF-159）：改用條件式原子 UPDATE 取代「讀 order → setStatus → save()」，
        // 避免與同一筆訂單上其他併發寫入（例如退款觸發的狀態轉換）交錯時，本次全欄位覆寫
        // 悄悄復原對方已提交的欄位；搶輸僅代表訂單狀態已被其他流程推進，不影響物流本身已
        // 記錄為 DELIVERED 的結果，故不拋例外，僅記錄無法同步。
        if (newStatus == Logistics.LogisticsStatus.DELIVERED) {
            orderRepository.findById(logistics.getOrderId()).ifPresent(order -> {
                int updated = orderRepository.updateStatusIfCurrent(
                        order.getId(), order.getStatus(), Order.OrderStatus.DELIVERED);
                if (updated > 0) {
                    log.info("Order status updated to DELIVERED: orderId={}", order.getId());
                } else {
                    log.warn("Order status not synced to DELIVERED (concurrently changed): orderId={}, currentStatus={}",
                            order.getId(), order.getStatus());
                }
            });
        }

        log.info("Logistics status updated: logisticsId={}, newStatus={}", logisticsId, newStatus);

        return toLogisticsResponse(logistics);
    }

    /**
     * 取消物流 (Mock)
     */
    @Transactional
    public LogisticsDto.LogisticsResponse cancelLogistics(UUID logisticsId, String reason) {
        // DEF-011：改用物流專用錯誤碼（原誤用 Supplier/PO 的 E_7000/E_7002）。
        Logistics logistics = logisticsRepository.findById(logisticsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7500)); // Logistics not found
        // DEF-036：取消為寫入操作，租戶擁有權檢查須先於狀態檢查（IDOR 正確順序，避免向未授權者洩漏物流狀態）。
        checkLogisticsTenant(logistics);

        if (logistics.getStatus() == Logistics.LogisticsStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.E_7502, "Cannot cancel delivered logistics");
        }

        // 併發防護（DEF-158）：原子 CAS 取代 setStatus+save，避免與併發的
        // updateLogisticsStatus(DELIVERED) 交錯時，上面基於舊快照的檢查被繞過
        // （見 LogisticsRepository.cancelIfNotStatus）。搶輸（已被併發推進為 DELIVERED）
        // 沿用同一個 E_7502。
        int updated = logisticsRepository.cancelIfNotStatus(
                logisticsId, Logistics.LogisticsStatus.DELIVERED, Logistics.LogisticsStatus.RETURNED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.E_7502, "Cannot cancel delivered logistics");
        }
        logistics.setStatus(Logistics.LogisticsStatus.RETURNED);

        log.info("Logistics cancelled: logisticsId={}, reason={}", logisticsId, reason);
        return toLogisticsResponse(logistics);
    }

    // ========== Helper Methods ==========

    /**
     * 「沒有真正租戶」的預設佔位租戶 ID（見 {@code TenantContextFilter.resolveEffectiveTenantId}）。
     * {@link #checkOrderTenant} 的租戶比對需明確排除它，見該方法 Javadoc。
     */
    private static final UUID SYSTEM_TENANT_UUID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    /**
     * 物流租戶擁有權檢查（DEF-019 建立時新增；DEF-036 擴及查詢/追蹤/狀態更新/取消等其餘方法）。
     * 賣家限本租戶訂單（order.tenantId == 當前租戶）、admin（ROLE_ADMIN/SUPER_ADMIN）放行，
     * 越權回 403/E_1007。杜絕任何具 order:read/order:update 權限者存取或竄改他租戶物流單（IDOR）。
     * 比照 PaymentStateService.checkOrderOwnership，惟物流為賣家側故採 tenant-based（非買家 user-based）。
     *
     * <p>🔴 Sprint 151（DEF-190，修 `OrderService` 的 DEF-189 時比對發現的同型既有漏洞，隨手
     * 一併修復）：{@code TenantContextFilter.resolveEffectiveTenantId} 對「使用者未歸屬任何實際
     * 租戶」（一般 BUYER、或尚未通過審核的 SELLER）一律 fallback 到同一個常數
     * {@code AppConstants.SYSTEM_TENANT_ID}，任兩個未加入店鋪的一般使用者會落在同一個租戶值。
     * 租戶比對若未排除這個佔位值，等於任一使用者都能讀取/竄改任一其他使用者掛在系統租戶下訂單的
     * 物流記錄——正是本方法原本要防堵的同一種跨使用者 IDOR。
     */
    private void checkOrderTenant(final Order order) {
        UUID tenantId = TenantContext.getCurrentTenant();
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && (
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ||
            auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
        );
        boolean isSameTenant = tenantId != null && tenantId.equals(order.getTenantId())
                && !tenantId.equals(SYSTEM_TENANT_UUID);
        if (!isAdmin && !isSameTenant) {
            throw new BusinessException(ErrorCode.E_1007, "Not authorized to access logistics for this order");
        }
    }

    /**
     * 依物流單反查所屬訂單並執行租戶擁有權檢查（DEF-036）。
     * 供 getLogistics/trackLogistics/getTrackingDetail/updateLogisticsStatus/cancelLogistics 共用。
     */
    private void checkLogisticsTenant(final Logistics logistics) {
        Order order = orderRepository.findById(logistics.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));
        checkOrderTenant(order);
    }

    private String getStatusMessage(final Logistics.LogisticsStatus status) {
        return switch ( status) {
            case PENDING -> "物流單建立，等待取貨";
            case PICKED_UP -> "已取件，準備配送";
            case IN_TRANSIT -> "配送中";
            case OUT_FOR_DELIVERY -> "配送員已出發";
            case DELIVERED -> "已簽收";
            case FAILED -> "配送失敗";
            case RETURNED -> "已退貨";
        };
    }

    private String getMockLocation(final Logistics.LogisticsProvider provider) {
        return provider == Logistics.LogisticsProvider.HCT ? "黑貓物流中心-台北" : "新竹物流中心-新竹";
    }

    private List<LogisticsDto.TrackingEvent> generateMockEvents(Logistics logistics) {
        List<LogisticsDto.TrackingEvent> events = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        events.add(LogisticsDto.TrackingEvent.builder()
                .status("PENDING")
                .description("物流單建立")
                .location(logistics.getLogisticsProvider() == Logistics.LogisticsProvider.HCT ? "黑貓物流-台北" : "新竹物流-新竹")
                .eventTime(now.minusDays(2))
                .build());

        if (logistics.getStatus() != Logistics.LogisticsStatus.PENDING) {
            events.add(LogisticsDto.TrackingEvent.builder()
                    .status("PICKED_UP")
                    .description("已取件")
                    .location(logistics.getShippingAddress())
                    .eventTime(now.minusDays(1).plusHours(10))
                    .build());
        }

        if (logistics.getStatus() == Logistics.LogisticsStatus.IN_TRANSIT
                || logistics.getStatus() == Logistics.LogisticsStatus.OUT_FOR_DELIVERY
                || logistics.getStatus() == Logistics.LogisticsStatus.DELIVERED) {
            events.add(LogisticsDto.TrackingEvent.builder()
                    .status("IN_TRANSIT")
                    .description("配送中")
                    .location(logistics.getLogisticsProvider() == Logistics.LogisticsProvider.HCT ? "黑貓物流-桃園" : "新竹物流-台中")
                    .eventTime(now.minusHours(IN_TRANSIT_HOURS_AGO))
                    .build());

            events.add(LogisticsDto.TrackingEvent.builder()
                    .status("OUT_FOR_DELIVERY")
                    .description("配送員出發")
                    .location(logistics.getShippingAddress())
                    .eventTime(now.minusHours(OUT_FOR_DELIVERY_HOURS_AGO))
                    .build());
        }

        if (logistics.getStatus() == Logistics.LogisticsStatus.DELIVERED) {
            events.add(LogisticsDto.TrackingEvent.builder()
                    .status("DELIVERED")
                    .description("已簽收")
                    .location(logistics.getShippingAddress())
                    .eventTime(now.minusHours(DELIVERED_HOURS_AGO))
                    .build());
        }

        return events;
    }

    private LogisticsDto.LogisticsResponse toLogisticsResponse(Logistics logistics) {
        return LogisticsDto.LogisticsResponse.builder()
                .logisticsId(logistics.getId())
                .orderId(logistics.getOrderId())
                .logisticsProvider(logistics.getLogisticsProvider().name())
                .trackingNumber(logistics.getTrackingNumber())
                .status(logistics.getStatus().name())
                .pickupTime(logistics.getPickupTime())
                .deliveryTime(logistics.getDeliveryTime())
                .shippingAddress(logistics.getShippingAddress())
                .receiverName(logistics.getReceiverName())
                .receiverPhone(logistics.getReceiverPhone())
                .createdAt(logistics.getCreatedAt())
                .updatedAt(logistics.getUpdatedAt())
                .build();
    }
}
