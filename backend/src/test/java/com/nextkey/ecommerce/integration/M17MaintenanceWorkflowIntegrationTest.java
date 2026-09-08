package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * M17 MAINTENANCE 狀態工作流程 Backend API 整合測試（PRD §5.5.3，Sprint 96）
 *
 * 測試範圍：
 * - IT-M17-001: 房東標記維護 - AVAILABLE 日期成功
 * - IT-M17-002: 房東標記維護 - 已 BOOKED 日期保留 bookingId 並標記 Booking
 * - IT-M17-003: 房東解除維護 - 成功
 * - IT-M17-004: 房東標記維護 - 結束日早於起始日 → 400
 * - IT-M17-005: Admin 查詢 MaintenanceWarnings - 緊急標記
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M17: MAINTENANCE 狀態工作流程 Backend API 整合測試")
class M17MaintenanceWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomCalendarRepository roomCalendarRepository;

    @MockBean
    private BookingRepository bookingRepository;

    private static final UUID ROOM_LISTING_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");
    private static final String MAINTENANCE_URL = "/v2/dashboard/rooms/" + ROOM_LISTING_ID + "/maintenance";

    private RoomCalendar calendarOf(RoomCalendar.RoomCalendarStatus status, UUID bookingId) {
        return RoomCalendar.builder()
                .calendarDate(LocalDate.now().plusDays(1))
                .status(status)
                .bookingId(bookingId)
                .build();
    }

    @Test
    @DisplayName("IT-M17-001: 房東標記維護-AVAILABLE 日期成功")
    @WithMockUser(username = "host", authorities = {"room:update"})
    void markMaintenance_availableDates_returns200() throws Exception {
        RoomCalendar calendar = calendarOf(RoomCalendar.RoomCalendarStatus.AVAILABLE, null);
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any()))
                .thenReturn(List.of(calendar));

        String body = """
                {"startDate": "%s", "endDate": "%s"}
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

        mockMvc.perform(post(MAINTENANCE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(roomCalendarRepository).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE));
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("IT-M17-002: 房東標記維護-已 BOOKED 日期保留 bookingId 並標記 Booking under_maintenance")
    @WithMockUser(username = "host", authorities = {"room:update"})
    void markMaintenance_bookedDates_flagsBooking() throws Exception {
        UUID bookingId = UUID.randomUUID();
        RoomCalendar calendar = calendarOf(RoomCalendar.RoomCalendarStatus.BOOKED, bookingId);
        Booking booking = Booking.builder().id(bookingId).statusFlags(new HashMap<>()).build();

        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any()))
                .thenReturn(List.of(calendar));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        String body = """
                {"startDate": "%s", "endDate": "%s"}
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

        mockMvc.perform(post(MAINTENANCE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(roomCalendarRepository).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.MAINTENANCE && bookingId.equals(c.getBookingId())));
        verify(bookingRepository).save(argThat(
                b -> Boolean.TRUE.equals(b.getStatusFlags().get("under_maintenance"))));
    }

    @Test
    @DisplayName("IT-M17-003: 房東解除維護-成功")
    @WithMockUser(username = "host", authorities = {"room:update"})
    void unmarkMaintenance_success_returns200() throws Exception {
        RoomCalendar calendar = calendarOf(RoomCalendar.RoomCalendarStatus.MAINTENANCE, null);
        when(roomCalendarRepository.findByRoomListingIdAndCalendarDateBetweenWithLockNowait(any(), any(), any()))
                .thenReturn(List.of(calendar));

        mockMvc.perform(delete(MAINTENANCE_URL)
                        .with(csrf())
                        .param("startDate", LocalDate.now().plusDays(1).toString())
                        .param("endDate", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(roomCalendarRepository).save(argThat(
                c -> c.getStatus() == RoomCalendar.RoomCalendarStatus.AVAILABLE));
    }

    @Test
    @DisplayName("IT-M17-004: 房東標記維護-結束日早於起始日 → 400")
    @WithMockUser(username = "host", authorities = {"room:update"})
    void markMaintenance_endBeforeStart_returns400() throws Exception {
        String body = """
                {"startDate": "%s", "endDate": "%s"}
                """.formatted(LocalDate.now().plusDays(5), LocalDate.now().plusDays(1));

        mockMvc.perform(post(MAINTENANCE_URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(roomCalendarRepository, never()).save(any());
    }

    @Test
    @DisplayName("IT-M17-005: Admin 查詢 MaintenanceWarnings-緊急標記")
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void getMaintenanceWarnings_urgentBooking_returnsFlagged() throws Exception {
        UUID bookingId = UUID.randomUUID();
        RoomCalendar calendar = RoomCalendar.builder()
                .status(RoomCalendar.RoomCalendarStatus.MAINTENANCE)
                .bookingId(bookingId)
                .build();
        Booking booking = Booking.builder()
                .id(bookingId)
                .checkInDate(LocalDate.now())
                .guestEmail("guest@example.com")
                .build();

        when(roomCalendarRepository.findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus.MAINTENANCE))
                .thenReturn(List.of(calendar));
        when(bookingRepository.findAllById(List.of(bookingId))).thenReturn(List.of(booking));

        mockMvc.perform(get("/v2/admin/maintenance-warnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.warnings", hasSize(1)))
                .andExpect(jsonPath("$.data.warnings[0].guestEmail").value("guest@example.com"))
                .andExpect(jsonPath("$.data.warnings[0].urgent").value(true));
    }
}
