package com.nextkey.ecommerce.core.notification;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.notification.NotificationPreferenceDto;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.notification.UserNotificationPreference;
import com.nextkey.ecommerce.domain.repository.UserNotificationPreferenceRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final UserNotificationPreferenceRepository preferenceRepository;

    /**
     * 取得用戶所有通知偏好。
     * 無明確設定的 type × channel 組合，預設回傳 enabled = true。
     */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceDto.PreferenceItem> getPreferences(UUID userId) {
        Map<String, Boolean> stored = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        p -> p.getNotificationType().name() + "|" + p.getChannel().name(),
                        UserNotificationPreference::isEnabled));

        return Arrays.stream(Notification.NotificationType.values())
                .flatMap(type -> Arrays.stream(Notification.NotificationChannel.values())
                        .map(channel -> {
                            String key = type.name() + "|" + channel.name();
                            boolean enabled = stored.getOrDefault(key, true);
                            return NotificationPreferenceDto.PreferenceItem.builder()
                                    .notificationType(type.name())
                                    .channel(channel.name())
                                    .enabled(enabled)
                                    .build();
                        }))
                .collect(Collectors.toList());
    }

    /**
     * Upsert 單一通知偏好。
     */
    @Transactional
    public NotificationPreferenceDto.PreferenceItem upsertPreference(
            UUID userId, NotificationPreferenceDto.UpsertRequest request) {

        Notification.NotificationType type = parseNotificationType(request.getNotificationType());
        Notification.NotificationChannel channel = parseChannel(request.getChannel());

        Optional<UserNotificationPreference> existing =
                preferenceRepository.findByUserIdAndNotificationTypeAndChannel(userId, type, channel);

        UserNotificationPreference pref;
        if (existing.isPresent()) {
            pref = existing.get();
            pref.setEnabled(request.getEnabled());
        } else {
            pref = UserNotificationPreference.builder()
                    .userId(userId)
                    .notificationType(type)
                    .channel(channel)
                    .enabled(request.getEnabled())
                    .build();
        }

        pref = preferenceRepository.save(pref);
        log.info("Notification preference upserted: userId={} type={} channel={} enabled={}",
                userId, type, channel, pref.isEnabled());

        return NotificationPreferenceDto.PreferenceItem.builder()
                .notificationType(pref.getNotificationType().name())
                .channel(pref.getChannel().name())
                .enabled(pref.isEnabled())
                .build();
    }

    /**
     * 檢查用戶是否啟用指定 type × channel 的通知。
     * 無明確設定時預設回傳 true（向後相容）。
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId, Notification.NotificationType type, Notification.NotificationChannel channel) {
        return preferenceRepository
                .findByUserIdAndNotificationTypeAndChannel(userId, type, channel)
                .map(UserNotificationPreference::isEnabled)
                .orElse(true);
    }

    private Notification.NotificationType parseNotificationType(String value) {
        try {
            return Notification.NotificationType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_9000, "Invalid notificationType: " + value);
        }
    }

    private Notification.NotificationChannel parseChannel(String value) {
        try {
            return Notification.NotificationChannel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_9000, "Invalid channel: " + value);
        }
    }
}
