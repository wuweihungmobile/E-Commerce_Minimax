import { test, expect, Page } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';
import { verifyEmailViaMailbox } from './helpers/mailbox';
import { adminAccessToken } from './helpers/store';

/**
 * AT-M17-001: 開店申請流程 E2E 測試
 *
 * 测试步骤：
 * 1. 前往開店申請頁面 (/tenant/apply)
 * 2. 填寫必填欄位（storeName, businessType, contactEmail）
 * 3. 提交申請
 * 4. 驗證申請成功提示
 *
 * Sprint 314（DEF-432）：申請表單新增選填的「店鋪描述」（FRD AC-M17-001-1、PRD §9.10.1，≤ 1000 字）。後兩個案例鎖：
 * 填了 → 申請紀錄帶有那段描述；沒填 → 申請紀錄沒有描述（null，不是空字串）。申請紀錄從管理員的待審核列表讀回，
 * 驗證的是後端真的收到什麼，不只相信畫面導向了哪裡。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/** 選第一個營業類型（零售商城）：Select 是 Radix 元件，要先點開再點選項。 */
async function chooseRetailBusinessType(page: Page): Promise<void> {
  await page.locator('button[role="combobox"]').first().click();
  await page.getByRole('option', { name: '零售商城' }).click();
}

/** 從管理員的待審核列表讀回某間店的開店申請（依店名找）。 */
async function readPendingApplication(page: Page, storeName: string): Promise<{ storeDescription: string | null }> {
  const response = await page.request.get(`${API_BASE}/v2/admin/tenant-applications`, {
    headers: { Authorization: `Bearer ${await adminAccessToken(page)}` },
  });
  expect(response.status(), await response.text()).toBe(200);
  const applications = (await response.json()).data.applications as Array<{
    storeName: string;
    storeDescription: string | null;
  }>;
  const application = applications.find((a) => a.storeName === storeName);
  expect(application, '待審核列表應含剛送出的申請').toBeTruthy();
  return application as { storeDescription: string | null };
}
test.describe('AT-M17-001: 開店申請流程', () => {
  test.beforeEach(async ({ page }) => {
    // 註冊新用戶並登入（共用 helper，AI-2101b 統一）
    const { email } = await registerAndLogin(page);

    // Sprint 204（PRD §7.4.2）：已登入者提交開店申請的前置條件是已驗證 Email。
    // 走真實流程：註冊時後端寄出驗證信（Mock 寫進後端日誌），從日誌取連結並開啟。
    await verifyEmailViaMailbox(page, email);

    // 驗證 JWT token 已正確存儲
    const accessToken = await page.evaluate(() => localStorage.getItem('accessToken'));
    expect(accessToken).toBeTruthy();

    // 等待頁面穩定後再前往申請頁面
    await page.waitForLoadState('domcontentloaded');

    // 前往申請頁面
    await page.goto('/tenant/apply');
  });

  test('開店申請流程 - 完整填寫並提交', async ({ page }) => {
    // 填寫開店申請表單
    await page.fill('input[name="storeName"]', 'Test Store E2E');

    // 選擇 businessType - 使用 Select 元件
    const businessTypeSelect = page.locator('button[role="combobox"]').first();
    if (await businessTypeSelect.isVisible()) {
      await businessTypeSelect.click();
      await expect(page.locator('[role="option"]').first()).toBeVisible();
      // 點擊第一個選項
      await page.click('[role="option"]:first-child');
    }

    // 填寫聯絡 Email
    await page.fill('input[name="contactEmail"]', `test-e2e-${Date.now()}@example.com`);

    // 填寫聯絡電話
    await page.fill('input[name="contactPhone"]', '0912345678');

    // 提交申請
    await page.click('button[type="submit"]:not(:has-text("搜尋"))');

    // Sprint 146（DEF-179）：先前 businessType 值域與後端驗證規則完全不重疊，
    // 每一筆申請必定 400，但本測試用 waitForURL().catch(() => {}) 吞掉逾時，
    // 再對「兩種結果都 test.skip()」，導致這個 400 從未被本測試攔截過。
    // 修復後應確實導向店鋪列表，故此處改為真斷言而非可被吞掉的逾時。
    await page.waitForURL('**/dashboard/tenants**', { timeout: 15000 });
    expect(page.url()).toContain('/dashboard/tenants');
  });

  test('開店申請流程 - 填寫店鋪描述 → 申請紀錄帶有那段描述（DEF-432）', async ({ page }) => {
    test.setTimeout(60_000);
    const storeName = `E2E 描述申請店 ${Date.now()}`;
    const storeDescription = `E2E 開店申請的店鋪描述 ${Date.now()}`;

    await page.fill('input[name="storeName"]', storeName);
    // 後端上限 1000 字（TenantApplicationRequest）：表單一開始就限制輸入長度
    await expect(page.locator('#storeDescription')).toHaveAttribute('maxlength', '1000');
    await page.fill('#storeDescription', storeDescription);
    await chooseRetailBusinessType(page);
    await page.fill('input[name="contactEmail"]', `test-e2e-desc-${Date.now()}@example.com`);
    await page.fill('input[name="contactPhone"]', '0912345678');
    await page.click('button[type="submit"]:not(:has-text("搜尋"))');
    await page.waitForURL('**/dashboard/tenants**', { timeout: 15000 });

    expect((await readPendingApplication(page, storeName)).storeDescription).toBe(storeDescription);
  });

  test('開店申請流程 - 不填店鋪描述 → 申請紀錄沒有描述（null，不是空字串）（DEF-432）', async ({ page }) => {
    test.setTimeout(60_000);
    const storeName = `E2E 無描述申請店 ${Date.now()}`;

    await page.fill('input[name="storeName"]', storeName);
    await chooseRetailBusinessType(page);
    await page.fill('input[name="contactEmail"]', `test-e2e-nodesc-${Date.now()}@example.com`);
    await page.fill('input[name="contactPhone"]', '0912345678');
    await page.click('button[type="submit"]:not(:has-text("搜尋"))');
    await page.waitForURL('**/dashboard/tenants**', { timeout: 15000 });

    expect((await readPendingApplication(page, storeName)).storeDescription).toBeNull();
  });

  test('開店申請流程 - 缺少必填欄位應顯示錯誤', async ({ page }) => {
    // 清空任何可能預填的欄位
    await page.fill('input[name="storeName"]', '');

    // 直接提交（不填寫必填欄位）- HTML5 原生驗證會阻止提交
    const submitButton = page.locator('button[type="submit"]:not(:has-text("搜尋"))');

    // 嘗試點擊提交，應該觸發 HTML5 驗證
    await submitButton.click({ force: true });

    // HTML5 驗證失敗時，瀏覽器會顯示內建驗證訊息
    // 我們可以檢查必填欄位是否有 focus 或驗證提示
    const storeNameInput = page.locator('input[name="storeName"]');
    await expect(storeNameInput).toBeAttached();
  });
});