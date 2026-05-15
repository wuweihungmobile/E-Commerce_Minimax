-- V29__Add_Tenant_Id_To_Knowledge_Categories_Faq_Categories_Faq_Articles.sql
-- 修復多租戶隔離 - 為 Knowledge Categories, Faq Categories, Faq Articles 加入 tenant_id

-- 1. 為 knowledge_categories 加入 tenant_id
ALTER TABLE knowledge_categories ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE knowledge_categories ADD CONSTRAINT fk_knowledge_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
CREATE INDEX idx_knowledge_categories_tenant_id ON knowledge_categories(tenant_id);

-- 2. 為 faq_categories 加入 tenant_id
ALTER TABLE faq_categories ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE faq_categories ADD CONSTRAINT fk_faq_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
CREATE INDEX idx_faq_categories_tenant_id ON faq_categories(tenant_id);

-- 3. 為 faq_articles 加入 tenant_id (從 category 繼承)
ALTER TABLE faq_articles ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE faq_articles ADD CONSTRAINT fk_faq_articles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
CREATE INDEX idx_faq_articles_tenant_id ON faq_articles(tenant_id);

-- 4. 更新 unique constraint 加入 tenant_id
-- 先移除舊的 unique constraint
ALTER TABLE knowledge_categories DROP CONSTRAINT IF EXISTS uk_knowledge_categories_slug;
-- 新增以 tenant_id 為主的 unique constraint
ALTER TABLE knowledge_categories ADD CONSTRAINT uk_knowledge_categories_tenant_slug UNIQUE (tenant_id, slug);

ALTER TABLE faq_categories DROP CONSTRAINT IF EXISTS uk_faq_categories_slug;
ALTER TABLE faq_categories ADD CONSTRAINT uk_faq_categories_tenant_slug UNIQUE (tenant_id, slug);

ALTER TABLE faq_articles DROP CONSTRAINT IF EXISTS uk_faq_articles_slug;
ALTER TABLE faq_articles ADD CONSTRAINT uk_faq_articles_tenant_slug UNIQUE (tenant_id, slug);

COMMENT ON COLUMN knowledge_categories.tenant_id IS '租戶 ID - 多租戶隔離';
COMMENT ON COLUMN faq_categories.tenant_id IS '租戶 ID - 多租戶隔離';
COMMENT ON COLUMN faq_articles.tenant_id IS '租戶 ID - 多租戶隔離';