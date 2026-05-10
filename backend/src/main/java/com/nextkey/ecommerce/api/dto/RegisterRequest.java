package com.nextkey.ecommerce.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    // Validation constraints
    private static final int PASSWORD_MAX_LENGTH = 128;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = PASSWORD_MAX_LENGTH, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Password must contain uppercase, lowercase and numeric characters"
    )
    private String password;

    private String fullName;

    private String phone;

    /** 會員類型：BUYER / SELLER / HOST / STORE_OWNER / STORE_STAFF，預設 BUYER */
    @Pattern(regexp = "^(BUYER|SELLER|HOST|STORE_OWNER|STORE_STAFF)$", message = "userType must be BUYER, SELLER, HOST, STORE_OWNER, or STORE_STAFF")
    private String userType;

    /** 關聯的 Tenant ID（可選） */
    private UUID tenantId;
}
