-- V54__Fix_Media_Assets_Tags_Column_Type.sql
-- NextKey E-Commerce Platform
-- 用途: 將 media_assets.tags 欄位從 PostgreSQL text[] 轉換為 JSONB
-- 問題: V38 建立 tags 為 TEXT[]，但 MediaAsset entity 的 List<String> tags 原本
--       未標註 @JdbcTypeCode，導致 Hibernate 對「無註解的 List<String>」之映射依
--       執行環境而異：
--         - 本機 macOS：映射為 array（_varchar），validate 對 text[] 通過
--         - GitHub Linux runner：映射為 jsonb，validate 失敗、Backend 無法啟動、E2E Tests 失敗：
--           "wrong column type encountered in column [tags] in table [media_assets];
--            found [_text (Types#ARRAY)], but expecting [jsonb (Types#JSON)]"
--       本次已於 MediaAsset entity 補上 @JdbcTypeCode(SqlTypes.JSON) + columnDefinition = "jsonb"，
--       使映射在所有環境一致為 jsonb，需同步將欄位轉為 jsonb。
-- 慣例: 程式碼庫 tags 欄位統一使用 jsonb（參考 ArticleVersion.tags / Post.tags /
--       V48__Fix_Article_Versions_Tags_Column_Type.sql / V8__Fix_Listings_Tags_Column_Type.sql）
-- 安全性: 使用 information_schema 檢查欄位型別，空表與有資料皆適用（冪等）

DO $$
DECLARE
    col_type TEXT;
BEGIN
    -- 以 information_schema 檢查欄位型別（不依賴資料列，空表也適用）
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_name = 'media_assets'
      AND column_name = 'tags';

    -- 只有當欄位是 ARRAY (text[]) 時才轉換；若已是 jsonb 則跳過
    IF col_type = 'ARRAY' THEN
        -- to_jsonb 將 PostgreSQL 陣列 {a,b,c} 直接轉為 JSON 陣列 ["a","b","c"]
        ALTER TABLE media_assets
            ALTER COLUMN tags TYPE jsonb USING to_jsonb(tags);
        RAISE NOTICE 'media_assets.tags converted from text[] to jsonb.';
    ELSE
        RAISE NOTICE 'media_assets.tags is already % (not ARRAY). Skipping conversion.', COALESCE(col_type, 'unknown');
    END IF;
END $$;

-- 設定預設值，與其他 tags 欄位一致
ALTER TABLE media_assets
    ALTER COLUMN tags SET DEFAULT '[]'::jsonb;
