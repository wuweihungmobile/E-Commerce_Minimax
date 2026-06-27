-- V51__Create_Logistics_Table.sql
-- NextKey E-Commerce Platform
-- 用途: 建立 logistics 表
-- 問題: Logistics entity 存在，但沒有對應的建表 migration，
--       導致 ddl-auto=validate 時 Hibernate schema 驗證失敗（missing table）。
-- 型別來源: 由 Hibernate ddl-auto=create 對 PostgreSQL 產生的 DDL 萃取，確保與 validate 一致。
--   LocalDateTime -> timestamp(6) without time zone；Instant -> timestamp(6) with time zone；
--   logistics_data（String + columnDefinition="jsonb"）-> jsonb

CREATE TABLE logistics (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    logistics_provider character varying(255) NOT NULL,
    tracking_number character varying(255),
    status character varying(255) NOT NULL,
    pickup_time timestamp(6) without time zone,
    delivery_time timestamp(6) without time zone,
    shipping_address text,
    receiver_name character varying(255),
    receiver_phone character varying(255),
    logistics_data jsonb,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT logistics_pkey PRIMARY KEY (id),
    CONSTRAINT logistics_logistics_provider_check CHECK (((logistics_provider)::text = ANY ((ARRAY['HCT'::character varying, 'TCAT'::character varying])::text[]))),
    CONSTRAINT logistics_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PICKED_UP'::character varying, 'IN_TRANSIT'::character varying, 'OUT_FOR_DELIVERY'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying, 'RETURNED'::character varying])::text[])))
);
