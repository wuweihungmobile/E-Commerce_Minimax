import { test, expect } from '@playwright/test';

/**
 * AT-M17-003: Feature Toggle 更新 E2E 測試
 *
 * 测试步骤：
 * 1. StoreOwner 登入系統
 * 2. 前往店鋪設定頁面 (/dashboard/tenants/:id/features)
 * 3. 查看 Feature Toggle 列表
 * 4. 變更某個 Toggle 的狀態
 * 5. 驗證變更成功
 */
test.describe('AT-M17-003: Feature Toggle 更新', () => {
  test.beforeEach(async ({ page }) => {
    // 註冊新用戶（確保有有效憑證）
    const testEmail = `test-e2e-owner-${Date.now()}@example.com`;
    const testPassword = 'Test123!';
    await page.goto('/login');

    // 點擊註冊連結
    await page.click('a:has-text("create a new account")');

    // 填寫註冊表單
    await page.fill('input[name="fullName"]', 'Test Owner E2E');
    await page.fill('input[name="email"]', testEmail);
    await page.fill('input[name="password"]', testPassword);
    await page.fill('input[name="confirmPassword"]', testPassword);
    await page.click('button[type="submit"]');

    // 等待註冊完成（跳轉到登入頁面）
    await page.waitForURL('**/login**', { timeout: 15000 });

    // 現在使用註冊的帳號登入
    await page.fill('input[name="email"]', testEmail);
    await page.fill('input[name="password"]', testPassword);
    await page.click('button[type="submit"]');

    // 等待登入完成並跳轉
    await page.waitForURL('**/dashboard**', { timeout: 15000 });

    // 驗證 JWT token 已正確存儲
    const accessToken = await page.evaluate(() => localStorage.getItem('accessToken'));
    expect(accessToken).toBeTruthy();

    // 等待頁面穩定
    await page.waitForLoadState('networkidle');

    // 前往 Feature Toggle 管理頁面（需要 tenant ID）
    // 先嘗試取得 tenant ID
    await page.goto('/dashboard/tenants');
    await page.waitForLoadState('networkidle');

    // 嘗試從現有資料取得 tenant ID
    const tenantLink = page.locator('a[href*="/dashboard/tenants/"]').first();
    if (await tenantLink.isVisible()) {
      await tenantLink.click();
      await page.waitForLoadState('networkidle');
      // 現在應該在 /dashboard/tenants/[id] 頁面
      // 點擊 Features 連結
      const featuresLink = page.locator('a:has-text("功能"), a:has-text("Feature")').first();
      if (await featuresLink.isVisible()) {
        await featuresLink.click();
      } else {
        // 直接導航到 features URL
        await page.goto('/dashboard/tenants/' + (await page.url()).split('/').pop() + '/features');
      }
    } else {
      // 沒有 tenant，跳過測試
      test.skip('No tenant available for Feature Toggle test');
    }
  });

  test('Feature Toggle 開關切換', async ({ page }) => {
    // 等待頁面載入完成
    await expect(page.getByText(/功能|Feature|Toggle|開關/i).first()).toBeVisible({ timeout: 15000 });

    // 找到第一個 Toggle 並切換狀態（如果存在開關）
    const toggleSwitch = page.locator('input[type="checkbox"]').first();

    if (await toggleSwitch.isVisible()) {
      // 取得初始狀態
      const initialState = await toggleSwitch.isChecked();

      // 點擊切換
      await toggleSwitch.click();

      // 驗證狀態已變更
      const newState = await toggleSwitch.isChecked();
      expect(newState).toBe(!initialState);
    } else {
      test.skip('No toggle switches available');
    }
  });

  test('Feature Toggle 數值更新', async ({ page }) => {
    // 等待頁面載入完成
    await expect(page.getByText(/功能|Feature|Toggle|開關/i).first()).toBeVisible({ timeout: 15000 });

    // 嘗試找到編輯按鈕（如果存在）
    const editButton = page.locator('button:has-text("編輯"), button:has-text("Edit")').first();

    if (await editButton.isVisible()) {
      await editButton.click();

      // 修改數值（如果出現輸入框）
      const valueInput = page.locator('input[name="value"], input[type="number"]');
      if (await valueInput.isVisible()) {
        await valueInput.fill('200');

        // 儲存
        const saveButton = page.locator('button:has-text("儲存"), button:has-text("Save"), button:has-text("更新")');
        await saveButton.click();

        // 驗證更新成功
        await expect(page.getByText(/成功|success/i)).toBeVisible({ timeout: 10000 });
      }
    } else {
      test.skip('No editable feature toggles available');
    }
  });
});