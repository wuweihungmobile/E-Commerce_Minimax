-- V6__Tenant_Feature_Toggles_Fix.sql
-- NextKey E-Commerce Platform - M17 Feature Toggle 修正
-- Created: 2026-05-01
-- QA 驗證後修復：補充 numeric toggles 預設值
-- 前置需求: V9__System_Tenant_Init.sql (System Tenant 需先建立)

-- =============================================
-- 確認 System Tenant 存在
-- =============================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tenants WHERE id = '00000000-0000-0000-0000-000000000001') THEN
        RAISE EXCEPTION 'System Tenant (00000000-...) must be created by V9 migration first';
    END IF;
END $$;

-- =============================================
-- 修正 tenant_feature_toggles 表結構
-- 新增 numeric toggle 支援 (MAX_PRODUCTS, MAX_ROOMS, MAX_POSTS, COMMISSION_RATE)
-- =============================================

-- V1__Initial_Schema.sql 已經建立了 tenant_feature_toggles 表
-- 這裡只是確保 config JSONB 欄位正確

-- 確認 tenant_feature_toggles 表存在
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tenant_feature_toggles') THEN
        CREATE TABLE tenant_feature_toggles (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
            feature_key VARCHAR(100) NOT NULL,
            is_enabled BOOLEAN DEFAULT FALSE,
            config JSONB DEFAULT '{}',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(tenant_id, feature_key)
        );
        CREATE INDEX idx_feature_toggles_tenant ON tenant_feature_toggles(tenant_id);
    END IF;
END $$;

-- =============================================
-- 更新系統租戶的 Feature Toggle 預設值（依文件規格）
-- REF: Sprint 7 Plan L109-120, Sprint 7 User Stories L131-142
-- =============================================

-- 先刪除舊記錄再重新初始化（確保數值一致）
DELETE FROM tenant_feature_toggles WHERE tenant_id = '00000000-0000-0000-0000-000000000001';

-- Boolean toggles
INSERT INTO tenant_feature_toggles (tenant_id, feature_key, is_enabled, config, enabled_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'RETAIL_ENABLED', true, '{}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', 'BOOKING_ENABLED', true, '{}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', 'CMS_ENABLED', true, '{}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', 'ERP_ENABLED', true, '{}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', 'DYNAMIC_PRICING_ENABLED', true, '{}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000001', 'PROMO_ENABLED', false, '{}', NULL);

-- Numeric toggles (stored in config JSONB as {"value": number})
INSERT INTO tenant_feature_toggles (tenant_id, feature_key, is_enabled, config)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'MAX_PRODUCTS', true, '{"value": 100}'),
    ('00000000-0000-0000-0000-000000000001', 'MAX_ROOMS', true, '{"value": 20}'),
    ('00000000-0000-0000-0000-000000000001', 'MAX_POSTS', true, '{"value": 50}'),
    ('00000000-0000-0000-0000-000000000001', 'COMMISSION_RATE', true, '{"value": 0.05}');

-- =============================================
-- 確保所有 10 個 Feature Toggle 都存在於系統租戶
-- =============================================