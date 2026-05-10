package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 會員註冊成功回應 DTO
 * 對應 API-M03-001 / US-M03-001
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    private UUID userId;
    private String email;
    private String userType;
    private Instant createdAt;
}
