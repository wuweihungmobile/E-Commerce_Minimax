import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-STORE-CHECKOUT-REAL: 同店結帳（真實後端，完全不 mock）—— Sprint 237（DEF-319 訂單側）
 *
 * 為什麼需要：PRD US-008／PC-005：單筆訂單限同一商家。訂單原本蓋成「下單者的租戶」，沒有店鋪的真實消費者是系統租戶佔位值，
 * 所以店主讀不到也處理不了客人的訂單（Sprint 232 實測）。Sprint 237 起訂單歸屬商品所屬的店鋪，購物車含多家店鋪的商品時
 * 必須指定要結哪一家（E-5020），其餘留在購物車分開結帳。單元與整合測試把 Redis 或資料庫的一部分換成固件，這裡用打包 JAR＋
 * PostgreSQL＋Redis＋兩家真實店鋪（真實開店流程）＋一位沒有店鋪的真實消費者走完整條路徑。
 *
 * 資料全由真實流程建立（與 at-seller-dashboard-real 同一套前提，見 helpers/store.ts）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PRICE_A = 300;
const PRICE_B = 500;
const RECIPIENT_A = 'E2E 同店結帳收件人A';
const RECIPIENT_B = 'E2E 同店結帳收件人B';

type OrderListItem = { id: string; shippingRecipientName?: string | null; status: string };
type CartItem = { listingId: string; storeId?: string | null; storeName?: string | null };
type StoreSummary = { storeId: string; storeName?: string | null; itemCount: number; totalAmount: number };

async function listTenantOrders(page: Page): Promise<OrderListItem[]> {
  const response = await page.request.get(`${API_BASE}/v2/orders/tenant?size=100`, { headers: await authHeaders(page) });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).data.content as OrderListItem[];
}

async function getCart(page: Page): Promise<{ items: CartItem[]; stores: StoreSummary[] }> {
  const response = await page.request.get(`${API_BASE}/v2/cart`, { headers: await authHeaders(page) });
  expect(response.status(), await response.text()).toBe(200);
  const data = (await response.json()).data;
  return { items: data.items as CartItem[], stores: data.stores as StoreSummary[] };
}

async function placeOrder(page: Page, recipient: string, storeId?: string) {
  return page.request.post(`${API_BASE}/v2/orders`, {
    headers: await authHeaders(page),
    data: {
      orderType: 'PRODUCT',
      storeId,
      shippingAddress: '台北市信義區信義路五段 7 號',
      shippingRecipientName: recipient,
      shippingPhone: '0987654321',
    },
  });
}

test.describe('AT-STORE-CHECKOUT-REAL: 同店結帳（真實後端）', () => {
  // 後面的案例都依賴前面建立的店鋪、商品與訂單，且開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let ownerA: Account;
  let ownerB: Account;
  let tenantA: string;
  let tenantB: string;
  let productA: string;
  let productB: string;
  let customer: Account;
  let orderA: string;
  let orderB: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    for (const label of ['A', 'B'] as const) {
      const page = await browser.newPage({ baseURL: WEB_BASE });
      try {
        const seeded = await seedStore(page, {
          businessType: 'RETAIL_ONLY',
          storeLabel: `E2E 同店結帳店鋪${label}`,
          product: { name: `E2E 店鋪${label}商品`, price: label === 'A' ? PRICE_A : PRICE_B },
        });
        if (label === 'A') {
          ownerA = seeded.owner;
          tenantA = seeded.tenantId;
          productA = seeded.productId as string;
        } else {
          ownerB = seeded.owner;
          tenantB = seeded.tenantId;
          productB = seeded.productId as string;
        }
      } finally {
        await page.close();
      }
    }
  });

  test('E2E-SCHK-01: 消費者（不屬於任何店鋪）把兩家店鋪的商品放進同一個購物車 → 購物車依店鋪分組', async ({ page }: { page: Page }) => {
    expect(tenantA).not.toBe(tenantB);
    customer = await registerAndLogin(page);
    const headers = await authHeaders(page);
    for (const listingId of [productA, productB]) {
      const add = await page.request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId, quantity: 1 } });
      expect(add.status(), await add.text()).toBeLessThan(300);
    }

    const cart = await getCart(page);
    expect(cart.items.map((i) => i.storeId).sort(), '每個項目都帶出所屬店鋪').toEqual([tenantA, tenantB].sort());
    expect(cart.stores, '依店鋪分組的摘要').toHaveLength(2);
    const summaryA = cart.stores.find((s) => s.storeId === tenantA)!;
    const summaryB = cart.stores.find((s) => s.storeId === tenantB)!;
    expect(summaryA.totalAmount).toBe(PRICE_A);
    expect(summaryB.totalAmount).toBe(PRICE_B);
    expect(summaryA.storeName, '店鋪名稱供購物車顯示').toContain('E2E 同店結帳店鋪A');
  });

  test('E2E-SCHK-02: 購物車含兩家店鋪的商品、沒指定店鋪就結帳 → 400 E-5020，不建立訂單、購物車原封不動', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, customer.email, customer.password);

    const response = await placeOrder(page, RECIPIENT_A);
    expect(response.status(), await response.text()).toBe(400);
    expect((await response.json()).code).toBe('E-5020');
    expect((await getCart(page)).items, '購物車沒有被動到').toHaveLength(2);
  });

  test('E2E-SCHK-03: 指定店鋪 A 結帳 → 訂單歸店鋪 A；店鋪 A 的店主看得到，店鋪 B 的店主看不到；店鋪 B 的商品留在購物車', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, customer.email, customer.password);
    const created = await placeOrder(page, RECIPIENT_A, tenantA);
    expect(created.status(), await created.text()).toBe(201);
    orderA = (await created.json()).data.id as string;
    const cart = await getCart(page);
    expect(cart.items.map((i) => i.listingId), '只剩店鋪 B 的商品').toEqual([productB]);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, ownerA.email, ownerA.password);
    const ownersOrders = await listTenantOrders(page);
    const mine = ownersOrders.find((o) => o.id === orderA);
    expect(mine, `店鋪 A 的店主應看得到消費者的訂單（目前共 ${ownersOrders.length} 筆）`).toBeTruthy();
    expect(mine!.shippingRecipientName).toBe(RECIPIENT_A);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, ownerB.email, ownerB.password);
    expect((await listTenantOrders(page)).map((o) => o.id), '店鋪 B 的店主看不到店鋪 A 的訂單').not.toContain(orderA);
  });

  test('E2E-SCHK-04: 店鋪 B 的商品之後自己結帳（只剩一家店鋪、不必指定）→ 訂單歸店鋪 B，店鋪 A 的店主看不到', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, customer.email, customer.password);
    const created = await placeOrder(page, RECIPIENT_B);
    expect(created.status(), await created.text()).toBe(201);
    orderB = (await created.json()).data.id as string;
    expect((await getCart(page)).items, '購物車已清空').toHaveLength(0);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, ownerB.email, ownerB.password);
    const ownersOrders = await listTenantOrders(page);
    const mine = ownersOrders.find((o) => o.id === orderB);
    expect(mine, '店鋪 B 的店主看得到').toBeTruthy();
    expect(mine!.shippingRecipientName).toBe(RECIPIENT_B);
    expect(ownersOrders.map((o) => o.id), '店鋪 B 看不到店鋪 A 的').not.toContain(orderA);

    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, ownerA.email, ownerA.password);
    expect((await listTenantOrders(page)).map((o) => o.id), '店鋪 A 看不到店鋪 B 的').not.toContain(orderB);
  });
});
