package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Logout Response DTO
 * 用於會員登出回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {

    /**
     * 登出是否成功
     */
    private boolean success;

    /**
     * 回應訊息
     */
    private String message;
}
