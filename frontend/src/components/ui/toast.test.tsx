import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Toaster, toast } from './toast'

// toast 取代原生 alert()：它是普通 DOM（E2E 才能用可見性斷言），而且是模組層級的單例（任何事件處理器、catch 區塊都能呼叫）。
// 單例的狀態會跨案例殘留，所以每個案例結束都把計時器走完，讓殘留的通知自己消失。
describe('toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    vi.useRealTimers()
  })

  const toasts = () => screen.queryAllByTestId('toast')

  it('錯誤用 role=alert（輔助科技立刻宣告），成功與資訊用 role=status（不打斷）', () => {
    render(<Toaster />)
    act(() => {
      toast.error('刪除失敗')
      toast.success('已儲存')
      toast.info('同步中')
    })
    const byKind = Object.fromEntries(toasts().map((el) => [el.getAttribute('data-kind'), el.getAttribute('role')]))
    expect(byKind).toEqual({ error: 'alert', success: 'status', info: 'status' })
  })

  it('依呼叫順序由上而下疊放，訊息文字原樣顯示', () => {
    render(<Toaster />)
    act(() => {
      toast.info('第一則')
      toast.info('第二則')
    })
    expect(toasts().map((el) => el.textContent)).toEqual(['第一則', '第二則'])
  })

  it('成功 4 秒後自己消失，錯誤多留到 6 秒（使用者需要更久讀錯誤原因）', () => {
    render(<Toaster />)
    act(() => {
      toast.success('已儲存')
      toast.error('儲存失敗')
    })
    act(() => {
      vi.advanceTimersByTime(3999)
    })
    expect(toasts()).toHaveLength(2)
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(toasts().map((el) => el.getAttribute('data-kind'))).toEqual(['error'])
    act(() => {
      vi.advanceTimersByTime(1999)
    })
    expect(toasts()).toHaveLength(1)
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(toasts()).toHaveLength(0)
  })

  it('可以手動關閉，而且關閉時一併清掉自動消失的計時器（不留會在之後亂觸發的 timer）', () => {
    render(<Toaster />)
    act(() => {
      toast.error('儲存失敗')
    })
    expect(vi.getTimerCount()).toBe(1)
    fireEvent.click(screen.getByLabelText('關閉通知'))
    expect(toasts()).toHaveLength(0)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('只關掉被點的那一則，其他的不受影響', () => {
    render(<Toaster />)
    act(() => {
      toast.info('甲')
      toast.info('乙')
    })
    fireEvent.click(screen.getAllByLabelText('關閉通知')[0])
    expect(toasts().map((el) => el.textContent)).toEqual(['乙'])
  })
})
