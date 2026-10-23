-- V94__Remove_Test_Seed_Data_And_Lock_Known_Accounts.sql
-- DEF-418（Sprint 305）：移除 V1／V5／V7／V15／V16 種下的測試帳號與測試租戶資料，並鎖住 admin@nextkey.local 的密碼
--
-- 背景：application.yml 的 Flyway 沒有環境過濾，所有遷移在每個 Flyway 資料庫都會跑，包含日後的正式環境。
-- 上述遷移把測試帳號、測試租戶（nextkey-test、pending-test、active-test、m15-test-store-2、m15-api-test-store）
-- 與其房源、房型、定價規則、功能開關、貼文放進每個資料庫；其中 admin@nextkey.local（SUPER_ADMIN）的密碼被 V7 以
-- ON CONFLICT DO UPDATE 蓋成已知的測試密碼（Sprint 304 以 Spring Security BCryptPasswordEncoder 離線核對：
-- 唯一可證實為已知密碼的帳號），E2E gate 每次都用它登入成功。
--
-- 做法（使用者於 Sprint 304 選定的做法 A：前進式遷移，不改既有遷移）：
--   1. 鎖密碼：admin 與全部測試帳號的 password_hash 一律換成「不可登入」的 bcrypt 雜湊（以安全亂數當密碼做 bcrypt，
--      密碼已丟棄，沒有人知道，matches() 對任何輸入都是 false）。這一步最先做、一定會做，不受後面刪除是否成功影響。
--   2. 刪除測試租戶：逐個租戶、各自一個子交易（PL/pgSQL 的 BEGIN … EXCEPTION 區塊）。它的貼文、貼文分類與租戶本身要嘛一起刪掉
--      （房源、房型、定價規則、功能開關、成員隨外鍵 CASCADE 一併消失），要嘛因為被其他資料以外鍵參照（舊 dev 資料庫手動操作
--      留下的紀錄）而整個租戶原封不動——包含它的貼文與分類——並發出 NOTICE，不讓整個遷移失敗、也不讓應用程式起不來。
--   3. 刪除測試帳號：逐個帳號，同樣各自容錯。租戶要先於帳號刪除（理由見 DO 區塊內的註解）。
--   只有外鍵違反（foreign_key_violation）會被容忍；其他種類的錯誤（例如資料表不存在）仍會讓遷移失敗。
--   保留：系統租戶（V1／V9，id 00000000-0000-0000-0000-000000000001）與它的功能開關、admin@nextkey.local 這一列（只鎖密碼）。
--
-- 之後要在本機開發或 E2E 使用 admin：執行 scripts/sql/seed-e2e-admin.sql（make dev-admin-password；make validate-e2e 會自動執行）。
-- 正式環境第一次設定 admin 密碼：見 docs/08_deployment/FIRST_ADMIN_SETUP.md。絕對不要對正式環境執行 seed-e2e-admin.sql。
--
-- 冪等：重複執行不會出錯（鎖密碼是固定值，刪除的列已不存在）。純 SQL（沒有 Flyway 專屬語法），Flyway 與 psql 皆可執行
-- （make validate-schema-doc 以 psql 逐支套用 V1～V94 已驗證）。dev 的 docker-entrypoint-initdb.d 路徑另有字母序問題，
-- 見 DEF-356；V94 在那條路徑沒有實測過。

DO $$
DECLARE
    locked_hash      CONSTANT TEXT   := '$2a$10$pm46Q36jtWvbgm1vK87mReCGpp1SkEfwu4vpD1Xq845myDwXQrUim';
    admin_email      CONSTANT TEXT   := 'admin@nextkey.local';
    -- 測試帳號（admin 另外處理：只鎖密碼、不刪除）
    test_emails      CONSTANT TEXT[] := ARRAY[
        'test-store-owner@nextkeytest.com',   -- V5
        'pending-owner@e2e-test.com',         -- V7
        'active-owner@e2e-test.com',          -- V7
        'buyer-test@e2e-test.com',            -- V7
        'm15-owner@test.com',                 -- V15
        'm15-api-owner@test.com'              -- V16
    ];
    -- 測試租戶（系統租戶 00000000-... 不在內）
    test_tenant_ids  CONSTANT UUID[] := ARRAY[
        '11111111-1111-1111-1111-111111111111',   -- V5 nextkey-test-tenant（V15 的同 id 租戶以 ON CONFLICT DO NOTHING 略過）
        '22222222-2222-2222-2222-222222222222',   -- V15 M15 測試店鋪 2
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',   -- V7 pending-review-tenant
        'cccccccc-cccc-cccc-cccc-cccccccccccc',   -- V7 active-test-tenant
        'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'    -- V16 M15 API 自動化測試店鋪
    ]::UUID[];
    account     TEXT;
    tenant_item UUID;
BEGIN
    -- 1. 鎖密碼（一律先做）
    UPDATE users SET password_hash = locked_hash WHERE email = admin_email OR email = ANY(test_emails);

    -- 2. 逐個測試租戶刪除；每個租戶一個子交易：貼文、分類、租戶要嘛全刪，要嘛因外鍵被擋下而整個租戶原封不動。
    --    刪除順序與容錯範圍的依據（以 pg_constraint 實測 V93 schema 全部 117 個外鍵）：
    --      - posts、post_categories 以 NO ACTION 參照 tenants，所以要先於租戶刪除；沒有任何表以 NO ACTION 參照它們
    --        （posts.category_id 是 SET NULL、post_embeds 是 CASCADE），所以這兩句本身不會被擋
    --      - listings、tenant_feature_toggles、tenant_members 對 tenants 是 CASCADE；rooms、pricing_rules、products、
    --        product_skus 對 listings 是 CASCADE——所以租戶一刪，它們就一起消失，不需要另外顯式刪除
    --      - 擋得下來的是 NO ACTION／RESTRICT 的參照（例如 cart_items、orders、bookings 指向測試租戶的房源或租戶本身）
    --      - 沒有可延後（DEFERRABLE）的外鍵，違反在語句結束時就拋出，接得住；7 個 SET NULL 外鍵的欄位全都可為 NULL，
    --        連鎖刪除不會變成 not_null_violation 這種接不住的錯誤
    FOREACH tenant_item IN ARRAY test_tenant_ids LOOP
        BEGIN
            DELETE FROM posts           WHERE tenant_id = tenant_item;
            DELETE FROM post_categories WHERE tenant_id = tenant_item;
            DELETE FROM tenants         WHERE id = tenant_item;
        EXCEPTION WHEN foreign_key_violation THEN
            RAISE NOTICE 'V94：仍有其他資料參照，保留租戶 %（連同它的貼文與分類）', tenant_item;
        END;
    END LOOP;

    -- 3. 逐個帳號刪除：一個被擋下，不影響其他沒被擋下的。
    --    租戶先於帳號：listings.owner_id、posts.author_id 以 NO ACTION 參照 users，帳號要等自己的房源與貼文隨租戶消失之後才刪得掉。
    --    反過來（帳號先刪）時，持有測試店鋪房源或貼文的帳號會因為它們還在而在這一輪刪不掉，要等第二次執行才清得掉。
    --    （users 資料表本身沒有任何外鍵，順序與 users.tenant_id 無關。）
    FOREACH account IN ARRAY test_emails LOOP
        BEGIN
            DELETE FROM users WHERE email = account;
        EXCEPTION WHEN foreign_key_violation THEN
            RAISE NOTICE 'V94：仍有其他資料參照，保留帳號 %（密碼已鎖）', account;
        END;
    END LOOP;
END $$;
