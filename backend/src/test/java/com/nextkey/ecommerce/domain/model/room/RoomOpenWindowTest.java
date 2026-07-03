package com.nextkey.ecommerce.domain.model.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Room 開放窗領域邏輯單元測試（Sprint 47 US-001 / AI-2202e）。
 *
 * 驗證 {@link Room#resolveOpenUntil(LocalDate)} 與 {@link Room#isBeyondOpenWindow(LocalDate, LocalDate)}：
 * 固定截止 open_until_date 與滾動視窗 booking_window_days 取「最早生效者」；兩者皆 null = 無限制。
 */
@DisplayName("Room: 開放窗有效上限（AI-2202e）")
class RoomOpenWindowTest {

    private static final LocalDate REF = LocalDate.of(2027, 8, 1);

    @Test
    @DisplayName("UT-ROOM-OW-001: 兩者皆 null → 無限制（resolveOpenUntil 回 null、isBeyond 恆 false）")
    void bothNull_unlimited() {
        Room room = Room.builder().build();

        assertThat(room.resolveOpenUntil(REF)).isNull();
        assertThat(room.isBeyondOpenWindow(REF.plusYears(10), REF)).isFalse();
    }

    @Test
    @DisplayName("UT-ROOM-OW-002: 僅固定截止 open_until_date → 回該日（與基準日無關）")
    void onlyOpenUntilDate() {
        Room room = Room.builder().openUntilDate(LocalDate.of(2027, 8, 10)).build();

        assertThat(room.resolveOpenUntil(REF)).isEqualTo(LocalDate.of(2027, 8, 10));
        assertThat(room.isBeyondOpenWindow(LocalDate.of(2027, 8, 10), REF)).isFalse(); // 含當日
        assertThat(room.isBeyondOpenWindow(LocalDate.of(2027, 8, 11), REF)).isTrue();
    }

    @Test
    @DisplayName("UT-ROOM-OW-003: 僅滾動視窗 booking_window_days → 基準日 + N 天")
    void onlyBookingWindowDays() {
        Room room = Room.builder().bookingWindowDays(30).build();

        assertThat(room.resolveOpenUntil(REF)).isEqualTo(REF.plusDays(30)); // 2027-08-31
        assertThat(room.isBeyondOpenWindow(REF.plusDays(30), REF)).isFalse();
        assertThat(room.isBeyondOpenWindow(REF.plusDays(31), REF)).isTrue();
    }

    @Test
    @DisplayName("UT-ROOM-OW-004: 兩者並設 → 取最早生效者（此處固定截止較早）")
    void bothSet_fixedDateEarlier() {
        // open_until_date=08-10；滾動 30 天=08-31 → min = 08-10
        Room room = Room.builder()
                .openUntilDate(LocalDate.of(2027, 8, 10))
                .bookingWindowDays(30)
                .build();

        assertThat(room.resolveOpenUntil(REF)).isEqualTo(LocalDate.of(2027, 8, 10));
    }

    @Test
    @DisplayName("UT-ROOM-OW-005: 兩者並設 → 取最早生效者（此處滾動視窗較早）")
    void bothSet_windowEarlier() {
        // open_until_date=09-01；滾動 5 天=08-06 → min = 08-06
        Room room = Room.builder()
                .openUntilDate(LocalDate.of(2027, 9, 1))
                .bookingWindowDays(5)
                .build();

        assertThat(room.resolveOpenUntil(REF)).isEqualTo(REF.plusDays(5)); // 2027-08-06
    }
}
