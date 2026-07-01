package com.nextkey.ecommerce.domain.model.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理操作稽核紀錄（DEF-016）。持久化 AdminService 的關鍵管理操作，
 * 取代原本僅 log.info 的做法。
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 操作對象所屬租戶（部分平台級操作可能為 null） */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** 執行操作者（管理員），系統自動流程可能為 null */
    @Column(name = "user_id")
    private UUID userId;

    /** 操作動作（如 TENANT_APPROVED / TENANT_STATUS_UPDATED / USER_STATUS_UPDATED / FEATURE_TOGGLE_UPDATED） */
    @Column(nullable = false, length = 100)
    private String action;

    /** 受影響實體類型（如 TENANT / USER / FEATURE_TOGGLE） */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** 受影響實體 ID */
    @Column(name = "entity_id")
    private UUID entityId;

    /** 變更前值（自由文字/序列化描述） */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** 變更後值 */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** 操作原因 */
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
