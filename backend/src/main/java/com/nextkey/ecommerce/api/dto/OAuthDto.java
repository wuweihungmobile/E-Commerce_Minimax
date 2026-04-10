package com.nextkey.ecommerce.api.dto;

import com.nextkey.ecommerce.domain.model.user.OAuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

public class OAuthDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthRequest {
        @NotNull(message = "Provider is required")
        private OAuthProvider provider;

        @NotBlank(message = "Authorization code is required")
        private String code;

        // Optional: redirect URI used in the authorization request
        private String redirectUri;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkRequest {
        @NotNull(message = "Provider is required")
        private OAuthProvider provider;

        @NotBlank(message = "Authorization code is required")
        private String code;

        private String redirectUri;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OAuthUserInfo {
        private String provider;
        private String providerUserId;
        private String email;
        private String name;
        private String pictureUrl;
    }
}