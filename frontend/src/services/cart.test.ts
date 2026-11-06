import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '@/lib/axios'
import cartService from './cart'

vi.mock('@/lib/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

// Sprint 331（DEF-455）把購物車的請求從 5 個頁面搬進 cartService。這些測試守的是「線上格式沒有變」：
// 路徑、查詢字串的編碼、請求本文有沒有多帶欄位——後端對「缺少欄位」與「欄位是 null」的處理不同，頁面端又沒有別的測試看得到。
const http = vi.mocked(apiClient)
const ok = <T>(data: T) => ({ data: { success: true, data } })

// resetAllMocks（不是 clearAllMocks）：某個案例失敗時，它沒用完的 mockResolvedValueOnce 佇列不能漏到下一個案例
beforeEach(() => {
  vi.resetAllMocks()
})

describe('cartService', () => {
  it('getCart：GET /v2/cart，回傳拆掉信封的資料', async () => {
    http.get.mockResolvedValue(ok({ cartId: 'c1', items: [] }))
    await expect(cartService.getCart()).resolves.toEqual({ cartId: 'c1', items: [] })
    expect(http.get.mock.calls[0][0]).toBe('/v2/cart')
  })

  it('getCart：把 signal 轉給 axios（useAsyncData 才能真的中止請求）', async () => {
    http.get.mockResolvedValue(ok({ items: [] }))
    const controller = new AbortController()
    await cartService.getCart({ signal: controller.signal })
    expect(http.get.mock.calls[0][1]).toEqual({ signal: controller.signal })
  })

  it('addItem：商品只送 listingId＋quantity；房源多帶入住／退房日', async () => {
    http.post.mockResolvedValue(ok(null))
    await cartService.addItem({ listingId: 'p1', quantity: 2 })
    await cartService.addItem({ listingId: 'r1', quantity: 1, startDate: '2026-12-01', endDate: '2026-12-03' })
    expect(http.post.mock.calls[0]).toEqual(['/v2/cart/items', { listingId: 'p1', quantity: 2 }])
    expect(http.post.mock.calls[1]).toEqual(['/v2/cart/items', { listingId: 'r1', quantity: 1, startDate: '2026-12-01', endDate: '2026-12-03' }])
  })

  it('updateQuantity：PUT 項目路徑，本文只有 quantity', async () => {
    http.put.mockResolvedValue(ok({ cartItemKey: 'k1', quantity: 3, subtotal: 300 }))
    await expect(cartService.updateQuantity('k1', 3)).resolves.toEqual({ cartItemKey: 'k1', quantity: 3, subtotal: 300 })
    expect(http.put).toHaveBeenCalledWith('/v2/cart/items/k1', { quantity: 3 })
  })

  it('removeItem：DELETE 項目路徑', async () => {
    http.delete.mockResolvedValue(ok(null))
    await cartService.removeItem('k1')
    expect(http.delete).toHaveBeenCalledWith('/v2/cart/items/k1')
  })

  describe('促銷碼', () => {
    it('validatePromo：代碼要做 URL 編碼（含空白與符號）；單一店鋪的舊購物車不帶 storeId', async () => {
      http.get.mockResolvedValue(ok({ valid: true }))
      await cartService.validatePromo('SAVE 10%')
      expect(http.get.mock.calls[0][0]).toBe('/v2/cart/validate-promo?code=SAVE%2010%25')
    })

    it('validatePromo：多家店鋪時帶 storeId（促銷碼在店鋪驗證）', async () => {
      http.get.mockResolvedValue(ok({ valid: false, invalidReason: '已過期' }))
      await expect(cartService.validatePromo('A', 'store-1')).resolves.toEqual({ valid: false, invalidReason: '已過期' })
      expect(http.get.mock.calls[0][0]).toBe('/v2/cart/validate-promo?code=A&storeId=store-1')
    })

    it('applyPromo：沒有 storeId 時請求本文「只有」promoCode（不是 storeId: undefined）', async () => {
      http.post.mockResolvedValue(ok({ appliedPromoCode: 'A', shippingFee: 0, discountAmount: 10, finalAmount: 90 }))
      await cartService.applyPromo('A')
      expect(http.post.mock.calls[0][0]).toBe('/v2/cart/apply-promo')
      expect(http.post.mock.calls[0][1]).toStrictEqual({ promoCode: 'A' })
    })

    it('applyPromo：有 storeId 時一併送出', async () => {
      http.post.mockResolvedValue(ok({ storeId: 's1', appliedPromoCode: 'A', shippingFee: 0, discountAmount: 10, finalAmount: 90 }))
      await cartService.applyPromo('A', 's1')
      expect(http.post.mock.calls[0][1]).toStrictEqual({ promoCode: 'A', storeId: 's1' })
    })

    it('removePromo：有 storeId 才帶 params；沒有就不帶設定物件', async () => {
      http.delete.mockResolvedValue(ok(null))
      await cartService.removePromo('s1')
      await cartService.removePromo()
      expect(http.delete.mock.calls[0]).toEqual(['/v2/cart/promo', { params: { storeId: 's1' } }])
      expect(http.delete.mock.calls[1][0]).toBe('/v2/cart/promo')
      expect(http.delete.mock.calls[1][1]).toBeUndefined()
    })
  })
})
