import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-SEO-SSR-REAL: 公開頁的 SEO 伺服器端渲染（DEF-500，Sprint 341，真實後端）
 *
 * 之前公開頁的內容都由瀏覽器載入：第一份 HTML 只有載入中骨架，搜尋引擎與不執行 JavaScript 的爬蟲看不到商品、文章與連結
 * （Sprint 340 實測：平台 /blog 的 12 張卡片，初始 HTML 裡 0 張）。現在頁面的首份資料由 Next 伺服器直接向後端取、隨第一份 HTML 送出。
 *
 * 這份規格的核心手法：**不用瀏覽器**——用 Playwright 的 request（只發 HTTP、不執行任何 JavaScript）取頁面，
 * 看到的就是爬蟲看到的第一份 HTML。用瀏覽器的話，頁面載入後 JavaScript 會補齊內容，規格會假綠燈。
 *
 * 守的是使用者（搜尋引擎）看得到的行為，不是實作：
 *   ① 內容在第一份 HTML 裡：店名、商品／房源卡片與連結、文章全文與內嵌商品卡、貼文列表；
 *   ② 分頁是真正的連結：爬蟲沿著 <a href="…?page=2"> 能走到第 2 頁以後的內容；
 *   ③ 不存在的頁面回真正的 404 並帶 noindex（草稿、停權店鋪不外洩）——不是「200 加一個找不到的畫面」（soft 404）；
 *   ④ 結構化資料（JSON-LD）：商品價格、文章發布日期、店鋪、網站；與頁面內容一致；
 *   ⑤ robots.txt 與 sitemap.xml 列出真實的店鋪、商品、文章；
 *   ⑥ 爬蟲與連結預覽取文章不計瀏覽，瀏覽器載入才計一次；瀏覽器載入時不再重複請求伺服器端已經帶來的資料。
 *
 * 暫時不用 mock：伺服器端的請求是 Next 直接打後端，Playwright 的 page.route 攔不到，必須是真實資料。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const STAMP = Date.now();
const PRODUCT_NAME = `SEO商品${STAMP}`;
const ROOM_NAME = `SEO房型${STAMP}`;
const MAIN_TITLE = `SEO主文章${STAMP}`;
const FILLER_COUNT = 13; // 加上主文章共 14 篇：每頁 12 篇，第 2 頁 2 篇
const filler = (n: number) => `SEO分頁文章${String(n).padStart(2, '0')}-${STAMP}`;
const DRAFT_TITLE = `SEO草稿${STAMP}`;

interface Fetched {
  status: number;
  body: string;
}

/**
 * 取頁面的第一份 HTML（不執行 JavaScript）。React 伺服器端渲染會在相鄰的文字節點之間插入 `<!-- -->`（例如 `{幣別} {價格}` 變成
 * `TWD<!-- --> <!-- -->456`），瀏覽器看起來是連續的文字；這裡先去掉，斷言才是「使用者（與搜尋引擎）讀到的文字」。
 */
async function html(request: APIRequestContext, path: string): Promise<Fetched> {
  const response = await request.get(path, { maxRedirects: 0 });
  return { status: response.status(), body: (await response.text()).replace(/<!-- -->/g, '') };
}

/** 頁面裡所有 JSON-LD 區塊（解析後）。 */
function jsonLd(body: string): Array<Record<string, unknown>> {
  return [...body.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)].map(
    (match) => JSON.parse(match[1]) as Record<string, unknown>
  );
}

const typeOf = (data: Array<Record<string, unknown>>, type: string) => data.find((d) => d['@type'] === type);

/** 這一頁是否帶 noindex（Next 在 notFound() 時自動加；搜尋引擎據此不收錄）。 */
const hasNoindex = (body: string) => /<meta name="robots" content="[^"]*noindex/.test(body);

test.describe('AT-SEO-SSR-REAL: 公開頁的 SEO 伺服器端渲染（真實後端，不執行 JavaScript）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let storeSlug: string;
  let storeName: string;
  let tenantId: string;
  let productId: string;
  let roomId: string;
  let mainSlug: string;
  let draftSlug: string;
  let ownerPage: Page;

  async function createPost(title: string, content: string, publish: boolean): Promise<string> {
    const response = await ownerPage.request.post(`${API_BASE}/v2/dashboard/posts`, {
      headers: await authHeaders(ownerPage),
      data: { title, content, autoPublish: publish },
    });
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()).data.slug as string;
  }

  /** 讀目前的瀏覽數，自己不計瀏覽（countView=false）。 */
  async function viewCount(request: APIRequestContext): Promise<number> {
    const response = await request.get(`${API_BASE}/v2/posts/${mainSlug}`, { params: { tenantId, countView: false } });
    expect(response.status(), await response.text()).toBe(200);
    return (await response.json()).data.viewCount as number;
  }

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    ownerPage = await browser.newPage({ baseURL: WEB_BASE });
    const seeded = await seedStore(ownerPage, {
      businessType: 'HYBRID',
      storeLabel: 'E2E SEO 店',
      product: { name: PRODUCT_NAME, price: 456 },
      room: { name: ROOM_NAME, price: 3456 },
    });
    storeSlug = seeded.storeSlug;
    tenantId = seeded.tenantId;
    productId = seeded.productId!;
    roomId = seeded.roomId!;

    const profile = await ownerPage.request.get(`${API_BASE}/v2/public/stores/${storeSlug}`);
    expect(profile.status(), await profile.text()).toBe(200);
    storeName = (await profile.json()).data.name as string;

    // 精選：店鋪首頁的精選區塊
    const featured = await ownerPage.request.put(`${API_BASE}/v2/products/${productId}`, {
      headers: await authHeaders(ownerPage),
      data: { isFeatured: true },
    });
    expect(featured.status(), await featured.text()).toBe(200);

    // 先建 13 篇陪襯（較舊），最後才建主文章（最新）→ 主文章在第 1 頁、最舊的兩篇在第 2 頁
    for (let n = 0; n < FILLER_COUNT; n++) await createPost(filler(n), `${filler(n)} 的內容`, true);
    mainSlug = await createPost(
      MAIN_TITLE,
      `這是 SEO 測試文章的內文。\n\n{{embed:listing:${productId}}}\n\n文章結尾。`,
      true
    );
    draftSlug = await createPost(DRAFT_TITLE, '還沒發布的草稿內容', false);
  });

  test.afterAll(async () => {
    await ownerPage?.close();
  });

  test('E2E-SEO-01: 店鋪首頁——店名、精選商品與連結在第一份 HTML 裡（不是載入中骨架）；有店鋪的結構化資料與標準網址', async ({ request }) => {
    const { status, body } = await html(request, `/stores/${storeSlug}`);

    expect(status).toBe(200);
    expect(body, '店名在第一份 HTML 裡').toContain(storeName);
    expect(body, '精選商品卡片在第一份 HTML 裡').toContain(PRODUCT_NAME);
    expect(body, '商品卡片是連到詳情頁的真正連結').toContain(`href="/listings/${productId}"`);
    expect(body, '不該是載入中骨架').not.toContain('data-testid="store-loading"');
    expect(typeOf(jsonLd(body), 'Store')).toMatchObject({ name: storeName });
    expect(body).toContain(`<link rel="canonical" href="https://www.nextkey.com/stores/${storeSlug}"`);
  });

  test('E2E-SEO-02: 店鋪的商品頁與房源頁——各自只有自己的類型，卡片是連結', async ({ request }) => {
    const products = await html(request, `/stores/${storeSlug}/products`);
    expect(products.status).toBe(200);
    expect(products.body).toContain(PRODUCT_NAME);
    expect(products.body).toContain(`href="/listings/${productId}"`);
    expect(products.body).not.toContain(ROOM_NAME);

    const rooms = await html(request, `/stores/${storeSlug}/rooms`);
    expect(rooms.status).toBe(200);
    expect(rooms.body).toContain(ROOM_NAME);
    expect(rooms.body).toContain(`href="/listings/${roomId}"`);
    expect(rooms.body).not.toContain(PRODUCT_NAME);
  });

  test('E2E-SEO-03: 商品詳情——名稱、價格、說明在第一份 HTML 裡；結構化資料的價格與頁面一致；房源是 HotelRoom', async ({ request }) => {
    const product = await html(request, `/listings/${productId}`);
    expect(product.status).toBe(200);
    expect(product.body).toContain(PRODUCT_NAME);
    expect(product.body, '價格在第一份 HTML 裡').toContain('NT$456');
    expect(product.body).not.toContain('data-testid="listing-loading"');
    const data = typeOf(jsonLd(product.body), 'Product');
    expect(data, '商品頁有 Product 結構化資料').toMatchObject({
      name: PRODUCT_NAME,
      url: `https://www.nextkey.com/listings/${productId}`,
      offers: { '@type': 'Offer', price: 456, priceCurrency: 'TWD' },
    });
    expect(product.body).toContain(`<link rel="canonical" href="https://www.nextkey.com/listings/${productId}"`);

    const room = await html(request, `/listings/${roomId}`);
    expect(room.status).toBe(200);
    expect(typeOf(jsonLd(room.body), 'HotelRoom')).toMatchObject({ name: ROOM_NAME, offers: { price: 3456 } });
  });

  test('E2E-SEO-04: 文章頁——全文與內嵌的商品卡在第一份 HTML 裡；BlogPosting 有發布日期與標題；爬蟲取文章不計瀏覽', async ({ request }) => {
    const before = await viewCount(request);
    const { status, body } = await html(request, `/stores/${storeSlug}/blog/${mainSlug}`);

    expect(status).toBe(200);
    expect(body, '文章標題').toContain(MAIN_TITLE);
    expect(body, '文章內文').toContain('這是 SEO 測試文章的內文。');
    expect(body, '內嵌的商品卡片（文章內容的一部分）：商品名稱與價格').toContain(PRODUCT_NAME);
    expect(body).toContain('TWD 456');
    expect(body, '不該是載入中').not.toContain('data-testid="store-article-loading"');
    const article = typeOf(jsonLd(body), 'BlogPosting');
    expect(article).toMatchObject({ headline: MAIN_TITLE, url: `https://www.nextkey.com/stores/${storeSlug}/blog/${mainSlug}` });
    expect(typeof article?.datePublished, '有發布日期').toBe('string');
    expect(body).toContain(`<link rel="canonical" href="https://www.nextkey.com/stores/${storeSlug}/blog/${mainSlug}"`);

    expect(await viewCount(request), '爬蟲與連結預覽取文章不是一次瀏覽：瀏覽數不能動（Sprint 336 的決定，伺服器端渲染不能打破它）').toBe(before);
  });

  test('E2E-SEO-05: 店鋪貼文列表——貼文是連結；分頁是真正的連結，沿著它走得到第 2 頁的內容', async ({ request }) => {
    const first = await html(request, `/stores/${storeSlug}/blog`);
    expect(first.status).toBe(200);
    expect(first.body).toContain(MAIN_TITLE);
    expect(first.body).toContain(`href="/stores/${storeSlug}/blog/${mainSlug}"`);
    expect(first.body, '草稿不外洩').not.toContain(DRAFT_TITLE);
    const nextHref = `/stores/${storeSlug}/blog?page=2`;
    expect(first.body, '分頁是 <a href>：爬蟲只會沿著連結走').toContain(`href="${nextHref}"`);
    expect(first.body).not.toContain(filler(0)); // 最舊的陪襯文章不在第 1 頁

    const second = await html(request, nextHref);
    expect(second.status).toBe(200);
    expect(second.body, '沿著連結走到第 2 頁，看得到第 1 頁沒有的內容').toContain(filler(0));
    expect(second.body).not.toContain(MAIN_TITLE);
  });

  test('E2E-SEO-06: 首頁、商品、房源、部落格列表——有商品／文章卡片與連結、分頁連結，沒有載入中骨架（跨店鋪，資料不是只有我的）', async ({ request }) => {
    const home = await html(request, '/');
    expect(home.status).toBe(200);
    expect(home.body).toContain('data-testid="product-grid"');
    expect(home.body, '首頁有指向商品詳情的真正連結').toMatch(/href="\/listings\/[0-9a-f-]{36}"/);
    expect(home.body).not.toContain('data-testid="home-empty"');
    expect(typeOf(jsonLd(home.body), 'WebSite')).toMatchObject({ name: expect.stringContaining('意象若水') });

    for (const path of ['/products', '/rooms']) {
      const list = await html(request, path);
      expect(list.status, path).toBe(200);
      expect(list.body, path).toMatch(/href="\/listings\/[0-9a-f-]{36}"/);
    }

    const blog = await html(request, '/blog');
    expect(blog.status).toBe(200);
    expect(blog.body, '平台部落格有文章卡片，連到各店鋪的文章頁').toMatch(/href="\/stores\/[^"]+\/blog\/[^"]+"/);
    expect(blog.body).toContain('data-testid="blog-grid"');
  });

  test('E2E-SEO-07: 不存在的頁面回真正的 404＋noindex（不是 200 加「找不到」畫面）；草稿不外洩；好好的頁面不受影響', async ({ request }) => {
    const missingStore = await html(request, `/stores/no-such-store-${STAMP}`);
    expect(missingStore.status, '店鋪不存在').toBe(404);
    expect(hasNoindex(missingStore.body)).toBe(true);

    const missingPost = await html(request, `/stores/${storeSlug}/blog/no-such-post-${STAMP}`);
    expect(missingPost.status, '文章不存在（店鋪是好的）').toBe(404);
    expect(hasNoindex(missingPost.body)).toBe(true);

    const draft = await html(request, `/stores/${storeSlug}/blog/${draftSlug}`);
    expect(draft.status, '草稿對訪客等於不存在').toBe(404);
    expect(draft.body, '草稿的標題不能出現在回應裡').not.toContain(DRAFT_TITLE);

    for (const id of [randomUUID(), 'not-a-uuid']) {
      const missingListing = await html(request, `/listings/${id}`);
      expect(missingListing.status, `商品 ${id}`).toBe(404);
      expect(hasNoindex(missingListing.body)).toBe(true);
    }

    // 對照：好好的頁面沒有被誤判
    const fine = await html(request, `/stores/${storeSlug}`);
    expect(fine.status).toBe(200);
    expect(hasNoindex(fine.body), '正常頁面不能帶 noindex').toBe(false);
  });

  test('E2E-SEO-08: robots.txt 擋掉後台與 API、指向 sitemap；sitemap.xml 列出這間店鋪、它的商品、房源與文章（不含草稿）', async ({ request }) => {
    const robots = await request.get('/robots.txt');
    expect(robots.status()).toBe(200);
    const robotsText = await robots.text();
    expect(robotsText).toContain('Allow: /');
    for (const path of ['/api/', '/dashboard', '/admin']) expect(robotsText).toContain(`Disallow: ${path}`);
    expect(robotsText).toContain('Sitemap: https://www.nextkey.com/sitemap.xml');

    const sitemap = await request.get('/sitemap.xml');
    expect(sitemap.status()).toBe(200);
    expect(sitemap.headers()['content-type']).toContain('xml');
    const xml = await sitemap.text();
    const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1].replace('https://www.nextkey.com', ''));
    const why = '（sitemap 在伺服器記憶體留存一小時；重複對同一台長時間運作的伺服器跑這份規格時，請先重啟前端）';
    for (const expected of [
      '/',
      '/products',
      '/blog',
      `/stores/${storeSlug}`,
      `/stores/${storeSlug}/products`,
      `/stores/${storeSlug}/rooms`,
      `/stores/${storeSlug}/blog`,
      `/listings/${productId}`,
      `/listings/${roomId}`,
      `/stores/${storeSlug}/blog/${mainSlug}`,
    ]) {
      expect(locs, `${expected} ${why}`).toContain(expected);
    }
    expect(locs, '草稿不能出現在 sitemap').not.toContain(`/stores/${storeSlug}/blog/${draftSlug}`);
    expect(locs.filter((loc) => loc.includes('?')), '不收錄分頁、排序、搜尋結果').toEqual([]);
  });

  test('E2E-SEO-09: 瀏覽器載入時不再重複請求伺服器端已經帶來的資料；文章頁只多一個「計一次瀏覽」的請求，瀏覽數 +1', async ({ page, request }) => {
    const dataRequests: string[] = [];
    page.on('request', (req) => {
      const url = req.url();
      if (url.startsWith(API_BASE) && /\/v2\/(public\/|listings|posts)/.test(url)) dataRequests.push(`${req.method()} ${url.replace(API_BASE, '')}`);
    });

    await page.goto(`/stores/${storeSlug}`);
    await expect(page.getByTestId('store-featured-listings')).toContainText(PRODUCT_NAME);
    await page.goto(`/stores/${storeSlug}/products`);
    await expect(page.getByTestId('store-listing-grid')).toContainText(PRODUCT_NAME);
    await page.goto(`/listings/${productId}`);
    await expect(page.getByTestId('listing-detail')).toContainText(PRODUCT_NAME);
    expect(dataRequests, '店鋪首頁、商品頁、商品詳情：資料都已隨 HTML 到達，瀏覽器不該再向後端要一次').toEqual([]);

    const before = await viewCount(request);
    await page.goto(`/stores/${storeSlug}/blog/${mainSlug}`);
    await expect(page.getByTestId('store-article-title')).toHaveText(MAIN_TITLE);
    await expect(page.getByText(/次瀏覽/)).toContainText(`${before + 1} 次瀏覽`);
    expect(dataRequests.filter((r) => r.startsWith('GET /v2/posts/')), '只有「計一次瀏覽」那一個請求').toHaveLength(1);
    expect(dataRequests.some((r) => r.includes('countView=false')), '瀏覽器載入才是一次真正的瀏覽，不能帶 countView=false').toBe(false);
    expect(await viewCount(request), '一次瀏覽只計一次').toBe(before + 1);
  });

  // 放在最後：停權之後這間店鋪的頁面就不公開了，前面的案例都需要它是營業中
  test('E2E-SEO-10: 店鋪被停權 → 它的店鋪首頁與文章頁對訪客是真正的 404，文章標題不外洩', async ({ request, browser }) => {
    const adminPage = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const token = await adminAccessToken(adminPage);
      await setStoreStatusWithToken(adminPage, token, tenantId, 'SUSPENDED');

      const home = await html(request, `/stores/${storeSlug}`);
      expect(home.status, '停權店鋪的首頁').toBe(404);
      expect(hasNoindex(home.body)).toBe(true);
      const article = await html(request, `/stores/${storeSlug}/blog/${mainSlug}`);
      expect(article.status, '停權店鋪的文章頁（整頁不公開，與店鋪首頁一致）').toBe(404);
      expect(article.body, '文章標題不外洩').not.toContain(MAIN_TITLE);
    } finally {
      await adminPage.close();
    }
  });
});
