-- V19__Fix_Stock_Movement_Reference_Type_Constraint.sql
-- 修復 stock_movements.reference_type CHECK 約束，添加 MANUAL 值
-- 當前約束缺少 MANUAL 選項

-- 刪除現有的約束
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_reference_type_check;

-- 重新添加約束，包含所有有效的 ReferenceType 值（包括 MANUAL）
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_reference_type_check
    CHECK (reference_type IS NULL OR reference_type IN (
        'PURCHASE_ORDER',
        'ORDER',
        'INVENTORY_CHECK',
        'TRANSFER',
        'MANUAL'
    ));