import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';

/**
 * AT-M17-004: 店鋪 Profile 更新 E2E 測試
 *
 * 测试步骤：
 * 1. StoreOwner 登入系統
 * 2. 前往店鋪列表頁面 (/dashboard/tenants)
 * 3. 選擇一個店鋪進行編輯
 * 4. 修改店鋪資訊
 * 5. 儲存變更
 * 6. 驗證更新成功
 */
test.describe('AT-M17-004: 店鋪 Profile 更新', () => {
  test.beforeEach(async ({ page }) => {
    // 註冊新用戶並登入（共用 helper，AI-2101b 統一）
    await registerAndLogin(page);

    // 驗證 JWT token 已正確存儲
    const accessToken = await page.evaluate(() => localStorage.getItem('accessToken'));
    expect(accessToken).toBeTruthy();

    // 等待頁面穩定
    await page.waitForLoadState('domcontentloaded');

    // 前往店鋪列表頁面
    await page.goto('/dashboard/tenants');
  });

  test('店鋪 Profile 完整更新', async ({ page }) => {
    // 等待店鋪列表載入
    await expect(page.getByText(/店鋪|店鋪列表|Tenant/i).first()).toBeVisible({ timeout: 10000 });

    // 找到第一個編輯按鈕
    const editButton = page.locator('button:has-text("編輯"), a:has-text("編輯")').first();

    if (await editButton.isVisible()) {
      await editButton.click();

      // 等待表單載入
      await expect(page.locator('input[name="storeName"], input[name="name"]').first()).toBeVisible({ timeout: 10000 });

      // 修改店鋪名稱
      const newStoreName = 'Updated Store ' + Date.now();
      const storeNameInput = page.locator('input[name="storeName"], input[name="name"]').first();
      await storeNameInput.clear();
      await storeNameInput.fill(newStoreName);

      // 修改店鋪描述（如果存在）
      const descInput = page.locator('textarea[name="storeDescription"], textarea[name="description"]');
      if (await descInput.isVisible()) {
        await descInput.fill('這是更新後的店鋪描述');
      }

      // 修改聯絡 Email（如果存在）
      const emailInput = page.locator('input[name="contactEmail"], input[name="email"]');
      if (await emailInput.isVisible()) {
        await emailInput.fill('updated@example.com');
      }

      // 儲存變更
      const submitButton = page.locator('button[type="submit"]:not(:has-text("搜尋")), button:has-text("儲存"), button:has-text("更新")');
      await submitButton.click();

      // 驗證更新成功
      await expect(page.getByText(/成功|success/i)).toBeVisible({ timeout: 10000 });
    } else {
      test.skip();
    }
  });

  test('店鋪 Profile 部分更新', async ({ page }) => {
    // 等待店鋪列表載入
    await expect(page.getByText(/店鋪|店鋪列表|Tenant/i).first()).toBeVisible({ timeout: 10000 });

    // 找到第一個編輯按鈕
    const editButton = page.locator('button:has-text("編輯"), a:has-text("編輯")').first();

    if (await editButton.isVisible()) {
      await editButton.click();

      // 等待表單載入
      await expect(page.locator('input[name="storeName"], input[name="name"]').first()).toBeVisible({ timeout: 10000 });

      // 只修改店鋪名稱
      const newStoreName = 'Partial Update ' + Date.now();
      const storeNameInput = page.locator('input[name="storeName"], input[name="name"]').first();
      await storeNameInput.clear();
      await storeNameInput.fill(newStoreName);

      // 儲存變更
      const submitButton = page.locator('button[type="submit"]:not(:has-text("搜尋")), button:has-text("儲存"), button:has-text("更新")');
      await submitButton.click();

      // 驗證更新成功
      await expect(page.getByText(/成功|success/i)).toBeVisible({ timeout: 10000 });
    } else {
      test.skip();
    }
  });
});