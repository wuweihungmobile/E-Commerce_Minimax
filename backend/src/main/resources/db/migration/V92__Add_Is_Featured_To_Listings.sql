-- V92__Add_Is_Featured_To_Listings.sql
-- DEF-321(b)（Sprint 232 使用者選定；Sprint 252 實作；PRD §12.7「精選商品/房源（由 StoreOwner 設定）」）
--
-- 問題：店鋪前台首頁需要顯示 StoreOwner 指定的「精選商品/房源」，但 Listing 完全沒有對應欄位，
-- 全庫搜尋不到任何策展機制。
--
-- 修法：新增 is_featured，預設 false，不影響既有資料；StoreOwner 於 dashboard 編輯商品/房源時勾選。

ALTER TABLE listings
    ADD COLUMN is_featured BOOLEAN NOT NULL DEFAULT false;
