import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';

/**
 * AT-ACCOUNT-DATA-RIGHTS: PRD §1.5.1 會員資料權利前端入口 E2E 測試
 *
 * Sprint 153（Sprint 149 §7 範圍外項目）：後端 GET /v2/auth/me/data-export、
 * DELETE /v2/auth/me 早於 Sprint 94（AI-2428）完成，此前全站零前端呼叫點（DEF-185 §2.3
 * 記錄的偽陽性排除項目之一）。本測試涵蓋新增的 /account 頁面兩項操作。
 *
 * 每個 test 各自呼叫 registerAndLogin 建立獨立的新帳號（helper 以 timestamp+random 產生
 * 隨機 email），刪除帳戶測試不影響其他測試或其他 spec 共用的帳號。
 */
test.describe('AT-ACCOUNT-DATA-RIGHTS: 會員資料權利', () => {
  test('匯出我的資料：點擊按鈕觸發 GET /v2/auth/me/data-export 並下載 JSON 檔', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/account');

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.waitForResponse(
        (r) => r.url().includes('/v2/auth/me/data-export') && r.request().method() === 'GET'
      ),
      page.getByTestId('account-export-data').click(),
    ]);

    expect(download.suggestedFilename()).toMatch(/^my-data-export-\d{4}-\d{2}-\d{2}\.json$/);
  });

  test('刪除帳戶：確認後呼叫 DELETE /v2/auth/me 並導向登入頁、清除本機 token', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/account');

    await page.getByTestId('account-delete-open').click();

    const [response] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/v2/auth/me') && r.request().method() === 'DELETE'
      ),
      page.getByTestId('account-delete-confirm').click(),
    ]);
    expect(response.status()).toBe(200);

    await page.waitForURL('**/login**', { timeout: 10000 });
    const accessToken = await page.evaluate(() => localStorage.getItem('accessToken'));
    expect(accessToken).toBeNull();
  });
});
