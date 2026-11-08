import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// DEF-473（Sprint 333）：文章內嵌商品卡，「載入失敗」不能被說成「此商品已下架」。
// PRD §6.6.3 只規定一種情形顯示「此商品已下架」：listing_id 不存在（已刪除或格式有誤）——後端對這兩種情形分別回 404（E-3000）與 400（E-9000）。
// 其他任何失敗（網路中斷、5xx、429…）是「卡片暫時載不出來」，商品可能好好的：使用者看到「已下架」會以為店家把商品拿掉了，
// 也沒有重試的辦法。這裡守的是：兩種畫面不互相冒充，失敗的那一張可以單獨重試，且不影響同一篇文章的其他卡。

vi.mock('@/services/cms', () => ({
  getPublishedPostBySlug: vi.fn(),
  getListingCard: vi.fn(),
}))
vi.mock('@/services/authStore', () => ({
  subscribeAuth: () => () => {},
  getSelectedTenantIdSnapshot: () => 'tenant-1',
  getAuthServerSnapshot: () => null,
}))
vi.mock('next/navigation', () => ({ useParams: () => ({ slug: 'hello' }) }))

import { getListingCard, getPublishedPostBySlug } from '@/services/cms'
import BlogPostPage from './page'

const ID_A = '11111111-1111-1111-1111-111111111111'
const ID_B = '22222222-2222-2222-2222-222222222222'

const post = (content: string) =>
  ({
    id: 'p1',
    slug: 'hello',
    title: '一篇有商品卡的文章',
    content,
    excerpt: '',
    featuredImageUrl: '',
    authorName: '作者',
    categoryId: '',
    categoryName: '',
    tags: [],
    viewCount: 3,
    publishedAt: '2026-11-01T00:00:00Z',
    createdAt: '2026-11-01T00:00:00Z',
  }) as never

const card = (listingId: string, title: string, statusReason: string | null = null) =>
  ({
    listingId,
    listingType: 'PRODUCT',
    title,
    coverImageUrl: '',
    basePrice: 100,
    currentPrice: 100,
    currency: 'TWD',
    availability: { inStock: true, availableQty: 5, available: true },
    tenantName: '好店',
    ctaUrl: `/listings/${listingId}`,
    isActive: true,
    statusReason,
  }) as never

/** 長得像 axios 的 HTTP 失敗：useAsyncData 靠 `response.status` 取得狀態碼 */
const httpError = (status: number) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: { code: 'E-X', message: '後端訊息' } } })

const getPost = vi.mocked(getPublishedPostBySlug)
const getCard = vi.mocked(getListingCard)

beforeEach(() => {
  getPost.mockReset()
  getCard.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

/** 等卡片的載入骨架（animate-pulse）消失，之後的斷言才是在看「落定後」的畫面 */
async function cardSettled(container: HTMLElement) {
  await waitFor(() => expect(getCard).toHaveBeenCalled())
  await waitFor(() => expect(container.querySelector('.animate-pulse')).toBeNull())
}

describe('文章內嵌商品卡 EmbeddedListingCard', () => {
  it('成功 → 顯示商品名、店名與價格', async () => {
    getPost.mockResolvedValue(post(`前言 {{embed:listing:${ID_A}}} 結語`))
    getCard.mockResolvedValue(card(ID_A, '手沖咖啡豆'))
    render(<BlogPostPage />)
    expect(await screen.findByText('手沖咖啡豆')).toBeTruthy()
    expect(screen.getByText('好店')).toBeTruthy()
    expect(screen.queryByTestId('embedded-card-error')).toBeNull()
  })

  it('商品不存在（後端 404）→ 「此商品已下架」', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockRejectedValue(httpError(404))
    const { container } = render(<BlogPostPage />)
    await cardSettled(container)
    expect(await screen.findByText('此商品已下架')).toBeTruthy()
    expect(screen.queryByTestId('embedded-card-error')).toBeNull()
  })

  it('id 格式有誤（後端 400）→ 「此商品已下架」（PRD §6.6.3：已刪除或格式有誤）', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockRejectedValue(httpError(400))
    const { container } = render(<BlogPostPage />)
    await cardSettled(container)
    expect(await screen.findByText('此商品已下架')).toBeTruthy()
    expect(screen.queryByTestId('embedded-card-error')).toBeNull()
  })

  it.each([500, 403, 429])('後端回 %i（不是「商品不存在」）→ 顯示載入失敗與「重新載入」，不能說成「此商品已下架」', async (status) => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockRejectedValue(httpError(status))
    const { container } = render(<BlogPostPage />)
    await cardSettled(container)
    expect(screen.queryByText('此商品已下架'), `HTTP ${status} 被畫成了「此商品已下架」`).toBeNull()
    expect(await screen.findByTestId('embedded-card-error')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
  })

  it('沒有收到回應（網路中斷，沒有 HTTP 狀態）→ 顯示載入失敗與「重新載入」，不能說成「此商品已下架」', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockRejectedValue(new Error('Network Error'))
    const { container } = render(<BlogPostPage />)
    await cardSettled(container)
    expect(screen.queryByText('此商品已下架'), '網路中斷被畫成了「此商品已下架」').toBeNull()
    expect(await screen.findByTestId('embedded-card-error')).toBeTruthy()
  })

  it('按「重新載入」→ 只重抓這一張，成功後顯示商品卡、錯誤消失', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockRejectedValueOnce(httpError(500)).mockResolvedValueOnce(card(ID_A, '手沖咖啡豆'))
    render(<BlogPostPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('手沖咖啡豆')).toBeTruthy()
    expect(getCard).toHaveBeenCalledTimes(2)
    expect(screen.queryByTestId('embedded-card-error')).toBeNull()
  })

  it('先失敗、重試時商品已被刪除（404）→ 這時才顯示「此商品已下架」（每一輪依自己的結果判斷）', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockRejectedValueOnce(httpError(500)).mockRejectedValueOnce(httpError(404))
    render(<BlogPostPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('此商品已下架')).toBeTruthy()
    expect(screen.queryByTestId('embedded-card-error')).toBeNull()
  })

  it('同一篇文章兩張卡：一張商品不存在（404）、一張成功 → 各自顯示，互不影響', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}} 中間文字 {{embed:listing:${ID_B}}}`))
    getCard.mockImplementation(async (id: string) => {
      if (id === ID_A) throw httpError(404)
      return card(ID_B, '另一個好東西')
    })
    render(<BlogPostPage />)
    expect(await screen.findByText('另一個好東西')).toBeTruthy()
    expect(screen.getByText('此商品已下架')).toBeTruthy()
  })

  it('成功回應但店鋪停權（statusReason=listing_unavailable）→ 仍是商品卡＋「前往店鋪」（這是成功的回應，不是失敗）', async () => {
    getPost.mockResolvedValue(post(`{{embed:listing:${ID_A}}}`))
    getCard.mockResolvedValue(card(ID_A, '停權店的商品', 'listing_unavailable'))
    render(<BlogPostPage />)
    expect(await screen.findByText('停權店的商品')).toBeTruthy()
    expect(screen.getByRole('link', { name: '前往店鋪' })).toBeTruthy()
    expect(screen.queryByTestId('embedded-card-error')).toBeNull()
  })
})
