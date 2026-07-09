-- Sprint 85: M16 ERP 採購審批金額上限機制（PRD §6.7.2）
ALTER TABLE tenants ADD COLUMN purchase_order_approval_threshold NUMERIC(12,2) NULL;
ALTER TABLE purchase_orders ADD COLUMN reviewed_by UUID NULL;
ALTER TABLE purchase_orders ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE purchase_orders ADD COLUMN rejection_reason TEXT NULL;
