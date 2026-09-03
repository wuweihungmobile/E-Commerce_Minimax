-- V71__Align_Stock_Movement_Types_To_PRD.sql
-- Sprint 114 / DEF-063：將 stock_movements.movement_type 對齊 PRD §6.7.4 異動類型表。
--
-- 背景：實體枚舉 StockMovement.MovementType 用的是一組與 PRD 無關的命名，而 PRD §6.7.4、
-- PRD 資料表定義、StockMovementDto／StockMovementRequest 的欄位註解、前端 StockMovementType
-- 全部用 PRD 命名。DTO 的 movementType 是 String 直接進 valueOf()，中間沒有轉換層，
-- 導致 /dashboard/erp/stock-movements/new 的 7 個選項有 5 個（含預設值）必定回 E_7005。
--
-- 對照表（左＝舊值，右＝PRD 值）。RELEASE／TRANSFER_IN／TRANSFER_OUT／RETURN 名稱本來就相同。
--   PURCHASE_RECEIPT → INBOUND       採購入庫
--   SALE             → OUTBOUND      訂單出貨
--   RESERVATION      → RESERVE       訂單預留
--   ADJUSTMENT       → ADJUST_PLUS   盤盈調整（舊值語意即 +total_qty）
--   DAMAGE           → ADJUST_MINUS  盤虧調整（舊值語意即 -total_qty）
--   THEFT            → SCRAP         報廢出庫（舊枚舉註解本來就寫「報廢」）
--
-- RETURN 保持原值不轉換：它在 PRD §6.7.4 沒有對應型別，強行改寫成任一 PRD 值都會竄改歷史語意
-- （舊程式碼對 RETURN 是靜默 no-op，庫存並未變動，與任何 PRD 型別的方向都不符）。
-- Sprint 114 起後端明確拒絕新建 RETURN，既有列則原樣保留供查閱。

UPDATE stock_movements SET movement_type = 'INBOUND'      WHERE movement_type = 'PURCHASE_RECEIPT';
UPDATE stock_movements SET movement_type = 'OUTBOUND'     WHERE movement_type = 'SALE';
UPDATE stock_movements SET movement_type = 'RESERVE'      WHERE movement_type = 'RESERVATION';
UPDATE stock_movements SET movement_type = 'ADJUST_PLUS'  WHERE movement_type = 'ADJUSTMENT';
UPDATE stock_movements SET movement_type = 'ADJUST_MINUS' WHERE movement_type = 'DAMAGE';
UPDATE stock_movements SET movement_type = 'SCRAP'        WHERE movement_type = 'THEFT';

-- 加上值域約束，讓「枚舉與 PRD 再次漂移」變成 DB 層擋得住的錯誤，而不是等使用者在 UI 上撞到
-- E_7005 才被發現（比照 V19 對 reference_type 的作法）。若既有資料含對照表以外的值，
-- 這裡會直接失敗——那是刻意的：不明值必須被看見，不能靜默放行。
ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_movement_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_movement_type_check
    CHECK (movement_type IN (
        'INBOUND',
        'OUTBOUND',
        'RESERVE',
        'RELEASE',
        'ADJUST_PLUS',
        'ADJUST_MINUS',
        'TRANSFER_IN',
        'TRANSFER_OUT',
        'SCRAP',
        'RETURN'
    ));
