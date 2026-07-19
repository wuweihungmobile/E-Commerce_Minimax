-- Sprint 98 (PRD §8.2.3): tenant_members 邀請確認狀態機
-- 既有紀錄（皆為直接新增即生效）一律回填為 ACTIVE，不影響現有已加入的成員。
ALTER TABLE tenant_members ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('INVITED', 'ACTIVE', 'REMOVED'));

ALTER TABLE tenant_members ADD COLUMN invited_at TIMESTAMP WITH TIME ZONE;

UPDATE tenant_members SET invited_at = joined_at WHERE invited_at IS NULL;

CREATE INDEX idx_tenant_members_status ON tenant_members(status);
