import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '@/lib/axios'
import tenantService from './tenant'

vi.mock('@/lib/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

// Sprint 331（DEF-455）：店主端的店鋪請求從 4 個元件與 1 個頁面搬進 tenantService；測的是「線上格式沒有變」與預設值。
const http = vi.mocked(apiClient)
const ok = <T>(data: T) => ({ data: { success: true, data } })

// resetAllMocks（不是 clearAllMocks）：某個案例失敗時，它沒用完的 mockResolvedValueOnce 佇列不能漏到下一個案例
beforeEach(() => {
  vi.resetAllMocks()
})

describe('tenantService', () => {
  it('apply：POST /v2/tenants/apply，本文原樣送出', async () => {
    http.post.mockResolvedValue(ok(null))
    await tenantService.apply({ storeName: '店', businessType: 'RETAIL_ONLY', contactEmail: 'a@b.co', contactPhone: '0912345678' })
    expect(http.post).toHaveBeenCalledWith('/v2/tenants/apply', {
      storeName: '店',
      businessType: 'RETAIL_ONLY',
      contactEmail: 'a@b.co',
      contactPhone: '0912345678',
    })
  })

  it('listMine：回傳 tenants 陣列；後端沒給 tenants（或整個 data）時是空陣列，不是 undefined', async () => {
    http.get.mockResolvedValueOnce(ok({ tenants: [{ tenantId: 't1' }] }))
    http.get.mockResolvedValueOnce(ok({}))
    http.get.mockResolvedValueOnce({ data: { success: true } })
    await expect(tenantService.listMine()).resolves.toEqual([{ tenantId: 't1' }])
    await expect(tenantService.listMine()).resolves.toEqual([])
    await expect(tenantService.listMine()).resolves.toEqual([])
    expect(http.get.mock.calls[0][0]).toBe('/v2/tenants/my')
  })

  it('getById／update：路徑帶店鋪 id', async () => {
    http.get.mockResolvedValue(ok({ tenantId: 't1', storeName: '店' }))
    http.put.mockResolvedValue(ok(null))
    await expect(tenantService.getById('t1')).resolves.toEqual({ tenantId: 't1', storeName: '店' })
    await tenantService.update('t1', { storeName: '新', slug: 's', businessType: 'HYBRID', contactEmail: 'a@b.co', contactPhone: '1' })
    expect(http.get.mock.calls[0][0]).toBe('/v2/tenants/t1')
    expect(http.put.mock.calls[0][0]).toBe('/v2/tenants/t1')
  })

  it('getMyFeatures：配額沒給時補空陣列（頁面直接 map，不能是 undefined）', async () => {
    http.get.mockResolvedValue(ok({ features: [{ featureKey: 'BOOKING_ENABLED' }] }))
    await expect(tenantService.getMyFeatures()).resolves.toEqual({ features: [{ featureKey: 'BOOKING_ENABLED' }], quotas: [] })
    expect(http.get.mock.calls[0][0]).toBe('/v2/dashboard/tenants/features')
  })

  it('updateMyFeature：PUT 功能路徑，本文只有 enabled，回傳後端的結果（DEF-428：畫面以後端結果為準）', async () => {
    http.put.mockResolvedValue(ok({ featureKey: 'BOOKING_ENABLED', previousState: false, newState: false, status: 'PENDING_APPROVAL' }))
    const result = await tenantService.updateMyFeature('BOOKING_ENABLED', true)
    expect(http.put).toHaveBeenCalledWith('/v2/dashboard/tenants/features/BOOKING_ENABLED', { enabled: true })
    expect(result.status).toBe('PENDING_APPROVAL')
  })
})
