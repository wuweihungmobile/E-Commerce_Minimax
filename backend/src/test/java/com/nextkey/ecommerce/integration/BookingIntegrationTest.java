package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.api.dto.BookingDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * M02 預訂管理 Backend API 整合測試 (T-M02-05 ~ T-M02-06)
 *
 * 測試範圍：
 * - IT-M02-005: 日曆查詢-單日可用
 * - IT-M02-006: 日曆查詢-多日
 *
 * Sprint 316（DEF-437）：原 IT-M02-007（預訂衝突-Redis鎖防範雙重預訂）與 IT-M02-008（預訂衝突-跨租戶隔離）已刪除。
 * 兩者都把整個 BookingService 換成 @MockBean 再斷言 201，真正的服務程式碼從未被執行，不可能因為它們宣稱保護的邏輯壞掉而失敗
 * （實測：把 createBooking 整個換成 throw，這兩個測試仍全綠）。預訂衝突與並行搶訂改由
 * BookingApiRealStackIntegrationTest（真實 Redis 鎖，DEF-431）與 BookingControllerE2ETest（API-M06-002、IT-M06-104）守住。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M02-Booking: M02 預訂管理 Backend API 整合測試")
@WithMockUser(username = "test-user", authorities = {"room:create", "room:read", "room:update", "room:delete", "booking:create", "booking:read", "booking:update", "booking:delete"})
class BookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    @MockBean
    private com.nextkey.ecommerce.core.booking.BookingService bookingService;

    private static final String BASE_URL = "/v2/bookings";

    private BookingDto.AvailabilityResponse buildMockAvailabilityResponse(UUID listingId, boolean available, int nightsCount) {
        return BookingDto.AvailabilityResponse.builder()
                .available(available)
                .roomListingId(listingId)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(1 + nightsCount))
                .nightsCount(nightsCount)
                .totalPrice(BigDecimal.valueOf(2500.00 * nightsCount))
                .currency("TWD")
                .build();
    }

    // ── IT-M02-005: 日曆查詢-單日可用 (P0) ───────────────────────

    @Test
    @DisplayName("IT-M02-005: 日曆查詢-單日可用")
    void checkAvailability_singleDayAvailable_returnsAvailable() throws Exception {
        UUID listingId = UUID.randomUUID();

        when(bookingService.checkAvailability(any(BookingDto.AvailabilityRequest.class)))
                .thenReturn(buildMockAvailabilityResponse(listingId, true, 1));

        BookingDto.AvailabilityRequest request = BookingDto.AvailabilityRequest.builder()
                .roomListingId(listingId)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .build();

        mockMvc.perform(get(BASE_URL + "/availability")
                        .with(csrf())
                        .param("roomListingId", request.getRoomListingId().toString())
                        .param("checkInDate", request.getCheckInDate().toString())
                        .param("checkOutDate", request.getCheckOutDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    // ── IT-M02-006: 日曆查詢-多日 (P1) ────────────────────────────

    @Test
    @DisplayName("IT-M02-006: 日曆查詢-多日")
    void checkAvailability_multiDayAvailable_returnsAvailable() throws Exception {
        UUID listingId = UUID.randomUUID();

        when(bookingService.checkAvailability(any(BookingDto.AvailabilityRequest.class)))
                .thenReturn(buildMockAvailabilityResponse(listingId, true, 3));

        BookingDto.AvailabilityRequest request = BookingDto.AvailabilityRequest.builder()
                .roomListingId(listingId)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(4))
                .build();

        mockMvc.perform(get(BASE_URL + "/availability")
                        .with(csrf())
                        .param("roomListingId", request.getRoomListingId().toString())
                        .param("checkInDate", request.getCheckInDate().toString())
                        .param("checkOutDate", request.getCheckOutDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.nightsCount").value(3));
    }
}