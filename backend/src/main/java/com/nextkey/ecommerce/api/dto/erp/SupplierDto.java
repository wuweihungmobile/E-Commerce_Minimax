package com.nextkey.ecommerce.api.dto.erp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

/**
 * 供應商 DTO
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDto {

    private java.util.UUID id;

    @NotBlank(message = "Name is required")
    private String name;

    private String contactPerson;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;

    private String address;

    private String status; // ACTIVE / INACTIVE

    private Instant createdAt;

    private Instant updatedAt;
}