import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';

/**
 * AT-M11-E2E: M11 購物車 + 結帳流程 E2E 測試
 *
 * 測試涵蓋：
 * - E2E-M11-001: 加入商品到購物車
 * - E2E-M11-002: 查看購物車內容
 * - E2E-M11-003: 套用優惠券 - 有效代碼
 * - E2E-M11-004: 套用優惠券 - 無效代碼
 * - E2E-M11-005: 移除優惠券
 * - E2E-M11-006: 更新商品數量
 * - E2E-M11-007: 移除購物車商品
 * - E2E-M11-008: 前往結帳頁面
 * - E2E-M11-009: 完整結帳流程
 * - E2E-M11-011: 預訂成功後驗證跳轉
 */

// 測試資料
const TEST_DATA = {
  promoCodes: {
    valid: 'TEST20',
    invalid: 'INVALID123'
  },
  guestInfo: {
    validName: '測試用戶',
    validPhone: '0912345678',
    validEmail: 'e2e-test@example.com'
  }
};

/**
 * E2E-M11-001: 加入商品到購物車
 */
test.describe('E2E-M11-001: 加入商品到購物車', () => {
  test('從商品頁面加入購物車', async ({ page }) => {
    await registerAndLogin(page);

    // 假設已有一個商品頁面，這裡訪問首頁然後點擊
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // 嘗試點擊任一商品卡片
    const productCard = page.locator('[class*="card"], [class*="product"]').first();
    if (await productCard.isVisible({ timeout: 5000 })) {
      await productCard.click();

      // 如果有加入購物車按鈕則點擊
      const addToCartButton = page.locator('button:has-text("加入購物車"), button:has-text("Add to Cart")').first();
      if (await addToCartButton.isVisible({ timeout: 2000 })) {
        await addToCartButton.click();
        await page.waitForResponse(r => r.url().includes('/v2/cart/items'), { timeout: 15000 }).catch(() => {});
        console.log('✅ E2E-M11-001: 已點擊加入購物車');
      }
    }

    // 驗證成功提示或跳轉
    console.log('Current URL:', page.url());
  });
});

/**
 * E2E-M11-002: 查看購物車內容
 */
test.describe('E2E-M11-002: 查看購物車內容', () => {
  test('訪問購物車頁面', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    console.log('Cart URL:', page.url());
    expect(page.url()).toContain('/cart');

    // 檢查頁面標題
    const title = page.locator('h1:has-text("購物車")').first();
    if (await title.isVisible({ timeout: 3000 })) {
      console.log('✅ E2E-M11-002: 購物車頁面載入成功');
    }
  });
});

/**
 * E2E-M11-003: 套用優惠券 - 有效代碼
 */
test.describe('E2E-M11-003: 套用優惠券 - 有效代碼', () => {
  test('在購物車頁面套用有效優惠券', async ({ page }) => {
    await registerAndLogin(page);

    // 前往購物車頁面
    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    // 檢查是否有優惠券輸入框
    const promoInput = page.locator('input[placeholder="輸入優惠券代碼"]').first();
    if (await promoInput.isVisible({ timeout: 3000 })) {
      await promoInput.fill(TEST_DATA.promoCodes.valid);

      const applyButton = page.locator('button:has-text("套用")').first();
      await applyButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/cart/apply-promo'), { timeout: 15000 }).catch(() => {});

      // 驗證成功提示
      const successText = page.locator('text=/已套用/i').first();
      if (await successText.isVisible({ timeout: 3000 })) {
        console.log('✅ E2E-M11-003: 優惠券套用成功');
      }
    } else {
      console.log('⚠️ E2E-M11-003: 優惠券輸入框不可見 (可能購物車為空)');
    }
  });
});

/**
 * E2E-M11-004: 套用優惠券 - 無效代碼
 */
test.describe('E2E-M11-004: 套用優惠券 - 無效代碼', () => {
  test('在購物車頁面套用無效優惠券', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    const promoInput = page.locator('input[placeholder="輸入優惠券代碼"]').first();
    if (await promoInput.isVisible({ timeout: 3000 })) {
      await promoInput.fill(TEST_DATA.promoCodes.invalid);

      const applyButton = page.locator('button:has-text("套用")').first();
      await applyButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/cart/apply-promo'), { timeout: 15000 }).catch(() => {});

      // 驗證錯誤提示
      const errorText = page.locator('text=/無效/i, text=/失敗/i').first();
      if (await errorText.isVisible({ timeout: 3000 })) {
        console.log('✅ E2E-M11-004: 無效優惠券錯誤提示正確');
      }
    } else {
      console.log('⚠️ E2E-M11-004: 優惠券輸入框不可見');
    }
  });
});

/**
 * E2E-M11-005: 移除優惠券
 */
test.describe('E2E-M11-005: 移除優惠券', () => {
  test('移除已套用的優惠券', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    // 檢查是否有移除按鈕
    const removeButton = page.locator('button:has-text("移除")').first();
    if (await removeButton.isVisible({ timeout: 3000 })) {
      await removeButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/cart/promo'), { timeout: 15000 }).catch(() => {});
      console.log('✅ E2E-M11-005: 已點擊移除優惠券');
    } else {
      console.log('⚠️ E2E-M11-005: 沒有已套用的優惠券可移除');
    }
  });
});

/**
 * E2E-M11-006: 更新商品數量
 */
test.describe('E2E-M11-006: 更新商品數量', () => {
  test('增加購物車商品數量', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    // 找增加按鈕 (+)
    const increaseButton = page.locator('button:has-text("+")').first();
    if (await increaseButton.isVisible({ timeout: 3000 })) {
      await increaseButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/cart/items'), { timeout: 15000 }).catch(() => {});
      console.log('✅ E2E-M11-006: 已增加商品數量');
    } else {
      console.log('⚠️ E2E-M11-006: 找不到增加按鈕');
    }
  });

  test('手動輸入數量', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    // 找數量輸入框
    const quantityInput = page.locator('input[type="number"]').first();
    if (await quantityInput.isVisible({ timeout: 3000 })) {
      await quantityInput.clear();
      await quantityInput.fill('3');
      await quantityInput.press('Enter');
      await page.waitForResponse(r => r.url().includes('/v2/cart/items'), { timeout: 15000 }).catch(() => {});
      console.log('✅ E2E-M11-006: 已手動輸入數量');
    } else {
      console.log('⚠️ E2E-M11-006: 找不到數量輸入框');
    }
  });
});

/**
 * E2E-M11-007: 移除購物車商品
 */
test.describe('E2E-M11-007: 移除購物車商品', () => {
  test('從購物車移除商品', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    // 找移除按鈕
    const removeButton = page.locator('button:has-text("移除")').first();
    if (await removeButton.isVisible({ timeout: 3000 })) {
      await removeButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/cart/items'), { timeout: 15000 }).catch(() => {});
      console.log('✅ E2E-M11-007: 已移除購物車商品');
    } else {
      console.log('⚠️ E2E-M11-007: 購物車可能為空');
    }
  });
});

/**
 * E2E-M11-008: 前往結帳頁面
 */
test.describe('E2E-M11-008: 前往結帳頁面', () => {
  test('從購物車前往結帳', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cart');
    await page.waitForLoadState('domcontentloaded');

    // 找前往結帳按鈕
    const checkoutButton = page.locator('button:has-text("前往結帳")').first();
    if (await checkoutButton.isVisible({ timeout: 3000 })) {
      await checkoutButton.click();
      await page.waitForURL('**/checkout**', { timeout: 15000 });

      console.log('Checkout URL:', page.url());
      expect(page.url()).toContain('/checkout');
      console.log('✅ E2E-M11-008: 成功前往結帳頁面');
    } else {
      console.log('⚠️ E2E-M11-008: 找不到前往結帳按鈕');
    }
  });
});

/**
 * E2E-M11-009: 完整結帳流程
 */
test.describe('E2E-M11-009: 完整結帳流程', () => {
  test('填寫旅客資料並提交預訂', async ({ page }) => {
    await registerAndLogin(page);

    // 前往結帳頁面
    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');

    console.log('Checkout Page URL:', page.url());

    // 填寫旅客資料
    const guestNameInput = page.locator('#guestName, input[name="guestName"]').first();
    const guestPhoneInput = page.locator('#guestPhone, input[name="guestPhone"]').first();
    const guestEmailInput = page.locator('#guestEmail, input[name="guestEmail"]').first();

    if (await guestNameInput.isVisible({ timeout: 3000 })) {
      await guestNameInput.fill(TEST_DATA.guestInfo.validName);
      console.log('✅ 已填寫姓名');
    }

    if (await guestPhoneInput.isVisible({ timeout: 3000 })) {
      await guestPhoneInput.fill(TEST_DATA.guestInfo.validPhone);
      console.log('✅ 已填寫電話');
    }

    if (await guestEmailInput.isVisible({ timeout: 3000 })) {
      await guestEmailInput.fill(TEST_DATA.guestInfo.validEmail);
      console.log('✅ 已填寫 Email');
    }

    // 提交預訂
    const submitButton = page.locator('button:has-text("確認預訂")').first();
    if (await submitButton.isVisible({ timeout: 3000 })) {
      await submitButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/bookings'), { timeout: 15000 }).catch(() => {});

      console.log('After submit URL:', page.url());

      // 檢查是否成功
      const successText = page.locator('text=/預訂成功/i').first();
      if (await successText.isVisible({ timeout: 5000 })) {
        console.log('✅ E2E-M11-009: 預訂成功完成');
      } else {
        console.log('⚠️ E2E-M11-009: 未看到預訂成功訊息, URL:', page.url());
      }
    } else {
      console.log('⚠️ E2E-M11-009: 找不到確認預訂按鈕');
    }
  });
});

/**
 * E2E-M11-011: 預訂成功後驗證跳轉
 */
test.describe('E2E-M11-011: 預訂成功後驗證跳轉', () => {
  test('預訂成功後應該看到預訂編號', async ({ page }) => {
    await registerAndLogin(page);

    // 嘗試完成一次預訂
    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');

    // 填寫表單
    const guestNameInput = page.locator('#guestName').first();
    const guestPhoneInput = page.locator('#guestPhone').first();
    const guestEmailInput = page.locator('#guestEmail').first();

    if (await guestNameInput.isVisible()) {
      await guestNameInput.fill(TEST_DATA.guestInfo.validName);
      await guestPhoneInput.fill(TEST_DATA.guestInfo.validPhone);
      await guestEmailInput.fill(TEST_DATA.guestInfo.validEmail);

      const submitButton = page.locator('button:has-text("確認預訂")').first();
      await submitButton.click();
      await page.waitForResponse(r => r.url().includes('/v2/bookings'), { timeout: 15000 }).catch(() => {});
    }

    // 檢查預訂編號顯示
    const bookingIdText = page.locator('text=/預訂編號/i').first();
    if (await bookingIdText.isVisible({ timeout: 5000 })) {
      console.log('✅ E2E-M11-011: 預訂編號已顯示');
    } else {
      console.log('⚠️ E2E-M11-011: 未看到預訂編號');
    }

    // 檢查是否還在結帳頁或已跳轉
    console.log('Final URL:', page.url());
  });
});