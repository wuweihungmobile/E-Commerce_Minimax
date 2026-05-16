-- V31__Create_Booking_Reviews_Table.sql
-- 評價系統 - 建立預訂評價表（民宿/房間預訂）

CREATE TABLE booking_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    user_id UUID NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200),
    content TEXT,
    images JSONB DEFAULT '[]'::jsonb,
    is_visible BOOLEAN DEFAULT true,
    is_anonymous BOOLEAN DEFAULT false,
    host_reply TEXT,
    host_replied_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_booking_reviews_unique UNIQUE (booking_id, user_id)
);

-- 索引
CREATE INDEX idx_booking_reviews_booking_id ON booking_reviews(booking_id);
CREATE INDEX idx_booking_reviews_user_id ON booking_reviews(user_id);
CREATE INDEX idx_booking_reviews_rating ON booking_reviews(rating);
CREATE INDEX idx_booking_reviews_is_visible ON booking_reviews(is_visible) WHERE is_visible = true;
CREATE INDEX idx_booking_reviews_created_at ON booking_reviews(created_at DESC);

-- 註解
COMMENT ON TABLE booking_reviews IS '預訂評價表 - 用於記錄用戶對民宿/房間預訂的評價';
COMMENT ON COLUMN booking_reviews.host_reply IS '房東回覆內容';
COMMENT ON COLUMN booking_reviews.host_replied_at IS '房東回覆時間';