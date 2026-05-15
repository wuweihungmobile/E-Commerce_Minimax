package com.nextkey.ecommerce.api.dto.payment;

import java.math.BigDecimal;
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
public class PaymentDto {

    private UUID id;
    private UUID orderId;
    private UUID bookingId;
    private String paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String transactionId;
    private Instant paidAt;
    private Instant createdAt;
    private Instant updatedAt;
}