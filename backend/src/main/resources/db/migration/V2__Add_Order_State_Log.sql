-- V2__Add_Order_State_Log.sql
-- Add Order State Log table for order state machine tracking
-- 注意：此 table 已在 V1__Initial_Schema.sql 中建立，此 migration 使用 IF NOT EXISTS 避免衝突

CREATE TABLE IF NOT EXISTS order_state_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_by UUID REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_state_log_order ON order_state_log(order_id);
CREATE INDEX IF NOT EXISTS idx_order_state_log_sequence ON order_state_log(order_id, sequence);