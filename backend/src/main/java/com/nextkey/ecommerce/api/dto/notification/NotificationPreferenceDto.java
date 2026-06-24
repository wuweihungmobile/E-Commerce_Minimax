package com.nextkey.ecommerce.api.dto.notification;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class NotificationPreferenceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertRequest {
        @NotNull(message = "Notification type is required")
        private String notificationType;

        @NotNull(message = "Channel is required")
        private String channel;

        @NotNull(message = "Enabled flag is required")
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreferenceItem {
        private String notificationType;
        private String channel;
        private boolean enabled;
    }
}
