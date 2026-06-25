package com.nextkey.ecommerce.domain.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.notification.NotificationHistory;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

    Page<NotificationHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT COUNT(h) FROM NotificationHistory h WHERE h.userId = :userId AND h.isRead = false")
    int countUnreadByUserId(@Param("userId") UUID userId);
}
