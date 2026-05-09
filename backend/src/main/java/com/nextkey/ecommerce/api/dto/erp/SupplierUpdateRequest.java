package com.nextkey.ecommerce.api.dto.erp;

import lombok.*;

/**
 * 更新供應商請求
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierUpdateRequest {

    private String name;

    private String contactPerson;

    private String email;

    private String phone;

    private String address;

    private String status; // ACTIVE / INACTIVE
}