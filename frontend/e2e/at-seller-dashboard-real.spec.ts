import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-SELLER-DASHBOARD-REAL: 商家端訂單／訂房管理（真實後端，完全不 mock）—— Sprint 232
 *
 * 為什麼需要：Sprint 151（賣家訂單管理）與 Sprint 231（商家端訂房管理）的驗證，都讓「下單的人」與「商家」落在同一個
 * 租戶（後端測試把買家放進房源租戶、Sprint 231 的 HTTP 測試把買家升級成 STORE_OWNER 自己訂自己看），前端頁面更從未被
 * 瀏覽器開過；真實情境卻是「消費者（不屬於任何店鋪）訂商家的東西」。Sprint 232 用真實後端＋兩個真實角色走一遍才發現：
 *   1. 洩漏：沒有店鋪的買家呼叫 GET /v2/dashboard/bookings、GET /v2/orders/tenant，看得到別的買家的訂房／訂單
 *      （訂房人姓名、收件人姓名、金額、日期）。原因是這類使用者的租戶是系統租戶佔位值，而他們下的單也蓋成這個租戶。
 *      Sprint 232 已修（空頁）——E2E-SDASH-02／06 用正常斷言守住。
 *   2. 歸屬（DEF-319）：訂單／訂房蓋的是「下單者的租戶」，不是「賣家的租戶」，所以商家端列表看不到真實消費者的
 *      單、商家也無法取消／處理，週結算（依 orders.tenant_id 彙總）也不會納入。訂房側已於 Sprint 236 修復
 *      （E2E-SDASH-03／04 已是正常斷言）；訂單側尚未修復，E2E-SDASH-07／08 描述「修好之後應有的行為」，目前以
 *      test.fail() 標示「應該失敗」：修好後它們會「意外通過」而報錯，提醒移除標記（比 test.skip 好——
 *      每次都真的執行，不會悄悄爛掉）。
 *
 * 資料全由真實流程建立（與 at-booking-payment-real 同一套前提）：店主註冊→驗證 Email→申請開店（HYBRID）→管理員核准→
 * 管理員開啟 BOOKING_ENABLED→店主重新登入→建立 ROOM 房源與 PRODUCT 商品；消費者另行註冊（不屬於任何店鋪）。
 * 信件是日誌型 Mock，Email 驗證連結從 E2E_BACKEND_LOG 取（見 helpers/mailbox.ts）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const NIGHT_PRICE = 3200;
const PRODUCT_PRICE = 450;
const GUEST_NAME = 'E2E 住客王小明';
const OWNER_GUEST_NAME = 'E2E 店主自訂客';
const RECIPIENT_NAME = 'E2E 收件人李小華';
const DEF_319 = 'DEF-319（訂單側）：訂單蓋成下單者的租戶（一般消費者＝系統租戶），不是賣家的租戶；修復後移除 test.fail()';

function isoDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString().slice(0, 10);
}

type BookingListItem = { id: string; guestName?: string | null; status: string };
type OrderListItem = { id: string; shippingRecipientName?: string | null; status: string };

async function listTenantBookings(page: Page): Promise<BookingListItem[]> {
  const response = await page.request.get(`${API_BASE}/v2/dashboard/bookings?size=100`, { headers: await authHeaders(page) });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).data.content as BookingListItem[];
}

async function listTenantOrders(page: Page): Promise<OrderListItem[]> {
  const response = await page.request.get(`${API_BASE}/v2/orders/tenant?size=100`, { headers: await authHeaders(page) });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).data.content as OrderListItem[];
}

test.describe('AT-SELLER-DASHBOARD-REAL: 商家端訂單／訂房管理（真實後端）', () => {
  // 後面的案例都依賴前面建立的房源、商品與訂單，且開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let roomId: string;
  let productId: string;
  let owner: Account;
  let customer: Account;
  let bookingId: string;
  let orderId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ roomId, productId, owner } = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 商家端管理店',
        room: { name: 'E2E 商家端海景房', price: NIGHT_PRICE },
        product: { name: 'E2E 商家端隨行杯', price: PRODUCT_PRICE },
      }) as { roomId: string; productId: string; owner: Account });
    } finally {
      await page.close();
    }
  });

  test('E2E-SDASH-01: 消費者（不屬於任何店鋪）訂房並付款（建立前提）', async ({ page }: { page: Page }) => {
    customer = await registerAndLogin(page);
    const headers = await authHeaders(page);
    const created = await page.request.post(`${API_BASE}/v2/bookings`, {
      headers: { ...headers, 'Idempotency-Key': randomUUID() },
      data: {
        roomListingId: roomId,
        checkInDate: isoDate(30),
        checkOutDate: isoDate(32),
        guestCount: 2,
        guestName: GUEST_NAME,
        guestPhone: '0912345678',
        guestEmail: customer.email,
      },
    });
    expect(created.status(), await created.text()).toBe(201);
    bookingId = (await created.json()).data.id as string;
    const pay = await page.request.post(`${API_BASE}/v2/bookings/${bookingId}/pay`, { headers });
    expect(pay.status(), await pay.text()).toBe(200);
  });

  test('E2E-SDASH-02: 另一位沒有店鋪的買家呼叫商家端訂房列表 → 空頁，看不到別人的訂房（Sprint 232 修復的洩漏）', async ({ page }: { page: Page }) => {
    await page.evaluate(() => localStorage.clear()).catch(() => {});
    const other = await registerAndLogin(page);
    expect(other.email).not.toBe(customer.email);
    const items = await listTenantBookings(page);
    expect(items.map((b) => b.id), '沒有店鋪的買家不得從商家端列表看到別人的訂房').not.toContain(bookingId);
    expect(items, '沒有店鋪的買家呼叫商家端列表，應是空的').toEqual([]);
  });

  test('E2E-SDASH-03: 店主的訂房管理列表看得到消費者訂的這筆（含訂房人姓名）', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    const items = await listTenantBookings(page);
    const mine = items.find((b) => b.id === bookingId);
    expect(mine, `店主的訂房管理列表應含消費者訂的這筆（目前共 ${items.length} 筆）`).toBeTruthy();
    expect(mine!.guestName).toBe(GUEST_NAME);
    expect(mine!.status).toBe('PAID');
  });

  test('E2E-SDASH-04: 店主在訂房管理畫面開啟這筆訂房並取消 → 全額退款，取消方為商家', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    await page.goto('/dashboard/bookings');
    await expect(page.getByRole('heading', { name: '訂房管理' })).toBeVisible({ timeout: 20000 });
    const row = page.getByRole('link').filter({ hasText: GUEST_NAME });
    await expect(row, '列表應顯示消費者的訂房（以訂房人姓名辨識）').toBeVisible({ timeout: 10000 });

    await row.click();
    await expect(page).toHaveURL(new RegExp(`/dashboard/bookings/${bookingId}$`), { timeout: 20000 });
    await expect(page.getByText(`姓名：${GUEST_NAME}`)).toBeVisible({ timeout: 20000 });
    await page.getByRole('button', { name: '取消訂房' }).click();
    await page.getByLabel('取消原因（選填）').fill('E2E 房源臨時維修');
    await page.getByRole('button', { name: '確認取消' }).click();
    await expect(page.getByText(/預訂已取消，將退款/)).toBeVisible({ timeout: 20000 });

    const headers = await authHeaders(page);
    const detail = await page.request.get(`${API_BASE}/v2/bookings/${bookingId}`, { headers });
    expect(detail.status(), await detail.text()).toBe(200);
    const booking = (await detail.json()).data;
    expect(booking.status).toBe('CANCELLED');
    expect(booking.canceledBy, '店主取消的取消方是商家').toBe('MERCHANT');
    expect(Number(booking.refundAmount), '商家取消一律全額退款').toBe(NIGHT_PRICE * 2);
  });

  test('E2E-SDASH-05: 消費者（不屬於任何店鋪）購買商品並付款（建立前提）', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, customer.email, customer.password);
    const headers = await authHeaders(page);
    const add = await page.request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId: productId, quantity: 1 } });
    expect(add.status(), await add.text()).toBeLessThan(300);
    const created = await page.request.post(`${API_BASE}/v2/orders`, {
      headers,
      data: {
        orderType: 'PRODUCT',
        shippingAddress: '台北市信義區信義路五段 7 號',
        shippingRecipientName: RECIPIENT_NAME,
        shippingPhone: '0987654321',
      },
    });
    expect(created.status(), await created.text()).toBe(201);
    orderId = (await created.json()).data.id as string;
    const pay = await page.request.post(`${API_BASE}/v2/orders/${orderId}/pay`, { headers });
    expect(pay.status(), await pay.text()).toBe(200);
  });

  test('E2E-SDASH-06: 另一位沒有店鋪的買家呼叫賣家訂單列表 → 空頁，看不到別人的訂單（Sprint 232 修復的洩漏）', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    const other = await registerAndLogin(page);
    expect(other.email).not.toBe(customer.email);
    const items = await listTenantOrders(page);
    expect(items.map((o) => o.id), '沒有店鋪的買家不得從賣家訂單列表看到別人的訂單').not.toContain(orderId);
    expect(items, '沒有店鋪的買家呼叫賣家訂單列表，應是空的').toEqual([]);
  });

  test('E2E-SDASH-07: 店主的賣家訂單列表看得到消費者買的這筆（含收件人姓名）', async ({ page }: { page: Page }) => {
    test.fail(true, DEF_319);
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    const items = await listTenantOrders(page);
    const mine = items.find((o) => o.id === orderId);
    expect(mine, `店主的賣家訂單列表應含消費者買的這筆（目前共 ${items.length} 筆）`).toBeTruthy();
    expect(mine!.shippingRecipientName).toBe(RECIPIENT_NAME);
  });

  test('E2E-SDASH-08: 店主能讀取消費者買的訂單，並把它確認（PAID → CONFIRMED）', async ({ page }: { page: Page }) => {
    test.fail(true, DEF_319);
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    const headers = await authHeaders(page);
    const detail = await page.request.get(`${API_BASE}/v2/orders/${orderId}`, { headers });
    expect(detail.status(), `店主應能讀取店內商品被購買的訂單：${await detail.text()}`).toBe(200);
    const confirm = await page.request.patch(`${API_BASE}/v2/orders/${orderId}/status`, {
      headers,
      data: { targetStatus: 'CONFIRMED' },
    });
    expect(confirm.status(), `店主應能確認訂單：${await confirm.text()}`).toBe(200);
    expect((await confirm.json()).data.status).toBe('CONFIRMED');
  });

  test('E2E-SDASH-09: 店主自己訂了自己的房（同租戶的特例）→ 商家端訂房管理畫面：列表、詳情、取消，款項全額退回', async ({ page }: { page: Page }) => {
    // 這是 Sprint 231 當時唯一驗證過的情境（訂房人與商家同一個租戶），不代表真實消費者——那是 SDASH-03／04（DEF-319）。
    // 留著它是因為前端畫面（列表、詳情、取消面板、退款說明）在這之前從未被瀏覽器開過；它與 DEF-319 無關，修好後仍應通過。
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, owner.email, owner.password);
    const headers = await authHeaders(page);
    const created = await page.request.post(`${API_BASE}/v2/bookings`, {
      headers: { ...headers, 'Idempotency-Key': randomUUID() },
      data: {
        roomListingId: roomId,
        checkInDate: isoDate(60),
        checkOutDate: isoDate(62),
        guestCount: 1,
        guestName: OWNER_GUEST_NAME,
        guestPhone: '0911222333',
        guestEmail: owner.email,
      },
    });
    expect(created.status(), await created.text()).toBe(201);
    const ownBookingId = (await created.json()).data.id as string;
    const pay = await page.request.post(`${API_BASE}/v2/bookings/${ownBookingId}/pay`, { headers });
    expect(pay.status(), await pay.text()).toBe(200);

    await page.goto('/dashboard/bookings');
    await expect(page.getByRole('heading', { name: '訂房管理' })).toBeVisible({ timeout: 20000 });
    const row = page.getByRole('link').filter({ hasText: OWNER_GUEST_NAME });
    await expect(row, '列表應顯示這筆訂房（以訂房人姓名辨識）').toBeVisible({ timeout: 20000 });
    await expect(row).toContainText('已付款');
    await page.screenshot({ path: test.info().outputPath('sdash09-list.png'), fullPage: true });

    await row.click();
    await expect(page).toHaveURL(new RegExp(`/dashboard/bookings/${ownBookingId}$`), { timeout: 20000 });
    await expect(page.getByText(`姓名：${OWNER_GUEST_NAME}`)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText('取消政策：')).toHaveCount(0); // 政策說明只在展開取消面板後才出現
    await page.screenshot({ path: test.info().outputPath('sdash09-detail.png'), fullPage: true });

    await page.getByRole('button', { name: '取消訂房' }).click();
    await expect(page.getByText(/商家取消一律全額退款/)).toBeVisible();
    await page.getByLabel('取消原因（選填）').fill('E2E 驗收取消');
    await page.screenshot({ path: test.info().outputPath('sdash09-cancel-panel.png'), fullPage: true });
    await page.getByRole('button', { name: '確認取消' }).click();
    await expect(page.getByText(/預訂已取消，將退款/)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText('退款資訊')).toBeVisible({ timeout: 20000 });
    await page.screenshot({ path: test.info().outputPath('sdash09-cancelled.png'), fullPage: true });

    const detail = await page.request.get(`${API_BASE}/v2/bookings/${ownBookingId}`, { headers });
    expect(detail.status(), await detail.text()).toBe(200);
    const booking = (await detail.json()).data;
    expect(booking.status).toBe('CANCELLED');
    expect(Number(booking.refundAmount), '入住前 60 天取消，全額退款').toBe(NIGHT_PRICE * 2);
    // 排程（E2E 堆疊把間隔調成數秒）隨後把款項退回
    await expect
      .poll(async () => (await (await page.request.get(`${API_BASE}/v2/bookings/${ownBookingId}`, { headers })).json()).data.refundStatus, {
        timeout: 40_000,
        intervals: [2000],
      })
      .toBe('COMPLETED');
    await page.reload();
    await expect(page.getByText(/已退款/)).toBeVisible({ timeout: 20000 });
    await page.screenshot({ path: test.info().outputPath('sdash09-refunded.png'), fullPage: true });
  });
});
