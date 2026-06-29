import { test, expect, type Page, type Browser } from '@playwright/test';

/**
 * AT-M10-CHAT: M10 IM 即時聊天 E2E（Sprint 25 US-004 / AC-004-4）
 *
 * 驗證前端接上 Sprint 24 STOMP 後端的即時收訊 happy path：
 * 1. 使用者 A、B 各自註冊登入
 * 2. A 於 /dashboard/chat 發起與 B 的對話並送出初始訊息
 * 3. A 於輸入框送出訊息，訊息串即時顯示
 * 4. B 開啟同一對話，A 再送一則訊息 → B 的訊息串「即時」收到（STOMP）
 *
 * 需求：後端（含 WebSocket /ws）於 http://localhost:8080/api 運行，前端於 :3000。
 */

interface RegisteredUser {
  email: string;
  password: string;
  userId: string;
}

/** 透過 UI 註冊並登入，回傳 localStorage 中的 userId。 */
async function registerAndLogin(page: Page, label: string): Promise<RegisteredUser> {
  const email = `e2e-chat-${label}-${Date.now()}@example.com`;
  const password = 'Test123!';

  await page.goto('/login');
  await page.click('a:has-text("create a new account")');

  await page.fill('input[name="fullName"]', `Chat ${label}`);
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="confirmPassword"]', password);
  await page.click('button[type="submit"]');

  await page.waitForURL('**/login**', { timeout: 15000 });

  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 15000 });

  const userId = await page.evaluate(() => {
    const raw = localStorage.getItem('user');
    return raw ? (JSON.parse(raw).id as string) : '';
  });
  expect(userId).toBeTruthy();

  return { email, password, userId };
}

async function loginExisting(page: Page, user: RegisteredUser): Promise<void> {
  await page.goto('/login');
  await page.fill('input[name="email"]', user.email);
  await page.fill('input[name="password"]', user.password);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 15000 });
}

test.describe('AT-M10-CHAT: M10 IM 即時聊天', () => {
  test('A 與 B 即時收發訊息 happy path', async ({ page, browser }: { page: Page; browser: Browser }) => {
    // 1. 先在獨立 context 註冊 B，取得 B 的 userId（保留登入供稍後即時接收）
    const contextB = await browser.newContext();
    const pageB = await contextB.newPage();
    const userB = await registerAndLogin(pageB, 'b');

    // 2. A 註冊登入
    await registerAndLogin(page, 'a');

    // 3. A 發起與 B 的對話並送出初始訊息
    await page.goto('/dashboard/chat');
    await page.click('[data-testid="new-conversation-button"]');
    await page.fill('[data-testid="new-recipient-input"]', userB.userId);
    await page.fill('[data-testid="new-message-input"]', 'Hello from A');
    await page.click('[data-testid="create-conversation-submit"]');

    // 對話建立後自動開啟，訊息串顯示初始訊息
    await expect(page.getByTestId('message-thread')).toContainText('Hello from A', {
      timeout: 15000,
    });

    // 4. A 透過輸入框送出訊息，訊息串即時顯示
    await page.fill('[data-testid="message-input"]', 'Second message from A');
    await page.click('[data-testid="send-button"]');
    await expect(page.getByTestId('message-thread')).toContainText('Second message from A', {
      timeout: 15000,
    });

    // 5. B 開啟同一對話
    await loginExisting(pageB, userB);
    await pageB.goto('/dashboard/chat');
    await pageB.click('[data-testid^="conversation-item-"]');
    await expect(pageB.getByTestId('message-thread')).toContainText('Hello from A', {
      timeout: 15000,
    });
    // 等待 STOMP 連線就緒
    await expect(pageB.getByTestId('socket-status')).toHaveText('即時連線中', { timeout: 15000 });
    // socket-status 於 onConnect 同步翻為 connected，但 SUBSCRIBE frame 需短暫時間送達/處理；
    // 稍候確保訂閱就緒，避免在 sub-100ms 窗口送出而錯過廣播（真實使用者訂閱後持續有效）
    await pageB.waitForTimeout(1500);

    // 6. A 再送一則 → B 即時收到（STOMP，非重新整理）
    const liveText = `Live ping ${Date.now()}`;
    await page.fill('[data-testid="message-input"]', liveText);
    await page.click('[data-testid="send-button"]');

    await expect(pageB.getByTestId('message-thread')).toContainText(liveText, { timeout: 15000 });

    await contextB.close();
  });
});
