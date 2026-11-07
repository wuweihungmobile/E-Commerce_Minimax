import { test, expect, Page } from '@playwright/test';
import { loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-DASHBOARD-POST-EDIT-REAL: 貼文編輯頁儲存成功後可以再次儲存（DEF-467，Sprint 332，真實後端）
 *
 * 缺陷（Sprint 331 收尾時用真實瀏覽器實測屬實）：`/dashboard/posts/[id]/edit` 的 `handleSubmit` 只在 `catch` 裡
 * `setSaving(false)`，成功路徑不重設；頁面元件沒有卸載（只是重新載入），所以儲存成功之後按鈕永遠停在「儲存中...」
 * 且 disabled，使用者不重新整理就無法再儲存一次。
 *
 * 為什麼用真實後端：這個缺陷要「儲存成功 → 重新載入 → 按鈕狀態」整段走完才看得到；mock 掉儲存或載入任一端，
 * 按鈕卡住與否就取決於 mock 的寫法，不是頁面的行為。
 *
 * 斷言刻意不綁按鈕文字與成功訊息文字（合併貼文介面〔DEF-466〕時兩者可能調整），只綁行為：
 * 儲存按鈕在成功之後仍可按、第二次修改能真的送出並寫進伺服器。
 */

const WEB_BASE = 'http://localhost:3000';
const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

test.describe('AT-DASHBOARD-POST-EDIT-REAL: 貼文編輯頁儲存後可再次儲存（真實後端）', () => {
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let owner: Account;
  let postId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seeded = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 貼文編輯店',
      });
      owner = seeded.owner;

      const created = await page.request.post(`${API_BASE}/v2/dashboard/posts`, {
        headers: await authHeaders(page),
        data: { title: 'E2E 編輯貼文 原始標題', content: '這是 E2E 用的貼文內容。' },
      });
      expect(created.status(), await created.text()).toBe(200);
      postId = (await created.json()).data.id as string;
      expect(postId, '貼文應已建立').toBeTruthy();
    } finally {
      await page.close();
    }
  });

  /** 在編輯表單改標題並按儲存，等到「儲存請求」回 200 才回傳 */
  async function editTitleAndSave(page: Page, title: string): Promise<void> {
    // 標題是表單裡第一個文字欄位（兩套貼文介面的版面都是如此）
    const titleInput = page.locator('form input[type="text"]').first();
    await titleInput.fill(title);
    const saved = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && new URL(r.url()).pathname.endsWith(`/v2/dashboard/posts/${postId}`)
    );
    await page.locator('form button[type="submit"]').click();
    expect((await saved).status(), '儲存請求應成功').toBe(200);
  }

  test('E2E-POST-EDIT-01: 儲存成功後，儲存按鈕恢復可按，第二次修改也能存進伺服器', async ({ page }: { page: Page }) => {
    await loginOnly(page, owner.email, owner.password);
    await page.goto(`/dashboard/posts/${postId}/edit`);
    await expect(page.locator('form input[type="text"]').first()).toHaveValue('E2E 編輯貼文 原始標題');

    await editTitleAndSave(page, 'E2E 編輯貼文 第一次修改');

    // 儲存成功並重新載入之後：表單帶入伺服器上剛存的值，儲存按鈕不能卡在「儲存中」
    const saveButton = page.locator('form button[type="submit"]');
    await expect(page.locator('form input[type="text"]').first()).toHaveValue('E2E 編輯貼文 第一次修改');
    await expect(saveButton, 'DEF-467：儲存成功後按鈕必須恢復可按').toBeEnabled();
    await expect(saveButton).not.toContainText('儲存中');

    // 不重新整理就再改一次、再存一次——按鈕卡住的話這一步會逾時
    await editTitleAndSave(page, 'E2E 編輯貼文 第二次修改');

    const afterSecond = await page.request.get(`${API_BASE}/v2/dashboard/posts/${postId}`, {
      headers: await authHeaders(page),
    });
    expect(afterSecond.status(), await afterSecond.text()).toBe(200);
    expect((await afterSecond.json()).data.title, '第二次修改應已寫進伺服器').toBe('E2E 編輯貼文 第二次修改');
  });
});
