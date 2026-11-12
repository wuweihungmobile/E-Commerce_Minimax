// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildSitemap, loadSitemapSources } from './sitemap'

// sitemap.xml（DEF-500，Sprint 341）：告訴搜尋引擎有哪些公開頁面。這裡守的是「收錄什麼、不收錄什麼」與「後端壞了不能讓 sitemap 壞掉或烤進殘缺內容」。

const L1 = '0b135be9-bf30-4ddb-ab3f-092f3e26b845'
const L2 = '1c246cfa-c041-4eec-bc40-1a4f3f37c956'
const urls = (entries: { url: string }[]) => entries.map((e) => e.url.replace('https://www.nextkey.com', ''))

describe('buildSitemap：收錄什麼', () => {
  const entries = buildSitemap(
    [
      { id: L1, listingType: 'PRODUCT', tenantSlug: 'demo', updatedAt: '2026-11-01T00:00:00Z' },
      { id: L2, listingType: 'ROOM', tenantSlug: 'demo' },
    ],
    [{ slug: 'hand brew', tenantSlug: 'cafe', publishedAt: '2026-10-01T00:00:00Z' }]
  )

  it('固定頁面：首頁、商品、房源、部落格、隱私權政策、服務條款', () => {
    expect(urls(entries).slice(0, 6)).toEqual(['/', '/products', '/rooms', '/blog', '/privacy', '/terms'])
  })

  it('店鋪只列「有內容」的頁：有商品才列商品頁、有房源才列房源頁、有文章才列部落格頁（沒有內容的頁是空頁，不該被收錄）', () => {
    const list = urls(entries)
    expect(list).toContain('/stores/demo')
    expect(list).toContain('/stores/demo/products')
    expect(list).toContain('/stores/demo/rooms')
    expect(list).not.toContain('/stores/demo/blog')
    expect(list).toContain('/stores/cafe')
    expect(list).toContain('/stores/cafe/blog')
    expect(list).not.toContain('/stores/cafe/products')
  })

  it('每個商品／房源與每篇文章都有網址；文章網址帶店鋪、slug 有編碼；有日期才帶 lastModified', () => {
    const list = urls(entries)
    expect(list).toContain(`/listings/${L1}`)
    expect(list).toContain(`/listings/${L2}`)
    expect(list).toContain('/stores/cafe/blog/hand%20brew')
    const withDate = entries.find((e) => e.url.endsWith(`/listings/${L1}`))
    const without = entries.find((e) => e.url.endsWith(`/listings/${L2}`))
    expect(withDate?.lastModified).toEqual(new Date('2026-11-01T00:00:00Z'))
    expect(without).not.toHaveProperty('lastModified')
  })

  it('不收錄分頁、排序、搜尋結果（同一批內容的不同切法）', () => {
    expect(urls(entries).filter((u) => u.includes('?'))).toEqual([])
  })

  it('沒有店鋪代稱的項目（舊後端、資料不完整）略過，不會產生 /stores/undefined', () => {
    const list = urls(buildSitemap([{ id: L1, listingType: 'PRODUCT' }], [{ slug: 'x', tenantSlug: '', publishedAt: null }]))
    expect(list.filter((u) => u.startsWith('/stores'))).toEqual([])
    expect(list).toContain(`/listings/${L1}`)
  })
})

describe('loadSitemapSources：後端壞了不能讓 sitemap 壞掉', () => {
  const fetchMock = vi.fn()
  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  const listingPage = (ids: string[], totalPages: number) => ({
    data: { content: ids.map((id) => ({ id, listingType: 'PRODUCT', tenantSlug: 'demo' })), totalPages },
  })
  const postPage = (slugs: string[], totalPages: number) => ({
    data: { posts: slugs.map((slug) => ({ slug, tenantSlug: 'demo', publishedAt: null })), totalPages },
  })

  it('多頁：讀完所有頁，標記為完整', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      const u = new URL(String(url))
      const page = Number(u.searchParams.get('page'))
      if (u.pathname.endsWith('/v2/public/listings')) return json(listingPage([`l${page}`], 3))
      return json(postPage([`p${page}`], 2))
    })
    const result = await loadSitemapSources()
    expect(result.listings.map((l) => l.id)).toEqual(['l0', 'l1', 'l2'])
    expect(result.posts.map((p) => p.slug)).toEqual(['p0', 'p1'])
    expect(result.complete).toBe(true)
  })

  it('後端完全壞了：回空清單、標記為不完整（呼叫端只輸出固定頁面，而且不留存，下一次重試）', async () => {
    fetchMock.mockResolvedValue(json({}, 500))
    expect(await loadSitemapSources()).toEqual({ listings: [], posts: [], complete: false })
    fetchMock.mockRejectedValue(new Error('network down'))
    expect(await loadSitemapSources()).toEqual({ listings: [], posts: [], complete: false })
  })

  it('中間某一頁失敗：保留已讀到的，標記為不完整', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      const u = new URL(String(url))
      const page = Number(u.searchParams.get('page'))
      if (u.pathname.endsWith('/v2/public/posts')) return json(postPage(['p0'], 1))
      return page === 1 ? json({}, 429) : json(listingPage([`l${page}`], 3))
    })
    const result = await loadSitemapSources()
    expect(result.listings.map((l) => l.id)).toEqual(['l0', 'l2'])
    expect(result.complete).toBe(false)
  })

  it('回應格式不對（沒有 content／posts）→ 不當成成功，不會把壞資料塞進 sitemap', async () => {
    fetchMock.mockResolvedValue(json({ data: { unexpected: true } }))
    expect(await loadSitemapSources()).toEqual({ listings: [], posts: [], complete: false })
  })
})
