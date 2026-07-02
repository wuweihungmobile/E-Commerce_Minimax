import { test, expect, Page } from '@playwright/test';

/**
 * AT-LISTING-DETAIL: 買家商品詳情頁 + 首頁「有資料」E2E（Sprint 38 US-001 AI-2103 / US-002 AI-1905）
 *
 * 以 page.route mock `/v2/listings*`（比照 at-homepage E2E-HOME-05/06，免後端 seed → 穩健不 flaky）
 * 驗證：首頁有資料網格 + 分頁翻頁、點卡導向詳情、詳情頁渲染 + PRODUCT 加購、詳情頁 401/404 鑑別。
 *
 * - E2E-DETAIL-01: 首頁有資料網格 + 分頁翻頁（AI-1905）
 * - E2E-DETAIL-02: 點商品卡 → 詳情頁渲染 + PRODUCT 加入購物車
 * - E2E-DETAIL-03: 詳情頁 401 → 恰為登入引導
 * - E2E-DETAIL-04: 詳情頁 404 → 找不到商品
 */

interface MockListing {
  id: string;
  tenantId: string;
  listingType: 'PRODUCT' | 'ROOM';
  title: string;
  description: string;
  coverImageUrl: string | null;
  status: string;
  basePrice: number;
  currency: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

function makeListing(
  title: string,
  id: string = title,
  type: 'PRODUCT' | 'ROOM' = 'PRODUCT'
): MockListing {
  return {
    id,
    tenantId: 't1',
    listingType: type,
    title,
    description: '這是測試商品描述。',
    coverImageUrl: null,
    status: 'ACTIVE',
    basePrice: 1200,
    currency: 'TWD',
    tags: ['測試'],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function pageBody(content: MockListing[], number: number, totalPages: number) {
  return JSON.stringify({
    success: true,
    data: { content, totalElements: totalPages * 12, totalPages, number, size: 12 },
  });
}

async function fulfillJson(route: import('@playwright/test').Route, status: number, body: string) {
  await route.fulfill({ status, contentType: 'application/json', body });
}

test.describe('AT-LISTING-DETAIL: 商品詳情頁 + 首頁有資料（S38）', () => {
  test('E2E-DETAIL-01: 首頁有資料網格 + 分頁翻頁', async ({ page }: { page: Page }) => {
    // list mock：依 page 參數回不同內容（每頁首張帶唯一標記）
    await page.route('**/v2/listings**', async (route) => {
      const num = Number(new URL(route.request().url()).searchParams.get('page') ?? '0');
      const content = [
        makeListing(`頁${num + 1}標記`),
        ...Array.from({ length: 11 }, (_, i) => makeListing(`頁${num + 1}商品${i + 2}`)),
      ];
      await fulfillJson(route, 200, pageBody(content, num, 3));
    });

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // 有資料 → 商品網格 + 分頁控制項
    await expect(page.getByTestId('product-grid')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('頁1標記', { exact: true })).toBeVisible();

    // 翻到第 2 頁 → 內容更新（頁2標記出現、頁1標記消失）
    await page.getByRole('button', { name: '2', exact: true }).click();
    await expect(page.getByText('頁2標記', { exact: true })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('頁1標記', { exact: true })).toHaveCount(0);
  });

  test('E2E-DETAIL-02: 點商品卡 → 詳情頁渲染 + PRODUCT 加入購物車', async ({ page }: { page: Page }) => {
    const listingId = '11111111-1111-1111-1111-111111111111';

    await page.route('**/v2/listings**', async (route) => {
      const url = route.request().url();
      if (url.includes(`/v2/listings/${listingId}`)) {
        // 詳情
        await fulfillJson(
          route,
          200,
          JSON.stringify({ success: true, data: makeListing('可購買的詳情商品', listingId) })
        );
      } else {
        // list：第一張卡連到 listingId
        const content = [
          makeListing('可點入的商品', listingId),
          ...Array.from({ length: 11 }, (_, i) => makeListing(`其他商品${i + 2}`)),
        ];
        await fulfillJson(route, 200, pageBody(content, 0, 1));
      }
    });
    await page.route('**/v2/cart/items', async (route) => {
      await fulfillJson(
        route,
        200,
        JSON.stringify({ success: true, data: { cartItemKey: 'k1', listingId, quantity: 1 } })
      );
    });

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // 點第一張卡（href=/listings/{listingId}）
    await page.getByText('可點入的商品', { exact: true }).click();
    await page.waitForURL(`**/listings/${listingId}`, { timeout: 15000 });

    // 詳情頁渲染
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('可購買的詳情商品', { exact: true })).toBeVisible();

    // PRODUCT 加入購物車 → 成功回饋
    await page.getByTestId('listing-add-cart').click();
    await expect(page.getByTestId('listing-add-success')).toBeVisible({ timeout: 10000 });
  });

  test('E2E-DETAIL-03: 詳情頁 401 → 恰為登入引導', async ({ page }: { page: Page }) => {
    const id = '22222222-2222-2222-2222-222222222222';
    await page.route(`**/v2/listings/${id}`, async (route) => {
      await fulfillJson(route, 401, JSON.stringify({ success: false, message: 'unauthorized' }));
    });

    await page.goto(`/listings/${id}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('listing-auth-empty')).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('link', { name: '前往登入' })).toBeVisible();
    await expect(page.getByTestId('listing-detail')).toHaveCount(0);
  });

  test('E2E-DETAIL-04: 詳情頁 404 → 找不到商品', async ({ page }: { page: Page }) => {
    const id = '33333333-3333-3333-3333-333333333333';
    await page.route(`**/v2/listings/${id}`, async (route) => {
      await fulfillJson(route, 404, JSON.stringify({ success: false, message: 'not found' }));
    });

    await page.goto(`/listings/${id}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('listing-notfound')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-detail')).toHaveCount(0);
  });
});
