import { renderToString } from 'react-dom/server'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 平台層部落格 /blog（跨店鋪，匿名可讀；Sprint 339，DEF-495，使用者選了『做成跨店鋪部落格』）。
// 之前這一頁只有登入且選定店鋪的人看得到那一間店鋪的貼文（tenantId 取自 localStorage），匿名訪客只看到登入提示；
// 現在列出所有營業中店鋪的已發布貼文。這裡守的是使用者看得到的行為：
//   ① 匿名可看（不依賴登入狀態、不送 tenantId）；卡片帶店鋪名稱，連結指向「那間店鋪」的文章頁；
//   ② 載入失敗是錯誤與重新載入，不能畫成「還沒有文章」（DEF-468 的原則：使用者分不出沒有與壞了）；
//   ③ 分頁走網址 ?page=（可分享、上一頁會回來），而且頁碼從網址決定。

vi.mock('@/services/cms', () => ({ getPublicPostFeed: vi.fn() }))
const push = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ push }) }))

import { getPublicPostFeed } from '@/services/cms'
import { PlatformBlogFeed } from './PlatformBlogFeed'

const card = (over: Record<string, unknown> = {}) => ({
  id: 'p1',
  slug: 'hello',
  title: '第一篇文章',
  excerpt: '摘要文字',
  featuredImageUrl: null,
  publishedAt: '2026-11-01T00:00:00Z',
  tenantSlug: 'shop-a',
  tenantName: '甲店',
  categoryId: null,
  categoryName: null,
  ...over,
})
const feed = (posts: unknown[], extra: Record<string, unknown> = {}) =>
  ({ posts, totalCount: posts.length, page: 0, size: 12, totalPages: 1, ...extra }) as never

const getFeed = vi.mocked(getPublicPostFeed)

beforeEach(() => {
  getFeed.mockReset()
  push.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('平台層部落格：卡片與連結', () => {
  it('匿名就能看：請求只帶頁碼與每頁 12 筆（沒有 tenantId），顯示標題、摘要、店鋪名稱，沒有登入提示', async () => {
    getFeed.mockResolvedValue(feed([card()]))
    render(<PlatformBlogFeed page={0} />)

    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    expect(screen.getByText('摘要文字')).toBeTruthy()
    expect(screen.getByText('甲店')).toBeTruthy()
    expect(screen.queryByText('請先登入並設定店鋪以瀏覽部落格')).toBeNull()
    expect(getFeed).toHaveBeenCalledTimes(1)
    expect(getFeed.mock.calls[0][0]).toEqual({ page: 0, size: 12 })
  })

  it('卡片連到「那間店鋪」的文章頁 /stores/{店鋪}/blog/{文章}（不同店鋪的卡片各連各的）', async () => {
    getFeed.mockResolvedValue(
      feed([
        card({ id: 'p1', slug: 'hello', title: '甲店的文章', tenantSlug: 'shop-a', tenantName: '甲店' }),
        card({ id: 'p2', slug: 'world', title: '乙店的文章', tenantSlug: 'shop-b', tenantName: '乙店' }),
      ])
    )
    render(<PlatformBlogFeed page={0} />)

    const linkA = (await screen.findByText('甲店的文章')).closest('a')
    const linkB = screen.getByText('乙店的文章').closest('a')
    expect(linkA?.getAttribute('href')).toBe('/stores/shop-a/blog/hello')
    expect(linkB?.getAttribute('href')).toBe('/stores/shop-b/blog/world')
  })

  it('有分類的卡片顯示分類名稱，沒有分類的不顯示；有封面圖才有圖片', async () => {
    getFeed.mockResolvedValue(
      feed([
        card({ id: 'p1', title: '有分類有封面', categoryId: 'c1', categoryName: '旅遊筆記', featuredImageUrl: 'https://img.example/a.png' }),
        card({ id: 'p2', slug: 'second', title: '沒分類沒封面' }),
      ])
    )
    render(<PlatformBlogFeed page={0} />)

    const first = (await screen.findByText('有分類有封面')).closest('a') as HTMLElement
    const second = screen.getByText('沒分類沒封面').closest('a') as HTMLElement
    expect(within(first).getByText('旅遊筆記')).toBeTruthy()
    expect(within(first).getByRole('img').getAttribute('alt')).toBe('有分類有封面')
    expect(within(second).queryByText('旅遊筆記')).toBeNull()
    expect(within(second).queryByRole('img')).toBeNull()
  })
})

describe('平台層部落格：空與失敗不能互相冒充', () => {
  it('成功但沒有任何已發布的貼文 → 顯示「目前還沒有已發布的文章」（這才是真的沒有）', async () => {
    getFeed.mockResolvedValue(feed([], { totalCount: 0, totalPages: 0 }))
    render(<PlatformBlogFeed page={0} />)

    expect(await screen.findByText('目前還沒有已發布的文章')).toBeTruthy()
    expect(screen.queryByTestId('blog-error')).toBeNull()
  })

  // 網址 ?page=999 超出範圍：後端回空清單但總數大於 0。這不是「還沒有文章」（有，只是這一頁沒有），畫成那樣會誤導，也沒有出路
  it('網址的頁碼超出範圍（空清單但總數大於 0）→「這一頁沒有文章」＋回到第一頁的連結，不是「目前還沒有已發布的文章」', async () => {
    getFeed.mockResolvedValue(feed([], { page: 8, totalCount: 25, totalPages: 3 }))
    render(<PlatformBlogFeed page={8} />)

    expect(await screen.findByText('這一頁沒有文章')).toBeTruthy()
    expect(screen.getByRole('link', { name: '回到第一頁' }).getAttribute('href')).toBe('/blog')
    expect(screen.queryByText('目前還沒有已發布的文章'), '超出範圍被畫成了「還沒有文章」').toBeNull()
    expect(screen.queryByTestId('blog-error')).toBeNull()
  })

  it('載入失敗 → 顯示錯誤與「重新載入」，不能畫成「目前還沒有已發布的文章」', async () => {
    getFeed.mockRejectedValue(new Error('network down'))
    render(<PlatformBlogFeed page={0} />)

    expect(await screen.findByTestId('blog-error')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
    expect(screen.queryByText('目前還沒有已發布的文章'), '載入失敗被畫成了「沒有文章」').toBeNull()
  })

  it('按「重新載入」會再請求一次，成功後顯示文章', async () => {
    getFeed.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(feed([card()]))
    render(<PlatformBlogFeed page={0} />)

    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('第一篇文章')).toBeTruthy()
    expect(getFeed).toHaveBeenCalledTimes(2)
    expect(screen.queryByTestId('blog-error')).toBeNull()
  })
})

describe('平台層部落格：分頁走網址', () => {
  it('頁碼由網址決定：page 屬性是 0-based，直接當請求的 page', async () => {
    getFeed.mockResolvedValue(feed([card()], { page: 2, totalPages: 5 }))
    render(<PlatformBlogFeed page={2} />)

    await screen.findByText('第一篇文章')
    expect(getFeed.mock.calls[0][0]).toEqual({ page: 2, size: 12 })
  })

  // 分頁是真正的連結（DEF-500）：搜尋引擎與不執行 JavaScript 的爬蟲只會沿著 <a href> 走，按鈕＋router.push 讓它們到不了第 2 頁
  it('總頁數大於 1 才顯示分頁；頁碼是連結 → /blog?page=N（第 1 頁是 /blog，不帶 page）', async () => {
    getFeed.mockResolvedValue(feed([card()], { totalPages: 3 }))
    render(<PlatformBlogFeed page={0} />)

    const nav = await screen.findByRole('navigation', { name: '分頁' })
    expect(within(nav).getByRole('link', { name: '2' }).getAttribute('href')).toBe('/blog?page=2')
    expect(within(nav).getByRole('link', { name: '3' }).getAttribute('href')).toBe('/blog?page=3')
    expect(within(nav).queryAllByRole('button'), '分頁不能是按鈕：爬蟲點不了').toHaveLength(0)
  })

  it('在第 2 頁：「1」連到 /blog（回到第一頁不帶 page）、「上一頁」連到 /blog、「下一頁」連到 /blog?page=3', async () => {
    getFeed.mockResolvedValue(feed([card()], { page: 1, totalPages: 3 }))
    render(<PlatformBlogFeed page={1} />)

    const nav = await screen.findByRole('navigation', { name: '分頁' })
    expect(within(nav).getByRole('link', { name: '1' }).getAttribute('href')).toBe('/blog')
    expect(within(nav).getByRole('link', { name: '上一頁' }).getAttribute('href')).toBe('/blog')
    expect(within(nav).getByRole('link', { name: '下一頁' }).getAttribute('href')).toBe('/blog?page=3')
  })

  it('只有一頁時沒有分頁列', async () => {
    getFeed.mockResolvedValue(feed([card()], { totalPages: 1 }))
    render(<PlatformBlogFeed page={0} />)

    await screen.findByText('第一篇文章')
    await waitFor(() => expect(screen.queryByRole('navigation', { name: '分頁' })).toBeNull())
  })
})

// DEF-500（Sprint 341）：SEO 的伺服器端渲染。server page 先取好這一頁、以 initialFeed 傳進來，第一份 HTML 就有文章卡片——
// 以前是載入中骨架，搜尋引擎與不執行 JavaScript 的爬蟲看不到任何文章。
describe('平台層部落格：伺服器端帶來的第一頁（initialFeed）', () => {
  it('第一個畫面就有文章卡片與連結，而且不再向後端請求一次', async () => {
    render(<PlatformBlogFeed page={0} initialFeed={feed([card({ title: '伺服器端渲染的文章' })])} />)

    const link = screen.getByText('伺服器端渲染的文章').closest('a')
    expect(link?.getAttribute('href'), '同步就能找到卡片＝它在第一份 HTML 裡').toBe('/stores/shop-a/blog/hello')
    expect(screen.queryByRole('status'), '不該再出現「載入中」').toBeNull()
    await waitFor(() => expect(getFeed).not.toHaveBeenCalled())
  })

  it('伺服器端沒取到（不傳 initialFeed）→ 與先前相同：先載入中，再由瀏覽器自己請求', async () => {
    getFeed.mockResolvedValue(feed([card({ title: '瀏覽器載入的文章' })]))
    render(<PlatformBlogFeed page={0} />)

    expect(screen.getByRole('status')).toBeTruthy()
    expect(await screen.findByText('瀏覽器載入的文章')).toBeTruthy()
    expect(getFeed).toHaveBeenCalledTimes(1)
  })

  it('伺服器帶來的是空的第一頁（總數為 0）→ 顯示「還沒有文章」，不是載入中', async () => {
    render(<PlatformBlogFeed page={0} initialFeed={feed([], { totalCount: 0, totalPages: 0 })} />)

    expect(screen.getByText('目前還沒有已發布的文章')).toBeTruthy()
    await waitFor(() => expect(getFeed).not.toHaveBeenCalled())
  })
})

// 日期在伺服器與訪客的瀏覽器各算一次（容器通常是 UTC、訪客在台灣 UTC+8）：沒指定時區的 toLocaleDateString 會在兩邊算出不同的一天，
// React 偵測到文字不符就丟掉伺服器的 HTML 重新渲染（console 出現 hydration 錯誤）。見 lib/date.ts 的 formatPublicDate。
describe('平台層部落格：日期在伺服器與瀏覽器算出同一個字串', () => {
  it('UTC 20:00 發布的文章，不論渲染環境在哪個時區，畫面上都是台灣的隔天（2026/11/2）', () => {
    const results: string[] = []
    for (const timeZone of ['UTC', 'Asia/Taipei', 'America/Los_Angeles']) {
      vi.stubEnv('TZ', timeZone)
      const html = renderToString(
        <PlatformBlogFeed page={0} initialFeed={feed([card({ title: '夜間發布', publishedAt: '2026-11-01T20:00:00Z' })])} />
      )
      results.push(html.match(/2026\/\d+\/\d+/)?.[0] ?? '（找不到日期）')
    }
    vi.unstubAllEnvs()
    expect(results).toEqual(['2026/11/2', '2026/11/2', '2026/11/2'])
  })
})
