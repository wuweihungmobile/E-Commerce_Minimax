import { test, expect } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, seedStore } from './helpers/store';

/**
 * AT-SELLER-PRODUCT-LIST-REAL: 店主的商品管理列表頁（真實後端，完全不 mock）—— Sprint 298
 *
 * 為什麼需要：`/dashboard/products` 的類別篩選（components/product/ProductList.tsx）原本有一個
 * `<SelectItem value="">所有類別</SelectItem>`。Radix UI 的 Select.Item 在 render 時對空字串 value 直接 throw
 * （@radix-ui/react-select 2.x 的明文限制），而這個 Select 在資料載入完成後才 render——所以任何店主開這頁，
 * 列表一載入就整頁崩潰（Next 的「This page couldn't load」）；Select 的 render 與商品數量無關。
 * 那一行與 Radix 依賴都是 2026-05-10 同一天進來的，這頁從來沒有成功渲染過；既有 E2E 只有 at-m15-e2e.spec.ts 的「商品頁面」
 * 開過這個路由，但它用沒有店鋪的帳號、只等 domcontentloaded、只 console.log、沒有任何斷言，不可能失敗。
 * Sprint 298 用真實全棧走訪 S297 動過的頁面時才第一次跑出來。
 *
 * 修法：Radix 的標準做法——「所有類別」改用哨兵值 ALL，`category` 狀態與送出的篩選參數仍維持「空字串＝不篩選」。
 * 所以 E2E-PRODLIST-02 特別守住「選回所有類別時請求不帶 category」：哨兵值若直接漏進請求，會變成 category=ALL。
 *
 * 資料全由真實流程建立（見 helpers/store.ts）：店主註冊→驗證 Email→申請開店→管理員核准→店主重新登入→建立商品。
 */

const WEB_BASE = 'http://localhost:3000';
const PRODUCT_NAME = 'E2E 商品列表測試杯';

test.describe('AT-SELLER-PRODUCT-LIST-REAL: 店主商品管理列表頁（真實後端）', () => {
  // 開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ owner } = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 商品列表店',
        product: { name: PRODUCT_NAME, price: 450 },
      }));
    } finally {
      await page.close();
    }
  });

  test('E2E-PRODLIST-01: 列表載入後顯示店主自己的商品，頁面沒有崩潰', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(String(error)));

    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/products');

    await expect(page.getByText(PRODUCT_NAME)).toBeVisible();
    await expect(page.getByText('This page couldn’t load')).toHaveCount(0);
    expect(pageErrors, `頁面不得有未捕捉的 render 錯誤：${pageErrors.join(' | ')}`).toEqual([]);
  });

  test('E2E-PRODLIST-02: 類別篩選——選類別請求帶 category，選回「所有類別」請求不帶 category', async ({ page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/products');
    await expect(page.getByText(PRODUCT_NAME)).toBeVisible();

    const trigger = page.locator('button[role="combobox"]');
    const isProductsRequest = (url: string) => /\/v2\/products\?/.test(url);

    // 選「電子產品」→ 重新抓取，且帶 category=ELECTRONICS
    const filtered = page.waitForRequest((req) => isProductsRequest(req.url()) && req.url().includes('category=ELECTRONICS'));
    await trigger.click();
    await page.getByRole('option', { name: '電子產品' }).click();
    await filtered;

    // 重新載入完成（Select 在載入期間會卸載、載完重新掛上）→ 選回「所有類別」→ 請求不得帶 category
    await expect(trigger).toBeVisible();
    const cleared = page.waitForRequest((req) => isProductsRequest(req.url()));
    await trigger.click();
    await page.getByRole('option', { name: '所有類別' }).click();
    const clearedRequest = await cleared;
    expect(clearedRequest.url(), '選回所有類別時不得把哨兵值或空類別送進請求').not.toContain('category=');

    await expect(page.getByText(PRODUCT_NAME)).toBeVisible();
  });
});
