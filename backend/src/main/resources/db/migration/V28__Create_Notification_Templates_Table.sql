-- V28__Create_Notification_Templates_Table.sql
-- 通知模板系統

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    template_code VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    name VARCHAR(200) NOT NULL,
    subject VARCHAR(500),
    content_template TEXT NOT NULL,
    variables JSONB DEFAULT '[]',
    is_active BOOLEAN DEFAULT true,
    priority INTEGER DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_notification_template_code UNIQUE (tenant_id, template_code)
);

COMMENT ON TABLE notification_templates IS '通知模板表';
COMMENT ON COLUMN notification_templates.template_code IS '模板代碼，如 ORDER_CONFIRMED_EMAIL';
COMMENT ON COLUMN notification_templates.notification_type IS '通知類型，如 ORDER_CONFIRMED, PAYMENT_SUCCESS';
COMMENT ON COLUMN notification_templates.channel IS '通知渠道：IN_APP, EMAIL, SMS, PUSH';
COMMENT ON COLUMN notification_templates.content_template IS '內容模板，變量格式：{{variable_name}}';
COMMENT ON COLUMN notification_templates.variables IS '變量列表 JSONB';

-- 建立索引
CREATE INDEX idx_notification_templates_type ON notification_templates(notification_type);
CREATE INDEX idx_notification_templates_channel ON notification_templates(channel);
CREATE INDEX idx_notification_templates_tenant ON notification_templates(tenant_id);
CREATE INDEX idx_notification_templates_active ON notification_templates(is_active) WHERE deleted_at IS NULL;