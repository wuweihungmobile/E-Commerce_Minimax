package com.nextkey.ecommerce.core.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.audit.AuditLog;
import com.nextkey.ecommerce.domain.repository.audit.AuditLogRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 共用稽核紀錄服務（Sprint 135，稽核日誌覆蓋率掃描）。
 * 抽取自 AdminService 既有的 recordAudit 私有方法（DEF-016），供 AdminService 以外、
 * 也執行敏感狀態變更的服務共用同一套 audit_log 持久化機制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 記錄一筆稽核紀錄。稽核失敗不應中斷主要業務流程。
     * actorUserId 為 null 時（例如 webhook 等無使用者情境）改用目前 TenantContext 的使用者。
     */
    public void record(String action, String entityType, UUID entityId, UUID tenantId,
                        String oldValue, String newValue, String reason, UUID actorUserId) {
        try {
            UUID userId = actorUserId != null ? actorUserId : TenantContext.getCurrentUser();
            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .tenantId(tenantId)
                    .userId(userId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .reason(reason)
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to persist audit log: action={}, entityId={}, error={}", action, entityId, e.getMessage());
        }
    }

    /** 以目前 TenantContext 使用者作為操作者。 */
    public void record(String action, String entityType, UUID entityId, UUID tenantId,
                        String oldValue, String newValue, String reason) {
        record(action, entityType, entityId, tenantId, oldValue, newValue, reason, null);
    }
}
