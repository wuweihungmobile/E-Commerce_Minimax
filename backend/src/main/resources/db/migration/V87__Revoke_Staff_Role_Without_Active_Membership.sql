-- V87__Revoke_Staff_Role_Without_Active_Membership.sql
-- Sprint 235（DEF-329；稽核 B7，真實全棧實測重現）
--
-- 問題：TenantService.acceptInvite 會把 users.role 設成 STORE_STAFF，但 removeMember 只把 tenant_members.status 改成
-- REMOVED、不改角色。JWT 的角色取自 users.role，所以被店主移除的店員下次登入或換發 token 仍帶 STORE_STAFF
-- （修復前連同店鋪租戶一起，修復後租戶退回系統租戶——但仍帶著店員權限落在系統租戶，是另一種外洩）。
--
-- 程式碼已修（removeMember 收回角色；登入租戶解析只認 ACTIVE 成員）。本遷移處理修復前就已存在的遺留資料：
-- 沒有任何有效（ACTIVE）成員資格、角色卻仍是 STORE_STAFF 的使用者，一律收回成 BUYER。
-- 不變量：角色 STORE_STAFF 只應該出現在至少有一筆 ACTIVE 成員資格的人身上。
-- 僅更新 role = 'STORE_STAFF' 且無 ACTIVE 成員資格的列；可重複執行（第二次不會再更新任何列）。

UPDATE users
SET role = 'BUYER'
WHERE role = 'STORE_STAFF'
  AND NOT EXISTS (
      SELECT 1
      FROM tenant_members m
      WHERE m.user_id = users.id
        AND m.status = 'ACTIVE');
