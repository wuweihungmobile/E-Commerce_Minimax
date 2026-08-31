-- Sprint 100 (PRD §9.5.1 / §2630): 訂單促銷碼落地與每人用券次數追蹤
--
-- 背景：PRD §9.5.1 明訂「M05 訂單建立時，系統在校驗 totalAmount 之後、寫入訂單之前」
-- 須驗證促銷碼並套用折扣，但既有實作只在加入購物車時驗證一次、折扣僅存在 Redis 購物車，
-- 訂單金額（Σsubtotal + shipping_fee）從未扣除折扣，且 promo_codes.current_usage_count
-- 永遠停留在 0（incrementUsageCount 全庫零呼叫者）、max_usage_per_user 全庫零讀取。

-- 1. 訂單留存促銷碼與折扣金額（供 PRD §2630 取消時退還優惠券、以及對帳/結算追溯）
ALTER TABLE orders ADD COLUMN promo_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

COMMENT ON COLUMN orders.promo_code IS
    '下單當下套用的促銷碼快照（PRD §9.5.1）；NULL 表示未使用優惠券';
COMMENT ON COLUMN orders.discount_amount IS
    '下單當下的折扣金額（PRD §9.5.1 步驟 4）；total_amount 已為扣除本欄位後的應付金額';

-- 2. 用券紀錄：支撐 promo_codes.max_usage_per_user（每人限用次數）
--    採「軟撤銷」而非實體刪除——比照 Sprint 98 tenant_members.status 的既有決策，
--    取消訂單退還額度時標記 REVOKED 而非刪列，保留可追溯的用券歷史。
CREATE TABLE promo_code_usages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promo_code_id UUID NOT NULL REFERENCES promo_codes(id),
    user_id UUID NOT NULL REFERENCES users(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

-- 每人限用次數的查詢主索引（countByPromoCodeIdAndUserIdAndStatus）
CREATE INDEX idx_promo_code_usages_promo_user
    ON promo_code_usages(promo_code_id, user_id, status);

-- 取消訂單時反查該筆訂單的用券紀錄
CREATE INDEX idx_promo_code_usages_order ON promo_code_usages(order_id);

COMMENT ON TABLE promo_code_usages IS
    '用券紀錄（Sprint 100）：支撐 promo_codes.max_usage_per_user 每人限用檢查。PRD 未定義此表，為落實既有 max_usage_per_user 欄位所需的自行設計資料模型';
COMMENT ON COLUMN promo_code_usages.status IS
    'ACTIVE=佔用額度；REVOKED=訂單取消後已退還額度（PRD §2630「優惠券：若已使用促銷碼，則退還」），計數時僅計 ACTIVE';
