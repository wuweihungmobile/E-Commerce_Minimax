-- Sprint 21 US-006: M11 運費模板 CRUD
-- 商家可設定固定運費（FIXED）或免運門檻（FREE_THRESHOLD）模板

CREATE TABLE shipping_templates (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    name           VARCHAR(100) NOT NULL,
    fee_type       VARCHAR(20)  NOT NULL CHECK (fee_type IN ('FIXED', 'FREE_THRESHOLD')),
    fixed_amount   DECIMAL(10, 2),
    free_threshold DECIMAL(10, 2),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_shipping_templates PRIMARY KEY (id)
);

CREATE INDEX idx_shipping_templates_tenant_id ON shipping_templates(tenant_id);
