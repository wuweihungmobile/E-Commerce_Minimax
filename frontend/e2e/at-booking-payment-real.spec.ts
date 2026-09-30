import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { verifyEmailViaMailbox } from './helpers/mailbox';

/**
 * AT-BOOKING-PAYMENT-REAL: 訂房付款（真實後端，完全不 mock）—— Sprint 223（DEF-313）
 *
 * 為什麼需要：at-room-booking／at-booking-payment／at-m11-cart-checkout 的訂房與購物車案例全是 page.route mock
 * 或空購物車的軟斷言，從來沒有對真實後端建立過一筆訂房。Sprint 223 用真實後端＋真實瀏覽器走一遍才發現：
 * 打包成 JAR 的後端裡，購物車「加入商品」與「帶 Idempotency-Key 的建立訂房」（前端每次都帶）都回 500——
 * 兩個同名 redisTemplate bean 靜默互相覆蓋，贏的那個沒有 JavaTimeModule，Redis 存不了 LocalDate／Instant。
 * 後端整合測試把 Redis 換成 mock，前端 E2E 全 mock，兩邊都看不到。
 *
 * 資料全由真實流程建立（與 at-m17-002 同一套前提）：店主註冊→驗證 Email→申請開店（HYBRID：商品＋訂房都能賣）→管理員核准→
 * 管理員開啟 BOOKING_ENABLED（核准後預設關閉）→店主重新登入（JWT 才帶新租戶）→建立 ROOM 房源與 PRODUCT 商品→買家訂房、
 * 買商品、合併結帳與付款。
 * 信件是日誌型 Mock，Email 驗證連結從 E2E_BACKEND_LOG 取（見 helpers/mailbox.ts）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PASSWORD = 'Test123!';
const NIGHT_PRICE = 3200;
const PRODUCT_PRICE = 450;

function isoDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString().slice(0, 10);
}

async function accessToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('accessToken'));
  expect(token, '應已登入並持有 accessToken').toBeTruthy();
  return token as string;
}

/** 店主註冊→驗證 Email→申請開店→管理員核准並開啟訂房→店主重新登入→建立一間 ROOM 房源與一件 PRODUCT 商品，回傳兩者的 id。 */
async function seedStore(page: Page): Promise<{ roomId: string; productId: string }> {
  const owner = await registerAndLogin(page);
  await verifyEmailViaMailbox(page, owner.email);
  const ownerToken = await accessToken(page);

  const storeName = `E2E 訂房付款店 ${Date.now()}`;
  const apply = await page.request.post(`${API_BASE}/v2/tenants/apply`, {
    headers: { Authorization: `Bearer ${ownerToken}` },
    data: {
      storeName,
      storeDescription: 'AT-BOOKING-PAYMENT-REAL 自建前提',
      businessType: 'HYBRID',
      contactEmail: `store-${Date.now()}@example.com`,
      contactPhone: '0912345678',
    },
  });
  expect(apply.status(), await apply.text()).toBe(201);

  await page.evaluate(() => localStorage.clear());
  await loginOnly(page, 'admin@nextkey.local', PASSWORD);
  const adminHeaders = { Authorization: `Bearer ${await accessToken(page)}` };
  const pending = await page.request.get(`${API_BASE}/v2/admin/tenant-applications`, { headers: adminHeaders });
  expect(pending.status(), await pending.text()).toBe(200);
  const applications = (await pending.json()).data.applications as Array<{ applicationId: string; storeName: string }>;
  const application = applications.find((a) => a.storeName === storeName);
  expect(application, '待審核列表應含剛送出的申請').toBeTruthy();
  const approve = await page.request.post(
    `${API_BASE}/v2/admin/tenant-applications/${application!.applicationId}/approve`,
    { headers: adminHeaders }
  );
  expect(approve.status(), await approve.text()).toBe(200);
  const tenantId = (await approve.json()).data.tenantId as string;
  // 核准後 BOOKING_ENABLED 預設 false（需管理員開啟），沒開就建立不了房源（403 E-2004）
  const toggle = await page.request.put(`${API_BASE}/v2/admin/tenants/${tenantId}/features/BOOKING_ENABLED`, {
    headers: adminHeaders,
    data: { enabled: true },
  });
  expect(toggle.status(), await toggle.text()).toBe(200);

  await page.evaluate(() => localStorage.clear());
  await loginOnly(page, owner.email, owner.password);
  const ownerHeaders = { Authorization: `Bearer ${await accessToken(page)}` };
  const room = await page.request.post(`${API_BASE}/v2/dashboard/listings`, {
    headers: ownerHeaders,
    data: {
      listingType: 'ROOM',
      name: 'E2E 海景房',
      description: 'AT-BOOKING-PAYMENT-REAL',
      price: NIGHT_PRICE,
      location: '花蓮',
      maxGuests: 2,
      roomCount: 5,
    },
  });
  expect(room.status(), await room.text()).toBe(200);
  // 商品不建 SKU／庫存列＝未啟用庫存追蹤，下單時略過預扣（ProductInventoryService.reserveForOrder），不需要另外進貨
  const product = await page.request.post(`${API_BASE}/v2/dashboard/listings`, {
    headers: ownerHeaders,
    data: {
      listingType: 'PRODUCT',
      name: 'E2E 隨行杯',
      description: 'AT-BOOKING-PAYMENT-REAL',
      price: PRODUCT_PRICE,
      category: '生活',
      brand: 'E2E',
    },
  });
  expect(product.status(), await product.text()).toBe(200);
  return {
    roomId: (await room.json()).data.listingId as string,
    productId: (await product.json()).data.listingId as string,
  };
}

test.describe('AT-BOOKING-PAYMENT-REAL: 訂房付款（真實後端）', () => {
  // 後面的案例都依賴前面建立的房源與訂房，且開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let roomId: string;
  let productId: string;
  let buyer: { email: string; password: string };
  let bookingId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ roomId, productId } = await seedStore(page));
    } finally {
      await page.close();
    }
  });

  test('E2E-BPAYR-01: 帶 Idempotency-Key 建立訂房 → 201；同一把鍵重送 → 回同一筆，不重複建立', async ({ page }: { page: Page }) => {
    buyer = await registerAndLogin(page);
    const headers = { Authorization: `Bearer ${await accessToken(page)}` };
    const idempotencyKey = randomUUID();
    const body = {
      roomListingId: roomId,
      checkInDate: isoDate(30),
      checkOutDate: isoDate(32),
      guestCount: 2,
      guestName: 'E2E 買家',
      guestPhone: '0912345678',
      guestEmail: buyer.email,
    };

    // 前端結帳頁每次送出都帶 Idempotency-Key；服務把回應（含 LocalDate）存進 Redis，存不進去就整個請求 500
    const created = await page.request.post(`${API_BASE}/v2/bookings`, {
      headers: { ...headers, 'Idempotency-Key': idempotencyKey },
      data: body,
    });
    expect(created.status(), await created.text()).toBe(201);
    const booking = (await created.json()).data;
    expect(booking.status).toBe('CREATED');
    expect(Number(booking.totalAmount)).toBe(NIGHT_PRICE * 2);
    bookingId = booking.id as string;

    const replay = await page.request.post(`${API_BASE}/v2/bookings`, {
      headers: { ...headers, 'Idempotency-Key': idempotencyKey },
      data: body,
    });
    expect(replay.status(), await replay.text()).toBe(200);
    expect((await replay.json()).data.id, '同一把鍵重送應回同一筆訂房').toBe(bookingId);

    const list = await page.request.get(`${API_BASE}/v2/bookings`, { headers });
    expect(list.status(), await list.text()).toBe(200);
    const mine = ((await list.json()).data.content as Array<{ id: string; roomListingId: string }>).filter(
      (b) => b.roomListingId === roomId
    );
    expect(mine.map((b) => b.id), '這位買家在這個房源只該有一筆訂房（重放沒有再建一筆）').toEqual([bookingId]);
  });

  test('E2E-BPAYR-02: 訂房詳情頁真實付款 → 付款成功；狀態端點 SUCCESS；重複付款與 Stripe 未啟用都不是 5xx', async ({ page }: { page: Page }) => {
    await loginOnly(page, buyer.email, buyer.password);
    const headers = { Authorization: `Bearer ${await accessToken(page)}` };

    const before = await page.request.get(`${API_BASE}/v2/orders/bookings/${bookingId}/payment`, { headers });
    expect(before.status(), await before.text()).toBe(200);
    const beforeState = (await before.json()).data;
    expect(beforeState.canPay).toBe(true);
    expect(beforeState.paymentProvider).toBe('mock');

    await page.goto(`/bookings/${bookingId}`);
    await expect(page.getByTestId('booking-payment-card')).toContainText('6,400', { timeout: 20000 });
    await page.getByTestId('booking-pay-mock').click();
    await expect(page.getByTestId('booking-payment-paid')).toBeVisible({ timeout: 20000 });
    await page.reload();
    await expect(page.getByTestId('booking-payment-paid'), '重新載入後仍是已付款').toBeVisible({ timeout: 20000 });

    const after = await page.request.get(`${API_BASE}/v2/orders/bookings/${bookingId}/payment`, { headers });
    const afterState = (await after.json()).data;
    expect(afterState.orderStatus).toBe('PAID');
    expect(afterState.paymentStatus).toBe('SUCCESS');
    expect(afterState.canPay).toBe(false);

    const again = await page.request.post(`${API_BASE}/v2/bookings/${bookingId}/pay`, { headers });
    expect(again.status(), '重複付款應被拒絕（422 E-5011），不可是 5xx').toBe(422);
    const stripe = await page.request.post(`${API_BASE}/v2/bookings/${bookingId}/pay/checkout`, { headers });
    expect(stripe.status(), 'Stripe 未啟用時發起結帳應被拒絕（400 E-6002），不可是 5xx').toBe(400);
  });

  test('E2E-BPAYR-03: 房間加入購物車 → 結帳 → 預訂成功畫面帶付款卡片 → 付款', async ({ page }: { page: Page }) => {
    await loginOnly(page, buyer.email, buyer.password);
    const headers = { Authorization: `Bearer ${await accessToken(page)}` };

    // 購物車把 CartItemData（含 Instant）存進 Redis；同一個缺陷讓「加入商品」對所有人回 500
    const add = await page.request.post(`${API_BASE}/v2/cart/items`, {
      headers,
      data: { listingId: roomId, quantity: 1, startDate: isoDate(40), endDate: isoDate(42) },
    });
    expect(add.status(), await add.text()).toBeLessThan(300);

    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('header-cart-count'), '結帳前購物車有這間房').toHaveText('1', { timeout: 15000 });
    await page.fill('#guestName', 'E2E 買家');
    await page.fill('#guestPhone', '0912345678');
    await page.fill('#guestEmail', buyer.email);
    await page.getByRole('button', { name: '確認預訂' }).click();

    await expect(page.getByText('預訂成功！')).toBeVisible({ timeout: 20000 });
    // 訂掉的房間已從購物車移除，Header 徽章要跟著消失（原本停在「1」）
    await expect(page.getByTestId('header-cart-count')).toHaveCount(0, { timeout: 15000 });
    await expect(page.getByTestId('booking-payment-card')).toContainText('6,400', { timeout: 20000 });
    await page.getByTestId('booking-pay-mock').click();
    await expect(page.getByTestId('booking-payment-paid')).toBeVisible({ timeout: 20000 });
  });

  test('E2E-BPAYR-04: 別的買家不能查詢或支付這筆訂房（403），也不會是 5xx', async ({ page }: { page: Page }) => {
    await registerAndLogin(page);
    const headers = { Authorization: `Bearer ${await accessToken(page)}` };

    const state = await page.request.get(`${API_BASE}/v2/orders/bookings/${bookingId}/payment`, { headers });
    const pay = await page.request.post(`${API_BASE}/v2/bookings/${bookingId}/pay`, { headers });
    expect(state.status(), await state.text()).toBe(403);
    expect(pay.status(), await pay.text()).toBe(403);
  });

  test('E2E-BPAYR-05: 商品加入購物車 → 商品結帳 → 訂單建立並 Mock 付款成功', async ({ page }: { page: Page }) => {
    await loginOnly(page, buyer.email, buyer.password);
    const headers = { Authorization: `Bearer ${await accessToken(page)}` };

    const add = await page.request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId: productId, quantity: 2 } });
    expect(add.status(), await add.text()).toBeLessThan(300);

    await page.goto('/checkout/product');
    await page.waitForLoadState('domcontentloaded');
    // 買家沒有存過地址 → 直接顯示手動填寫表單
    await page.fill('#manualRecipient', 'E2E 買家');
    await page.fill('#manualPhone', '0912345678');
    await page.fill('#manualAddress', '台北市信義區信義路五段 7 號');
    await page.getByRole('button', { name: '確認送出並付款' }).click();

    await page.waitForURL(/\/orders\/[0-9a-f-]{36}/, { timeout: 30000 });
    const orderId = page.url().split('/orders/')[1].split(/[?#]/)[0];
    const state = await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers });
    expect(state.status(), await state.text()).toBe(200);
    const paid = (await state.json()).data;
    expect(paid.orderStatus, 'Mock 模式下單後應已付款').toBe('PAID');
    expect(paid.paymentStatus).toBe('SUCCESS');
  });

  test('E2E-BPAYR-06: 合併結帳（商品＋房間，帶冪等鍵）→ Mock 模式訂單與訂房兩邊都付款', async ({ page }: { page: Page }) => {
    await loginOnly(page, buyer.email, buyer.password);
    const headers = { Authorization: `Bearer ${await accessToken(page)}` };
    const checkIn = isoDate(50);

    const addProduct = await page.request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId: productId, quantity: 1 } });
    expect(addProduct.status(), await addProduct.text()).toBeLessThan(300);
    const addRoom = await page.request.post(`${API_BASE}/v2/cart/items`, {
      headers,
      data: { listingId: roomId, quantity: 1, startDate: checkIn, endDate: isoDate(52) },
    });
    expect(addRoom.status(), await addRoom.text()).toBeLessThan(300);

    await page.goto('/checkout/mixed');
    await page.waitForLoadState('domcontentloaded');
    await page.fill('#guestName', 'E2E 買家');
    await page.fill('#manualRecipient', 'E2E 買家');
    await page.fill('#manualPhone', '0912345678');
    await page.fill('#manualAddress', '台北市信義區信義路五段 7 號');
    // 前端每次送出都帶新的 Idempotency-Key；回應含訂單（Instant）與訂房（LocalDate），與 BPAYR-01 同一個缺陷
    await page.getByRole('button', { name: '確認送出並付款' }).click();

    // 兩邊都付款成功才會導向訂單詳情；訂房那一半付款失敗會改導向 /bookings/{id}
    await page.waitForURL(/\/orders\/[0-9a-f-]{36}/, { timeout: 30000 });
    const orderId = page.url().split('/orders/')[1].split(/[?#]/)[0];
    const orderState = await page.request.get(`${API_BASE}/v2/orders/${orderId}/payment`, { headers });
    expect((await orderState.json()).data.orderStatus).toBe('PAID');

    const list = await page.request.get(`${API_BASE}/v2/bookings`, { headers });
    const mixedBooking = ((await list.json()).data.content as Array<{ id: string; roomListingId: string; checkInDate: string }>).find(
      (b) => b.roomListingId === roomId && b.checkInDate === checkIn
    );
    expect(mixedBooking, '合併結帳應建立一筆訂房').toBeTruthy();
    const bookingState = await page.request.get(`${API_BASE}/v2/orders/bookings/${mixedBooking!.id}/payment`, { headers });
    expect((await bookingState.json()).data.orderStatus, '訂房那一半也要付款（Sprint 222）').toBe('PAID');
  });
});
