-- 合併結帳（PRODUCT + ROOM 一次結清，DEF-048 擴大範圍）需要單一筆 PromoCodeUsage
-- 同時代表一個 Order 與一個 Booking（一次用券 = 一次額度，使用者已拍板）。
--
-- V76 的 chk_promo_code_usages_source 是 XOR（恰好一個非 null），無法表達「兩者皆非 null」
-- 這種合併結帳場景，改為「至少一個非 null」。單一類型結帳（既有 OrderService／BookingService
-- 各自的路徑）仍然只會寫入一欄非 null，不受影響。
ALTER TABLE promo_code_usages DROP CONSTRAINT chk_promo_code_usages_source;
ALTER TABLE promo_code_usages ADD CONSTRAINT chk_promo_code_usages_source
    CHECK (order_id IS NOT NULL OR booking_id IS NOT NULL);

-- 部分取消追蹤（使用者拍板：合併結帳中若只取消其中一邊，額度不退還，兩邊都取消才真正釋放）。
-- 只有合併結帳寫入的列會同時有 order_id 與 booking_id 兩者非 null；既有單一類型結帳的列
-- 這兩欄永遠是 null，取消時的既有邏輯（立即 REVOKED + 釋放額度）完全不受影響。
ALTER TABLE promo_code_usages ADD COLUMN order_released_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE promo_code_usages ADD COLUMN booking_released_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN promo_code_usages.order_released_at IS
    '合併結帳（order_id 與 booking_id 皆非 null）中，Order 側已取消的時間點；
     單一類型用券紀錄恆為 NULL。兩欄皆非 NULL 時才真正 REVOKED 並釋放額度。';
COMMENT ON COLUMN promo_code_usages.booking_released_at IS
    '合併結帳（order_id 與 booking_id 皆非 null）中，Booking 側已取消的時間點；
     單一類型用券紀錄恆為 NULL。兩欄皆非 NULL 時才真正 REVOKED 並釋放額度。';
