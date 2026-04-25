-- V4__Tenant_Application.sql
-- NextKey E-Commerce Platform - M17 Tenant Application Schema
-- Created: 2026-04-20

-- =============================================
-- Tenant Applications (租戶申請)
-- =============================================

CREATE TABLE tenant_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID,  -- nullable for Guest users (no authentication required)
    store_name VARCHAR(100) NOT NULL,
    store_description TEXT,
    business_type VARCHAR(50) NOT NULL,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(20),
    business_license_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_applications_user ON tenant_applications(user_id);
CREATE INDEX idx_tenant_applications_status ON tenant_applications(status);
CREATE INDEX idx_tenant_applications_tenant ON tenant_applications(tenant_id);

-- =============================================
-- Trigger: Auto-update updated_at for tenant_applications
-- =============================================
CREATE TRIGGER update_tenant_applications_updated_at BEFORE UPDATE ON tenant_applications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();