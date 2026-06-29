-- V56__Add_Tenant_Id_To_Conversations.sql
-- 用途: 為 conversations 補上 tenant_id（多租戶隔離技術債）
-- 對應: Sprint 25 US-003 / AI-802（Conversation 為全庫少數未帶 tenant_id 的 entity 之一）
--
-- 歸屬規則（依關聯實體推導，使用者於 Sprint 25 Planning 確認）:
--   1. listing_id 存在 → 取該 listing 的 tenant_id（對話歸屬於被詢問的賣方租戶）
--   2. 否則 order_id 存在 → 取該 order 的 tenant_id
--   3. 否則（純 DIRECT 或關聯實體已不存在）→ 退回 System Tenant
--      (00000000-0000-0000-0000-000000000001，見 V9)
--
-- 慣例: FK/索引/命名對齊 V29（fk_<table>_tenant + ON DELETE CASCADE + idx_<table>_tenant_id）；
--       與 V29「一律 DEFAULT system tenant」不同處在於回填採推導（保留租戶語意）。

-- 1. 新增欄位（先 nullable，待回填後再設 NOT NULL）
ALTER TABLE conversations ADD COLUMN tenant_id UUID;

-- 2. 回填既有資料（依關聯實體推導）
UPDATE conversations c
SET tenant_id = l.tenant_id
FROM listings l
WHERE c.listing_id = l.id AND c.tenant_id IS NULL;

UPDATE conversations c
SET tenant_id = o.tenant_id
FROM orders o
WHERE c.order_id = o.id AND c.tenant_id IS NULL;

-- 純 DIRECT（無 listing/order）或關聯實體已不存在 → System Tenant
UPDATE conversations
SET tenant_id = '00000000-0000-0000-0000-000000000001'
WHERE tenant_id IS NULL;

-- 3. 設 NOT NULL + 外鍵 + 索引（對齊 V29 多租戶隔離慣例）
ALTER TABLE conversations ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
CREATE INDEX idx_conversations_tenant_id ON conversations(tenant_id);

COMMENT ON COLUMN conversations.tenant_id IS '租戶 ID - 多租戶隔離（AI-802 / Sprint 25 US-003）';
