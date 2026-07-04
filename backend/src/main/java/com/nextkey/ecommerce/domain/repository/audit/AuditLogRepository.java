package com.nextkey.ecommerce.domain.repository.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.nextkey.ecommerce.domain.model.audit.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    // 平台管理者查詢稽核紀錄（Sprint 61 US-001，DEF-016 後續）：見 AdminService#getAuditLogs。
    // 改用 Specification 動態組合可選篩選條件（action/startDate/endDate），避免靜態 JPQL
    // 的「:param IS NULL OR ...」寫法在 PostgreSQL 下對純 null 參數觸發
    // "could not determine data type of parameter" 錯誤（已於 Sprint 61 開發時實測踩雷）。
}
