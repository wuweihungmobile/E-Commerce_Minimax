package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合併結帳 DTO（Sprint 126，DEF-048 擴大範圍：購物車同時有 PRODUCT 與 ROOM 項目時，
 * 一次動作同時結清兩者，單一張優惠券的折扣分攤到兩側）。
 */
public class CheckoutDto {

    private static final int SHIPPING_ADDRESS_MAX_LENGTH = 500;
    private static final int RECIPIENT_NAME_MAX_LENGTH = 200;
    private static final int PHONE_MAX_LENGTH = 50;
    private static final int GUEST_NAME_MAX_LENGTH = 200;

    // ========== Mixed Checkout Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MixedCheckoutRequest {

        // ---- PRODUCT 側收件資訊（比照 OrderDto.CreateRequest）----
        @Size(max = SHIPPING_ADDRESS_MAX_LENGTH, message = "Shipping address too long")
        private String shippingAddress;

        @Size(max = RECIPIENT_NAME_MAX_LENGTH, message = "Recipient name too long")
        private String shippingRecipientName;

        @Size(max = PHONE_MAX_LENGTH, message = "Phone too long")
        private String shippingPhone;

        /** 選用：從地址簿選擇的地址 ID，提供時覆蓋上方手動輸入欄位（比照 OrderDto.CreateRequest）。 */
        private UUID addressId;

        private String notes;

        // ---- ROOM 側訂房資訊（比照 BookingDto.CreateRequest；roomListingId/日期取自購物車項目，
        //      不由前端重複帶入，避免與購物車實際內容不一致）----
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

        // ---- 共用 ----
        /** 選用：結帳時套用的促銷碼，同時分攤到 PRODUCT 與 ROOM 兩側。 */
        private String promoCode;
    }

    // ========== Mixed Checkout Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MixedCheckoutResponse {
        private OrderDto.OrderResponse order;
        private BookingDto.BookingResponse booking;
        /** 套用的促銷碼；null 表示未套用。 */
        private String promoCode;
        /** 兩側折扣合計（= order.discountAmount + booking.discountAmount）。 */
        private BigDecimal totalDiscountAmount;
    }
}
