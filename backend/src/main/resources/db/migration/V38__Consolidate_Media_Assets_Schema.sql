-- V38__Consolidate_Media_Assets_Schema.sql
-- 統一 media_assets 表結構，修復 V13 和 V22 的衝突
-- 結合 V13 (uploader_id, original_name, is_active) 和 V22 (category_id, tags, usage_count, alt_text, title, is_deleted) 的欄位
-- Sprint 17 US-004: Flyway 正式啟用評估

-- =============================================
-- Phase 1: 處理現有資料（如果表存在且有資料）
-- =============================================

-- 檢查是否需要遷移資料（只有當有舊 schema 資料時才執行）
DO $$
DECLARE
    has_v13_data BOOLEAN := FALSE;
    has_v22_data BOOLEAN := FALSE;
    table_exists BOOLEAN := FALSE;
BEGIN
    -- 檢查表是否存在
    SELECT EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_name = 'media_assets'
    ) INTO table_exists;

    IF table_exists THEN
        -- 檢查是 V13 還是 V22 schema
        -- V13 有 original_name 欄位
        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'media_assets' AND column_name = 'original_name'
        ) INTO has_v13_data;

        -- V22 有 category_id 欄位
        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'media_assets' AND column_name = 'category_id'
        ) INTO has_v22_data;

        RAISE NOTICE 'Current schema - V13 (original_name): %, V22 (category_id): %', has_v13_data, has_v22_data;

        -- 如果是 V13 schema，需要遷移到完整 schema
        IF has_v13_data AND NOT has_v22_data THEN
            RAISE NOTICE 'Detected V13 schema, migrating to consolidated schema...';

            -- 備份現有資料
            CREATE TABLE IF NOT EXISTS _media_assets_backup AS
            SELECT * FROM media_assets;

            -- 重建表為完整 schema
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS category_id UUID;
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS tags TEXT[];
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS usage_count INTEGER DEFAULT 0;
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS alt_text VARCHAR(255);
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS title VARCHAR(255);
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;

            RAISE NOTICE 'V13 schema migrated successfully';
        END IF;

        -- 如果是 V22 schema，確保有 V13 的欄位
        IF has_v22_data AND NOT has_v13_data THEN
            RAISE NOTICE 'Detected V22 schema, adding V13 columns...';

            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS uploader_id UUID;
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS original_name VARCHAR(255);
            ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

            RAISE NOTICE 'V22 schema enhanced with V13 columns';
        END IF;
    ELSE
        RAISE NOTICE 'media_assets table does not exist, will be created by Flyway';
    END IF;
END $$;

-- =============================================
-- Phase 2: 建立完整的 schema（如果表是空的或不存在）
-- =============================================

-- 確保所有必要欄位都存在（等冪性操作）
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS uploader_id UUID;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS original_name VARCHAR(255);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS file_path VARCHAR(1000);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS file_type VARCHAR(20);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS tags TEXT[] DEFAULT '{}';
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS usage_count INTEGER DEFAULT 0;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS alt_text VARCHAR(255);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS width INTEGER;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS height INTEGER;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- =============================================
-- Phase 3: 建立索引（等冪性操作）
-- =============================================

-- 主索引（如果不存在）
CREATE INDEX IF NOT EXISTS idx_media_assets_tenant_id ON media_assets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_media_assets_uploader_id ON media_assets(uploader_id);
CREATE INDEX IF NOT EXISTS idx_media_assets_category_id ON media_assets(category_id);
CREATE INDEX IF NOT EXISTS idx_media_assets_file_type ON media_assets(file_type);
CREATE INDEX IF NOT EXISTS idx_media_assets_mime_type ON media_assets(mime_type);
CREATE INDEX IF NOT EXISTS idx_media_assets_created_at ON media_assets(created_at DESC);

-- 軟刪除查詢優化
CREATE INDEX IF NOT EXISTS idx_media_assets_not_deleted ON media_assets(id) WHERE is_deleted = FALSE;

COMMENT ON TABLE media_assets IS '統一後的媒體資產表 - 結合 V13 和 V22 schema';
COMMENT ON COLUMN media_assets.uploader_id IS '上傳者 ID (來自 V13)';
COMMENT ON COLUMN media_assets.original_name IS '原始檔案名稱 (來自 V13)';
COMMENT ON COLUMN media_assets.category_id IS '分類 ID (來自 V22)';
COMMENT ON COLUMN media_assets.tags IS '標籤陣列 (來自 V22)';
COMMENT ON COLUMN media_assets.usage_count IS '被引用次數 (來自 V22)';
COMMENT ON COLUMN media_assets.alt_text IS '替代文字 (來自 V22)';
COMMENT ON COLUMN media_assets.title IS '標題 (來自 V22)';
COMMENT ON COLUMN media_assets.is_active IS '是否啟用 (來自 V13)';
COMMENT ON COLUMN media_assets.is_deleted IS '軟刪除標記 (來自 V22)';