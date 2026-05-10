package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggleUpdateResponse {

    private String featureKey;
    private Boolean previousState;
    private Boolean newState;
    private String status;
    private String statusDescription;
}