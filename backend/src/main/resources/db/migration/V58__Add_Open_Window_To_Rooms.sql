-- V58__Add_Open_Window_To_Rooms.sql
-- NextKey E-Commerce Platform
-- Sprint 47 US-001 (AI-2202e): 開放窗語意實作
-- 用途: 為 rooms 新增「開放窗」兩欄，讓房源可表達「僅開放某段期間預訂」：
--         - open_until_date    DATE : 開放至某固定日（含當日）；NULL = 無此限制
--         - booking_window_days INT : 開放未來 N 天（滾動，相對下單/查詢當日）；NULL = 無此限制
--       有效開放上限 = 兩者中「最早生效者」min(open_until_date, referenceDate + booking_window_days)；
--       兩者皆 NULL = 無限制（維持現狀「無 room_calendar 記錄之日 = 可訂」的三層硬語意）。
-- 安全性 / 既有房源過渡: 兩欄皆 nullable、無 DEFAULT → 既有列自動 NULL = 無限制，
--       維持現狀「全可訂」，backfill 免任何資料異動（RoomService.createRoom 本就不 seed 日曆，
--       稀疏 lazy 模型相容）。使用 ADD COLUMN IF NOT EXISTS 保證冪等（空表與有資料皆適用）。
-- 慣例: 打破 S42~S46 連續零-migration（PO 已於 Sprint 主軸選定時知悉並接受）；
--       Room entity 同步加 openUntilDate (LocalDate) / bookingWindowDays (Integer)，
--       ddl-auto=validate 對齊（參考評估文件 CALENDAR_OPEN_WINDOW_ASSESSMENT.md §4）。

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS open_until_date DATE;

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS booking_window_days INTEGER;
