-- V35__Create_Settlement_Statements_Table.sql
-- 建立結算單表 (Settlement Statements)

CREATE TABLE settlement_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    statement_number VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_orders INTEGER DEFAULT 0,
    total_gmv DECIMAL(14, 2) DEFAULT 0,
    total_refunds DECIMAL(14, 2) DEFAULT 0,
    commission_amount DECIMAL(14, 2) DEFAULT 0,
    net_settlement_amount DECIMAL(14, 2) DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'TWD',
    status VARCHAR(20) DEFAULT 'PENDING',
    generated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID REFERENCES users(id),
    rejection_reason TEXT,
    approved_at TIMESTAMP WITH TIME ZONE,
    paid_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, statement_number)
);

-- 建立索引
CREATE INDEX idx_settlement_tenant_period ON settlement_statements(tenant_id, period_start DESC);
CREATE INDEX idx_settlement_status ON settlement_statements(status);

COMMENT ON TABLE settlement_statements IS '結算單 - 記錄每週商家的收益結算狀態';
COMMENT ON COLUMN settlement_statements.statement_number IS '結算單號 (Tenant 內唯一)';
COMMENT ON COLUMN settlement_statements.period_start IS '結算週期開始日期';
COMMENT ON COLUMN settlement_statements.period_end IS '結算週期結束日期';
COMMENT ON COLUMN settlement_statements.status IS 'PENDING / PENDING_REVIEW / APPROVED / REJECTED / PAID / FAILED';