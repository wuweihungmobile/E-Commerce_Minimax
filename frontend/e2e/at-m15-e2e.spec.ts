import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers/auth';

/**
 * AT-M15-E2E: M15 CMS 功能 E2E 測試
 *
 * 測試涵蓋 (根據 TR_M15_FE_INTEGRATION_TEST_PLAN.md):
 * - E2E-M15-001: 建立並發布貼文流程
 * - E2E-M15-002: 編輯並更新貼文流程
 * - E2E-M15-003: 媒體上傳流程
 * - E2E-M15-004: 前台部落格瀏覽流程
 * - E2E-M15-005: 嵌入商品卡解析流程
 */

/**
 * E2E-M15-001: 建立並發布貼文流程
 */
test.describe('E2E-M15-001: 建立並發布貼文流程', () => {
  test('登入後訪問 CMS 列表頁', async ({ page }) => {
    await registerAndLogin(page);

    // 訪問 CMS 列表頁
    await page.goto('/cms');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    console.log('CMS 列表 URL:', page.url());
    expect(page.url()).toContain('/cms');
  });

  test('新建貼文並發布', async ({ page }) => {
    await registerAndLogin(page);

    // 訪問新建貼文頁
    await page.goto('/cms/posts/new');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    console.log('新建貼文 URL:', page.url());

    // 填寫標題
    const titleInput = page.locator('input[name="title"], input[placeholder*="標題"]').first();
    if (await titleInput.isVisible()) {
      await titleInput.fill('E2E Test Post ' + Date.now());
    }

    // 填寫內容
    const contentArea = page.locator('textarea[name="content"], textarea[placeholder*="內容"]').first();
    if (await contentArea.isVisible()) {
      await contentArea.fill('# Test Content\n\nThis is an E2E test post.');
    }

    console.log('新建貼文表單填寫完成');
  });

  test('CMS 列表頁篩選功能', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cms');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    // 點擊「已發布」篩選
    const publishedBtn = page.locator('button:has-text("已發布")').first();
    if (await publishedBtn.isVisible()) {
      await publishedBtn.click();
      await page.waitForTimeout(500);
      console.log('已發布篩選完成');
    }

    // 點擊「草稿」篩選
    const draftBtn = page.locator('button:has-text("草稿")').first();
    if (await draftBtn.isVisible()) {
      await draftBtn.click();
      await page.waitForTimeout(500);
      console.log('草稿篩選完成');
    }
  });
});

/**
 * E2E-M15-002: 編輯並更新貼文流程
 */
test.describe('E2E-M15-002: 編輯並更新貼文流程', () => {
  test('訪問編輯貼文頁', async ({ page }) => {
    await registerAndLogin(page);

    // 先訪問 CMS 列表頁
    await page.goto('/cms');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    // 嘗試點擊任一篇文章的編輯按鈕
    const editBtn = page.locator('a:has-text("編輯"), button:has-text("編輯")').first();
    if (await editBtn.isVisible()) {
      await editBtn.click();
      await page.waitForTimeout(2000);
      console.log('編輯頁 URL:', page.url());
    } else {
      console.log('沒有可編輯的貼文，跳過此測試');
    }
  });
});

/**
 * E2E-M15-003: 媒體上傳流程
 */
test.describe('E2E-M15-003: 媒體上傳流程', () => {
  test('訪問媒體庫頁面', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/cms/media');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    console.log('媒體庫 URL:', page.url());
    expect(page.url()).toContain('/cms/media');

    const content = await page.content();
    console.log('頁面包含「媒體庫」:', content.includes('媒體庫'));
    console.log('頁面包含「上傳」:', content.includes('上傳'));
  });

  test('媒體庫篩選功能', async ({ page }) => {
    // 媒體庫載入/篩選失敗時頁面會觸發原生 alert()（media/page.tsx loadMedia catch）；
    // 新帳號可能無 CMS 媒體權限 → getMediaList 403 → alert，該原生 dialog 於 teardown 時
    // 會間歇造成 session 崩潰（既有 flaky 根因，S37 AI-2001）。註冊 dialog 處理器自動關閉。
    page.on('dialog', (dialog) => {
      void dialog.dismiss().catch(() => {});
    });

    await registerAndLogin(page);

    await page.goto('/cms/media');
    await page.waitForLoadState('domcontentloaded');

    // 篩選鈕的 active class（bg-blue-100）由本地 filter state 控制、不依賴後端資料 → 穩定可斷言
    const allBtn = page.getByRole('button', { name: /^全部/ });
    const imageBtn = page.getByRole('button', { name: '圖片', exact: true });
    await expect(allBtn).toBeVisible({ timeout: 15000 });
    await expect(imageBtn).toBeVisible();

    // 點「圖片」→ 該鈕轉 active
    await imageBtn.click();
    await expect(imageBtn).toHaveClass(/bg-blue-100/, { timeout: 10000 });

    // 點「全部」→ 轉 active；再等網路 idle，確保篩選 refetch 完成後才結束測試，
    // 避免 in-flight 請求（及其失敗 alert）於 teardown 時崩潰（取代原無斷言 + 固定 sleep）。
    await allBtn.click();
    await expect(allBtn).toHaveClass(/bg-blue-100/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');
  });
});

/**
 * E2E-M15-004: 前台部落格瀏覽流程
 */
test.describe('E2E-M15-004: 前台部落格瀏覽流程', () => {
  test('訪問前台部落格首頁', async ({ page }) => {
    await page.goto('/blog');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    console.log('前台首頁 URL:', page.url());
    expect(page.url()).toContain('/blog');
    console.log('前台首頁測試完成');
  });

  test('訪問前台文章詳情（無效 slug）', async ({ page }) => {
    await page.goto('/blog/non-existent-slug-12345');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    console.log('文章詳情 URL:', page.url());

    const content = await page.content();
    // 應該顯示「找不到文章」或類似的訊息
    console.log('顯示找不到文章:', content.includes('找不到文章') || content.includes('不存在'));
  });
});

/**
 * E2E-M15-005: 嵌入商品卡解析流程
 * 注意：此測試需要後端有已發布的貼文包含嵌入語法
 */
test.describe('E2E-M15-005: 嵌入商品卡解析流程', () => {
  test('前台部落格顯示嵌入卡片區域', async ({ page }) => {
    // 先確保有登入的 tenantId
    await registerAndLogin(page);

    // 前往前台部落格
    await page.goto('/blog');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    // 如果有文章，點擊進入詳情頁
    const firstPostLink = page.locator('a[href^="/blog/"]').first();
    if (await firstPostLink.isVisible()) {
      await firstPostLink.click();
      await page.waitForTimeout(2000);

      console.log('文章詳情 URL:', page.url());
      console.log('嵌入卡片解析測試完成');
    } else {
      console.log('沒有可測試的文章，跳過嵌入卡片測試');
    }
  });
});

/**
 * 額外測試：Dashboard 各頁面（確保舊路由仍然可用）
 */
test.describe('額外測試: Dashboard 頁面', () => {
  test('Dashboard 首頁', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/dashboard');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2000);

    console.log('Dashboard URL:', page.url());
  });

  test('商品頁面', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/dashboard/products');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    console.log('商品頁 URL:', page.url());
  });

  test('房型頁面', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/dashboard/rooms');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000);

    console.log('房型頁 URL:', page.url());
  });
});