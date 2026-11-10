import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-EMBED-CARD-STATUS: 部落格嵌入卡片的狀態顯示（DEF-321(d)，Sprint 253，真實後端）
 *
 * PRD §6.6.3 規格：ROOM 類型進入 MAINTENANCE 時，嵌入卡片 `ctaUrl="/contact"`、`currentPrice=null`；
 * Listing 進入 INACTIVE/DELETED（Cascade）時，`statusReason="listing_unavailable"`、
 * `ctaUrl="/stores/{slug}"`。本規格依序讓同一組商品/房源卡經歷「正常 → 維護中 → 下架」三個真實狀態，
 * 驗證文章頁（Sprint 336 起是 /stores/{店鋪}/blog/{文章}）的渲染確實依 statusReason 分流文案與連結，而非先前固定顯示「查看詳情」。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

test.describe('AT-EMBED-CARD-STATUS: 嵌入卡片狀態顯示（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let storeSlug: string;
  let tenantId: string;
  let productId: string;
  let roomId: string;
  let slug: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 嵌入卡片狀態店',
        product: { name: '嵌入卡片測試商品', price: 777 },
        room: { name: '嵌入卡片測試房型', price: 1777 },
      });
      owner = seeded.owner;
      storeSlug = seeded.storeSlug;
      tenantId = seeded.tenantId;
      productId = seeded.productId!;
      roomId = seeded.roomId!;

      const createPost = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: {
          title: 'E2E 嵌入卡片狀態測試貼文',
          content: `商品 {{embed:listing:${productId}}} 房型 {{embed:listing:${roomId}}}`,
          autoPublish: true,
        },
      });
      expect(createPost.status(), await createPost.text()).toBe(200);
      slug = (await createPost.json()).data.slug as string;
      expect(slug, '貼文應已產生 slug').toBeTruthy();
    } finally {
      await page.close();
    }
  });

  test('E2E-EMBED-01: 正常狀態 → 商品卡與房型卡皆顯示價格與「查看詳情」', async ({ page }: { page: Page }) => {
    // 不登入：文章網址帶店鋪之後，匿名訪客就是看得到（Sprint 336，DEF-483）
    await page.goto(`/stores/${storeSlug}/blog/${slug}`);

    await expect(page.getByText('嵌入卡片測試商品')).toBeVisible();
    await expect(page.getByText('嵌入卡片測試房型')).toBeVisible();
    await expect(page.getByText('TWD 777').first()).toBeVisible();
    await expect(page.getByText('TWD 1,777').first()).toBeVisible();
    await expect(page.getByRole('link', { name: '查看詳情' })).toHaveCount(2);
  });

  test('E2E-EMBED-02: 房源進入維護中 → 房型卡顯示「房源維護中」、無定價、CTA 為聯絡客服', async ({
    page,
  }: {
    page: Page;
  }) => {
    // DEF-360（Sprint 255 已修復）：POST .../maintenance 原本對「從未被訂過、無 room_calendar 記錄」
    // 的房源會靜默空操作；本測試的房源正是這種剛建立、從未被訂的狀態，直接驗證真實 API 端到端行為
    // （而非像 PostControllerE2ETest 那樣直接寫 DB 繞過 markMaintenance 本身）。
    await loginOnly(page, owner.email, owner.password);

    const today = new Date();
    const startDate = today.toISOString().slice(0, 10);
    const endDate = new Date(today.getTime() + 7 * 86_400_000).toISOString().slice(0, 10);
    const markMaintenance = await page.request.post(
      `${API_BASE}/v2/dashboard/rooms/${roomId}/maintenance`,
      { headers: await authHeaders(page), data: { startDate, endDate } }
    );
    expect(markMaintenance.status(), await markMaintenance.text()).toBe(200);

    await page.goto(`/stores/${storeSlug}/blog/${slug}`);

    await expect(page.getByText('房源維護中')).toBeVisible();
    await expect(page.getByText('無定價')).toBeVisible();
    // 文章頁自 Sprint 328 起套用店面外殼，頁尾也有一個『聯絡客服』連結（/notifications）；
    // 這裡要驗的是嵌入卡片自己的 CTA（/contact），所以限定在卡片 <article> 內查詢
    const contactCta = page.getByRole('article').getByRole('link', { name: '聯絡客服' });
    await expect(contactCta).toHaveAttribute('href', '/contact');

    // 商品卡不受影響，仍是正常狀態
    await expect(page.getByText('TWD 777').first()).toBeVisible();
  });

  test('E2E-EMBED-03: 商品被刪除 → 商品卡顯示「此商品已下架」、CTA 導向店鋪', async ({ page }: { page: Page }) => {
    // Sprint 336（DEF-483）：這個案例原本用「店鋪被停權」讓卡片進入 listing_unavailable，再以店主身分打開文章。
    // 文章網址帶店鋪之後，停權店鋪的文章頁整頁不公開（與店鋪首頁、商品頁一致，見 E2E-EMBED-04），也就看不到卡片了；
    // 改成直接刪除商品——PRD §6.6.3 對 listing_unavailable 的定義本來就是「Listing 進入 INACTIVE／DELETED」。
    await loginOnly(page, owner.email, owner.password);
    const deleted = await page.request.delete(`${API_BASE}/v2/products/${productId}`, {
      headers: await authHeaders(page),
    });
    expect(deleted.status(), await deleted.text()).toBe(200);

    await page.goto(`/stores/${storeSlug}/blog/${slug}`);

    await expect(page.getByText('此商品已下架').first()).toBeVisible();
    const storeCta = page.getByRole('link', { name: '前往店鋪' }).first();
    await expect(storeCta).toHaveAttribute('href', new RegExp('^/stores/'));
  });

  test('E2E-EMBED-04: 店鋪被停權 → 文章頁整頁不公開（顯示「找不到這間店鋪」），文章與嵌入卡都不出現', async ({
    page,
  }: {
    page: Page;
  }) => {
    // setStoreStatus 內部會 localStorage.clear()，但本測試的 page 在這一步還沒導覽過任何頁面
    // （仍是 about:blank），存取 localStorage 會拋 SecurityError。setStoreStatusWithToken 改用
    // page.request + 既有 bearer token，不碰 localStorage，比照既有 at-store-suspended-payment-real.spec.ts。
    const adminToken = await adminAccessToken(page);
    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');

    await page.goto(`/stores/${storeSlug}/blog/${slug}`);

    await expect(page.getByTestId('store-notfound')).toBeVisible();
    await expect(page.getByText('E2E 嵌入卡片狀態測試貼文')).toHaveCount(0);
    await expect(page.getByText('此商品已下架')).toHaveCount(0);
  });
});
