-- V98__Add_Is_Public_To_Media_Assets.sql
-- DEF-482（Sprint 336；使用者 2026-11-10 以互動選項拍板「素材逐一開關」）：媒體庫的檔案網址
-- /api/v2/media/files/{素材編號} 需要登入且有 media:read，而 <img> 不會帶憑證，所以店主上傳的圖沒有辦法拿去當
-- 公開頁（貼文封面圖等）的圖——媒體庫是一座孤島，只有媒體中心自己（用帶憑證的元件）看得到。
--
-- 做法：每個素材一個「公開」開關，預設私有。公開的素材可由匿名端點 GET /v2/public/media/{素材編號} 讀取；
-- 私有的、已刪除的、所屬店鋪不是 ACTIVE 的一律回 404（不區分原因，避免被拿來列舉素材是否存在）。
-- 原本需要登入的 /v2/media/files/{素材編號} 不變，仍然限本店鋪。
--
-- 既有素材全部回填為私有（DEFAULT FALSE）：過去沒有任何公開用途，沒有人期待它們是公開的；要公開由店主在媒體中心逐一切換。
-- 這也是安全的預設：這支遷移不會讓任何既有檔案變成匿名可讀。
--
-- NOT NULL DEFAULT FALSE：PostgreSQL 11 起新增帶常數預設值的欄位不會重寫整張表；實體以 nullable = false 對應。

ALTER TABLE media_assets ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN media_assets.is_public IS '公開開關：true 才能由匿名端點 /v2/public/media/{id} 讀取；預設 false（私有）。DEF-482，Sprint 336';
