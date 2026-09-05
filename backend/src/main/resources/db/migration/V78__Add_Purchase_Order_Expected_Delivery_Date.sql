-- Sprint 129 (DEF-078): 採購單預計到貨日期欄位原本不存在，DTO 恆回傳 null，
-- 前端檢視/編輯頁對 null 呼叫 .split('T') 必定拋出 TypeError（頁面必定顯示「載入採購訂單失敗」）。
ALTER TABLE purchase_orders ADD COLUMN expected_delivery_date DATE NULL;
