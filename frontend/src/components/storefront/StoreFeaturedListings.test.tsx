import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// DEF-468（Sprint 332）：店鋪首頁的「精選商品/房源」區塊，載入失敗不能被當成「沒有精選」而整塊消失。
// 沒有精選項目時這一區本來就不顯示（店主沒勾選）；但請求失敗時使用者看到的也是「什麼都沒有」，
// 分不出是店主沒設定還是載入壞了，也沒有重試的辦法。

vi.mock('@/services/storefront', () => ({
  default: { getStoreListings: vi.fn() },
}))
vi.mock('@/components/storefront/ProductCard', () => ({
  ProductCard: ({ title }: { title: string }) => <div data-testid="featured-card">{title}</div>,
}))

import storefrontService from '@/services/storefront'
import { StoreFeaturedListings } from './StoreFeaturedListings'

const getListings = vi.mocked(storefrontService.getStoreListings)
const listing = { id: 'l1', title: '精選的商品', coverImageUrl: '', basePrice: 100, currency: 'TWD', listingType: 'PRODUCT' }
const listingsPage = (content: unknown[]) => ({ content }) as never

beforeEach(() => {
  getListings.mockReset()
})

describe('StoreFeaturedListings', () => {
  it('有精選項目 → 顯示標題與商品卡', async () => {
    getListings.mockResolvedValue(listingsPage([listing]))
    render(<StoreFeaturedListings slug="demo" />)
    expect(await screen.findByTestId('store-featured-listings')).toBeTruthy()
    expect(screen.getByText('精選的商品')).toBeTruthy()
  })

  it('成功但沒有精選項目 → 這一區不顯示，也沒有錯誤畫面（店主沒勾選，不是出錯）', async () => {
    getListings.mockResolvedValue(listingsPage([]))
    const { container } = render(<StoreFeaturedListings slug="demo" />)
    await waitFor(() => expect(getListings).toHaveBeenCalledTimes(1))
    // Skeleton 的標記是 animate-pulse：它消失代表載入結束
    await waitFor(() => expect(container.querySelector('.animate-pulse')).toBeNull())
    expect(screen.queryByTestId('store-featured-error')).toBeNull()
    expect(screen.queryByTestId('store-featured-listings')).toBeNull()
  })

  it('載入失敗 → 顯示錯誤與「重新載入」，不能整塊消失', async () => {
    getListings.mockRejectedValue(new Error('network down'))
    render(<StoreFeaturedListings slug="demo" />)
    expect(await screen.findByTestId('store-featured-error'), '載入失敗被當成「沒有精選」而整塊消失').toBeTruthy()
    expect(screen.getByRole('button', { name: '重新載入' })).toBeTruthy()
  })

  it('按「重新載入」會再請求一次，成功後顯示精選商品並移除錯誤', async () => {
    getListings.mockRejectedValueOnce(new Error('network down')).mockResolvedValueOnce(listingsPage([listing]))
    render(<StoreFeaturedListings slug="demo" />)
    fireEvent.click(await screen.findByRole('button', { name: '重新載入' }))
    expect(await screen.findByText('精選的商品')).toBeTruthy()
    expect(getListings).toHaveBeenCalledTimes(2)
    expect(screen.queryByTestId('store-featured-error')).toBeNull()
  })
})
