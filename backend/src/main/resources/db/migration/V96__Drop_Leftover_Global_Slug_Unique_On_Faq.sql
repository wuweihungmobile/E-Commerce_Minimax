-- V96__Drop_Leftover_Global_Slug_Unique_On_Faq.sql
-- DEF-460（Sprint 330）：FAQ 的 slug 應該是「每間店鋪唯一」，資料庫卻留著「全平台唯一」的舊約束。
--
-- 成因：V26／V27 建 faq_categories／faq_articles 時，slug 的 UNIQUE 沒有命名，PostgreSQL 依預設規則命名為
-- <表>_slug_key。V29 想把唯一性改成（tenant_id, slug），卻用猜的名字
-- DROP CONSTRAINT IF EXISTS uk_faq_categories_slug／uk_faq_articles_slug——那兩個名字不存在，IF EXISTS 讓它
-- 靜默略過，舊約束因此一直留著；V29 新增的 uk_faq_*_tenant_slug 則正常建立，兩條並存。
--
-- 後果：店鋪 B 想用店鋪 A 用過的 slug 建立 FAQ 分類或文章時，服務層的 existsByTenantIdAndSlug（只看本店鋪）
-- 放行，資料庫的全域約束擋下，沒有業務錯誤對應，回 500 E-9900。
--
-- 這裡的約束名稱是在 Flyway 全新資料庫上用 pg_get_indexdef 實測得到的（faq_categories_slug_key、
-- faq_articles_slug_key），不是推論。Sprint 330 的 FaqKnowledgeSlugPerTenantFlywayIntegrationTest 會檢查
-- 遷移跑完後這四張表都只剩（tenant_id, slug）唯一索引，所以名稱若不吻合，測試會紅，不會再靜默略過。
--
-- knowledge_categories 不需要處理：V23 的約束有命名（uk_knowledge_categories_slug），V29 刪得掉；
-- knowledge_articles 從 V24 起就是（tenant_id, slug）。
--
-- 只放寬：刪掉較嚴的全域約束不可能讓既有資料違反任何約束；（tenant_id, slug）唯一約束仍在，
-- 同一間店鋪內不能重複。

ALTER TABLE faq_categories DROP CONSTRAINT IF EXISTS faq_categories_slug_key;
ALTER TABLE faq_articles DROP CONSTRAINT IF EXISTS faq_articles_slug_key;
