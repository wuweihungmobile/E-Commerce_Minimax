import { test, expect, APIRequestContext, APIResponse } from '@playwright/test';
import { postAuthApi } from './helpers/auth';
import { Account, adminAccessToken, seedStore } from './helpers/store';

/**
 * AT-PRICING-CART-REAL: 動態定價「規則 → 購物車 → 訂單金額」整條鏈（真實後端、真實 Redis）—— Sprint 308
 *
 * 為什麼需要：
 * - DEF-423（Sprint 307）修的是「建立規則時，夾帶別家店的商品 id 不被驗證」。它的危害有多大，取決於規則會不會一路傳到消費者
 *   實際付的錢：購物車（`RedisCartService.toCartItemResponse`）讀取時以 `PricingService.getEffectivePrice` 重算單價，結帳
 *   （`OrderService.createOrderFromCart`）直接取購物車回應的單價與小計寫進訂單明細與總額。後端的單元與整合測試把 Redis 換成 mock
 *   （`IntegrationTestConfiguration`），所以這一段從來沒有在真實 Redis 上串起來驗證過；Sprint 307 只實測到 `effective-price` 端點。
 *   本規格在打包 JAR ＋ PostgreSQL ＋ Redis ＋ 兩家真實店鋪（真實開店流程）＋ 消費者上，把整條鏈走一遍。
 * - E2E-PCART-02 是 E2E-PCART-01 的對照組：店主為「自己的」商品建立規則，消費者的購物車與訂單必須是規則價。沒有它，
 *   E2E-PCART-01 的「原價」會是空洞的斷言——規則根本不生效時看到的也是原價。
 *
 * E2E-PCART-03／04 守住 DEF-424（Sprint 309，使用者以互動選項裁決「關閉後立即失效」）：
 * - 原本店鋪層的 `DYNAMIC_PRICING_ENABLED` 只管「能不能建立規則」；規則套用到購物車時，開關是以**呼叫者的租戶**查
 *   （`FeatureToggleService.isFeatureEnabled` 讀 `TenantContext`），而購物車端點只有 BUYER 持有權限，他們的租戶都是系統租戶
 *   佔位值——它的開關在 Flyway 種子（V1、V6）是 TRUE。於是管理員關掉某家店的開關，它既有的規則照樣套用到消費者
 *   （Sprint 308 的 E2E-PCART-03 當時是「現況記錄」，斷言的就是這個行為）。
 * - 這**不違反當時的規格**（PRD 只寫「Service 層在建規則前查詢」），是規格沒定義的產品語意，所以 Sprint 308 只記錄、不擅自改；
 *   Sprint 309 依使用者裁決，由定價引擎（`PricingService.calculatePrice`／`getEffectivePrice`）統一以**房源／商品所屬店鋪**的
 *   開關把關：關閉時規則一律不套用（購物車、訂房、價格試算端點自動一致），重新開啟立即恢復，且各店鋪互不影響。
 *   03 因此從「規則照樣套用」翻成「回原價」；04 驗證重新開啟後同一條規則恢復。
 * - 同一個語意在 Hibernate 測試庫與 Flyway 資料庫的前提不同（系統租戶的開關：測試庫關閉、Flyway 種子開啟），後端的 Java E2E
 *   （`StoreToggleGatesApplicationE2ETest`）因此不依賴它；本規格跑在 Flyway 資料庫，是最接近正式環境的一道。
 *
 * 資料全由真實流程建立（見 helpers/store.ts）；店鋪與消費者都用 API 登入，不碰瀏覽器登入狀態、少用登入限流額度。
 * 店鋪 A（HYBRID，有房源與商品）是「想夾帶別店商品 id 的賣家」；店鋪 B（只賣商品）是被夾帶的對象。
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
const WEB_BASE = 'http://localhost:3000';
const PASSWORD = 'Test123!';

const BASE_PRICE = 1000;
const OWN_RULE_PRICE = 800;
const SMUGGLED_PRICE = 1;
const OWN_RULE_NAME = 'E2E 店鋪B自己的規則';
const A_RULE_PRICE = 900;
const A_RULE_NAME = 'E2E 店鋪A自己的規則';

type AuthHeaders = { Authorization: string };
type CartItem = {
  listingId: string;
  unitPrice: number;
  subtotal: number;
  originalUnitPrice?: number | null;
  discountAmount?: number | null;
  appliedRuleName?: string | null;
  priceAdjustmentType?: string | null;
};
type Cart = { items: CartItem[]; totalAmount: number };
type OrderItem = { listingId: string; unitPrice: number; subtotal: number };
type Order = { id: string; tenantId: string; totalAmount: number; shippingFee: number; items: OrderItem[] };

/** 規則有效期間取寬（前後各留幾天），避免 UTC 日期與營運時區差一天的邊界。 */
const day = (offset: number) => new Date(Date.now() + offset * 86_400_000).toISOString().slice(0, 10);

async function loginViaApi(request: APIRequestContext, account: Account): Promise<AuthHeaders> {
  const login = await postAuthApi(request, `${API_BASE}/v2/auth/login`, { email: account.email, password: account.password });
  expect(login.status(), await login.text()).toBe(200);
  return { Authorization: `Bearer ${(await login.json()).data.accessToken as string}` };
}

/** 一位全新的消費者（不屬於任何店鋪，租戶是系統租戶佔位值）。 */
async function newConsumer(request: APIRequestContext): Promise<AuthHeaders> {
  const email = `e2e-s308-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const register = await postAuthApi(request, `${API_BASE}/v2/auth/register`, {
    email,
    password: PASSWORD,
    fullName: 'E2E 動態定價消費者',
    userType: 'BUYER',
  });
  expect(register.status(), await register.text()).toBe(201);
  return loginViaApi(request, { email, password: PASSWORD });
}

/** 管理員開關店鋪的動態定價（PUT /v2/admin/tenants/{id}/features/{key}）。 */
async function setDynamicPricing(request: APIRequestContext, adminToken: string, tenantId: string, enabled: boolean): Promise<void> {
  const response = await request.put(`${API_BASE}/v2/admin/tenants/${tenantId}/features/DYNAMIC_PRICING_ENABLED`, {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: { enabled },
  });
  expect(response.status(), await response.text()).toBe(200);
}

async function postRule(request: APIRequestContext, headers: AuthHeaders, body: Record<string, unknown>): Promise<APIResponse> {
  return request.post(`${API_BASE}/v2/dashboard/pricing/rules`, {
    headers,
    data: { ruleType: 'MANUAL_OVERRIDE', priority: 100, validFrom: day(-3), validTo: day(30), ...body },
  });
}

async function addToCart(request: APIRequestContext, headers: AuthHeaders, listingId: string): Promise<void> {
  const add = await request.post(`${API_BASE}/v2/cart/items`, { headers, data: { listingId, quantity: 1 } });
  expect(add.status(), await add.text()).toBeLessThan(300);
}

async function getCart(request: APIRequestContext, headers: AuthHeaders): Promise<Cart> {
  const response = await request.get(`${API_BASE}/v2/cart`, { headers });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).data as Cart;
}

async function placeOrder(request: APIRequestContext, headers: AuthHeaders, storeId: string): Promise<Order> {
  const response = await request.post(`${API_BASE}/v2/orders`, {
    headers,
    data: {
      orderType: 'PRODUCT',
      storeId,
      shippingAddress: '台北市信義區信義路五段 7 號',
      shippingRecipientName: 'E2E 動態定價收件人',
      shippingPhone: '0987654321',
    },
  });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).data as Order;
}

test.describe('AT-PRICING-CART-REAL: 動態定價 規則→購物車→訂單（真實後端、真實 Redis）', () => {
  // 開店流程只需要做一次；後面的案例沿用同一組店鋪與商品，且 02 建立的規則是 03 的前提
  test.describe.configure({ mode: 'serial', timeout: 90_000 });

  let adminToken: string;
  let ownerA: Account;
  let ownerB: Account;
  let roomA: string;
  let productA: string;
  let tenantB: string;
  let productB: string;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    const page = await browser.newPage({ baseURL: WEB_BASE });
    try {
      const seededA = await seedStore(page, {
        businessType: 'HYBRID',
        storeLabel: 'E2E 動態定價店鋪A',
        room: { name: 'E2E 店鋪A房型', price: 1500 },
        product: { name: 'E2E 店鋪A商品', price: BASE_PRICE },
      });
      const seededB = await seedStore(page, {
        businessType: 'RETAIL_ONLY',
        storeLabel: 'E2E 動態定價店鋪B',
        product: { name: 'E2E 店鋪B商品', price: BASE_PRICE },
      });
      ownerA = seededA.owner;
      roomA = seededA.roomId as string;
      productA = seededA.productId as string;
      ownerB = seededB.owner;
      tenantB = seededB.tenantId;
      productB = seededB.productId as string;

      adminToken = await adminAccessToken(page);
      // 店鋪 A 必須有開關，否則建立規則在第一行就被 E-2004 擋下，永遠走不到擁有權檢查（E-1007）。
      // 店鋪 B（被夾帶的對象）刻意保持預設的「關閉」：它從沒申請過動態定價，商品也不該被別人改價。
      await setDynamicPricing(page.request, adminToken, seededA.tenantId, true);
    } finally {
      await page.close();
    }
  });

  test('E2E-PCART-01: 店鋪 A 帶自己的房源 id、夾帶店鋪 B 的商品 id 建立規則 → 403 E-1007；消費者的購物車與訂單仍是原價（DEF-423 整條鏈）', async ({ request }) => {
    const smuggle = await postRule(request, await loginViaApi(request, ownerA), {
      roomListingId: roomA,
      listingId: productB,
      ruleName: 'E2E 夾帶別店商品',
      config: { price: SMUGGLED_PRICE },
    });
    // 狀態碼與所有「金額」斷言都用 soft：守門失效（201）時，後面仍會一路走到購物車與訂單，把整條鏈每一步被改寫後的
    // 實際金額都印出來，看得到影響到哪一步（Sprint 308 突變驗證即靠這個得到修復前的整條鏈數字）
    expect.soft(smuggle.status(), await smuggle.text()).toBe(403);
    expect.soft((await smuggle.json()).code, '被擋下的原因必須是擁有權檢查（E-1007），不是功能開關（E-2004）').toBe('E-1007');

    const consumer = await newConsumer(request);
    await addToCart(request, consumer, productB);
    const cart = await getCart(request, consumer);
    expect(cart.items, '購物車只有店鋪 B 的那件商品').toHaveLength(1);
    expect(cart.items[0].listingId).toBe(productB);
    expect.soft(cart.items[0].unitPrice, '購物車單價仍是原價，沒有被夾帶的規則改寫').toBe(BASE_PRICE);
    expect.soft(cart.items[0].appliedRuleName ?? null, '沒有任何規則套用在這件商品上').toBeNull();
    expect.soft(cart.totalAmount).toBe(BASE_PRICE);

    const order = await placeOrder(request, consumer, tenantB);
    expect(order.tenantId, '訂單歸店鋪 B').toBe(tenantB);
    expect(order.items).toHaveLength(1);
    expect(order.shippingFee, '店鋪 B 沒有運費模板').toBe(0);
    expect.soft(order.items[0].unitPrice, '訂單明細單價是原價').toBe(BASE_PRICE);
    expect.soft(order.totalAmount, '訂單總額是原價').toBe(BASE_PRICE);
  });

  test('E2E-PCART-02: 對照組——店鋪 B 為自己的商品建立規則 → 消費者的購物車與訂單都是規則價', async ({ request }) => {
    await setDynamicPricing(request, adminToken, tenantB, true);
    const create = await postRule(request, await loginViaApi(request, ownerB), {
      listingId: productB,
      ruleName: OWN_RULE_NAME,
      config: { price: OWN_RULE_PRICE },
    });
    expect(create.status(), await create.text()).toBe(201);

    const consumer = await newConsumer(request);
    await addToCart(request, consumer, productB);
    const cart = await getCart(request, consumer);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0]).toMatchObject({
      unitPrice: OWN_RULE_PRICE,
      originalUnitPrice: BASE_PRICE,
      discountAmount: BASE_PRICE - OWN_RULE_PRICE,
      appliedRuleName: OWN_RULE_NAME,
      priceAdjustmentType: 'DISCOUNT',
    });
    expect(cart.totalAmount, '購物車總額用調整後的小計').toBe(OWN_RULE_PRICE);

    const order = await placeOrder(request, consumer, tenantB);
    expect(order.items).toHaveLength(1);
    expect(order.items[0].unitPrice, '訂單明細單價沿用購物車算出的規則價').toBe(OWN_RULE_PRICE);
    expect(order.items[0].subtotal).toBe(OWN_RULE_PRICE);
    expect(order.totalAmount, '消費者實際被收的金額是規則價').toBe(OWN_RULE_PRICE);
  });

  test('E2E-PCART-03: 管理員關閉店鋪 B 的動態定價 → 新規則被擋（E-2004），既有規則立即對消費者失效（購物車與訂單回原價）；開關仍開的店鋪 A 不受影響（DEF-424）', async ({ request }) => {
    // 店鋪 A 的開關仍開啟：先替它自己的商品建一條規則，之後用來證明「各店鋪的開關互不影響」
    const createA = await postRule(request, await loginViaApi(request, ownerA), {
      listingId: productA,
      ruleName: A_RULE_NAME,
      config: { price: A_RULE_PRICE },
    });
    expect(createA.status(), await createA.text()).toBe(201);

    // 管理員撤銷店鋪 B 的動態定價（B 在 E2E-PCART-02 建立的規則還在，沒有被刪除或停用）
    await setDynamicPricing(request, adminToken, tenantB, false);

    const blocked = await postRule(request, await loginViaApi(request, ownerB), {
      listingId: productB,
      ruleType: 'SEASONAL',
      ruleName: 'E2E 關閉後新增',
      config: { multiplier: 1.2 },
    });
    expect(blocked.status(), '開關對「建立規則」有效：' + (await blocked.text())).toBe(403);
    expect((await blocked.json()).code).toBe('E-2004');

    const consumer = await newConsumer(request);
    await addToCart(request, consumer, productB);
    await addToCart(request, consumer, productA);
    const cart = await getCart(request, consumer);
    expect(cart.items, '購物車有兩家店鋪的商品').toHaveLength(2);
    const itemB = cart.items.find((i) => i.listingId === productB);
    const itemA = cart.items.find((i) => i.listingId === productA);
    expect(itemB, '店鋪 B 的商品在購物車內').toBeTruthy();
    expect(itemA, '店鋪 A 的商品在購物車內').toBeTruthy();
    expect(itemB!.unitPrice, '店鋪 B 的開關已關閉：既有規則立即失效，購物車回原價（Sprint 308 的現況記錄在此翻轉）').toBe(BASE_PRICE);
    expect(itemB!.appliedRuleName ?? null, '沒有任何規則套用在店鋪 B 的商品上').toBeNull();
    expect(itemB!.originalUnitPrice ?? null).toBeNull();
    expect(itemA!.unitPrice, '店鋪 A 的開關仍開啟：它的規則照常套用，不受店鋪 B 的開關影響').toBe(A_RULE_PRICE);
    expect(itemA!.appliedRuleName).toBe(A_RULE_NAME);

    // 真正重要的是消費者實際被收多少：訂單明細與總額跟著購物車回原價
    const order = await placeOrder(request, consumer, tenantB);
    expect(order.items).toHaveLength(1);
    expect(order.items[0].unitPrice, '訂單明細單價是原價').toBe(BASE_PRICE);
    expect(order.totalAmount, '消費者實際被收的金額是原價，不是已被撤銷的規則價').toBe(BASE_PRICE);
  });

  test('E2E-PCART-04: 管理員重新開啟店鋪 B 的動態定價 → 同一條規則立刻恢復套用（沒有被刪除或停用），訂單金額跟著回到規則價（DEF-424）', async ({ request }) => {
    await setDynamicPricing(request, adminToken, tenantB, true);

    const consumer = await newConsumer(request);
    await addToCart(request, consumer, productB);
    const cart = await getCart(request, consumer);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0]).toMatchObject({
      unitPrice: OWN_RULE_PRICE,
      originalUnitPrice: BASE_PRICE,
      appliedRuleName: OWN_RULE_NAME,
    });

    const order = await placeOrder(request, consumer, tenantB);
    expect(order.items[0].unitPrice).toBe(OWN_RULE_PRICE);
    expect(order.totalAmount, '重新開啟後，消費者被收的金額回到規則價').toBe(OWN_RULE_PRICE);
  });
});
