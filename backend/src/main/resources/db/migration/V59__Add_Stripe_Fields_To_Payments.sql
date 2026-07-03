-- V59__Add_Stripe_Fields_To_Payments.sql
-- NextKey E-Commerce Platform
-- Sprint 50 US-001 (AI-2410): 真實金流 Phase A — 卡片付款 MVP（Stripe Checkout hosted，平台代收）
-- 用途: 為 payments 新增 Stripe 專屬欄位，讓真實金流可記錄 Stripe 端識別碼：
--         - stripe_session_id        : Checkout Session id（cs_xxx，hosted Checkout 用）
--         - stripe_payment_intent_id : PaymentIntent id（pi_xxx，Session 完成後產生）
--         - stripe_charge_id         : Charge id（ch_xxx，退款/對帳用，Phase C 起）
--       原 transaction_id（VARCHAR 255）保留（mock 路徑續用 MOCK-xxxx；stripe 路徑存 session/pi id）。
-- 安全性 / 向後相容: 三欄皆 nullable、無 DEFAULT → 既有列自動 NULL，mock 付款不受影響。
--       使用 ADD COLUMN IF NOT EXISTS 保證冪等（空表與有資料皆適用）。
-- 慣例: payments 表自 V1 後首次改動（V1 之後 49 個 migration 未動 payments）；
--       PaymentMethod enum 加 STRIPE、PaymentStatus 加 PROCESSING 為 Java-only（欄位為 VARCHAR
--       + @Enumerated(STRING)，無 CHECK 約束，無需 migration）。ddl-auto=validate 對齊。
--       付款狀態權威更新於 Phase A 以「回跳 Session.retrieve」為來源；robust webhook 事件驅動留 Phase B（AI-2411）。

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS stripe_session_id VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(255);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS stripe_charge_id VARCHAR(255);
