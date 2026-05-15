package com.nextkey.ecommerce.api.dto.payment;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundPaymentRequest {

    @NotNull(message = "Payment ID is required")
    private UUID paymentId;

    private String reason;
}