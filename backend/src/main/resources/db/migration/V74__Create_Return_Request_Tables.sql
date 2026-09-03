-- V74__Create_Return_Request_Tables.sql
-- Sprint 118 / DEF-044：退貨申請與退貨入庫確認。
--
-- 背景：已付款訂單退款後，先前正式扣除的庫存不會回補，那批貨在系統裡就永遠消失了。
-- 使用者拍板（2026-09-03）採「做退貨確認流程」：**店家實際收到貨、確認可售後才回補庫存**
-- ——退錢與收貨是兩件事，退款當下就把庫存加回可售池，等於在賣還沒拿回來的東西。
--
-- 三項業務決策（同日拍板）：
--   1. 範圍含買家申請與審核（非只有店家端登記）
--   2. 與退款流程**完全獨立**：退款管錢、退貨管貨，互不強制
--      （可以只退錢不收貨——單價低於運費時；也可以只收貨不退錢——換貨）
--   3. 收到但不可售的商品：記錄但不回補
--
-- ⚠️ PRD 對退貨沒有任何規格（§13.2 反而把「逆向物流追蹤」列為 Phase 1 排除範圍）。
-- 但那張排除清單已經過時——同一張清單的第 1 項「不支援退款」與第 4 項「模擬支付」
-- 都早已實作。本次實作後 PRD 需一併更新。

CREATE TABLE return_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    return_number VARCHAR(50) NOT NULL UNIQUE,
    order_id UUID NOT NULL REFERENCES orders(id),
    customer_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    received_by UUID REFERENCES users(id),
    received_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT return_requests_status_check CHECK (status IN (
        'REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED', 'CANCELLED'
    ))
);

CREATE INDEX idx_return_requests_tenant ON return_requests(tenant_id);
CREATE INDEX idx_return_requests_order ON return_requests(order_id);
CREATE INDEX idx_return_requests_customer ON return_requests(customer_id);
CREATE INDEX idx_return_requests_status ON return_requests(status);

CREATE TABLE return_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_request_id UUID NOT NULL REFERENCES return_requests(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_items(id),
    sku_id UUID NOT NULL REFERENCES product_skus(id),
    requested_qty INTEGER NOT NULL,
    -- 收貨確認時才填：可售數量回補庫存（RETURN），不可售數量另寫一筆 SCRAP 抵銷
    sellable_qty INTEGER,
    unsellable_qty INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT return_request_items_requested_qty_positive CHECK (requested_qty > 0)
);

CREATE INDEX idx_return_request_items_request ON return_request_items(return_request_id);
CREATE INDEX idx_return_request_items_order_item ON return_request_items(order_item_id);
