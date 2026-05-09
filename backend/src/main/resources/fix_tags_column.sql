-- Fix listings.tags column type: text -> jsonb
-- This runs BEFORE Hibernate validates the schema
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'listings'
        AND column_name = 'tags'
        AND data_type = 'text'
    ) THEN
        ALTER TABLE listings ALTER COLUMN tags TYPE jsonb USING tags::text::jsonb;
    END IF;
END $$;