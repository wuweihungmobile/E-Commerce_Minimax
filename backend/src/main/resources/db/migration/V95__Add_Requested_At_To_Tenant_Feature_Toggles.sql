-- V95__Add_Requested_At_To_Tenant_Feature_Toggles.sql
-- DEF-429（Sprint 311；使用者以互動選項裁決「新增申請時間欄位」）
--
-- 問題：核准開店時系統替六個功能都建了預設資料列（AdminService.initializeFeatureToggles，BR-M17-002），其中三個
-- 需審核的功能（BOOKING_ENABLED／DYNAMIC_PRICING_ENABLED／PROMO_ENABLED）預設 is_enabled = false；而功能清單把
-- 「有資料列、未啟用、需審核」一律判成待審核（PENDING）。結果是全新店鋪從沒申請過這三項，店主端與管理員頁就顯示
-- 「待審核」，兩邊都無法分辨「店主申請了」與「系統預設建了一列」。
--
-- 修法：新增 requested_at，只在店主真的送出啟用申請時寫入；待審核＝需審核、未啟用、且 requested_at 有值。
-- 核准、停用或撤回時清除。
--
-- 既有資料列一律保持 NULL：預設列與真實申請在既有資料裡無法分辨（申請不會改動資料列的任何欄位），
-- 保守作法是全部視為「未申請」。如果有店主在本遷移前已送出、尚未被核准的申請，遷移後需要重新送出。

ALTER TABLE tenant_feature_toggles
    ADD COLUMN IF NOT EXISTS requested_at timestamp with time zone;
