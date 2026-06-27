-- V53__Fix_Entity_Schema_Drift.sql
-- NextKey E-Commerce Platform
-- 用途: 修正既有表與 entity 之間的 schema 漂移（缺欄位 / 型別不符），
--       使 ddl-auto=validate（E2E/production profile）能通過。
-- 來源: 以 Hibernate ddl-auto=create 對 PostgreSQL 產生的期望 schema，
--       與 Flyway 建出的 schema 逐欄位比對，僅修正 validate 實際會檢查的差異：
--         (1) 缺漏欄位（table|column 完全不存在）
--         (2) JDBC 型別不同（numeric vs double precision）
--       不修改 timestamp↔timestamptz、varchar↔text，因 PostgreSQL dialect 下
--       Hibernate validate 不視為型別不符（已於本地 validate 實測確認），
--       且對既有資料做 timestamp→timestamptz 轉換會有時區位移風險。
-- 安全性: 全部使用 IF NOT EXISTS / 可重入語法，新增欄位皆為 nullable（不影響既有資料）。

-- 1) knowledge_article_tags: entity 改以 UUID id 為主鍵（V25 原為複合主鍵 article_id+tag）
ALTER TABLE knowledge_article_tags ADD COLUMN IF NOT EXISTS id uuid;
UPDATE knowledge_article_tags SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE knowledge_article_tags ALTER COLUMN id SET NOT NULL;
ALTER TABLE knowledge_article_tags DROP CONSTRAINT IF EXISTS pk_knowledge_article_tags;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'knowledge_article_tags' AND constraint_type = 'PRIMARY KEY'
    ) THEN
        ALTER TABLE knowledge_article_tags ADD CONSTRAINT pk_knowledge_article_tags PRIMARY KEY (id);
    END IF;
END $$;
-- 保留 (article_id, tag) 的唯一性語意
ALTER TABLE knowledge_article_tags DROP CONSTRAINT IF EXISTS uq_knowledge_article_tags_article_tag;
ALTER TABLE knowledge_article_tags ADD CONSTRAINT uq_knowledge_article_tags_article_tag UNIQUE (article_id, tag);

-- 2) knowledge_articles: 缺漏 sort_order / tags
ALTER TABLE knowledge_articles ADD COLUMN IF NOT EXISTS sort_order integer;
ALTER TABLE knowledge_articles ADD COLUMN IF NOT EXISTS tags text;

-- 3) purchase_order_items: 缺漏 product_name / sku_code / subtotal
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS product_name character varying(255);
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS sku_code character varying(255);
ALTER TABLE purchase_order_items ADD COLUMN IF NOT EXISTS subtotal numeric(12,2);

-- 4) purchase_orders: 缺漏 currency
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS currency character varying(255);

-- 5) stock_movements: 缺漏 before/after 數量與 balance_after
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS before_total_qty integer;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS after_total_qty integer;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS before_reserved_qty integer;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS after_reserved_qty integer;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS balance_after integer;

-- 6) tenant_feature_toggles: 缺漏 enabled_at / disabled_at
ALTER TABLE tenant_feature_toggles ADD COLUMN IF NOT EXISTS enabled_at timestamp with time zone;
ALTER TABLE tenant_feature_toggles ADD COLUMN IF NOT EXISTS disabled_at timestamp with time zone;

-- 7) tenants: commission_rate 型別 numeric -> double precision（entity 為 double）
ALTER TABLE tenants ALTER COLUMN commission_rate TYPE double precision;
