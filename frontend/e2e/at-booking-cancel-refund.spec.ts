import { test, expect, Page, Route } from '@playwright/test';

/**
 * AT-BOOKING-CANCEL-REFUND: 訂房取消與退款（Sprint 227，DEF-312；PRD §15.2.5／Q14）
 *
 * 取消時告訴買家有沒有退款、退多少；退款進度在詳情頁可見。以 page.route mock（免後端 seed）：
 * - E2E-CREF-01: 已付款、離入住還有好幾天 → 取消面板顯示取消政策；取消後顯示「將退款 NT$X」；詳情顯示「退款處理中」
 * - E2E-CREF-02: 已付款、入住前不足 24 小時 → 取消後顯示「不退款」；沒有退款資訊卡片
 * - E2E-CREF-03: 未付款 → 取消後只顯示「預訂已取消」，不提「不退款」（沒付過款）
 * - E2E-CREF-04: 退款完成（COMPLETED）→ 詳情顯示「已退款」與金額
 * - E2E-CREF-05: 確認預訂前的結帳頁顯示取消政策摘要（PRD US-012）
 *
 * 全程 mock API，(auth) 頁沒有登入守門。
 */

const ROOM_ID = '44444444-4444-4444-4444-444444444444';
const BOOKING_ID = 'bk-cancel-1';

async function fulfillJson(route: Route, status: number, body: object) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function booking(status: string, extra: Record<string, unknown> = {}) {
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
    paymentDueAt: null,
    canceledAt: null,
    canceledBy: null,
    refundStatus: 'NONE',
    refundAmount: null,
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
    ...extra,
  };
}

function paymentState(orderStatus: string, paymentStatus: string | null) {
  return {
    orderId: BOOKING_ID,
    orderStatus,
    paymentId: paymentStatus ? 'pay-1' : null,
    paymentStatus,
    transactionId: paymentStatus ? 'MOCK-ABC12345' : null,
    nextValidStates: '',
    canPay: orderStatus === 'CREATED',
    canCancel: orderStatus === 'CREATED' || orderStatus === 'PAID',
    canRefund: false,
    paidAt: paymentStatus ? '2030-01-01T10:00:00Z' : null,
    updatedAt: '2030-01-01T10:00:00Z',
    paymentProvider: 'mock',
  };
}

/**
 * 設定詳情頁需要的 mock：取消前回 {@code before}、取消後（POST /cancel 被呼叫過）回 {@code after}。
 * 回傳取消呼叫次數的讀取函式。
 */
async function mockBooking(
  page: Page,
  before: ReturnType<typeof booking>,
  after: ReturnType<typeof booking>,
  cancelResult: object | null
): Promise<() => number> {
  let cancelled = 0;
  await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
    await fulfillJson(route, 200, { success: true, data: cancelled > 0 ? after : before });
  });
  await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
    const paid = (cancelled > 0 ? after : before).status !== 'CREATED';
    await fulfillJson(route, 200, {
      success: true,
      data: paymentState((cancelled > 0 ? after : before).status, paid ? 'SUCCESS' : null),
    });
  });
  await page.route(`**/v2/bookings/${BOOKING_ID}/cancel**`, async (route) => {
    cancelled += 1;
    await fulfillJson(route, 200, { success: true, message: 'Booking cancelled successfully', data: cancelResult });
  });
  return () => cancelled;
}

function cancelResult(refundStatus: string, refundAmount: number | null) {
  return {
    bookingId: BOOKING_ID,
    status: 'CANCELLED',
    canceledAt: '2029-12-20T10:00:00Z',
    canceledBy: 'CUSTOMER',
    refundStatus,
    refundAmount,
  };
}

async function cancelViaUi(page: Page) {
  await page.goto(`/bookings/${BOOKING_ID}`);
  await page.waitForLoadState('domcontentloaded');
  await page.getByRole('button', { name: '取消預訂' }).click();
  await expect(page.getByTestId('booking-cancel-policy')).toContainText('24 小時');
  await page.getByRole('button', { name: '確認取消' }).click();
}

test.describe('AT-BOOKING-CANCEL-REFUND: 訂房取消與退款（S227）', () => {
  test('E2E-CREF-01: 已付款、離入住還有好幾天 → 顯示取消政策；取消後「將退款」，詳情顯示退款處理中', async ({ page }: { page: Page }) => {
    const cancelCalls = await mockBooking(
      page,
      booking('PAID'),
      booking('CANCELLED', { canceledBy: 'CUSTOMER', refundStatus: 'PENDING', refundAmount: 6400 }),
      cancelResult('PENDING', 6400)
    );

    await cancelViaUi(page);

    await expect(page.getByTestId('booking-cancel-notice')).toContainText('將退款', { timeout: 15000 });
    await expect(page.getByTestId('booking-cancel-notice')).toContainText('6,400');
    await expect(page.getByTestId('booking-refund-card')).toContainText('退款處理中', { timeout: 15000 });
    await expect(page.getByTestId('booking-refund-card')).toContainText('6,400');
    expect(cancelCalls()).toBe(1);
  });

  test('E2E-CREF-02: 已付款、入住前不足 24 小時 → 取消後顯示「不退款」，沒有退款資訊卡片', async ({ page }: { page: Page }) => {
    await mockBooking(
      page,
      booking('PAID'),
      booking('CANCELLED', { canceledBy: 'CUSTOMER', refundStatus: 'NONE', refundAmount: null }),
      cancelResult('NONE', null)
    );

    await cancelViaUi(page);

    await expect(page.getByTestId('booking-cancel-notice')).toContainText('不退款', { timeout: 15000 });
    await expect(page.getByText('已取消', { exact: true }).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('booking-refund-card')).toHaveCount(0);
  });

  test('E2E-CREF-03: 未付款的訂房取消 → 只說預訂已取消，不提「不退款」（沒付過款）', async ({ page }: { page: Page }) => {
    await mockBooking(
      page,
      booking('CREATED'),
      booking('CANCELLED', { canceledBy: 'CUSTOMER' }),
      cancelResult('NONE', null)
    );

    await cancelViaUi(page);

    await expect(page.getByTestId('booking-cancel-notice')).toHaveText('預訂已取消。', { timeout: 15000 });
    await expect(page.getByTestId('booking-refund-card')).toHaveCount(0);
  });

  test('E2E-CREF-04: 退款完成（COMPLETED）→ 詳情顯示已退款與金額', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/bookings/${BOOKING_ID}`, async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: booking('CANCELLED', { canceledBy: 'CUSTOMER', refundStatus: 'COMPLETED', refundAmount: 6400 }),
      });
    });
    await page.route(`**/v2/orders/bookings/${BOOKING_ID}/payment`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: paymentState('CANCELLED', 'REFUNDED') });
    });

    await page.goto(`/bookings/${BOOKING_ID}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByTestId('booking-refund-card')).toContainText('已退款', { timeout: 15000 });
    await expect(page.getByTestId('booking-refund-card')).toContainText('6,400');
  });

  test('E2E-CREF-05: 確認預訂前的結帳頁顯示取消政策摘要（PRD US-012）', async ({ page }: { page: Page }) => {
    await page.route('**/v2/cart', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: {
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
          ],
          totalAmount: 6400,
          itemCount: 1,
        },
      });
    });

    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');

    const policy = page.getByTestId('checkout-cancel-policy');
    await expect(policy).toBeVisible({ timeout: 15000 });
    await expect(policy).toContainText('24 小時');
    await expect(policy).toContainText('不退款');
    await expect(page.getByRole('button', { name: '確認預訂' })).toBeVisible();
  });
});
