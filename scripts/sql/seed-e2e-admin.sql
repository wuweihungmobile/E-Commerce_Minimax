-- seed-e2e-admin.sql — E2E／本機開發專用：把 admin@nextkey.local 設成已知密碼 Test123!（bcrypt）
--
-- ⚠️ 絕對不要對正式或預備環境執行。DEF-418（Sprint 305）的 V94 遷移起，Flyway 不再留下任何已知密碼的帳號：
--    admin@nextkey.local 的密碼被鎖成不可登入，測試帳號與測試租戶被刪除。需要已知密碼的 admin 的地方
--    （E2E 守門、手動觸發的雲端 e2e job、本機 dev），一律在「遷移完成之後」才執行本檔：
--      - make validate-e2e              （scripts/validate-e2e.sh 在後端啟動後自動執行）
--      - .github/workflows/ci.yml 的 e2e job（手動觸發）
--      - make dev-admin-password        （本機 dev 資料庫）
--    正式環境第一次設定 admin：見 docs/08_deployment/FIRST_ADMIN_SETUP.md。
--
-- 冪等：admin 不存在就建立（保險；Flyway 建出的資料庫一定有那一列，V94 只鎖密碼不刪除），
-- 存在就只重設密碼與必要欄位。雜湊與 V7 原本寫入的相同（Spring BCryptPasswordEncoder 對 Test123! 的結果）。

INSERT INTO users (id, email, password_hash, full_name, role, status, email_verified)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@nextkey.local',
    '$2a$10$juUhq3y1wExU.I9ialD3Yuk6CrIsP37Z5//q/b1vR2jGIUlYgvkcq',
    'E2E Test Admin',
    'SUPER_ADMIN',
    'ACTIVE',
    TRUE
)
ON CONFLICT (email) DO UPDATE SET
    password_hash  = EXCLUDED.password_hash,
    role           = 'SUPER_ADMIN',
    status         = 'ACTIVE',
    email_verified = TRUE;
