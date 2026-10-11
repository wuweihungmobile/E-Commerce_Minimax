import { test, expect, Page } from '@playwright/test';
import { authHeaders, seedStore } from './helpers/store';

/**
 * AT-STOREFRONT-REAL: 店鋪前台 /stores/[slug]（DEF-321(b)，Sprint 252，真實後端）
 *
 * PRD §12.5／§12.7 要求 Guest（未登入）可瀏覽店鋪前台；既有 `/v2/listings` 對匿名請求會回 401
 * （SecurityConfig 未放行），本輪新增的 `/v2/public/stores/**` 才是真正可匿名呼叫的入口。本規格
 * 刻意不呼叫任何登入 API 即直接造訪頁面，驗證真正的匿名瀏覽器工作階段能看到內容。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

test.describe('AT-STOREFRONT-REAL: 店鋪前台匿名瀏覽（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let slug: string;
  let productId: string;
  let roomId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const { tenantId, productId: pid, roomId: rid } = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 店鋪前台店',
        product: { name: '店鋪前台測試商品', price: 888 },
        room: { name: '店鋪前台測試房型', price: 2888 },
      });
      productId = pid!;
      roomId = rid!;

      // 以本輪公開端點（匿名可呼叫）取得 slug，同時間接驗證端點本身可用
      const profile = await page.request.get(`${API_BASE}/v2/tenants/${tenantId}`);
      expect(profile.status(), await profile.text()).toBe(200);
      slug = (await profile.json()).data.slug as string;
      expect(slug, '開店審核通過應已自動產生 slug').toBeTruthy();

      // 店主把商品標記為「精選」，驗證首頁精選區塊
      const markFeatured = await page.request.put(`${API_BASE}/v2/products/${productId}`, {
        headers: await authHeaders(page),
        data: { isFeatured: true },
      });
      expect(markFeatured.status(), await markFeatured.text()).toBe(200);
    } finally {
      await page.close();
    }
  });

  test('E2E-STOREFRONT-01: 匿名訪客造訪首頁 → 看到店名與精選商品', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${slug}`);
    await expect(page.getByTestId('store-header')).toBeVisible();
    await expect(page.getByTestId('store-hero-banner')).toBeVisible();
    await expect(page.getByTestId('store-featured-listings')).toBeVisible();
    await expect(page.getByTestId('store-featured-listings')).toContainText('店鋪前台測試商品');
  });

  test('E2E-STOREFRONT-02: 匿名訪客切到「商品」分頁 → 看到該店商品', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${slug}`);
    await page.getByTestId('store-nav-products').click();
    await expect(page).toHaveURL(new RegExp(`/stores/${slug}/products$`));
    await expect(page.getByTestId('store-listing-grid')).toContainText('店鋪前台測試商品');
  });

  test('E2E-STOREFRONT-03: 匿名訪客切到「房源」分頁 → 看到該店房源', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${slug}/rooms`);
    await expect(page.getByTestId('store-listing-grid')).toContainText('店鋪前台測試房型');
  });

  test('E2E-STOREFRONT-04: 點擊商品卡 → 導向標準商品詳情頁', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/${slug}/products`);
    await page.getByText('店鋪前台測試商品').click();
    await expect(page).toHaveURL(new RegExp(`/listings/${productId}$`));
  });

  test('E2E-STOREFRONT-05: 不存在的 slug → 顯示「找不到這間店鋪」而非崩潰', async ({ page }: { page: Page }) => {
    await page.goto(`/stores/this-store-does-not-exist-${Date.now()}`);
    await expect(page.getByTestId('store-notfound')).toBeVisible();
  });
});
