package com.nextkey.ecommerce.api.dto.payment;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stripe Checkout Session 建立回應（Sprint 50 AI-2410，hosted Checkout）。
 * 前端以 {@code sessionUrl} 重導至 Stripe 託管付款頁。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSessionResponse {
    private UUID orderId;
    /** 訂房付款的結帳（Sprint 221）：與 {@link #orderId} 二擇一，另一個為 null。 */
    private UUID bookingId;
    private String sessionId;
    private String sessionUrl;
}
