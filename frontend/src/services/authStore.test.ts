import { afterEach, describe, expect, it } from 'vitest'
import { getSelectedTenantIdSnapshot } from './authStore'

// Sprint 331：公開部落格三頁的「目前選用店鋪」。重點是它讀的是 localStorage 的 'tenantId'——
// 和 axios 攔截器加在每個請求上的 X-Tenant-ID 同一把鍵（店主切換店鋪時只改這一把，不改登入者資料裡的所屬店鋪）。
afterEach(() => {
  localStorage.clear()
})

describe('getSelectedTenantIdSnapshot', () => {
  it('讀 localStorage 的 tenantId', () => {
    localStorage.setItem('tenantId', 't-123')
    expect(getSelectedTenantIdSnapshot()).toBe('t-123')
  })

  it('沒有這把鍵，或值是空字串：回 null（頁面據此顯示「請先登入並設定店鋪」）', () => {
    expect(getSelectedTenantIdSnapshot()).toBeNull()
    localStorage.setItem('tenantId', '')
    expect(getSelectedTenantIdSnapshot()).toBeNull()
  })

  it('不看登入者資料（user）裡的 tenantId：切換店鋪只改 tenantId 這一把鍵', () => {
    localStorage.setItem('user', JSON.stringify({ tenantId: 'owner-store' }))
    localStorage.setItem('tenantId', 'switched-store')
    expect(getSelectedTenantIdSnapshot()).toBe('switched-store')
  })
})
