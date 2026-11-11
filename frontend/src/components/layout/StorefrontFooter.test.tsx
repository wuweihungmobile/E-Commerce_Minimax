import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StorefrontFooter } from './StorefrontFooter'

// 暫時入口（Sprint 338，DEF-491）：平台層的部落格頁 /blog 原本沒有任何導覽連結指向它（只剩手打網址與書籤）。
// 頁尾放一個「部落格」連結讓它有入口；正式的入口（跨店鋪部落格）或移除兩頁待 DEF-495 決定，到時這個連結與這個測試一併處理。

describe('頁尾的暫時入口：部落格（DEF-491）', () => {
  it('頁尾有「部落格」連結，指向平台層的 /blog', () => {
    render(<StorefrontFooter />)
    const link = within(screen.getByTestId('storefront-footer')).getByRole('link', { name: '部落格' })
    expect(link.getAttribute('href')).toBe('/blog')
  })

  it('賣家外殼用的頁尾（不放懸浮客服鈕）也有這個連結：連結在頁尾本體，不依賴懸浮鈕', () => {
    render(<StorefrontFooter showChatFab={false} />)
    expect(screen.queryByRole('link', { name: '聊聊客服' })).toBeNull()
    const link = within(screen.getByTestId('storefront-footer')).getByRole('link', { name: '部落格' })
    expect(link.getAttribute('href')).toBe('/blog')
  })

  it('原本的三個頁尾連結沒有被擠掉', () => {
    render(<StorefrontFooter />)
    const footer = within(screen.getByTestId('storefront-footer'))
    expect(footer.getByRole('link', { name: '隱私權政策' }).getAttribute('href')).toBe('/privacy')
    expect(footer.getByRole('link', { name: '服務條款' }).getAttribute('href')).toBe('/terms')
    expect(footer.getByRole('link', { name: '聯絡客服' }).getAttribute('href')).toBe('/notifications')
  })
})
