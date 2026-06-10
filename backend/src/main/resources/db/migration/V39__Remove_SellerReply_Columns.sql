-- V39__Remove_SellerReply_Columns.sql
-- 移除 Review 表的 sellerReply 欄位
-- Sprint 17 US-005: 移除 /reply + sellerReply 清理
-- 歷史: sellerReply 已遷移到獨立的 review_replies 表（Sprint 16 US-001）

-- =============================================
-- 確認 reviews 表存在
-- =============================================
DO $$
DECLARE
    table_exists BOOLEAN := FALSE;
BEGIN
    SELECT EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_name = 'reviews'
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'reviews table does not exist, skipping sellerReply cleanup';
    ELSE
        -- 檢查 seller_reply 欄位是否存在
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'reviews' AND column_name = 'seller_reply'
        ) THEN
            RAISE NOTICE 'Removing seller_reply and seller_replied_at columns from reviews table';

            -- 移除 seller_reply 欄位（等冪性）
            ALTER TABLE reviews DROP COLUMN IF EXISTS seller_reply;
            ALTER TABLE reviews DROP COLUMN IF EXISTS seller_replied_at;

            RAISE NOTICE 'sellerReply columns removed successfully';
        ELSE
            RAISE NOTICE 'seller_reply column does not exist, nothing to remove';
        END IF;
    END IF;
END $$;

-- 註釋：sellerReply 欄位已於 Sprint 16 US-001 遷移到獨立的 review_replies 表
-- 此次移除是為了清理 legacy 欄位，避免資料不一致