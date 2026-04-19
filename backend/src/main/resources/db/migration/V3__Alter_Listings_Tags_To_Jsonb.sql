-- V3__Alter_Listings_Tags_To_Jsonb.sql
-- NextKey E-Commerce Platform - Verify tags column is JSONB
-- Created: 2026-04-17
-- Description: Migration file to verify tags column is already JSONB (fixed in V1)

-- =============================================
-- Verify tags column is already JSONB (fixed in V1)
-- =============================================
DO $$
BEGIN
    -- Check that the column type is jsonb
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'listings'
        AND column_name = 'tags'
        AND data_type = 'jsonb'
    ) THEN
        -- If not jsonb, try to convert it
        EXECUTE 'ALTER TABLE listings ALTER COLUMN tags SET DATA TYPE jsonb USING tags::jsonb';
    END IF;

    RAISE NOTICE 'Migration V3 verified: tags column is JSONB';
END $$;