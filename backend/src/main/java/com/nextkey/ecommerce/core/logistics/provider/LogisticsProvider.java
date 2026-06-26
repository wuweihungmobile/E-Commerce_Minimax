package com.nextkey.ecommerce.core.logistics.provider;

import com.nextkey.ecommerce.api.dto.LogisticsDto;

public interface LogisticsProvider {

    String getProviderCode();

    LogisticsDto.ShipmentResult createShipment(LogisticsDto.CreateRequest request);

    LogisticsDto.TrackingResult trackShipment(String trackingNumber);
}
