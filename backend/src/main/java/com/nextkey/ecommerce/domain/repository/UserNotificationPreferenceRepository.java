package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.notification.UserNotificationPreference;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {

    List<UserNotificationPreference> findByUserId(UUID userId);

    Optional<UserNotificationPreference> findByUserIdAndNotificationTypeAndChannel(
            UUID userId,
            Notification.NotificationType notificationType,
            Notification.NotificationChannel channel);
}
