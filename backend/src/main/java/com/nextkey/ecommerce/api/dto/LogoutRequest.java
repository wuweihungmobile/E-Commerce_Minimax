package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Logout Request DTO
 * 用於會員登出請求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    /**
     * 要失效的 Refresh Token
     * 若不提供，則失效當前用戶的所有 Refresh Token
     */
    private String refreshToken;
}
