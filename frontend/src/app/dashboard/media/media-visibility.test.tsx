import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 媒體中心的公開開關（Sprint 336，DEF-482）。
// 背景：媒體庫的檔案網址需要登入，<img> 帶不出憑證，所以店主上傳的圖沒辦法拿去當公開頁的圖。每個素材有一個「公開」開關
// （預設私有）：公開＝任何拿到網址的人都能看，不需要登入。這裡守的是使用者看得到的行為：
//   ① 私有／公開的狀態與「複製公開網址」只在公開時出現；② 開關兩個方向都先確認，取消確認不送請求；
//   ③ 失敗時狀態維持原狀並告知；④ 複製的是「完整網址」（貼到貼文封面圖欄位要能用），剪貼簿不可用時至少把網址交給使用者。

vi.mock('@/services/media', () => ({
  getMediaAssets: vi.fn(),
  getMediaCategories: vi.fn(),
  deleteMediaAsset: vi.fn(),
  createMediaCategory: vi.fn(),
  uploadMediaAssetMultipart: vi.fn(),
  getMediaAssetFileBlob: vi.fn(),
  updateMediaAsset: vi.fn(),
}))
vi.mock('@/components/ui/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/components/ui/confirm-dialog', () => ({ confirmDialog: vi.fn() }))
// 縮圖用帶憑證的元件抓 blob；這裡不測縮圖
vi.mock('@/components/ui/authenticated-image', () => ({ AuthenticatedImage: () => <span data-testid="thumb" /> }))

import { getMediaAssets, getMediaCategories, updateMediaAsset } from '@/services/media'
import { toast } from '@/components/ui/toast'
import { confirmDialog } from '@/components/ui/confirm-dialog'
import MediaPage from './page'

const PRIVATE_ID = '11111111-1111-1111-1111-111111111111'
const PUBLIC_ID = '22222222-2222-2222-2222-222222222222'
const PUBLIC_PATH = `/api/v2/public/media/${PUBLIC_ID}`

const asset = (id: string, fileName: string, extra: Record<string, unknown> = {}) =>
  ({
    id,
    tenantId: 't1',
    categoryId: null,
    categoryName: null,
    fileName,
    filePath: `t1/${fileName}`,
    fileSize: 2048,
    mimeType: 'image/png',
    tags: [],
    usageCount: 0,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    url: `/api/v2/media/files/${id}`,
    isPublic: false,
    ...extra,
  }) as never

const pageOf = (...items: unknown[]) => ({
  content: items,
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: 1,
  first: true,
  last: true,
})

const listMock = vi.mocked(getMediaAssets)
const updateMock = vi.mocked(updateMediaAsset)
const confirmMock = vi.mocked(confirmDialog)

beforeEach(() => {
  vi.mocked(getMediaCategories).mockResolvedValue([])
  listMock.mockReset()
  updateMock.mockReset()
  confirmMock.mockReset()
  vi.mocked(toast.success).mockReset()
  vi.mocked(toast.error).mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
  listMock.mockResolvedValue(
    pageOf(asset(PRIVATE_ID, 'private.png'), asset(PUBLIC_ID, 'public.png', { isPublic: true, publicUrl: PUBLIC_PATH })) as never
  )
})

const switchOf = (id: string) => screen.findByTestId(`media-public-switch-${id}`)

describe('狀態顯示', () => {
  it('私有的素材：標示「私有」、開關是關的、沒有「複製公開網址」；公開的素材相反', async () => {
    render(<MediaPage />)

    expect((await switchOf(PRIVATE_ID)).getAttribute('aria-checked')).toBe('false')
    expect(screen.getByTestId(`media-visibility-${PRIVATE_ID}`).textContent).toBe('私有')
    expect(screen.queryByTestId(`media-copy-url-${PRIVATE_ID}`)).toBeNull()

    expect((await switchOf(PUBLIC_ID)).getAttribute('aria-checked')).toBe('true')
    expect(screen.getByTestId(`media-visibility-${PUBLIC_ID}`).textContent).toBe('公開')
    expect(screen.getByTestId(`media-copy-url-${PUBLIC_ID}`)).toBeTruthy()
  })

  it('開關有可讀的名稱（螢幕閱讀器念得出是哪個檔案的公開設定）', async () => {
    render(<MediaPage />)
    await switchOf(PRIVATE_ID)
    expect(screen.getByRole('switch', { name: '公開 private.png' })).toBeTruthy()
    expect(screen.getByRole('switch', { name: '公開 public.png' })).toBeTruthy()
  })

  it('後端沒有回傳 isPublic（舊的回應）→ 當作私有，不顯示複製按鈕', async () => {
    listMock.mockResolvedValue(pageOf(asset(PRIVATE_ID, 'old.png', { isPublic: undefined })) as never)
    render(<MediaPage />)
    expect((await switchOf(PRIVATE_ID)).getAttribute('aria-checked')).toBe('false')
    expect(screen.queryByTestId(`media-copy-url-${PRIVATE_ID}`)).toBeNull()
  })
})

describe('切換公開', () => {
  it('開啟：先確認（說明「任何人拿到網址都能看」）→ 確認後送出 isPublic:true → 成功訊息並重新載入列表', async () => {
    confirmMock.mockResolvedValue(true)
    updateMock.mockResolvedValue({} as never)
    render(<MediaPage />)

    fireEvent.click(await switchOf(PRIVATE_ID))

    await waitFor(() => expect(updateMock).toHaveBeenCalledWith(PRIVATE_ID, { isPublic: true }))
    const options = confirmMock.mock.calls[0][0] as { message: string; confirmLabel: string }
    expect(options.message).toContain('任何人只要拿到網址都能看到')
    expect(options.confirmLabel).toBe('公開')
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('已公開'))
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2))
  })

  it('關閉（取消公開）：確認訊息說明「已經使用這個網址的頁面會顯示不出圖片」→ 送出 isPublic:false', async () => {
    confirmMock.mockResolvedValue(true)
    updateMock.mockResolvedValue({} as never)
    render(<MediaPage />)

    fireEvent.click(await switchOf(PUBLIC_ID))

    await waitFor(() => expect(updateMock).toHaveBeenCalledWith(PUBLIC_ID, { isPublic: false }))
    const options = confirmMock.mock.calls[0][0] as { message: string; confirmLabel: string }
    expect(options.message).toContain('顯示不出圖片')
    expect(options.confirmLabel).toBe('取消公開')
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('已取消公開'))
  })

  it('在確認視窗按取消 → 不送任何請求、不重新載入、開關維持原狀', async () => {
    confirmMock.mockResolvedValue(false)
    render(<MediaPage />)

    fireEvent.click(await switchOf(PRIVATE_ID))

    await waitFor(() => expect(confirmMock).toHaveBeenCalled())
    expect(updateMock).not.toHaveBeenCalled()
    expect(listMock).toHaveBeenCalledTimes(1)
    expect((await switchOf(PRIVATE_ID)).getAttribute('aria-checked')).toBe('false')
  })

  it('後端失敗 → 顯示失敗訊息、不說「已公開」、不重新載入；開關維持原狀（沒有假裝成功）', async () => {
    confirmMock.mockResolvedValue(true)
    updateMock.mockRejectedValue(new Error('boom'))
    render(<MediaPage />)

    fireEvent.click(await switchOf(PRIVATE_ID))

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('更新公開設定失敗，請稍後再試'))
    expect(toast.success).not.toHaveBeenCalled()
    expect(listMock).toHaveBeenCalledTimes(1)
    expect((await switchOf(PRIVATE_ID)).getAttribute('aria-checked')).toBe('false')
  })
})

describe('複製公開網址', () => {
  const setClipboard = (writeText: (text: string) => Promise<void>) =>
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

  it('複製的是完整網址（有主機，結尾是後端給的路徑），不是只有路徑——貼到貼文的精選圖片欄位才能用', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setClipboard(writeText)
    render(<MediaPage />)

    fireEvent.click(await screen.findByTestId(`media-copy-url-${PUBLIC_ID}`))

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1))
    const copied = writeText.mock.calls[0][0] as string
    expect(copied).toMatch(/^https?:\/\//)
    expect(copied.endsWith(PUBLIC_PATH)).toBe(true)
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('已複製公開網址'))
  })

  it('剪貼簿不可用 → 不假裝成功，把網址放進錯誤訊息讓使用者手動複製', async () => {
    setClipboard(vi.fn().mockRejectedValue(new Error('denied')))
    render(<MediaPage />)

    fireEvent.click(await screen.findByTestId(`media-copy-url-${PUBLIC_ID}`))

    await waitFor(() => expect(toast.error).toHaveBeenCalled())
    expect(vi.mocked(toast.error).mock.calls[0][0]).toContain(PUBLIC_PATH)
    expect(toast.success).not.toHaveBeenCalled()
  })
})
