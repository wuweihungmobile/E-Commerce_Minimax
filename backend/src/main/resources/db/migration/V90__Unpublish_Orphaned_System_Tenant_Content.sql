-- V90__Unpublish_Orphaned_System_Tenant_Content.sql
-- Sprint 242（DEF-333；Sprint 240 登記的「系統租戶既有資料」，使用者要求依最佳狀態處理）
--
-- 問題：Sprint 240（DEF-326）起，沒有店鋪的 SELLER／HOST 簽發 token 時一律是 BUYER，不能再管理店家層資料。修復前他們在系統租戶
-- （平台自營，00000000-0000-0000-0000-000000000001；所有沒有店鋪的消費者共用的租戶脈絡）底下建立的商品、房源、貼文、CMS 頁面
-- 仍然對外公開，擁有者卻再也編輯、下架不了；買家在這些商品上下的單蓋成系統租戶，沒有任何店家能出貨或處理（Sprint 237 起訂單歸屬
-- 商品所屬的店鋪，系統租戶底下的商品沒有店家）。另一種同源的孤兒：SELLER 先在系統租戶建了資料、之後才開店升成 STORE_OWNER——
-- 他的租戶換成店鋪，系統租戶底下的舊資料同樣管不到（擁有權檢查比對租戶，不比對建立者）。
--
-- 不變量：系統租戶底下對外公開的內容，建立者必須是平台管理員（ADMIN、SUPER_ADMIN）——只有他們能繼續管理系統租戶的資料。
--
-- 處理：下架，不刪除。
--   * 商品／房源：ACTIVE → INACTIVE（店鋪停權時 AdminService.deactivateTenantListings 也是這個做法；平台管理員可再上架）。
--   * 貼文、CMS 頁面：PUBLISHED → DRAFT（Post.unpublish() 的語意；管理員可再發布）。
--   草稿、已下架、已刪除的不動；建立者是平台管理員的不動；店鋪租戶底下的不動；CMS 頁面沒有建立者（author_id 為 NULL，平台自建）的不動。
--   橫幅（cms_banners）沒有建立者欄位，無法判斷是不是孤兒，本遷移不處理。
--
-- 可還原：每一筆被下架的資料寫一列 audit_log（action = 'DEF333_UNPUBLISHED_ORPHAN'，old_value／new_value 是前後狀態，
-- entity_type 為 LISTING／POST／CMS_PAGE）。要還原就依這些列把狀態改回 old_value。
--
-- 🔴 部署前請先看筆數（唯讀，三個 SELECT 可單獨執行；與下面的 UPDATE 條件相同）：
--     SELECT l.listing_type, l.status, u.role, count(*) FROM listings l JOIN users u ON u.id = l.owner_id
--     WHERE l.tenant_id = '00000000-0000-0000-0000-000000000001' AND l.status = 'ACTIVE'
--       AND u.role NOT IN ('ADMIN', 'SUPER_ADMIN') GROUP BY 1, 2, 3 ORDER BY 1, 3;
--     SELECT u.role, count(*) FROM posts p JOIN users u ON u.id = p.author_id
--     WHERE p.tenant_id = '00000000-0000-0000-0000-000000000001' AND p.status = 'PUBLISHED'
--       AND u.role NOT IN ('ADMIN', 'SUPER_ADMIN') GROUP BY 1 ORDER BY 1;
--     SELECT u.role, count(*) FROM cms_pages c JOIN users u ON u.id = c.author_id
--     WHERE c.tenant_id = '00000000-0000-0000-0000-000000000001' AND c.status = 'PUBLISHED'
--       AND u.role NOT IN ('ADMIN', 'SUPER_ADMIN') GROUP BY 1 ORDER BY 1;
--
-- 可重複執行（第二次沒有 ACTIVE／PUBLISHED 的孤兒可更新，不會再寫稽核列）。

DO $$
DECLARE
    system_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001';
    hidden_listings INTEGER;
    hidden_posts INTEGER;
    hidden_pages INTEGER;
BEGIN
    -- 1. 商品與房源
    WITH hidden AS (
        UPDATE listings l
        SET status = 'INACTIVE', updated_at = NOW()
        FROM users u
        WHERE u.id = l.owner_id
          AND l.tenant_id = system_tenant
          AND l.status = 'ACTIVE'
          AND u.role NOT IN ('ADMIN', 'SUPER_ADMIN')
        RETURNING l.id, l.tenant_id)
    INSERT INTO audit_log (id, tenant_id, user_id, action, entity_type, entity_id, old_value, new_value, reason, created_at)
    SELECT gen_random_uuid(), tenant_id, NULL, 'DEF333_UNPUBLISHED_ORPHAN', 'LISTING', id, 'ACTIVE', 'INACTIVE',
           'V90：系統租戶底下、建立者不是平台管理員的上架商品／房源，下架（不刪除）', NOW()
    FROM hidden;
    GET DIAGNOSTICS hidden_listings = ROW_COUNT;

    -- 2. 貼文
    WITH hidden AS (
        UPDATE posts p
        SET status = 'DRAFT', updated_at = NOW()
        FROM users u
        WHERE u.id = p.author_id
          AND p.tenant_id = system_tenant
          AND p.status = 'PUBLISHED'
          AND u.role NOT IN ('ADMIN', 'SUPER_ADMIN')
        RETURNING p.id, p.tenant_id)
    INSERT INTO audit_log (id, tenant_id, user_id, action, entity_type, entity_id, old_value, new_value, reason, created_at)
    SELECT gen_random_uuid(), tenant_id, NULL, 'DEF333_UNPUBLISHED_ORPHAN', 'POST', id, 'PUBLISHED', 'DRAFT',
           'V90：系統租戶底下、建立者不是平台管理員的已發布貼文，退回草稿（不刪除）', NOW()
    FROM hidden;
    GET DIAGNOSTICS hidden_posts = ROW_COUNT;

    -- 3. CMS 頁面
    WITH hidden AS (
        UPDATE cms_pages c
        SET status = 'DRAFT', updated_at = NOW()
        FROM users u
        WHERE u.id = c.author_id
          AND c.tenant_id = system_tenant
          AND c.status = 'PUBLISHED'
          AND u.role NOT IN ('ADMIN', 'SUPER_ADMIN')
        RETURNING c.id, c.tenant_id)
    INSERT INTO audit_log (id, tenant_id, user_id, action, entity_type, entity_id, old_value, new_value, reason, created_at)
    SELECT gen_random_uuid(), tenant_id, NULL, 'DEF333_UNPUBLISHED_ORPHAN', 'CMS_PAGE', id, 'PUBLISHED', 'DRAFT',
           'V90：系統租戶底下、建立者不是平台管理員的已發布 CMS 頁面，退回草稿（不刪除）', NOW()
    FROM hidden;
    GET DIAGNOSTICS hidden_pages = ROW_COUNT;

    RAISE NOTICE 'V90 系統租戶孤兒內容下架：商品／房源 % 筆、貼文 % 筆、CMS 頁面 % 筆（已逐筆寫入 audit_log，action = DEF333_UNPUBLISHED_ORPHAN）',
        hidden_listings, hidden_posts, hidden_pages;
END $$;
