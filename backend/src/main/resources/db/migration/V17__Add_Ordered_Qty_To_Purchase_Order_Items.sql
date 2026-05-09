-- V17__Add_Ordered_Qty_To_Purchase_Order_Items.sql
-- 新增 ordered_qty 欄位到 purchase_order_items 表
-- 此欄位用於追蹤原始訂購數量，區分已收貨數量

ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS ordered_qty INTEGER NOT NULL DEFAULT 0;

-- 將現有記錄的 ordered_qty 設為 quantity 的值（已訂購等於已收貨）
UPDATE purchase_order_items SET ordered_qty = quantity WHERE ordered_qty IS NULL OR ordered_qty = 0;

-- 確保 ordered_qty 不為 NULL
ALTER TABLE purchase_order_items ALTER COLUMN ordered_qty SET DEFAULT 0;
ALTER TABLE purchase_order_items ALTER COLUMN ordered_qty SET NOT NULL;