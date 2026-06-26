package com.nextkey.ecommerce.core.logistics.provider;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.api.dto.LogisticsDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HCTLogisticsProvider implements LogisticsProvider {

    @Override
    public String getProviderCode() {
        return "HCT";
    }

    @Override
    public LogisticsDto.ShipmentResult createShipment(LogisticsDto.CreateRequest request) {
        String trackingNumber = "HCT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("HCT createShipment: orderId={}, trackingNumber={}", request.getOrderId(), trackingNumber);
        return LogisticsDto.ShipmentResult.builder()
                .trackingNumber(trackingNumber)
                .build();
    }

    @Override
    public LogisticsDto.TrackingResult trackShipment(String trackingNumber) {
        return LogisticsDto.TrackingResult.builder()
                .trackingNumber(trackingNumber)
                .status("IN_TRANSIT")
                .location("黑貓物流中心-台北")
                .build();
    }
}
