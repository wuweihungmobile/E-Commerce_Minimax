-- V20: 建立優惠券資料表
-- 用於 M04 購物車優惠券驗證功能

CREATE TABLE promo_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING')),
    discount_value DECIMAL(12, 2) NOT NULL,
    min_purchase_amount DECIMAL(12, 2),
    max_discount_amount DECIMAL(12, 2),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    max_usage_count INTEGER,
    current_usage_count INTEGER DEFAULT 0,
    max_usage_per_user INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 建立索引
CREATE INDEX idx_promo_codes_tenant_id ON promo_codes(tenant_id);
CREATE INDEX idx_promo_codes_code ON promo_codes(code);
CREATE INDEX idx_promo_codes_active_dates ON promo_codes(start_date, end_date);