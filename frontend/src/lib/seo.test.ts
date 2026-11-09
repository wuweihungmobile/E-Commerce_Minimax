// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchPublicData } from './server-api'
import { listingMetadata, storeMetadata } from './seo'
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
