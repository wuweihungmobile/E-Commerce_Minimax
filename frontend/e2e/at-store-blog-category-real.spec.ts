import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { authHeaders, seedStore } from './helpers/store';

/**
 * AT-STORE-BLOG-CATEGORY-REAL: 店鋪前台貼文列表的公開版分類篩選（DEF-486，Sprint 337，真實後端）
 *
 * 之前文章頁的分類徽章連到 /blog/category/{id}：那一頁的 tenantId 取自瀏覽器 localStorage，只有登入才有，
 * 匿名訪客（包含從公開的店鋪貼文列表點進文章的人）點了只看到「請先登入並設定店鋪以瀏覽部落格」。
 * 現在篩選是店鋪前台貼文列表自己的功能：/stores/{店鋪}/blog?category={分類編號}，店鋪由網址決定，匿名可用。
 *
 * 為什麼用真實後端：分類來自 GET /v2/posts/categories（只回啟用中的分類）、貼文來自 GET /v2/posts?categoryId=&page=，
 * 「只出現該分類的已發布貼文、草稿不外洩、翻頁後分類還在」要連同後端的篩選與分頁一起才算數。
 *
 * 除了建立資料（店主身分）之外，全部以匿名訪客造訪：這是公開頁。
 *
 * 分類目前沒有任何 API 能停用（PostCategoryService 只在建立時設 isActive=true），所以「已停用」的分類只可能來自資料庫直接修改，
 * 這裡無法用真實流程製造；公開端點只回啟用中分類由後端 IT-M15-021 守門，這裡用「不存在的編號」「格式錯誤的編號」驗證對不上的畫面。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const STAMP = Date.now();
// 四個分類名稱只差中文、數字（時間戳）部分相同：網址代稱由名稱產生（中文會被濾掉），所以四個的代稱原本都是同一個時間戳，
// 第二個起會被 409 E-8014 擋下（DEF-492，Sprint 337 寫這份規格時第一次執行就撞到，當時替名稱各加了不同的英文字繞過）。
// Sprint 338 起代稱撞到會自動加後綴，這組名稱本身就是 DEF-492 在真實後端的回歸測試：beforeAll 建得起來才算過。
const TRAVEL = `旅遊筆記${STAMP}`;
const COFFEE = `咖啡手帖${STAMP}`;
const EMPTY = `只有草稿的分類${STAMP}`;
const PAGED = `很多文章的分類${STAMP}`;
const TRAVEL_POST = `旅遊文章${STAMP}`;
const COFFEE_POST = `咖啡文章${STAMP}`;
const PLAIN_POST = `沒有分類的文章${STAMP}`;
const DRAFT_POST = `草稿文章${STAMP}`;
const PAGE_SIZE = 12;

test.describe('AT-STORE-BLOG-CATEGORY-REAL: 店鋪貼文列表的分類篩選（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let storeSlug: string;
  let travelId: string;
  let coffeeId: string;
  let emptyId: string;
  let pagedId: string;
  let travelPostSlug: string;

  const blogUrl = (query = '') => `/stores/${storeSlug}/blog${query}`;

  async function createCategory(page: Page, name: string): Promise<string> {
    const response = await page.request.post(`${API_BASE}/v2/dashboard/post-categories`, {
      headers: await authHeaders(page),
      data: { name, description: `${name} 的分類描述` },
    });
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()).data.id as string;
  }

  async function createPost(page: Page, title: string, categoryId: string | null, publish: boolean): Promise<string> {
    const response = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
      headers: await authHeaders(page),
      data: { title, content: `${title} 的內容`, ...(categoryId ? { categoryId } : {}), autoPublish: publish },
    });
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()).data.slug as string;
  }

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180_000);
    const page = await browser.newPage();
    try {
      ({ storeSlug } = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 分類篩選店' }));
      travelId = await createCategory(page, TRAVEL);
      coffeeId = await createCategory(page, COFFEE);
      emptyId = await createCategory(page, EMPTY);
      pagedId = await createCategory(page, PAGED);

      // 比一頁多一篇：翻頁後分類還在。先建這一批：列表依發布時間新到舊，12 篇一頁，
      // 後面建的幾篇才會留在「全部」的第一頁（先建它們的話，會被這一批擠到第二頁）
      for (let i = 1; i <= PAGE_SIZE + 1; i++) {
        await createPost(page, `分頁文章${String(i).padStart(2, '0')}-${STAMP}`, pagedId, true);
      }
      travelPostSlug = await createPost(page, TRAVEL_POST, travelId, true);
      await createPost(page, COFFEE_POST, coffeeId, true);
      await createPost(page, PLAIN_POST, null, true);
      await createPost(page, DRAFT_POST, emptyId, false); // 分類裡只有草稿：對訪客而言這個分類沒有貼文
    } finally {
      await page.close();
    }
  });

  /** 篩選列上的按鈕（選取態以 aria-pressed 表達） */
  const chip = (page: Page, name: string, pressed?: boolean) =>
    page.getByRole('group', { name: '文章分類' }).getByRole('button', { name, pressed });

  test('E2E-STOREBLOGCAT-01: 匿名訪客開店鋪貼文列表 → 有篩選列（「全部」選取＋各分類），所有貼文都在，沒有登入提示', async ({ page }: { page: Page }) => {
    await page.goto(blogUrl());

    await expect(page.getByTestId('store-blog-grid')).toContainText(TRAVEL_POST);
    await expect(page.getByTestId('store-blog-grid')).toContainText(COFFEE_POST);
    await expect(page.getByTestId('store-blog-grid')).toContainText(PLAIN_POST);
    await expect(page.getByTestId('store-blog-grid')).not.toContainText(DRAFT_POST);
    await expect(chip(page, '全部', true)).toBeVisible();
    for (const name of [TRAVEL, COFFEE, EMPTY, PAGED]) {
      await expect(chip(page, name, false)).toBeVisible();
    }
    await expect(page.getByText('請先登入並設定店鋪以瀏覽部落格')).toHaveCount(0);
  });

  test('E2E-STOREBLOGCAT-02: 點分類 → 網址帶 category、只剩該分類的已發布貼文、該分類是選取態；點「全部」→ 回到全部', async ({ page }: { page: Page }) => {
    await page.goto(blogUrl());
    await expect(page.getByTestId('store-blog-grid')).toContainText(COFFEE_POST);

    await chip(page, TRAVEL).click();

    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog\\?category=${travelId}$`));
    await expect(chip(page, TRAVEL, true)).toBeVisible();
    await expect(chip(page, '全部', false)).toBeVisible();
    await expect(page.getByTestId('store-blog-grid')).toContainText(TRAVEL_POST);
    await expect(page.getByTestId('store-blog-grid')).not.toContainText(COFFEE_POST);
    await expect(page.getByTestId('store-blog-grid')).not.toContainText(PLAIN_POST);
    // 篩選後的文章連結仍是帶店鋪的新網址
    await expect(page.getByTestId('store-blog-grid').getByRole('link', { name: new RegExp(TRAVEL_POST) })).toHaveAttribute(
      'href',
      `/stores/${storeSlug}/blog/${travelPostSlug}`
    );

    await chip(page, '全部').click();

    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog$`));
    await expect(chip(page, '全部', true)).toBeVisible();
    await expect(page.getByTestId('store-blog-grid')).toContainText(COFFEE_POST);
    await expect(page.getByTestId('store-blog-grid')).toContainText(TRAVEL_POST);
  });

  test('E2E-STOREBLOGCAT-03: 匿名訪客在文章頁點分類徽章 → 導向這間店鋪貼文列表的該分類，看得到該分類的貼文（之前是登入提示）', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${storeSlug}/blog/${travelPostSlug}`);
    const badge = page.getByTestId('blog-post-category-link');
    await expect(badge).toHaveText(TRAVEL);
    await expect(badge).toHaveAttribute('href', `/stores/${storeSlug}/blog?category=${travelId}`);

    await badge.click();

    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog\\?category=${travelId}$`));
    await expect(page.getByTestId('store-blog-grid')).toContainText(TRAVEL_POST);
    await expect(page.getByTestId('store-blog-grid')).not.toContainText(COFFEE_POST);
    await expect(chip(page, TRAVEL, true)).toBeVisible();
    await expect(page.getByText('請先登入並設定店鋪以瀏覽部落格')).toHaveCount(0);
  });

  test('E2E-STOREBLOGCAT-04: 分類編號對不上（不存在、格式錯誤）→ 「此分類不存在或已停用」，不送出貼文請求；點「查看全部貼文」回到全部', async ({ page }: { page: Page }) => {
    const postRequests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/v2/posts?') || request.url().endsWith('/v2/posts')) postRequests.push(request.url());
    });

    for (const bogus of [randomUUID(), 'abc']) {
      await page.goto(blogUrl(`?category=${bogus}`));
      await expect(page.getByTestId('store-blog-category-not-found')).toBeVisible();
      await expect(page.getByText('此分類不存在或已停用')).toBeVisible();
      await expect(chip(page, '全部', false)).toBeVisible();
      await expect(chip(page, TRAVEL, false)).toBeVisible();
    }
    expect(postRequests, '對不上的分類不該送出貼文請求（後端對格式錯誤的編號會回 400，畫面會變成「載入失敗」）').toEqual([]);

    await page.getByRole('link', { name: '查看全部貼文' }).click();

    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog$`));
    await expect(page.getByTestId('store-blog-grid')).toContainText(TRAVEL_POST);
  });

  test('E2E-STOREBLOGCAT-05: 分類存在但裡面只有草稿 → 「此分類尚無已發布的貼文」（不是「不存在」），草稿不外洩', async ({ page }: { page: Page }) => {
    await page.goto(blogUrl(`?category=${emptyId}`));

    await expect(page.getByTestId('store-blog-category-empty')).toBeVisible();
    await expect(page.getByText('此分類尚無已發布的貼文')).toBeVisible();
    await expect(page.getByTestId('store-blog-category-not-found')).toHaveCount(0);
    await expect(chip(page, EMPTY, true)).toBeVisible();
    await expect(page.getByText(DRAFT_POST)).toHaveCount(0);
  });

  test('E2E-STOREBLOGCAT-06: 篩選中翻頁 → 網址同時帶 category 與 page，第二頁只有剩下的那一篇，分類仍是選取態', async ({ page }: { page: Page }) => {
    await page.goto(blogUrl(`?category=${pagedId}`));
    await expect(page.getByTestId('store-blog-grid').getByRole('link')).toHaveCount(PAGE_SIZE);

    // 分頁是真正的連結（DEF-500，Sprint 341）：搜尋引擎與不執行 JavaScript 的爬蟲只會沿著 <a href> 走
    await page.getByRole('navigation', { name: '分頁' }).getByRole('link', { name: '2' }).click();

    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog\\?category=${pagedId}&page=2$`));
    await expect(page.getByTestId('store-blog-grid').getByRole('link')).toHaveCount(1);
    await expect(chip(page, PAGED, true)).toBeVisible();
    // 換分類回到第一頁：網址不再帶 page
    await chip(page, TRAVEL).click();
    await expect(page).toHaveURL(new RegExp(`/stores/${storeSlug}/blog\\?category=${travelId}$`));
  });
});
