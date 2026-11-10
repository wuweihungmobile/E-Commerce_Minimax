import { test, expect, Page } from '@playwright/test';
import { asMockLoggedInBuyer } from './helpers/mockLogin';

/**
 * AT-MEDIA-VISIBILITY: 媒體中心的公開開關（Sprint 336，DEF-482）
 *
 * 背景：媒體庫的檔案網址 /api/v2/media/files/{id} 需要登入，<img> 帶不出憑證，店主上傳的圖沒辦法拿去當公開頁的圖。
 * 每個素材有一個「公開」開關（預設私有）：公開＝任何拿到網址的人都能看；公開的素材多一個「複製公開網址」。
 *
 * 為什麼用 mock 的後端：真實的上傳要有 MinIO（本機 E2E 守門沒有起 MinIO，見 DOCKER_POLICY：它是按需啟動的服務），
 * 沒有素材就沒有東西可以切換。後端的真實行為（匿名讀取、各種不該讀到的情況、撤回）由後端的
 * PublicMediaEndpointIntegrationTest 對真實 PostgreSQL＋真實 HTTP 驗證。
 * 這裡守的是瀏覽器裡的前端行為，而且用的是真實的確認視窗、Toast 與剪貼簿——單元測試把它們都 mock 掉了：
 * 切換前先確認、確認後送出正確的請求、畫面跟著更新、複製到剪貼簿的是完整網址。
 */

const ASSET_ID = '11111111-1111-1111-1111-111111111111';
// 1x1 透明 PNG：縮圖用帶憑證的元件抓檔案，回一張真的圖就不會走後備圖示
const TINY_PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
const API_ORIGIN = 'http://localhost:8080';

interface FakeBackend {
  /** 送到 PUT /v2/media/{id} 的請求內容（依序） */
  updates: Array<Record<string, unknown>>;
  /** 目前假後端記著的公開狀態 */
  isPublic: () => boolean;
}

/** 假的後端：一個素材，公開狀態由 PUT 改變，列表與回應都帶出目前的狀態與 publicUrl */
async function openMediaCenter(page: Page, startPublic: boolean): Promise<FakeBackend> {
  const updates: Array<Record<string, unknown>> = [];
  let isPublic = startPublic;
  const asset = () => ({
    id: ASSET_ID, tenantId: 't', categoryId: null, categoryName: null, fileName: 'hero.png', filePath: `t/${ASSET_ID}-hero.png`,
    fileSize: 70, mimeType: 'image/png', tags: [], usageCount: 0, createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z',
    url: `/api/v2/media/files/${ASSET_ID}`, isPublic, ...(isPublic ? { publicUrl: `/api/v2/public/media/${ASSET_ID}` } : {}),
  });

  await asMockLoggedInBuyer(page);
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^\/api/, '');
    const json = (data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) });

    if (request.method() === 'GET' && path === `/v2/media/files/${ASSET_ID}`) {
      return route.fulfill({ status: 200, contentType: 'image/png', body: TINY_PNG });
    }
    if (request.method() === 'PUT' && path === `/v2/media/${ASSET_ID}`) {
      const body = request.postDataJSON() as Record<string, unknown>;
      updates.push(body);
      if (typeof body.isPublic === 'boolean') isPublic = body.isPublic;
      return json(asset());
    }
    if (path === '/v2/media') {
      return json({ content: [asset()], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true });
    }
    return json([]);
  });
  await page.goto('/dashboard/media');
  await expect(page.getByTestId(`media-public-switch-${ASSET_ID}`)).toBeVisible({ timeout: 15000 });
  return { updates, isPublic: () => isPublic };
}

const switchOf = (page: Page) => page.getByTestId(`media-public-switch-${ASSET_ID}`);
const visibilityOf = (page: Page) => page.getByTestId(`media-visibility-${ASSET_ID}`);
const copyButton = (page: Page) => page.getByTestId(`media-copy-url-${ASSET_ID}`);

test.describe('AT-MEDIA-VISIBILITY: 素材的公開開關', () => {
  test('E2E-MEDIA-VIS-01: 私有素材 → 開啟：先出現確認視窗，確認後送出 isPublic:true，畫面變成「公開」並出現「複製公開網址」', async ({ page }: { page: Page }) => {
    const backend = await openMediaCenter(page, false);
    await expect(visibilityOf(page)).toHaveText('私有');
    await expect(switchOf(page)).toHaveAttribute('aria-checked', 'false');
    await expect(copyButton(page), '私有的素材沒有公開網址可複製').toHaveCount(0);

    await switchOf(page).click();

    const dialog = page.getByTestId('confirm-dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('任何人只要拿到網址都能看到這個檔案');
    expect(backend.updates, '確認之前不能送出任何更新').toEqual([]);
    await page.getByTestId('confirm-dialog-confirm').click();

    await expect(visibilityOf(page)).toHaveText('公開');
    await expect(switchOf(page)).toHaveAttribute('aria-checked', 'true');
    await expect(copyButton(page)).toBeVisible();
    expect(backend.updates).toEqual([{ isPublic: true }]);
    expect(backend.isPublic()).toBe(true);
  });

  test('E2E-MEDIA-VIS-02: 在確認視窗按取消 → 沒有送出任何更新，開關維持原狀', async ({ page }: { page: Page }) => {
    const backend = await openMediaCenter(page, false);

    await switchOf(page).click();
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-cancel').click();

    await expect(page.getByTestId('confirm-dialog')).toHaveCount(0);
    await expect(visibilityOf(page)).toHaveText('私有');
    await expect(switchOf(page)).toHaveAttribute('aria-checked', 'false');
    expect(backend.updates).toEqual([]);
  });

  test('E2E-MEDIA-VIS-03: 公開素材 → 取消公開：確認視窗說明後果，確認後送出 isPublic:false，「複製公開網址」消失', async ({ page }: { page: Page }) => {
    const backend = await openMediaCenter(page, true);
    await expect(visibilityOf(page)).toHaveText('公開');

    await switchOf(page).click();

    const dialog = page.getByTestId('confirm-dialog');
    await expect(dialog).toContainText('顯示不出圖片');
    await page.getByTestId('confirm-dialog-confirm').click();

    await expect(visibilityOf(page)).toHaveText('私有');
    await expect(copyButton(page)).toHaveCount(0);
    expect(backend.updates).toEqual([{ isPublic: false }]);
  });

  test('E2E-MEDIA-VIS-04: 「複製公開網址」把完整網址（有主機，指向後端）放進剪貼簿，並出現「已複製」的提示', async ({ page }: { page: Page }) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://localhost:3000' });
    await openMediaCenter(page, true);

    await copyButton(page).click();

    // 完整網址：後端的 origin（取自建置時的 NEXT_PUBLIC_API_URL）＋後端給的路徑；只有路徑的話貼到貼文的精選圖片欄位無法使用
    const copied = await page.evaluate(() => navigator.clipboard.readText());
    expect(copied).toBe(`${API_ORIGIN}/api/v2/public/media/${ASSET_ID}`);
    await expect(page.getByTestId('toast').filter({ hasText: '已複製公開網址' })).toBeVisible();
  });
});
