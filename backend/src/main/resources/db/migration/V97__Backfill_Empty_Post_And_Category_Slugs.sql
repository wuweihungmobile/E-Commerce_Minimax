-- V97__Backfill_Empty_Post_And_Category_Slugs.sql
-- DEF-480（Sprint 334）：全中文標題的貼文、全中文名稱的貼文分類，slug 曾被存成空字串。
--
-- 成因：PostService／PostCategoryService 的 generateSlug 用 [^a-z0-9\s\-] 刪掉所有非英數字元，
-- 全中文的標題或名稱就變成 ""。posts.slug 是 NOT NULL UNIQUE（V10）、post_categories 是 (tenant_id, slug)
-- 唯一（V11），空字串是合法值，所以不會被資料庫擋下。後果：公開部落格清單的文章連結是 /blog/<slug>，
-- slug 為空時變成 /blog，點下去回到清單本身，文章頁無法從清單進入。
--
-- 程式碼已改成清完為空時改用 post-<8 碼>／category-<8 碼> 後備（Sprint 334）；這支遷移把已經存下的
-- 空字串補成同樣格式。因為上述唯一約束，posts 全平台最多 1 列、每間店鋪的 post_categories 最多 1 列是空的，
-- 隨機碼互相撞名的機率可以忽略，也不需要處理碰撞。
--
-- 刻意只動 slug = '' 的列：先前被存成 "-<8碼>"（開頭有連字號但可用）的貼文網址是能開的，
-- 改掉它們會讓已分享出去的連結失效，得不償失。空字串的那一列本來就點不進去，補上只有好處。
--
-- 冪等：補完之後沒有任何列的 slug 還是空字串，重跑不會再動到任何列。

UPDATE posts
SET slug = 'post-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)
WHERE slug = '';

UPDATE post_categories
SET slug = 'category-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)
WHERE slug = '';
