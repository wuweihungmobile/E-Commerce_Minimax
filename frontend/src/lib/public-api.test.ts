// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  FEATURED_LIMIT,
  LIST_PAGE_SIZE,
  listingCardPath,
  listingPath,
  postBySlugPath,
  publicListingsPath,
  publicPostFeedPath,
  SORT_MAP,
  storeCategoriesPath,
  storeListingsPath,
  storePostsPath,
  storeProfilePath,
} from './public-api'

// 契約測試（DEF-500，Sprint 341）：伺服器端渲染（SEO）取的資料，必須和瀏覽器端的 services 取的是「同一批端點、同一組查詢參數」。
// 兩邊各自組請求網址，哪天有人改了其中一邊（改參數名、加篩選、換端點），伺服器端渲染出來的第一頁就會和瀏覽器之後載入的不一致——
// 搜尋引擎看到的與使用者看到的不同，或換頁後內容跳掉，而且沒有任何型別或單元測試的訊號。這裡實際呼叫 services，
// 攔下它們送出的請求，和伺服器端的路徑逐項比對。

const get = vi.hoisted(() => vi.fn())
vi.mock('@/lib/axios', () => ({ default: { get } }))

import listingService from '@/services/listing'
import storefrontService from '@/services/storefront'
import { getListingCard, getPublicCategories, getPublicPostFeed, getPublishedPostBySlug, getPublishedPosts } from '@/services/cms'

interface Requested {
  path: string
  query: Record<string, string>
}

/** 把 apiClient.get(url, { params }) 這種呼叫還原成「路徑＋查詢參數」（axios 會把 params 物件序列化進網址；undefined 不送）。 */
function lastRequest(): Requested {
  const [url, config] = get.mock.calls[get.mock.calls.length - 1] as [string, { params?: Record<string, unknown> }?]
  const parsed = new URL(url, 'http://backend')
  const query: Record<string, string> = Object.fromEntries(parsed.searchParams.entries())
  for (const [key, value] of Object.entries(config?.params ?? {})) {
    if (value !== undefined) query[key] = String(value)
  }
  return { path: parsed.pathname, query }
}

/** 伺服器端組出來的路徑，同樣還原成「路徑＋查詢參數」。 */
function serverRequest(path: string): Requested {
  const parsed = new URL(path, 'http://backend')
  return { path: parsed.pathname, query: Object.fromEntries(parsed.searchParams.entries()) }
}

const TENANT_ID = '3f2a1c9e-5b7d-4e8f-9a6b-1c2d3e4f5a6b'
const LISTING_ID = '0b135be9-bf30-4ddb-ab3f-092f3e26b845'

beforeEach(() => {
  get.mockReset()
  get.mockResolvedValue({ data: { data: {} } })
})

describe('伺服器端與瀏覽器端的請求一致', () => {
  it('跨店鋪商品／房源列表（首頁、/products、/rooms）：各種篩選與排序組合', async () => {
    const cases = [
      { page: 0, size: LIST_PAGE_SIZE },
      { page: 2, size: LIST_PAGE_SIZE, type: 'product' as const, keyword: '咖啡', ...SORT_MAP.priceAsc },
      { page: 0, size: LIST_PAGE_SIZE, type: 'room' as const, ...SORT_MAP.priceDesc },
      { page: 1, size: LIST_PAGE_SIZE, keyword: 'a&b=c', ...SORT_MAP.newest },
    ]
    for (const query of cases) {
      await listingService.getPublicListings(query)
      expect(serverRequest(publicListingsPath(query)), JSON.stringify(query)).toEqual(lastRequest())
    }
  })

  it('單一店鋪的商品／房源列表（含精選）', async () => {
    const cases = [
      { type: 'PRODUCT' as const, page: 0, size: LIST_PAGE_SIZE },
      { type: 'ROOM' as const, page: 3, size: LIST_PAGE_SIZE },
      { featured: true, size: FEATURED_LIMIT },
      { featured: false, page: 0 },
    ]
    for (const query of cases) {
      await storefrontService.getStoreListings('demo-shop', query)
      expect(serverRequest(storeListingsPath('demo-shop', query)), JSON.stringify(query)).toEqual(lastRequest())
    }
  })

  it('店鋪公開資料', async () => {
    await storefrontService.getStoreProfile('demo-shop')
    expect(serverRequest(storeProfilePath('demo-shop'))).toEqual(lastRequest())
  })

  it('跨店鋪貼文列表（/blog）', async () => {
    await getPublicPostFeed({ page: 1, size: LIST_PAGE_SIZE })
    expect(serverRequest(publicPostFeedPath(1, LIST_PAGE_SIZE))).toEqual(lastRequest())
  })

  it('店鋪的貼文列表（含分類篩選）與分類清單', async () => {
    await getPublishedPosts(TENANT_ID, { page: 0, size: LIST_PAGE_SIZE })
    expect(serverRequest(storePostsPath(TENANT_ID, { page: 0, size: LIST_PAGE_SIZE }))).toEqual(lastRequest())

    await getPublishedPosts(TENANT_ID, { page: 2, size: LIST_PAGE_SIZE, categoryId: 'c0ffee00-0000-4000-8000-000000000001' })
    expect(
      serverRequest(storePostsPath(TENANT_ID, { page: 2, size: LIST_PAGE_SIZE, categoryId: 'c0ffee00-0000-4000-8000-000000000001' }))
    ).toEqual(lastRequest())

    await getPublicCategories(TENANT_ID)
    expect(serverRequest(storeCategoriesPath(TENANT_ID))).toEqual(lastRequest())
  })

  it('單篇文章：伺服器端帶 countView=false（伺服器端渲染不是一次瀏覽），瀏覽器端沿用後端預設的計瀏覽', async () => {
    await getPublishedPostBySlug('hello-world', TENANT_ID, { countView: false })
    expect(serverRequest(postBySlugPath('hello-world', TENANT_ID))).toEqual(lastRequest())

    await getPublishedPostBySlug('hello-world', TENANT_ID)
    const browser = lastRequest()
    expect(browser.query.countView, '瀏覽器載入才是一次真正的瀏覽，不能帶 countView=false（否則瀏覽數永遠不會增加）').toBeUndefined()
    expect(serverRequest(postBySlugPath('hello-world', TENANT_ID)).query).toEqual({ ...browser.query, countView: 'false' })
  })

  it('商品／房源詳情與文章內嵌卡片', async () => {
    await listingService.getListingById(LISTING_ID)
    expect(serverRequest(listingPath(LISTING_ID))).toEqual(lastRequest())

    await getListingCard(LISTING_ID)
    expect(serverRequest(listingCardPath(LISTING_ID))).toEqual(lastRequest())
  })
})

describe('路徑組合本身', () => {
  it('來自網址的 slug 一律編碼（空白、斜線、問號不能改變路徑結構）', () => {
    expect(storeProfilePath('a b/c?d')).toBe('/v2/public/stores/a%20b%2Fc%3Fd')
    expect(storeListingsPath('a/b', { page: 0 })).toBe('/v2/public/stores/a%2Fb/listings?page=0')
    expect(postBySlugPath('x/y?z', TENANT_ID)).toBe(`/v2/posts/x%2Fy%3Fz?tenantId=${TENANT_ID}&countView=false`)
  })

  it('關鍵字與分類編號編碼進查詢字串；空關鍵字不送', () => {
    expect(publicListingsPath({ page: 0, size: 12, keyword: '手沖 咖啡&豆' })).toContain('keyword=%E6%89%8B%E6%B2%96+%E5%92%96%E5%95%A1%26%E8%B1%86')
    expect(publicListingsPath({ page: 0, size: 12, keyword: '' })).not.toContain('keyword')
    expect(storePostsPath(TENANT_ID, { page: 0, size: 12, categoryId: 'a&b' })).toContain('categoryId=a%26b')
  })

  it('同一份資料的路徑逐字相同——請求內快取靠它合併 generateMetadata 與頁面本體的重複請求', () => {
    expect(storeProfilePath('demo')).toBe(storeProfilePath('demo'))
    expect(postBySlugPath('p', TENANT_ID)).toBe(postBySlugPath('p', TENANT_ID))
  })
})
