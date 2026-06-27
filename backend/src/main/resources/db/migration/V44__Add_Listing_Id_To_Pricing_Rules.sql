-- Sprint 22 US-001: 延伸 pricing_rules 支援一般 Listing（product 商品）
-- 現有 room_listing_id 保持不動（後向相容）
ALTER TABLE pricing_rules
    ADD COLUMN listing_id UUID REFERENCES listings(id) ON DELETE CASCADE;

CREATE INDEX idx_pricing_rules_listing_id ON pricing_rules(listing_id);
