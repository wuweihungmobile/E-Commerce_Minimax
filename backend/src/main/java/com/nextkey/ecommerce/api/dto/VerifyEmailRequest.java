package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 以連結完成 Email 驗證（Sprint 204，FRD US-M03-007）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailRequest {

    private static final int TOKEN_MAX_LENGTH = 128;

    @NotBlank(message = "Token is required")
    @Size(max = TOKEN_MAX_LENGTH, message = "Token is too long")
    private String token;
}
