import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { authHeaders, seedStore } from './helpers/store';

/**
 * AT-ADMIN-TENANT-FEATURES-REAL: 平台管理台功能開關 UI（DEF-321(e)，Sprint 253，真實後端）
 *
 * 後端 GET/PUT /v2/admin/tenants/{id}/features 早已存在但零前端呼叫點。本規格驗證新頁面：
 * (1) 能看到完整功能目錄（含人類可讀名稱，非只有 isEnabled 的資料列）；
 * (2) 店主自助送出需審核功能的申請後，Admin 頁面要能看到「待審核」提示並核准生效。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const ADMIN_EMAIL = 'admin@nextkey.local';
const ADMIN_PASSWORD = 'Test123!';

test.describe('AT-ADMIN-TENANT-FEATURES-REAL: 平台管理台功能開關（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let tenantId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'BOOKING_ONLY',
        storeLabel: 'E2E Admin 功能開關店',
      });
      tenantId = seeded.tenantId;

      // 店主自助送出需審核功能（BOOKING_ENABLED requiresApproval=true）的申請，
      // 送出後 isEnabled 仍為 false、status=PENDING（DEF-247 既有設計）。
      const requestFeature = await page.request.put(
        `${API_BASE}/v2/dashboard/tenants/features/BOOKING_ENABLED`,
        { headers: await authHeaders(page), data: { enabled: true } }
      );
      expect(requestFeature.status(), await requestFeature.text()).toBe(200);
    } finally {
      await page.close();
    }
  });

  test('E2E-ADMIN-FEAT-01: Admin 看到完整功能目錄，含人類可讀名稱與待審核提示', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto(`/admin/tenants/${tenantId}/features`);

    // 人類可讀名稱（非只有 featureKey 原始字串）。{ exact: true } 避免撞到下方
    // 「3 項功能待審核：民宿預訂功能、...」摘要句子裡的同一段文字（strict mode violation）。
    await expect(page.getByText('零售功能', { exact: true })).toBeVisible();
    await expect(page.getByText('民宿預訂功能', { exact: true })).toBeVisible();

    // 待審核提示（頂部摘要 Alert + 該項目的狀態徽章，徽章有 3 個同文字，取第一個即可）
    await expect(page.getByText(/項功能待審核/)).toBeVisible();
    await expect(page.getByText('待審核').first()).toBeVisible();
  });

  test('E2E-ADMIN-FEAT-02: Admin 核准待審核功能後，狀態變為已啟用', async ({ page }: { page: Page }) => {
    await loginOnly(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.goto(`/admin/tenants/${tenantId}/features`);

    const bookingRow = page.locator('#BOOKING_ENABLED');
    await expect(bookingRow).toBeVisible();
    await bookingRow.click();

    await expect(page.getByText('功能「BOOKING_ENABLED」已啟用')).toBeVisible();

    const verify = await page.request.get(`${API_BASE}/v2/admin/tenants/${tenantId}/features`, {
      headers: await authHeaders(page),
    });
    expect(verify.status(), await verify.text()).toBe(200);
    const booking = (await verify.json()).data.features.find(
      (f: { featureKey: string }) => f.featureKey === 'BOOKING_ENABLED'
    );
    expect(booking.isEnabled).toBe(true);
    expect(booking.status).toBe('ACTIVE');
  });
});
