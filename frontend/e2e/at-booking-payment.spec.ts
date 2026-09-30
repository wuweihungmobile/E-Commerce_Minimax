import { test, expect, Page, Route } from '@playwright/test';

/**
 * AT-BOOKING-PAYMENT: 訂房付款 E2E（Sprint 222，DEF-303 (1)）
 *
 * 訂房原本完全沒有付款步驟（Sprint 221 補上後端）。以 page.route mock（免後端 seed）覆蓋前端付款流程：
 * - E2E-BPAY-01: 訂房詳情 Mock 付款 → 付款成功、狀態標籤與付款區塊更新
 * - E2E-BPAY-02: 付款提供者為 stripe → 只有「前往付款」、重導
 * - E2E-BPAY-03: 付款失敗 → 顯示後端訊息、仍可再按
 * - E2E-BPAY-04/05: Stripe 回跳成功頁（有／缺 session_id／尚未付款）
 * - E2E-BPAY-06: Stripe 取消頁 → 返回預訂
 * - E2E-BPAY-07: 已取消且沒有付款紀錄 → 不顯示付款區塊
 * - E2E-BPAY-08: 付款狀態載入失敗 → 降級提示
 * - E2E-BPAY-09: 訂房結帳完成畫面帶付款區塊，可直接付款
 * - E2E-BPAY-10: 合併結帳（Mock）→ 訂單與訂房兩邊都付款
 *
 * 全程 mock API，(auth) 頁沒有登入守門。
 */

const ROOM_ID = '44444444-4444-4444-4444-444444444444';
const BOOKING_ID = 'bk-pay-1';

async function fulfillJson(route: Route, status: number, body: object) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function booking(status: string) {
  return {
    id: BOOKING_ID,
    tenantId: 't1',
    userId: 'u1',
    roomListingId: ROOM_ID,
    roomTitle: '若水海景房',
    coverImageUrl: null,
    checkInDate: '2030-01-01',
    checkOutDate: '2030-01-03',
    guestCount: 2,
    status,
    totalAmount: 6400,
    promoCode: null,
    discountAmount: 0,
    currency: 'TWD',
    guestName: '測試訪客',
    guestPhone: '0912345678',
    guestEmail: 'guest@example.com',
    specialRequests: null,
    nightsCount: 2,
    checkInTime: '15:00:00',
    checkOutTime: '11:00:00',
    createdAt: '2030-01-01T00:00:00Z',
    updatedAt: '2030-01-01T00:00:00Z',
  };
}

/** 後端 OrderPaymentStateDto 的訂房版（orderId／orderStatus 欄位裝的是訂房 id／訂房狀態）。 */
function paymentState(overrides: Record<string, unknown> = {}) {
  return {
    orderId: BOOKING_ID,
    orderStatus: 'CREATED',
    paymentId: null,
    paymentStatus: null,
    transactionId: null,
    nextValidStates: 'PAID,CANCELLED',
    canPay: true,
    canCancel: true,
    canRefund: false,
    paidAt: null,
    updatedAt: '2030-01-01T00:00:00Z',
    paymentProvider: 'mock',
    ...overrides,
  };
}

const PAID_STATE = paymentState({
  orderStatus: 'PAID',
  paymentId: 'pay-1',
  paymentStatus: 'SUCCESS',
  transactionId: 'MOCK-ABC12345',
  nextValidStates: 'CONFIRMED,CANCELLED',
  canPay: false,
  canRefund: true,
  paidAt: '2030-01-01T10:00:00Z',
});

function cartWithRoom(extra: object[] = []) {
  return {
    cartId: 'c1',
    userId: 'u1',
    items: [
      {
        cartItemKey: 'k-room',
        listingId: ROOM_ID,
        listingName: '若水海景房',
        coverImageUrl: null,
        quantity: 1,
        unitPrice: 6400,
        subtotal: 6400,
        listingType: 'ROOM',
        startDate: '2030-01-01',
        endDate: '2030-01-03',
      },
      ...extra,
    ],
    totalAmount: 6400,
    itemCount: 1 + extra.length,
  };
}

test.describe('AT-BOOKING-PAYMENT: 訂房付款（S222）', () => {
  test('E2E-BPAY-01: 訂房詳情 Mock 付款 → 付款成功、狀態標籤與付款區塊更新', async ({ page }: { page: Page }) => {
    let paid = false;
    let payCalls = 0;
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: booking(paid ? 'PAID' : 'CREATED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paid ? PAID_STATE : paymentState() });
    });
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay`, async (route) => {
      payCalls += 1;
      paid = true;
      await fulfillJson(route, 200, { success: true, message: 'Payment successful', data: PAID_STATE });
    });

    await page.goto(`/bookings/${BOOKING_ID}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('booking-payment-card')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-card')).toContainText('6,400');
    await expect(page.getByTestId('booking-payment-card')).toContainText('模擬付款');
    await expect(page.getByTestId('booking-pay-checkout')).toHaveCount(0);

    await page.getByTestId('booking-pay-mock').click();

    await expect(page.getByTestId('booking-payment-paid')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-paid')).toContainText('付款成功');
    await expect(page.getByTestId('booking-payment-paid')).toContainText('MOCK-ABC12345');
    await expect(page.getByTestId('booking-payment-card')).toHaveCount(0);
    await expect(page.getByText('已付款', { exact: true }).first()).toBeVisible({ timeout: 10000 });
    expect(payCalls).toBe(1);
  });

  test('E2E-BPAY-02: 付款提供者為 stripe → 只有「前往付款」，按下後重導', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: booking('CREATED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paymentState({ paymentProvider: 'stripe' }) });
    });
    let checkoutCalls = 0;
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay/checkout`, async (route) => {
      checkoutCalls += 1;
      // 真實的 sessionUrl 是 Stripe 的絕對網址；測試改指向站內的取消頁，以便驗證「確實重導」
      await fulfillJson(route, 200, {
        success: true,
        data: { bookingId: BOOKING_ID, sessionId: 'cs_test_1', sessionUrl: `/bookings/${BOOKING_ID}/payment/cancel` },
      });
    });
    let mockPayCalls = 0;
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay`, async (route) => {
      mockPayCalls += 1;
      await fulfillJson(route, 422, { success: false, code: 'E-6004', message: 'Mock payment is not available' });
    });

    await page.goto(`/bookings/${BOOKING_ID}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('booking-payment-card')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-pay-mock')).toHaveCount(0);
    await page.getByTestId('booking-pay-checkout').click();

    await expect(page.getByTestId('booking-payment-cancel-card')).toBeVisible({ timeout: 15000 });
    expect(checkoutCalls).toBe(1);
    expect(mockPayCalls).toBe(0);
  });

  test('E2E-BPAY-03: 付款失敗 → 顯示後端訊息，仍可再按一次', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: booking('CREATED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paymentState() });
    });
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay`, async (route) => {
      await fulfillJson(route, 422, {
        success: false,
        code: 'E-5011',
        message: 'Booking cannot be paid in current status',
      });
    });

    await page.goto(`/bookings/${BOOKING_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await page.getByTestId('booking-pay-mock').click();

    await expect(page.getByTestId('booking-pay-error')).toContainText('Booking cannot be paid', { timeout: 15000 });
    await expect(page.getByTestId('booking-pay-mock')).toBeEnabled();
    await expect(page.getByTestId('booking-payment-paid')).toHaveCount(0);
  });

  test('E2E-BPAY-04: Stripe 回跳成功頁 → 以 session_id 確認並顯示付款成功', async ({ page }: { page: Page }) => {
    let requestedSession: string | null = null;
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay/checkout/return**`, async (route) => {
      requestedSession = new URL(route.request().url()).searchParams.get('sessionId');
      await fulfillJson(route, 200, { success: true, data: PAID_STATE });
    });

    await page.goto(`/bookings/${BOOKING_ID}/payment/success?session_id=cs_test_paid`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('booking-payment-success-card')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-success-paid')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('booking-payment-success-card')).toContainText('付款成功');
    await expect(page.getByTestId('booking-payment-success-view')).toBeVisible();
    expect(requestedSession).toBe('cs_test_paid');
  });

  test('E2E-BPAY-05: Stripe 回跳成功頁缺 session_id / 尚未付款 → 不會誤報付款成功', async ({ page }: { page: Page }) => {
    await page.goto(`/bookings/${BOOKING_ID}/payment/success`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByText('缺少付款工作階段資訊')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-success-paid')).toHaveCount(0);

    await page.route(`**/v2/bookings/${BOOKING_ID}/pay/checkout/return**`, async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: paymentState({ paymentStatus: 'PROCESSING', paymentProvider: 'stripe' }),
      });
    });
    await page.goto(`/bookings/${BOOKING_ID}/payment/success?session_id=cs_test_open`);
    await expect(page.getByTestId('booking-payment-success-pending')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-success-paid')).toHaveCount(0);
  });

  test('E2E-BPAY-06: Stripe 取消頁 → 訂房仍待付款，可返回預訂', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: booking('CREATED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paymentState({ paymentProvider: 'stripe' }) });
    });

    await page.goto(`/bookings/${BOOKING_ID}/payment/cancel`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('booking-payment-cancel-card')).toContainText('預訂尚未付款', { timeout: 15000 });

    await page.getByTestId('booking-payment-cancel-back').click();
    await expect(page).toHaveURL(new RegExp(`/bookings/${BOOKING_ID}$`), { timeout: 15000 });
    await expect(page.getByTestId('booking-payment-card')).toBeVisible({ timeout: 15000 });
  });

  test('E2E-BPAY-07: 已取消且沒有付款紀錄的訂房 → 不顯示付款區塊', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: booking('CANCELLED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: paymentState({ orderStatus: 'CANCELLED', canPay: false, canCancel: false, nextValidStates: '' }),
      });
    });

    await page.goto(`/bookings/${BOOKING_ID}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByText('住宿資訊')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-loading')).toHaveCount(0, { timeout: 10000 });
    await expect(page.getByTestId('booking-payment-card')).toHaveCount(0);
    await expect(page.getByTestId('booking-payment-paid')).toHaveCount(0);
  });

  test('E2E-BPAY-08: 付款狀態載入失敗 → 降級提示，訂房其餘資訊照常顯示', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: booking('CREATED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 500, { success: false, code: 'E-9900', message: 'boom' });
    });

    await page.goto(`/bookings/${BOOKING_ID}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('booking-payment-unavailable')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('住宿資訊')).toBeVisible();
    await expect(page.getByTestId('booking-pay-mock')).toHaveCount(0);
  });

  test('E2E-BPAY-09: 訂房結帳完成畫面帶付款區塊，可直接付款', async ({ page }: { page: Page }) => {
    let paid = false;
    await page.route('**/v2/cart', async (route) => {
      if (route.request().method() === 'DELETE') {
        await fulfillJson(route, 200, { success: true, data: null });
        return;
      }
      await fulfillJson(route, 200, { success: true, data: cartWithRoom() });
    });
    await page.route('**/v2/bookings', async (route) => {
      await fulfillJson(route, 201, { success: true, data: booking('CREATED') });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paid ? PAID_STATE : paymentState() });
    });
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay`, async (route) => {
      paid = true;
      await fulfillJson(route, 200, { success: true, data: PAID_STATE });
    });

    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');
    await page.fill('#guestName', '測試訪客');
    await page.fill('#guestPhone', '0912345678');
    await page.fill('#guestEmail', 'guest@example.com');
    await page.getByRole('button', { name: '確認預訂' }).click();

    await expect(page.getByText('預訂成功！')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-card')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('booking-payment-card')).toContainText('6,400');

    await page.getByTestId('booking-pay-mock').click();
    await expect(page.getByTestId('booking-payment-paid')).toBeVisible({ timeout: 15000 });
  });

  test('E2E-BPAY-10: 合併結帳（Mock）→ 訂單與訂房兩邊都付款', async ({ page }: { page: Page }) => {
    const paidPaths: string[] = [];
    const ORDER_ID = 'ord-mix-1';
    await page.route('**/v2/cart', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: cartWithRoom([
          {
            cartItemKey: 'k-product',
            listingId: '55555555-5555-5555-5555-555555555555',
            listingName: '隨行杯',
            coverImageUrl: null,
            quantity: 1,
            unitPrice: 450,
            subtotal: 450,
            listingType: 'PRODUCT',
          },
        ]),
      });
    });
    await page.route('**/v2/addresses', async (route) => {
      await fulfillJson(route, 200, { success: true, data: [] });
    });
    await page.route('**/v2/checkout/mixed', async (route) => {
      await fulfillJson(route, 201, {
        success: true,
        data: {
          order: { id: ORDER_ID, status: 'CREATED', totalAmount: 450, currency: 'TWD', items: [] },
          booking: booking('CREATED'),
          promoCode: null,
          totalDiscountAmount: 0,
        },
      });
    });
    await page.route(`**/v2/orders/${ORDER_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paymentState({ orderId: ORDER_ID }) });
    });
    await page.route(`**/v2/orders/${ORDER_ID}/pay`, async (route) => {
      paidPaths.push(new URL(route.request().url()).pathname.replace(/^\/api/, ""));
      await fulfillJson(route, 200, { success: true, data: PAID_STATE });
    });
    await page.route(`**/v2/bookings/${BOOKING_ID}/pay`, async (route) => {
      paidPaths.push(new URL(route.request().url()).pathname.replace(/^\/api/, ""));
      await fulfillJson(route, 200, { success: true, data: PAID_STATE });
    });

    await page.goto('/checkout/mixed');
    await page.waitForLoadState('domcontentloaded');
    await page.fill('#guestName', '測試訪客');
    await page.fill('#manualRecipient', '測試買家');
    await page.fill('#manualPhone', '0912345678');
    await page.fill('#manualAddress', '台北市信義區信義路五段 7 號');
    await page.getByRole('button', { name: '確認送出並付款' }).click();

    await expect
      .poll(() => paidPaths.slice().sort(), {
        timeout: 15000,
        message: '合併結帳（Mock）應同時付款訂單與訂房兩邊',
      })
      .toEqual([`/v2/bookings/${BOOKING_ID}/pay`, `/v2/orders/${ORDER_ID}/pay`].sort());
  });
});
