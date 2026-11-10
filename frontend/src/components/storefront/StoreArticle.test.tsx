import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 店鋪前台的單篇文章（Sprint 336，DEF-483）。
// 之前的文章頁靠 localStorage 的 tenantId 取文章，匿名訪客只看到「請先登入並設定店鋪以瀏覽部落格」；
// 現在店鋪由網址決定、tenantId 由公開的店鋪資料提供，元件只吃 props。這裡守的是：
//   ① 用 props 的 tenantId 與文章代稱取文章，而且是一次「真正的瀏覽」（不帶 countView:false）；
//   ② 店鋪外殼已經有 h1（店名），文章標題是 h2，頁面不能出現第二個 h1；
//   ③ 找不到文章用白話說明、失敗不冒充成「不存在」；返回連結回到這間店鋪的部落格。
// 內嵌商品卡的行為（DEF-473）見 StoreArticle.embedded-card.test.tsx。

vi.mock('@/services/cms', () => ({
  getPublishedPostBySlug: vi.fn(),
  getListingCard: vi.fn(),
}))

import { getPublishedPostBySlug } from '@/services/cms'
import { StoreArticle } from './StoreArticle'

const getPost = vi.mocked(getPublishedPostBySlug)

const post = (extra: Record<string, unknown> = {}) =>
  ({
    id: 'p1',
    tenantId: 'tenant-1',
    tenantName: '山海小店',
    tenantSlug: 'demo',
    slug: 'hand-brew',
    title: '手沖入門：從選豆開始',
    content: '先選豆，再磨豆。',
    excerpt: '',
    featuredImageUrl: '',
    authorName: '小明',
    categoryId: '',
    categoryName: '',
    tags: [],
    viewCount: 7,
    publishedAt: '2026-11-01T00:00:00Z',
    createdAt: '2026-11-01T00:00:00Z',
    ...extra,
  }) as never

const httpError = (status: number) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: { code: 'E-X', message: '後端訊息' } } })

const renderArticle = () => render(<StoreArticle storeSlug="demo" tenantId="tenant-1" postSlug="hand-brew" />)

beforeEach(() => {
  getPost.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('取得文章', () => {
  it('用 props 的文章代稱與 tenantId 取文章（不看瀏覽器的 localStorage），而且是一次真正的瀏覽：不帶 countView:false', async () => {
    getPost.mockResolvedValue(post())
    renderArticle()

    await screen.findByTestId('store-article-title')
    expect(getPost).toHaveBeenCalledTimes(1)
    const [slug, tenantId, options] = getPost.mock.calls[0]
    expect(slug).toBe('hand-brew')
    expect(tenantId).toBe('tenant-1')
    expect(
      (options as { countView?: boolean } | undefined)?.countView,
      '瀏覽器載入文章才是一次瀏覽；若這裡帶了 countView:false，瀏覽數永遠不會增加'
    ).not.toBe(false)
  })

  it('顯示標題、作者、內文與瀏覽數', async () => {
    getPost.mockResolvedValue(post({ tags: ['咖啡', '手沖'] }))
    renderArticle()

    expect((await screen.findByTestId('store-article-title')).textContent).toBe('手沖入門：從選豆開始')
    expect(screen.getByText('小明')).toBeTruthy()
    expect(screen.getByText('先選豆，再磨豆。')).toBeTruthy()
    expect(screen.getByText('7 次瀏覽')).toBeTruthy()
    expect(screen.getByText('#咖啡')).toBeTruthy()
  })

  it('載入中顯示「載入中...」', () => {
    getPost.mockReturnValue(new Promise(() => {}))
    renderArticle()
    expect(screen.getByTestId('store-article-loading').textContent).toContain('載入中')
  })
})

describe('版面', () => {
  it('文章標題是 h2、整個元件沒有 h1——店鋪外殼已經有一個 h1（店名），頁面只能有一個', async () => {
    getPost.mockResolvedValue(post())
    const { container } = renderArticle()

    const title = await screen.findByRole('heading', { level: 2, name: '手沖入門：從選豆開始' })
    expect(title).toBeTruthy()
    expect(container.querySelector('h1')).toBeNull()
  })

  it('「返回部落格」回到這間店鋪的部落格 /stores/{店鋪}/blog，不是平台層級的 /blog（那一頁匿名訪客打不開）', async () => {
    getPost.mockResolvedValue(post())
    renderArticle()

    expect((await screen.findByTestId('store-article-back')).getAttribute('href')).toBe('/stores/demo/blog')
  })

  it('有分類 → 顯示分類連結；沒有分類 → 不顯示', async () => {
    getPost.mockResolvedValueOnce(post({ categoryId: 'c1', categoryName: '教學' }))
    const first = renderArticle()
    expect((await screen.findByTestId('blog-post-category-link')).textContent).toBe('教學')
    first.unmount()

    getPost.mockResolvedValueOnce(post())
    renderArticle()
    await screen.findByTestId('store-article-title')
    expect(screen.queryByTestId('blog-post-category-link')).toBeNull()
  })

  it('分類徽章連到「這間店鋪」貼文列表的分類篩選（DEF-486）——不是 /blog/category/{id}（那一頁要登入，匿名訪客點了只看到登入提示）', async () => {
    getPost.mockResolvedValue(post({ categoryId: 'c1', categoryName: '教學' }))
    renderArticle()

    const href = (await screen.findByTestId('blog-post-category-link')).getAttribute('href')
    expect(href).toBe('/stores/demo/blog?category=c1')
    expect(href).not.toContain('/blog/category/')
  })
})

describe('找不到與失敗', () => {
  it('文章不存在或未發布（後端 404）→ 「找不到文章」＋白話說明（不是英文的 Request failed…），並可返回這間店鋪的部落格', async () => {
    getPost.mockRejectedValue(httpError(404))
    renderArticle()

    const box = await screen.findByTestId('store-article-notfound')
    expect(box.textContent).toContain('找不到文章')
    expect(box.textContent).toContain('此文章不存在或已下架')
    expect(box.textContent).not.toContain('Request failed')
    expect(screen.getByRole('link', { name: '返回部落格' }).getAttribute('href')).toBe('/stores/demo/blog')
  })

  it('其他失敗（500）→ 不能說成「此文章不存在或已下架」（文章可能好好的，只是暫時載不出來）', async () => {
    getPost.mockRejectedValue(httpError(500))
    renderArticle()

    const box = await screen.findByTestId('store-article-notfound')
    expect(box.textContent).not.toContain('此文章不存在或已下架')
    await waitFor(() => expect(box.textContent).toContain('500'))
  })
})
