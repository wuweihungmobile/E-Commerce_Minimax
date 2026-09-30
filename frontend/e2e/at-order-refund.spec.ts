import { test, expect, Page, Route } from '@playwright/test';

/**
 * AT-ORDER-REFUND: 訂單詳情的退款資訊（Sprint 226，DEF-303 (5)）
 *
 * 取消已付款訂單後，系統自動退款：REFUNDING＝處理中、REFUNDED＝已退回。以 page.route mock（免後端 seed）：
 * - E2E-OREF-01: 退款處理中（REFUNDING）→ 顯示「退款處理中」，不顯示已退款金額
 * - E2E-OREF-02: 已退款（REFUNDED）→ 顯示「款項已退回」與已退款金額
 * - E2E-OREF-03: 其他狀態（已付款）→ 不顯示退款資訊
 *
 * 全程 mock API，(auth) 頁沒有登入守門。
 */

const ORDER_ID = 'ord-refund-1';

async function fulfillJson(route: Route, status: number, body: object) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function order(status: string) {
  return {
    id: ORDER_ID,
    tenantId: 't1',
    userId: 'u1',
    orderType: 'PRODUCT',
    status,
    totalAmount: 450,
    shippingFee: 0,
    currency: 'TWD',
    shippingAddress: '台北市信義區信義路五段 7 號',
    shippingRecipientName: '測試買家',
    shippingPhone: '0912345678',
    notes: null,
    guestCount: null,
    guestName: null,
    guestPhone: null,
    guestEmail: null,
    items: [],
    createdAt: '2030-01-01T00:00:00Z',
    updatedAt: '2030-01-01T00:00:00Z',
  };
}

function paymentState(orderStatus: string, paymentStatus: string, refundedAmount: number) {
  return {
    orderId: ORDER_ID,
    orderStatus,
    paymentId: 'pay-1',
    paymentStatus,
    transactionId: 'MOCK-ABC12345',
    nextValidStates: '',
    canPay: false,
    canCancel: false,
    canRefund: orderStatus === 'REFUNDING',
    paidAt: '2030-01-01T10:00:00Z',
    updatedAt: '2030-01-01T10:00:00Z',
    paymentProvider: 'mock',
    refundedAmount,
  };
}

async function mockOrder(page: Page, status: string, payment: ReturnType<typeof paymentState>) {
  await page.route(`**/v2/orders/${ORDER_ID}`, async (route) => {
    await fulfillJson(route, 200, { success: true, data: order(status) });
  });
  await page.route(`**/v2/orders/${ORDER_ID}/payment`, async (route) => {
    await fulfillJson(route, 200, { success: true, data: payment });
  });
  await page.route(`**/v2/orders/${ORDER_ID}/logs`, async (route) => {
    await fulfillJson(route, 200, { success: true, data: [] });
  });
  await page.route(`**/v2/logistics/**`, async (route) => {
    await fulfillJson(route, 200, { success: true, data: [] });
  });
}

test.describe('AT-ORDER-REFUND: 訂單退款資訊（S226）', () => {
  test('E2E-OREF-01: 退款處理中（REFUNDING）→ 顯示處理中說明，不顯示已退款金額', async ({ page }: { page: Page }) => {
    await mockOrder(page, 'REFUNDING', paymentState('REFUNDING', 'SUCCESS', 0));

    await page.goto(`/orders/${ORDER_ID}`);
    await page.waitForLoadState('domcontentloaded');

    const card = page.getByTestId('order-refund-card');
    await expect(card).toBeVisible({ timeout: 15000 });
    await expect(card).toContainText('退款處理中');
    await expect(card).not.toContainText('已退款金額');
  });

  test('E2E-OREF-02: 已退款（REFUNDED）→ 顯示款項已退回與已退款金額', async ({ page }: { page: Page }) => {
    await mockOrder(page, 'REFUNDED', paymentState('REFUNDED', 'REFUNDED', 450));

    await page.goto(`/orders/${ORDER_ID}`);
    await page.waitForLoadState('domcontentloaded');

    const card = page.getByTestId('order-refund-card');
    await expect(card).toBeVisible({ timeout: 15000 });
    await expect(card).toContainText('款項已退回');
    await expect(card).toContainText('已退款金額');
    await expect(card).toContainText('450');
  });

  test('E2E-OREF-03: 已付款（PAID）的訂單 → 不顯示退款資訊', async ({ page }: { page: Page }) => {
    await mockOrder(page, 'PAID', paymentState('PAID', 'SUCCESS', 0));

    await page.goto(`/orders/${ORDER_ID}`);
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByText('付款資訊')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('order-refund-card')).toHaveCount(0);
  });
});
