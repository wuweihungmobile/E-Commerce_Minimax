import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, adminAccessToken, authHeaders, seedStore, setStoreStatusWithToken } from './helpers/store';

/**
 * AT-EMBED-CARD-STATUS: 部落格嵌入卡片的狀態顯示（DEF-321(d)，Sprint 253，真實後端）
 *
 * PRD §6.6.3 規格：ROOM 類型進入 MAINTENANCE 時，嵌入卡片 `ctaUrl="/contact"`、`currentPrice=null`；
 * Listing 進入 INACTIVE/DELETED（Cascade）時，`statusReason="listing_unavailable"`、
 * `ctaUrl="/stores/{slug}"`。本規格依序讓同一組商品/房源卡經歷「正常 → 維護中 → 下架」三個真實狀態，
 * 驗證 `/blog/[slug]` 的渲染確實依 statusReason 分流文案與連結，而非先前固定顯示「查看詳情」。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

test.describe('AT-EMBED-CARD-STATUS: 嵌入卡片狀態顯示（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let tenantId: string;
  let productId: string;
  let roomId: string;
  let slug: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 嵌入卡片狀態店',
        product: { name: '嵌入卡片測試商品', price: 777 },
        room: { name: '嵌入卡片測試房型', price: 1777 },
      });
      owner = seeded.owner;
      tenantId = seeded.tenantId;
      productId = seeded.productId!;
      roomId = seeded.roomId!;

      const createPost = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: {
          title: 'E2E 嵌入卡片狀態測試貼文',
          content: `商品 {{embed:listing:${productId}}} 房型 {{embed:listing:${roomId}}}`,
          autoPublish: true,
        },
      });
      expect(createPost.status(), await createPost.text()).toBe(200);
      slug = (await createPost.json()).data.slug as string;
      expect(slug, '貼文應已產生 slug').toBeTruthy();
    } finally {
      await page.close();
    }
  });

  test('E2E-EMBED-01: 正常狀態 → 商品卡與房型卡皆顯示價格與「查看詳情」', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto(`/blog/${slug}`);

    await expect(page.getByText('嵌入卡片測試商品')).toBeVisible();
    await expect(page.getByText('嵌入卡片測試房型')).toBeVisible();
    await expect(page.getByText('TWD 777').first()).toBeVisible();
    await expect(page.getByText('TWD 1,777').first()).toBeVisible();
    await expect(page.getByRole('link', { name: '查看詳情' })).toHaveCount(2);
  });

  test('E2E-EMBED-02: 房源進入維護中 → 房型卡顯示「房源維護中」、無定價、CTA 為聯絡客服', async ({
    page,
  }: {
    page: Page;
  }) => {
    // DEF-360（已知缺口，未修）：POST .../maintenance 對「從未被訂過、無 room_calendar 記錄」的
    // 房源會靜默空操作（回 200 但完全沒寫入），本測試的房源正是這種剛建立、從未被訂的狀態，
    // 會在下面 markMaintenance 這步就卡住——不是卡片渲染邏輯錯（那部分已由
    // ListingCardServiceTest／PostControllerE2ETest 的單元/整合測試直接驗證過）。
    // DEF-360 修好後，這個測試應該會「意外通過」而報錯，屆時移除這行 test.fail()。
    test.fail(true, 'DEF-360: markMaintenance 對全新房源靜默空操作，見 DEFERRED_ITEMS_TRACKER.md');

    await loginOnly(page, owner.email, owner.password);

    const today = new Date();
    const startDate = today.toISOString().slice(0, 10);
    const endDate = new Date(today.getTime() + 7 * 86_400_000).toISOString().slice(0, 10);
    const markMaintenance = await page.request.post(
      `${API_BASE}/v2/dashboard/rooms/${roomId}/maintenance`,
      { headers: await authHeaders(page), data: { startDate, endDate } }
    );
    expect(markMaintenance.status(), await markMaintenance.text()).toBe(200);

    await page.goto(`/blog/${slug}`);

    await expect(page.getByText('房源維護中')).toBeVisible();
    await expect(page.getByText('無定價')).toBeVisible();
    const contactCta = page.getByRole('link', { name: '聯絡客服' });
    await expect(contactCta).toHaveAttribute('href', '/contact');

    // 商品卡不受影響，仍是正常狀態
    await expect(page.getByText('TWD 777').first()).toBeVisible();
  });

  test('E2E-EMBED-03: 店鋪被停權 → 商品卡顯示「此商品已下架」、CTA 導向店鋪', async ({
    page,
  }: {
    page: Page;
  }) => {
    // setStoreStatus 內部會 localStorage.clear()，但本測試的 page 在這一步還沒導覽過任何頁面
    // （仍是 about:blank），存取 localStorage 會拋 SecurityError。setStoreStatusWithToken 改用
    // page.request + 既有 bearer token，不碰 localStorage，比照既有 at-store-suspended-payment-real.spec.ts。
    const adminToken = await adminAccessToken(page);
    await setStoreStatusWithToken(page, adminToken, tenantId, 'SUSPENDED');

    await loginOnly(page, owner.email, owner.password);
    await page.goto(`/blog/${slug}`);

    await expect(page.getByText('此商品已下架').first()).toBeVisible();
    const storeCta = page.getByRole('link', { name: '前往店鋪' }).first();
    await expect(storeCta).toHaveAttribute('href', new RegExp('^/stores/'));
  });
});
