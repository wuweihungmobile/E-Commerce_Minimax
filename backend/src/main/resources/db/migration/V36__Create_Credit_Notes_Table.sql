-- V36__Create_Credit_Notes_Table.sql
-- 建立貸項通知單表 (Credit Notes) - 用於結算單冲銷

CREATE TABLE credit_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    original_statement_id UUID NOT NULL REFERENCES settlement_statements(id),
    credit_note_number VARCHAR(30) NOT NULL,
    type VARCHAR(20) DEFAULT 'CREDIT_NOTE',
    amount DECIMAL(14, 2) NOT NULL,
    reason TEXT,
    issued_by UUID REFERENCES users(id),
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING',
    UNIQUE (tenant_id, credit_note_number)
);

-- 建立索引
CREATE INDEX idx_credit_notes_tenant ON credit_notes(tenant_id, issued_at DESC);
CREATE INDEX idx_credit_notes_original_statement ON credit_notes(original_statement_id);

COMMENT ON TABLE credit_notes IS '貸項通知單 - 用於結算單冲銷（如退款導致已結算金額需要調整）';
COMMENT ON COLUMN credit_notes.type IS '類型：固定為 CREDIT_NOTE';
COMMENT ON COLUMN credit_notes.amount IS '冲銷金額（負值）';
COMMENT ON COLUMN credit_notes.status IS 'PENDING / APPLIED';