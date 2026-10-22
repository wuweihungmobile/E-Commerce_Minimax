-- V93__Return_Request_Items_Sku_Nullable.sql
-- DEF-404（Sprint 300；以真實資料掃描退貨頁面時發現）
--
-- 問題：商品沒建 SKU＝未啟用庫存追蹤，下單後的訂單品項沒有 SKU（order_items.sku_id 本來就可為 NULL）。
-- 這種品項申請退貨時，ReturnRequestService 刻意把 return_request_items.sku_id 設為 NULL
-- （receiveReturn 也只對 sku_id 不為 NULL 的品項回補庫存），但 V74 把該欄位定義成 NOT NULL，
-- 插入違反約束 → POST /v2/returns 回 500。沒建 SKU 的店鋪，買家永遠無法申請退貨。
--
-- 修法：sku_id 改為可為 NULL（保留外鍵：有值時仍必須指向存在的 SKU）。沒有 SKU＝沒有庫存可回補，
-- 收貨確認只更新退貨單狀態與收到的數量，不寫任何庫存異動。既有資料不受影響。

ALTER TABLE return_request_items
    ALTER COLUMN sku_id DROP NOT NULL;
