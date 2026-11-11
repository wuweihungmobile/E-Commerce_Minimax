import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-POST-RULES-REAL: 貼文網址（slug）與刪除規則（DEF-480、DEF-476，Sprint 334，真實後端）
 *
 * 兩件事都是「畫面、前端判斷、後端規則」要一起成立才算數，所以不 mock：
 *
 * 1. DEF-480：全中文標題的貼文 slug 曾是空字串（平台第一篇）或 `-<8碼>`；空字串讓部落格清單的連結變成 `/blog`，
 *    點下去回到清單本身，文章頁進不去。先前所有真實後端 E2E 的標題都含英文，從沒走過這條路徑。
 *    標題故意用「全中文」——數字也轉成中文數字，否則 `[^a-z0-9]` 會留下阿拉伯數字，slug 就不會是空的。
 * 2. DEF-476（使用者決定「要先下架才能刪」）：已發布的貼文，列表與編輯頁的刪除鈕都不可按、後端回 409 E-4107；
 *    下架之後才能刪。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const STAMP = Date.now();
// 0-9 → 〇一二三四五六七八九：讓標題每次執行都不同，卻仍是「全中文」
const zh = (n: number) => String(n).replace(/\d/g, (d) => '〇一二三四五六七八九'[Number(d)]);

interface CreatedPost { id: string; slug: string; }

async function createPost(page: Page, title: string, publish: boolean): Promise<CreatedPost> {
  const created = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
    headers: await authHeaders(page),
    data: { title, content: `${title} 的內容` },
  });
  expect(created.status(), await created.text()).toBe(200);
  const post = (await created.json()).data as CreatedPost;
  if (publish) {
    const published = await page.request.post(`${API_BASE}/v2/dashboard/posts/${post.id}/publish`, {
      headers: await authHeaders(page),
    });
    expect(published.status(), await published.text()).toBe(200);
  }
  return post;
}

test.describe('AT-POST-RULES-REAL: 貼文 slug 與刪除規則（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 120_000 });

  let owner: Account;
  let storeSlug: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ owner, storeSlug } = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 貼文規則店' }));
    } finally {
      await page.close();
    }
  });

  test('E2E-POSTRULE-01: 全中文標題的貼文有可用的網址：店鋪貼文列表的連結是 /stores/{店鋪}/blog/post-<8碼>，點得進文章頁；兩篇網址不同', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    const titleA = `視覺檢查貼文${zh(STAMP)}甲`;
    const titleB = `視覺檢查貼文${zh(STAMP)}乙`;
    const a = await createPost(page, titleA, true);
    const b = await createPost(page, titleB, true);

    expect(a.slug, 'DEF-480：全中文標題的 slug 不能是空字串').toMatch(/^post-[0-9a-f]{8}$/);
    expect(b.slug).toMatch(/^post-[0-9a-f]{8}$/);
    expect(a.slug, '兩篇的網址不同').not.toBe(b.slug);

    // 用這間店鋪自己的貼文列表（不是平台層 /blog：Sprint 339 起 /blog 是跨店鋪的列表，全套並行時「剛建的一定在第一頁」不成立）
    await page.goto(`/stores/${storeSlug}/blog`);
    const linkA = page.locator(`a[href^="/stores/${storeSlug}/blog/post-"]`, { hasText: titleA });
    await expect(linkA, '清單裡的連結要指向這間店鋪的文章頁，而不是 /blog').toHaveAttribute(
      'href',
      `/stores/${storeSlug}/blog/${a.slug}`
    );

    await linkA.click();
    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog/${a.slug}$`));
    // 店鋪外殼的 h1 是店名，文章標題是 h2（Sprint 336，DEF-483）
    await expect(page.getByRole('heading', { level: 2, name: titleA })).toBeVisible();
  });

  test('E2E-POSTRULE-02: 編輯頁照舊儲存（標題沒改）不會換網址；標題真的改了才換', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    const title = `Slug Stability ${STAMP}`;
    const post = await createPost(page, title, true);

    await page.goto(`/dashboard/posts/${post.id}/edit`);
    const titleInput = page.locator('form input[type="text"]').first();
    await expect(titleInput).toHaveValue(title);

    // 只改內容、標題照舊，連續存兩次（編輯頁每次儲存都會把標題一起送出）
    for (const text of ['第一次改內容', '第二次改內容']) {
      await page.locator('form textarea').first().fill(text);
      const saved = page.waitForResponse(
        (r) => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith(`/v2/dashboard/posts/${post.id}`)
      );
      await page.locator('form button[type="submit"]').click();
      expect((await saved).status()).toBe(200);
      await expect(page.locator('form button[type="submit"]')).toBeEnabled();
    }

    const after = await page.request.get(`${API_BASE}/v2/dashboard/posts/${post.id}`, { headers: await authHeaders(page) });
    expect((await after.json()).data.slug, '標題沒改，已發布貼文的網址不能變（分享出去的連結會失效）').toBe(post.slug);

    // 標題真的改了才換
    await titleInput.fill(`Renamed Post ${STAMP}`);
    const renamed = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith(`/v2/dashboard/posts/${post.id}`)
    );
    await page.locator('form button[type="submit"]').click();
    expect((await renamed).status()).toBe(200);
    const afterRename = await page.request.get(`${API_BASE}/v2/dashboard/posts/${post.id}`, { headers: await authHeaders(page) });
    expect((await afterRename.json()).data.slug).toMatch(/^renamed-post-/);
  });

  test('E2E-POSTRULE-03: 後端契約——已發布的貼文用 API 直接刪除 → 409 E-4107，貼文仍在', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    const post = await createPost(page, `E2E 刪除規則 API ${STAMP}`, true);

    const denied = await page.request.delete(`${API_BASE}/v2/dashboard/posts/${post.id}`, { headers: await authHeaders(page) });
    expect(denied.status(), await denied.text()).toBe(409);
    expect((await denied.json()).code).toBe('E-4107');

    const still = await page.request.get(`${API_BASE}/v2/dashboard/posts/${post.id}`, { headers: await authHeaders(page) });
    expect(still.status(), '被擋下的刪除不能有副作用').toBe(200);
  });

  test('E2E-POSTRULE-04: 貼文列表——已發布的貼文刪除鈕不可按，草稿的可按', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    const published = `E2E 列表已發布 ${STAMP}`;
    const draft = `E2E 列表草稿 ${STAMP}`;
    await createPost(page, published, true);
    await createPost(page, draft, false);

    await page.goto('/dashboard/posts');
    const publishedRow = page.getByRole('row').filter({ hasText: published });
    const draftRow = page.getByRole('row').filter({ hasText: draft });
    await expect(publishedRow.getByRole('button', { name: '刪除' })).toBeDisabled();
    await expect(draftRow.getByRole('button', { name: '刪除' })).toBeEnabled();
  });

  test('E2E-POSTRULE-05: 編輯頁——已發布時刪除鈕不可按；按「下架」後可按，刪除成功回到列表且貼文真的不在了', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    const post = await createPost(page, `E2E 編輯頁刪除 ${STAMP}`, true);

    await page.goto(`/dashboard/posts/${post.id}/edit`);
    const deleteButton = page.locator('form').getByRole('button', { name: '刪除', exact: true });
    await expect(deleteButton, '已發布 → 要先下架').toBeDisabled();

    await page.locator('form').getByRole('button', { name: '下架', exact: true }).click();
    await expect(deleteButton, '下架後草稿 → 可以刪').toBeEnabled();

    await deleteButton.click();
    await page.getByTestId('confirm-dialog-confirm').click();
    await expect(page).toHaveURL(/\/dashboard\/posts$/);

    const gone = await page.request.get(`${API_BASE}/v2/dashboard/posts/${post.id}`, { headers: await authHeaders(page) });
    expect(gone.status(), '貼文應已被刪除').toBe(404);
  });
});
