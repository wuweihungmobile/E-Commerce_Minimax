-- V64__Create_Transfers_Table.sql
-- NextKey E-Commerce Platform
-- Sprint 80 US-001 (AI-2416): 真實金流 Phase D-2 — 結算單審核通過後分潤 transfer
-- 用途: 記錄 SettlementStatement 審核通過（APPROVED）後，實際呼叫 Stripe Transfer.create 轉給賣家 Connect 帳戶的明細，
--       供對帳與失敗重試查詢。transfer_amount 為觸發當下 settlement_statement.net_settlement_amount 的快照。
-- 冪等: settlement_statement_id 唯一索引，同一張結算單僅一筆 transfer 記錄（重複觸發需查既有記錄，不可重複建立）。
-- 安全性: 新表，不影響既有資料；tenant_id 索引供租戶範圍化查詢（比照 DEF-038 教訓，查詢層須強制租戶隔離）。

CREATE TABLE IF NOT EXISTS transfers (
    id UUID PRIMARY KEY,
    settlement_statement_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    stripe_transfer_id VARCHAR(255),
    transfer_amount DECIMAL(14, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transfers_settlement_statement_id ON transfers (settlement_statement_id);
CREATE INDEX IF NOT EXISTS idx_transfers_tenant_id ON transfers (tenant_id);
