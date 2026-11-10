import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { Account, adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';
import { loginOnly } from './helpers/auth';

/**
 * AT-ARTICLE-TITLES-REAL: 文章頁的網址帶店鋪、匿名可讀、有實體標題（DEF-483，Sprint 336，真實後端）
 *
 * 之前的文章頁是 /blog/{文章}：沒有店鋪，靠瀏覽器 localStorage 的 tenantId 取文章，而 tenantId 只有登入才會寫入——
 * 匿名訪客（包含從公開的店鋪貼文列表點進來的人）只看到「請先登入並設定店鋪以瀏覽部落格」，伺服器端也查不到文章，
 * 所以沒有實體標題。現在文章網址是 /stores/{店鋪}/blog/{文章}：店鋪由網址決定，匿名可讀，伺服器端組出
 * 「文章標題｜店名」與 Open Graph。
 *
 * 為什麼用真實後端、而且要讀「伺服器吐出的原始 HTML」：標題與 Open Graph 是給爬蟲與分享卡片看的，它們不執行頁面的 JavaScript
 * （見 at-entity-titles-real）。「不計瀏覽」「停權店鋪不外洩」「草稿不外洩」也只有真實後端能證明。
 *
 * 除了舊網址的登入者案例（E2E-ARTICLE-06）之外，全部匿名造訪：這些是公開頁。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const BRAND = '意象若水 RUOSHUI';
const STAMP = Date.now();
const POST_TITLE = `文章標題測試${STAMP}`;
const DRAFT_TITLE = `未發布草稿${STAMP}`;
const POST_BODY = `這是 E2E 文章內文${STAMP}。`;
const COVER_URL = `https://cdn.example.com/cover-${STAMP}.png`;

test.describe('AT-ARTICLE-TITLES-REAL: 文章頁（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let tenantId: string;
  let storeSlug: string;
  let storeName: string;
  let postSlug: string;
  let draftSlug: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, { businessType: 'RETAIL_ONLY', storeLabel: 'E2E 文章標題店' });
      owner = seeded.owner;
      tenantId = seeded.tenantId;
      storeSlug = seeded.storeSlug;
      const store = await page.request.get(`${API_BASE}/v2/public/stores/${storeSlug}`);
      expect(store.status(), await store.text()).toBe(200);
      storeName = (await store.json()).data.name as string;
      expect(storeName).toContain('E2E 文章標題店');

      const published = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: { title: POST_TITLE, content: POST_BODY, featuredImageUrl: COVER_URL, autoPublish: true },
      });
      expect(published.status(), await published.text()).toBe(200);
      postSlug = (await published.json()).data.slug as string;

      const draft = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: { title: DRAFT_TITLE, content: '還沒發布的內容' },
      });
      expect(draft.status(), await draft.text()).toBe(200);
      draftSlug = (await draft.json()).data.slug as string;
      expect(draftSlug).not.toBe(postSlug);
    } finally {
      await page.close();
    }
  });

  const articleUrl = (slug: string) => `/stores/${storeSlug}/blog/${slug}`;

  /** 讀目前瀏覽數，自己不計瀏覽（countView=false） */
  async function viewCount(request: APIRequestContext): Promise<number> {
    const response = await request.get(`${API_BASE}/v2/posts/${postSlug}`, {
      params: { tenantId, countView: 'false' },
    });
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()).data.viewCount as number;
  }

  /**
   * 三個公開貼文端點（列表、單篇、公開分類）對這間店鋪回的狀態碼（Sprint 337，DEF-485）。
   * 單篇帶 countView=false，不動瀏覽數；其餘兩個端點不看這個參數。
   */
  async function publicPostApiStatuses(request: APIRequestContext): Promise<Record<string, number>> {
    const statuses: Record<string, number> = {};
    for (const path of ['/v2/posts', `/v2/posts/${postSlug}`, '/v2/posts/categories']) {
      const response = await request.get(`${API_BASE}${path}`, { params: { tenantId, countView: 'false' } });
      statuses[path] = response.status();
    }
    return statuses;
  }

  test('E2E-ARTICLE-01: 匿名訪客從店鋪貼文列表點進文章 → 看得到文章（之前是「請先登入並設定店鋪以瀏覽部落格」）', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${storeSlug}/blog`);
    const link = page.getByRole('link', { name: new RegExp(POST_TITLE) });
    await expect(link).toHaveAttribute('href', articleUrl(postSlug));

    await link.click();

    await expect(page).toHaveURL(new RegExp(`${articleUrl(postSlug)}$`));
    await expect(page.getByRole('heading', { level: 2, name: POST_TITLE })).toBeVisible();
    await expect(page.getByText(POST_BODY)).toBeVisible();
    await expect(page.getByText('請先登入並設定店鋪以瀏覽部落格')).toHaveCount(0);
    // 店鋪外殼的 h1 是店名；文章標題是 h2，整頁只有一個 h1
    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(storeName);
    await expect(page.getByTestId('store-article-back')).toHaveAttribute('href', `/stores/${storeSlug}/blog`);
  });

  test('E2E-ARTICLE-02: 爬蟲看到的原始 HTML（不執行 JavaScript）就有「文章標題｜店名」與文章型 Open Graph', async ({ request }: { request: APIRequestContext }) => {
    const html = await (await request.get(`${WEB_BASE}${articleUrl(postSlug)}`)).text();
    expect(html, '原始 HTML 的 <title>').toContain(`<title>${POST_TITLE}｜${storeName}｜${BRAND}</title>`);
    expect(html, 'og:title').toContain(`<meta property="og:title" content="${POST_TITLE}｜${storeName}"`);
    expect(html, 'og:type 是文章').toContain('<meta property="og:type" content="article"');
    expect(html, 'og:url 必須是絕對網址（metadataBase）').toContain(
      `<meta property="og:url" content="https://www.nextkey.com/stores/${storeSlug}/blog/${postSlug}"`
    );
    expect(html, '沒有摘要時 description 取內文').toContain(`<meta name="description" content="${POST_BODY}"`);
    expect(html, 'og:image：封面圖是絕對網址才放').toContain(`<meta property="og:image" content="${COVER_URL}"`);
    expect(html, '發布時間').toMatch(/<meta property="article:published_time" content="\d{4}-\d{2}-\d{2}T/);
  });

  test('E2E-ARTICLE-03: 組標題的伺服器端請求不計瀏覽；一次真正的瀏覽器載入只計一次', async ({ page, request }: { page: Page; request: APIRequestContext }) => {
    const before = await viewCount(request);

    // 伺服器端為了標題讀文章三次（爬蟲、連結預覽都會這樣）：瀏覽數不能動
    for (let i = 0; i < 3; i++) {
      const html = await (await request.get(`${WEB_BASE}${articleUrl(postSlug)}`)).text();
      expect(html).toContain(`<title>${POST_TITLE}｜`); // 確認這三次真的有走到「取得文章」那一步
    }
    expect(await viewCount(request), '伺服器端組標題不是一次瀏覽').toBe(before);

    // 真正的瀏覽器載入：頁面讀一次全文（計一次）；標題那一路不能再多算一次
    await page.goto(articleUrl(postSlug));
    await expect(page.getByTestId('store-article-title')).toBeVisible();
    expect(await viewCount(request), '一次瀏覽只計一次（之前的實作若讓標題請求也計，這裡會是 +2 或更多）').toBe(before + 1);
  });

  test('E2E-ARTICLE-04: 草稿、不存在的文章 → 靜態標題「部落格文章」、沒有 Open Graph，標題不外洩；頁面顯示「找不到文章」', async ({ page, request }: { page: Page; request: APIRequestContext }) => {
    for (const slug of [draftSlug, 'no-such-article-zzz']) {
      const html = await (await request.get(`${WEB_BASE}${articleUrl(slug)}`)).text();
      expect(html, `${slug} 的 <title>`).toContain(`<title>部落格文章｜${BRAND}</title>`);
      expect(html, '草稿的標題不能外洩').not.toContain(DRAFT_TITLE);
      expect(html, '取不到文章時不能留下殘缺的分享資訊').not.toContain('property="og:title"');
    }

    await page.goto(articleUrl(draftSlug));
    await expect(page.getByTestId('store-article-notfound')).toBeVisible();
    await expect(page.getByRole('heading', { name: '找不到文章' })).toBeVisible();
    await expect(page.getByText('此文章不存在或已下架')).toBeVisible();
    await expect(page.getByText(DRAFT_TITLE)).toHaveCount(0);
  });

  test('E2E-ARTICLE-05: 停權的店鋪 → 文章頁整頁不公開（標題退回靜態、不含文章標題與店名），公開貼文 API 也都是 404，恢復後又回來', async ({ page, request }: { page: Page; request: APIRequestContext }) => {
    const readable = { '/v2/posts': 200, [`/v2/posts/${postSlug}`]: 200, '/v2/posts/categories': 200 };
    const hidden = { '/v2/posts': 404, [`/v2/posts/${postSlug}`]: 404, '/v2/posts/categories': 404 };
    expect(await publicPostApiStatuses(request), '對照組：營業中的店鋪，三個公開貼文端點都讀得到').toEqual(readable);

    const adminToken = await adminAccessToken(page);
    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    try {
      // DEF-485（Sprint 337）：不只是頁面——直接打公開貼文 API 也一樣（之前停權店鋪的已發布貼文仍讀得到，只是畫面先擋了）
      expect(await publicPostApiStatuses(request), '管理員停權後，三個公開貼文端點立刻都是 404').toEqual(hidden);

      const html = await (await request.get(`${WEB_BASE}${articleUrl(postSlug)}`)).text();
      expect(html).toContain(`<title>部落格文章｜${BRAND}</title>`);
      expect(html, '停權店鋪的文章標題不能出現在 HTML').not.toContain(POST_TITLE);
      expect(html, '停權店鋪的店名不能出現在 HTML').not.toContain(storeName);

      await page.goto(articleUrl(postSlug));
      await expect(page.getByTestId('store-notfound')).toBeVisible();
      await expect(page.getByText(POST_TITLE)).toHaveCount(0);
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }
    const restored = await (await request.get(`${WEB_BASE}${articleUrl(postSlug)}`)).text();
    expect(restored, '不快取：恢復營業後標題立刻回來').toContain(`<title>${POST_TITLE}｜${storeName}｜${BRAND}</title>`);
    expect(await publicPostApiStatuses(request), '恢復營業後，公開貼文 API 立刻又讀得到').toEqual(readable);
  });

  test('E2E-ARTICLE-06: 舊網址 /blog/{文章}——登入的店主被導向新網址並看到文章（且只計一次瀏覽）；匿名訪客維持原本的登入提示', async ({ page, browser, request }: { page: Page; browser: import('@playwright/test').Browser; request: APIRequestContext }) => {
    const before = await viewCount(request);
    await loginOnly(page, owner.email, owner.password);

    await page.goto(`/blog/${postSlug}`);

    await expect(page).toHaveURL(new RegExp(`${articleUrl(postSlug)}$`));
    await expect(page.getByRole('heading', { level: 2, name: POST_TITLE })).toBeVisible();
    expect(await viewCount(request), '舊網址為了找店鋪而查詢，不能多計一次瀏覽').toBe(before + 1);

    // 匿名訪客：改版前就打不開，現在維持原本的提示，停在舊網址
    const anonymous = await browser.newPage({ baseURL: WEB_BASE });
    try {
      await anonymous.goto(`/blog/${postSlug}`);
      await expect(anonymous.getByText('請先登入並設定店鋪以瀏覽部落格')).toBeVisible();
      await expect(anonymous).toHaveURL(new RegExp(`/blog/${postSlug}$`));
    } finally {
      await anonymous.close();
    }
  });

  test('E2E-ARTICLE-07: 登入後的平台部落格清單 /blog，文章連結指向新網址（帶店鋪）', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/blog');
    await expect(page.getByRole('link', { name: new RegExp(POST_TITLE) })).toHaveAttribute('href', articleUrl(postSlug));
  });
});
