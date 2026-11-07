import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-DASHBOARD-POST-LIST-REAL: 貼文管理列表的分頁（DEF-466，Sprint 332，真實後端）
 *
 * 背景：/dashboard/posts 原本沒有分頁，只看得到後端預設的第一頁 20 筆，貼文超過 20 篇的店家看不到其餘的（/cms 的列表有分頁）。
 * 合併兩套貼文介面時把分頁帶進 /dashboard/posts。
 *
 * 為什麼用真實後端：「第 2 頁有什麼、總數是多少、刪掉最後一頁唯一的一筆之後停在哪裡」都取決於後端回的
 * totalCount／totalPages 與排序，mock 掉就只是在測自己寫的 mock。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const POST_COUNT = 21; // 一頁 20 筆：21 篇剛好多出 1 筆到第 2 頁

test.describe('AT-DASHBOARD-POST-LIST-REAL: 貼文管理列表分頁（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  const STAMP = Date.now();
  const titleOf = (n: number) => `E2E 分頁貼文 ${STAMP} #${String(n).padStart(2, '0')}`;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 貼文分頁店' });
      owner = seeded.owner;
      // 依序建立：列表依建立時間由新到舊排，所以 #21 在最前面、#01 在第 2 頁
      for (let n = 1; n <= POST_COUNT; n++) {
        const created = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
          headers: await authHeaders(page),
          data: { title: titleOf(n), content: `分頁測試貼文 ${n}` },
        });
        expect(created.status(), await created.text()).toBe(200);
      }
    } finally {
      await page.close();
    }
  });

  const rows = (page: Page) => page.locator('tbody tr');

  test('E2E-POST-LIST-01: 21 篇貼文 → 第 1 頁 20 筆、顯示總數與分頁；第 2 頁只有最舊的那 1 篇', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/posts');

    await expect(rows(page)).toHaveCount(20);
    await expect(page.getByText(`共 ${POST_COUNT} 篇`)).toBeVisible();
    await expect(page.getByRole('row').filter({ hasText: titleOf(POST_COUNT) }), '最新的在第 1 頁').toBeVisible();
    await expect(page.getByRole('row').filter({ hasText: titleOf(1) }), '最舊的不在第 1 頁').toHaveCount(0);

    const nav = page.getByRole('navigation', { name: '分頁' });
    await expect(nav).toBeVisible();
    await nav.getByRole('button', { name: '2', exact: true }).click();

    await expect(rows(page)).toHaveCount(1);
    await expect(page.getByRole('row').filter({ hasText: titleOf(1) })).toBeVisible();
  });

  test('E2E-POST-LIST-02: 刪掉最後一頁唯一的一筆 → 退回前一頁（不停在沒有分頁控制的空頁）', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/posts');
    await page.getByRole('navigation', { name: '分頁' }).getByRole('button', { name: '2', exact: true }).click();
    await expect(rows(page)).toHaveCount(1);

    // 草稿才能刪（列表原有的規則）；播種的貼文都是草稿
    await page.getByRole('row').filter({ hasText: titleOf(1) }).getByRole('button', { name: '刪除' }).click();
    await page.getByTestId('confirm-dialog-confirm').click();

    // 刪掉之後總共 20 篇、剛好一頁：畫面回到 20 筆的第 1 頁，而不是「尚無貼文」
    await expect(page.getByText(`共 ${POST_COUNT - 1} 篇`)).toBeVisible();
    await expect(rows(page)).toHaveCount(20);
    await expect(page.getByText('尚無貼文')).toHaveCount(0);
  });
});
