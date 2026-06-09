package com.nextkey.ecommerce.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate.NotificationChannel;
import com.nextkey.ecommerce.domain.model.notification.NotificationTemplate.NotificationType;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTemplateCode(String templateCode);

    Optional<NotificationTemplate> findByTemplateCodeAndTenantId(String templateCode, UUID tenantId);

    Page<NotificationTemplate> findByTenantIdOrderByPriorityDesc(UUID tenantId, Pageable pageable);

    Page<NotificationTemplate> findByTenantIdAndIsActiveTrueOrderByPriorityDesc(UUID tenantId, Pageable pageable);

    List<NotificationTemplate> findByNotificationTypeAndChannelAndIsActiveTrue(
            NotificationType notificationType, NotificationChannel channel);

    Optional<NotificationTemplate> findByNotificationTypeAndChannelAndTenantIdAndIsActiveTrue(
            NotificationType notificationType, NotificationChannel channel, UUID tenantId);

    @Query("SELECT t FROM NotificationTemplate t WHERE t.tenantId = :tenantId " +
           "AND t.deletedAt IS NULL " +
           "AND (:notificationType IS NULL OR t.notificationType = :notificationType) " +
           "AND (:channel IS NULL OR t.channel = :channel) " +
           "AND (:isActive IS NULL OR t.isActive = :isActive) " +
           "ORDER BY t.priority DESC")
    Page<NotificationTemplate> searchTemplates(
            @Param("tenantId") UUID tenantId,
            @Param("notificationType") NotificationType notificationType,
            @Param("channel") NotificationChannel channel,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    boolean existsByTemplateCodeAndTenantId(String templateCode, UUID tenantId);

    @Query("SELECT COUNT(t) FROM NotificationTemplate t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL")
    long countActiveByTenantId(@Param("tenantId") UUID tenantId);
}