import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';

/**
 * AT-PROFILE-EDIT-REAL: 個人資料編輯表單 /auth/profile（DEF-358(b)，Sprint 257，真實後端）
 *
 * PRD §10.1.1 登記的缺口：全庫查無個人資料編輯（姓名/電話）功能，`/account` 頁僅有 GDPR
 * 自助功能（資料匯出/刪除帳戶）。本規格驗證新頁面與新增的 `PUT /v2/auth/me` 端點串接後的
 * 真實行為——不 mock，真實註冊/登入/更新。
 *
 * `registerAndLogin` 註冊時固定填入姓名「E2E Test User」（見 helpers/auth.ts），可做為
 * 表單預填值的已知基準。
 */
test.describe('AT-PROFILE-EDIT-REAL: 個人資料編輯表單', () => {
  test('編輯個人資料：表單預填現有姓名，更新後呼叫 PUT /v2/auth/me，重新整理後持久化', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/auth/profile');

    await expect(page.getByTestId('profile-full-name')).toHaveValue('E2E Test User');

    await page.getByTestId('profile-full-name').fill('更新後的姓名');
    await page.getByTestId('profile-phone').fill('0912345678');

    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/v2/auth/me') && r.request().method() === 'PUT'),
      page.getByTestId('profile-save').click(),
    ]);
    expect(response.status()).toBe(200);
    await expect(page.getByTestId('profile-save-success')).toBeVisible();

    await page.reload();
    await expect(page.getByTestId('profile-full-name')).toHaveValue('更新後的姓名');
    await expect(page.getByTestId('profile-phone')).toHaveValue('0912345678');
  });

  test('只更新電話：姓名欄位維持不變（PATCH 語意，前端與後端一致）', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/auth/profile');

    await page.getByTestId('profile-phone').fill('0987654321');
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/v2/auth/me') && r.request().method() === 'PUT'),
      page.getByTestId('profile-save').click(),
    ]);

    await expect(page.getByTestId('profile-full-name')).toHaveValue('E2E Test User');
    await expect(page.getByTestId('profile-phone')).toHaveValue('0987654321');
  });

  test('姓名清空後儲存：顯示錯誤訊息，不會把姓名清空', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/auth/profile');

    await page.getByTestId('profile-full-name').fill('');
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/v2/auth/me') && r.request().method() === 'PUT'),
      page.getByTestId('profile-save').click(),
    ]);
    expect(response.status()).toBe(400);
    await expect(page.getByTestId('profile-save-success')).not.toBeVisible();

    await page.reload();
    await expect(page.getByTestId('profile-full-name')).toHaveValue('E2E Test User');
  });

  test('從帳戶設定頁可導航至個人資料編輯頁', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/account');

    await page.getByTestId('account-edit-profile').click();
    await expect(page).toHaveURL(/\/auth\/profile$/);
  });
});
