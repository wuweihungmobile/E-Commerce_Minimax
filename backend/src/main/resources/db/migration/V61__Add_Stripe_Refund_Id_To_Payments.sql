-- V61__Add_Stripe_Refund_Id_To_Payments.sql
-- NextKey E-Commerce Platform
-- Sprint 52 US-001 (AI-2412): 真實金流 Phase C — 退款真串接
-- 用途: payments 加 stripe_refund_id（re_xxx），記錄 Stripe 退款 id（供對帳/查詢）。
-- 安全性 / 向後相容: nullable、無 DEFAULT → 既有列 NULL，mock 退款不受影響。
-- 慣例: 續 V59（Stripe 欄位）/ V60（事件去重）的付款 schema 演進；ADD COLUMN IF NOT EXISTS 冪等。

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS stripe_refund_id VARCHAR(255);
