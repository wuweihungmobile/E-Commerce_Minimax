import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from '@/lib/axios'
import adminService from './admin'

vi.mock('@/lib/axios', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

// Sprint 331（DEF-455）：平台管理員端的請求從 6 個頁面搬進 adminService；測的是「線上格式沒有變」與預設值。
const http = vi.mocked(apiClient)
const ok = <T>(data: T) => ({ data: { success: true, data } })

// resetAllMocks（不是 clearAllMocks）：某個案例失敗時，它沒用完的 mockResolvedValueOnce 佇列不能漏到下一個案例
beforeEach(() => {
  vi.resetAllMocks()
})

describe('adminService：店鋪與開店申請', () => {
  it('開店申請與店鋪是不同實體、不同端點（DEF-060：待審核分頁查申請，不是查 status=PENDING_REVIEW 的店鋪）', async () => {
    http.get.mockResolvedValue(ok({ applications: [{ applicationId: 'a1' }] }))
    await expect(adminService.listTenantApplications()).resolves.toEqual([{ applicationId: 'a1' }])
    expect(http.get.mock.calls[0][0]).toBe('/v2/admin/tenant-applications')
  })

  it('listTenantApplications：後端沒給 applications 時是空陣列', async () => {
    http.get.mockResolvedValue(ok({}))
    await expect(adminService.listTenantApplications()).resolves.toEqual([])
  })

  it('listTenants：查詢參數放在 params（page 為 0-based，由呼叫端換算）', async () => {
    http.get.mockResolvedValue(ok({ tenants: [] }))
    await adminService.listTenants({ page: 0, size: 20, status: 'ACTIVE', keyword: '花蓮' })
    expect(http.get.mock.calls[0][0]).toBe('/v2/admin/tenants')
    expect(http.get.mock.calls[0][1]).toEqual({ params: { page: 0, size: 20, status: 'ACTIVE', keyword: '花蓮' } })
  })

  it('核准申請不帶本文；駁回申請本文是 { reason }', async () => {
    http.post.mockResolvedValue(ok(null))
    await adminService.approveTenantApplication('a1')
    await adminService.rejectTenantApplication('a1', '資料不齊')
    expect(http.post.mock.calls[0][0]).toBe('/v2/admin/tenant-applications/a1/approve')
    expect(http.post.mock.calls[0][1]).toBeUndefined()
    expect(http.post.mock.calls[1]).toEqual(['/v2/admin/tenant-applications/a1/reject', { reason: '資料不齊' }])
  })

  it('getTenantFeatures：配額沒給時補空陣列；updateTenantFeature：PUT 本文只有 enabled', async () => {
    http.get.mockResolvedValue(ok({ features: [] }))
    http.put.mockResolvedValue(ok(null))
    await expect(adminService.getTenantFeatures('t1')).resolves.toEqual({ features: [], quotas: [] })
    await adminService.updateTenantFeature('t1', 'BOOKING_ENABLED', false)
    expect(http.get.mock.calls[0][0]).toBe('/v2/admin/tenants/t1/features')
    expect(http.put).toHaveBeenCalledWith('/v2/admin/tenants/t1/features/BOOKING_ENABLED', { enabled: false })
  })
})

describe('adminService：採購單審批、稽核紀錄、統計', () => {
  it('listPendingPurchaseOrders：總覽頁不帶任何設定（只取 totalElements）；列表頁帶分頁參數', async () => {
    http.get.mockResolvedValue(ok({ purchaseOrders: [], totalElements: 3 }))
    await adminService.listPendingPurchaseOrders()
    await adminService.listPendingPurchaseOrders({ page: 1, size: 20 })
    expect(http.get.mock.calls[0][0]).toBe('/v2/admin/purchase-orders/pending')
    expect(http.get.mock.calls[0][1]).toBeUndefined()
    expect(http.get.mock.calls[1][1]).toEqual({ params: { page: 1, size: 20 } })
  })

  it('核准不帶本文；駁回本文是 { reason }', async () => {
    http.post.mockResolvedValue(ok(null))
    await adminService.approvePurchaseOrder('po1')
    await adminService.rejectPurchaseOrder('po1', '金額過高')
    expect(http.post.mock.calls[0][0]).toBe('/v2/admin/purchase-orders/po1/approve')
    expect(http.post.mock.calls[0][1]).toBeUndefined()
    expect(http.post.mock.calls[1]).toEqual(['/v2/admin/purchase-orders/po1/reject', { reason: '金額過高' }])
  })

  it('listAuditLogs：沒填的篩選條件不出現在 params 裡（axios 會略過 undefined）', async () => {
    http.get.mockResolvedValue(ok({ logs: [] }))
    await adminService.listAuditLogs({ page: 0, size: 20, action: 'TENANT_APPROVED' })
    expect(http.get.mock.calls[0][0]).toBe('/v2/admin/audit-logs')
    expect(http.get.mock.calls[0][1]).toStrictEqual({ params: { page: 0, size: 20, action: 'TENANT_APPROVED' } })
  })

  it('getPlatformStats：GET /v2/admin/stats，把 signal 轉給 axios', async () => {
    http.get.mockResolvedValue(ok({ totalTenants: 1 }))
    const controller = new AbortController()
    await expect(adminService.getPlatformStats({ signal: controller.signal })).resolves.toEqual({ totalTenants: 1 })
    expect(http.get.mock.calls[0][0]).toBe('/v2/admin/stats')
    expect(http.get.mock.calls[0][1]).toEqual({ signal: controller.signal })
  })
})
