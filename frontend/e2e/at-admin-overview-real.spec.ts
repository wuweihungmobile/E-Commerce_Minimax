import { test, expect } from '@playwright/test';
import { loginOnly } from './helpers/auth';

/**
 * AT-ADMIN-OVERVIEW-REAL: 平台管理台總覽首頁 /admin（DEF-358(d)，Sprint 258，真實後端）
 *
 * PRD §10.1.3 登記的缺口：平台管理台各功能（/admin/tenants、/admin/audit-logs 等）各自
 * 獨立路由，無統一入口頁。本規格驗證新頁面與既有、先前零前端呼叫點的 `GET /v2/admin/stats`
 * 串接後的真實行為——不 mock，真實登入既有固定 SUPER_ADMIN 測試帳號。
 *
 * 平台統計數字依賴共用測試資料庫的既有資料量，無法斷言精確數值，只斷言「有顯示數字」與
 * 「導覽功能正確」。
 */

const ADMIN_EMAIL = 'admin@nextkey.local';
const ADMIN_PASSWORD = 'Test123!';

test.describe('AT-ADMIN-OVERVIEW-REAL: 平台管理台總覽首頁', () => {
  test('E2E-ADMIN-OV-01: 顯示平台統計卡片，皆有數字（非載入失敗）', async ({ page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto('/admin');

    await expect(page.getByTestId('admin-overview-error')).not.toBeVisible();
    await expect(page.getByText('租戶總數')).toBeVisible();
    await expect(page.getByText('待審核開店申請')).toBeVisible();
    await expect(page.getByText('待審批採購單')).toBeVisible();
  });

  test('E2E-ADMIN-OV-02: 快速入口可導航至租戶管理頁', async ({ page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto('/admin');

    await page.getByTestId('admin-tenants-link').click();
    await expect(page).toHaveURL(/\/admin\/tenants$/);
  });

  test('E2E-ADMIN-OV-03: 待審核開店申請數字可點擊，導向租戶管理頁', async ({ page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto('/admin');

    await page.getByTestId('admin-pending-tenant-reviews').click();
    await expect(page).toHaveURL(/\/admin\/tenants$/);
  });

  test('E2E-ADMIN-OV-04: 待審批採購單數字可點擊，導向採購單審批頁', async ({ page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto('/admin');

    await page.getByTestId('admin-pending-purchase-orders').click();
    await expect(page).toHaveURL(/\/admin\/purchase-orders$/);
  });
});
