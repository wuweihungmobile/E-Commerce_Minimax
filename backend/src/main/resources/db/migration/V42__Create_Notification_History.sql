-- Sprint 20 US-005: M09 通知歷史記錄
-- 與 notifications 表（發送追蹤）分離，專注於用戶端已讀/未讀歷史
-- notifications 表保留 MQ 發送狀態追蹤（retry_count, error_message 等）

CREATE TABLE notification_history (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    tenant_id     UUID,
    notification_type VARCHAR(50)  NOT NULL,
    channel       VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    title         VARCHAR(500) NOT NULL,
    body          TEXT,
    is_read       BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at       TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_notification_history PRIMARY KEY (id)
);

CREATE INDEX idx_notif_history_user_id ON notification_history (user_id);
CREATE INDEX idx_notif_history_user_unread ON notification_history (user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notif_history_created_at ON notification_history (user_id, created_at DESC);
