// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchPublicData } from './server-api'
import { articleJsonLd, listingJsonLd, listingMetadata, postMetadata, storeJsonLd, storeMetadata, websiteJsonLd } from './seo'
import { SITE_NAME } from './site'

// DEF-475（Sprint 335）：公開頁（店鋪前台、商品／房源詳情）的標題原本是靜態的頁型名稱（「店鋪首頁」「商品／房源詳情」），
// 搜尋結果與社群分享看到的是同一個標題。這裡守的是伺服器端取實體名稱的那一層：
//   1. 呼叫後端公開 API 的方式（網址組合、逾時、不快取）；
//   2. **任何失敗都退回原本的靜態頁型標題**——標題請求不能讓頁面壞掉或變慢；
//   3. 輸入來自網址（slug、id），必須編碼／驗證後才能放進伺服器端請求的路徑。

const fetchMock = vi.fn()

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubEnv('API_INTERNAL_URL', '')
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

const store = (extra: Record<string, unknown> = {}) => ({
  data: { tenantId: 't1', slug: 'demo', name: '山海小店', description: '賣山也賣海', logoUrl: null, ...extra },
})
const LISTING_ID = '0b135be9-bf30-4ddb-ab3f-092f3e26b845'
const TENANT_ID = '3f2a1c9e-5b7d-4e8f-9a6b-1c2d3e4f5a6b'
const post = (extra: Record<string, unknown> = {}) => ({
  data: {
    title: '手沖入門：從選豆開始',
    excerpt: '三個步驟學會手沖',
    content: '內文',
    featuredImageUrl: null,
    publishedAt: '2026-11-01T08:00:00Z',
    ...extra,
  },
})
/** 依請求的路徑回不同內容：店鋪 → store，文章 → post；其餘 404 */
function routeFetch(routes: { store?: Response | Error; post?: Response | Error }) {
  fetchMock.mockImplementation(async (url: string) => {
    const hit = String(url).includes('/v2/public/stores/') ? routes.store : String(url).includes('/v2/posts/') ? routes.post : undefined
    if (hit instanceof Error) throw hit
    return hit ?? json({}, 404)
  })
}
const listing = (extra: Record<string, unknown> = {}) => ({
  data: { id: LISTING_ID, title: '手沖咖啡豆', description: '淺焙', coverImageUrl: null, ...extra },
})

describe('fetchPublicData：伺服器端呼叫後端公開 API', () => {
  it('沒設定 API_INTERNAL_URL → 預設 http://localhost:8080/api，路徑接在後面', async () => {
    fetchMock.mockResolvedValue(json({ data: { ok: 1 } }))
    expect(await fetchPublicData('/v2/public/stores/demo')).toEqual({ ok: 1 })
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/v2/public/stores/demo')
  })

  it('有設定 API_INTERNAL_URL → 用它；結尾多餘的斜線會被去掉，不會變成 //', async () => {
    vi.stubEnv('API_INTERNAL_URL', 'http://backend.internal:8080/api//')
    fetchMock.mockResolvedValue(json({ data: {} }))
    await fetchPublicData('/v2/x')
    expect(fetchMock.mock.calls[0][0]).toBe('http://backend.internal:8080/api/v2/x')
  })

  it('不快取（店名改了、店鋪停權了，標題立刻反映）且有逾時（後端慢的時候不能拖住整個頁面）', async () => {
    fetchMock.mockResolvedValue(json({ data: {} }))
    await fetchPublicData('/v2/x')
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.cache).toBe('no-store')
    expect(init.signal, '沒有逾時訊號：後端沒回應時頁面會一直等').toBeInstanceOf(AbortSignal)
  })

  it('HTTP 非 2xx（404、500…）→ null', async () => {
    fetchMock.mockResolvedValue(json({ code: 'E-2000' }, 404))
    expect(await fetchPublicData('/v2/x')).toBeNull()
    fetchMock.mockResolvedValue(json({}, 500))
    expect(await fetchPublicData('/v2/x')).toBeNull()
  })

  it('HTTP 非 2xx 就不採用——就算錯誤回應的 body 剛好也帶了 data（例如代理層回的 JSON）', async () => {
    fetchMock.mockResolvedValue(json({ data: { name: '不該被採用的名字' } }, 502))
    expect(await fetchPublicData('/v2/x')).toBeNull()
  })

  it('網路錯誤、逾時（AbortError）→ null，不丟例外', async () => {
    fetchMock.mockRejectedValue(new TypeError('fetch failed'))
    expect(await fetchPublicData('/v2/x')).toBeNull()
    fetchMock.mockRejectedValue(new DOMException('The operation was aborted due to timeout', 'TimeoutError'))
    expect(await fetchPublicData('/v2/x')).toBeNull()
  })

  it('回應不是 JSON、或沒有 data → null', async () => {
    fetchMock.mockResolvedValue(new Response('<html>502</html>', { status: 200 }))
    expect(await fetchPublicData('/v2/x')).toBeNull()
    fetchMock.mockResolvedValue(json({ success: true }))
    expect(await fetchPublicData('/v2/x')).toBeNull()
  })
})

describe('storeMetadata：店鋪前台各頁', () => {
  it('取得店名 → 標題是店名；有說明就帶 description；Open Graph 帶標題、說明、網站名稱、頁面網址', async () => {
    fetchMock.mockResolvedValue(json(store()))
    const meta = await storeMetadata('demo', { fallbackTitle: '店鋪首頁', path: '' })
    expect(meta.title).toBe('山海小店')
    expect(meta.description).toBe('賣山也賣海')
    expect(meta.openGraph).toMatchObject({
      title: '山海小店',
      description: '賣山也賣海',
      siteName: SITE_NAME,
      type: 'website',
      url: '/stores/demo',
    })
  })

  it('子頁帶區段名稱：「店名｜商品」，網址帶上子路徑', async () => {
    fetchMock.mockResolvedValue(json(store()))
    const meta = await storeMetadata('demo', { section: '商品', fallbackTitle: '店鋪商品', path: '/products' })
    expect(meta.title).toBe('山海小店｜商品')
    expect(meta.openGraph).toMatchObject({ title: '山海小店｜商品', url: '/stores/demo/products' })
  })

  it('店標是絕對網址 → 放進 Open Graph 圖片；相對路徑或空值 → 不放（相對路徑可能是需要登入的內部網址）', async () => {
    fetchMock.mockResolvedValue(json(store({ logoUrl: 'https://cdn.example.com/logo.png' })))
    expect((await storeMetadata('demo', { fallbackTitle: 'x', path: '' })).openGraph).toMatchObject({
      images: ['https://cdn.example.com/logo.png'],
    })
    fetchMock.mockResolvedValue(json(store({ logoUrl: '/api/v2/media/files/abc' })))
    expect((await storeMetadata('demo', { fallbackTitle: 'x', path: '' })).openGraph).not.toHaveProperty('images')
  })

  it('說明是空的 → 不帶 description；說明太長 → 截成 160 字並加「…」', async () => {
    fetchMock.mockResolvedValue(json(store({ description: '   ' })))
    const empty = await storeMetadata('demo', { fallbackTitle: 'x', path: '' })
    expect(empty).not.toHaveProperty('description')
    expect(empty.openGraph).not.toHaveProperty('description')

    fetchMock.mockResolvedValue(json(store({ description: '長'.repeat(300) })))
    const long = await storeMetadata('demo', { fallbackTitle: 'x', path: '' })
    expect(long.description).toBe('長'.repeat(159) + '…')
    expect(long.description).toHaveLength(160)
  })

  it('店名前後的空白會被去掉；說明裡的換行與連續空白壓成一個空格（分享卡片不顯示斷行）', async () => {
    fetchMock.mockResolvedValue(json(store({ name: '  山海小店 ', description: '第一行\n\n  第二行' })))
    const meta = await storeMetadata('demo', { fallbackTitle: 'x', path: '' })
    expect(meta.title).toBe('山海小店')
    expect(meta.description).toBe('第一行 第二行')
  })

  it('找不到店鋪（404）、後端壞了、網路中斷 → 退回靜態頁型標題，而且只有標題（沒有殘缺的 Open Graph）', async () => {
    fetchMock.mockResolvedValue(json({ code: 'E-2000' }, 404))
    expect(await storeMetadata('nope', { fallbackTitle: '店鋪首頁', path: '' })).toEqual({ title: '店鋪首頁' })
    fetchMock.mockRejectedValue(new TypeError('fetch failed'))
    expect(await storeMetadata('demo', { section: '商品', fallbackTitle: '店鋪商品', path: '/products' })).toEqual({ title: '店鋪商品' })
  })

  it('店名是空的或不是字串 → 退回靜態標題（不能出現「｜商品」這種開頭空白的標題）', async () => {
    fetchMock.mockResolvedValue(json(store({ name: '  ' })))
    expect(await storeMetadata('demo', { section: '商品', fallbackTitle: '店鋪商品', path: '/products' })).toEqual({ title: '店鋪商品' })
    fetchMock.mockResolvedValue(json(store({ name: 123 })))
    expect(await storeMetadata('demo', { fallbackTitle: '店鋪首頁', path: '' })).toEqual({ title: '店鋪首頁' })
  })

  it('slug 來自網址：特殊字元要編碼後才放進伺服器端請求的路徑（不能用 ../ 之類跳到別的端點）', async () => {
    fetchMock.mockResolvedValue(json(store()))
    await storeMetadata('../admin/x?y=1#z', { fallbackTitle: 'x', path: '' })
    const url = String(fetchMock.mock.calls[0][0])
    expect(url).toBe('http://localhost:8080/api/v2/public/stores/..%2Fadmin%2Fx%3Fy%3D1%23z')
    expect(url).not.toContain('/admin/')
  })
})

describe('listingMetadata：商品／房源詳情', () => {
  it('取得名稱 → 標題是商品名；Open Graph 網址是 /listings/<id>', async () => {
    fetchMock.mockResolvedValue(json(listing({ coverImageUrl: 'https://cdn.example.com/c.jpg' })))
    const meta = await listingMetadata(LISTING_ID, '商品／房源詳情')
    expect(meta.title).toBe('手沖咖啡豆')
    expect(meta.description).toBe('淺焙')
    expect(meta.openGraph).toMatchObject({
      title: '手沖咖啡豆',
      siteName: SITE_NAME,
      type: 'website',
      url: `/listings/${LISTING_ID}`,
      images: ['https://cdn.example.com/c.jpg'],
    })
    expect(String(fetchMock.mock.calls[0][0])).toBe(`http://localhost:8080/api/v2/listings/${LISTING_ID}`)
  })

  it('id 不是 UUID → 根本不呼叫後端（省一次請求，也不讓任意字串進到伺服器端請求），直接用靜態標題', async () => {
    expect(await listingMetadata('x', '商品／房源詳情')).toEqual({ title: '商品／房源詳情' })
    expect(await listingMetadata('../../actuator/health', '商品／房源詳情')).toEqual({ title: '商品／房源詳情' })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('大寫的 UUID 也接受', async () => {
    fetchMock.mockResolvedValue(json(listing()))
    expect((await listingMetadata(LISTING_ID.toUpperCase(), 'x')).title).toBe('手沖咖啡豆')
  })

  it('找不到商品（404）、後端壞了 → 靜態標題', async () => {
    fetchMock.mockResolvedValue(json({ code: 'E-3000' }, 404))
    expect(await listingMetadata(LISTING_ID, '商品／房源詳情')).toEqual({ title: '商品／房源詳情' })
    fetchMock.mockRejectedValue(new TypeError('fetch failed'))
    expect(await listingMetadata(LISTING_ID, '商品／房源詳情')).toEqual({ title: '商品／房源詳情' })
  })
})


describe('postMetadata：文章頁（Sprint 336，DEF-483）', () => {
  const storeWithTenant = (extra: Record<string, unknown> = {}) => json(store({ tenantId: TENANT_ID, ...extra }))

  it('標題是「文章標題｜店名」；Open Graph 是文章型、帶發布時間，網址是 /stores/<店>/blog/<文章>', async () => {
    routeFetch({ store: storeWithTenant(), post: json(post()) })
    const meta = await postMetadata('demo', 'hand-brew', '部落格文章')
    expect(meta.title).toBe('手沖入門：從選豆開始｜山海小店')
    expect(meta.description).toBe('三個步驟學會手沖')
    expect(meta.openGraph).toMatchObject({
      title: '手沖入門：從選豆開始｜山海小店',
      siteName: SITE_NAME,
      type: 'article',
      publishedTime: '2026-11-01T08:00:00Z',
      url: '/stores/demo/blog/hand-brew',
    })
  })

  it('先查店鋪（得到 tenantId）再查文章；查文章帶 tenantId 與 countView=false（標題請求不是一次瀏覽）', async () => {
    routeFetch({ store: storeWithTenant(), post: json(post()) })
    await postMetadata('demo', 'hand-brew', '部落格文章')
    const urls = fetchMock.mock.calls.map((c) => String(c[0]))
    expect(urls).toEqual([
      'http://localhost:8080/api/v2/public/stores/demo',
      `http://localhost:8080/api/v2/posts/hand-brew?tenantId=${TENANT_ID}&countView=false`,
    ])
  })

  it('沒有摘要 → 用內文當 description，而且商品嵌入語法 {{embed:listing:…}} 不能出現在裡面', async () => {
    routeFetch({
      store: storeWithTenant(),
      post: json(post({ excerpt: '', content: `開頭 {{embed:listing:${LISTING_ID}}} 結尾` })),
    })
    const meta = await postMetadata('demo', 'hand-brew', '部落格文章')
    expect(meta.description).toBe('開頭 結尾')
  })

  it('封面圖是絕對網址 → 放進 Open Graph 圖片；相對路徑（可能是需要登入的內部網址）→ 不放', async () => {
    routeFetch({ store: storeWithTenant(), post: json(post({ featuredImageUrl: 'https://cdn.example.com/c.png' })) })
    expect((await postMetadata('demo', 'a', 'x')).openGraph?.images).toEqual(['https://cdn.example.com/c.png'])
    routeFetch({ store: storeWithTenant(), post: json(post({ featuredImageUrl: '/api/v2/media/files/abc' })) })
    expect((await postMetadata('demo', 'a', 'x')).openGraph).not.toHaveProperty('images')
  })

  it('發布時間不是合法日期 → 不放 publishedTime（不把壞資料寫進分享資訊）', async () => {
    routeFetch({ store: storeWithTenant(), post: json(post({ publishedAt: 'not-a-date' })) })
    expect((await postMetadata('demo', 'a', 'x')).openGraph).not.toHaveProperty('publishedTime')
  })

  it('店鋪不存在或非 ACTIVE（404）→ 靜態標題，而且根本不去查文章（停權店鋪的文章標題不會出現）', async () => {
    routeFetch({ store: json({ code: 'E-2000' }, 404), post: json(post()) })
    expect(await postMetadata('demo', 'hand-brew', '部落格文章')).toEqual({ title: '部落格文章' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('文章不存在、未發布（404）→ 靜態標題，只有標題（沒有殘缺的 Open Graph）', async () => {
    routeFetch({ store: storeWithTenant(), post: json({ code: 'E-4100' }, 404) })
    expect(await postMetadata('demo', 'hand-brew', '部落格文章')).toEqual({ title: '部落格文章' })
  })

  it('後端壞了、網路中斷 → 靜態標題（標題請求不能讓頁面壞掉）', async () => {
    routeFetch({ store: storeWithTenant(), post: json({}, 500) })
    expect(await postMetadata('demo', 'a', '部落格文章')).toEqual({ title: '部落格文章' })
    routeFetch({ store: new Error('ECONNREFUSED'), post: json(post()) })
    expect(await postMetadata('demo', 'a', '部落格文章')).toEqual({ title: '部落格文章' })
  })

  it('後端給的 tenantId 不像 UUID → 不拿去組請求（它會被放進查詢字串），直接用靜態標題', async () => {
    routeFetch({ store: json(store({ tenantId: 'x&evil=1' })), post: json(post()) })
    expect(await postMetadata('demo', 'a', '部落格文章')).toEqual({ title: '部落格文章' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('店鋪沒有 tenantId 欄位、或文章標題是空的 → 靜態標題', async () => {
    routeFetch({ store: json(store()), post: json(post()) }) // store() 預設 tenantId 是 't1'，不是 UUID
    expect(await postMetadata('demo', 'a', '部落格文章')).toEqual({ title: '部落格文章' })
    routeFetch({ store: storeWithTenant(), post: json(post({ title: '   ' })) })
    expect(await postMetadata('demo', 'a', '部落格文章')).toEqual({ title: '部落格文章' })
  })

  it('兩段 slug 都來自網址：特殊字元要編碼後才放進請求的路徑', async () => {
    routeFetch({ store: storeWithTenant(), post: json(post()) })
    await postMetadata('a/../b', 'x?y#z', '部落格文章')
    const urls = fetchMock.mock.calls.map((c) => String(c[0]))
    expect(urls[0]).toBe('http://localhost:8080/api/v2/public/stores/a%2F..%2Fb')
    expect(urls[1]).toContain('/v2/posts/x%3Fy%23z?tenantId=')
  })
})

// DEF-500（Sprint 341）：SEO。標準網址（canonical）與結構化資料（JSON-LD）。
describe('標準網址（canonical）', () => {
  it('店鋪頁、商品頁、文章頁都帶 canonical，且與 og:url 同一個乾淨路徑（追蹤參數進來的網址要收斂成同一個）', async () => {
    fetchMock.mockResolvedValue(json(store()))
    const storePage = await storeMetadata('demo', { fallbackTitle: '店鋪首頁', path: '/products', section: '商品' })
    expect(storePage.alternates?.canonical).toBe('/stores/demo/products')
    expect(storePage.alternates?.canonical).toBe((storePage.openGraph as { url?: string }).url)

    fetchMock.mockResolvedValue(json(listing()))
    expect((await listingMetadata(LISTING_ID, '詳情')).alternates?.canonical).toBe(`/listings/${LISTING_ID}`)

    routeFetch({ store: json(store({ tenantId: TENANT_ID })), post: json(post()) })
    expect((await postMetadata('demo', 'hand-brew', '部落格文章')).alternates?.canonical).toBe('/stores/demo/blog/hand-brew')
  })

  it('取不到資料時只回靜態標題，不帶殘缺的 canonical', async () => {
    fetchMock.mockResolvedValue(json({}, 404))
    const result = await storeMetadata('gone', { fallbackTitle: '店鋪首頁', path: '' })
    expect(result).toEqual({ title: '店鋪首頁' })
  })
})

/** 依路徑（a.b.c）取巢狀欄位；取不到是 undefined。JSON-LD 的形狀是資料，不為它另外宣告型別。 */
const at = (value: unknown, path: string): unknown =>
  path.split('.').reduce<unknown>((current, key) => (current as Record<string, unknown> | null | undefined)?.[key], value)

describe('結構化資料（JSON-LD）', () => {
  it('整站：網站名稱＋站內搜尋（搜尋走首頁的 ?keyword=）', () => {
    const data = websiteJsonLd()
    expect(at(data, '@type')).toBe('WebSite')
    expect(at(data, 'name')).toBe(SITE_NAME)
    expect(at(data, 'potentialAction.target.urlTemplate')).toBe('https://www.nextkey.com/?keyword={search_term_string}')
  })

  it('商品：Product＋名稱、說明、圖片、價格與幣別；url 是絕對網址', () => {
    const data = listingJsonLd(LISTING_ID, {
      title: ' 手沖咖啡豆 ', description: '淺焙', coverImageUrl: 'https://img.example/a.png', listingType: 'PRODUCT', basePrice: 350, currency: 'TWD',
    })
    expect(at(data, '@type')).toBe('Product')
    expect(at(data, 'name')).toBe('手沖咖啡豆')
    expect(at(data, 'url')).toBe(`https://www.nextkey.com/listings/${LISTING_ID}`)
    expect(at(data, 'image')).toBe('https://img.example/a.png')
    expect(at(data, 'offers')).toMatchObject({ '@type': 'Offer', price: 350, priceCurrency: 'TWD' })
  })

  it('房源：HotelRoom；沒有合法價格就不帶 offers（寧可少，不寫錯）；相對路徑的圖片不收（爬蟲抓不到）', () => {
    expect(at(listingJsonLd(LISTING_ID, { title: '海景雙人房', listingType: 'ROOM', basePrice: 2800, currency: 'TWD' }), '@type')).toBe('HotelRoom')
    const noPrice = listingJsonLd(LISTING_ID, { title: '商品', listingType: 'PRODUCT', basePrice: 'abc', currency: 'TWD', coverImageUrl: '/api/v2/media/x.png' })
    expect(at(noPrice, 'offers')).toBeUndefined()
    expect(at(noPrice, 'image')).toBeUndefined()
    expect(listingJsonLd(LISTING_ID, { title: '  ' })).toBeNull()
  })

  it('文章：BlogPosting＋標題、發布日期、作者、所屬店鋪部落格；說明去掉商品嵌入語法；標題最長 110 字', () => {
    const data = articleJsonLd('demo', 'hand-brew', '山海小店', {
      title: '手沖入門', content: '先選豆 {{embed:listing:0b135be9-bf30-4ddb-ab3f-092f3e26b845}} 再磨豆', publishedAt: '2026-11-01T08:00:00Z', authorName: '小明',
    })
    expect(at(data, '@type')).toBe('BlogPosting')
    expect(at(data, 'url')).toBe('https://www.nextkey.com/stores/demo/blog/hand-brew')
    expect(at(data, 'datePublished')).toBe('2026-11-01T08:00:00Z')
    expect(at(data, 'author')).toEqual({ '@type': 'Person', name: '小明' })
    expect(at(data, 'isPartOf')).toMatchObject({ '@type': 'Blog', name: '山海小店｜部落格' })
    expect(at(data, 'description')).toBe('先選豆 再磨豆')
    expect(String(at(articleJsonLd('demo', 'x', '店', { title: 'a'.repeat(200) }), 'headline'))).toHaveLength(110)
    expect(articleJsonLd('demo', 'x', '店', { title: '' })).toBeNull()
  })

  it('店鋪：Store＋店名、說明、Logo；沒有店名就不輸出', () => {
    const data = storeJsonLd('demo', { name: '山海小店', description: '賣山也賣海', logoUrl: 'https://img.example/logo.png' })
    expect(data).toMatchObject({ '@type': 'Store', name: '山海小店', url: 'https://www.nextkey.com/stores/demo', logo: 'https://img.example/logo.png' })
    expect(storeJsonLd('demo', { name: '' })).toBeNull()
  })

  it('slug 含特殊字元時網址有編碼（不會破壞結構）', () => {
    expect(at(storeJsonLd('a b/c', { name: '店' }), 'url')).toBe('https://www.nextkey.com/stores/a%20b%2Fc')
  })
})
