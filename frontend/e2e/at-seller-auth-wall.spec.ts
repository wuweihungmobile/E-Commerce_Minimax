import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';

/**
 * AT-SELLER-AUTH-WALL: 賣家外殼（/dashboard、/admin）的登入守門（真實瀏覽器，不 mock）—— Sprint 331
 * （原本還涵蓋 /cms；Sprint 332 的 DEF-466 把 /cms 併進 /dashboard 並移除，下面改測貼文管理與媒體中心。）
 *
 * 為什麼需要：Sprint 331 之前，登入守門寫在 37 個 dashboard 頁面各自的 useEffect 裡，/admin 與 /cms 的頁面（除了 /admin 首頁）完全沒有。
 * 沒登入的人開 /admin/tenants 會先看到整頁、先發出必然 401 的請求，再由 axios 攔截器的 refresh 失敗路徑整頁導去登入頁。
 * 守門移到外殼 SellerShell（與買家頁面的 AuthGuard 同一個元件，DEF-413）之後：
 *   ① 沒登入 → 導去 /login?redirect=<原頁>，而且**不發任何受保護的請求**（頁面內容根本不渲染）；
 *   ② 已登入的人整頁重新載入不被誤踢（hydration 當下的登入快照一律是「未登入」，守門必須讀當下的 token）。
 *
 * 登入密度：本檔 1 次表單登入（管理員，由 Flyway 種子＋E2E 專用密碼）。
 */

const WEB_BASE = 'http://localhost:3000';
const ADMIN = { email: 'admin@nextkey.local', password: 'Test123!' };

/** 沒登入的訪客開這些頁面，都應該被導去登入頁。涵蓋兩個區域的首頁、列表頁、巢狀路徑。 */
const PROTECTED: string[] = [
  '/dashboard',
  '/dashboard/products',
  '/dashboard/orders',
  '/dashboard/erp/suppliers',
  '/dashboard/posts',
  '/dashboard/media',
  '/admin',
  '/admin/tenants',
  '/admin/audit-logs',
];

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

async function openAsVisitor(page: Page, path: string) {
  await page.goto('/login');
  await page.evaluate(() => localStorage.clear());
  await page.goto(path);
}

test.describe('AT-SELLER-AUTH-WALL: 沒登入的訪客開賣家外殼的頁面 → 導去登入頁並記住原頁', () => {
  for (const path of PROTECTED) {
    test(`E2E-SWALL-01: 沒登入開 ${path} → 被導去 /login?redirect=…，而且沒對後端 API 發出任何請求`, async ({ page }: { page: Page }) => {
      const apiRequests: string[] = [];
      page.on('request', (request) => {
        if (request.url().startsWith(API_BASE)) apiRequests.push(`${request.method()} ${new URL(request.url()).pathname}`);
      });

      await openAsVisitor(page, path);

      await expect(page).toHaveURL(`${WEB_BASE}/login?redirect=${encodeURIComponent(path)}`, { timeout: 20000 });
      await expect(page.getByRole('heading', { name: /Sign in/i })).toBeVisible();
      // 修復前：/admin/* 與 /cms/*（已於 Sprint 332 移除）的頁面先渲染、先打 API（401），才被攔截器導走
      expect(apiRequests, '沒登入不得對後端 API 發出任何請求（頁面內容根本不該渲染）').toEqual([]);
    });
  }
});

test.describe('AT-SELLER-AUTH-WALL: 已登入的人不被守門誤踢', () => {
  test('E2E-SWALL-02: 管理員登入後開 /admin/audit-logs 看得到頁面；整頁重新載入仍留在原頁', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN.email, ADMIN.password);

    await page.goto('/admin/audit-logs');
    await expect(page.getByRole('heading', { name: '稽核紀錄' })).toBeVisible({ timeout: 20000 });

    // 整頁重新載入：hydration 當下的登入快照是「未登入」，守門必須讀當下的 token，不能把已登入的人踢走
    await page.reload();
    await expect(page.getByRole('heading', { name: '稽核紀錄' })).toBeVisible({ timeout: 20000 });
    await page.waitForLoadState('networkidle');
    expect(new URL(page.url()).pathname, '已登入的人整頁重新載入不得被導去登入頁').toBe('/admin/audit-logs');
  });
});
