package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 以連結重設密碼（Sprint 204，FRD US-M03-006）。
 *
 * <p>新密碼規則與 {@link RegisterRequest#getPassword()} 相同（FRD BR-M03-002）。格式驗證在進到 Service 之前就完成，
 * 所以格式不合時連結不會被消耗，使用者可以改好密碼再送一次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    private static final int PASSWORD_MAX_LENGTH = 128;
    /** 我們簽發的 token 為 43 字元；上限放寬到 128 只為擋掉過長輸入，不需精確。 */
    private static final int TOKEN_MAX_LENGTH = 128;

    @NotBlank(message = "Token is required")
    @Size(max = TOKEN_MAX_LENGTH, message = "Token is too long")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = PASSWORD_MAX_LENGTH, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Password must contain uppercase, lowercase and numeric characters"
    )
    private String newPassword;
}
