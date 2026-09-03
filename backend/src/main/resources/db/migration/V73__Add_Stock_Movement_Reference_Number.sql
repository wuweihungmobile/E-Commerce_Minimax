-- V73__Add_Stock_Movement_Reference_Number.sql
-- Sprint 117 / DEF-064：新增店家自填的參考單號欄位。
--
-- 背景：ERP 新增庫存異動的表單一直有「參考單號」輸入框，但 StockMovementRequest 根本沒有這個欄位，
-- 送出後被 Jackson 靜默忽略——使用者打了字、按了送出、什麼也沒發生，而且不會有任何錯誤訊息。
--
-- 與 reference_id / reference_type 的差別：後兩者是**系統產生**的異動回指來源（採購單 id、訂單 id），
-- 本欄是**店家自己記**的單號（如「盤點單 2026-09」）。使用者拍板（選項 C）兩者並存：
-- 共用一欄會讓「這個單號是誰產生的」永遠說不清楚。

ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);
