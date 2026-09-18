package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * 預訂 DTO
 */
public class BookingDto {

    // Validation constraints
    private static final int GUEST_NAME_MAX_LENGTH = 200;

    // ========== Create Booking Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "Room listing ID is required")
        private UUID roomListingId;

        @NotNull(message = "Check-in date is required")
        @FutureOrPresent(message = "Check-in date must be today or in the future")
        private LocalDate checkInDate;

        @NotNull(message = "Check-out date is required")
        @Future(message = "Check-out date must be in the future")
        private LocalDate checkOutDate;

        @NotNull(message = "Guest count is required")
        @Min(value = 1, message = "Guest count must be at least 1")
        private Integer guestCount;

        @NotBlank(message = "Guest name is required")
        @Size(max = GUEST_NAME_MAX_LENGTH, message = "Guest name too long")
        private String guestName;

        @Pattern(regexp = "^[0-9]{8,15}$", message = "Invalid phone format")
        private String guestPhone;

        @Email(message = "Invalid email format")
        private String guestEmail;

        @Size(max = 1000, message = "Special requests too long")
        private String specialRequests;

        /**
         * 選用：結帳時套用的促銷碼（Sprint 124，DEF-047／PRD US-010）。
         * 訂房結帳不像 PRODUCT 走購物車，沒有 Redis 儲存的已套用促銷碼，故由前端於送出訂房請求時明確帶入。
         */
        private String promoCode;
    }

    // ========== Update Booking Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private LocalDate checkInDate;
        private LocalDate checkOutDate;

        @Min(value = 1, message = "Guest count must be at least 1")
        private Integer guestCount;

        @Size(max = GUEST_NAME_MAX_LENGTH, message = "Guest name too long")
        private String guestName;

        @Pattern(regexp = "^[0-9]{8,15}$", message = "Invalid phone format")
        private String guestPhone;

        @Email(message = "Invalid email format")
        private String guestEmail;

        @Size(max = 1000, message = "Special requests too long")
        private String specialRequests;
    }

    // ========== Booking Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingResponse {
        private UUID id;
        private UUID tenantId;
        private UUID userId;
        private UUID roomListingId;
        private String roomTitle;
        private String coverImageUrl;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private Integer guestCount;
        private String status;
        private BigDecimal totalAmount;
        /** 下單當下套用的促銷碼（Sprint 124，DEF-047）；null 表示未使用優惠券 */
        private String promoCode;
        /** 下單當下的折扣金額（Sprint 124）；totalAmount 已扣除本欄位 */
        private BigDecimal discountAmount;
        private String currency;
        private String guestName;
        private String guestPhone;
        private String guestEmail;
        private String specialRequests;
        private Integer nightsCount;
        private LocalTime checkInTime;
        private LocalTime checkOutTime;
        private java.time.Instant createdAt;
        private java.time.Instant updatedAt;
    }

    // ========== Booking List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingListResponse {
        private UUID id;
        private UUID roomListingId;
        private String roomTitle;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private Integer guestCount;
        private String status;
        private BigDecimal totalAmount;
        private String currency;
        private Integer nightsCount;
        private java.time.Instant createdAt;
    }

    // ========== Calendar Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarResponse {
        private LocalDate date;
        private String status; // AVAILABLE, BOOKED, BLOCKED, MAINTENANCE
        private BigDecimal price;
        private UUID bookingId;
        // 動態定價每日調整（AI-2405b / AI-2406b）：可訂日有規則生效時填入——price 為調整後、originalPrice 為調整前。
        // 調整可為折扣（price < originalPrice）或加價（price > originalPrice）；priceAdjustmentType 明示方向
        // （DISCOUNT / MARKUP / NONE）。無規則生效時三者為 null（price 即基準價，向後相容）。
        private BigDecimal originalPrice;
        private String appliedRuleName;
        private String priceAdjustmentType;
    }

    // ========== Maintenance Request（PRD §5.5.3）==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceRequest {
        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;
    }

    // ========== Availability Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityRequest {
        @NotNull(message = "Room listing ID is required")
        private UUID roomListingId;

        @NotNull(message = "Check-in date is required")
        private LocalDate checkInDate;

        @NotNull(message = "Check-out date is required")
        private LocalDate checkOutDate;
    }

    // ========== Availability Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityResponse {
        private boolean available;
        private UUID roomListingId;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private Integer nightsCount;
        private BigDecimal totalPrice;
        private String currency;
        private List<CalendarResponse> calendarDetails;
        private String unavailableReason;

        /**
         * 動態定價調整（AI-2402 / AI-2406b）：有規則生效時填入。
         * originalTotalPrice = 調整前總價、totalPrice = 調整後總價（含漲價）、appliedRuleName = 套用的規則名。
         * discountAmount = 有號調整差額 = originalTotalPrice − totalPrice（正=折扣、負=加價）；
         * priceAdjustmentType 明示方向（DISCOUNT / MARKUP / NONE）。
         * 無規則生效時四者為 null，totalPrice 即為原價（向後相容）。
         */
        private BigDecimal originalTotalPrice;
        private BigDecimal discountAmount;
        private String appliedRuleName;
        private String priceAdjustmentType;
    }

    /**
     * availability 不可訂原因碼（Sprint 58 AI-2408）：取代原英文字串，前端依 code 查表顯示中文訊息。
     */
    public enum AvailabilityReasonCode {
        INVALID_DATE_RANGE,
        NOT_OPEN_FOR_BOOKING,
        BOOKED,
        BLOCKED,
        MAINTENANCE
    }
}