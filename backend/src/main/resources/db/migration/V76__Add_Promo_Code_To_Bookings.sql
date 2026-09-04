-- Sprint 124 (DEF-047 / PRD US-010)：訂房（民宿預訂）結帳套用促銷碼
--
-- 背景：PRD US-010「作為預訂買家，我希望在結帳時套用優惠碼」。Sprint 100（V70）已對商品訂單
-- （OrderService.createOrderFromCart）落地促銷碼，但訂房走的是完全不同的服務／實體：
-- BookingController → BookingService.createBooking，寫入 bookings 表（非 orders 表），
-- bookings 表從未有促銷碼相關欄位，此路徑對 PRD US-010 完全沒有落地。

-- 1. 訂房留存促銷碼與折扣金額（比照 orders 表 V70 的作法，供取消時退還優惠券、對帳追溯）
ALTER TABLE bookings ADD COLUMN promo_code VARCHAR(50);
ALTER TABLE bookings ADD COLUMN discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

COMMENT ON COLUMN bookings.promo_code IS
    '下單當下套用的促銷碼快照（Sprint 124，PRD US-010）；NULL 表示未使用優惠券';
COMMENT ON COLUMN bookings.discount_amount IS
    '下單當下的折扣金額（Sprint 124）；total_amount 已為扣除本欄位後的應付金額';

-- 2. promo_code_usages（V70）的 order_id 是 NOT NULL 且 REFERENCES orders(id)，訂房的用券紀錄
--    不能塞進同一欄（booking id 不在 orders 表裡，會違反 FK）。改為 order_id 可為 null、
--    新增 booking_id（FK bookings），並以 CHECK 約束「恰好一個來源非 null」，避免一筆用券紀錄
--    同時屬於訂單與訂房、或兩者都不屬於的髒資料。
ALTER TABLE promo_code_usages ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE promo_code_usages ADD COLUMN booking_id UUID REFERENCES bookings(id);
ALTER TABLE promo_code_usages ADD CONSTRAINT chk_promo_code_usages_source
    CHECK ((order_id IS NOT NULL) <> (booking_id IS NOT NULL));

-- 取消預訂時反查該筆預訂的用券紀錄（比照 V70 的 idx_promo_code_usages_order）
CREATE INDEX idx_promo_code_usages_booking ON promo_code_usages(booking_id);
