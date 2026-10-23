import { test, expect, Page } from '@playwright/test';
import { Account } from './helpers/store';

/**
 * AT-BUYER-AUTH-WALL: 買家頁面的登入守門（真實瀏覽器，不 mock）—— Sprint 302（DEF-413）
 *
 * 為什麼需要：Sprint 301 以真實瀏覽器、乾淨 context、完全沒有 token 逐頁量買家頁面，9 頁只有 /cart 會把沒登入的人導去登入頁；
 * 其餘停在原頁，顯示「無法載入…請稍後再試」（/returns 還露出後端原文 Authentication required；/account、/checkout 整頁照常渲染）。
 * 使用者的決定：在買家頁面共用的版面層加登入守門（沒 token 就導去 /login?redirect=<原頁>，登入後回原頁），公開頁面排除，
 * /checkout 也需要登入。
 *
 * 守門有兩個容易寫壞的地方，這支規格各自守住：
 * - 整頁重新載入時 hydration 當下的登入快照一律是「未登入」——若守門用它判斷，已登入的人會被誤踢（WALL-03 的 reload 步驟）。
 * - 登出時 token 被清掉——守門若對登入狀態的變化做反應，會與登出自己的 router.push('/login') 搶，把網址變成
 *   /login?redirect=…（由 at-login-landing-real 的 E2E-LOGIN-04 與 at-buyer-pages 的 E2E-BUYER-05 守住）。
 *
 * 登入密度：本檔 1 次表單登入（買家以 API 註冊）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PASSWORD = 'Test123!';

/** 沒登入的訪客開這些頁面，都應該被導去登入頁（含 /checkout，使用者決定需登入）。 */
const PROTECTED: string[] = [
  '/orders',
  '/orders?tab=returns',
  '/orders/00000000-0000-0000-0000-000000000000',
  '/addresses',
  '/notifications',
  '/bookings',
  '/returns',
  '/account',
  '/checkout',
  '/support/tickets',
  '/auth/profile',
];

/** 認證流程頁面：不需要登入，也不能被導去登入頁（否則登入頁自己就繞圈）。第二欄是該頁的標題，用來證明內容真的有顯示。 */
const PUBLIC: Array<[string, RegExp]> = [
  ['/login', /Sign in/i],
  ['/register', /Create your account/i],
  ['/forgot-password', /Reset your password/i],
  ['/reset-password', /Choose a new password/i],
  ['/verify-email', /Verify your email/i],
];

/** 填登入表單並送出（不導航：呼叫端已經在要登入的那個網址上，不能再 goto('/login') 把 ?redirect= 洗掉）。 */
async function fillAndSubmitLogin(page: Page, account: Account) {
  await page.getByLabel(/email/i).fill(account.email);
  await page.locator('input[type="password"]').fill(account.password);
  await page.getByRole('button', { name: /sign in/i }).click();
}

async function openAsVisitor(page: Page, path: string) {
  // 乾淨 context 本來就沒有 token；先落在公開頁清一次 localStorage，避免上一案例遺留
  await page.goto('/login');
  await page.evaluate(() => localStorage.clear());
  await page.goto(path);
}

test.describe('AT-BUYER-AUTH-WALL: 沒登入的訪客開買家頁面 → 導去登入頁並記住原頁', () => {
  for (const path of PROTECTED) {
    test(`E2E-WALL-01: 沒登入開 ${path} → 被導去 /login?redirect=…`, async ({ page }: { page: Page }) => {
      await openAsVisitor(page, path);

      // 修復前：停在原頁，顯示「無法載入…請稍後再試」或整頁照常渲染
      await expect(page).toHaveURL(`${WEB_BASE}/login?redirect=${encodeURIComponent(path)}`, { timeout: 20000 });
      await expect(page.getByRole('heading', { name: /Sign in/i })).toBeVisible();
    });
  }
});

test.describe('AT-BUYER-AUTH-WALL: 對照——認證流程的公開頁面不被擋', () => {
  for (const [path, heading] of PUBLIC) {
    test(`E2E-WALL-02: 沒登入開 ${path} → 停在原頁，不導向，內容照常顯示`, async ({ page }: { page: Page }) => {
      await openAsVisitor(page, path);
      // 守門的效果在 hydration 之後才發生；等網路安靜再量，才證明「沒被導走」而不是「還沒來得及導」
      await page.waitForLoadState('networkidle');

      expect(new URL(page.url()).pathname, '公開頁面不得被導去別處').toBe(path);
      expect(new URL(page.url()).search, '公開頁面不得被加上 ?redirect=').not.toContain('redirect=');
      await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    });
  }
});

test.describe('AT-BUYER-AUTH-WALL: 真實登入後回到原頁，整頁重新載入也不被誤踢', () => {
  let buyer: Account;

  test.beforeAll(async ({ request }) => {
    const email = `e2e-wall-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@example.com`;
    const register = await request.post(`${API_BASE}/v2/auth/register`, {
      data: { email, password: PASSWORD, fullName: 'E2E 登入守門買家' },
    });
    expect(register.status(), await register.text()).toBeLessThan(300);
    buyer = { email, password: PASSWORD };
  });

  test('E2E-WALL-03: 沒登入開 /orders → 導去登入 → 登入後回到 /orders 並看到訂單頁；整頁重新載入仍留在 /orders', async ({ page }: { page: Page }) => {
    await openAsVisitor(page, '/orders');
    await expect(page).toHaveURL(`${WEB_BASE}/login?redirect=%2Forders`, { timeout: 20000 });

    await fillAndSubmitLogin(page, buyer);
    await expect(page).toHaveURL(`${WEB_BASE}/orders`, { timeout: 20000 });
    await expect(page.getByRole('heading', { name: '我的訂單' })).toBeVisible();
    await expect(page.getByText('無法載入訂單')).toHaveCount(0);

    // 整頁重新載入：hydration 當下的登入快照一律是「未登入」，守門必須讀當下的 token，不能把已登入的人踢走
    await page.reload();
    await expect(page.getByRole('heading', { name: '我的訂單' })).toBeVisible({ timeout: 20000 });
    await page.waitForLoadState('networkidle');
    expect(new URL(page.url()).pathname, '已登入的人整頁重新載入不得被導去登入頁').toBe('/orders');
  });
});
