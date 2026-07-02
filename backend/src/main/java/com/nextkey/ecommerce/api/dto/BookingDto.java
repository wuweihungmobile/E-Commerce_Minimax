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
    }

    // ========== Update Booking Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private Integer guestCount;
        private String guestName;
        private String guestPhone;
        private String guestEmail;
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
         * 動態定價折扣（AI-2402）：套用早鳥/長住/末班車折扣時填入。
         * originalTotalPrice = 折扣前總價、discountAmount = 折扣金額、appliedRuleName = 套用的規則名。
         * 無折扣時三者為 null，totalPrice 即為原價（向後相容）。
         */
        private BigDecimal originalTotalPrice;
        private BigDecimal discountAmount;
        private String appliedRuleName;
    }
}