import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-PLATFORM-BLOG-REAL: 平台層部落格 /blog（跨店鋪，匿名可讀；DEF-495，Sprint 339，真實後端）
 *
 * 使用者在 Sprint 338 收尾時以互動選項選了『做成跨店鋪部落格』（長期方向；Sprint 338 先做了暫時入口）。
 * 之前 /blog 用瀏覽器 localStorage 的店鋪（只有登入才有）取「那一間店鋪」的貼文，匿名訪客只看到登入提示；
 * 現在 /blog 列出所有營業中店鋪（ACTIVE）的已發布貼文，新到舊，匿名可讀（GET /v2/public/posts）。
 *
 * 為什麼用真實後端：「只有已發布、而且店鋪是 ACTIVE 的貼文才出現、停權後立刻消失」是後端查詢與前端畫面一起的行為。
 *
 * 為什麼要先用 API 算出「某篇文章在第幾頁」再開那一頁：這份列表橫跨整個資料庫，E2E 全套並行時別的規格也在建立貼文，
 * 「我剛建的文章一定在第一頁」是不成立的假設；用 API 讀出同樣排序的列表、算出那篇文章的全局位置，再除以畫面的每頁 12 筆得到頁碼，
 * 到瀏覽器看那一頁，才是確定的。為了讓分頁一定存在，乙店建立 13 篇（全列表至少 13 篇＝至少 2 頁）。
 *
 * 為什麼讀列表要一次 50 筆（後端上限）、而且讀到要的貼文就停：後端對每個來源 IP 有 100 次／分的限流桶（RateLimitFilter），
 * E2E 的匿名請求全從同一個 IP 出去。這份列表在累積很多貼文的資料庫裡很長，逐頁（12 筆）掃完整份會打出一串請求，
 * 擠掉同時在跑的其他規格（Sprint 339 第一次執行就看到店鋪層規格被 429）。
 *
 * 全部以匿名訪客造訪（除了建立資料與切換店鋪狀態）：這是公開頁。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const STAMP = Date.now();
const PAGE_SIZE = 12;
const SHOP_B_POSTS = 13;

const CATEGORY_A = `旅遊筆記${STAMP}`;
const TITLE_A1 = `甲店的文章${STAMP}`;
const TITLE_A_DRAFT = `甲店的草稿${STAMP}`;
const titleB = (n: number) => `乙店文章${String(n).padStart(2, '0')}-${STAMP}`;

interface FeedCard {
  title: string;
  slug: string;
  tenantSlug: string;
  tenantName: string;
  categoryName: string | null;
}

/**
 * 依後端同樣的排序讀公開列表，回傳讀到的卡片（依序）。每次 50 筆（後端上限）；`until` 裡的標題都讀到了就停，沒有給就讀到底。
 */
async function readFeed(request: APIRequestContext, until: string[] = []): Promise<FeedCard[]> {
  const cards: FeedCard[] = [];
  for (let page = 0; page < 60; page++) {
    const response = await request.get(`${API_BASE}/v2/public/posts`, { params: { page, size: 50 } });
    expect(response.status(), await response.text()).toBe(200);
    const data = (await response.json()).data as { posts: FeedCard[]; totalPages: number };
    cards.push(...data.posts);
    if (until.length > 0 && until.every((title) => cards.some((card) => card.title === title))) break;
    if (page + 1 >= data.totalPages) break;
  }
  return cards;
}

/** 這篇文章在畫面（每頁 12 筆）的第幾頁（0-based）；讀到的列表裡沒有 → -1。 */
const uiPageOf = (cards: FeedCard[], title: string): number => {
  const index = cards.findIndex((card) => card.title === title);
  return index < 0 ? -1 : Math.floor(index / PAGE_SIZE);
};
const blogUrl = (page: number) => (page === 0 ? '/blog' : `/blog?page=${page + 1}`);

test.describe('AT-PLATFORM-BLOG-REAL: 平台層部落格（跨店鋪，真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let tenantA: string;
  let slugA: string;
  let slugB: string;
  let nameA: string;
  let nameB: string;
  let a1Slug: string;

  async function createPost(page: Page, title: string, categoryId: string | null, publish: boolean): Promise<string> {
    const response = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
      headers: await authHeaders(page),
      data: { title, content: `${title} 的內容`, ...(categoryId ? { categoryId } : {}), autoPublish: publish },
    });
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()).data.slug as string;
  }

  async function storeName(page: Page, slug: string): Promise<string> {
    const store = await page.request.get(`${API_BASE}/v2/public/stores/${slug}`);
    expect(store.status(), await store.text()).toBe(200);
    return (await store.json()).data.name as string;
  }

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    // 兩間店各用自己的瀏覽器分頁：seedStore 結束時該分頁就是那間店店主的登入狀態，authHeaders 取到的是對應的 token
    const pageA = await browser.newPage({ baseURL: WEB_BASE });
    const pageB = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ storeSlug: slugA, tenantId: tenantA } = await seedStore(pageA, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 跨店鋪甲店' }));
      nameA = await storeName(pageA, slugA);
      const category = await pageA.request.post(`${API_BASE}/v2/dashboard/post-categories`, {
        headers: await authHeaders(pageA),
        data: { name: CATEGORY_A, description: `${CATEGORY_A} 的描述` },
      });
      expect(category.status(), await category.text()).toBe(200);
      const categoryId = (await category.json()).data.id as string;
      a1Slug = await createPost(pageA, TITLE_A1, categoryId, true);
      await createPost(pageA, TITLE_A_DRAFT, null, false); // 草稿：不能出現在公開列表

      ({ storeSlug: slugB } = await seedStore(pageB, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 跨店鋪乙店' }));
      nameB = await storeName(pageB, slugB);
      for (let n = 1; n <= SHOP_B_POSTS; n++) {
        await createPost(pageB, titleB(n), null, true); // 乙店的文章都比甲店晚發布
      }
    } finally {
      await pageA.close();
      await pageB.close();
    }
  });

  test('E2E-PLATBLOG-01: 匿名訪客開 /blog：沒有登入提示；兩間店的已發布貼文都在公開列表、草稿不在、乙店（較晚發布）排在甲店前面', async ({ page, request }) => {
    await page.goto('/blog');
    await expect(page.getByRole('heading', { level: 1, name: '部落格' })).toBeVisible();
    await expect(page.getByText('請先登入並設定店鋪以瀏覽部落格')).toHaveCount(0);
    await expect(page.getByTestId('blog-grid').getByRole('link').first()).toBeVisible();

    // 讀到兩篇已發布的貼文為止（新到舊）：兩間店的都在、乙店排在甲店前面；這段範圍裡沒有甲店的草稿
    // （草稿「整份列表都不出現」由後端整合測試 IT-M15-032 驗證；這裡不為了證明不存在而把整份列表掃完，見檔頭）
    const titleNewestB = titleB(SHOP_B_POSTS);
    const cards = await readFeed(request, [TITLE_A1, titleNewestB]);
    const titles = cards.map((card) => card.title);
    expect(titles, '兩間店的已發布貼文都在公開列表').toEqual(expect.arrayContaining([TITLE_A1, titleNewestB]));
    expect(titles.indexOf(titleNewestB), '乙店比甲店晚發布，新到舊排序時不能在甲店後面').toBeLessThan(titles.indexOf(TITLE_A1));
    expect(titles, '草稿不能出現在公開列表').not.toContain(TITLE_A_DRAFT);
  });

  test('E2E-PLATBLOG-02: 卡片帶店鋪名稱與分類；連結指向「那間店鋪」的文章頁（不是 /blog），點進去看得到文章', async ({ page, request }) => {
    const cards = await readFeed(request, [TITLE_A1]);
    const pageOfA1 = uiPageOf(cards, TITLE_A1);
    expect(pageOfA1, '公開列表裡應該有甲店的已發布貼文').toBeGreaterThanOrEqual(0);
    const apiCard = cards.find((card) => card.title === TITLE_A1)!;
    expect(apiCard.tenantSlug).toBe(slugA);
    expect(apiCard.tenantName).toBe(nameA);
    expect(nameB, '兩間店的名稱不同，卡片才分得出來').not.toBe(nameA);

    await page.goto(blogUrl(pageOfA1));
    const card = page.getByTestId('blog-grid').getByRole('link', { name: new RegExp(TITLE_A1) });
    await expect(card).toHaveAttribute('href', `/stores/${slugA}/blog/${a1Slug}`);
    await expect(card).toContainText(nameA);
    await expect(card).toContainText(CATEGORY_A);

    await card.click();
    await expect(page).toHaveURL(new RegExp(`/stores/${slugA}/blog/${a1Slug}$`));
    // 店鋪外殼的 h1 是店名，文章標題是 h2（Sprint 336，DEF-483）
    await expect(page.getByRole('heading', { level: 2, name: TITLE_A1 })).toBeVisible();
  });

  test('E2E-PLATBLOG-03: 店鋪被停權 → 它的貼文從公開列表消失（API 與畫面），恢復營業後又回來', async ({ page, request }) => {
    const adminToken = await adminAccessToken(page);
    const before = await readFeed(request, [TITLE_A1]);
    const pageOfA1 = uiPageOf(before, TITLE_A1);
    expect(pageOfA1).toBeGreaterThanOrEqual(0);
    const cardOnScreen = () => page.getByTestId('blog-grid').getByRole('link', { name: new RegExp(TITLE_A1) });

    await page.goto(blogUrl(pageOfA1));
    await expect(cardOnScreen()).toBeVisible();

    await setStoreStatusWithToken(page, adminToken, tenantA, 'SUSPENDED');
    try {
      const whileSuspended = await readFeed(request); // 讀到底：停權後整份列表都不能有這間店的貼文
      expect(whileSuspended.map((card) => card.title), '停權後公開列表整份都沒有這間店的貼文').not.toContain(TITLE_A1);
      expect(whileSuspended.map((card) => card.title), '另一間店不受影響').toContain(titleB(SHOP_B_POSTS));
      await page.goto(blogUrl(pageOfA1));
      await expect(cardOnScreen()).toHaveCount(0);
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantA, 'ACTIVE');
    }

    const restored = await readFeed(request, [TITLE_A1]);
    expect(uiPageOf(restored, TITLE_A1), '恢復營業後貼文回來').toBeGreaterThanOrEqual(0);
    await page.goto(blogUrl(uiPageOf(restored, TITLE_A1)));
    await expect(cardOnScreen()).toBeVisible();
  });

  test('E2E-PLATBLOG-04: 分頁走網址：點「2」→ /blog?page=2、點「1」→ /blog；頁碼超出範圍 → 「這一頁沒有文章」與回到第一頁的連結', async ({ page }) => {
    await page.goto('/blog');
    const pager = page.getByRole('navigation', { name: '分頁' });
    await expect(pager, '乙店有 13 篇，全列表至少 13 篇，一定有第 2 頁').toBeVisible();

    await pager.getByRole('button', { name: '2', exact: true }).click();
    await expect(page).toHaveURL(`${WEB_BASE}/blog?page=2`);
    await expect(page.getByTestId('blog-grid').getByRole('link').first()).toBeVisible();

    await pager.getByRole('button', { name: '1', exact: true }).click();
    await expect(page).toHaveURL(`${WEB_BASE}/blog`);

    await page.goto('/blog?page=9999');
    await expect(page.getByTestId('blog-page-out-of-range')).toBeVisible();
    await expect(page.getByText('目前還沒有已發布的文章')).toHaveCount(0);
    await page.getByRole('link', { name: '回到第一頁' }).click();
    await expect(page).toHaveURL(`${WEB_BASE}/blog`);
    await expect(page.getByTestId('blog-grid')).toBeVisible();
  });

  test('E2E-PLATBLOG-05: 入口與舊網址：匿名訪客從首頁點頁尾「部落格」到 /blog；舊的分類頁網址 /blog/category/{編號} 以 308 轉到 /blog', async ({ page, request }) => {
    await page.goto('/');
    await page.getByTestId('storefront-footer').getByRole('link', { name: '部落格', exact: true }).click();
    await expect(page).toHaveURL(`${WEB_BASE}/blog`);
    await expect(page).toHaveTitle(/^部落格｜/);

    const old = await request.get(`/blog/category/${randomUUID()}`, { maxRedirects: 0 });
    expect(old.status(), '分類頁移除是定案，用 308').toBe(308);
    expect(new URL(old.headers()['location'], WEB_BASE).pathname).toBe('/blog');
  });
});
