package com.nextkey.ecommerce.core.logistics.provider;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.api.dto.LogisticsDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TCATLogisticsProvider implements LogisticsProvider {

    @Override
    public String getProviderCode() {
        return "TCAT";
    }

    @Override
    public LogisticsDto.ShipmentResult createShipment(LogisticsDto.CreateRequest request) {
        String trackingNumber = "TCAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("TCAT createShipment: orderId={}, trackingNumber={}", request.getOrderId(), trackingNumber);
        return LogisticsDto.ShipmentResult.builder()
                .trackingNumber(trackingNumber)
                .build();
    }

    @Override
    public LogisticsDto.TrackingResult trackShipment(String trackingNumber) {
        return LogisticsDto.TrackingResult.builder()
                .trackingNumber(trackingNumber)
                .status("IN_TRANSIT")
                .location("新竹物流中心-新竹")
                .build();
    }
}
