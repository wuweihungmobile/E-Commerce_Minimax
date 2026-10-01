-- V86__Notifications_Refund_Completed_Type.sql
-- Sprint 229（使用者 2026-10-01「請繼續完成任務」；PRD US-005「取消後即時收到退款狀態通知」、US-014 付款逾時通知）
--
-- 問題：notifications.notification_type 的 CHECK 約束（V52）沒有 REFUND_COMPLETED。通知範本的枚舉
-- （NotificationTemplate.NotificationType）與前端選項早就有「退款完成」，但真正寫入 notifications 的枚舉
-- （Notification.NotificationType）沒有，資料庫約束也沒有——送出 REFUND_COMPLETED 通知會先在 valueOf 丟例外，
-- 就算過了這關，INSERT 也會被約束擋下。Sprint 229 起自動退款完成會通知買家，需要這個類型。
--
-- 修法：重建約束，在 PAYMENT_FAILED 之後加入 REFUND_COMPLETED。既有資料列都在原清單內，約束放寬不會讓任何資料失效。

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_notification_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_notification_type_check CHECK (notification_type IN (
        'ORDER_CONFIRMED', 'ORDER_PAID', 'ORDER_SHIPPED', 'ORDER_DELIVERED', 'ORDER_COMPLETED', 'ORDER_CANCELLED',
        'BOOKING_CONFIRMED', 'BOOKING_REMINDER', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED', 'REFUND_COMPLETED',
        'REVIEW_REQUEST', 'NEW_MESSAGE', 'SYSTEM_ANNOUNCEMENT'));
