package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stripe Connect Express 帳戶 onboarding DTO（Sprint 53 AI-2413 Phase D-1）。
 */
public class StripeConnectDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnboardingResponse {
        private String onboardingUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResponse {
        private String accountId;
        private String onboardingStatus;
        private boolean chargesEnabled;
        private boolean payoutsEnabled;
    }
}
