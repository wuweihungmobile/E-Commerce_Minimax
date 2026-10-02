import { test, expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './helpers/auth';
import { Account, authHeaders, seedStore } from './helpers/store';

/**
 * AT-SYSTEM-TENANT-ISOLATION-REAL: 沒有店鋪的使用者不得碰到店家層資料（真實後端，完全不 mock）—— Sprint 234
 *
 * 為什麼需要：沒有加入任何店鋪的使用者（一般買家）的租戶不是 null，而是系統租戶佔位值，所有這類使用者共用同一個；
 * 而一般消費者建立的資料（訂單、客服工單…）也蓋成這個租戶。店家層端點若直接拿「呼叫者的租戶」去查或比對，
 * 任一買家就等於「同租戶」，看得到、甚至能改所有其他買家的資料。Sprint 232 修了訂單與訂房的列表，
 * Sprint 233 讀碼時又發現客服工單與退貨申請同型，唯讀稽核證實 BUYER 持有 support_ticket:read／create，
 * 所以 /v2/dashboard/support/tickets* 對任何買家都放行——**包括以「店員」身分在別人的工單發言**。
 *
 * 這份 spec 用兩個真實消費者（都沒有店鋪）重現，並守住修復：B 不得列出、讀取或回覆 A 的客服工單。
 * 資料全由真實流程建立（開店→商品→A 下單→A 開工單）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PRODUCT_PRICE = 450;
const TICKET_SUBJECT = 'E2E 商品到貨有瑕疵';
const IMPERSONATION = '我是客服人員，請提供您的信用卡末四碼以便退款';

type TicketItem = { id: string; subject: string };

test.describe('AT-SYSTEM-TENANT-ISOLATION-REAL: 沒有店鋪的使用者看不到店家層客服工單（真實後端）', () => {
  // 後面的案例都依賴前面建立的商品、訂單與工單，且開店流程只需要做一次
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let productId: string;
  let customerA: Account;
  let customerB: Account;
  let ticketId: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      ({ productId } = (await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 系統租戶隔離店',
        product: { name: 'E2E 隔離測試杯', price: PRODUCT_PRICE },
      })) as { productId: string });
    } finally {
      await page.close();
    }
  });

  test('E2E-STI-01: 消費者 A（沒有店鋪）買商品、對那筆訂單開客服工單（建立前提）', async ({ page }: { page: Page }) => {
    customerA = await registerAndLogin(page);
    const headers = await authHeaders(page);
    const add = await page.request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId: productId, quantity: 1 } });
    expect(add.status(), await add.text()).toBeLessThan(300);
    const order = await page.request.post(`${API_BASE}/v2/orders`, {
      headers,
      data: {
        orderType: 'PRODUCT',
        shippingAddress: '台北市信義區信義路五段 7 號',
        shippingRecipientName: 'E2E 收件人',
        shippingPhone: '0987654321',
      },
    });
    expect(order.status(), await order.text()).toBe(201);
    const orderId = (await order.json()).data.id as string;

    const ticket = await page.request.post(`${API_BASE}/v2/support/tickets`, {
      headers,
      data: { category: 'PRODUCT', subject: TICKET_SUBJECT, description: '收到的杯子有裂痕，請協助處理', orderId },
    });
    expect(ticket.status(), await ticket.text()).toBeLessThan(300);
    ticketId = (await ticket.json()).data.id as string;
  });

  test('E2E-STI-02: 另一位沒有店鋪的買家呼叫店家層工單列表 → 空，看不到別人的客服工單', async ({ page }: { page: Page }) => {
    await page.goto('/login');
    await page.evaluate(() => localStorage.clear());
    customerB = await registerAndLogin(page);
    expect(customerB.email).not.toBe(customerA.email);

    const response = await page.request.get(`${API_BASE}/v2/dashboard/support/tickets?size=100`, { headers: await authHeaders(page) });
    // 兩種修法都可接受：空頁，或明確拒絕；不可是別人的工單
    if (response.status() === 200) {
      const tickets = (await response.json()).data.tickets as TicketItem[];
      expect(tickets.map((t) => t.id), '沒有店鋪的買家不得從店家層列表看到別人的工單').not.toContain(ticketId);
      expect(tickets, '沒有店鋪的買家呼叫店家層工單列表，應是空的').toEqual([]);
    } else {
      expect([401, 403, 404]).toContain(response.status());
    }
  });

  test('E2E-STI-03: 同一位買家不得讀取別人工單的全文與訊息', async ({ page }: { page: Page }) => {
    // 每個案例都是全新的分頁，所以明確以買家 B 登入
    await loginOnly(page, customerB.email, customerB.password);
    const detail = await page.request.get(`${API_BASE}/v2/dashboard/support/tickets/${ticketId}`, { headers: await authHeaders(page) });
    expect(detail.status(), `不得讀到別人的工單：${await detail.text()}`).not.toBe(200);
    expect([401, 403, 404]).toContain(detail.status());
  });

  test('E2E-STI-04: 同一位買家不得以「店員」身分在別人的工單發言；A 的工單裡沒有任何 STAFF 訊息', async ({ page }: { page: Page }) => {
    await loginOnly(page, customerB.email, customerB.password);
    const post = await page.request.post(`${API_BASE}/v2/dashboard/support/tickets/${ticketId}/messages`, {
      headers: await authHeaders(page),
      data: { message: IMPERSONATION },
    });
    expect(post.status(), `不得在別人的工單冒充店員發言：${await post.text()}`).not.toBe(200);
    expect([401, 403, 404]).toContain(post.status());

    // 回到 A，確認自己的工單沒有被塞進任何店員訊息
    await page.evaluate(() => localStorage.clear());
    await loginOnly(page, customerA.email, customerA.password);
    const mine = await page.request.get(`${API_BASE}/v2/support/tickets/${ticketId}`, { headers: await authHeaders(page) });
    expect(mine.status(), await mine.text()).toBe(200);
    const messages = ((await mine.json()).data.messages ?? []) as Array<{ senderType: string; message: string }>;
    expect(messages.filter((m) => m.senderType === 'STAFF'), 'A 的工單不應出現任何店員訊息').toEqual([]);
    expect(messages.map((m) => m.message)).not.toContain(IMPERSONATION);
  });
});
