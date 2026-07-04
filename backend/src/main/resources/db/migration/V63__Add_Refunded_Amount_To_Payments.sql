-- V63__Add_Refunded_Amount_To_Payments.sql
-- NextKey E-Commerce Platform
-- Sprint 56 US-001 (AI-2415): 部分退款——任意金額，運費不退
-- 用途: payments 加 refunded_amount，追蹤累計已退款金額（支援部分退款 + 判斷是否已全額退款）。
-- 安全性 / 向後相容: NOT NULL DEFAULT 0 → 既有列（全數尚未退款）皆為 0，不影響既有查詢。
-- 慣例: 續 V61（stripe_refund_id）/ V62（Connect 欄位）的付款 schema 演進；ADD COLUMN IF NOT EXISTS 冪等。

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refunded_amount DECIMAL(12, 2) NOT NULL DEFAULT 0;
