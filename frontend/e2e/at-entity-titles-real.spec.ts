import { test, expect, Page } from '@playwright/test';
import { adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';
import { loginOnly } from './helpers/auth';

/**
 * AT-ENTITY-TITLES-REAL: 公開頁的實體標題與分享資訊（DEF-475，Sprint 335，真實後端）
 *
 * 動態路由的公開頁（店鋪前台、商品／房源詳情）原本只有靜態的頁型標題（「店鋪首頁」「商品／房源詳情」）：
 * 搜尋結果與社群分享看到的每一頁都一樣。現在伺服器端用後端的公開 API 取店名／商品名，組進 `<title>` 與 Open Graph。
 *
 * 為什麼用真實後端、而且要讀「伺服器吐出的原始 HTML」：標題與 Open Graph 是給爬蟲與分享卡片看的，它們不執行頁面的 JavaScript。
 * 瀏覽器載入後 `document.title` 對了，不代表爬蟲看到的 HTML 也對——所以有一個案例直接抓原始 HTML 比對（E2E-ENTITY-04）。
 * 後端公開 API 的真實行為（匿名可呼叫、非 ACTIVE 店鋪回 404）也只有真實後端能證明。
 *
 * 全部匿名造訪（沒有登入）：這些是公開頁。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const BRAND = '意象若水 RUOSHUI';
const STAMP = Date.now();
const PRODUCT_NAME = `實體標題商品${STAMP}`;
const ROOM_NAME = `實體標題房型${STAMP}`;
const NO_SUCH_LISTING = '00000000-0000-4000-8000-000000000000';

test.describe('AT-ENTITY-TITLES-REAL: 公開頁的實體標題（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let slug: string;
  let storeName: string;
  let tenantId: string;
  let productId: string;
  let roomId: string;
  let owner: { email: string; password: string };

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 實體標題店',
        product: { name: PRODUCT_NAME, price: 321 },
        room: { name: ROOM_NAME, price: 1234 },
      });
      tenantId = seeded.tenantId;
      owner = seeded.owner;
      productId = seeded.productId!;
      roomId = seeded.roomId!;
      // 匿名可呼叫的公開端點取 slug 與店名（同時確認後端這兩個公開端點真的對匿名開放）
      const profile = await page.request.get(`${API_BASE}/v2/tenants/${tenantId}`);
      expect(profile.status(), await profile.text()).toBe(200);
      slug = (await profile.json()).data.slug as string;
      const store = await page.request.get(`${API_BASE}/v2/public/stores/${slug}`);
      expect(store.status(), await store.text()).toBe(200);
      storeName = (await store.json()).data.name as string;
      expect(storeName).toContain('E2E 實體標題店');
    } finally {
      await page.close();
    }
  });

  test('E2E-ENTITY-01: 店鋪前台各頁的標題帶店名：首頁「店名」、子頁「店名｜區段」', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${slug}`);
    await expect(page).toHaveTitle(`${storeName}｜${BRAND}`);
    await page.goto(`/stores/${slug}/products`);
    await expect(page).toHaveTitle(`${storeName}｜商品｜${BRAND}`);
    await page.goto(`/stores/${slug}/rooms`);
    await expect(page).toHaveTitle(`${storeName}｜房源｜${BRAND}`);
    await page.goto(`/stores/${slug}/blog`);
    await expect(page).toHaveTitle(`${storeName}｜部落格｜${BRAND}`);
  });

  test('E2E-ENTITY-02: 商品、房源詳情頁的標題是商品／房型名稱', async ({ page }: { page: Page }) => {
    await page.goto(`/listings/${productId}`);
    await expect(page).toHaveTitle(`${PRODUCT_NAME}｜${BRAND}`);
    await page.goto(`/listings/${roomId}`);
    await expect(page).toHaveTitle(`${ROOM_NAME}｜${BRAND}`);
  });

  test('E2E-ENTITY-03: 在店鋪頁內用軟導覽換頁（點「商品」分頁）→ 標題跟著換成「店名｜商品」', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${slug}`);
    await expect(page).toHaveTitle(`${storeName}｜${BRAND}`);
    await page.getByTestId('store-nav-products').click();
    await expect(page).toHaveURL(new RegExp(`/stores/${slug}/products$`));
    await expect(page).toHaveTitle(`${storeName}｜商品｜${BRAND}`);
  });

  test('E2E-ENTITY-04: 爬蟲看到的原始 HTML（不執行 JavaScript）就有店名標題與 Open Graph；og:url 是絕對網址（metadataBase）', async ({ request }) => {
    const html = await (await request.get(`${WEB_BASE}/stores/${slug}`)).text();
    expect(html, '原始 HTML 的 <title>').toContain(`<title>${storeName}｜${BRAND}</title>`);
    expect(html, 'og:title').toMatch(new RegExp(`<meta property="og:title" content="${storeName}"`));
    expect(html, 'og:description（店鋪說明）').toMatch(/<meta property="og:description" content="E2E 實體標題店 自建前提"/);
    expect(html, 'og:site_name').toContain(`<meta property="og:site_name" content="${BRAND}"`);
    expect(html, 'og:url 必須是絕對網址：相對路徑靠根 layout 的 metadataBase 組成').toMatch(
      new RegExp(`<meta property="og:url" content="https://www\\.nextkey\\.com/stores/${slug}"`)
    );
    expect(html, 'meta description').toMatch(/<meta name="description" content="E2E 實體標題店 自建前提"/);

    const listingHtml = await (await request.get(`${WEB_BASE}/listings/${productId}`)).text();
    expect(listingHtml).toContain(`<title>${PRODUCT_NAME}｜${BRAND}</title>`);
    expect(listingHtml).toMatch(new RegExp(`<meta property="og:title" content="${PRODUCT_NAME}"`));
  });

  test('E2E-ENTITY-05: 找不到的店鋪／商品（真實後端回 404）→ 退回原本的頁型標題，而且沒有任何 Open Graph 標籤', async ({ page, request }: { page: Page; request: import('@playwright/test').APIRequestContext }) => {
    await page.goto('/stores/no-such-store-zzz');
    await expect(page).toHaveTitle(`店鋪首頁｜${BRAND}`);
    await page.goto(`/listings/${NO_SUCH_LISTING}`);
    await expect(page).toHaveTitle(`商品／房源詳情｜${BRAND}`);
    // id 不是 UUID：不會去問後端（單元測試守），畫面標題同樣是頁型標題
    await page.goto('/listings/not-a-uuid');
    await expect(page).toHaveTitle(`商品／房源詳情｜${BRAND}`);

    const html = await (await request.get(`${WEB_BASE}/stores/no-such-store-zzz`)).text();
    expect(html, '取不到店鋪時不能留下殘缺的分享資訊').not.toContain('property="og:title"');
  });

  test('E2E-ENTITY-06: 停權的店鋪不洩漏店名：後端對非 ACTIVE 店鋪回 404 → 標題退回頁型標題；恢復後又帶店名', async ({ page, request }: { page: Page; request: import('@playwright/test').APIRequestContext }) => {
    const adminToken = await adminAccessToken(page);
    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    try {
      const html = await (await request.get(`${WEB_BASE}/stores/${slug}`)).text();
      expect(html, '停權店鋪的店名不能出現在標題裡').not.toContain(storeName);
      expect(html).toContain(`<title>店鋪首頁｜${BRAND}</title>`);
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }
    const restored = await (await request.get(`${WEB_BASE}/stores/${slug}`)).text();
    expect(restored, '不快取：恢復營業後標題立刻回來').toContain(`<title>${storeName}｜${BRAND}</title>`);
  });
  test('E2E-ENTITY-07: 商品被刪除（不是 ACTIVE）之後，名稱不能留在標題裡——後端對非 ACTIVE 刊登一律回同一個 404 E-3000，標題退回頁型標題', async ({ page, request }: { page: Page; request: import('@playwright/test').APIRequestContext }) => {
    const name = `待刪除商品${STAMP}`;
    await loginOnly(page, owner.email, owner.password);
    const created = await page.request.post(`${API_BASE}/v2/dashboard/listings`, {
      headers: await authHeaders(page),
      data: { listingType: 'PRODUCT', name, description: '會被刪除', price: 99, category: '生活', brand: 'E2E' },
    });
    expect(created.status(), await created.text()).toBe(200);
    const id = (await created.json()).data.listingId as string;

    const before = await (await request.get(`${WEB_BASE}/listings/${id}`)).text();
    expect(before, '上架中：標題是商品名').toContain(`<title>${name}｜${BRAND}</title>`);

    const removed = await page.request.delete(`${API_BASE}/v2/products/${id}`, { headers: await authHeaders(page) });
    expect([200, 204], await removed.text()).toContain(removed.status());

    const after = await (await request.get(`${WEB_BASE}/listings/${id}`)).text();
    expect(after, '刪除之後商品名不能再出現在標題或分享資訊裡').not.toContain(name);
    expect(after).toContain(`<title>商品／房源詳情｜${BRAND}</title>`);
  });
});
