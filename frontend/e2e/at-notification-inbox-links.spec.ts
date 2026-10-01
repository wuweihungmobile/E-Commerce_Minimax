import { test, expect, Page, Route } from '@playwright/test';

/**
 * AT-NOTIFICATION-INBOX-LINKS: 通知收件匣的標籤與連結（Sprint 229，PRD US-005／US-014）
 *
 * Sprint 229 起，訂房取消、未付款逾時、退款完成會對買家送出站內通知。真實後端 E2E（at-booking-payment-real）驗證了
 * 「取消後即時收到」「退款完成」與「查看訂單／查看訂房」連結，但**逾時通知造不出來**（要等 24 小時），
 * 所以逾時通知帶的「重新預訂」連結（PRD US-014 的重試連結）只能在這裡以 mock 驗證。
 *
 * - E2E-NINBOX-01: 訂房逾時通知（data 帶 bookingId 與 listingId）→ 顯示「查看訂房」與「重新預訂」，連到對的頁面
 * - E2E-NINBOX-02: 退款完成（REFUND_COMPLETED）→ 顯示中文標籤「退款完成」，不是原始字串
 * - E2E-NINBOX-03: 訂單通知只有「查看訂單」，沒有訂房或重新預訂連結；沒有 data 的通知不顯示任何連結
 *
 * 全程 mock API，(auth) 頁沒有登入守門。
 */

const BOOKING_ID = 'bk-timeout-1';
const LISTING_ID = '55555555-5555-5555-5555-555555555555';
const ORDER_ID = 'ord-refund-1';

async function fulfillJson(route: Route, status: number, body: object) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function notice(id: string, notificationType: string, title: string, content: string, data?: Record<string, unknown>) {
  return {
    notificationId: id,
    userId: 'u1',
    notificationType,
    title,
    content,
    data,
    channel: 'IN_APP',
    isRead: false,
    readAt: null,
    createdAt: '2030-01-02T10:00:00Z',
  };
}

async function mockInbox(page: Page, notifications: ReturnType<typeof notice>[]) {
  await page.route('**/v2/notifications**', async (route) => {
    await fulfillJson(route, 200, {
      success: true,
      data: {
        notifications,
        page: 0,
        size: 15,
        totalElements: notifications.length,
        totalPages: 1,
        unreadCount: notifications.length,
      },
    });
  });
}

async function openInbox(page: Page) {
  await page.goto('/notifications');
  await page.waitForLoadState('domcontentloaded');
  await expect(page.getByRole('heading', { name: '通知收件匣' })).toBeVisible({ timeout: 15000 });
}

test.describe('AT-NOTIFICATION-INBOX-LINKS: 通知收件匣的標籤與連結（S229）', () => {
  test('E2E-NINBOX-01: 訂房逾時通知 → 「查看訂房」與「重新預訂」連到訂房與房源頁', async ({ page }: { page: Page }) => {
    await mockInbox(page, [
      notice('n1', 'ORDER_CANCELLED', '訂房因逾期未付款已取消', '您的訂房「若水海景房」（2030-01-05 入住）超過付款期限，已自動取消並釋出日期。如仍想入住，可重新預訂。', {
        bookingId: BOOKING_ID,
        listingId: LISTING_ID,
        reason: 'PAYMENT_TIMEOUT',
      }),
    ]);

    await openInbox(page);

    await expect(page.getByText('訂房因逾期未付款已取消')).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('link', { name: '查看訂房 →' })).toHaveAttribute('href', `/bookings/${BOOKING_ID}`);
    await expect(page.getByRole('link', { name: '重新預訂 →' })).toHaveAttribute('href', `/listings/${LISTING_ID}`);
    await expect(page.getByRole('link', { name: '查看訂單 →' })).toHaveCount(0);
  });

  test('E2E-NINBOX-02: 退款完成通知 → 類型標籤顯示「退款完成」（不是原始的 REFUND_COMPLETED）', async ({ page }: { page: Page }) => {
    await mockInbox(page, [
      notice('n2', 'REFUND_COMPLETED', '退款已完成', '訂房「若水海景房」的款項 NT$6,400 已退回原付款方式，實際入帳時間依付款機構而定。', {
        bookingId: BOOKING_ID,
        refundAmount: 6400,
      }),
    ]);

    await openInbox(page);

    await expect(page.getByText('退款已完成')).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('退款完成', { exact: true })).toBeVisible();
    await expect(page.getByText('REFUND_COMPLETED')).toHaveCount(0);
    await expect(page.getByRole('link', { name: '查看訂房 →' })).toHaveAttribute('href', `/bookings/${BOOKING_ID}`);
    await expect(page.getByRole('link', { name: '重新預訂 →' }), '退款通知不帶 listingId，不該出現重新預訂').toHaveCount(0);
  });

  test('E2E-NINBOX-03: 訂單通知只有「查看訂單」；沒有 data 的通知不顯示任何連結', async ({ page }: { page: Page }) => {
    await mockInbox(page, [
      notice('n3', 'ORDER_CANCELLED', '訂單因逾期未付款已取消', '您的訂單 #abcd1234（NT$900）超過付款期限，已自動取消。如仍需要，請重新下單。', {
        orderId: ORDER_ID,
        reason: 'PAYMENT_TIMEOUT',
      }),
      notice('n4', 'SYSTEM_ANNOUNCEMENT', '系統公告', '本週六凌晨維護。'),
    ]);

    await openInbox(page);

    await expect(page.getByText('訂單因逾期未付款已取消')).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('link', { name: '查看訂單 →' })).toHaveCount(1);
    await expect(page.getByRole('link', { name: '查看訂單 →' })).toHaveAttribute('href', `/orders/${ORDER_ID}`);
    await expect(page.getByRole('link', { name: '查看訂房 →' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: '重新預訂 →' })).toHaveCount(0);
    await expect(page.getByText('系統公告').first()).toBeVisible();
  });
});
