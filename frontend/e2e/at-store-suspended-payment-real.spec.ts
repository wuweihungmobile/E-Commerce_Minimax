import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { registerAndLogin } from './helpers/auth';
import { Account, adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-STORE-SUSPENDED-PAY-REAL: 停權店鋪的未付款訂單／訂房不能再付款（真實後端，完全不 mock）—— Sprint 242（使用者拍板）
 *
 * 為什麼需要：Sprint 239 只擋「建立」訂單與訂房；店鋪停權前就已成立、尚未付款的單，停權後消費者仍可把款項付給停權的店鋪。
 * Sprint 242 把同一個判斷（只有 ACTIVE 的店鋪能收款）接到所有會收錢的付款入口（Mock 付款、Stripe Checkout、舊版 /v2/payments，
 * 訂單與訂房各一），並讓付款狀態回應帶 storeOpen、前端據此顯示「店鋪暫停營業」並收起付款按鈕。
 * 單元與整合測試都是服務層；這裡用打包 JAR＋PostgreSQL＋Redis＋真實開店流程＋管理員真的把店鋪停權，驗證完整路徑：
 * HTTP 狀態碼與錯誤碼、瀏覽器看到的畫面、取消照常可用、恢復營業後又能付款。
 *
 * 資料全由真實流程建立（與 at-store-checkout-real 同一套前提，見 helpers/store.ts）。店鋪狀態每個案例結束前都還原成 ACTIVE
 * （整個守門共用同一個資料庫）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PRODUCT_PRICE = 320;
const NIGHT_PRICE = 2800;

function isoDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString().slice(0, 10);
}

async function placeProductOrder(page: Page, productId: string): Promise<string> {
  const headers = await authHeaders(page);
  const add = await page.request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId: productId, quantity: 1 } });
  expect(add.status(), await add.text()).toBeLessThan(300);
  const created = await page.request.post(`${API_BASE}/v2/orders`, {
    headers,
    data: {
      orderType: 'PRODUCT',
      shippingAddress: '台北市信義區信義路五段 7 號',
      shippingRecipientName: 'E2E 停權付款收件人',
      shippingPhone: '0987654321',
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  return (await created.json()).data.id as string;
}

async function placeBooking(page: Page, roomId: string, email: string, checkInOffset: number): Promise<string> {
  const created = await page.request.post(`${API_BASE}/v2/bookings`, {
    headers: { ...(await authHeaders(page)), 'Idempotency-Key': randomUUID() },
    data: {
      roomListingId: roomId,
      checkInDate: isoDate(checkInOffset),
      checkOutDate: isoDate(checkInOffset + 2),
      guestCount: 2,
      guestName: 'E2E 停權付款住客',
      guestPhone: '0912345678',
      guestEmail: email,
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  return (await created.json()).data.id as string;
}

/**
 * 還原買家的登入狀態（beforeAll 登入一次後存下的 localStorage），不再打登入端點：登入端點有 IP 限流（每分鐘 30 次、
 * 整個 E2E 套件共用），本規格原本每個案例都重新登入，連帶讓不相干的 at-system-tenant-isolation-real 拿不到 token（Sprint 242 實測）。
 * 每個案例都是全新的瀏覽器環境，所以要在載入頁面前先設好；token 有效 15 分鐘，本規格約 2 分鐘跑完。
 */
async function restoreSession(page: Page, session: Record<string, string>): Promise<void> {
  await page.addInitScript((entries: Record<string, string>) => {
    for (const [key, value] of Object.entries(entries)) localStorage.setItem(key, value);
  }, session);
  await page.goto('/');
}

test.describe('AT-STORE-SUSPENDED-PAY-REAL: 停權店鋪不能再收款（真實後端）', () => {
  // 後面的案例依賴前面建立的店鋪與訂單，且開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 120_000 });

  let tenantId: string;
  let buyer: Account;
  let buyerSession: Record<string, string>;
  // 管理員 token 只用 API 登入一次：登入端點有 IP 限流（每分鐘 30 次、整個套件共用），每次停權／恢復都重新登入會拖累別的規格。
  // 買家也不必重新登入——店鋪狀態變更不影響買家的 token
  let adminToken: string;
  // 全部在店鋪營業時一次建好：店鋪被停權時，管理員端會把店鋪所有上架中的商品／房源自動下架（AdminService.deactivateTenantListings），
  // 恢復營業不會自動重新上架——所以停權過一次之後就不能再用同一個店鋪的商品建立新的訂單或訂房
  let orderToPay: string;
  let orderToCancel: string;
  let bookingToPay: string;
  let bookingToCancel: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 停權付款店',
        product: { name: 'E2E 停權付款商品', price: PRODUCT_PRICE },
        room: { name: 'E2E 停權付款房', price: NIGHT_PRICE },
      });
      tenantId = seeded.tenantId;
      adminToken = await adminAccessToken(page);
      // seedStore 結束時頁面是店主的登入狀態；registerAndLogin 先到 /login，已登入會被導走而跳過註冊，所以先清掉
      await page.evaluate(() => localStorage.clear());
      buyer = await registerAndLogin(page);
      buyerSession = await page.evaluate(() => Object.fromEntries(Object.entries(localStorage)));
      expect(buyerSession.accessToken, '買家應已登入').toBeTruthy();
      orderToPay = await placeProductOrder(page, seeded.productId as string);
      orderToCancel = await placeProductOrder(page, seeded.productId as string);
      bookingToPay = await placeBooking(page, seeded.roomId as string, buyer.email, 50);
      bookingToCancel = await placeBooking(page, seeded.roomId as string, buyer.email, 60);
    } finally {
      await page.close();
    }
  });

  test('E2E-SSP-01: 訂單——店鋪營業時有付款按鈕；停權後付款卡片換成「店鋪暫停營業」，Mock 付款與舊版付款端點都回 422 E-2010，訂單仍待付款', async ({ page }: { page: Page }) => {
    await restoreSession(page, buyerSession);
    const orderId = orderToPay;
    const headers = await authHeaders(page);

    await page.goto(`/orders/${orderId}`);
    await expect(page.getByRole('button', { name: '確認付款（模擬）' }), '店鋪營業中：有付款按鈕').toBeVisible({ timeout: 20000 });
    await expect(page.getByTestId('order-store-closed')).toHaveCount(0);
    const open = await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers });
    expect((await open.json()).data.storeOpen).toBe(true);

    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    try {
      const closedHeaders = headers;

      await page.goto(`/orders/${orderId}`);
      await expect(page.getByTestId('order-store-closed'), '停權後顯示說明').toBeVisible({ timeout: 20000 });
      await expect(page.getByTestId('order-store-closed')).toContainText('暫停營業');
      await expect(page.getByRole('button', { name: '確認付款（模擬）' }), '付款按鈕收起').toHaveCount(0);
      await expect(page.getByRole('button', { name: '取消訂單' }), '仍可取消').toBeVisible();

      const state = (await (await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers: closedHeaders })).json()).data;
      expect(state.storeOpen).toBe(false);
      expect(state.canPay, 'canPay 仍是「訂單狀態允許付款」，店鋪狀態由 storeOpen 表達').toBe(true);

      const pay = await page.request.post(`${API_BASE}/v2/orders/${orderId}/pay`, { headers: closedHeaders });
      expect(pay.status(), await pay.text()).toBe(422);
      expect((await pay.json()).code).toBe('E-2010');

      const legacy = await page.request.post(`${API_BASE}/v2/payments`, {
        headers: closedHeaders,
        data: { orderId, paymentMethod: 'CREDIT_CARD' },
      });
      expect(legacy.status(), await legacy.text()).toBe(422);
      expect((await legacy.json()).code).toBe('E-2010');

      const after = (await (await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers: closedHeaders })).json()).data;
      expect(after.orderStatus, '被擋下後訂單沒有被動到').toBe('CREATED');
      expect(after.paymentId, '沒有留下任何付款紀錄').toBeNull();
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }

    // 店鋪恢復營業：同一張訂單又能付款（暫停不是永久）
    await page.goto(`/orders/${orderId}`);
    await expect(page.getByTestId('order-store-closed')).toHaveCount(0, { timeout: 20000 });
    await page.getByRole('button', { name: '確認付款（模擬）' }).click();
    await expect(page.getByText('付款成功').first()).toBeVisible({ timeout: 20000 });
    const paid = (await (await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers: await authHeaders(page) })).json()).data;
    expect(paid.orderStatus).toBe('PAID');
  });

  test('E2E-SSP-02: 訂單——店鋪停權後，消費者仍能在畫面上取消這張未付款的訂單', async ({ page }: { page: Page }) => {
    await restoreSession(page, buyerSession);
    const orderId = orderToCancel;

    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    try {
      await page.goto(`/orders/${orderId}`);
      await expect(page.getByTestId('order-store-closed')).toBeVisible({ timeout: 20000 });

      await page.getByRole('button', { name: '取消訂單' }).click();
      await page.getByRole('button', { name: '確認取消' }).click();
      await expect(page.getByText('已取消').first(), '取消成功').toBeVisible({ timeout: 20000 });
      const state = (await (await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers: await authHeaders(page) })).json()).data;
      expect(state.orderStatus).toBe('CANCELLED');
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }
  });

  test('E2E-SSP-03: 訂房——停權後訂房詳情頁顯示「店鋪暫停營業」、付款回 422 E-2010；仍可取消；恢復營業後又能付款', async ({ page }: { page: Page }) => {
    await restoreSession(page, buyerSession);
    const bookingId = bookingToPay;

    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    const headers = await authHeaders(page);
    try {
      await page.goto(`/bookings/${bookingId}`);
      await expect(page.getByTestId('booking-store-closed')).toBeVisible({ timeout: 20000 });
      await expect(page.getByTestId('booking-pay-mock'), '付款按鈕收起').toHaveCount(0);

      const state = (await (await page.request.get(`${API_BASE}/v2/orders/bookings/${bookingId}/payment`, { headers })).json()).data;
      expect(state.storeOpen).toBe(false);

      const pay = await page.request.post(`${API_BASE}/v2/bookings/${bookingId}/pay`, { headers });
      expect(pay.status(), await pay.text()).toBe(422);
      expect((await pay.json()).code).toBe('E-2010');
      // 舊版 POST /v2/payments 的訂房分支從 HTTP 打不到（PaymentRequest.orderId 是 @NotNull，只帶 bookingId 在驗證層就回 400，
      // Sprint 221 起已記載），所以這裡不對它做 HTTP 探針；該分支的守門由 PaymentServiceStoreGuardTest／
      // StoreSuspendedPaymentIntegrationTest 在服務層覆蓋
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }

    await page.goto(`/bookings/${bookingId}`);
    await expect(page.getByTestId('booking-store-closed')).toHaveCount(0, { timeout: 20000 });
    await page.getByTestId('booking-pay-mock').click();
    await expect(page.getByTestId('booking-payment-paid')).toBeVisible({ timeout: 20000 });

    // 另一筆未付款的訂房：停權後仍可取消
    const second = bookingToCancel;
    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');
    try {
      const cancel = await page.request.post(
        `${API_BASE}/v2/bookings/${second}/cancel?reason=${encodeURIComponent('店鋪暫停營業，取消訂房')}`,
        { headers }
      );
      expect(cancel.status(), await cancel.text()).toBe(200);
    } finally {
      await setStoreStatusWithToken(page, adminToken, tenantId, 'ACTIVE');
    }
  });
});
