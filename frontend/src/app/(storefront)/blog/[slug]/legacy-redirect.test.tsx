import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 舊的文章網址 /blog/{slug} 的導向（Sprint 336，DEF-483）：文章的網址改成 /stores/{店鋪}/blog/{slug}，
// 舊網址只負責把「改版前打得開它的人」（登入且選定店鋪的人）導向新網址，書籤與連結不變成死連結。
// 改版前就打不開的人（匿名訪客）維持原本的登入提示——不能因為導向機制而多出新的行為。

const state = { tenantId: 'tenant-1' as string | null }
const replace = vi.fn()

vi.mock('@/services/cms', () => ({ getPublishedPostBySlug: vi.fn() }))
vi.mock('@/services/authStore', () => ({
  subscribeAuth: () => () => {},
  getSelectedTenantIdSnapshot: () => state.tenantId,
  getAuthServerSnapshot: () => null,
}))
vi.mock('next/navigation', () => ({
  useParams: () => ({ slug: 'hand-brew' }),
  useRouter: () => ({ replace }),
}))

import { getPublishedPostBySlug } from '@/services/cms'
import LegacyBlogPostRedirectPage from './page'

const getPost = vi.mocked(getPublishedPostBySlug)
const httpError = (status: number) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: { code: 'E-X' } } })

beforeEach(() => {
  state.tenantId = 'tenant-1'
  getPost.mockReset()
  replace.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('舊網址 /blog/{slug}', () => {
  it('登入且選定店鋪 → 找出文章所屬店鋪，導向 /stores/{店鋪}/blog/{slug}（用 replace，上一頁不會回到舊網址）', async () => {
    getPost.mockResolvedValue({ slug: 'hand-brew', tenantSlug: 'demo' } as never)
    render(<LegacyBlogPostRedirectPage />)

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/stores/demo/blog/hand-brew'))
  })

  it('為了找店鋪而查的這一次不計瀏覽（countView:false）——真正的瀏覽是導向後的那一頁，不能算兩次', async () => {
    getPost.mockResolvedValue({ slug: 'hand-brew', tenantSlug: 'demo' } as never)
    render(<LegacyBlogPostRedirectPage />)

    await waitFor(() => expect(getPost).toHaveBeenCalled())
    const [slug, tenantId, options] = getPost.mock.calls[0]
    expect([slug, tenantId]).toEqual(['hand-brew', 'tenant-1'])
    expect((options as { countView?: boolean }).countView).toBe(false)
  })

  it('沒有選用店鋪的訪客 → 原本的登入提示，不發請求、不導向', async () => {
    state.tenantId = null
    render(<LegacyBlogPostRedirectPage />)

    expect(await screen.findByText('請先登入並設定店鋪以瀏覽部落格')).toBeTruthy()
    expect(getPost).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
  })

  it('文章不存在（404）→ 「找不到文章」＋白話說明，可返回 /blog；不導向', async () => {
    getPost.mockRejectedValue(httpError(404))
    render(<LegacyBlogPostRedirectPage />)

    expect(await screen.findByText('此文章不存在或已下架')).toBeTruthy()
    expect(screen.getByRole('link', { name: '返回部落格' }).getAttribute('href')).toBe('/blog')
    expect(replace).not.toHaveBeenCalled()
  })

  it('找到文章但回應沒有店鋪代稱（後端比前端舊）→ 明講，不卡在「載入中」，也不導向自己', async () => {
    getPost.mockResolvedValue({ slug: 'hand-brew' } as never)
    render(<LegacyBlogPostRedirectPage />)

    expect(await screen.findByText('無法開啟這篇文章（缺少店鋪資訊）')).toBeTruthy()
    expect(replace).not.toHaveBeenCalled()
  })
})
