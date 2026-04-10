-- V2__Add_Order_State_Log.sql
-- Add Order State Log table for order state machine tracking

CREATE TABLE order_state_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_by UUID REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_state_log_order ON order_state_log(order_id);
CREATE INDEX idx_order_state_log_sequence ON order_state_log(order_id, sequence);