-- V12__create_post_embeds_table.sql
-- M15 CMS: 嵌入卡片資料表
-- Created: 2026-05-03
-- 用途: 建立 post_embeds 表，支援貼文中嵌入商品/房型卡片
-- 前置需求: V10 (posts 表), V1 (listings 表)

-- =============================================
-- 1. Post Embeds 表結構
-- =============================================
CREATE TABLE post_embeds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listings(id),
    listing_type VARCHAR(20),
    embed_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT post_embeds_post_listing_unique UNIQUE (post_id, listing_id)
);

-- 建立索引
CREATE INDEX idx_post_embeds_post_id ON post_embeds(post_id);
CREATE INDEX idx_post_embeds_listing_id ON post_embeds(listing_id);
CREATE INDEX idx_post_embeds_embed_order ON post_embeds(embed_order);

COMMENT ON TABLE post_embeds IS 'Embedded listing cards in posts';
COMMENT ON COLUMN post_embeds.listing_id IS 'Reference to listings.id (PRODUCT or ROOM)';