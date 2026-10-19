import { Page } from '@playwright/test';

/**
 * 共用測試帳號 helper（Sprint 39 US-004 / AI-2101）
 *
 * 收斂原本散落於 9 份 spec 的重複 registerAndLogin，並收斂 DEF-022（固定 sleep）：
 * - 以 waitForURL 條件等待取代 waitForTimeout（成功即早返回，不再固定等 3 秒）
 * - submit 選擇器排除賣場 Header 的搜尋鈕（button[type=submit]:not(:has-text("搜尋"))），
 *   避免 S37 揭露的碰撞（登入頁頂端有共用 Header 的搜尋表單）
 *
 * 行為：指定 email 時優先登入；未指定（全新隨機帳號）或帳號不存在（仍停在 /login）則自動註冊後再登入。
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

/**
 * 送出登入後等待「有結果」的上限。
 * Sprint 233：密碼雜湊成本改為 12（FRD NFR-SEC-002），成功的登入在雙 worker 並行的負載下偶爾超過原本固定的 4 秒；
 * 那時 token 還沒存好就繼續往下，造成間歇性的「應已登入並持有 accessToken」失敗（頁面快照停在「Signing in...」）。
 */
const LOGIN_RESULT_TIMEOUT_MS = 20_000;

/**
 * 登入端點有 IP 限流（`LoginRateLimitFilter`：每個來源 IP、每個路徑 30 次／分，Sprint 168），錯誤文字是「已超過速率限制」
 * （E-9904）。E2E 套件所有請求都來自同一個 IP，跑得越快、登入越密集，越容易撞到——Sprint 234 實測：套件 3.6 分鐘跑完時
 * 約每分鐘 38 次登入，超過每分鐘 30 次的補充速率，數個案例的登入被擋、拿不到 token。
 * 被限流時登入頁會顯示該錯誤；token bucket 每 2 秒補一個，所以等一下重試即可，不需要放寬正式環境的限流。
 */
const RATE_LIMIT_ERROR_TEXT = '速率限制';
const RATE_LIMIT_RETRY_WAIT_MS = 2_500;
const RATE_LIMIT_MAX_RETRIES = 8;

async function submitLogin(page: Page, email: string, password: string): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.click(LOGIN_SUBMIT);
    // 等到「有結果」才返回，而不是固定等一段時間：
    //  - 成功：離開 /login（token 在導向前就已存入 localStorage）
    //  - 失敗（例如帳號不存在）：登入頁顯示錯誤訊息；後端立即回應，所以註冊流程的第一次嘗試不會白等
    // 兩者都沒出現（極端情況）才在上限後放行，由呼叫端依 URL 判斷。
    const settled = (p: Promise<unknown>) => p.then(() => true, () => false);
    await Promise.race([
      settled(
        page.waitForURL((url) => !url.pathname.includes('/login'), {
          timeout: LOGIN_RESULT_TIMEOUT_MS,
          waitUntil: 'commit',
        })
      ),
      settled(page.getByTestId('login-error').waitFor({ state: 'visible', timeout: LOGIN_RESULT_TIMEOUT_MS })),
    ]);

    // 只有「被限流」才重試；帳號不存在、密碼錯誤等其他失敗維持原行為——回到仍在登入頁的狀態，
    // 交給呼叫端依 onLoginPage(page) 判斷（registerAndLogin 的「先試登入、失敗就改註冊」正是靠這個）。
    const rateLimited =
      onLoginPage(page) &&
      (await page
        .getByTestId('login-error')
        .innerText({ timeout: 1_000 })
        .then((text) => text.includes(RATE_LIMIT_ERROR_TEXT), () => false));
    if (!rateLimited) return;
    if (attempt >= RATE_LIMIT_MAX_RETRIES) {
      // Sprint 286：被限流且重試次數已用盡——這對任何呼叫端都不是「可接受的分支」（不像「帳號
      // 不存在」可能是刻意要測的情境），一律視為基礎設施問題大聲失敗，而非悄悄讓呼叫端在毫無
      // token 的狀態下繼續往下執行，直到不相關的下游症狀（元素找不到／401）才被迫倒查回這裡。
      throw new Error(
        `submitLogin: 持續被速率限制擋下，已重試 ${RATE_LIMIT_MAX_RETRIES} 次仍失敗（email=${email}）`
      );
    }
    await page.waitForTimeout(RATE_LIMIT_RETRY_WAIT_MS);
  }
}

/**
 * 送出註冊表單並等待導回登入頁；遇到 Sprint 183（DEF-250）的註冊端點 IP 限流（與登入端點
 * 共用同一套 Lua token bucket 機制，但以路徑+IP 獨立計量）時重試，比照 {@link submitLogin}。
 *
 * Sprint 286：這裡先前完全沒有重試機制——被限流時 `page.waitForURL('**\/login**')` 逾時，
 * 由呼叫端的 `.catch(() => {})` 吞掉並靜默留在 `/register`，導致 {@link registerAndLogin}
 * 誤判「仍在 /login」為 false 而整個跳過 {@link submitLogin}，最終回傳一個從未真正登入過的
 * 「假成功」。這是全量 `make validate-release` 跑 174 個 spec 時，`at-account-data-rights.spec.ts`
 * 兩個測試必然失敗（登出畫面＋「Authentication required」）的根因：註冊頁沒有 `data-testid`
 * 標記錯誤訊息，改以文字比對偵測限流。
 */
async function submitRegister(
  page: Page,
  fullName: string,
  email: string,
  password: string
): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    await page.goto('/register');
    await page.waitForLoadState('domcontentloaded');
    await page.fill('input[name="fullName"]', fullName);
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.fill('input[name="confirmPassword"]', password);
    await page.click(LOGIN_SUBMIT);
    // 註冊成功通常導回 /login?registered=true
    await page.waitForURL('**/login**', { timeout: 6_000 }).catch(() => {});

    if (onLoginPage(page)) return; // 成功導回登入頁

    const rateLimited = await page
      .getByText(RATE_LIMIT_ERROR_TEXT)
      .isVisible()
      .catch(() => false);
    if (!rateLimited || attempt >= RATE_LIMIT_MAX_RETRIES) {
      throw new Error(
        `submitRegister: 註冊失敗，仍停在 ${page.url()}（email=${email}, attempt=${attempt}, rateLimited=${rateLimited}）`
      );
    }
    await page.waitForTimeout(RATE_LIMIT_RETRY_WAIT_MS);
  }
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
  // 沒有指定 email＝全新的隨機帳號，不可能已存在：先試登入只會必然失敗，卻白白消耗一次登入限流額度
  // （Sprint 234 實測：48 個呼叫點全是全新帳號，這個多餘的登入約佔全套件登入的三分之一）。
  // 呼叫端明確指定 email（可能是既有帳號）時才維持「優先登入」。
  if (testEmail) {
    await submitLogin(page, email, password);
  }

  // 仍在登入頁 → 帳號不存在 → 自動註冊後再登入
  // 直接前往 /register（不點連結）：S37 共用 StorefrontHeader 於 /login 也有一個「註冊」
  // 連結（<a href="/register">註冊</a>），與登入表單的註冊連結文字碰撞，.first() 恆選到
  // header 連結，而其於失敗登入後 re-render 時不穩定 → click 間歇逾時（at-buyer-pages flaky
  // 根因）。與 helper 頂端記載的 S37「submit 與 Header 搜尋鈕碰撞」同類，改直接 goto 消除歧義。
  if (onLoginPage(page)) {
    await submitRegister(page, 'E2E Test User', email, password);

    if (onLoginPage(page)) {
      await submitLogin(page, email, password);
    }
  }

  const userId = await readUserId(page);
  if (!userId) {
    // Sprint 286：最後一道防呆——即使前面每一步都「沒拋例外」，也要確認真的登入成功了，
    // 不讓呼叫端在毫無 token 的瀏覽器狀態下繼續執行，等下游症狀出現才被迫倒查回這裡。
    throw new Error(`registerAndLogin: 流程走完但讀不到 userId，尚未真正登入（email=${email}）`);
  }
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
