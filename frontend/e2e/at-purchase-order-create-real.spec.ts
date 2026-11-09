import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-PO-CREATE-REAL: 賣家端採購單建立表單（DEF-479，Sprint 335，真實後端）
 *
 * 缺口：`/dashboard/erp/purchase-orders/new` 的「選供應商 → 選商品與規格 → 填數量與單價 → 送出 → 列表出現」整條流程
 * 沒有任何前端 E2E（Sprint 333 動了這個元件，只靠 Vitest + mock 服務守）。表單的商品／規格下拉資料來自後端
 * `listing-options`，送出的 payload 形狀（listingId／skuId／unitCost）與後端的驗證互相依賴，mock 掉任一端都證明不了這條路真的通。
 *
 * 前提全走真實流程：開店 → API 建一個供應商 → API 建商品的**兩個**規格（SKU）。商品只有一個規格時表單會自動帶入（Sprint 178 的設計，
 * 減少一次點擊）；有多個規格時不自動選，避免收錯規格——所以 01 要自己選、02 驗證沒選就送出會被擋下。
 * 與 `DEF-234`（送審／收貨入口）同一條流程的第一段。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const STAMP = Date.now();
const SUPPLIER_NAME = `E2E 供應商 ${STAMP}`;
const PRODUCT_NAME = `E2E 採購商品 ${STAMP}`;
const SKU_CODE = `PO-${STAMP}`;
const SKU_CODE_B = `PO-${STAMP}-B`;

// 頁首還有「切換色票主題」的下拉，所以不能依順序取 select；用各自的佔位選項定位
const supplierSelectOf = (page: Page) => page.locator('select', { has: page.locator('option', { hasText: '請選擇供應商' }) });
const productSelectOf = (page: Page) => page.locator('select', { has: page.locator('option', { hasText: '請選擇商品' }) });
const skuSelectOf = (page: Page) => page.locator('select', { has: page.locator('option', { hasText: '請選擇規格' }) });

test.describe('AT-PO-CREATE-REAL: 採購單建立（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let productId: string;
  let supplierId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 採購單店',
        product: { name: PRODUCT_NAME, price: 100 },
      });
      owner = seeded.owner;
      productId = seeded.productId!;

      const sku = await page.request.post(`${API_BASE}/v2/products/${productId}/skus`, {
        headers: await authHeaders(page),
        data: { skuCode: SKU_CODE, specName: '預設規格' },
      });
      expect(sku.status(), await sku.text()).toBe(201);
      const skuB = await page.request.post(`${API_BASE}/v2/products/${productId}/skus`, {
        headers: await authHeaders(page),
        data: { skuCode: SKU_CODE_B, specName: '第二規格' },
      });
      expect(skuB.status(), await skuB.text()).toBe(201);

      const supplier = await page.request.post(`${API_BASE}/v2/dashboard/suppliers`, {
        headers: await authHeaders(page),
        data: { name: SUPPLIER_NAME },
      });
      expect(supplier.status(), await supplier.text()).toBe(201);
      supplierId = (await supplier.json()).data.id as string;
    } finally {
      await page.close();
    }
  });

  test('E2E-POCREATE-01: 選供應商、商品與規格，填數量與單價，送出 → 回到列表且新採購單出現，後端存的金額正確', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/erp/purchase-orders/new');

    // 兩個下拉都載入完成（真實後端回來的選項）才能選
    const supplierSelect = supplierSelectOf(page);
    await expect(supplierSelect.locator('option', { hasText: SUPPLIER_NAME })).toHaveCount(1);
    await supplierSelect.selectOption({ label: SUPPLIER_NAME });

    const productSelect = productSelectOf(page);
    await expect(productSelect.locator('option', { hasText: PRODUCT_NAME })).toHaveCount(1);
    await productSelect.selectOption({ label: PRODUCT_NAME });

    // 選了商品之後才出現規格下拉
    const skuSelect = skuSelectOf(page);
    await expect(skuSelect.locator('option', { hasText: `${SKU_CODE} - 預設規格` })).toHaveCount(1);
    await skuSelect.selectOption({ label: `${SKU_CODE} - 預設規格` });

    // 品項列的兩個數字輸入：數量、單價
    const numbers = page.locator('tbody input[type="number"]');
    await numbers.nth(0).fill('5');
    await numbers.nth(1).fill('30');
    await expect(page.getByRole('cell', { name: '150', exact: true }), '小計＝5 × 30').toBeVisible();
    await expect(page.getByRole('cell', { name: '150 元' }), '總金額').toBeVisible();

    const created = page.waitForResponse(
      (r) => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/v2/dashboard/purchase-orders')
    );
    await page.getByRole('button', { name: '建立訂單' }).click();
    expect((await created).status(), '建立請求應成功').toBe(201);

    // 回到列表，新採購單出現（以供應商名稱辨認）
    await expect(page).toHaveURL(/\/dashboard\/erp\/purchase-orders$/);
    await expect(page.getByText(`供應商：${SUPPLIER_NAME}`)).toBeVisible();

    // 後端實際存的內容：供應商、品項數量、單價、總額
    const list = await page.request.get(`${API_BASE}/v2/dashboard/purchase-orders`, { headers: await authHeaders(page) });
    expect(list.status(), await list.text()).toBe(200);
    const orders = (await list.json()).data.content as Array<{
      id: string; supplierName: string; status: string; totalAmount: number;
      items: Array<{ quantity: number; unitPrice: number; skuId: string }>;
    }>;
    const mine = orders.find((o) => o.supplierName === SUPPLIER_NAME);
    expect(mine, '列表 API 應含剛建立的採購單').toBeTruthy();
    expect(mine!.status).toBe('DRAFT');
    expect(Number(mine!.totalAmount)).toBe(150);
    expect(mine!.items).toHaveLength(1);
    expect(mine!.items[0].quantity).toBe(5);
    expect(Number(mine!.items[0].unitPrice)).toBe(30);
  });

  test('E2E-POCREATE-02: 商品有兩個規格 → 不自動帶入；沒選規格就送出 → 前端擋下並說明原因，不送出建立請求', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/erp/purchase-orders/new');
    const supplierSelect = supplierSelectOf(page);
    await expect(supplierSelect.locator('option', { hasText: SUPPLIER_NAME })).toHaveCount(1);
    await supplierSelect.selectOption({ label: SUPPLIER_NAME });
    const productSelect = productSelectOf(page);
    await expect(productSelect.locator('option', { hasText: PRODUCT_NAME })).toHaveCount(1);
    await productSelect.selectOption({ label: PRODUCT_NAME });

    // 兩個規格 → 不自動帶入（只有一個規格時才會自動帶入）
    await expect(skuSelectOf(page)).toHaveValue('');

    let posted = 0;
    page.on('request', (r) => {
      if (r.method() === 'POST' && new URL(r.url()).pathname.endsWith('/v2/dashboard/purchase-orders')) posted++;
    });
    await page.getByRole('button', { name: '建立訂單' }).click();

    await expect(page.getByTestId('toast').filter({ hasText: '請為每個品項選擇規格' })).toBeVisible();
    expect(posted, '沒選規格不該送出建立請求（收貨時沒有 SKU 不會真正入庫）').toBe(0);
    await expect(page).toHaveURL(/\/purchase-orders\/new$/);
    // 供應商存在只是為了確認前提沒壞
    expect(supplierId).toBeTruthy();
  });
});
