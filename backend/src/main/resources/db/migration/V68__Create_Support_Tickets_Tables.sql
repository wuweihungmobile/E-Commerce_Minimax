-- Sprint 91: M18 客服工單子系統（PRD §6.10，M18_Knowledge_Management_SPEC.md 第 5 章）
CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id),
    ticket_number VARCHAR(20) NOT NULL UNIQUE,
    category VARCHAR(20) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    customer_id UUID NOT NULL REFERENCES users(id),
    assigned_to UUID REFERENCES users(id),
    order_id UUID REFERENCES orders(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_support_tickets_tenant ON support_tickets(tenant_id);
CREATE INDEX idx_support_tickets_customer ON support_tickets(customer_id);

CREATE TABLE support_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    sender_type VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    attachments JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_messages_ticket ON support_messages(ticket_id);

COMMENT ON TABLE support_tickets IS '客服工單（PRD §6.10 M18 Phase 2-B）：tenant_id 為 NULL 表示平台工單（無關聯店家），僅 ADMIN/SUPER_ADMIN 可見';
COMMENT ON TABLE support_messages IS '客服工單訊息串，sender_type 區分 CUSTOMER/STAFF/SYSTEM';
