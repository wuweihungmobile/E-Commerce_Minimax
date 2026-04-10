package com.nextkey.ecommerce.core.logistics;

import com.nextkey.ecommerce.api.dto.LogisticsDto;
import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * 建立物流單 (Mock)
     */
    @Transactional
    public LogisticsDto.LogisticsResponse createLogistics(LogisticsDto.CreateRequest request) {
        log.info("Creating logistics: orderId={}, provider={}",
                request.getOrderId(), request.getLogisticsProvider());

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5000, "Order not found"));

        // 檢查是否已有物流單
        List<Logistics> existingLogistics = logisticsRepository.findByOrderId(request.getOrderId());
        boolean hasActiveLogistics = existingLogistics.stream()
                .anyMatch(l -> l.getStatus() != Logistics.LogisticsStatus.DELIVERED
                        && l.getStatus() != Logistics.LogisticsStatus.RETURNED);
        if (hasActiveLogistics) {
            throw new BusinessException(ErrorCode.E_7001, "Active logistics already exists for this order");
        }

        // 建立物流單 (Mock 直接 PENDING)
        Logistics logistics = Logistics.builder()
                .orderId(order.getId())
                .logisticsProvider(Logistics.LogisticsProvider.valueOf(request.getLogisticsProvider().name()))
                .trackingNumber(generateMockTrackingNumber(request.getLogisticsProvider()))
                .status(Logistics.LogisticsStatus.PENDING)
                .receiverName(request.getReceiverName() != null ? request.getReceiverName() : order.getShippingRecipientName())
                .receiverPhone(request.getReceiverPhone() != null ? request.getReceiverPhone() : order.getShippingPhone())
                .shippingAddress(request.getShippingAddress() != null ? request.getShippingAddress() : order.getShippingAddress())
                .logisticsData("{}")
                .build();

        logistics = logisticsRepository.save(logistics);

        log.info("Logistics created: logisticsId={}, trackingNumber={}",
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
        return toLogisticsResponse(logistics);
    }

    /**
     * 依 orderId 取得物流列表
     */
    @Transactional(readOnly = true)
    public List<LogisticsDto.LogisticsResponse> getLogisticsByOrderId(UUID orderId) {
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

        logistics.setStatus(newStatus);
        if (newStatus == Logistics.LogisticsStatus.DELIVERED) {
            logistics.setDeliveryTime(LocalDateTime.now());
        } else if (newStatus == Logistics.LogisticsStatus.PICKED_UP) {
            logistics.setPickupTime(LocalDateTime.now());
        }

        logistics = logisticsRepository.save(logistics);
        log.info("Logistics status updated: logisticsId={}, newStatus={}", logisticsId, newStatus);

        return toLogisticsResponse(logistics);
    }

    /**
     * 取消物流 (Mock)
     */
    @Transactional
    public LogisticsDto.LogisticsResponse cancelLogistics(UUID logisticsId, String reason) {
        Logistics logistics = logisticsRepository.findById(logisticsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7000));

        if (logistics.getStatus() == Logistics.LogisticsStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.E_7002, "Cannot cancel delivered logistics");
        }

        logistics.setStatus(Logistics.LogisticsStatus.RETURNED);
        logistics = logisticsRepository.save(logistics);

        log.info("Logistics cancelled: logisticsId={}, reason={}", logisticsId, reason);
        return toLogisticsResponse(logistics);
    }

    // ========== Helper Methods ==========

    private String generateMockTrackingNumber(LogisticsDto.LogisticsProvider provider) {
        String prefix = provider == LogisticsDto.LogisticsProvider.HCT ? "HCT" : "TCAT";
        return prefix + System.currentTimeMillis() % 100000000;
    }

    private String getStatusMessage(Logistics.LogisticsStatus status) {
        return switch (status) {
            case PENDING -> "物流單建立，等待取貨";
            case PICKED_UP -> "已取件，準備配送";
            case IN_TRANSIT -> "配送中";
            case OUT_FOR_DELIVERY -> "配送員已出發";
            case DELIVERED -> "已簽收";
            case FAILED -> "配送失敗";
            case RETURNED -> "已退貨";
        };
    }

    private String getMockLocation(Logistics.LogisticsProvider provider) {
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
                    .eventTime(now.minusHours(12))
                    .build());

            events.add(LogisticsDto.TrackingEvent.builder()
                    .status("OUT_FOR_DELIVERY")
                    .description("配送員出發")
                    .location(logistics.getShippingAddress())
                    .eventTime(now.minusHours(2))
                    .build());
        }

        if (logistics.getStatus() == Logistics.LogisticsStatus.DELIVERED) {
            events.add(LogisticsDto.TrackingEvent.builder()
                    .status("DELIVERED")
                    .description("已簽收")
                    .location(logistics.getShippingAddress())
                    .eventTime(now.minusHours(1))
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
