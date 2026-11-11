import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 暫時入口（Sprint 338，DEF-491）：平台層的分類頁 /blog/category/[id] 原本沒有任何連結指向它
// （文章頁的分類徽章在 Sprint 337 改連店鋪前台的公開篩選）。/blog 清單頁上方的分類篩選列是它的入口。
// 分類只是附加功能：沒有分類、或分類載入失敗，就不顯示篩選列，文章照常顯示（不為它另畫錯誤畫面）。
// 正式的入口（跨店鋪部落格）或移除兩頁待 DEF-495 決定，到時這個測試與篩選列一併處理。

vi.mock('@/services/cms', () => ({
  getPublishedPosts: vi.fn(),
  getPublicCategories: vi.fn(),
}))
vi.mock('@/services/authStore', () => ({
  subscribeAuth: () => () => {},
  getSelectedTenantIdSnapshot: () => 'tenant-1',
  getAuthServerSnapshot: () => null,
}))
const push = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ push }) }))

import { getPublicCategories, getPublishedPosts } from '@/services/cms'
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
const category = (id: string, name: string, sortOrder = 0) => ({ id, name, sortOrder, description: '' })

const getPosts = vi.mocked(getPublishedPosts)
const getCategories = vi.mocked(getPublicCategories)

beforeEach(() => {
  getPosts.mockReset()
  getCategories.mockReset()
  push.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('平台層 /blog 的分類篩選列（分類頁的暫時入口，DEF-491）', () => {
  it('有分類 → 顯示「全部」與各分類；「全部」是目前的選取態', async () => {
    getPosts.mockResolvedValue(postsPage([post]))
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記')]))
    render(<BlogPage />)
    const bar = await screen.findByTestId('blog-categories')
    expect(within(bar).getByRole('button', { name: '全部' }).getAttribute('aria-pressed')).toBe('true')
    expect(within(bar).getByRole('button', { name: '旅遊筆記' }).getAttribute('aria-pressed')).toBe('false')
  })

  it('點分類 → 導向平台層分類頁 /blog/category/{分類編號}', async () => {
    getPosts.mockResolvedValue(postsPage([post]))
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記'), category('c2', '咖啡手帖')]))
    render(<BlogPage />)
    fireEvent.click(await screen.findByRole('button', { name: '咖啡手帖' }))
    expect(push).toHaveBeenCalledTimes(1)
    expect(push).toHaveBeenCalledWith('/blog/category/c2')
  })

  it('分類依店主設定的順序（sortOrder）、相同再依名稱排列，不隨後端回傳順序變動', async () => {
    getPosts.mockResolvedValue(postsPage([post]))
    // 名稱用 ASCII：中文的排序依語系（zh-Hant 是筆畫序，「乙」會排在「甲」前面），測試不該綁定語系；這裡要守的是「先 sortOrder、再名稱」
    getCategories.mockResolvedValue(
      categoriesPage([category('c3', 'C', 2), category('c1', 'B', 1), category('c2', 'A', 1)])
    )
    render(<BlogPage />)
    const bar = await screen.findByTestId('blog-categories')
    const labels = within(bar)
      .getAllByRole('button')
      .map((button) => button.textContent)
    expect(labels).toEqual(['全部', 'A', 'B', 'C'])
  })

  it('沒有任何分類 → 不顯示篩選列，文章照常顯示', async () => {
    getPosts.mockResolvedValue(postsPage([post]))
    getCategories.mockResolvedValue(categoriesPage([]))
    render(<BlogPage />)
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    await waitFor(() => expect(getCategories).toHaveBeenCalledTimes(1))
    expect(screen.queryByTestId('blog-categories')).toBeNull()
  })

  it('分類載入失敗 → 不顯示篩選列，也不另外畫錯誤畫面；文章照常顯示（分類是附加功能）', async () => {
    getPosts.mockResolvedValue(postsPage([post]))
    getCategories.mockRejectedValue(new Error('network down'))
    render(<BlogPage />)
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    await waitFor(() => expect(getCategories).toHaveBeenCalledTimes(1))
    expect(screen.queryByTestId('blog-categories')).toBeNull()
    expect(screen.queryByTestId('blog-error'), '分類失敗不該讓整頁顯示成載入文章失敗').toBeNull()
  })
})
