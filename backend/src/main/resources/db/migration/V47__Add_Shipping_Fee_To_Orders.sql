-- DEF-008: 訂單新增 shipping_fee 欄位，支援 ShippingTemplate 接入結帳
-- Sprint 23 US-005
ALTER TABLE orders
    ADD COLUMN shipping_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00;
