import { test, expect, Page } from '@playwright/test';
import { loginOnly, registerAndLogin } from './helpers/auth';
import { verifyEmailViaMailbox, waitForMailToken } from './helpers/mailbox';

/**
 * AT-M03-006／007: 忘記密碼、重設密碼、Email 驗證 E2E（Sprint 204，DEF-252／253；FRD US-M03-006／007）
 *
 * 用真實後端。信件是日誌型 Mock，連結從後端日誌取得（見 helpers/mailbox.ts），等同「打開信箱點連結」。
 *
 * 這組測試守的是「意圖」，不只是畫面：
 * - 重設連結**只能用一次**、舊密碼在重設後**立刻失效**（不是「改了密碼但舊的還能登入」）
 * - 忘記密碼**不揭露** Email 是否已註冊（未註冊的 Email 看到一模一樣的畫面）
 * - 未驗證 Email 的已登入會員**不能開店**，驗證之後才可以
 */
const SUBMIT = 'button[type="submit"]:not(:has-text("搜尋"))';
const NEW_PASSWORD = 'NewPass456!';

async function fillAndSubmitStoreApplication(page: Page): Promise<void> {
  await page.fill('input[name="storeName"]', `E2E 驗證閘門店鋪 ${Date.now()}`);
  const businessTypeSelect = page.locator('button[role="combobox"]').first();
  await businessTypeSelect.click();
  await expect(page.locator('[role="option"]').first()).toBeVisible();
  await page.click('[role="option"]:first-child');
  await page.fill('input[name="contactEmail"]', `store-${Date.now()}@example.com`);
  await page.fill('input[name="contactPhone"]', '0912345678');
  await page.click(SUBMIT);
}

test.describe('AT-M03-006: 忘記密碼與重設密碼', () => {
  test('申請連結 → 收信重設 → 舊密碼立刻失效、新密碼可登入；同一條連結不能再用', async ({ page }) => {
    const { email, password } = await registerAndLogin(page);
    await page.evaluate(() => localStorage.clear());

    await test.step('申請重設連結', async () => {
      await page.goto('/forgot-password');
      await page.fill('input[name="email"]', email);
      await page.click(SUBMIT);
      await expect(page.getByTestId('forgot-password-submitted')).toBeVisible();
    });

    const token = await waitForMailToken(page, email, 'reset-password');

    await test.step('用連結設定新密碼', async () => {
      await page.goto(`/reset-password?token=${token}`);
      await page.fill('input[name="password"]', NEW_PASSWORD);
      await page.fill('input[name="confirmPassword"]', NEW_PASSWORD);
      await page.click(SUBMIT);
      await expect(page.getByTestId('reset-password-done')).toBeVisible();
    });

    await test.step('舊密碼不能再登入，新密碼可以', async () => {
      await loginOnly(page, email, password);
      expect(page.url(), '重設後舊密碼必須立刻失效').toContain('/login');

      await loginOnly(page, email, NEW_PASSWORD);
      await expect.poll(() => page.url(), { timeout: 10000 }).not.toContain('/login');
    });

    await test.step('同一條連結第二次使用 → 顯示無效或已過期', async () => {
      await page.evaluate(() => localStorage.clear());
      await page.goto(`/reset-password?token=${token}`);
      await page.fill('input[name="password"]', 'Another789!');
      await page.fill('input[name="confirmPassword"]', 'Another789!');
      await page.click(SUBMIT);
      await expect(page.getByTestId('reset-password-invalid-link')).toBeVisible();
    });
  });

  test('沒有 token 的重設頁直接顯示無效連結，並提供重新申請的入口', async ({ page }) => {
    await page.goto('/reset-password');
    await expect(page.getByTestId('reset-password-invalid-link')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Request a new link' })).toBeVisible();
  });

  test('未註冊的 Email 看到與已註冊者完全相同的成功畫面（不揭露帳號是否存在）', async ({ page }) => {
    await page.goto('/forgot-password');
    await page.fill('input[name="email"]', `nobody-${Date.now()}@example.com`);
    await page.click(SUBMIT);
    await expect(page.getByTestId('forgot-password-submitted')).toContainText(
      "If that email is registered, we've sent a link to reset your password."
    );
  });
});

test.describe('AT-M03-007: Email 驗證與開店申請前置條件', () => {
  test('未驗證 Email 的已登入會員不能開店；驗證之後就可以', async ({ page }) => {
    const { email } = await registerAndLogin(page);

    await test.step('未驗證 → 被擋下並提供重寄入口', async () => {
      await page.goto('/tenant/apply');
      await fillAndSubmitStoreApplication(page);
      await expect(page.getByText('請先驗證電子郵件')).toBeVisible();
      await expect(page.getByRole('button', { name: '重新寄送驗證信' })).toBeVisible();

      await page.getByRole('button', { name: '重新寄送驗證信' }).click();
      await expect(page.getByText('驗證信已寄出')).toBeVisible();
    });

    await test.step('點驗證連結後，同一位會員可以送出申請', async () => {
      await verifyEmailViaMailbox(page, email);
      await page.goto('/tenant/apply');
      await fillAndSubmitStoreApplication(page);
      await page.waitForURL('**/dashboard/tenants**', { timeout: 15000 });
    });
  });

  test('驗證連結無效或已使用 → 顯示無效連結', async ({ page }) => {
    const { email } = await registerAndLogin(page);
    const token = await waitForMailToken(page, email, 'verify-email');

    await page.goto(`/verify-email?token=${token}`);
    await expect(page.getByTestId('verify-email-success')).toBeVisible({ timeout: 15000 });

    // 一次性：同一條連結第二次開啟
    await page.goto(`/verify-email?token=${token}`);
    await expect(page.getByTestId('verify-email-failed')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('verify-email-failed')).toContainText('invalid or has expired');
  });
});
