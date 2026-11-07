import { test, expect, Page } from '@playwright/test';

/**
 * AT-CMS-REDIRECT: 已移除的 /cms 網址轉到對應的新頁面（Sprint 332，DEF-466）
 *
 * 背景：/cms 是早期的內容管理後台，與 /dashboard/posts、/dashboard/media 是兩套並行的貼文／媒體介面。
 * 合併之後 /cms 的頁面全部移除，next.config.ts 的 redirects() 把舊網址逐一轉到對應的新頁面——
 * 專案內沒有任何連結指向 /cms，會用到轉址的只有書籤與手打的網址。
 *
 * 為什麼用請求層驗（不開瀏覽器、不登入）：轉址在 Next 的設定層就發生，在登入守門之前；
 * 只看狀態碼與 Location 才能確定「是轉址表做的事」，不被後面的登入導向混在一起。最後一個案例才走完整瀏覽器流程。
 */

const WEB_BASE = 'http://localhost:3000';
const POST_ID = '3f2b8c1e-0000-4000-8000-000000000001';

const REDIRECTS: Array<[string, string]> = [
  ['/cms', '/dashboard/posts'],
  ['/cms/posts/new', '/dashboard/posts/new'],
  [`/cms/posts/${POST_ID}/edit`, `/dashboard/posts/${POST_ID}/edit`],
  ['/cms/media', '/dashboard/media'],
];

test.describe('AT-CMS-REDIRECT: /cms 舊網址 → 對應的新頁面', () => {
  for (const [from, to] of REDIRECTS) {
    test(`E2E-CMS-REDIR-01: ${from.replace(POST_ID, ':id')} → 308 ${to.replace(POST_ID, ':id')}`, async ({ request }) => {
      const response = await request.get(from, { maxRedirects: 0 });
      expect(response.status(), '搬家是定案，用 308').toBe(308);
      expect(new URL(response.headers()['location'], WEB_BASE).pathname).toBe(to);
    });
  }

  test('E2E-CMS-REDIR-02: 沒有列在轉址表的 /cms/* 仍是 404——不是統一轉走，免得打錯網址的人被默默丟到貼文列表', async ({ request }) => {
    const response = await request.get('/cms/does-not-exist', { maxRedirects: 0 });
    expect(response.status()).toBe(404);
  });

  test('E2E-CMS-REDIR-03: 沒登入開 /cms → 轉到 /dashboard/posts → 登入守門導去登入頁並記住 /dashboard/posts', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await page.goto('/cms');
    await expect(page).toHaveURL(`${WEB_BASE}/login?redirect=${encodeURIComponent('/dashboard/posts')}`, { timeout: 20000 });
  });
});
