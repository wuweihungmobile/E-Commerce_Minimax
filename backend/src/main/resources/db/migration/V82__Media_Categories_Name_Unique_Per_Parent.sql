-- V82__Media_Categories_Name_Unique_Per_Parent.sql
-- DEF-160（MediaService.createCategory，Sprint 143）
--
-- 問題：createCategory 的分類名稱唯一性檢查（existsByTenantIdAndNameAndParentIsNull /
-- existsByTenantIdAndNameAndParentId）與 save() 之間是 check-then-act TOCTOU，
-- media_categories 對 (tenant_id, name, parent_id) 沒有任何唯一約束兜底，兩個併發請求
-- 可各自建立一筆同名分類。
--
-- 修法：對應程式碼既有的兩種檢查分支各建一個部分唯一索引：
-- 1. 根層級（parent_id IS NULL）：同一 tenant 下 name 不可重複。
-- 2. 子層級（parent_id IS NOT NULL）：同一 tenant + 同一 parent 下 name 不可重複。
-- 用兩個部分索引而非單一 UNIQUE(tenant_id, name, parent_id) 約束，因 PostgreSQL 的唯一
-- 約束視多個 NULL 互不相等，無法用單一約束涵蓋「根層級同名也要擋」的語意。

CREATE UNIQUE INDEX idx_media_categories_root_name_unique
    ON media_categories (tenant_id, name)
    WHERE parent_id IS NULL;

CREATE UNIQUE INDEX idx_media_categories_child_name_unique
    ON media_categories (tenant_id, parent_id, name)
    WHERE parent_id IS NOT NULL;
