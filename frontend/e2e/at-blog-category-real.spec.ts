import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { loginOnly } from './helpers/auth';
import { Account, adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-BLOG-CATEGORY-REAL: 部落格分類篩選頁 /blog/category/[id]（DEF-358(c)，Sprint 256，真實後端）
 *
 * PRD §10.1.1 登記的缺口：`/blog`、文章頁皆已實作，僅分類篩選頁缺席。本規格驗證新頁面
 * 與新增的 `GET /v2/posts/categories`（公開，僅回傳啟用中分類）、`GET /v2/posts?categoryId=` 兩個
 * 後端端點串接後的真實行為——不 mock，真實註冊/開店/建分類/建貼文。
 *
 * `/blog/category/[id]` 與既有 `/blog` 同屬一組既有慣例：tenantId 來自
 * localStorage（非 URL），所以每個 test 都要先以 loginOnly 讓瀏覽器真正登入過一次（登入會把
 * tenantId 寫入 localStorage，見 services/auth.ts），而不是用匿名 page。
 * （文章頁自 Sprint 336 起網址帶店鋪、匿名可讀，不在這組慣例裡了，見 at-article-titles-real。）
 *
 * Sprint 337（DEF-486）：文章頁的分類徽章改連「店鋪前台貼文列表的公開版分類篩選」（/stores/{店鋪}/blog?category=），
 * 不再連到這一頁，所以原本的 E2E-BLOGCAT-02（徽章 → 這一頁）移到 at-store-blog-category-real 的 E2E-STOREBLOGCAT-03
 * （改成匿名訪客、導向新的篩選）。這一頁（登入者、tenantId 取自 localStorage）保留，目前沒有任何連結指向它。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

async function createCategory(page: Page, name: string): Promise<string> {
  const response = await page.request.post(`${API_BASE}/v2/dashboard/post-categories`, {
    headers: await authHeaders(page),
    data: { name, description: `${name} 的分類描述` },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).data.id as string;
}

async function createPublishedPost(page: Page, title: string, categoryId: string): Promise<string> {
  const create = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
    headers: await authHeaders(page),
    data: { title, content: `${title} 的內容`, categoryId },
  });
  expect(create.status(), await create.text()).toBe(200);
  const post = (await create.json()).data as { id: string; slug: string };
  const publish = await page.request.post(`${API_BASE}/v2/dashboard/posts/${post.id}/publish`, {
    headers: await authHeaders(page),
  });
  expect(publish.status(), await publish.text()).toBe(200);
  return post.slug;
}

test.describe('AT-BLOG-CATEGORY-REAL: 部落格分類篩選頁（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let storeSlug: string;
  let tenantId: string;
  let categoryAId: string;
  let categoryASlugPost: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage();
    try {
      ({ owner, storeSlug, tenantId } = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 部落格分類店',
      }));
    } finally {
      await page.close();
    }
  });

  test('E2E-BLOGCAT-01: 分類頁只顯示該分類下的已發布貼文，不含其他分類', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);

    categoryAId = await createCategory(page, `分類 A ${Date.now()}`);
    const categoryBId = await createCategory(page, `分類 B ${Date.now()}`);
    categoryASlugPost = await createPublishedPost(page, `分類A的文章 ${Date.now()}`, categoryAId);
    const postBSlug = await createPublishedPost(page, `分類B的文章 ${Date.now()}`, categoryBId);
    void postBSlug;

    await page.goto(`/blog/category/${categoryAId}`);
    await expect(page.getByTestId('blog-category-title')).toBeVisible();
    await expect(page.getByTestId('blog-category-grid')).toContainText('分類A的文章');
    await expect(page.getByTestId('blog-category-grid')).not.toContainText('分類B的文章');
    // 分類頁是產生文章連結的三個地方之一：連結要指向帶店鋪的新網址（Sprint 336，DEF-483），不是舊的 /blog/{文章}
    await expect(page.getByTestId('blog-category-grid').getByRole('link', { name: /分類A的文章/ })).toHaveAttribute(
      'href',
      `/stores/${storeSlug}/blog/${categoryASlugPost}`
    );
  });

  // E2E-BLOGCAT-02 已移到 at-store-blog-category-real 的 E2E-STOREBLOGCAT-03（見檔頭說明，Sprint 337，DEF-486）

  test('E2E-BLOGCAT-03: 不存在的分類 ID → 顯示「此分類不存在」而非崩潰', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);

    await page.goto(`/blog/category/${randomUUID()}`);
    await expect(page.getByTestId('blog-category-not-found')).toBeVisible();
  });

  test('E2E-BLOGCAT-04: 存在但尚無已發布貼文的分類 → 顯示空狀態而非「不存在」', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);

    const emptyCategoryId = await createCategory(page, `空分類 ${Date.now()}`);

    await page.goto(`/blog/category/${emptyCategoryId}`);
    await expect(page.getByTestId('blog-category-title')).toBeVisible();
    await expect(page.getByTestId('blog-category-empty')).toBeVisible();
  });

  test('E2E-BLOGCAT-05: 店鋪被停權後，店主的平台層 /blog 顯示「載入文章失敗」（不是空清單、不是舊貼文）；恢復後又看得到貼文（DEF-485）', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    const adminToken = await adminAccessToken(page);

    // 對照組：營業中的店鋪，平台層 /blog 列得出貼文
    await page.goto('/blog');
    await expect(page.getByText(/分類A的文章/).first()).toBeVisible();

    // 這一頁的 tenantId 取自瀏覽器 localStorage，登入後就留在那裡；店鋪被停權後，公開貼文 API 對它回 404（DEF-485），
    // 頁面把失敗畫成失敗（錯誤與重新載入），而不是「尚無已發布的文章」——使用者分不出「真的沒有」與「出錯」是 DEF-468 的問題
    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    try {
      await page.goto('/blog');
      await expect(page.getByTestId('blog-error')).toBeVisible();
      await expect(page.getByText('尚無已發布的文章')).toHaveCount(0);
      await expect(page.getByText(/分類A的文章/)).toHaveCount(0);
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }

    await page.goto('/blog');
    await expect(page.getByText(/分類A的文章/).first()).toBeVisible();
  });
});
