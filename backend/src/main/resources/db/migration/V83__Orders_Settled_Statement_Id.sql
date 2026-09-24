-- V83__Orders_Settled_Statement_Id.sql
-- DEF-273（Sprint 195，使用者以互動選擇拍板）
--
-- 問題：週結算以「下單時間落在該週、且結算產生當下狀態為 COMPLETED/DELIVERED」歸屬訂單，
-- orders 沒有完成時間、也沒有已結算標記。週間下單、下週才送達的訂單，之後任何一期都撈不到它，
-- 永遠不會被結算（真實資料庫實測：週三下單 5000 元、結算當下 SHIPPING，改 DELIVERED 後
-- 第 W／W+1／W+2 週結算單皆 0 筆）。
--
-- 修法：訂單記錄它被哪一張結算單結算（settled_statement_id）。每週結算「所有已完成且尚未結算」的訂單
-- （不論哪週下單），結算時以 UPDATE ... WHERE settled_statement_id IS NULL 原子標記，
-- 保證每筆訂單恰好被結算一次：不漏、不重複。
--
-- 歷史訂單：不回填（NULL）。因為週結算單過去在真實資料庫從未成功產生過（DEF-272），
-- 所有歷史已完成訂單本來就都尚未結算，首次結算會一併納入。
--
-- 只有結算流程會寫這個欄位（原生 UPDATE），Order 實體對它是唯讀映射（insertable/updatable = false），
-- 避免任何後續的實體更新把記憶體中的舊值寫回、蓋掉併發的結算標記。

ALTER TABLE orders
    ADD COLUMN settled_statement_id UUID REFERENCES settlement_statements (id);

-- 由結算單反查其訂單（駁回時釋放、退款調整時定位）
CREATE INDEX idx_orders_settled_statement
    ON orders (settled_statement_id)
    WHERE settled_statement_id IS NOT NULL;

-- 結算時「某租戶尚未結算的可結算訂單」（部分索引，索引只含待結算者，隨結算自然縮小）
CREATE INDEX idx_orders_unsettled
    ON orders (tenant_id, created_at)
    WHERE settled_statement_id IS NULL AND status IN ('DELIVERED', 'COMPLETED');
