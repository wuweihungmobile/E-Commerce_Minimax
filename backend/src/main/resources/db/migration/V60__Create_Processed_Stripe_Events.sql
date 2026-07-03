-- V60__Create_Processed_Stripe_Events.sql
-- NextKey E-Commerce Platform
-- Sprint 51 US-002 (AI-2411): 真實金流 Phase B — webhook 事件去重
-- 用途: 記錄已處理的 Stripe webhook 事件 id，防止 Stripe 重送造成重複處理副作用。
--       Stripe 明確要求 webhook 消費者以 event id 去重（events 可能被投遞多次）。
-- 冪等雙層: (1) 狀態轉移冪等（已 PAID → no-op，S50 已有）(2) 事件 id 去重（本表）。
-- 安全性: 新表，不影響既有資料；event_id 為主鍵天然去重。

CREATE TABLE IF NOT EXISTS processed_stripe_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100),
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
