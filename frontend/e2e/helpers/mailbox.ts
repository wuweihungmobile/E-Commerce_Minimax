import { expect, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

/**
 * 信箱 helper（Sprint 204，DEF-252／253）。
 *
 * 後端目前只有日誌型 Mock 寄信（LoggingEmailSender）：非 prod profile 時，把信件全文（含一次性連結）寫進後端日誌。
 * E2E 用真實後端（profile=test），所以從**後端日誌檔**取得寄給某個信箱的最新連結，效果等同「打開信箱點連結」。
 *
 * 日誌檔路徑由 E2E 啟動腳本以環境變數 `E2E_BACKEND_LOG` 提供（scripts/validate-e2e.sh 與雲端 e2e job 都會設）。
 * 沒設就直接失敗並說清楚——不靜默跳過：驗證流程是開店申請的前置條件，少了它相關 spec 本來就無法成立。
 *
 * 為什麼不做成後端「開發用信箱查詢端點」：那個端點等於「任何人可讀他人的密碼重設連結」，一旦被誤部署到非預期
 * 環境就是帳號接管。日誌檔只有跑 E2E 的人讀得到。
 */
const MARKER = '[MOCK-EMAIL] to=';

export type MailKind = 'reset-password' | 'verify-email';

function readBackendLog(): string {
  const logPath = process.env.E2E_BACKEND_LOG;
  if (!logPath) {
    throw new Error(
      '缺少環境變數 E2E_BACKEND_LOG（後端日誌檔路徑）。請經 `make validate-e2e` 執行，或自行設定為 backend 日誌檔的絕對路徑。'
    );
  }
  return readFileSync(logPath, 'utf-8');
}

/** 在日誌中找出「寄給 `to`、且內含 `kind` 連結」的最新一封信的 token；找不到回 null。 */
function findLatestToken(log: string, to: string, kind: MailKind): string | null {
  const marker = `${MARKER}${to} `;
  let from = log.length;
  while (from > 0) {
    const start = log.lastIndexOf(marker, from - 1);
    if (start < 0) return null;
    // 這封信的內容到下一封信的標記為止（信件全文含換行）
    const nextMail = log.indexOf(MARKER, start + marker.length);
    const block = log.slice(start, nextMail < 0 ? undefined : nextMail);
    const match = block.match(new RegExp(`/${kind}\\?token=([A-Za-z0-9_-]+)`));
    if (match) return match[1];
    from = start;
  }
  return null;
}

/** 輪詢後端日誌，直到出現寄給 `to` 的 `kind` 連結，回傳其 token。 */
export async function waitForMailToken(
  page: Page,
  to: string,
  kind: MailKind,
  timeoutMs = 15000
): Promise<string> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const token = findLatestToken(readBackendLog(), to, kind);
    if (token) return token;
    if (Date.now() > deadline) {
      throw new Error(`${timeoutMs}ms 內後端日誌沒有出現寄給 ${to} 的 ${kind} 信件`);
    }
    await page.waitForTimeout(250);
  }
}

/** 等同「收到註冊驗證信並點連結」：走 UI 的 /verify-email 頁，並斷言驗證成功。 */
export async function verifyEmailViaMailbox(page: Page, email: string): Promise<void> {
  const token = await waitForMailToken(page, email, 'verify-email');
  await page.goto(`/verify-email?token=${token}`);
  await expect(page.getByTestId('verify-email-success')).toBeVisible({ timeout: 15000 });
}
