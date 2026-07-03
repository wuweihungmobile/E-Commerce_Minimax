import { test, expect, Page, Route } from '@playwright/test';

/**
 * AT-ROOM-BOOKING: ROOM 訂房閉環 E2E（Sprint 39 US-003 AI-2104）
 *
 * 以 page.route mock（免後端 seed）覆蓋 ROOM 訂房閉環與衝突路徑：
 * - E2E-ROOM-01: 詳情頁 ROOM 選日期 → 計價 → 加入購物車
 * - E2E-ROOM-02: checkout 填訪客資料 → 建立 booking 成功 → 預訂成功畫面
 * - E2E-ROOM-03: checkout 日期衝突（409 / E-4001）→ 「所選日期已被預訂」優雅提示
 * - E2E-ROOM-05: 整月日曆載入 → 已訂日禁選 → 點選可訂區間 → 可用性 + 加購（Sprint 41 US-004 AI-2202b）
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

function availabilityResponse(available: boolean, unavailableReason: string | null = null) {
  return {
    available,
    roomListingId: ROOM_ID,
    checkInDate: '2030-01-01',
    checkOutDate: '2030-01-03',
    nightsCount: 2,
    totalPrice: available ? 6400 : null,
    currency: 'TWD',
    unavailableReason,
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
  test('E2E-ROOM-01: 詳情頁 ROOM 選日期 → 可用性(可訂) → 加入購物車', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    await page.route('**/v2/bookings/availability**', async (route) => {
      await fulfillJson(route, 200, { success: true, data: availabilityResponse(true) });
    });
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, { success: true, data: [] });
    });
    await page.route('**/v2/cart/items', async (route) => {
      await fulfillJson(route, 200, { success: true, data: { cartItemKey: 'k1', listingId: ROOM_ID, quantity: 1 } });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    // 選日期 → 查詢可用性（可訂）
    await page.getByTestId('listing-checkin').fill('2030-01-01');
    await page.getByTestId('listing-checkout').fill('2030-01-03');
    await page.getByRole('button', { name: '查詢可用性' }).click();
    await expect(page.getByTestId('listing-availability')).toBeVisible({ timeout: 10000 });

    // 可訂 → 加入購物車 → 成功
    await page.getByTestId('listing-add-cart').click();
    await expect(page.getByTestId('listing-add-success')).toBeVisible({ timeout: 10000 });
  });

  test('E2E-ROOM-04: 詳情頁 ROOM 不可預訂 → 禁用加購 + 顯示原因', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    await page.route('**/v2/bookings/availability**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: availabilityResponse(false, '所選日期已被預訂'),
      });
    });
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, { success: true, data: [] });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    await page.getByTestId('listing-checkin').fill('2030-01-01');
    await page.getByTestId('listing-checkout').fill('2030-01-03');
    await page.getByRole('button', { name: '查詢可用性' }).click();

    // 不可預訂 → 顯示原因 + 加購鈕禁用
    await expect(page.getByTestId('listing-unavailable')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('listing-add-cart')).toBeDisabled();
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

  test('E2E-ROOM-05: 整月日曆載入 → 已訂日禁選 → 點選可訂區間 → 可用性 + 加購', async ({ page }: { page: Page }) => {
    // 用「下個月」確保所有日期為未來（避開過去日禁選）
    const now = new Date();
    const ny = now.getMonth() === 11 ? now.getFullYear() + 1 : now.getFullYear();
    const nm = now.getMonth() === 11 ? 0 : now.getMonth() + 1; // 0-based
    const pad = (n: number) => (n < 10 ? '0' + n : String(n));
    const d = (day: number) => `${ny}-${pad(nm + 1)}-${pad(day)}`;
    const bookedDay = d(10);
    const inDay = d(5);
    const outDay = d(8);

    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    // 日曆：下個月第 10 日為 BOOKED（不可訂）
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: [{ date: bookedDay, status: 'BOOKED', price: 3200, bookingId: 'bk-x' }],
      });
    });
    // 可用性：回傳與所選日期一致（解析 query，讓 roomAddBlocked 解除）
    await page.route('**/v2/bookings/availability**', async (route) => {
      const url = new URL(route.request().url());
      const ci = url.searchParams.get('checkInDate') ?? inDay;
      const co = url.searchParams.get('checkOutDate') ?? outDay;
      await fulfillJson(route, 200, {
        success: true,
        data: {
          available: true,
          roomListingId: ROOM_ID,
          checkInDate: ci,
          checkOutDate: co,
          nightsCount: 3,
          totalPrice: 9600,
          currency: 'TWD',
          unavailableReason: null,
        },
      });
    });
    await page.route('**/v2/cart/items', async (route) => {
      await fulfillJson(route, 200, { success: true, data: { cartItemKey: 'k1', listingId: ROOM_ID, quantity: 1 } });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    // 整月日曆渲染
    await expect(page.getByTestId('listing-calendar')).toBeVisible({ timeout: 10000 });

    // 切到下個月（日曆初始為本月）
    await page.getByTestId('calendar-next').click();

    // 已訂日禁選（disabled）
    const booked = page.getByTestId(`calendar-day-${bookedDay}`);
    await expect(booked).toBeVisible();
    await expect(booked).toBeDisabled();

    // 可訂日顯示每日價格（無 room_calendar 記錄之日以 basePrice 補齊；AI-2202c Part A）
    await expect(page.getByTestId(`calendar-price-${inDay}`)).toBeVisible();

    // 點選可訂入住 + 退房 → 同步到日期輸入框
    await page.getByTestId(`calendar-day-${inDay}`).click();
    await page.getByTestId(`calendar-day-${outDay}`).click();
    await expect(page.getByTestId('listing-checkin')).toHaveValue(inDay);
    await expect(page.getByTestId('listing-checkout')).toHaveValue(outDay);

    // 查詢可用性（可訂）→ 加購成功
    await page.getByRole('button', { name: '查詢可用性' }).click();
    await expect(page.getByTestId('listing-availability')).toBeVisible({ timeout: 10000 });
    await page.getByTestId('listing-add-cart').click();
    await expect(page.getByTestId('listing-add-success')).toBeVisible({ timeout: 10000 });
  });

  test('E2E-ROOM-06: 可用性回折扣 → 顯示折扣後價 + 原價刪除線 + 折扣標籤（Sprint 43 US-004 AI-2405）', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, { success: true, data: [] });
    });
    // availability 回動態定價折扣（早鳥 15% off）：原價 6400 → 折扣後 5440，省 960
    await page.route('**/v2/bookings/availability**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: {
          available: true,
          roomListingId: ROOM_ID,
          checkInDate: '2030-01-01',
          checkOutDate: '2030-01-03',
          nightsCount: 2,
          totalPrice: 5440,
          currency: 'TWD',
          unavailableReason: null,
          originalTotalPrice: 6400,
          discountAmount: 960,
          appliedRuleName: '早鳥 15% off',
        },
      });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    await page.getByTestId('listing-checkin').fill('2030-01-01');
    await page.getByTestId('listing-checkout').fill('2030-01-03');
    await page.getByRole('button', { name: '查詢可用性' }).click();

    await expect(page.getByTestId('listing-availability')).toBeVisible({ timeout: 10000 });
    // 原價刪除線（6,400）、折扣後總價（5,440）、折扣標籤（規則名 + 省 960）
    await expect(page.getByTestId('listing-original-price')).toContainText('6,400');
    await expect(page.getByTestId('listing-total-price')).toContainText('5,440');
    const badge = page.getByTestId('listing-discount-badge');
    await expect(badge).toContainText('早鳥 15% off');
    await expect(badge).toContainText('960');
  });

  test('E2E-ROOM-07: 整月日曆每日折扣 → 格子顯示折扣後價 + 原價刪除線（Sprint 44 US-002 AI-2405b）', async ({ page }: { page: Page }) => {
    // 用「下個月」確保為未來日
    const now = new Date();
    const ny = now.getMonth() === 11 ? now.getFullYear() + 1 : now.getFullYear();
    const nm = now.getMonth() === 11 ? 0 : now.getMonth() + 1;
    const pad = (n: number) => (n < 10 ? '0' + n : String(n));
    const d = (day: number) => `${ny}-${pad(nm + 1)}-${pad(day)}`;
    const discDay = d(6);

    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    // 日曆：第 6 日為可訂且有折扣（原價 3200 → 折扣後 2720）
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: [
          { date: discDay, status: 'AVAILABLE', price: 2720, bookingId: null, originalPrice: 3200, appliedRuleName: '早鳥 15% off' },
        ],
      });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-calendar')).toBeVisible({ timeout: 10000 });

    // 切到下個月（日曆初始為本月）
    await page.getByTestId('calendar-next').click();

    // 折扣後價 + 原價刪除線
    await expect(page.getByTestId(`calendar-price-${discDay}`)).toContainText('2,720');
    await expect(page.getByTestId(`calendar-original-price-${discDay}`)).toContainText('3,200');
  });

  test('E2E-ROOM-08: 可用性回漲價 → 顯示漲價後價 + 原價不刪除線 + 加價標籤（Sprint 46 AI-2406b）', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, { success: true, data: [] });
    });
    // availability 回漲價（週末加成 20%）：原價 6400 → 漲價後 7680，加價 1280（discountAmount 有號=負）
    await page.route('**/v2/bookings/availability**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: {
          available: true,
          roomListingId: ROOM_ID,
          checkInDate: '2030-01-01',
          checkOutDate: '2030-01-03',
          nightsCount: 2,
          totalPrice: 7680,
          currency: 'TWD',
          unavailableReason: null,
          originalTotalPrice: 6400,
          discountAmount: -1280,
          appliedRuleName: '週末加成 20%',
          priceAdjustmentType: 'MARKUP',
        },
      });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    await page.getByTestId('listing-checkin').fill('2030-01-01');
    await page.getByTestId('listing-checkout').fill('2030-01-03');
    await page.getByRole('button', { name: '查詢可用性' }).click();

    await expect(page.getByTestId('listing-availability')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('listing-total-price')).toContainText('7,680');
    const original = page.getByTestId('listing-original-price');
    await expect(original).toContainText('6,400');
    await expect(original).not.toHaveClass(/line-through/);
    const badge = page.getByTestId('listing-discount-badge');
    await expect(badge).toContainText('週末加成 20%');
    await expect(badge).toContainText('加價');
    await expect(badge).toContainText('1,280');
  });

  test('E2E-ROOM-09: 整月日曆每日漲價 → 格子顯示漲價後價 + 原價不刪除線（Sprint 46 AI-2406b）', async ({ page }: { page: Page }) => {
    const now = new Date();
    const ny = now.getMonth() === 11 ? now.getFullYear() + 1 : now.getFullYear();
    const nm = now.getMonth() === 11 ? 0 : now.getMonth() + 1;
    const pad = (n: number) => (n < 10 ? '0' + n : String(n));
    const d = (day: number) => `${ny}-${pad(nm + 1)}-${pad(day)}`;
    const upDay = d(6);

    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: [
          { date: upDay, status: 'AVAILABLE', price: 3840, bookingId: null, originalPrice: 3200, appliedRuleName: '週末加成 20%', priceAdjustmentType: 'MARKUP' },
        ],
      });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-calendar')).toBeVisible({ timeout: 10000 });

    await page.getByTestId('calendar-next').click();

    await expect(page.getByTestId(`calendar-price-${upDay}`)).toContainText('3,840');
    const originalCell = page.getByTestId(`calendar-original-price-${upDay}`);
    await expect(originalCell).toContainText('3,200');
    await expect(originalCell).not.toHaveClass(/line-through/);
  });

  test('E2E-ROOM-10: 日曆未開放日 NOT_OPEN → 禁選 + 灰底不刪除線（Sprint 47 AI-2202e）', async ({ page }: { page: Page }) => {
    const now = new Date();
    const ny = now.getMonth() === 11 ? now.getFullYear() + 1 : now.getFullYear();
    const nm = now.getMonth() === 11 ? 0 : now.getMonth() + 1;
    const pad = (n: number) => (n < 10 ? '0' + n : String(n));
    const d = (day: number) => `${ny}-${pad(nm + 1)}-${pad(day)}`;
    const notOpenDay = d(6);

    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    // 日曆：第 6 日為未開放（超過房源開放窗）→ 後端補 NOT_OPEN（price 為 basePrice）
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: [
          { date: notOpenDay, status: 'NOT_OPEN', price: 3200, bookingId: null },
        ],
      });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('listing-calendar')).toBeVisible({ timeout: 10000 });

    await page.getByTestId('calendar-next').click();

    // 未開放日：禁選（disabled + data-unavailable/not-open=true）、不刪除線（區別於 BLOCKED）
    const cell = page.getByTestId(`calendar-day-${notOpenDay}`);
    await expect(cell).toBeDisabled();
    await expect(cell).toHaveAttribute('data-not-open', 'true');
    await expect(cell).toHaveAttribute('data-unavailable', 'true');
    await expect(cell).not.toHaveClass(/line-through/);
  });

  test('E2E-ROOM-11: 可用性回未開放 → 禁用加購 + 顯示未開放原因（Sprint 47 AI-2202e）', async ({ page }: { page: Page }) => {
    await page.route(`**/v2/listings/${ROOM_ID}`, async (route) => {
      await fulfillJson(route, 200, { success: true, data: roomListing() });
    });
    await page.route('**/v2/bookings/calendar**', async (route) => {
      await fulfillJson(route, 200, { success: true, data: [] });
    });
    // availability 回未開放（超窗）：available=false + 未開放原因
    await page.route('**/v2/bookings/availability**', async (route) => {
      await fulfillJson(route, 200, {
        success: true,
        data: availabilityResponse(false, 'Date 2030-01-01 is not open for booking'),
      });
    });

    await page.goto(`/listings/${ROOM_ID}`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.getByTestId('listing-detail')).toBeVisible({ timeout: 15000 });

    await page.getByTestId('listing-checkin').fill('2030-01-01');
    await page.getByTestId('listing-checkout').fill('2030-01-03');
    await page.getByRole('button', { name: '查詢可用性' }).click();

    const unavailable = page.getByTestId('listing-unavailable');
    await expect(unavailable).toBeVisible({ timeout: 10000 });
    await expect(unavailable).toContainText('not open');
    await expect(page.getByTestId('listing-add-cart')).toBeDisabled();
  });
});
