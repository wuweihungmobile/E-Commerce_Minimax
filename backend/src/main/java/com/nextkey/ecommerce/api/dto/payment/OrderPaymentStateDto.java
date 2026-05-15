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
}