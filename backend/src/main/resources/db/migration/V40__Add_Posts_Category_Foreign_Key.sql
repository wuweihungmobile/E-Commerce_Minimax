-- V40__Add_Posts_Category_Foreign_Key.sql
-- M15 CMS: 為 posts.category_id 添加外鍵約束
-- Created: 2026-06-13
-- 用途: 建立 posts.category_id 對 post_categories(id) 的外鍵約束
-- 前置需求: V10 (posts 表), V11 (post_categories 表)

-- =============================================
-- 為 posts.category_id 添加外鍵約束
-- =============================================
ALTER TABLE posts
ADD CONSTRAINT fk_posts_category_id
FOREIGN KEY (category_id) REFERENCES post_categories(id)
ON DELETE SET NULL ON UPDATE CASCADE;

COMMENT ON CONSTRAINT fk_posts_category_id ON posts IS 'Foreign key constraint for posts.category_id -> post_categories.id';
