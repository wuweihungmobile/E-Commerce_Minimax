-- Sprint 19 US-005: 通知用戶偏好設定
-- 允許用戶控制各通知類型 × 頻道組合的啟用狀態
-- 無明確設定時視為 enabled = true（向後相容）

CREATE TABLE user_notification_preferences (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    notification_type VARCHAR(50)  NOT NULL,
    channel       VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_notification_preferences PRIMARY KEY (id),
    CONSTRAINT uq_user_notification_pref UNIQUE (user_id, notification_type, channel)
);

CREATE INDEX idx_user_notification_pref_user_id ON user_notification_preferences (user_id);
