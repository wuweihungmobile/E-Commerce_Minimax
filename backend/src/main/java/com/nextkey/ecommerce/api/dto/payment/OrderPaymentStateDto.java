package com.nextkey.ecommerce.api.dto.payment;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaymentStateDto {

    private UUID orderId;
    private String orderStatus;
    private UUID paymentId;
    private String paymentStatus;
    private String transactionId;
    private String nextValidStates;
    private Boolean canPay;
    private Boolean canCancel;
    private Boolean canRefund;
    private Instant paidAt;
    private Instant updatedAt;
    // 真實金流（Sprint 50 AI-2410）：付款提供者（mock / stripe），前端據此決定付款 UI（重導 or mock 按鈕）
    private String paymentProvider;
}