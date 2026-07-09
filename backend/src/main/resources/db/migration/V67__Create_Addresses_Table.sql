-- Sprint 87: 收貨地址簿（PRD §14.3.1 Phase 2-B）
CREATE TABLE addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20),
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100),
    address_line VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_addresses_user ON addresses(user_id);

COMMENT ON TABLE addresses IS '收貨地址簿（PRD §14.3.1 Phase 2-B）：買家個人資料，與租戶無關';
COMMENT ON COLUMN addresses.is_default IS '每個使用者僅可有一筆預設地址（應用層保證，非 DB constraint）';
