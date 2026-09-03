-- V72__Drop_Orphan_Inventory_Table.sql
-- Sprint 116 / DEF-066：移除孤兒的 inventory 表。
--
-- 背景：系統有兩張庫存表。真正的數字全在 product_inventory——訂單預扣／扣帳／釋放、
-- ERP 手動異動、採購收貨都寫那張；而本表**沒有任何生產程式碼寫入**。
-- V50 的檔頭註解自承它的來歷：「Inventory / InventoryCheck entity 存在，但沒有對應的建表
-- migration，導致 ddl-auto=validate 時 Hibernate schema 驗證失敗（missing table）」
-- ——換句話說，它是為了讓 schema 驗證過關而補建的空殼，不是為了存資料。
--
-- 後果是 ERP 庫存台帳／庫存明細／低庫存預警（PRD §6.7.2 列為 P0）與賣場商品卡讀了它，
-- 在生產環境上永遠是空的。Sprint 116 已把這四個讀取點全部改讀 product_inventory。
--
-- 為什麼要真的刪掉而不是留著：留下一張名字看起來就是「庫存」的空表，正是這個缺陷能存活的原因
-- ——下一個人同樣會理所當然地讀它。連同 Inventory entity 與 InventoryRepository 一併移除。
--
-- ⚠️ inventory_checks（庫存盤點，PRD §6.7.2 P1）**不在本次移除範圍**：它的 InventoryCheck entity
-- 仍存在（同樣尚無生產寫入），刪表會讓 ddl-auto=validate 失敗。已另記為追蹤項目。

DROP TABLE IF EXISTS inventory;
