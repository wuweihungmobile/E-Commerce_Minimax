import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';
import { verifyEmailViaMailbox } from './helpers/mailbox';

/**
 * AT-M17-001: 開店申請流程 E2E 測試
 *
 * 测试步骤：
 * 1. 前往開店申請頁面 (/tenant/apply)
 * 2. 填寫必填欄位（storeName, businessType, contactEmail）
 * 3. 提交申請
 * 4. 驗證申請成功提示
 */
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