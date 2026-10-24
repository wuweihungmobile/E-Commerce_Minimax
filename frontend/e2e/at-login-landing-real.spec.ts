import { test, expect, Page } from '@playwright/test';
import { postAuthApi } from './helpers/auth';
import { Account, seedStore } from './helpers/store';

/**
 * AT-LOGIN-LANDING-REAL: 登入後的落地頁與「回到原頁」（真實後端，走真實登入表單）—— Sprint 301（DEF-409）
 *
 * 為什麼需要：登入頁原本對所有角色一律 `router.push('/dashboard')`，一般買家登入後落在賣家後台、看到「無法載入儀表板資料」
 * 與一排 403；被 401／沒有 token 踢來登入的人登入後也不會回到原頁。既有 E2E 的登入輔助函式雖然走真實登入表單，但只等「離開
 * /login」、從不看落地頁，所以沒人看過這個畫面（Sprint 300 以真實資料掃描時才發現）。
 *
 * 使用者在 Sprint 300 收尾的決定：依角色分流（BUYER→`/`、店家類→`/dashboard`、管理員→`/admin`），並支援同源的 `?redirect=`
 * （只接受站內路徑，防開放轉址）。`?redirect=` 的各種惡意輸入以純函式表格式測試窮舉（at-auth-redirect.spec.ts），
 * 這裡只守「登入頁真的用了它、導向真的生效」與角色落地頁。
 *
 * 登入密度：本檔約 4 次表單登入＋開店前提 3 次（買家改以 API 註冊，不多登入一次）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PASSWORD = 'Test123!';
const ADMIN: Account = { email: 'admin@nextkey.local', password: PASSWORD };

/** 填登入表單並送出（不導航；呼叫端決定是否先 goto）。 */
async function fillAndSubmitLogin(page: Page, account: Account) {
  await page.getByLabel(/email/i).fill(account.email);
  await page.locator('input[type="password"]').fill(account.password);
  await page.getByRole('button', { name: /sign in/i }).click();
}

async function gotoLogin(page: Page, query = '') {
  await page.goto(`/login${query}`);
  await page.evaluate(() => localStorage.clear());
}

test.describe('AT-LOGIN-LANDING-REAL: 登入落地頁（真實登入表單）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let buyer: Account;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ owner } = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 登入導向店' }));
      // 一般買家（沒有店鋪）改以 API 註冊：少一次表單登入，登入端點有 IP 限流
      const email = `e2e-landing-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@example.com`;
      const register = await postAuthApi(page.request, `${API_BASE}/v2/auth/register`, {
        email,
        password: PASSWORD,
        fullName: 'E2E 登入導向買家',
      });
      expect(register.status(), await register.text()).toBeLessThan(300);
      buyer = { email, password: PASSWORD };
    } finally {
      await page.close();
    }
  });

  test('E2E-LOGIN-01: 一般買家登入 → 落在商店首頁，不是賣家後台；惡意的 ?redirect= 被忽略', async ({ page }: { page: Page }) => {
    // 帶一個開放轉址的惡意參數：必須被忽略，改用角色預設落地頁
    await gotoLogin(page, '?redirect=%2F%2Fevil.example.com');
    await fillAndSubmitLogin(page, buyer);

    // 修復前：一律 /dashboard（賣家後台）
    await expect(page).toHaveURL(`${WEB_BASE}/`, { timeout: 20000 });
    expect(new URL(page.url()).origin, '不得被導去外部網站').toBe(WEB_BASE);
    await expect(page.getByText('無法載入儀表板資料')).toHaveCount(0);
  });

  test('E2E-LOGIN-02: 店主登入 → 進賣家後台 /dashboard', async ({ page }: { page: Page }) => {
    await gotoLogin(page);
    await fillAndSubmitLogin(page, owner);

    await expect(page).toHaveURL(`${WEB_BASE}/dashboard`, { timeout: 20000 });
  });

  test('E2E-LOGIN-03: 管理員登入 → 進平台管理台 /admin', async ({ page }: { page: Page }) => {
    await gotoLogin(page);
    await fillAndSubmitLogin(page, ADMIN);

    // 修復前：一律 /dashboard（賣家後台）
    await expect(page).toHaveURL(`${WEB_BASE}/admin`, { timeout: 20000 });
  });

  test('E2E-LOGIN-04: 未登入開需要登入的頁面 → 被帶去登入並記住原頁；登入後回到原頁；登出不帶回原頁', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());

    // 未登入開購物車：被導去登入頁，網址記住原頁
    await page.goto('/cart');
    await expect(page).toHaveURL(`${WEB_BASE}/login?redirect=%2Fcart`, { timeout: 20000 });

    await fillAndSubmitLogin(page, buyer);
    // 修復前：落在 /dashboard
    await expect(page).toHaveURL(`${WEB_BASE}/cart`, { timeout: 20000 });

    // 對照：登出是離開，不該帶 ?redirect=（否則下一個登入的人會被送回前一個人的頁面）
    await page.getByText('登出', { exact: true }).click();
    await expect(page).toHaveURL(`${WEB_BASE}/login`, { timeout: 20000 });
  });
});

// 獨立的 describe（不是 serial）：上面任何一案失敗都不該讓這案「never run」而看不到它自己的紅燈
test.describe('AT-LOGIN-LANDING-REAL: 登入狀態失效時回到原頁（axios 401 攔截器）', () => {
  test('E2E-LOGIN-05: 手上的 token 已失效且刷新也失敗 → 整頁被導去登入頁，網址記住原頁', async ({ page }: { page: Page }) => {
    // 攔截器只在「有 refreshToken 但刷新失敗」時才整頁導去登入頁；用後端不認得的 token 即可穩定重現，不需要登入
    await page.goto('/login');
    await page.evaluate(() => {
      localStorage.clear();
      localStorage.setItem('accessToken', 'bogus.bogus.bogus');
      localStorage.setItem('refreshToken', 'bogus-refresh-token');
    });

    // 買家的訂單頁沒有自己的登入牆，載入時直接打 API → 401 → 刷新 401 → 攔截器導去登入頁
    await page.goto('/orders');
    // 修復前：導去沒有任何參數的 /login，登入後回不到剛才的頁面
    await expect(page).toHaveURL(`${WEB_BASE}/login?redirect=%2Forders`, { timeout: 20000 });
    expect(await page.evaluate(() => localStorage.getItem('accessToken')), '失效的 token 應已清掉').toBeNull();
  });
});
