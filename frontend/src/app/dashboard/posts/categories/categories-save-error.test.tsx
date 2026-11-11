import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 貼文分類的儲存失敗訊息（Sprint 338，DEF-492）：同一家店不可有同名分類（忽略大小寫與前後空白），後端回 409 `E-8021`。
// 之前不論什麼原因失敗，畫面都只說「儲存分類失敗，請稍後再試」——店主以為是暫時性問題而一再重試，其實要換名稱才會成功。
// 這裡守的是：同名 → 明確告知是名稱的問題；其他失敗（網路、伺服器）仍是原本的「請稍後再試」，兩種訊息不互相冒充。

vi.mock('@/services/cms', () => ({
  getCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}))
vi.mock('@/components/ui/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/components/ui/confirm-dialog', () => ({ confirmDialog: vi.fn() }))

import { createCategory, getCategories, updateCategory } from '@/services/cms'
import { toast } from '@/components/ui/toast'
import CategoriesPage from './page'

const DUPLICATE_NAME_MESSAGE = '分類名稱已存在，請換一個名稱'
const GENERIC_MESSAGE = '儲存分類失敗，請稍後再試'

/** axios 錯誤的形狀：getApiErrorInfo 從 response 取 HTTP 狀態與後端的錯誤碼 */
const apiError = (status: number, code?: string) => ({
  response: { status, data: { ...(code ? { code } : {}), message: '後端寫的訊息（畫面不該直接照抄）' } },
})

const existing = { id: 'c1', name: '旅遊', description: '', sortOrder: 0, isActive: true }

beforeEach(() => {
  vi.mocked(getCategories).mockReset()
  vi.mocked(createCategory).mockReset()
  vi.mocked(updateCategory).mockReset()
  vi.mocked(toast.success).mockReset()
  vi.mocked(toast.error).mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.mocked(getCategories).mockResolvedValue({ categories: [existing], totalCount: 1 } as never)
})

/** 開「新增分類」表單、填名稱、按儲存 */
async function submitNewCategory(name: string) {
  fireEvent.click(await screen.findByRole('button', { name: '新增分類' }))
  fireEvent.change(await screen.findByPlaceholderText('分類名稱'), { target: { value: name } })
  fireEvent.click(screen.getByRole('button', { name: '儲存' }))
}

describe('貼文分類管理：儲存失敗的訊息（DEF-492）', () => {
  it('建立時同名（409 E-8021）→ 告知名稱已存在；表單留著、已填的名稱還在，讓店主直接改', async () => {
    vi.mocked(createCategory).mockRejectedValue(apiError(409, 'E-8021'))
    render(<CategoriesPage />)
    await submitNewCategory('旅遊')
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(DUPLICATE_NAME_MESSAGE))
    expect(toast.error).not.toHaveBeenCalledWith(GENERIC_MESSAGE)
    expect((screen.getByPlaceholderText('分類名稱') as HTMLInputElement).value).toBe('旅遊')
  })

  it('編輯時改成別人的名稱（409 E-8021）→ 同樣告知名稱已存在', async () => {
    vi.mocked(updateCategory).mockRejectedValue(apiError(409, 'E-8021'))
    render(<CategoriesPage />)
    fireEvent.click(await screen.findByRole('button', { name: '編輯' }))
    fireEvent.change(await screen.findByPlaceholderText('分類名稱'), { target: { value: '美食' } })
    fireEvent.click(screen.getByRole('button', { name: '儲存' }))
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(DUPLICATE_NAME_MESSAGE))
    expect(toast.error).not.toHaveBeenCalledWith(GENERIC_MESSAGE)
  })

  it('其他失敗（伺服器 500、沒有錯誤碼的網路錯誤）→ 仍是「請稍後再試」，不冒充成名稱問題', async () => {
    vi.mocked(createCategory).mockRejectedValueOnce(apiError(500)).mockRejectedValueOnce(new Error('network down'))
    render(<CategoriesPage />)
    await submitNewCategory('美食')
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(GENERIC_MESSAGE))
    fireEvent.click(screen.getByRole('button', { name: '儲存' }))
    await waitFor(() => expect(toast.error).toHaveBeenCalledTimes(2))
    expect(vi.mocked(toast.error).mock.calls.map(([message]) => message)).toEqual([GENERIC_MESSAGE, GENERIC_MESSAGE])
  })

  it('別的 409（例如其他錯誤碼）也不冒充成「名稱已存在」：只有 E-8021 才是名稱問題', async () => {
    vi.mocked(createCategory).mockRejectedValue(apiError(409, 'E-8014'))
    render(<CategoriesPage />)
    await submitNewCategory('美食')
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(GENERIC_MESSAGE))
    expect(toast.error).not.toHaveBeenCalledWith(DUPLICATE_NAME_MESSAGE)
  })

  it('成功建立 → 「分類已建立」並關閉表單（成功路徑沒被動到）', async () => {
    vi.mocked(createCategory).mockResolvedValue({ ...existing, id: 'c2', name: '美食' } as never)
    render(<CategoriesPage />)
    await submitNewCategory('美食')
    await waitFor(() => expect(toast.success).toHaveBeenCalledWith('分類已建立'))
    expect(toast.error).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.queryByPlaceholderText('分類名稱')).toBeNull())
  })
})
