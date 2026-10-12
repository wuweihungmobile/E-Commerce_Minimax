import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { authHeaders, seedStore } from './helpers/store';

/**
 * AT-DASHBOARD-TENANT-FEATURES-REAL: 店主自助功能開關頁（DEF-359，Sprint 254，真實後端）
 *
 * `dashboard/tenants/[id]/features/page.tsx` 從未有 E2E 覆蓋——這正是 DEF-359（getStatusBadge
 * 判斷順序錯誤，導致「待審核」被誤標成「已停用」）長期未被發現的原因：Sprint 253 建 Admin 版
 * 同功能頁時比對同一套邏輯才發現。本規格鎖住修復後的行為：店主送出需審核功能的申請後，
 * 自助頁要顯示「待審核」而非「已停用」。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

test.describe('AT-DASHBOARD-TENANT-FEATURES-REAL: 店主自助功能開關（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let tenantId: string;
  let ownerEmail: string;
  let ownerPassword: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'BOOKING_ONLY',
        storeLabel: 'E2E 自助功能開關店',
      });
      tenantId = seeded.tenantId;
      ownerEmail = seeded.owner.email;
      ownerPassword = seeded.owner.password;

      // 店主自助送出需審核功能（BOOKING_ENABLED requiresApproval=true）的申請，
      // 送出後 isEnabled 仍為 false、status=PENDING（DEF-247 既有設計，與 Admin 版規格同一設定）。
      const requestFeature = await page.request.put(
        `${API_BASE}/v2/dashboard/tenants/features/BOOKING_ENABLED`,
        { headers: await authHeaders(page), data: { enabled: true } }
      );
      expect(requestFeature.status(), await requestFeature.text()).toBe(200);
    } finally {
      await page.close();
    }
  });

  test('E2E-DASH-FEAT-01: 送出需審核功能申請後，自助頁顯示待審核而非已停用（DEF-359）', async ({
    page,
  }: {
    page: Page;
  }) => {
    await loginOnly(page, ownerEmail, ownerPassword);
    await page.goto(`/dashboard/tenants/${tenantId}/features`);

    // Switch 本身用 id={featureKey}（非一整列），要往上找該功能列（class 含 justify-between）
    // 才能把斷言限定在這一列，避免頁尾固定說明文字裡「...待審核」字樣或其他功能的「已停用」互相干擾。
    const bookingRow = page.locator(
      'xpath=//*[@id="BOOKING_ENABLED"]/ancestor::div[contains(@class,"justify-between")][1]'
    );
    await expect(bookingRow).toBeVisible();

    // 修復前：!enabled 先判斷，PENDING 時 isEnabled=false，誤顯示「已停用」。
    // 修復後：待審核優先判斷，應顯示「待審核」。
    await expect(bookingRow.getByText('待審核', { exact: true })).toBeVisible();
    await expect(bookingRow.getByText('已停用', { exact: true })).not.toBeVisible();
  });
});
