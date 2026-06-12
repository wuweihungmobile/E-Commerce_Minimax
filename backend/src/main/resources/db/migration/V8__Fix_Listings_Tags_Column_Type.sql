-- V8__Fix_Listings_Tags_Column_Type.sql
-- NextKey E-Commerce Platform - Sprint 7 QA 修復
-- Created: 2026-05-03
-- 用途: 將 listings.tags 欄位從 PostgreSQL text[] 格式轉換為 JSONB
-- 問題: Hibernate 從 text[] 欄位還原 tags 時失敗
-- 原因: PostgreSQL text[] 格式為 {e2e,test,room}，非 JSON array 格式
-- 參考: Sprint 7 QA 驗證發現的 Hibernate 警告
-- 相容性: V1 定義 tags 為 JSONB，此 migration 只處理 text[] 類型

-- =============================================
-- 0. 類型檢查：只在 tags 是 text[] 時才執行轉換
-- 如果已是 JSONB，直接跳過
-- =============================================
DO $$
DECLARE
    current_type TEXT;
BEGIN
    -- 檢查當前 tags 欄位的 PostgreSQL 類型
    SELECT pg_typeof(tags)::text INTO current_type
    FROM listings LIMIT 1;

    -- 如果不是 text[] 或記錄為空，表示不需要轉換
    IF current_type IS DISTINCT FROM 'text[]' THEN
        RAISE NOTICE 'tags column is already % (not text[]). Skipping conversion.', COALESCE(current_type, 'unknown');
        RETURN;
    END IF;
END $$;

-- =============================================
-- 1. 備份現有資料（可選，用於除錯）
-- =============================================
CREATE TABLE IF NOT EXISTS listings_tags_backup AS
SELECT id, tenant_id, tags FROM listings WHERE tags IS NOT NULL;

-- =============================================
-- 2. 將 PostgreSQL text[] 格式轉換為 JSON array 字串
-- PostgreSQL text[] 格式: {e2e,test,room}
-- JSON array 格式: ["e2e","test","room"]
-- =============================================

-- 先將 {e2e,test,room} 轉換為 ["e2e","test","room"]
-- 演算法：移除 {}，用 "," 分隔，加上 [ 和 ]
UPDATE listings
SET tags = ('[' || substring(tags::text from 2 for length(tags::text) - 2) || ']')::jsonb
WHERE tags IS NOT NULL
  AND pg_typeof(tags)::text = 'text[]'
  AND left(tags::text, 1) = '{'
  AND right(tags::text, 1) = '}';

-- =============================================
-- 3. 修改欄位類型為 JSONB
-- =============================================
ALTER TABLE listings
ALTER COLUMN tags TYPE jsonb USING tags::jsonb;

-- =============================================
-- 4. 設定預設值（未來新記錄）
-- =============================================
ALTER TABLE listings
ALTER COLUMN tags SET DEFAULT '[]'::jsonb;

-- =============================================
-- 5. 驗證轉換結果
-- =============================================
-- SELECT id, tags, pg_typeof(tags) FROM listings WHERE tags IS NOT NULL LIMIT 10;

-- =============================================
-- 清理備份表（確認轉換無誤後）
-- DROP TABLE IF EXISTS listings_tags_backup;
