-- V55__Fix_Rooms_Amenities_Column_Type.sql
-- NextKey E-Commerce Platform
-- 用途: 將 rooms.amenities 欄位從 PostgreSQL text[] 轉換為 JSONB
-- 問題: V1 建立 amenities 為 TEXT[]，Room entity 原本標註 @JdbcTypeCode(SqlTypes.ARRAY)
--       + columnDefinition = "TEXT[]"。此 ARRAY 映射在 Hibernate 6.4 跨環境不一致：
--         - 本機 macOS：validate 期望 ARRAY，對 text[] 通過
--         - GitHub Linux runner：validate 期望 JSON，對 text[] 失敗、Backend 無法啟動、E2E Tests 失敗：
--           "wrong column type encountered in column [amenities] in table [rooms];
--            found [_text (Types#ARRAY)], but expecting [text[] (Types#JSON)]"
--       本次已將 Room entity 改為 @JdbcTypeCode(SqlTypes.JSON) + columnDefinition = "jsonb"，
--       使映射在所有環境一致為 jsonb（jsonb 映射跨環境穩定），需同步將欄位轉為 jsonb。
-- 慣例: rooms.amenities 是全庫最後一個 @JdbcTypeCode(SqlTypes.ARRAY) 欄位；轉換後所有集合
--       欄位統一使用 jsonb（參考 Listing.tags / MediaAsset.tags / ArticleVersion.tags /
--       V8 / V48 / V54 等既有 jsonb 轉換）。
-- 安全性: amenities 無 native array 查詢（無 @> / ANY），轉換安全；使用 information_schema
--         檢查欄位型別，空表與有資料皆適用（冪等）。

DO $$
DECLARE
    col_type TEXT;
BEGIN
    -- 以 information_schema 檢查欄位型別（不依賴資料列，空表也適用）
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_name = 'rooms'
      AND column_name = 'amenities';

    -- 只有當欄位是 ARRAY (text[]) 時才轉換；若已是 jsonb 則跳過
    IF col_type = 'ARRAY' THEN
        -- to_jsonb 將 PostgreSQL 陣列 {a,b,c} 直接轉為 JSON 陣列 ["a","b","c"]
        ALTER TABLE rooms
            ALTER COLUMN amenities DROP DEFAULT;
        ALTER TABLE rooms
            ALTER COLUMN amenities TYPE jsonb USING to_jsonb(amenities);
        RAISE NOTICE 'rooms.amenities converted from text[] to jsonb.';
    ELSE
        RAISE NOTICE 'rooms.amenities is already % (not ARRAY). Skipping conversion.', COALESCE(col_type, 'unknown');
    END IF;
END $$;

-- 設定預設值，與其他集合欄位一致
ALTER TABLE rooms
    ALTER COLUMN amenities SET DEFAULT '[]'::jsonb;
