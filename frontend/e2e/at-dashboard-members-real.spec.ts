import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, accessToken, seedStore } from './helpers/store';

/**
 * AT-DASHBOARD-MEMBERS-REAL: 店鋪成員管理 UI（DEF-321 (a)，Sprint 248，真實後端）
 *
 * 後端 7 個成員管理端點（含 Sprint 248 新增的 email 查詢端點）早已存在，但先前全站零前端呼叫點
 * （DEF-321 (a)）。本規格走完整瀏覽器 UI：店主在 /dashboard/members 以 email 查詢並邀請成員、
 * 受邀人在 /account 看到並接受邀請、店主在成員列表看到狀態轉為使用中並可移除。
 */

const WEB_BASE = 'http://localhost:3000';

type Claims = { sub: string };

function claimsOf(token: string): Claims {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8')) as Claims;
}

test.describe('AT-DASHBOARD-MEMBERS-REAL: 店鋪成員管理 UI（真實後端）', () => {
  // 後面的案例依賴前面建立的邀請與成員狀態
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let tenantId: string;
  let invitee: Account;
  let inviteeId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ owner, tenantId } = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 成員管理店' }));
      await page.evaluate(() => localStorage.clear());
      invitee = await registerAndLogin(page);
      inviteeId = claimsOf(await accessToken(page)).sub;
    } finally {
      await page.close();
    }
  });

  test('E2E-MEM-01: 店主以 email 查詢並邀請成員', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/members');

    await page.getByTestId('invite-email-input').fill(invitee.email);
    await page.getByTestId('invite-lookup').click();
    await expect(page.getByText(invitee.email)).toBeVisible();

    await page.getByTestId('invite-confirm').click();
    await expect(page.getByTestId('action-message')).toBeVisible();
    await expect(page.getByText('邀請中')).toBeVisible();
  });

  test('E2E-MEM-02: 受邀人在帳戶頁看到並接受邀請', async ({ page }: { page: Page }) => {
    await loginOnly(page, invitee.email, invitee.password);
    await page.goto('/account');

    const acceptButton = page.getByTestId(`accept-invite-${tenantId}`);
    await expect(acceptButton).toBeVisible();
    await acceptButton.click();
    await expect(acceptButton).toHaveCount(0);
  });

  test('E2E-MEM-03: 店主在成員列表看到狀態轉為使用中，並可移除成員', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/members');

    await expect(page.getByText('使用中').first()).toBeVisible();

    await page.getByTestId(`remove-member-${inviteeId}`).click();
    await page.getByRole('button', { name: '確認' }).click();
    await expect(page.getByTestId('action-message')).toBeVisible();
    await expect(page.getByTestId(`remove-member-${inviteeId}`)).toHaveCount(0);
  });
});
