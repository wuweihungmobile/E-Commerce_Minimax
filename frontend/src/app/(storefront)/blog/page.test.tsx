import { describe, expect, it, vi } from 'vitest'

// 平台層部落格頁的 server page（Sprint 339，DEF-495）：頁碼由網址 ?page=（1-based）決定，解析成 0-based 交給列表元件，
// 做法與 /stores/[slug]/blog 相同（DEF-020 慣例）。壞網址（亂填、負數、陣列）一律當第一頁，不能讓頁面壞掉或發出怪請求。

vi.mock('@/components/storefront/PlatformBlogFeed', () => ({ PlatformBlogFeed: () => null }))

import BlogPage from './page'

/** server page 是 async 函式、回傳一個 React element；element.props.page 就是它交給列表的 0-based 頁碼 */
async function pageFor(search: Record<string, string | string[] | undefined>) {
  const element = (await BlogPage({ searchParams: Promise.resolve(search) })) as { props: { page: number } }
  return element.props.page
}

describe('/blog 的頁碼解析', () => {
  it('沒有 page → 第一頁（0）', async () => {
    expect(await pageFor({})).toBe(0)
  })

  it('?page=N 是 1-based：?page=1 → 0、?page=3 → 2', async () => {
    expect(await pageFor({ page: '1' })).toBe(0)
    expect(await pageFor({ page: '3' })).toBe(2)
  })

  it('壞值一律當第一頁：0、負數、非數字、空字串、重複的參數（陣列）', async () => {
    for (const bad of ['0', '-4', 'abc', '', ['2', '3']] as const) {
      expect(await pageFor({ page: bad as string | string[] }), JSON.stringify(bad)).toBe(0)
    }
  })
})
