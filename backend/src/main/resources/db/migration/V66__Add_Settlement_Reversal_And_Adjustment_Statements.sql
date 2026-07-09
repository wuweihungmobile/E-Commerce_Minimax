-- Sprint 86: M07 結算跨週期退款調整單 + PAID 結算單逆轉雙重授權（PRD §6.2.1）
ALTER TABLE settlement_statements ADD COLUMN reversal_initiated_by UUID NULL REFERENCES users(id);
ALTER TABLE settlement_statements ADD COLUMN reversal_initiated_by_role VARCHAR(20) NULL;
ALTER TABLE settlement_statements ADD COLUMN reversal_requested_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE settlement_statements ADD COLUMN reversal_reason TEXT NULL;
ALTER TABLE settlement_statements ADD COLUMN adjustment_amount DECIMAL(14,2) DEFAULT 0;

CREATE TABLE adjustment_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    original_statement_id UUID NOT NULL REFERENCES settlement_statements(id),
    adjustment_type VARCHAR(30) NOT NULL DEFAULT 'REFUND_DEDUCTION',
    amount DECIMAL(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applied_statement_id UUID NULL REFERENCES settlement_statements(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX idx_adjustment_statements_tenant_status ON adjustment_statements(tenant_id, status);
CREATE INDEX idx_adjustment_statements_original ON adjustment_statements(original_statement_id);

COMMENT ON TABLE adjustment_statements IS '跨週期退款調整單（PRD §6.2.1）：APPROVED/PAID 結算單涉及退款時產生，於下一結算週期一併結算';
COMMENT ON COLUMN settlement_statements.reversal_initiated_by_role IS 'SUPER_ADMIN / CFO：發起逆轉的角色，確認時須為不同角色';
COMMENT ON COLUMN settlement_statements.adjustment_amount IS '本期折入的 adjustment_statements 總金額（負值），供對帳追溯';
