package com.nextkey.ecommerce.domain.model.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.*;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String subject;

    @Column(name = "content_template", nullable = false, columnDefinition = "TEXT")
    private String contentTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> variables = List.of();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum NotificationType {
        ORDER_CONFIRMED,
        ORDER_PAID,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        ORDER_COMPLETED,
        ORDER_CANCELLED,
        BOOKING_CONFIRMED,
        BOOKING_REMINDER,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        REFUND_COMPLETED,
        REVIEW_REQUEST,
        NEW_MESSAGE,
        SYSTEM_ANNOUNCEMENT
    }

    public enum NotificationChannel {
        IN_APP,
        EMAIL,
        SMS,
        PUSH
    }
}