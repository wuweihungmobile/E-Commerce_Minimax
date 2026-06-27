-- V49__Create_Cms_Banner_And_Page_Tables.sql
-- NextKey E-Commerce Platform
-- 用途: 建立 M15 CMS 的 cms_banners 與 cms_pages 表
-- 問題: Banner / ContentPage entity 存在，但沒有對應的建表 migration，
--       導致 ddl-auto=validate（E2E/production profile）時 Hibernate schema 驗證失敗：
--       "Schema-validation: missing table [cms_banners]"
-- 型別來源: 由 Hibernate ddl-auto=create 對 PostgreSQL 產生的 DDL 萃取，確保與 validate 一致
--   Instant -> timestamp(6) with time zone；enum(EnumType.STRING) -> varchar(255) + CHECK；
--   @JdbcTypeCode(JSON) -> jsonb；columnDefinition="TEXT" -> text

CREATE TABLE cms_banners (
    id uuid NOT NULL,
    title character varying(255) NOT NULL,
    image_url character varying(255) NOT NULL,
    link_url character varying(255),
    link_type character varying(255),
    description text,
    button_text character varying(255),
    metadata jsonb,
    start_date date,
    end_date date,
    banner_type character varying(255) NOT NULL,
    "position" character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    tenant_id uuid,
    target_audience character varying(255),
    impression_count integer,
    click_count integer,
    sort_order integer,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT cms_banners_pkey PRIMARY KEY (id),
    CONSTRAINT cms_banners_banner_type_check CHECK (((banner_type)::text = ANY ((ARRAY['HERO'::character varying, 'PROMOTION'::character varying, 'ANNOUNCEMENT'::character varying, 'EMBEDDED_CARD'::character varying])::text[]))),
    CONSTRAINT cms_banners_link_type_check CHECK (((link_type)::text = ANY ((ARRAY['URL'::character varying, 'LISTING'::character varying, 'PAGE'::character varying, 'CATEGORY'::character varying])::text[]))),
    CONSTRAINT cms_banners_position_check CHECK ((("position")::text = ANY ((ARRAY['HOME_TOP'::character varying, 'HOME_MIDDLE'::character varying, 'HOME_BOTTOM'::character varying, 'LISTING_PAGE'::character varying, 'PRODUCT_PAGE'::character varying, 'CHECKOUT_PAGE'::character varying, 'SIDEBAR'::character varying])::text[]))),
    CONSTRAINT cms_banners_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);

CREATE TABLE cms_pages (
    id uuid NOT NULL,
    title character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    content text,
    metadata jsonb,
    page_type character varying(255) NOT NULL,
    template character varying(255),
    featured_image_url character varying(255),
    sections jsonb,
    status character varying(255) NOT NULL,
    published_at timestamp(6) with time zone,
    author_id uuid,
    tenant_id uuid,
    is_indexable boolean,
    sort_order integer,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    CONSTRAINT cms_pages_pkey PRIMARY KEY (id),
    CONSTRAINT cms_pages_slug_key UNIQUE (slug),
    CONSTRAINT cms_pages_page_type_check CHECK (((page_type)::text = ANY ((ARRAY['LANDING_PAGE'::character varying, 'ABOUT_US'::character varying, 'CONTACT_US'::character varying, 'FAQ'::character varying, 'TERMS'::character varying, 'PRIVACY'::character varying, 'CUSTOM'::character varying])::text[]))),
    CONSTRAINT cms_pages_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);
