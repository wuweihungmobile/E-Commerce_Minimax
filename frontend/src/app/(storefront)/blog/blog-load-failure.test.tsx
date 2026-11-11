import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// DEF-468（Sprint 332）：部落格清單頁與分類頁，載入失敗不能被畫成「沒有文章」。
// 使用者分不出「這家店真的沒有發布文章」與「網路或伺服器出錯、文章其實有」——前者不需要做什麼，後者要重試。
// 這裡守的是：失敗時畫面是錯誤＋重新載入，成功但空才是「尚無…」，兩種畫面不會互相冒充。

vi.mock('@/services/cms', () => ({
  getPublishedPosts: vi.fn(),
  getPublicCategories: vi.fn(),
}))
vi.mock('@/services/authStore', () => ({
  subscribeAuth: () => () => {},
  getSelectedTenantIdSnapshot: () => 'tenant-1',
  getAuthServerSnapshot: () => null,
}))
// useRouter：清單頁的分類篩選列（Sprint 338，DEF-491 暫時入口）點分類時導覽；這個檔案守的是載入失敗，不點它
vi.mock('next/navigation', () => ({ useParams: () => ({ id: 'cat-1' }), useRouter: () => ({ push: vi.fn() }) }))

import { getPublicCategories, getPublishedPosts } from '@/services/cms'
import BlogCategoryPage from './category/[id]/page'
import BlogPage from './page'

const post = {
  id: 'p1',
  slug: 'hello',
  title: '第一篇文章',
  content: '內容',
  excerpt: '摘要',
  featuredImageUrl: '',
  authorName: '作者',
  publishedAt: '2026-11-01T00:00:00Z',
  createdAt: '2026-11-01T00:00:00Z',
}
const postsPage = (posts: unknown[]) => ({ posts, totalCount: posts.length, page: 0, size: 12, totalPages: 1 }) as never
const categoriesPage = (categories: unknown[]) => ({ categories, totalCount: categories.length }) as never
const category = { id: 'cat-1', name: '旅遊筆記', description: '' }

const getPosts = vi.mocked(getPublishedPosts)
const getCategories = vi.mocked(getPublicCategories)

beforeEach(() => {
  getPosts.mockReset()
  getCategories.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

/** 等載入結束（畫面不再是「載入中」）；之後的斷言才是在看「落定後」的畫面 */
async function settled() {
  await waitFor(() => expect(screen.queryByRole('status')).toBeNull())
}

describe('部落格清單頁 /blog', () => {
  // 清單頁同時會載入分類（暫時入口的篩選列，DEF-491）：這裡一律回「沒有分類」，讓這組案例只守文章的載入行為
  beforeEach(() => {
    getCategories.mockResolvedValue(categoriesPage([]))
  })

  it('成功且有文章 → 顯示文章', async () => {
    getPosts.mockResolvedValue(postsPage([post]))
    render(<BlogPage />)
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
  })

  it('成功但沒有文章 → 顯示「尚無已發布的文章」（這才是真的沒有）', async () => {
    getPosts.mockResolvedValue(postsPage([]))
    render(<BlogPage />)
    expect(await screen.findByText('尚無已發布的文章')).toBeTruthy()
    expect(screen.queryByTestId('blog-error')).toBeNull()
  })

  it('載入失敗 → 顯示錯誤與「重新載入」，不能畫成「尚無已發布的文章」', async () => {
    getPosts.mockRejectedValue(new Error('network down'))
    render(<BlogPage />)
    await waitFor(() => expect(getPosts).toHaveBeenCalledTimes(1))
    await settled()
    expect(screen.queryByText('尚無已發布的文章'), '載入失敗被畫成了「沒有文章」').toBeNull()
    expect(await screen.findByTestId('blog-error')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
  })

  it('按「重新載入」會再請求一次，成功後顯示文章', async () => {
    getPosts.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(postsPage([post]))
    render(<BlogPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    expect(getPosts).toHaveBeenCalledTimes(2)
    expect(screen.queryByTestId('blog-error')).toBeNull()
  })
})

describe('部落格分類頁 /blog/category/[id]', () => {
  it('分類存在、成功但沒有文章 → 「此分類尚無已發布的文章」', async () => {
    getCategories.mockResolvedValue(categoriesPage([category]))
    getPosts.mockResolvedValue(postsPage([]))
    render(<BlogCategoryPage />)
    expect(await screen.findByTestId('blog-category-empty')).toBeTruthy()
    expect(screen.queryByTestId('blog-category-error')).toBeNull()
  })

  it('成功回應裡沒有這個分類 → 「此分類不存在」（分類真的不存在或已停用）', async () => {
    getCategories.mockResolvedValue(categoriesPage([{ ...category, id: 'other' }]))
    // 頁面讓分類與文章同時載入（分類確定不存在之前文章請求就已送出），所以文章這邊也要有回應
    getPosts.mockResolvedValue(postsPage([]))
    render(<BlogCategoryPage />)
    expect(await screen.findByTestId('blog-category-not-found')).toBeTruthy()
    expect(screen.queryByTestId('blog-category-error')).toBeNull()
  })

  it('查分類失敗 → 顯示錯誤與「重新載入」，不能畫成「此分類不存在」', async () => {
    getCategories.mockRejectedValue(new Error('network down'))
    getPosts.mockResolvedValue(postsPage([post]))
    render(<BlogCategoryPage />)
    await waitFor(() => expect(getCategories).toHaveBeenCalledTimes(1))
    await settled()
    expect(screen.queryByTestId('blog-category-not-found'), '查分類失敗被畫成了「此分類不存在」').toBeNull()
    expect(await screen.findByTestId('blog-category-error')).toBeTruthy()
  })

  it('載入文章失敗 → 顯示錯誤與「重新載入」，不能畫成「此分類尚無已發布的文章」', async () => {
    getCategories.mockResolvedValue(categoriesPage([category]))
    getPosts.mockRejectedValue(new Error('network down'))
    render(<BlogCategoryPage />)
    await waitFor(() => expect(getPosts).toHaveBeenCalledTimes(1))
    await settled()
    expect(screen.queryByTestId('blog-category-empty'), '載入文章失敗被畫成了「尚無文章」').toBeNull()
    expect(await screen.findByTestId('blog-category-error')).toBeTruthy()
  })

  it('查分類失敗後按「重新載入」→ 分類與文章都載入，顯示文章', async () => {
    getCategories.mockRejectedValueOnce(new Error('network down')).mockResolvedValue(categoriesPage([category]))
    getPosts.mockResolvedValue(postsPage([post]))
    render(<BlogCategoryPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    expect(screen.queryByTestId('blog-category-error')).toBeNull()
  })

  it('載入文章失敗後按「重新載入」→ 只重抓文章，成功後顯示文章', async () => {
    getCategories.mockResolvedValue(categoriesPage([category]))
    getPosts.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(postsPage([post]))
    render(<BlogCategoryPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    expect(getCategories).toHaveBeenCalledTimes(1)
    expect(getPosts).toHaveBeenCalledTimes(2)
  })
})
