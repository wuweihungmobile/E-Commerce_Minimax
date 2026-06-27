package com.nextkey.ecommerce.core.logistics.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nextkey.ecommerce.api.dto.LogisticsDto;

/**
 * M11 物流 Provider Stub 強化測試（Sprint 22 US-005）
 *
 * 測試範圍：
 * - TC-PROV-001: HCT 追蹤號格式 HCT-{yyyyMMdd}-{HEX8}
 * - TC-PROV-002: TCAT 追蹤號格式 TCAT-{yyyyMMdd}-{HEX8}
 * - TC-PROV-003: HCT trackShipment → IN_TRANSIT + 含「台北」
 */
@DisplayName("M11 物流 Provider Stub 整合測試（US-005）")
class LogisticsProviderIntegrationTest {

    private HCTLogisticsProvider hctProvider;
    private TCATLogisticsProvider tcatProvider;

    @BeforeEach
    void setUp() {
        hctProvider = new HCTLogisticsProvider();
        tcatProvider = new TCATLogisticsProvider();
    }

    @Test
    @DisplayName("TC-PROV-001: HCT createShipment → 追蹤號格式 HCT-{yyyyMMdd}-{HEX8}")
    void hct_createShipment_trackingNumberMatchesDateFormat() {
        LogisticsDto.CreateRequest request = LogisticsDto.CreateRequest.builder()
                .orderId(UUID.randomUUID())
                .build();

        LogisticsDto.ShipmentResult result = hctProvider.createShipment(request);

        assertThat(result.getTrackingNumber())
                .matches("HCT-\\d{8}-[A-F0-9]{8}");
    }

    @Test
    @DisplayName("TC-PROV-002: TCAT createShipment → 追蹤號格式 TCAT-{yyyyMMdd}-{HEX8}")
    void tcat_createShipment_trackingNumberMatchesDateFormat() {
        LogisticsDto.CreateRequest request = LogisticsDto.CreateRequest.builder()
                .orderId(UUID.randomUUID())
                .build();

        LogisticsDto.ShipmentResult result = tcatProvider.createShipment(request);

        assertThat(result.getTrackingNumber())
                .matches("TCAT-\\d{8}-[A-F0-9]{8}");
    }

    @Test
    @DisplayName("TC-PROV-003: HCT trackShipment → status=IN_TRANSIT, location 含「台北」")
    void hct_trackShipment_returnsInTransitWithTaipeiLocation() {
        LogisticsDto.TrackingResult result = hctProvider.trackShipment("HCT-20260627-ABCDEF12");

        assertThat(result.getStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.getLocation()).contains("台北");
    }
}
