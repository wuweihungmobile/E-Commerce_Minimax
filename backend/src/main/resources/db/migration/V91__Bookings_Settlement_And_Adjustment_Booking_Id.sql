-- V91__Bookings_Settlement_And_Adjustment_Booking_Id.sql
-- DEF-353（Sprint 245 收尾拍板，Sprint 247 實作；PRD §6.2.1「訂房納入結算」）
--
-- 問題：core/settlement 只讀 Order，訂房（Booking）的收益與退款從未進結算。開放訂房真實收款
-- （Stripe）前必須補上（PRD §6.2.1 上線前提）。
--
-- 修法：比照 V83（orders.settled_statement_id）同一套「恰好結算一次」設計——訂房記錄它被哪一張
-- 結算單結算，結算時以 UPDATE ... WHERE settled_statement_id IS NULL 原子標記。可結算條件見
-- BookingRepository：status=COMPLETED（退房完成），或 status=CANCELLED 且 refund_status=NONE
-- 且存在一筆 SUCCESS/PARTIALLY_REFUNDED 付款（區分「從未收款」與「收款後依政策不退款」，partial
-- index 的 predicate 不能含跨表 EXISTS 子查詢，故索引僅覆蓋狀態條件，付款存在性由查詢本身判斷）。
--
-- 同時新增 settlement_statements.total_bookings（與既有 total_orders 分開計數，避免「總筆數」
-- 欄位在加入訂房後變成誤導）；adjustment_statements.order_id 改為 nullable 並新增 booking_id
-- （比照 payments 表 order_id/booking_id 互斥慣例——沒有 CHECK 約束，由應用層保證互斥），
-- 讓跨結算週期退款調整（SettlementAdjustmentService）也能對訂房退款生成調整單。
--
-- 歷史訂房不回填（NULL）：結算過去從未對訂房結算過，下一次週結算會把目前已符合條件、尚未認領的
-- 訂房（含 Sprint 245～246 已退房完成或 no-show 取消的既有資料）一併納入，比照 V83 既有先例。

ALTER TABLE bookings
    ADD COLUMN settled_statement_id UUID REFERENCES settlement_statements (id);

-- 由結算單反查其訂房（駁回時釋放、退款調整時定位）
CREATE INDEX idx_bookings_settled_statement
    ON bookings (settled_statement_id)
    WHERE settled_statement_id IS NOT NULL;

-- 結算時「某租戶尚未結算、狀態已符合可結算條件」的訂房（部分索引；付款存在性由查詢本身判斷，不在索引內）
CREATE INDEX idx_bookings_unsettled
    ON bookings (tenant_id, created_at)
    WHERE settled_statement_id IS NULL
      AND (status = 'COMPLETED' OR (status = 'CANCELLED' AND refund_status = 'NONE'));

ALTER TABLE settlement_statements
    ADD COLUMN total_bookings INTEGER DEFAULT 0;

COMMENT ON COLUMN settlement_statements.total_bookings IS
    '本期納入的訂房筆數（與 total_orders 分開計數，PRD §6.2.1：訂房與訂單同一張結算單、同週期同抽成）';

ALTER TABLE adjustment_statements
    ALTER COLUMN order_id DROP NOT NULL;

ALTER TABLE adjustment_statements
    ADD COLUMN booking_id UUID NULL REFERENCES bookings (id);

CREATE INDEX idx_adjustment_statements_booking
    ON adjustment_statements (booking_id)
    WHERE booking_id IS NOT NULL;
