-- V89__Backfill_Order_Tenant_To_Store.sql
-- Sprint 237（DEF-319 訂單側；Sprint 232 以真實 JAR＋PostgreSQL＋Redis＋兩個真實角色實測重現）
--
-- 問題：OrderService.createOrderFromCart／CombinedCheckoutService 把訂單的 tenant_id 蓋成「下單者的租戶」。一般消費者不屬於任何店鋪，
-- 他們的租戶脈絡是系統租戶佔位值（00000000-0000-0000-0000-000000000001，不是 null、全體消費者共用），於是真實客人的訂單全部歸在系統租戶
-- 底下——店主的賣家訂單列表永遠是 0 筆、讀不到也處理不了客人的訂單，週結算（依 orders.tenant_id 為每個租戶彙總）也不會納入。
-- 運費模板與促銷碼同樣是用買家租戶解析，店鋪設定的運費與優惠券從未對真實消費者生效。
--
-- 程式碼已修（Sprint 237 同店結帳，PRD US-008／PC-005：單筆訂單限同一商家）。本遷移處理修復前就已存在的歷史資料。
-- 不變量：訂單的 tenant_id 等於其項目所屬房源（listings）的 tenant_id；訂單衍生出的資料（客服工單、退貨單、對話）與庫存異動，
-- 租戶跟著各自的來源（訂單、SKU 所屬的房源）。
--
-- 🔴 影響金流，部署前請先看筆數（下面三個 SELECT 可單獨執行）：
--   * 只搬「尚未被任何結算單認領」（settled_statement_id IS NULL）的訂單。已經被某張結算單結算過的訂單不動——
--     週結算會替每個 ACTIVE 租戶（含系統租戶）產生結算單，系統租戶底下的歷史訂單可能已經被系統租戶的結算單認領；
--     把它們搬給店鋪、結算單卻留在系統租戶，帳上就對不起來，而店鋪也不會因此自動拿到錢。這些訂單需要財務人工決定怎麼處理，
--     本遷移不替他們決定（筆數見 RAISE NOTICE）。
--   * 被搬到店鋪的、已完成且尚未結算的訂單，會在該店鋪「下一次」週結算進入結算單（結算本來就納入所有尚未結算的已完成訂單，
--     不論哪週下單）。結算單仍需經過審核流程才會撥款，但金額可能不小。
--   * 同一張訂單的項目來自多家店鋪（修復前購物車沒有限制）者無法判定歸屬，不動（筆數見 RAISE NOTICE）。
--   * 沒有項目的訂單（舊的 ROOM 訂單路徑）不動。
--
--   預覽：
--     SELECT o.id, o.tenant_id AS order_tenant, s.store_tenant_id, o.status, o.total_amount
--     FROM orders o
--     JOIN (SELECT oi.order_id, (ARRAY_AGG(DISTINCT l.tenant_id))[1] AS store_tenant_id
--           FROM order_items oi JOIN listings l ON l.id = oi.listing_id
--           GROUP BY oi.order_id HAVING COUNT(DISTINCT l.tenant_id) = 1) s ON s.order_id = o.id
--     WHERE o.tenant_id <> s.store_tenant_id AND o.settled_statement_id IS NULL;
--
-- 可重複執行（第二次不會再更新任何列）。

DO $$
DECLARE
    moved_orders INTEGER;
    ambiguous_orders INTEGER;
    settled_orders INTEGER;
    fixed_tickets INTEGER;
    fixed_returns INTEGER;
    fixed_conversations INTEGER;
    fixed_movements INTEGER;
BEGIN
    -- 資訊：無法判定歸屬、或已被結算單認領而不動的訂單
    SELECT COUNT(*) INTO ambiguous_orders FROM (
        SELECT oi.order_id
        FROM order_items oi JOIN listings l ON l.id = oi.listing_id
        GROUP BY oi.order_id
        HAVING COUNT(DISTINCT l.tenant_id) > 1) multi;
    SELECT COUNT(*) INTO settled_orders FROM orders o
    JOIN (SELECT oi.order_id, (ARRAY_AGG(DISTINCT l.tenant_id))[1] AS store_tenant_id
          FROM order_items oi JOIN listings l ON l.id = oi.listing_id
          GROUP BY oi.order_id HAVING COUNT(DISTINCT l.tenant_id) = 1) s ON s.order_id = o.id
    WHERE o.tenant_id <> s.store_tenant_id AND o.settled_statement_id IS NOT NULL;

    -- 1. 訂單：改成項目所屬房源的店鋪（只動單一店鋪、尚未結算的訂單）
    UPDATE orders o
    SET tenant_id = s.store_tenant_id
    FROM (SELECT oi.order_id, (ARRAY_AGG(DISTINCT l.tenant_id))[1] AS store_tenant_id
          FROM order_items oi JOIN listings l ON l.id = oi.listing_id
          GROUP BY oi.order_id
          HAVING COUNT(DISTINCT l.tenant_id) = 1) s
    WHERE o.id = s.order_id
      AND o.tenant_id <> s.store_tenant_id
      AND o.settled_statement_id IS NULL;
    GET DIAGNOSTICS moved_orders = ROW_COUNT;

    -- 2. 訂單衍生的資料：租戶跟著訂單（客服工單、退貨單、以訂單為對象的對話）
    UPDATE support_tickets t SET tenant_id = o.tenant_id
    FROM orders o
    WHERE t.order_id = o.id AND t.tenant_id IS DISTINCT FROM o.tenant_id;
    GET DIAGNOSTICS fixed_tickets = ROW_COUNT;

    UPDATE return_requests r SET tenant_id = o.tenant_id
    FROM orders o
    WHERE r.order_id = o.id AND r.tenant_id <> o.tenant_id;
    GET DIAGNOSTICS fixed_returns = ROW_COUNT;

    -- 對話的租戶來源是「房源優先、其次訂單」（ChatService.resolveTenantId），所以只動沒有房源的對話
    UPDATE conversations c SET tenant_id = o.tenant_id
    FROM orders o
    WHERE c.order_id = o.id AND c.listing_id IS NULL AND c.tenant_id <> o.tenant_id;
    GET DIAGNOSTICS fixed_conversations = ROW_COUNT;

    -- 3. 庫存異動：屬於 SKU 所屬房源的店鋪（訂單預扣／出貨扣帳的流水帳原本蓋成訂單的租戶）
    UPDATE stock_movements m SET tenant_id = l.tenant_id
    FROM product_skus s JOIN listings l ON l.id = s.product_listing_id
    WHERE m.sku_id = s.id AND m.tenant_id <> l.tenant_id;
    GET DIAGNOSTICS fixed_movements = ROW_COUNT;

    RAISE NOTICE 'V89 訂單租戶回填：搬到店鋪 % 筆；多家店鋪無法判定而不動 % 筆；已被結算單認領而不動 % 筆（需財務人工決定）',
        moved_orders, ambiguous_orders, settled_orders;
    RAISE NOTICE 'V89 衍生資料：客服工單 % 筆、退貨單 % 筆、對話 % 筆、庫存異動 % 筆改成跟著來源的租戶',
        fixed_tickets, fixed_returns, fixed_conversations, fixed_movements;
END $$;
