import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-LOAD-FAILURE-SCREENS: 三個頁面的「載入失敗」畫面（DEF-471／472／473，Sprint 333，真實後端＋注入網路失敗）
 *
 * 缺陷共通點：載入失敗被畫成別的東西，使用者據此做出錯誤判斷。
 *   - 訊息中心 /dashboard/chat：對話清單失敗 → 畫成「尚無對話」（DEF-471）
 *   - 採購單建立頁：供應商下拉失敗 → 下拉只剩佔位選項，毫無提示，使用者無法建單（DEF-472）
 *   - 文章內嵌商品卡：任何失敗 → 畫成「此商品已下架」（DEF-473）
 *
 * 為什麼用真實後端：
 *   ①內嵌商品卡「只有 404（不存在）與 400（格式有誤）才是已下架」這個判準，是前端對後端錯誤碼的依賴（E-3000／E-9000）；
 *     元件測試的 mock 只能證明前端依 mock 的狀態碼分流，證明不了後端真的回這兩個碼——這裡用真實後端把這個契約釘住。
 *   ②「載入失敗」與「真的是空的」要在同一頁走過：失敗時顯示錯誤，恢復後（真實後端的清單本來就是空的）換成另一個畫面。
 * 失敗用 page.route 注入（網路中斷或 500），其餘都是真實請求；注入只針對單一端點，且在步驟中途移除，恢復後走真實後端。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
/** 格式正確（內嵌語法只認 [a-f0-9-]）但不存在的 listing id → 後端 404 E-3000 */
const MISSING_ID = '00000000-0000-4000-8000-00000000f473';
/** 格式有誤的 id（符合內嵌語法的字元集，但不是 UUID）→ 後端 400 E-9000 */
const MALFORMED_ID = 'abc';

test.describe('AT-LOAD-FAILURE-SCREENS: 載入失敗不被畫成別的東西（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let slug: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 載入失敗店',
        product: { name: '載入失敗測試商品', price: 321 },
      });
      owner = seeded.owner;

      const created = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: {
          title: 'E2E 載入失敗畫面測試貼文',
          content: `真實商品 {{embed:listing:${seeded.productId}}} 不存在的商品 {{embed:listing:${MISSING_ID}}} 格式有誤 {{embed:listing:${MALFORMED_ID}}}`,
          autoPublish: true,
        },
      });
      expect(created.status(), await created.text()).toBe(200);
      slug = (await created.json()).data.slug as string;
      expect(slug, '貼文應已產生 slug').toBeTruthy();
    } finally {
      await page.close();
    }
  });

  test('E2E-LOADFAIL-01: 內嵌商品卡——真實後端回 404／400 才是「此商品已下架」，真實商品照常顯示', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto(`/blog/${slug}`);

    await expect(page.getByText('載入失敗測試商品')).toBeVisible();
    // 不存在的 UUID（真實 404）與格式有誤的 id（真實 400）各一張
    const gone = page.getByTestId('embedded-card-gone');
    await expect(gone).toHaveCount(2);
    await expect(gone.first()).toContainText('此商品已下架');
    // 三張都落定之後，沒有任何一張是「載入失敗」
    await expect(page.getByTestId('embedded-card-error')).toHaveCount(0);
  });

  test('E2E-LOADFAIL-02: 內嵌商品卡——請求失敗（500）顯示載入失敗與「重新載入」，不說成「已下架」；恢復後各卡依真實結果顯示', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, owner.email, owner.password);
    const cardEndpoint = '**/v2/listings/*/card';
    await page.route(cardEndpoint, (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, code: 'E-9900', message: '模擬的伺服器錯誤' }),
      })
    );
    await page.goto(`/blog/${slug}`);

    const errors = page.getByTestId('embedded-card-error');
    await expect(errors, 'DEF-473：三張卡都該顯示載入失敗').toHaveCount(3);
    await expect(page.getByText('此商品已下架'), 'DEF-473：失敗不能被說成已下架').toHaveCount(0);

    // 恢復：之後的請求走真實後端；三張各自重試，每按一次就有一張換成真實結果
    await page.unroute(cardEndpoint);
    for (let remaining = 3; remaining > 0; remaining--) {
      await page.getByRole('button', { name: '重新載入' }).first().click();
      await expect(errors).toHaveCount(remaining - 1);
    }
    await expect(page.getByText('載入失敗測試商品')).toBeVisible();
    await expect(page.getByTestId('embedded-card-gone')).toHaveCount(2);
  });

  test('E2E-LOADFAIL-03: 訊息中心——對話清單請求失敗顯示錯誤與「重新載入」，不說成「尚無對話」；恢復後才是真的沒有對話', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, owner.email, owner.password);
    const endpoint = '**/v2/chat/conversations*';
    await page.route(endpoint, (route) => (route.request().method() === 'GET' ? route.abort('failed') : route.continue()));
    await page.goto('/dashboard/chat');

    await expect(page.getByTestId('conversation-error'), 'DEF-471：失敗要顯示錯誤').toBeVisible();
    await expect(page.getByTestId('conversation-empty'), 'DEF-471：失敗不能被說成沒有對話').toHaveCount(0);

    await page.unroute(endpoint);
    await page.getByRole('button', { name: '重新載入' }).click();
    // 這個店主沒有任何對話：恢復之後才是「尚無對話」
    await expect(page.getByTestId('conversation-empty')).toBeVisible();
    await expect(page.getByTestId('conversation-error')).toHaveCount(0);
  });

  test('E2E-LOADFAIL-04: 採購單建立頁——新店鋪沒有任何供應商 → 顯示「沒有啟用中的供應商」與新增入口，不是載入失敗', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/erp/purchase-orders/new');

    await expect(page.getByTestId('po-no-supplier-hint')).toContainText('沒有啟用中的供應商');
    await expect(page.getByRole('link', { name: '新增供應商' })).toHaveAttribute('href', '/dashboard/erp/suppliers/new');
    await expect(page.getByTestId('po-options-error'), '真的沒有供應商不是載入失敗').toHaveCount(0);
  });

  test('E2E-LOADFAIL-05: 採購單建立頁——供應商請求失敗顯示錯誤與「重新載入」；恢復後（真實清單是空的）換成「沒有啟用中的供應商」', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, owner.email, owner.password);
    const endpoint = '**/v2/dashboard/suppliers*';
    await page.route(endpoint, (route) => (route.request().method() === 'GET' ? route.abort('failed') : route.continue()));
    await page.goto('/dashboard/erp/purchase-orders/new');

    const error = page.getByTestId('po-options-error');
    await expect(error, 'DEF-472：失敗要明講').toContainText('載入供應商失敗');
    await expect(page.getByTestId('po-no-supplier-hint'), 'DEF-472：失敗不能被說成沒有供應商').toHaveCount(0);

    await page.unroute(endpoint);
    await page.getByRole('button', { name: '重新載入' }).click();
    await expect(page.getByTestId('po-no-supplier-hint')).toBeVisible();
    await expect(error).toHaveCount(0);
  });
});
