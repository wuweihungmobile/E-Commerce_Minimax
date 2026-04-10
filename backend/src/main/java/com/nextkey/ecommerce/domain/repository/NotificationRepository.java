package com.nextkey.ecommerce.domain.repository;

import com.nextkey.ecommerce.domain.model.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndNotificationTypeAndIsSentFalse(UUID userId, Notification.NotificationType type);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false")
    int countUnreadByUserId(@Param("userId") UUID userId);

    @Query("SELECT n FROM Notification n WHERE n.isSent = false AND n.retryCount < :maxRetries")
    List<Notification> findPendingNotifications(@Param("maxRetries") int maxRetries);

    List<Notification> findByNotificationTypeAndCreatedAtAfter(Notification.NotificationType type, Instant after);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.notificationType = :type AND n.createdAt > :since")
    int countByUserIdAndTypeSince(@Param("userId") UUID userId, @Param("type") Notification.NotificationType type, @Param("since") Instant since);
}
