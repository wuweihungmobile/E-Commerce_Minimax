-- V57__Create_AuditLog_Table.sql
-- 用途: 建立 audit_log 表，持久化 Admin 管理操作稽核紀錄（DEF-016）
-- 對應: Sprint 31 US-003 / DEF-016（Sprint 28 US-004 盤點發現 AdminService audit 僅 log.info）
--
-- 設計說明:
--   1. 欄位型別對齊 AuditLog entity（供 Hibernate schema-validation）
--   2. tenant_id / user_id 可為 null（部分平台級操作或系統自動流程）
--   3. 刻意不建 tenant_id 外鍵 CASCADE —— 稽核紀錄應獨立於租戶生命週期保留（歷史留存）
--   4. 索引: 依租戶 + 時間查詢（findByTenantIdOrderByCreatedAtDesc）

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    old_value TEXT,
    new_value TEXT,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);

COMMENT ON TABLE audit_log IS 'Admin 管理操作稽核紀錄（DEF-016 / Sprint 31 US-003）';
COMMENT ON COLUMN audit_log.action IS '操作動作，如 TENANT_APPROVED / TENANT_STATUS_UPDATED / USER_STATUS_UPDATED / FEATURE_TOGGLE_UPDATED';
