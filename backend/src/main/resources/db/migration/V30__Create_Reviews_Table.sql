-- V30__Create_Reviews_Table.sql
-- 評價系統 - 建立商品評價表

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings(id),
    user_id UUID NOT NULL REFERENCES users(id),
    order_id UUID,
    booking_id UUID,
    review_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT',
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200),
    content TEXT,
    images JSONB DEFAULT '[]'::jsonb,
    helpful_votes JSONB DEFAULT '{}'::jsonb,
    helpful_count INTEGER DEFAULT 0,
    is_visible BOOLEAN DEFAULT true,
    is_anonymous BOOLEAN DEFAULT false,
    seller_reply TEXT,
    seller_replied_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reviews_unique_order UNIQUE (order_id, listing_id),
    CONSTRAINT chk_reviews_unique_booking UNIQUE (booking_id, listing_id)
);

-- 索引
CREATE INDEX idx_reviews_listing_id ON reviews(listing_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
CREATE INDEX idx_reviews_order_id ON reviews(order_id);
CREATE INDEX idx_reviews_booking_id ON reviews(booking_id);
CREATE INDEX idx_reviews_tenant_id ON reviews(tenant_id);
CREATE INDEX idx_reviews_rating ON reviews(rating);
CREATE INDEX idx_reviews_is_visible ON reviews(is_visible) WHERE is_visible = true;
CREATE INDEX idx_reviews_created_at ON reviews(created_at DESC);

-- 註解
COMMENT ON TABLE reviews IS '商品評價表 - 用於記錄用戶對商品的評價';
COMMENT ON COLUMN reviews.review_type IS '評價類型：PRODUCT(商品評價), ROOM(房源評價)';
COMMENT ON COLUMN reviews.helpful_votes IS '有用投票 Map（userId -> vote count）';