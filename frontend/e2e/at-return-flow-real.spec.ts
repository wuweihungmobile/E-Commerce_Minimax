import { test, expect, Locator, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-RETURN-FLOW-REAL: 退貨申請→審核→收貨（真實後端，完全不 mock）—— Sprint 300
 *
 * 為什麼需要：買家退貨申請頁（/returns/new、/returns/[id]）與店主退貨審核台（/dashboard/returns、
 * /dashboard/returns/[id]）在這之前從未被瀏覽器開過；後端退貨整合測試（M05ReturnRequestIntegrationTest）
 * 又只種「有 SKU」的訂單品項。Sprint 300 用真實資料掃描這些頁面時發現兩個缺陷：
 *   1. DEF-404：商品沒建 SKU（＝未啟用庫存追蹤，建立商品的預設狀態）時，買家申請退貨回 500——
 *      return_request_items.sku_id 是 NOT NULL，服務層卻刻意允許 null（V93 修正）。→ E2E-RETURN-02
 *   2. DEF-406：四個頁面的商品縮圖在沒有封面圖時指向不存在的 /placeholder.png，顯示成破圖。→ E2E-RETURN-01／02／03
 * 兩個缺陷刻意拆成各自獨立的案例：serial 模式下第一個失敗會讓後面的案例不執行，若縮圖檢查排在申請退貨之前
 * 又寫在同一個案例，就看不到後面那個缺陷自己的紅燈。
 *
 * 資料全由真實流程建立：店主註冊→驗證 Email→申請開店→管理員核准→店主重新登入→建商品（**不建 SKU**，正是
 * DEF-404 的情境）；消費者另行註冊、下單、付款；店主把訂單推進到「已送達」（退貨的前提）。
 * 登入密度：全檔約 8 次登入（含開店前提的 4 次）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PRODUCT_NAME = 'E2E 退貨商品';
const PRODUCT_PRICE = 450;
const REASON = 'E2E 商品與描述不符';

/** 收集所有失敗的圖片請求（>=400）；回傳的陣列隨後續請求即時增長。 */
function trackBrokenImages(page: Page): string[] {
  const broken: string[] = [];
  page.on('response', (response) => {
    if (response.request().resourceType() === 'image' && response.status() >= 400) {
      broken.push(`${response.status()} ${new URL(response.url()).pathname}`);
    }
  });
  return broken;
}

/** 縮圖確實載入成功（沒有封面圖時應顯示預設圖，而不是破圖）。 */
async function expectImageLoaded(image: Locator) {
  await expect(image).toBeVisible({ timeout: 20000 });
  await expect
    .poll(() => image.evaluate((el) => (el as HTMLImageElement).complete && (el as HTMLImageElement).naturalWidth > 0), {
      message: '商品縮圖應載入成功',
      timeout: 10000,
    })
    .toBe(true);
}

async function loginAs(page: Page, account: Account) {
  await page.goto('/login');
  await page.evaluate(() => localStorage.clear());
  await loginOnly(page, account.email, account.password);
}

test.describe('AT-RETURN-FLOW-REAL: 退貨申請→審核→收貨（真實後端）', () => {
  // 後面的案例都依賴前面建立的退貨單，且開店與下單流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let customer: Account;
  let orderId: string;
  let returnId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(180_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const store = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 退貨店',
        product: { name: PRODUCT_NAME, price: PRODUCT_PRICE },
      });
      owner = store.owner;
      const ownerHeaders = await authHeaders(page);

      await page.evaluate(() => localStorage.clear());
      customer = await registerAndLogin(page);
      const customerHeaders = await authHeaders(page);
      const add = await page.request.post(`${API_BASE}/v2/cart/items`, {
        headers: customerHeaders,
        data: { listingId: store.productId, quantity: 2 },
      });
      expect(add.status(), await add.text()).toBeLessThan(300);
      const created = await page.request.post(`${API_BASE}/v2/orders`, {
        headers: customerHeaders,
        data: {
          orderType: 'PRODUCT',
          shippingAddress: '台北市信義區信義路五段 7 號',
          shippingRecipientName: 'E2E 收件人',
          shippingPhone: '0987654321',
        },
      });
      expect(created.status(), await created.text()).toBe(201);
      orderId = (await created.json()).data.id as string;
      const pay = await page.request.post(`${API_BASE}/v2/orders/${orderId}/pay`, { headers: customerHeaders });
      expect(pay.status(), await pay.text()).toBe(200);

      // 退貨的前提是訂單已送達：店主依狀態機逐步推進
      for (const targetStatus of ['CONFIRMED', 'SHIPPING', 'DELIVERED']) {
        const step = await page.request.patch(`${API_BASE}/v2/orders/${orderId}/status`, {
          headers: ownerHeaders,
          data: { targetStatus },
        });
        expect(step.status(), await step.text()).toBe(200);
      }
    } finally {
      await page.close();
    }
  });

  test('E2E-RETURN-01: 沒有封面圖的商品縮圖顯示預設圖而不是破圖：買家的訂單詳情與退貨申請頁（DEF-406）', async ({ page }: { page: Page }) => {
    await loginAs(page, customer);
    const brokenImages = trackBrokenImages(page);

    await page.goto(`/orders/${orderId}`);
    await expectImageLoaded(page.getByRole('img', { name: PRODUCT_NAME }));

    await page.getByRole('link', { name: '申請退貨' }).click();
    await expect(page).toHaveURL(new RegExp(`/returns/new\\?orderId=${orderId}$`), { timeout: 15000 });
    await expectImageLoaded(page.getByRole('img', { name: PRODUCT_NAME }));

    expect(brokenImages, '不得有失敗的圖片請求（例如不存在的預設圖）').toEqual([]);
  });

  test('E2E-RETURN-02: 買家申請退貨（商品沒有 SKU）→ 退貨單建立、待審核（DEF-404）', async ({ page }: { page: Page }) => {
    await loginAs(page, customer);

    await page.goto(`/returns/new?orderId=${orderId}`);
    await page.locator('input[id^="qty-"]').fill('1');
    await page.getByPlaceholder('請說明退貨原因，方便店家審核').fill(REASON);
    await page.getByRole('button', { name: '送出退貨申請' }).click();

    // 修復前：後端 500，停在申請頁並顯示「提交退貨申請失敗」
    await expect(page).toHaveURL(/\/returns\/[0-9a-f-]{36}$/, { timeout: 20000 });
    returnId = new URL(page.url()).pathname.split('/').pop() as string;
    await expect(page.getByText('待審核')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(REASON)).toBeVisible();
    await expectImageLoaded(page.getByRole('img', { name: PRODUCT_NAME }));
  });

  test('E2E-RETURN-03: 店主在退貨審核台核准並確認收貨 → 已完成（沒有 SKU 的品項不需要、也不會回補庫存）', async ({ page }: { page: Page }) => {
    await loginAs(page, owner);

    // 店主的訂單詳情也用同一個預設縮圖（DEF-406 的第四個使用點）
    await page.goto(`/dashboard/orders/${orderId}`);
    await expectImageLoaded(page.getByRole('img', { name: PRODUCT_NAME }));

    await page.goto('/dashboard/returns');
    await expect(page.getByRole('heading', { name: '退貨審核' })).toBeVisible({ timeout: 20000 });
    const row = page.locator(`a[href="/dashboard/returns/${returnId}"]`);
    await expect(row, '審核台列表應列出買家剛送出的退貨單').toBeVisible({ timeout: 10000 });
    await expect(row).toContainText('待審核');

    await row.click();
    await expect(page).toHaveURL(new RegExp(`/dashboard/returns/${returnId}$`), { timeout: 15000 });
    await expect(page.getByText(REASON)).toBeVisible({ timeout: 15000 });

    await page.getByRole('button', { name: '核准', exact: true }).click();
    await expect(page.getByText('已核准，待收貨')).toBeVisible({ timeout: 15000 });

    // 預設可售數量＝申請數量（1）、不可售 0
    await page.getByRole('button', { name: '確認收貨' }).click();
    await expect(page.getByText('已完成')).toBeVisible({ timeout: 15000 });
  });

  test('E2E-RETURN-04: 買家的退貨列表與詳情看到最終狀態「已完成」', async ({ page }: { page: Page }) => {
    await loginAs(page, customer);

    await page.goto('/returns');
    const row = page.locator(`a[href="/returns/${returnId}"]`);
    await expect(row, '買家的退貨列表應列出這張退貨單').toBeVisible({ timeout: 20000 });
    await expect(row).toContainText('已完成');

    await row.click();
    await expect(page).toHaveURL(new RegExp(`/returns/${returnId}$`), { timeout: 15000 });
    await expect(page.getByText('已完成')).toBeVisible({ timeout: 15000 });
  });
});
