import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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

import { createConversation, getConversations, getMessages, markConversationRead } from '@/services/chat'
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
