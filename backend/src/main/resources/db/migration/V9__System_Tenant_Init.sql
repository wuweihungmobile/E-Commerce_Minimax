-- V9__System_Tenant_Init.sql
-- NextKey E-Commerce Platform - System Tenant 初始化
-- Created: 2026-05-03
-- 用途: 建立 System Tenant (00000000-0000-0000-0000-000000000001)
-- 前置需求: tenants 表已存在 (V1)
-- 影響: 為 System Tenant 相關功能提供基礎（如 Feature Toggle）

-- =============================================
-- 1. 建立 System Tenant
-- ID: 00000000-0000-0000-0000-000000000001
-- =============================================

INSERT INTO tenants (id, name, slug, status, description, contact_email, contact_phone, metadata)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'System Tenant',
    'system',
    'ACTIVE',
    'System-level tenant for platform-wide feature toggles and configurations',
    'system@nextkey.com',
    '0000000000',
    '{"type": "SYSTEM", "purpose": "platform-config"}'
)
ON CONFLICT (id) DO NOTHING;

-- =============================================
-- 2. 驗證 System Tenant 建立成功
-- =============================================
-- SELECT id, name, slug, status FROM tenants WHERE slug = 'system';
