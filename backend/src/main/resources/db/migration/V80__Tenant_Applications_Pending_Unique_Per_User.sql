-- V80__Tenant_Applications_Pending_Unique_Per_User.sql
-- Sprint 137 DEF-141：同一使用者併發送出兩次開店申請時，
-- 「existsByUserIdAndStatusIn(PENDING) 檢查」與「save() 新增」之間沒有原子保護，
-- 兩個併發請求都可能通過檢查，各自 INSERT 出重複的 PENDING TenantApplication。
--
-- 訪客（user_id IS NULL）不受此約束限制：PostgreSQL 的 UNIQUE 索引視多個 NULL 為互不相等，
-- 符合 TenantService.createApplication 原本「僅登入使用者才做重複申請檢查」的既有語意。
CREATE UNIQUE INDEX idx_tenant_applications_pending_user_unique
    ON tenant_applications(user_id)
    WHERE status = 'PENDING';
