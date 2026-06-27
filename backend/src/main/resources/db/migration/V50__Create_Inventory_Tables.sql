-- V50__Create_Inventory_Tables.sql
-- NextKey E-Commerce Platform
-- 用途: 建立 inventory 與 inventory_checks 表
-- 問題: Inventory / InventoryCheck entity 存在，但沒有對應的建表 migration，
--       導致 ddl-auto=validate 時 Hibernate schema 驗證失敗（missing table）。
-- 型別來源: 由 Hibernate ddl-auto=create 對 PostgreSQL 產生的 DDL 萃取，確保與 validate 一致。
--   @Version Long -> bigint；Instant -> timestamp(6) with time zone；LocalDate -> date

CREATE TABLE inventory (
    id uuid NOT NULL,
    sku_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    total_qty integer NOT NULL,
    reserved_qty integer NOT NULL,
    available_qty integer NOT NULL,
    safety_stock integer,
    reorder_point integer,
    sku_code character varying(255),
    product_name character varying(255),
    location character varying(255),
    last_inbound_date timestamp(6) with time zone,
    last_outbound_date timestamp(6) with time zone,
    version bigint,
    updated_at timestamp(6) with time zone,
    CONSTRAINT inventory_pkey PRIMARY KEY (id)
);

CREATE TABLE inventory_checks (
    id uuid NOT NULL,
    check_number character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    scheduled_date date,
    completed_date date,
    checked_by uuid,
    notes character varying(255),
    variance_count integer,
    total_items_checked integer,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT inventory_checks_pkey PRIMARY KEY (id),
    CONSTRAINT inventory_checks_check_number_key UNIQUE (check_number),
    CONSTRAINT inventory_checks_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);
