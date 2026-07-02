import { Page } from '@playwright/test';

/**
 * 共用測試帳號 helper（Sprint 39 US-004 / AI-2101）
 *
 * 收斂原本散落於 9 份 spec 的重複 registerAndLogin，並收斂 DEF-022（固定 sleep）：
 * - 以 waitForURL 條件等待取代 waitForTimeout（成功即早返回，不再固定等 3 秒）
 * - submit 選擇器排除賣場 Header 的搜尋鈕（button[type=submit]:not(:has-text("搜尋"))），
 *   避免 S37 揭露的碰撞（登入頁頂端有共用 Header 的搜尋表單）
 *
 * 行為：優先登入；帳號不存在（仍停在 /login）則自動註冊後再登入。
 * 回傳 { email, password, userId } 供呼叫端後續使用。
 *
 * Sprint 41 US-002（AI-2101b）：完全統一登入 helper——
 * - registerAndLogin 回傳增加 userId（讀 localStorage.user），供 at-m10-chat 等需要 userId 的情境
 * - 新增 loginOnly()：固定帳號登入（不註冊），供 admin（at-m17-002）或既有帳號再登入情境
 */
const LOGIN_SUBMIT = 'button[type="submit"]:not(:has-text("搜尋"))';

export interface AuthResult {
  email: string;
  password: string;
  userId: string;
}

function onLoginPage(page: Page): boolean {
  return page.url().includes('/login');
}

/** 讀取登入後 localStorage 中的 userId（無則回空字串）。 */
async function readUserId(page: Page): Promise<string> {
  return page.evaluate(() => {
    const raw = localStorage.getItem('user');
    if (!raw) return '';
    try {
      return (JSON.parse(raw).id as string) ?? '';
    } catch {
      return '';
    }
  });
}

async function submitLogin(page: Page, email: string, password: string): Promise<void> {
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click(LOGIN_SUBMIT);
  // 成功會離開 /login（早返回）；帳號不存在則停在 /login，等到 timeout 由呼叫端判斷
  await page
    .waitForURL((url) => !url.pathname.includes('/login'), { timeout: 4000 })
    .catch(() => {});
}

export async function registerAndLogin(
  page: Page,
  testEmail?: string
): Promise<AuthResult> {
  const email =
    testEmail || `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const password = 'Test123!';

  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');
  await submitLogin(page, email, password);

  // 仍在登入頁 → 帳號不存在 → 自動註冊後再登入
  if (onLoginPage(page)) {
    const registerLink = page
      .locator('a:has-text("create a new account"), a:has-text("註冊")')
      .first();
    if (await registerLink.isVisible().catch(() => false)) {
      await registerLink.click();
      await page.waitForURL('**/register**', { timeout: 6000 }).catch(() => {});
    }

    await page.fill('input[name="fullName"]', 'E2E Test User');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.fill('input[name="confirmPassword"]', password);
    await page.click(LOGIN_SUBMIT);
    // 註冊成功通常導回 /login?registered=true
    await page.waitForURL('**/login**', { timeout: 6000 }).catch(() => {});

    if (onLoginPage(page)) {
      await submitLogin(page, email, password);
    }
  }

  const userId = await readUserId(page);
  return { email, password, userId };
}

/**
 * 固定帳號登入（不註冊）。供 admin（at-m17-002）或「同一帳號再次登入」（at-m10-chat）情境。
 * 沿用 submitLogin 的安全 submit 選擇器與軟等待（成功即離開 /login）。
 */
export async function loginOnly(
  page: Page,
  email: string,
  password: string
): Promise<void> {
  await page.goto('/login');
  await page.waitForLoadState('domcontentloaded');
  await submitLogin(page, email, password);
}
