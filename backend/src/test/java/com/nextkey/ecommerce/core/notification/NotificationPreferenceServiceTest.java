package com.nextkey.ecommerce.core.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.api.dto.notification.NotificationPreferenceDto;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.notification.UserNotificationPreference;
import com.nextkey.ecommerce.domain.repository.UserNotificationPreferenceRepository;

@DisplayName("NotificationPreferenceService Tests")
@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private UserNotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationPreferenceService preferenceService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("TC-001: getPreferences — 無任何設定時，所有組合預設回傳 enabled = true")
    void getPreferences_noStoredPreferences_allDefaultEnabled() {
        when(preferenceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<NotificationPreferenceDto.PreferenceItem> result = preferenceService.getPreferences(USER_ID);

        // 13 type × 4 channel = 52 items
        assertEquals(
                Notification.NotificationType.values().length * Notification.NotificationChannel.values().length,
                result.size());
        assertTrue(result.stream().allMatch(NotificationPreferenceDto.PreferenceItem::isEnabled),
                "All preferences should default to enabled when no record exists");
    }

    @Test
    @DisplayName("TC-002: upsertPreference — 不存在時建立新記錄")
    void upsertPreference_noExisting_createsNewRecord() {
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(any(), any(), any()))
                .thenReturn(Optional.empty());

        UserNotificationPreference saved = UserNotificationPreference.builder()
                .userId(USER_ID)
                .notificationType(Notification.NotificationType.ORDER_CONFIRMED)
                .channel(Notification.NotificationChannel.EMAIL)
                .enabled(false)
                .build();
        when(preferenceRepository.save(any())).thenReturn(saved);

        NotificationPreferenceDto.UpsertRequest request = NotificationPreferenceDto.UpsertRequest.builder()
                .notificationType("ORDER_CONFIRMED")
                .channel("EMAIL")
                .enabled(false)
                .build();

        NotificationPreferenceDto.PreferenceItem result = preferenceService.upsertPreference(USER_ID, request);

        assertNotNull(result);
        assertEquals("ORDER_CONFIRMED", result.getNotificationType());
        assertEquals("EMAIL", result.getChannel());
        assertFalse(result.isEnabled());
        verify(preferenceRepository).save(any(UserNotificationPreference.class));
    }

    @Test
    @DisplayName("TC-003: isEnabled — 偏好設定為 false 時回傳 false（sendNotification 跳過邏輯依賴此方法）")
    void isEnabled_preferenceDisabled_returnsFalse() {
        UserNotificationPreference pref = UserNotificationPreference.builder()
                .userId(USER_ID)
                .notificationType(Notification.NotificationType.PAYMENT_SUCCESS)
                .channel(Notification.NotificationChannel.EMAIL)
                .enabled(false)
                .build();
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                USER_ID,
                Notification.NotificationType.PAYMENT_SUCCESS,
                Notification.NotificationChannel.EMAIL))
                .thenReturn(Optional.of(pref));

        boolean result = preferenceService.isEnabled(
                USER_ID,
                Notification.NotificationType.PAYMENT_SUCCESS,
                Notification.NotificationChannel.EMAIL);

        assertFalse(result, "Should return false when preference is explicitly disabled");
    }
}
