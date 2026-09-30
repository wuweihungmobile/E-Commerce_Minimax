-- V84__Bookings_Payment_Due_At.sql
-- DEF-311（Sprint 225，使用者 2026-10-01 對「訂房未付款逾時」回覆「依照建議」）
--
-- 問題：訂房沒有未付款逾時取消。訂單有（Sprint 219，24 小時），但訂房在 Sprint 221 之前根本沒有付款入口，
-- 所有既有訂房都停在 CREATED。若直接套用「建立超過 24 小時仍未付款就取消」，部署後第一輪排程會把它們全部取消並釋放日曆。
--
-- 修法：訂房自己記錄付款期限 payment_due_at。
--   * 新建立的訂房：建立時間 + 24 小時（app.booking-timeout.payment-hours）。
--   * 歷史訂房：不回填（NULL）＝永不因逾時被取消。它們從來沒有機會付款，不能追溯適用新規則。
--
-- 只有訂房建立時寫入；排程以 payment_due_at < now 判斷逾時。

ALTER TABLE bookings
    ADD COLUMN payment_due_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN bookings.payment_due_at IS
    '付款期限（Sprint 225，DEF-311）；CREATED 且超過此時間仍未付款的訂房由排程取消。NULL＝歷史訂房，永不逾時';

-- 排程「找出逾時未付款訂房」的部分索引：只含待付款且有期限者，隨訂房付款／取消自然縮小
CREATE INDEX idx_bookings_payment_due
    ON bookings (payment_due_at)
    WHERE status = 'CREATED' AND payment_due_at IS NOT NULL;
