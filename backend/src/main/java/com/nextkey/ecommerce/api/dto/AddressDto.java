package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收貨地址簿 DTO（PRD §14.3.1 Phase 2-B，Sprint 87）
 */
public class AddressDto {

    private static final int RECIPIENT_NAME_MAX_LENGTH = 100;
    private static final int PHONE_MAX_LENGTH = 50;
    private static final int POSTAL_CODE_MAX_LENGTH = 20;
    private static final int CITY_MAX_LENGTH = 100;
    private static final int DISTRICT_MAX_LENGTH = 100;
    private static final int ADDRESS_LINE_MAX_LENGTH = 500;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "Recipient name is required")
        @Size(max = RECIPIENT_NAME_MAX_LENGTH, message = "Recipient name must not exceed 100 characters")
        private String recipientName;

        @NotBlank(message = "Phone is required")
        @Size(max = PHONE_MAX_LENGTH, message = "Phone must not exceed 50 characters")
        private String phone;

        @Size(max = POSTAL_CODE_MAX_LENGTH, message = "Postal code must not exceed 20 characters")
        private String postalCode;

        @NotBlank(message = "City is required")
        @Size(max = CITY_MAX_LENGTH, message = "City must not exceed 100 characters")
        private String city;

        @Size(max = DISTRICT_MAX_LENGTH, message = "District must not exceed 100 characters")
        private String district;

        @NotBlank(message = "Address line is required")
        @Size(max = ADDRESS_LINE_MAX_LENGTH, message = "Address line must not exceed 500 characters")
        private String addressLine;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {

        @Size(max = RECIPIENT_NAME_MAX_LENGTH, message = "Recipient name must not exceed 100 characters")
        private String recipientName;

        @Size(max = PHONE_MAX_LENGTH, message = "Phone must not exceed 50 characters")
        private String phone;

        @Size(max = POSTAL_CODE_MAX_LENGTH, message = "Postal code must not exceed 20 characters")
        private String postalCode;

        @Size(max = CITY_MAX_LENGTH, message = "City must not exceed 100 characters")
        private String city;

        @Size(max = DISTRICT_MAX_LENGTH, message = "District must not exceed 100 characters")
        private String district;

        @Size(max = ADDRESS_LINE_MAX_LENGTH, message = "Address line must not exceed 500 characters")
        private String addressLine;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String recipientName;
        private String phone;
        private String postalCode;
        private String city;
        private String district;
        private String addressLine;
        @com.fasterxml.jackson.annotation.JsonProperty("isDefault")
        private boolean isDefault;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
