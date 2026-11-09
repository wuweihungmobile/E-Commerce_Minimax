import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// DEF-471（Sprint 333）：訊息中心（/dashboard/chat）的對話清單，「載入中」與「載入失敗」都不能被畫成「尚無對話」。
// 使用者分不出「我真的沒有對話」與「網路出錯、對話其實有」——前者不需要做什麼，後者要重試。
// 清單載入之後由 STOMP 收訊與各動作就地更新（setData），這裡守的只有「首次載入」的三種畫面：載入中、失敗、成功（有／沒有對話）。

vi.mock('@/services/chat', () => ({
  getConversations: vi.fn(),
  createConversation: vi.fn(),
  deleteConversation: vi.fn(),
  getMessages: vi.fn(),
  markConversationRead: vi.fn(),
  sendMessage: vi.fn(),
}))
vi.mock('@/services/authStore', () => ({
  subscribeAuth: () => () => {},
  getAuthUserIdSnapshot: () => 'user-1',
  getAuthServerSnapshot: () => null,
}))
// 即時收訊（STOMP）與本輪要守的載入失敗無關；真的連線會打開 WebSocket
vi.mock('@/hooks/useChatSocket', () => ({ useChatSocket: () => 'idle' }))
// 訊息串用到 scrollIntoView（jsdom 沒有）；這裡只看左側的對話清單
vi.mock('@/components/chat/MessageThread', () => ({ default: () => <div data-testid="message-thread-stub" /> }))
vi.mock('@/components/chat/MessageComposer', () => ({ default: () => <div data-testid="message-composer-stub" /> }))
// 刪除對話要先經過確認視窗；這裡直接回「確認」（視窗本身由 ConfirmDialogHost 負責，這個頁面沒有掛）
vi.mock('@/components/ui/confirm-dialog', () => ({ confirmDialog: vi.fn().mockResolvedValue(true) }))

import { createConversation, deleteConversation, getConversations, getMessages, markConversationRead } from '@/services/chat'
import { toast } from '@/components/ui/toast'
import ChatPage from './page'

const conversation = (conversationId: string, initiatorName: string) => ({
  conversationId,
  listingId: null,
  orderId: null,
  conversationType: 'DIRECT',
  initiatorId: 'user-2',
  initiatorName,
  recipientId: 'user-1',
  recipientName: '我',
  lastMessage: null,
  unreadCount: 0,
  isActive: true,
  lastMessageAt: null,
  createdAt: '2026-11-01T00:00:00Z',
})
const conversationsPage = (conversations: unknown[]) =>
  ({ conversations, page: 0, size: 50, totalElements: conversations.length, totalPages: 1, unreadCount: 0 }) as never

const getConvs = vi.mocked(getConversations)

beforeEach(() => {
  getConvs.mockReset()
  vi.mocked(createConversation).mockReset()
  vi.mocked(deleteConversation).mockReset()
  vi.mocked(getMessages).mockReset()
  vi.mocked(markConversationRead).mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

/** 等載入結束（畫面不再是「載入中」）；之後的斷言才是在看「落定後」的畫面 */
async function settled() {
  await waitFor(() => expect(screen.queryByRole('status')).toBeNull())
}

describe('訊息中心 /dashboard/chat 的對話清單', () => {
  it('成功且有對話 → 顯示對話清單', async () => {
    getConvs.mockResolvedValue(conversationsPage([conversation('c1', '小明')]))
    render(<ChatPage />)
    expect(await screen.findByTestId('conversation-item-c1')).toBeTruthy()
    expect(screen.queryByTestId('conversation-error')).toBeNull()
    expect(screen.queryByTestId('conversation-empty')).toBeNull()
  })

  it('成功但沒有對話 → 顯示「尚無對話」（這才是真的沒有）', async () => {
    getConvs.mockResolvedValue(conversationsPage([]))
    render(<ChatPage />)
    expect(await screen.findByTestId('conversation-empty')).toBeTruthy()
    expect(screen.queryByTestId('conversation-error')).toBeNull()
  })

  it('載入中 → 顯示「載入中」，不能先畫成「尚無對話」', async () => {
    getConvs.mockReturnValue(new Promise(() => {})) // 永遠沒有回應
    render(<ChatPage />)
    await waitFor(() => expect(getConvs).toHaveBeenCalledTimes(1))
    expect(screen.queryByTestId('conversation-empty'), '載入中被畫成了「尚無對話」').toBeNull()
    expect(screen.getByRole('status')).toBeTruthy()
  })

  it('載入失敗 → 顯示錯誤與「重新載入」，不能畫成「尚無對話」', async () => {
    getConvs.mockRejectedValue(new Error('network down'))
    render(<ChatPage />)
    await waitFor(() => expect(getConvs).toHaveBeenCalledTimes(1))
    await settled()
    expect(screen.queryByTestId('conversation-empty'), '載入失敗被畫成了「尚無對話」').toBeNull()
    expect(await screen.findByTestId('conversation-error')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
  })

  it('按「重新載入」會再請求一次，成功後顯示對話、錯誤消失', async () => {
    getConvs.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(conversationsPage([conversation('c1', '小明')]))
    render(<ChatPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByTestId('conversation-item-c1')).toBeTruthy()
    expect(getConvs).toHaveBeenCalledTimes(2)
    expect(screen.queryByTestId('conversation-error')).toBeNull()
  })

  it('重新載入又失敗 → 繼續顯示錯誤與「重新載入」，可以再試一次', async () => {
    getConvs.mockRejectedValue(new Error('network down'))
    render(<ChatPage />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    await waitFor(() => expect(getConvs).toHaveBeenCalledTimes(2))
    expect(await screen.findByTestId('conversation-error')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
  })

  it('首次載入失敗後，使用者自己發起對話成功 → 清單顯示新對話，不被舊的錯誤蓋住', async () => {
    // 實際情境：網路瞬斷讓首次載入失敗，使用者沒按「重新載入」，直接用上方的「發起對話」；建立後頁面會重抓清單並開啟該對話
    getConvs.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(conversationsPage([conversation('c9', '新朋友')]))
    vi.mocked(createConversation).mockResolvedValue({ conversationId: 'c9' } as never)
    vi.mocked(getMessages).mockResolvedValue({ messages: [], page: 0, size: 50, totalElements: 0, totalPages: 0 } as never)
    vi.mocked(markConversationRead).mockResolvedValue(undefined as never)
    render(<ChatPage />)
    await screen.findByTestId('conversation-error')

    fireEvent.click(screen.getByTestId('new-conversation-button'))
    fireEvent.change(screen.getByTestId('new-recipient-input'), { target: { value: 'user-9' } })
    fireEvent.click(screen.getByTestId('create-conversation-submit'))

    expect(await screen.findByTestId('conversation-item-c9')).toBeTruthy()
    expect(screen.queryByTestId('conversation-error'), '清單已載入卻仍顯示舊的載入錯誤').toBeNull()
  })
})

// DEF-477（Sprint 335）：建立／刪除對話成功之後，頁面會靜默重抓清單。重抓失敗時原本只 console.error——
// 建立成功但新對話沒出現在清單、刪除成功但舊對話還留在清單，使用者無從得知「動作成功了，只是清單沒更新」。
describe('訊息中心：建立／刪除對話之後的清單重抓', () => {
  let toastError: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    toastError = vi.spyOn(toast, 'error').mockImplementation(() => {})
    vi.mocked(getMessages).mockResolvedValue({ messages: [], page: 0, size: 50, totalElements: 0, totalPages: 0 } as never)
    vi.mocked(markConversationRead).mockResolvedValue(undefined as never)
  })
  afterEach(() => {
    toastError.mockRestore()
  })

  async function createConversationViaForm() {
    fireEvent.click(screen.getByTestId('new-conversation-button'))
    fireEvent.change(screen.getByTestId('new-recipient-input'), { target: { value: 'user-9' } })
    fireEvent.click(screen.getByTestId('create-conversation-submit'))
  }

  it('建立成功、清單重抓失敗 → 告訴使用者「對話已建立，但清單沒更新」，舊清單留著，仍開啟新對話', async () => {
    getConvs.mockResolvedValueOnce(conversationsPage([conversation('c1', '舊朋友')])).mockRejectedValueOnce(new Error('network down'))
    vi.mocked(createConversation).mockResolvedValue({ conversationId: 'c9' } as never)
    render(<ChatPage />)
    await screen.findByTestId('conversation-item-c1')

    await createConversationViaForm()

    await waitFor(() => expect(toastError).toHaveBeenCalledTimes(1))
    const message = String(toastError.mock.calls[0][0])
    expect(message, '要講清楚動作成功了').toContain('對話已建立')
    expect(message, '要講清楚清單沒更新').toContain('清單')
    expect(message, '不能沿用「建立對話失敗」——對話其實建好了').not.toContain('建立對話失敗')
    expect(screen.getByTestId('conversation-item-c1'), '重抓失敗時舊清單原樣保留').toBeTruthy()
    await waitFor(() => expect(getMessages).toHaveBeenCalled()) // 新對話仍照常開啟，不因為清單沒更新就中斷
  })

  it('刪除成功、清單重抓失敗 → 告訴使用者「對話已刪除，但清單沒更新」', async () => {
    getConvs.mockResolvedValueOnce(conversationsPage([conversation('c1', '舊朋友')])).mockRejectedValueOnce(new Error('network down'))
    vi.mocked(deleteConversation).mockResolvedValue(undefined as never)
    render(<ChatPage />)
    fireEvent.click(await screen.findByTestId('conversation-item-c1'))

    // 刪除鈕只在選了對話之後才出現
    fireEvent.click(await screen.findByTestId('delete-conversation-button'))

    await waitFor(() => expect(toastError).toHaveBeenCalledTimes(1))
    const message = String(toastError.mock.calls[0][0])
    expect(message).toContain('對話已刪除')
    expect(message).toContain('清單')
    expect(message, '不能沿用「刪除對話失敗」——對話其實刪掉了').not.toContain('刪除對話失敗')
  })

  it('對照：建立成功、清單也重抓成功 → 沒有任何錯誤通知，新對話出現在清單', async () => {
    getConvs.mockResolvedValueOnce(conversationsPage([])).mockResolvedValueOnce(conversationsPage([conversation('c9', '新朋友')]))
    vi.mocked(createConversation).mockResolvedValue({ conversationId: 'c9' } as never)
    render(<ChatPage />)
    await screen.findByText('尚無對話')

    await createConversationViaForm()

    expect(await screen.findByTestId('conversation-item-c9')).toBeTruthy()
    expect(toastError, '重抓成功時不該出現錯誤通知').not.toHaveBeenCalled()
  })

  it('建立對話本身失敗 → 仍是「建立對話失敗」（原有行為不變），而且不會去重抓清單', async () => {
    getConvs.mockResolvedValueOnce(conversationsPage([conversation('c1', '舊朋友')]))
    vi.mocked(createConversation).mockRejectedValue(new Error('403'))
    render(<ChatPage />)
    await screen.findByTestId('conversation-item-c1')

    await createConversationViaForm()

    await waitFor(() => expect(toastError).toHaveBeenCalledTimes(1))
    expect(String(toastError.mock.calls[0][0])).toContain('建立對話失敗')
    expect(getConvs, '只有首次載入那一次').toHaveBeenCalledTimes(1)
  })
})
