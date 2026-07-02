import { test, expect, Page, Route } from '@playwright/test';

/**
 * AT-ROOM-BOOKING: ROOM 訂房閉環 E2E（Sprint 39 US-003 AI-2104）
 *
 * 以 page.route mock（免後端 seed）覆蓋 ROOM 訂房閉環與衝突路徑：
 * - E2E-ROOM-01: 詳情頁 ROOM 選日期 → 計價 → 加入購物車
 * - E2E-ROOM-02: checkout 填訪客資料 → 建立 booking 成功 → 預訂成功畫面
 * - E2E-ROOM-03: checkout 日期衝突（409 / E-4001）→ 「所選日期已被預訂」優雅提示
 *
 * checkout 頁無登入守門（表單 + 送出走 mock），故不需真實登入。
 * 遠期日期（2030）確保通過「入住不早於今日 / 退房晚於入住」前端驗證。
 */

const ROOM_ID = '44444444-4444-4444-4444-444444444444';

async function fulfillJson(route: Route, status: number, body: object) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function roomListing() {
  return {
    id: ROOM_ID,
    tenantId: 't1',
    listingType: 'ROOM',
    title: '若水海景房',
    description: '面海雙人房，含早餐。',
    coverImageUrl: null,
    status: 'ACTIVE',
    basePrice: 3200,
    currency: 'TWD',
    tags: ['海景', '含早餐'],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function priceResponse() {
  return {
    roomListingId: ROOM_ID,
    checkInDate: '2030-01-01',
    checkOutDate: '2030-01-03',
    nights: 2,
    baseTotal: 6400,
    adjustedTotal: 6400,
    discount: 0,
    currency: 'TWD',
  };
}

function cartWithRoom() {
  return {
    cartId: 'c1',
    userId: 'u1',
    items: [
      {
        cartItemKey: 'k1',
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
  };
}

test.describe('AT-ROOM-BOOKING: ROOM 訂房閉環（S39）', () => {
  test('E2E-ROOM-01: 詳情頁 ROOM 選日期 → 計價 → 加入購物車', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/listings/${ROOM_ID}**`, async (route) => {
      if (route.request().url().includes('/price')) {
        await fulfillJson(route, 200, { success: true, data: priceResponse() });
      } else {
        await fulfillJson(route, 200, { success: true, data: roomListing() });
      }
    });
    await page.route('**/v2/cart/items', async (route) => {
      await fulfillJson(route, 200, { success: true, data: { cartItemKey: 'k1', listingId: ROOM_ID, quantity: 1 } });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    // 選日期 → 查詢價格
    await page.getByTestId('listing-checkin').fill('2030-01-01');
    await page.getByTestId('listing-checkout').fill('2030-01-03');
    await page.getByRole('button', { name: '查詢價格' }).click();
    await expect(page.getByTestId('listing-price')).toBeVisible({ timeout: 10000 });

    // 加入購物車 → 成功
    await page.getByTestId('listing-add-cart').click();
    await expect(page.getByTestId('listing-add-success')).toBeVisible({ timeout: 10000 });
  });

  test('E2E-ROOM-02: checkout 建立 booking 成功', async ({ page }: { page: Page }) => {
    await page.route('**/v2/cart', async (route) => {
      if (route.request().method() === 'DELETE') {
        await fulfillJson(route, 200, { success: true, data: null });
      } else {
        await fulfillJson(route, 200, { success: true, data: cartWithRoom() });
      }
    });
    await page.route('**/v2/bookings', async (route) => {
      await fulfillJson(route, 201, {
        success: true,
        data: { id: 'bk-1', roomListingId: ROOM_ID, status: 'CREATED', totalAmount: 6400, currency: 'TWD' },
      });
    });

    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');

    await page.fill('#guestName', '測試訪客');
    await page.fill('#guestPhone', '0912345678');
    await page.fill('#guestEmail', 'guest@example.com');
    await page.getByRole('button', { name: '確認預訂' }).click();

    await expect(page.getByText('預訂成功！')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('bk-1')).toBeVisible();
  });

  test('E2E-ROOM-03: checkout 日期衝突（409 E-4001）→ 優雅提示', async ({ page }: { page: Page }) => {
    await page.route('**/v2/cart', async (route) => {
      if (route.request().method() === 'DELETE') {
        await fulfillJson(route, 200, { success: true, data: null });
      } else {
        await fulfillJson(route, 200, { success: true, data: cartWithRoom() });
      }
    });
    await page.route('**/v2/bookings', async (route) => {
      await fulfillJson(route, 409, { success: false, code: 'E-4001', message: 'Room calendar conflict' });
    });

    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');

    await page.fill('#guestName', '測試訪客');
    await page.fill('#guestPhone', '0912345678');
    await page.fill('#guestEmail', 'guest@example.com');
    await page.getByRole('button', { name: '確認預訂' }).click();

    // 日期衝突優雅提示（bookingErrorMessage E-4001），非通用錯誤，非成功
    await expect(page.getByText('所選日期已被預訂', { exact: false })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('預訂成功！')).toHaveCount(0);
  });
});
