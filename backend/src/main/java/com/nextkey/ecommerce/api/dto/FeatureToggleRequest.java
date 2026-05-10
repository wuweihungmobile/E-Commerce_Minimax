package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggleRequest {

    @NotNull(message = "enabled field is required")
    private Boolean enabled;
}