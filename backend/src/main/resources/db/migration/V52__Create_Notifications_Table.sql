-- V52__Create_Notifications_Table.sql
-- NextKey E-Commerce Platform
-- 用途: 建立 notifications 表
-- 問題: Notification entity 存在，但沒有對應的建表 migration，
--       導致 ddl-auto=validate 時 Hibernate schema 驗證失敗（missing table）。
-- 型別來源: 由 Hibernate ddl-auto=create 對 PostgreSQL 產生的 DDL 萃取，確保與 validate 一致。
--   Instant -> timestamp(6) with time zone；enum -> varchar(255) + CHECK；@JdbcTypeCode(JSON) -> jsonb

CREATE TABLE notifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    notification_type character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    content text,
    data jsonb,
    channel character varying(255) NOT NULL,
    recipient character varying(255),
    is_sent boolean,
    is_read boolean,
    sent_at timestamp(6) with time zone,
    read_at timestamp(6) with time zone,
    error_message character varying(255),
    retry_count integer,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_channel_check CHECK (((channel)::text = ANY ((ARRAY['IN_APP'::character varying, 'EMAIL'::character varying, 'SMS'::character varying, 'PUSH'::character varying])::text[]))),
    CONSTRAINT notifications_notification_type_check CHECK (((notification_type)::text = ANY ((ARRAY['ORDER_CONFIRMED'::character varying, 'ORDER_PAID'::character varying, 'ORDER_SHIPPED'::character varying, 'ORDER_DELIVERED'::character varying, 'ORDER_COMPLETED'::character varying, 'ORDER_CANCELLED'::character varying, 'BOOKING_CONFIRMED'::character varying, 'BOOKING_REMINDER'::character varying, 'PAYMENT_SUCCESS'::character varying, 'PAYMENT_FAILED'::character varying, 'REVIEW_REQUEST'::character varying, 'NEW_MESSAGE'::character varying, 'SYSTEM_ANNOUNCEMENT'::character varying])::text[])))
);
