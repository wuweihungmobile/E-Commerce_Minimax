import { test, expect, Page } from '@playwright/test';
import { asMockLoggedInBuyer } from './helpers/mockLogin';

/**
 * AT-MEDIA-UPLOAD-PRECHECK: 媒體中心上傳前的預檢（Sprint 332，DEF-466）
 *
 * 背景：原本有兩個媒體管理頁面——/cms/media 在選檔當下就檢查類型與大小並給中文訊息，/dashboard/media（媒體中心）
 * 沒有，不合格的檔案要等整個上傳完、後端拒絕之後才得到泛用的「上傳失敗，請稍後再試」。合併成媒體中心一個頁面時，
 * 把預檢移植過來（規則在 lib/media.ts，與後端 MediaValidationService 一致）。
 *
 * 為什麼用 mock 的後端：真實的上傳要有 MinIO（本機 E2E 守門沒有起 MinIO，見 DOCKER_POLICY：它是按需啟動的服務）。
 * 這裡守的是前端自己的行為——不合格的檔案「根本不送出請求」、合格的檔案照常送到上傳端點；上傳與列表邏輯這次沒有動。
 */

const UPLOAD_PATH = '/v2/media/upload-multipart';
const MB = 1024 * 1024;

const ASSET = {
  id: 'm1', tenantId: 't', categoryId: null, categoryName: null, fileName: 'ok.png', filePath: 'p/ok.png', fileSize: 70,
  mimeType: 'image/png', tags: [], usageCount: 0, createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z', url: null,
};
// 1x1 透明 PNG
const TINY_PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');

/** 假的後端：媒體列表永遠是空的；記下送出的上傳請求 */
async function openMediaCenter(page: Page): Promise<{ uploads: string[] }> {
  const uploads: string[] = [];
  await asMockLoggedInBuyer(page);
  // 在「請求發出」的當下記錄，不是等 route handler 收到完整內容才記：大檔案的內容還在傳輸時 handler 還沒跑，
  // 「沒有送出請求」這種否定斷言會在請求被記錄之前就通過——突變驗證時 6 MB 的 PDF 案例就這樣倖存過一次
  page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === `/api${UPLOAD_PATH}`) uploads.push(UPLOAD_PATH);
  });
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^\/api/, '');
    let data: unknown = [];
    let status = 200;
    if (request.method() === 'POST' && path === UPLOAD_PATH) {
      data = ASSET;
      status = 201;
    } else if (path === '/v2/media') {
      data = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true };
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });
  });
  await page.goto('/dashboard/media');
  await expect(page.getByRole('button', { name: '上傳媒體' })).toBeVisible({ timeout: 15000 });
  return { uploads };
}

test.describe('AT-MEDIA-UPLOAD-PRECHECK: 選檔當下預檢，不合格的檔案不送出請求', () => {
  test('E2E-MEDIA-PRE-01: 不支援的檔案類型 → 顯示說明，沒有送出上傳請求', async ({ page }: { page: Page }) => {
    const { uploads } = await openMediaCenter(page);

    await page.locator('input[type="file"]').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') });

    await expect(page.getByTestId('toast').filter({ hasText: '不支援的檔案類型' })).toBeVisible();
    await page.waitForLoadState('networkidle'); // 否定斷言要等網路靜止之後才有意義
    expect(uploads, '不合格的檔案不應該送到後端').toEqual([]);
  });

  test('E2E-MEDIA-PRE-02: 超過該類型的上限（6 MB 的 PDF；文件上限是 5 MB）→ 顯示上限，沒有送出上傳請求', async ({ page }: { page: Page }) => {
    const { uploads } = await openMediaCenter(page);

    await page.locator('input[type="file"]').setInputFiles({ name: 'big.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(6 * MB) });

    await expect(page.getByTestId('toast').filter({ hasText: '文件上限 5 MB' })).toBeVisible();
    await page.waitForLoadState('networkidle');
    expect(uploads).toEqual([]);
  });

  test('E2E-MEDIA-PRE-03: 合格的檔案 → 照常送到上傳端點，成功後顯示「上傳成功」（預檢不能擋掉合格的檔案）', async ({ page }: { page: Page }) => {
    const { uploads } = await openMediaCenter(page);

    await page.locator('input[type="file"]').setInputFiles({ name: 'ok.png', mimeType: 'image/png', buffer: TINY_PNG });

    await expect(page.getByTestId('toast').filter({ hasText: '上傳成功' })).toBeVisible();
    expect(uploads).toEqual([UPLOAD_PATH]);
  });
});
