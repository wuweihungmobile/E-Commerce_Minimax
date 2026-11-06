import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '@/lib/axios'
import { getListingCard, getPublicCategories, getPublishedPostBySlug, getPublishedPosts } from './cms'

vi.mock('@/lib/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

// Sprint 331（DEF-456）：公開部落格的四個讀取端點多了 { signal } 參數，讓 useAsyncData 在條件改變／離開頁面時能真的取消請求。
// 這裡守的是「線上格式沒變」（網址、query）與「signal 有原樣交給 axios」——後者漏了的話，請求不會被取消，
// 只是回應被 hook 丟棄（行為仍正確，但浪費一個請求，而且沒有任何東西會報錯）。
const http = vi.mocked(apiClient)
const ok = <T>(data: T) => ({ data: { success: true, data } })

beforeEach(() => {
  vi.resetAllMocks()
})

describe('cms 公開端點', () => {
  it('getPublishedPosts：tenantId、分頁、分類篩選都放在 query；signal 原樣轉給 axios', async () => {
    http.get.mockResolvedValue(ok({ posts: [], totalPages: 0 }))
    const controller = new AbortController()
    await getPublishedPosts('t1', { page: 2, size: 12, categoryId: 'c1' }, { signal: controller.signal })
    expect(http.get).toHaveBeenCalledWith('/v2/posts', {
      params: { tenantId: 't1', page: 2, size: 12, categoryId: 'c1' },
      signal: controller.signal,
    })
  })

  it('getPublishedPosts：不傳選項時只有 params（沒有 signal 這個鍵）', async () => {
    http.get.mockResolvedValue(ok({ posts: [] }))
    await getPublishedPosts('t1')
    expect(http.get.mock.calls[0][1]).toEqual({ params: { tenantId: 't1' } })
  })

  it('getPublicCategories：只帶 tenantId；signal 原樣轉給 axios', async () => {
    http.get.mockResolvedValue(ok({ categories: [] }))
    const controller = new AbortController()
    await getPublicCategories('t1', { signal: controller.signal })
    expect(http.get).toHaveBeenCalledWith('/v2/posts/categories', { params: { tenantId: 't1' }, signal: controller.signal })
  })

  it('getPublishedPostBySlug：slug 在路徑、tenantId 在 query；回傳 data.data', async () => {
    http.get.mockResolvedValue(ok({ id: 'p1', slug: 'hello' }))
    const controller = new AbortController()
    await expect(getPublishedPostBySlug('hello', 't1', { signal: controller.signal })).resolves.toEqual({
      id: 'p1',
      slug: 'hello',
    })
    expect(http.get).toHaveBeenCalledWith('/v2/posts/hello', { params: { tenantId: 't1' }, signal: controller.signal })
  })

  it('getListingCard：沒有 query；signal 原樣轉給 axios', async () => {
    http.get.mockResolvedValue(ok({ listingId: 'l1' }))
    const controller = new AbortController()
    await getListingCard('l1', { signal: controller.signal })
    expect(http.get).toHaveBeenCalledWith('/v2/listings/l1/card', { signal: controller.signal })
  })
})
