-- V18__Make_Purchase_Order_Item_Fields_Nullable.sql
-- 將 purchase_order_items 表的某些欄位改為 nullable，以便建立採購單時不需要這些欄位

-- 將 product_name 改為 nullable（可以從 listing 取得）
ALTER TABLE purchase_order_items ALTER COLUMN product_name DROP NOT NULL;

-- 將 sku_code 改為 nullable（可以從 product_sku 取得）
ALTER TABLE purchase_order_items ALTER COLUMN sku_code DROP NOT NULL;