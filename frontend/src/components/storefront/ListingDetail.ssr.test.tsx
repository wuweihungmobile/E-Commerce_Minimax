import { hydrateRoot } from 'react-dom/client'
import { renderToString } from 'react-dom/server'
import { act, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

// 商品／房源詳情頁改由伺服器渲染之後（SEO，DEF-500，Sprint 341）：房源的整月日曆第一個畫面依賴「現在的時間」（本月是幾月、今天是哪天）。
// 伺服器（容器通常是 UTC）與訪客瀏覽器（台灣 UTC+8）在月底與午夜前後算出不同的月份與日期，hydration 會因文字不符而丟掉伺服器的 HTML。
// 日曆不是搜尋引擎要收錄的內容，所以只在瀏覽器渲染；伺服器端（與 hydration 期間）放同樣大小的佔位。

vi.mock('@/services/listing', () => ({ default: { getListingById: vi.fn() } }))
vi.mock('@/services/cart', () => ({ default: { addItem: vi.fn() } }))
vi.mock('@/services/cartEvents', () => ({ notifyCartChanged: vi.fn() }))
vi.mock('@/services/booking', () => ({ default: { checkAvailability: vi.fn() } }))
vi.mock('@/components/storefront/MonthCalendar', () => ({ MonthCalendar: () => <div data-testid="month-calendar" /> }))

import { ListingDetail } from './ListingDetail'

const room = {
  id: '0b135be9-bf30-4ddb-ab3f-092f3e26b845',
  tenantId: 't1',
  listingType: 'ROOM',
  title: '海景雙人房',
  description: '面海，含早餐',
  coverImageUrl: undefined,
  status: 'ACTIVE',
  basePrice: 2800,
  currency: 'TWD',
  tags: [],
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
} as never
const product = { ...(room as object), listingType: 'PRODUCT', title: '手沖咖啡豆', basePrice: 350 } as never

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('ListingDetail：伺服器端輸出', () => {
  it('房源：第一份 HTML 有名稱、價格與說明（給搜尋引擎），但沒有整月日曆，只有佔位', () => {
    const html = renderToString(<ListingDetail id={(room as { id: string }).id} initialListing={room} />)

    expect(html).toContain('海景雙人房')
    expect(html).toContain('NT$2,800')
    expect(html).toContain('面海，含早餐')
    expect(html, '整月日曆依賴「現在」，不能出現在伺服器端輸出').not.toContain('data-testid="month-calendar"')
    expect(html).toContain('data-testid="listing-calendar-placeholder"')
    expect(html, '不該是載入中骨架').not.toContain('data-testid="listing-loading"')
  })

  it('商品：第一份 HTML 有名稱、價格與數量欄位；沒有日曆也沒有佔位（商品不需要）', () => {
    const html = renderToString(<ListingDetail id={(product as { id: string }).id} initialListing={product} />)

    expect(html).toContain('手沖咖啡豆')
    expect(html).toContain('NT$350')
    expect(html).not.toContain('listing-calendar-placeholder')
  })
})

describe('ListingDetail：hydration 完成前，下單相關的互動控制項是停用的', () => {
  // 內容改由伺服器渲染之後，日期、數量與按鈕在第一份 HTML 就出現了，但事件處理還沒接上：這時輸入的值會在 hydration 時被重設、
  // 點擊沒有反應。以前它們要等用戶端載入完才出現，天然避開了這個空窗（E2E-GUESTCAL-02 第一次執行時就掉進去：載入完立刻填日期、按按鈕，沒反應）。
  const controls = (container: Element) =>
    ['listing-checkin', 'listing-checkout', 'listing-quantity', 'listing-add-cart'].map((id) => {
      const element = container.querySelector(`[data-testid="${id}"]`) as HTMLInputElement | HTMLButtonElement | null
      return [id, element ? element.disabled : null] as const
    })

  it('房源：日期欄位、查詢可用性、加入購物車在伺服器端輸出裡都是停用的；hydration 之後日期欄位與查詢按鈕可用', async () => {
    const id = (room as { id: string }).id
    const container = document.createElement('div')
    container.innerHTML = renderToString(<ListingDetail id={id} initialListing={room} />)
    document.body.appendChild(container)
    expect(controls(container).filter(([key]) => key !== 'listing-quantity')).toEqual([
      ['listing-checkin', true],
      ['listing-checkout', true],
      ['listing-add-cart', true],
    ])
    expect((container.querySelector('button.self-start') as HTMLButtonElement).disabled).toBe(true) // 查詢可用性

    await act(async () => {
      hydrateRoot(container, <ListingDetail id={id} initialListing={room} />)
    })

    expect((screen.getByTestId('listing-checkin') as HTMLInputElement).disabled).toBe(false)
    expect((screen.getByTestId('listing-checkout') as HTMLInputElement).disabled).toBe(false)
    expect((screen.getByRole('button', { name: '查詢可用性' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('商品：數量與加入購物車在伺服器端輸出裡是停用的；hydration 之後都可用', async () => {
    const id = (product as { id: string }).id
    const container = document.createElement('div')
    container.innerHTML = renderToString(<ListingDetail id={id} initialListing={product} />)
    document.body.appendChild(container)
    expect(controls(container).filter(([key]) => key === 'listing-quantity' || key === 'listing-add-cart')).toEqual([
      ['listing-quantity', true],
      ['listing-add-cart', true],
    ])

    await act(async () => {
      hydrateRoot(container, <ListingDetail id={id} initialListing={product} />)
    })

    expect((screen.getByTestId('listing-quantity') as HTMLInputElement).disabled).toBe(false)
    expect((screen.getByTestId('listing-add-cart') as HTMLButtonElement).disabled).toBe(false)
  })
})

describe('ListingDetail：hydration', () => {
  it('房源：用伺服器端的 HTML hydration 不會有任何不符的錯誤，接手之後佔位換成整月日曆', async () => {
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {})
    const id = (room as { id: string }).id
    const container = document.createElement('div')
    container.innerHTML = renderToString(<ListingDetail id={id} initialListing={room} />)
    document.body.appendChild(container)
    expect(container.querySelector('[data-testid="month-calendar"]')).toBeNull()

    await act(async () => {
      hydrateRoot(container, <ListingDetail id={id} initialListing={room} />)
    })

    expect(errors, 'hydration 不符（伺服器與瀏覽器第一個畫面不同）會走到 console.error').not.toHaveBeenCalled()
    expect(screen.getByTestId('month-calendar')).toBeTruthy()
    expect(container.querySelector('[data-testid="listing-calendar-placeholder"]')).toBeNull()
  })
})
