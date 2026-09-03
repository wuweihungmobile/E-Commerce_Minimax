-- V75__Drop_Orphan_Inventory_Checks_Table.sql
-- Sprint 121 / DEF-067：移除孤兒的 inventory_checks 表 + InventoryCheck entity。
--
-- 背景：與 V72（DEF-066，移除 inventory 表）同源。V50 的檔頭註解自承兩者的來歷：
-- 「Inventory / InventoryCheck entity 存在，但沒有對應的建表 migration，導致 ddl-auto=validate
-- 時 Hibernate schema 驗證失敗（missing table）」——都是為了讓 schema 驗證過關而補建的空殼。
--
-- 與 inventory 不同的是：inventory_checks **從未有任何生產程式碼讀或寫**（inventory 至少還被
-- 四個讀取點誤讀成資料來源），純粹是尚未實作功能（庫存盤點，PRD §6.7.2 P1）的骨架，且骨架本身
-- 也不完整——只有 InventoryCheck 單頭表（彙總的 varianceCount／totalItemsChecked），沒有任何
-- 逐 SKU 明細子表，就算接上 CRUD 也做不出「盤盈/盤虧記錄」。
--
-- 使用者已於 2026-09-04 裁決：移除孤兒骨架（技術債清理），庫存盤點若日後要做，
-- 應先走完整 AISDLC 需求分析（PRD 目前只有一行摘要，無 User Story／AC／API 規格），
-- 而非在殘留骨架上接功能。PRD §14.2.5 的 BV-2A-04（ERP 庫存準確率）KPI 目前無法量測，
-- 此為既有事實不因本次移除而改變。

DROP TABLE IF EXISTS inventory_checks;
