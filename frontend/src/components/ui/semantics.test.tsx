import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Alert } from './alert'
import { CardTitle } from './card'
import { Chip } from './chip'
import { PageTitle } from './page-title'
import { EmptyState, ErrorState, LoadingState } from './states'
import { StatusBadge } from './status-badge'

// 這些元件的外觀由 E2E 的 computed style 守（at-ui-design-system.spec.ts）；這裡守的是外觀之外、
// 換皮時最容易被「順手改掉」的東西：HTML 語意（標題層級、role、aria）與 E2E 賴以定位的 data-testid。

describe('PageTitle／CardTitle：標題層級', () => {
  it('PageTitle 是 <h1>（每頁只該有一個頁面主標題；螢幕閱讀器用它導覽，DEF-459）', () => {
    render(<PageTitle>商品管理</PageTitle>)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('商品管理')
  })

  it('CardTitle 是 <h3>，不是頁面主標題——拿它當頁面標題的頁面會出現「只有 h3、沒有 h1」', () => {
    render(<CardTitle>登入</CardTitle>)
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull()
    expect(screen.getByRole('heading', { level: 3 }).textContent).toBe('登入')
  })
})

describe('LoadingState／EmptyState：轉傳其餘屬性', () => {
  // Sprint 329 的遷移曾把 <div data-testid="…"> 換成 <EmptyState> 而丟掉 data-testid，type-check／lint 都沒抓到，
  // 只有完整 E2E 抓到。元件必須把 data-testid、id 等屬性轉傳給根元素。
  it('LoadingState 轉傳 data-testid／id，預設文案「載入中...」，role=status', () => {
    render(<LoadingState data-testid="orders-loading" id="x" />)
    const el = screen.getByTestId('orders-loading')
    expect(el.id).toBe('x')
    expect(el.textContent).toBe('載入中...')
    expect(el.getAttribute('role')).toBe('status')
  })

  it('LoadingState 可換文案', () => {
    render(<LoadingState label="驗證中..." />)
    expect(screen.getByRole('status').textContent).toBe('驗證中...')
  })

  it('EmptyState 轉傳 data-testid；標題、說明、動作都渲染；圖示只是裝飾（aria-hidden）', () => {
    render(
      <EmptyState
        data-testid="orders-empty"
        icon="📦"
        title="目前沒有訂單"
        description="快去逛逛吧"
        action={<button type="button">去逛逛</button>}
      />
    )
    const el = screen.getByTestId('orders-empty')
    expect(el.textContent).toContain('目前沒有訂單')
    expect(el.textContent).toContain('快去逛逛吧')
    expect(screen.getByRole('button', { name: '去逛逛' })).toBeTruthy()
    expect(screen.getByText('📦').getAttribute('aria-hidden')).toBe('true')
  })

  it('EmptyState 沒給說明／動作／圖示就不渲染對應區塊（不留空的 <p>）', () => {
    const { container } = render(<EmptyState title="沒有資料" />)
    expect(container.querySelectorAll('p')).toHaveLength(1)
    expect(container.querySelector('[aria-hidden]')).toBeNull()
  })
})

describe('ErrorState：載入失敗的畫面（DEF-468）', () => {
  it('role=alert（失敗是使用者沒預期的事，螢幕閱讀器要主動念）；轉傳 data-testid；預設標題與「重新載入」', () => {
    const onRetry = vi.fn()
    render(<ErrorState data-testid="x-error" onRetry={onRetry} />)
    const el = screen.getByTestId('x-error')
    expect(el.getAttribute('role')).toBe('alert')
    expect(el.textContent).toContain('載入失敗，請稍後再試')
    fireEvent.click(screen.getByRole('button', { name: '重新載入' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('標題、說明、重試鈕文字都可換', () => {
    render(<ErrorState title="載入文章失敗" description="請確認網路連線" onRetry={() => {}} retryLabel="再試一次" />)
    expect(screen.getByText('載入文章失敗')).toBeTruthy()
    expect(screen.getByText('請確認網路連線')).toBeTruthy()
    expect(screen.getByRole('button', { name: '再試一次' })).toBeTruthy()
  })

  it('沒給 onRetry 就沒有按鈕（不放一個按了沒反應的鈕）；沒給說明就不留空的 <p>', () => {
    const { container } = render(<ErrorState />)
    expect(screen.queryByRole('button')).toBeNull()
    expect(container.querySelectorAll('p')).toHaveLength(1)
  })

  it('與 EmptyState 長得不同、語意不同：EmptyState 不是 alert、也沒有重試鈕——兩者不能互相冒充', () => {
    render(<EmptyState title="尚無資料" />)
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })
})

describe('Chip', () => {
  it('選取態用 aria-pressed 表達（E2E 與輔助科技讀它，不讀 class 名）', () => {
    const { rerender } = render(<Chip selected>全部</Chip>)
    expect(screen.getByRole('button', { name: '全部' }).getAttribute('aria-pressed')).toBe('true')
    rerender(<Chip>全部</Chip>)
    expect(screen.getByRole('button', { name: '全部' }).getAttribute('aria-pressed')).toBe('false')
  })

  it('預設 type="button"（放進表單裡不會意外送出表單）', () => {
    render(<Chip>篩選</Chip>)
    expect(screen.getByRole('button').getAttribute('type')).toBe('button')
  })
})

describe('StatusBadge', () => {
  it('依領域＋狀態值顯示登錄表的標籤', () => {
    render(<StatusBadge domain="purchaseOrder" status="PENDING_APPROVAL" />)
    expect(screen.getByText('待審批')).toBeTruthy()
  })

  it('也可以直接給 entry（功能開關這種要先算出狀態的情況）', () => {
    render(<StatusBadge entry={{ label: '待審核', variant: 'warning' }} />)
    expect(screen.getByText('待審核')).toBeTruthy()
  })

  it('沒登錄的狀態值原樣顯示，不吞掉', () => {
    render(<StatusBadge domain="post" status="WEIRD" />)
    expect(screen.getByText('WEIRD')).toBeTruthy()
  })
})

describe('Alert', () => {
  it('一律 role="alert"（錯誤橫幅要讓輔助科技立刻宣告）', () => {
    render(<Alert variant="destructive">載入失敗</Alert>)
    expect(screen.getByRole('alert').textContent).toBe('載入失敗')
  })
})
