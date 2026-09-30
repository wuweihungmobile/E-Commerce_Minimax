-- V85__Bookings_Cancellation_Refund.sql
-- DEF-312（Sprint 227，使用者 2026-10-01 對「已付款訂房取消後錢沒有退」回覆「請對齊PRD」）
--
-- PRD §15.2.5／Q14（§17.4.5）：取消前 >= 24 小時全額退款；取消前 < 24 小時不退款；商家主動取消一律全額退款。
-- 取消回應含 canceledAt／canceledBy／refundStatus（NONE | PENDING | COMPLETED）／refundAmount。
--
-- 訂房沒有訂單的 REFUNDING 狀態，所以取消時把「這次取消該退多少」與退款進度記在訂房自己身上：
--   cancelled_at   取消時間
--   cancelled_by   取消方（CUSTOMER 買家本人、MERCHANT 商家／管理員代為取消、SYSTEM 逾時等系統取消）
--   refund_status  NONE 不需退款（未付款，或依 Q14 不退）、PENDING 等待自動退款、COMPLETED 已退回
--   refund_amount  應退金額（refund_status = NONE 時為 NULL）
-- 自動退款排程（RefundProcessingService）把 PENDING 的訂房退完後轉 COMPLETED。
--
-- 歷史資料：既有訂房 refund_status 一律為 NONE（過去沒有任何自動退款），cancelled_at／cancelled_by 為 NULL（未知）。

ALTER TABLE bookings
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancelled_by VARCHAR(20),
    ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN refund_amount DECIMAL(12, 2);

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_cancelled_by CHECK (cancelled_by IN ('CUSTOMER', 'MERCHANT', 'SYSTEM')),
    ADD CONSTRAINT chk_bookings_refund_status CHECK (refund_status IN ('NONE', 'PENDING', 'COMPLETED'));

COMMENT ON COLUMN bookings.cancelled_at IS '取消時間（Sprint 227，DEF-312）；NULL＝未取消，或歷史取消（未知）';
COMMENT ON COLUMN bookings.cancelled_by IS '取消方（Sprint 227）：CUSTOMER 買家本人／MERCHANT 商家或管理員代為取消／SYSTEM 系統（逾時）';
COMMENT ON COLUMN bookings.refund_status IS '退款進度（Sprint 227）：NONE 不需退款／PENDING 等待自動退款／COMPLETED 已退回';
COMMENT ON COLUMN bookings.refund_amount IS '取消時決定的應退金額（Sprint 227，PRD Q14）；refund_status = NONE 時為 NULL';

-- 自動退款排程「找出等待退款的訂房」的部分索引：只含 PENDING 者，隨退款完成自然縮小
CREATE INDEX idx_bookings_refund_pending
    ON bookings (id)
    WHERE refund_status = 'PENDING';
