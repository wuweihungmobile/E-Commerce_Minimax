// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 公開頁伺服器端渲染共用的取資料步驟（lib/public-data.ts，DEF-500，Sprint 341）。這裡守的是各頁共同依賴的幾條規則：
//   ① 店鋪不存在 → notFound()（真正的 404）；後端暫時不可用 → 絕不是 404（好好的店鋪不能因為後端一時壞了被搜尋引擎除名）；
//   ② 店鋪底下的頁面用不丟例外的版本（店鋪存在與否只由 stores/[slug]/layout.tsx 判定）；
//   ③ 貼文列表與元件同一條規則：指定了分類就要先確認它存在，不存在的分類編號不會送出貼文請求；
//   ④ 文章內嵌的商品卡片：取不到的略過（元件自己載入並顯示「已下架」或錯誤）、數量有上限、編號格式不對的不送出。

const fetchPublicMock = vi.hoisted(() => vi.fn())
vi.mock('./server-api', async () => {
  const actual = await vi.importActual<typeof import('./server-api')>('./server-api')
  return { ...actual, fetchPublic: fetchPublicMock }
})
vi.mock('next/navigation', () => ({
  notFound: () => {
    throw new Error('NEXT_HTTP_ERROR_FALLBACK;404')
  },
}))

import { loadEmbeddedCards, loadStoreBlog, loadStoreProfile, peekStoreProfile } from './public-data'

const TENANT_ID = '3f2a1c9e-5b7d-4e8f-9a6b-1c2d3e4f5a6b'
const ok = <T>(data: T) => ({ status: 'ok' as const, data })
const notfound = { status: 'notfound' as const }
const unavailable = { status: 'unavailable' as const }
const pathsRequested = () => fetchPublicMock.mock.calls.map((call) => String(call[0]))

beforeEach(() => {
  fetchPublicMock.mockReset() // 區塊寫法：箭頭函式直接回傳 mock 的話，vitest 會把它當成清理函式在每個測試之後呼叫
})

describe('店鋪的公開資料', () => {
  it('loadStoreProfile（layout 用）：存在 → 資料；不存在 → notFound()（真正的 404）；後端暫時不可用 → undefined，絕不是 404', async () => {
    fetchPublicMock.mockResolvedValueOnce(ok({ tenantId: TENANT_ID, name: '山海小店' }))
    expect(await loadStoreProfile('demo')).toEqual({ tenantId: TENANT_ID, name: '山海小店' })

    fetchPublicMock.mockResolvedValueOnce(notfound)
    await expect(loadStoreProfile('gone')).rejects.toThrow('NEXT_HTTP_ERROR_FALLBACK;404')

    fetchPublicMock.mockResolvedValueOnce(unavailable)
    expect(await loadStoreProfile('demo'), '好好的店鋪因為後端一時壞了就回 404，搜尋引擎會把它除名').toBeUndefined()
  })

  it('peekStoreProfile（頁面用）：不存在與不可用都回 undefined，不丟例外——「店鋪存在嗎」只由 layout 判定', async () => {
    fetchPublicMock.mockResolvedValueOnce(notfound)
    expect(await peekStoreProfile('gone')).toBeUndefined()
    fetchPublicMock.mockResolvedValueOnce(unavailable)
    expect(await peekStoreProfile('demo')).toBeUndefined()
    fetchPublicMock.mockResolvedValueOnce(ok({ name: '山海小店' }))
    expect(await peekStoreProfile('demo')).toEqual({ name: '山海小店' })
  })

  it('兩者請求同一條路徑（請求內快取靠逐字相同的路徑合併 layout 與頁面的請求），而且 slug 有編碼', async () => {
    fetchPublicMock.mockResolvedValue(ok({}))
    await loadStoreProfile('a b/c')
    await peekStoreProfile('a b/c')
    expect(pathsRequested()).toEqual(['/v2/public/stores/a%20b%2Fc', '/v2/public/stores/a%20b%2Fc'])
  })
})

describe('店鋪貼文列表的伺服器端資料（loadStoreBlog）', () => {
  const categories = { categories: [{ id: 'c1', name: '旅遊筆記' }], totalCount: 1 }
  const posts = { posts: [{ id: 'p1' }], totalCount: 1, page: 0, size: 12, totalPages: 1 }

  function serve(options: { categories?: unknown; posts?: unknown } = {}) {
    fetchPublicMock.mockImplementation(async (path: string) => {
      if (path.includes('/categories')) return options.categories ?? ok(categories)
      return options.posts ?? ok(posts)
    })
  }

  it('沒指定分類：分類與這一頁的貼文互不依賴，一起取；貼文請求不帶 categoryId', async () => {
    serve()
    const result = await loadStoreBlog(TENANT_ID, 2)

    expect(result).toEqual({ categories, posts })
    expect(pathsRequested().sort()).toEqual(
      [`/v2/posts/categories?tenantId=${TENANT_ID}`, `/v2/posts?tenantId=${TENANT_ID}&page=2&size=12`].sort()
    )
  })

  it('指定了存在的分類：先確認它存在，再取該分類的貼文（帶 categoryId）', async () => {
    serve()
    const result = await loadStoreBlog(TENANT_ID, 0, 'c1')

    expect(result.posts).toEqual(posts)
    expect(pathsRequested()[pathsRequested().length - 1]).toBe(`/v2/posts?tenantId=${TENANT_ID}&page=0&size=12&categoryId=c1`)
  })

  it('指定的分類不存在（網址亂填、已停用）：只回分類清單，根本不送出貼文請求（後端對格式錯誤的編號會回 400）', async () => {
    serve()
    const result = await loadStoreBlog(TENANT_ID, 0, 'not-a-real-category')

    expect(result).toEqual({ categories })
    expect(pathsRequested().filter((path) => path.startsWith('/v2/posts?'))).toEqual([])
  })

  it('指定了分類但分類清單取不到：無法確認它存不存在 → 不取貼文（元件會自己載入並顯示錯誤，不冒充成「不存在」）', async () => {
    serve({ categories: unavailable })
    const result = await loadStoreBlog(TENANT_ID, 0, 'c1')

    expect(result).toEqual({ categories: undefined })
    expect(pathsRequested().filter((path) => path.startsWith('/v2/posts?'))).toEqual([])
  })

  it('沒指定分類時，分類取不到不影響貼文（篩選列是附加功能）', async () => {
    serve({ categories: unavailable })
    const result = await loadStoreBlog(TENANT_ID, 0)

    expect(result.categories).toBeUndefined()
    expect(result.posts).toEqual(posts)
  })

  it('tenantId 不是 UUID（後端回應有問題）：什麼都不送，不信任後端回傳的任意字串', async () => {
    expect(await loadStoreBlog('x&evil=1', 0)).toEqual({})
    expect(fetchPublicMock).not.toHaveBeenCalled()
  })
})

describe('文章內嵌的商品卡片（loadEmbeddedCards）', () => {
  const id = (n: number) => `0b135be9-bf30-4ddb-ab3f-0000000000${String(n).padStart(2, '0')}`
  const embed = (listingId: string) => `{{embed:listing:${listingId}}}`

  it('每個編號取一次（內文裡重複出現的只取一次），結果以編號為鍵', async () => {
    fetchPublicMock.mockImplementation(async (path: string) => ok({ title: path }))
    const cards = await loadEmbeddedCards(`${embed(id(1))} 文字 ${embed(id(2))} 又一次 ${embed(id(1))}`)

    expect(Object.keys(cards)).toEqual([id(1), id(2)])
    expect(fetchPublicMock).toHaveBeenCalledTimes(2)
    expect(pathsRequested()[0]).toBe(`/v2/listings/${id(1)}/card`)
  })

  it('取不到的卡片（已下架 404、後端暫時不可用）略過，其餘照常——元件自己載入並顯示「此商品已下架」或錯誤', async () => {
    fetchPublicMock.mockImplementation(async (path: string) => (path.includes(id(2)) ? notfound : path.includes(id(3)) ? unavailable : ok({ ok: path })))
    const cards = await loadEmbeddedCards(`${embed(id(1))}${embed(id(2))}${embed(id(3))}`)

    expect(Object.keys(cards)).toEqual([id(1)])
  })

  it('編號不是 UUID 格式的不送出請求（內文是店家寫的，不信任）', async () => {
    fetchPublicMock.mockResolvedValue(ok({}))
    expect(await loadEmbeddedCards('{{embed:listing:abc-123}} {{embed:listing:../../etc/passwd}}')).toEqual({})
    expect(fetchPublicMock).not.toHaveBeenCalled()
  })

  it('一篇文章最多先取 12 張（多出來的由瀏覽器自己載入）——店家寫的內文沒有數量上限，不能讓一篇文章拖住整個頁面', async () => {
    fetchPublicMock.mockResolvedValue(ok({}))
    const content = Array.from({ length: 30 }, (_, n) => embed(id(n))).join(' ')
    const cards = await loadEmbeddedCards(content)

    expect(fetchPublicMock).toHaveBeenCalledTimes(12)
    expect(Object.keys(cards)).toHaveLength(12)
  })

  it('沒有任何嵌入：不送出請求', async () => {
    expect(await loadEmbeddedCards('一般的文章內容')).toEqual({})
    expect(fetchPublicMock).not.toHaveBeenCalled()
  })
})
