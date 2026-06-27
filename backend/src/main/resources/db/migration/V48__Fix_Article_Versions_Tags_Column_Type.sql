-- V48__Fix_Article_Versions_Tags_Column_Type.sql
-- NextKey E-Commerce Platform
-- 用途: 將 article_versions.tags 欄位從 PostgreSQL text[] 轉換為 JSONB
-- 問題: V32 建立 tags 為 TEXT[]，但 ArticleVersion entity 以
--       @JdbcTypeCode(SqlTypes.JSON) + columnDefinition = "jsonb" 映射，
--       導致 Hibernate schema-validation 失敗、Backend 無法啟動、E2E Tests 失敗：
--       "wrong column type encountered in column [tags] in table [article_versions];
--        found [_text (Types#ARRAY)], but expecting [jsonb (Types#JSON)]"
-- 慣例: 程式碼庫 tags 欄位統一使用 jsonb（參考 V8__Fix_Listings_Tags_Column_Type.sql）
-- 安全性: 使用 information_schema 檢查欄位型別，空表與有資料皆適用（冪等）

DO $$
DECLARE
    col_type TEXT;
BEGIN
    -- 以 information_schema 檢查欄位型別（不依賴資料列，空表也適用）
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_name = 'article_versions'
      AND column_name = 'tags';

    -- 只有當欄位是 ARRAY (text[]) 時才轉換；若已是 jsonb 則跳過
    IF col_type = 'ARRAY' THEN
        -- to_jsonb 將 PostgreSQL 陣列 {a,b,c} 直接轉為 JSON 陣列 ["a","b","c"]
        ALTER TABLE article_versions
            ALTER COLUMN tags TYPE jsonb USING to_jsonb(tags);
        RAISE NOTICE 'article_versions.tags converted from text[] to jsonb.';
    ELSE
        RAISE NOTICE 'article_versions.tags is already % (not ARRAY). Skipping conversion.', COALESCE(col_type, 'unknown');
    END IF;
END $$;

-- 設定預設值，與 listings.tags 一致
ALTER TABLE article_versions
    ALTER COLUMN tags SET DEFAULT '[]'::jsonb;
