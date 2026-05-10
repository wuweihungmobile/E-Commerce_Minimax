package com.nextkey.ecommerce.core.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知服務 (Mock Implementation)
 * Phase 2 預留 RabbitMQ 整合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // Pagination and default values
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_NOTIFICATION_COUNT = 20;

    /**
     * 發送通知 (Mock)
     */
    @Transactional
    public NotificationDto.NotificationResponse sendNotification(NotificationDto.SendRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006, "User not found"));

        Notification.NotificationChannel channel = request.getChannel() != null
                ? Notification.NotificationChannel.valueOf(request.getChannel().name())
                : Notification.NotificationChannel.IN_APP;

        String recipient = request.getRecipient();
        if (recipient == null && channel != Notification.NotificationChannel.IN_APP) {
            recipient = getRecipientForChannel(user, channel);
        }

        Notification notification = Notification.builder()
                .userId(user.getId())
                .notificationType(Notification.NotificationType.valueOf(request.getNotificationType().name()))
                .title(request.getTitle())
                .content(request.getContent())
                .data(request.getData())
                .channel(channel)
                .recipient(recipient)
                .isSent(false)
                .isRead(false)
                .retryCount(0)
                .build();

        // Mock: 模擬發送成功
        notification.setIsSent(true);
        notification.setSentAt(Instant.now());

        notification = notificationRepository.save(notification);

        log.info("Notification sent: id={}, userId={}, type={}, channel={}",
                notification.getId(), user.getId(), request.getNotificationType(), channel);

        return toNotificationResponse(notification);
    }

    /**
     * 廣播通知 (Mock)
     */
    @Transactional
    public int broadcastNotification(final NotificationDto.BroadcastRequest request) {
        List<User> users;
        if (request.getTenantId() != null) {
            users = userRepository.findByTenantId(request.getTenantId());
        } else {
            users = userRepository.findAll();
        }

        int count = 0;
        for (User user : users) {
            try {
                NotificationDto.SendRequest sendRequest = NotificationDto.SendRequest.builder()
                        .userId(user.getId())
                        .notificationType(request.getNotificationType())
                        .title(request.getTitle())
                        .content(request.getContent())
                        .data(request.getData())
                        .channel(request.getChannel())
                        .build();

                sendNotification(sendRequest);
                count++;
            } catch (BusinessException | DataAccessException e) {
                log.warn("Failed to send notification to user: {}", user.getId(), e);
            }
        }

        log.info("Broadcast completed: type={}, count={}", request.getNotificationType(), count);
        return count;
    }

    /**
     * 取得用戶通知列表
     */
    @Transactional(readOnly = true)
    public NotificationDto.NotificationListResponse getUserNotifications(
            UUID userId, int page, int size, Boolean unreadOnly) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE));

        Page<Notification> notifications;
        if (Boolean.TRUE.equals(unreadOnly)) {
            notifications = notificationRepository
                    .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageRequest);
        } else {
            notifications = notificationRepository
                    .findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
        }

        int unreadCount = notificationRepository.countUnreadByUserId(userId);

        List<NotificationDto.NotificationResponse> responses = notifications.getContent().stream()
                .map(this::toNotificationResponse)
                .collect(Collectors.toList());

        return NotificationDto.NotificationListResponse.builder()
                .notifications(responses)
                .page(page)
                .size(size)
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * 標記已讀
     */
    @Transactional
    public NotificationDto.NotificationListResponse markAsRead(UUID userId, NotificationDto.MarkReadRequest request) {
        if (request.getNotificationIds() != null && !request.getNotificationIds().isEmpty()) {
            // 標記指定通知為已讀
            for (UUID notificationId : request.getNotificationIds()) {
                notificationRepository.findById(notificationId)
                        .filter(n -> n.getUserId().equals(userId))
                        .ifPresent(n -> {
                            n.setIsRead(true);
                            n.setReadAt(Instant.now());
                            notificationRepository.save(n);
                        });
            }
        } else {
            // 標記所有通知為已讀
            Page<Notification> unread = notificationRepository
                    .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, Integer.MAX_VALUE));
            for (Notification n : unread.getContent()) {
                n.setIsRead(true);
                n.setReadAt(Instant.now());
                notificationRepository.save(n);
            }
        }

        log.info("Notifications marked as read: userId={}", userId);

        return getUserNotifications(userId, 0, DEFAULT_NOTIFICATION_COUNT, false);
    }

    /**
     * 刪除通知
     */
    @Transactional
    public void deleteNotification(final UUID userId, final UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_8000, "Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.E_1007, "Cannot delete notification of other user");
        }

        notificationRepository.delete(notification);
        log.info("Notification deleted: id={}", notificationId);
    }

    /**
     * 取得未讀計數
     */
    @Transactional(readOnly = true)
    public NotificationDto.UnreadCountResponse getUnreadCount(UUID userId) {
        int count = notificationRepository.countUnreadByUserId(userId);

        return NotificationDto.UnreadCountResponse.builder()
                .userId(userId)
                .unreadCount(count)
                .build();
    }

    // ========== Mock Helper Methods ==========

    private String getRecipientForChannel(final User user, final Notification.NotificationChannel channel) {
        return switch ( channel) {
            case EMAIL -> user.getEmail();
            case SMS -> user.getPhone();
            default -> null;
        };
    }

    private NotificationDto.NotificationResponse toNotificationResponse(Notification notification) {
        return NotificationDto.NotificationResponse.builder()
                .notificationId(notification.getId())
                .userId(notification.getUserId())
                .notificationType(notification.getNotificationType().name())
                .title(notification.getTitle())
                .content(notification.getContent())
                .data(notification.getData())
                .channel(notification.getChannel().name())
                .recipient(notification.getRecipient())
                .isSent(notification.getIsSent())
                .isRead(notification.getIsRead())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .retryCount(notification.getRetryCount())
                .errorMessage(notification.getErrorMessage())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
