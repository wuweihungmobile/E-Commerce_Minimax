import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 店鋪前台貼文列表的公開版分類篩選（Sprint 337，DEF-486）。
// 之前文章頁的分類徽章連到 /blog/category/{id}，那一頁的 tenantId 取自 localStorage（只有登入才有），匿名訪客點了只看到登入提示。
// 現在店鋪由網址決定、tenantId 由公開的店鋪資料提供，分類篩選是列表自己的功能：網址 ?category={分類編號}。
// 這裡守的是：
//   ① 篩選列有「全部」與各分類，選取態正確（aria-pressed，輔助科技與 E2E 都讀它），順序穩定；
//   ② 貼文請求帶不帶 categoryId 取決於網址；換分類回第一頁、翻頁保留分類；
//   ③ 分類對不上（網址亂填、已停用）→ 「此分類不存在或已停用」，而且根本不送出貼文請求；
//   ④ 失敗不冒充：分類載入失敗＋指定了分類 → 錯誤與重新載入（不是「不存在」）；沒指定分類 → 貼文照常、只是沒有篩選列；
//   ⑤ 空狀態分清楚：「此分類尚無…」與「此店鋪目前沒有…」；
//   ⑥ 換分類或翻頁不重新請求分類（篩選列不閃）。

const { push } = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('next/navigation', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/services/cms', () => ({
  getPublishedPosts: vi.fn(),
  getPublicCategories: vi.fn(),
}))

import { getPublicCategories, getPublishedPosts } from '@/services/cms'
import { StoreBlogList, type StoreBlogListProps } from './StoreBlogList'

const getPosts = vi.mocked(getPublishedPosts)
const getCategories = vi.mocked(getPublicCategories)

const post = (id: string, title: string) => ({
  id,
  slug: `slug-${id}`,
  title,
  content: `${title} 的內容`,
  excerpt: '',
  featuredImageUrl: '',
})
const postsPage = (posts: unknown[], totalPages = 1) =>
  ({ posts, totalCount: posts.length, page: 0, size: 12, totalPages }) as never
const category = (id: string, name: string, sortOrder = 0) => ({ id, name, sortOrder, description: '', isActive: true })
const categoriesPage = (categories: unknown[]) => ({ categories, totalCount: categories.length }) as never

const renderList = (props: Partial<StoreBlogListProps> = {}) =>
  render(<StoreBlogList slug="demo" tenantId="tenant-1" page={0} {...props} />)

/** 篩選列上依序出現的按鈕文字 */
const chipLabels = () =>
  Array.from(screen.getByTestId('store-blog-categories').querySelectorAll('button')).map((b) => b.textContent)

beforeEach(() => {
  push.mockReset()
  getPosts.mockReset()
  getCategories.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('篩選列', () => {
  it('沒指定分類：「全部」是選取態，後面是各分類；貼文請求不帶 categoryId', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記'), category('c2', '咖啡手帖')]))
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')]))
    renderList()

    expect(await screen.findByText('第一篇')).toBeTruthy()
    expect(screen.getByRole('button', { name: '全部', pressed: true })).toBeTruthy()
    expect(screen.getByRole('button', { name: '旅遊筆記', pressed: false })).toBeTruthy()
    expect(screen.getByRole('button', { name: '咖啡手帖', pressed: false })).toBeTruthy()
    const [tenantId, params] = getPosts.mock.calls[0]
    expect(tenantId).toBe('tenant-1')
    expect(params).toEqual({ page: 0, size: 12 })
    expect(params, '沒指定分類就不能帶 categoryId（帶了會變成篩選一個空分類）').not.toHaveProperty('categoryId')
  })

  it('指定存在的分類：該分類是選取態、「全部」不是；貼文請求帶 categoryId，顯示的是篩選後的貼文', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記'), category('c2', '咖啡手帖')]))
    getPosts.mockResolvedValue(postsPage([post('p2', '手沖入門')]))
    renderList({ categoryId: 'c2' })

    expect(await screen.findByText('手沖入門')).toBeTruthy()
    expect(screen.getByRole('button', { name: '咖啡手帖', pressed: true })).toBeTruthy()
    expect(screen.getByRole('button', { name: '全部', pressed: false })).toBeTruthy()
    expect(getPosts.mock.calls[0][1]).toEqual({ page: 0, size: 12, categoryId: 'c2' })
  })

  it('順序依店主設定的 sortOrder，相同再依名稱（公開端點不保證順序，畫面順序不能跟著資料庫亂跳）', async () => {
    getCategories.mockResolvedValue(
      categoriesPage([category('c3', 'C 類', 2), category('c2', 'B 類', 1), category('c1', 'A 類', 1)])
    )
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')]))
    renderList()

    await screen.findByText('第一篇')
    expect(chipLabels()).toEqual(['全部', 'A 類', 'B 類', 'C 類'])
  })

  it('店鋪沒有任何啟用中的分類 → 不顯示篩選列（只有「全部」沒有意義），貼文照常', async () => {
    getCategories.mockResolvedValue(categoriesPage([]))
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')]))
    renderList()

    expect(await screen.findByText('第一篇')).toBeTruthy()
    expect(screen.queryByTestId('store-blog-categories')).toBeNull()
  })

  it('點分類 → 網址帶 ?category=（不帶 page，換分類回第一頁）；點「全部」→ 回到不帶任何參數的網址', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記'), category('c2', '咖啡手帖')]))
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')]))
    renderList({ categoryId: 'c1', page: 2 })

    await screen.findByText('第一篇')
    fireEvent.click(screen.getByRole('button', { name: '咖啡手帖' }))
    expect(push).toHaveBeenLastCalledWith('/stores/demo/blog?category=c2')

    fireEvent.click(screen.getByRole('button', { name: '全部' }))
    expect(push).toHaveBeenLastCalledWith('/stores/demo/blog')
  })
})

describe('翻頁', () => {
  // 分頁是真正的連結（DEF-500，Sprint 341）：搜尋引擎與不執行 JavaScript 的爬蟲只會沿著 <a href> 走
  it('篩選中翻頁 → 連結同時帶 category 與 page（1-based）；沒有篩選時只帶 page', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記')]))
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')], 3))
    const first = renderList({ categoryId: 'c1' })
    await screen.findByText('第一篇')

    expect(screen.getByRole('link', { name: '2' }).getAttribute('href')).toBe('/stores/demo/blog?category=c1&page=2')
    first.unmount()

    renderList()
    await screen.findByText('第一篇')
    expect(screen.getByRole('link', { name: '2' }).getAttribute('href')).toBe('/stores/demo/blog?page=2')
    expect(screen.getByRole('link', { name: '3' }).getAttribute('href')).toBe('/stores/demo/blog?page=3')
  })
})

describe('分類對不上', () => {
  it('網址的分類不在公開清單裡（亂填或已停用）→ 「此分類不存在或已停用」＋回到全部的連結；不送出貼文請求；篩選列仍在', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記')]))
    renderList({ categoryId: 'not-a-real-category' })

    const notFound = await screen.findByTestId('store-blog-category-not-found')
    expect(notFound.textContent).toContain('此分類不存在或已停用')
    expect(screen.getByRole('link', { name: '查看全部貼文' }).getAttribute('href')).toBe('/stores/demo/blog')
    expect(screen.getByRole('button', { name: '旅遊筆記' }), '其他分類仍可點').toBeTruthy()
    expect(screen.getByRole('button', { name: '全部', pressed: false }), '沒有任何選取態').toBeTruthy()
    expect(getPosts, '不存在的分類不該送出貼文請求（後端對格式錯誤的編號會回 400，畫面會變成「載入失敗」）').not.toHaveBeenCalled()
  })
})

describe('失敗不冒充', () => {
  it('分類載入失敗＋指定了分類 → 錯誤與「重新載入」，不是「此分類不存在」；重試成功後顯示該分類的貼文', async () => {
    getCategories.mockRejectedValueOnce(new Error('network down'))
    getCategories.mockResolvedValueOnce(categoriesPage([category('c1', '旅遊筆記')]))
    getPosts.mockResolvedValue(postsPage([post('p1', '旅遊第一篇')]))
    renderList({ categoryId: 'c1' })

    expect(await screen.findByTestId('store-blog-category-error')).toBeTruthy()
    expect(screen.queryByTestId('store-blog-category-not-found'), '失敗被畫成了「不存在」').toBeNull()
    expect(getPosts).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '重新載入' }))

    expect(await screen.findByText('旅遊第一篇')).toBeTruthy()
    expect(getCategories).toHaveBeenCalledTimes(2)
    expect(getPosts.mock.calls[0][1]).toEqual({ page: 0, size: 12, categoryId: 'c1' })
  })

  it('分類載入失敗＋沒指定分類 → 貼文照常顯示，只是沒有篩選列（篩選是附加功能，不能拖垮列表）', async () => {
    getCategories.mockRejectedValue(new Error('network down'))
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')]))
    renderList()

    expect(await screen.findByText('第一篇')).toBeTruthy()
    expect(screen.queryByTestId('store-blog-categories')).toBeNull()
    expect(screen.queryByTestId('store-blog-category-error')).toBeNull()
  })

  it('貼文載入失敗 → 「載入失敗，請稍後再試」，不是任何一種空狀態', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記')]))
    getPosts.mockRejectedValue(new Error('network down'))
    renderList({ categoryId: 'c1' })

    expect(await screen.findByText('載入失敗，請稍後再試')).toBeTruthy()
    expect(screen.queryByTestId('store-blog-category-empty')).toBeNull()
    expect(screen.queryByTestId('store-blog-empty')).toBeNull()
  })
})

describe('空狀態', () => {
  it('分類存在但沒有已發布貼文 → 「此分類尚無已發布的貼文」（不是「不存在」，也不是整間店沒有貼文）', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記')]))
    getPosts.mockResolvedValue(postsPage([]))
    renderList({ categoryId: 'c1' })

    const empty = await screen.findByTestId('store-blog-category-empty')
    expect(empty.textContent).toContain('此分類尚無已發布的貼文')
    expect(screen.queryByTestId('store-blog-category-not-found')).toBeNull()
    expect(screen.queryByTestId('store-blog-empty')).toBeNull()
  })

  it('沒指定分類而且整間店都沒有貼文 → 「此店鋪目前沒有已發布的貼文」', async () => {
    getCategories.mockResolvedValue(categoriesPage([]))
    getPosts.mockResolvedValue(postsPage([]))
    renderList()

    const empty = await screen.findByTestId('store-blog-empty')
    expect(empty.textContent).toContain('此店鋪目前沒有已發布的貼文')
    expect(screen.queryByTestId('store-blog-category-empty')).toBeNull()
  })
})

describe('換分類與翻頁的請求', () => {
  it('換分類或翻頁（props 變動）→ 貼文以新的條件重新請求；分類只請求一次，篩選列不會跟著重抓', async () => {
    getCategories.mockResolvedValue(categoriesPage([category('c1', '旅遊筆記'), category('c2', '咖啡手帖')]))
    getPosts.mockResolvedValue(postsPage([post('p1', '第一篇')]))
    const view = renderList({ categoryId: 'c1' })
    await screen.findByText('第一篇')
    expect(getPosts).toHaveBeenCalledTimes(1)

    view.rerender(<StoreBlogList slug="demo" tenantId="tenant-1" page={0} categoryId="c2" />)
    await waitFor(() => expect(getPosts).toHaveBeenCalledTimes(2))
    expect(getPosts.mock.calls[1][1]).toEqual({ page: 0, size: 12, categoryId: 'c2' })

    view.rerender(<StoreBlogList slug="demo" tenantId="tenant-1" page={1} categoryId="c2" />)
    await waitFor(() => expect(getPosts).toHaveBeenCalledTimes(3))
    expect(getPosts.mock.calls[2][1]).toEqual({ page: 1, size: 12, categoryId: 'c2' })

    expect(getCategories, '分類與貼文各自請求：換分類或翻頁不該連分類一起重抓（篩選列會閃）').toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('store-blog-categories'), '篩選列一直都在').toBeTruthy()
  })
})

// DEF-500（Sprint 341）：SEO 的伺服器端渲染。server page 先取好分類與這一頁的貼文，第一份 HTML 就有貼文卡片（以前是載入中骨架）。
describe('伺服器端帶來的初始資料（initialCategories／initialPosts）', () => {
  it('第一個畫面就有篩選列與貼文，而且不再向後端請求分類與貼文', async () => {
    renderList({
      initialCategories: categoriesPage([category('c1', '旅遊筆記')]),
      initialPosts: postsPage([post('p1', '伺服器端渲染的貼文')]),
    })

    const link = screen.getByText('伺服器端渲染的貼文').closest('a')
    expect(link?.getAttribute('href'), '同步就能找到卡片＝它在第一份 HTML 裡').toBe('/stores/demo/blog/slug-p1')
    expect(chipLabels()).toEqual(['全部', '旅遊筆記'])
    await waitFor(() => expect(getPosts).not.toHaveBeenCalled())
    expect(getCategories).not.toHaveBeenCalled()
  })

  it('指定的分類不存在：伺服器端只帶分類清單、沒有貼文 → 直接顯示「此分類不存在或已停用」，不送貼文請求', async () => {
    renderList({ categoryId: 'nope', initialCategories: categoriesPage([category('c1', '旅遊筆記')]) })

    expect(screen.getByTestId('store-blog-category-not-found').textContent).toContain('此分類不存在或已停用')
    await waitFor(() => expect(getPosts).not.toHaveBeenCalled())
  })

  it('伺服器端沒取到（不傳）→ 與先前相同：骨架，再由瀏覽器自己請求', async () => {
    getCategories.mockResolvedValue(categoriesPage([]))
    getPosts.mockResolvedValue(postsPage([post('p1', '瀏覽器載入的貼文')]))
    renderList()

    expect(await screen.findByText('瀏覽器載入的貼文')).toBeTruthy()
    expect(getPosts).toHaveBeenCalledTimes(1)
  })
})
