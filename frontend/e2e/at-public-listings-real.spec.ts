import { test, expect, Page } from '@playwright/test';
import { seedStore } from './helpers/store';

/**
 * AT-PUBLIC-LISTINGS-REAL: 跨店鋪公開商品/房源瀏覽 /products、/rooms（DEF-358(a)，Sprint 259，真實後端）
 *
 * PRD §10.1.1 登記的缺口：商品/房源瀏覽先前僅能透過各店鋪 `/stores/{slug}/products`／`/rooms`
 * （Sprint 252），無跨店鋪聚合列表。本規格驗證新頁面與新增的跨租戶公開端點
 * `GET /v2/public/listings` 串接後的真實行為——不 mock，真實開兩家店，全程不登入（比照
 * PRD §12.7「Guest 可瀏覽所有商品與房源」，與既有 at-storefront-real.spec.ts 同一哲學）。
 *
 * 同時驗證本輪連動修復的首頁登入牆移除、/listings/[id] 詳情頁對 Guest 開放。
 */

const WEB_BASE = 'http://localhost:3000';

test.describe('AT-PUBLIC-LISTINGS-REAL: 跨店鋪公開瀏覽（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let storeAProductId: string;
  let storeBProductId: string;
  let storeARoomId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const marker = Date.now();
      const storeA = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: `E2E 跨店鋪瀏覽 A店 ${marker}`,
        product: { name: `跨店鋪商品A ${marker}`, price: 777 },
        room: { name: `跨店鋪房源A ${marker}`, price: 2777 },
      });
      storeAProductId = storeA.productId!;
      storeARoomId = storeA.roomId!;

      const storeB = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: `E2E 跨店鋪瀏覽 B店 ${marker}`,
        product: { name: `跨店鋪商品B ${marker}`, price: 888 },
      });
      storeBProductId = storeB.productId!;
    } finally {
      await page.close();
    }
  });

  test('E2E-PUBLIC-LISTINGS-01: 匿名訪客造訪 /products → 看到兩家店的商品（跨店鋪聚合）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await page.goto('/products');
    await expect(page.getByTestId('listing-type-grid')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-type-grid')).toContainText(`跨店鋪商品A`);
    await expect(page.getByTestId('listing-type-grid')).toContainText(`跨店鋪商品B`);
  });

  test('E2E-PUBLIC-LISTINGS-02: 匿名訪客造訪 /rooms → 只看到房源，點擊卡片導向正確的 listing', async ({
    page,
  }: {
    page: Page;
  }) => {
    await page.goto('/rooms');
    await expect(page.getByTestId('listing-type-grid')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-type-grid')).toContainText('跨店鋪房源A');

    await page.getByText('跨店鋪房源A', { exact: false }).first().click();
    await expect(page).toHaveURL(new RegExp(`/listings/${storeARoomId}$`));
  });

  test('E2E-PUBLIC-LISTINGS-03: 匿名訪客從 /products 點擊商品卡 → 可直接看到商品詳情（不被導向登入頁）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await page.goto('/products');
    await page.getByText('跨店鋪商品A', { exact: false }).first().click();
    await expect(page).toHaveURL(new RegExp(`/listings/${storeAProductId}$`));
    // 修復前：此端點要求登入，匿名訪客會卡在 listing-auth-empty（「請先登入以查看商品詳情」）。
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-auth-empty')).toHaveCount(0);
  });

  test('E2E-PUBLIC-LISTINGS-04: 匿名訪客造訪首頁 → 直接看到精選/列表商品，不再顯示登入引導', async ({
    page,
  }: {
    page: Page;
  }) => {
    await page.goto('/');
    await expect(page.getByTestId('product-grid').or(page.getByTestId('home-empty'))).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByTestId('home-auth-empty')).toHaveCount(0);
  });

  test('E2E-PUBLIC-LISTINGS-05: /listings/{id} 直接造訪（無登入）同樣可見商品詳情', async ({
    page,
  }: {
    page: Page;
  }) => {
    await page.goto(`/listings/${storeBProductId}`);
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-auth-empty')).toHaveCount(0);
  });
});
