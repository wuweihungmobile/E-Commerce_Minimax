-- V88__Backfill_Booking_Tenant_To_Listing_Store.sql
-- Sprint 236（DEF-319 訂房側；Sprint 232 以真實 JAR＋PostgreSQL＋Redis＋兩個真實角色實測重現）
--
-- 問題：BookingService.createBooking 把訂房的 tenant_id 蓋成「下單者的租戶」。一般消費者不屬於任何店鋪，
-- 他們的租戶脈絡是系統租戶佔位值（00000000-0000-0000-0000-000000000001，不是 null、全體消費者共用），
-- 於是真實客人的訂房全部歸在系統租戶底下——商家端的訂房管理（Sprint 231）對這些訂房永遠是 0 筆，
-- 店主讀不到、也處理不了客人的訂房（以上為實測）；另依程式碼推論：A 店成員去訂 B 店的房，訂房也蓋成 A 店租戶、被 A 店的人看到。
--
-- 程式碼已修（Sprint 236：訂房歸屬「房源所屬的店鋪」，促銷碼也以店鋪租戶解析）。本遷移處理修復前就已存在的
-- 歷史資料：訂房的租戶與其房源所屬的租戶不一致者，一律改成房源的租戶。
-- 不變量：bookings.tenant_id 必須等於該筆訂房 room_listing_id 所指房源（listings）的 tenant_id。
--
-- 只動 bookings.tenant_id：付款（payments）、評價（booking_reviews）、用券紀錄（promo_code_usages）都沒有自己的
-- tenant_id，經由訂房或房源取得租戶；稽核紀錄（audit_logs）是當時的歷史事實，不改寫。
-- 取消訂房退還優惠券額度是依 promo_code_usages 的 booking_id 找券，與訂房租戶無關，不受本遷移影響。
-- 已知的邊角：修復前「A 店成員拿 A 店的促銷碼訂 B 店的房」留下的訂房（跨店使用促銷碼，PRD PC-005 Phase 1 本不支援），
-- 回填後訂房屬於 B 店；之後若改日期重算折扣，依 booking.tenant_id 找不到 A 店的券，折扣會歸零，與一般
-- 「券已不存在」的處理相同。這種資料是修復前的錯誤使用方式留下的，不為它保留跨店歸屬。
-- 可重複執行（第二次不會再更新任何列）。

UPDATE bookings b
SET tenant_id = l.tenant_id
FROM listings l
WHERE b.room_listing_id = l.id
  AND b.tenant_id <> l.tenant_id;
