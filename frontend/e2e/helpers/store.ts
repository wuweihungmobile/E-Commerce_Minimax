import { expect, Page } from '@playwright/test';
import { registerAndLogin, loginOnly } from './auth';
import { verifyEmailViaMailbox } from './mailbox';

/**
 * 真實開店流程 helper（Sprint 234 自 at-seller-dashboard-real.spec.ts 抽出，避免第三份複製）。
 *
 * 資料全由真實流程建立，不 mock、不寫 SQL：店主註冊→驗證 Email→申請開店→管理員核准→（需要訂房時）管理員開啟
 * BOOKING_ENABLED（核准後預設關閉，沒開就建立不了房源，403 E-2004）→店主重新登入（JWT 才帶新租戶）→建立房源／商品。
 * 信件是日誌型 Mock，Email 驗證連結從 E2E_BACKEND_LOG 取（見 helpers/mailbox.ts）。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const PASSWORD = 'Test123!';

export type Account = { email: string; password: string };

export interface SeedStoreOptions {
  /** 申請開店時的 businessType：只賣商品用 RETAIL_ONLY，有房源要 BOOKING_ONLY 或 HYBRID。 */
  businessType: 'RETAIL_ONLY' | 'BOOKING_ONLY' | 'HYBRID';
  /** 店名前綴（後面會接時間戳，保證唯一）。 */
  storeLabel: string;
  /** 建立一間 ROOM 房源（需要 BOOKING_ONLY／HYBRID，會替店鋪開啟 BOOKING_ENABLED）。 */
  room?: { name: string; price: number };
  /** 建立一件 PRODUCT 商品（不建 SKU＝未啟用庫存追蹤，下單時略過預扣，不必進貨）。 */
  product?: { name: string; price: number };
}

export interface SeededStore {
  owner: Account;
  tenantId: string;
  roomId?: string;
  productId?: string;
}

export async function accessToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('accessToken'));
  expect(token, '應已登入並持有 accessToken').toBeTruthy();
  return token as string;
}

export async function authHeaders(page: Page): Promise<{ Authorization: string }> {
  return { Authorization: `Bearer ${await accessToken(page)}` };
}

export async function seedStore(page: Page, options: SeedStoreOptions): Promise<SeededStore> {
  const owner = await registerAndLogin(page);
  await verifyEmailViaMailbox(page, owner.email);

  const storeName = `${options.storeLabel} ${Date.now()}`;
  const apply = await page.request.post(`${API_BASE}/v2/tenants/apply`, {
    headers: await authHeaders(page),
    data: {
      storeName,
      storeDescription: `${options.storeLabel} 自建前提`,
      businessType: options.businessType,
      contactEmail: `store-${Date.now()}@example.com`,
      contactPhone: '0912345678',
    },
  });
  expect(apply.status(), await apply.text()).toBe(201);

  await page.evaluate(() => localStorage.clear());
  await loginOnly(page, 'admin@nextkey.local', PASSWORD);
  const adminHeaders = await authHeaders(page);
  const pending = await page.request.get(`${API_BASE}/v2/admin/tenant-applications`, { headers: adminHeaders });
  expect(pending.status(), await pending.text()).toBe(200);
  const applications = (await pending.json()).data.applications as Array<{ applicationId: string; storeName: string }>;
  const application = applications.find((a) => a.storeName === storeName);
  expect(application, '待審核列表應含剛送出的申請').toBeTruthy();
  const approve = await page.request.post(
    `${API_BASE}/v2/admin/tenant-applications/${application!.applicationId}/approve`,
    { headers: adminHeaders }
  );
  expect(approve.status(), await approve.text()).toBe(200);
  const tenantId = (await approve.json()).data.tenantId as string;

  if (options.room) {
    const toggle = await page.request.put(`${API_BASE}/v2/admin/tenants/${tenantId}/features/BOOKING_ENABLED`, {
      headers: adminHeaders,
      data: { enabled: true },
    });
    expect(toggle.status(), await toggle.text()).toBe(200);
  }

  await page.evaluate(() => localStorage.clear());
  await loginOnly(page, owner.email, owner.password);
  const ownerHeaders = await authHeaders(page);
  const seeded: SeededStore = { owner: { email: owner.email, password: owner.password }, tenantId };

  if (options.room) {
    const room = await page.request.post(`${API_BASE}/v2/dashboard/listings`, {
      headers: ownerHeaders,
      data: {
        listingType: 'ROOM',
        name: options.room.name,
        description: options.storeLabel,
        price: options.room.price,
        location: '花蓮',
        maxGuests: 2,
        roomCount: 5,
      },
    });
    expect(room.status(), await room.text()).toBe(200);
    seeded.roomId = (await room.json()).data.listingId as string;
  }
  if (options.product) {
    const product = await page.request.post(`${API_BASE}/v2/dashboard/listings`, {
      headers: ownerHeaders,
      data: {
        listingType: 'PRODUCT',
        name: options.product.name,
        description: options.storeLabel,
        price: options.product.price,
        category: '生活',
        brand: 'E2E',
      },
    });
    expect(product.status(), await product.text()).toBe(200);
    seeded.productId = (await product.json()).data.listingId as string;
  }
  return seeded;
}
