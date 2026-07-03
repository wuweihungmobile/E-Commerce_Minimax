-- V62__Add_Stripe_Connect_Fields_To_Tenants.sql
-- NextKey E-Commerce Platform
-- Sprint 53 US-001 (AI-2413 Phase D-1): 真實金流 Phase D-1 — Stripe Connect Express 帳戶 onboarding
-- 用途: tenants 加 Stripe Connect 帳戶欄位（帳戶 id + onboarding 狀態 + charges/payouts 啟用旗標）。
-- 安全性 / 向後相容: 皆 nullable 或有 DEFAULT → 既有列不受影響；預設 NOT_STARTED/false，代收後分潤（Phase D-2）另評估。
-- 慣例: 續 V59-V61（Stripe 付款/退款 schema 演進）；ADD COLUMN IF NOT EXISTS 冪等。

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS stripe_connect_account_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS connect_onboarding_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS connect_charges_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS connect_payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE;
