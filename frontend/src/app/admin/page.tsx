'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import { loginHref } from '@/lib/auth-redirect'

// PRD §10.1.3 `/admin`（DEF-358(d)，Sprint 258）：平台管理台統一入口首頁。
// 各功能頁（/admin/tenants 等）皆無前端角色守門，純依賴後端 403；本頁沿用同一慣例，
// 不額外新增角色導向邏輯（範圍精準對應缺口本身：補入口頁，不是重做既有頁面的存取控制）。

interface ApiResponse<T> {
  success: boolean
  data: T
}

interface PlatformStatsResponse {
  totalTenants: number
  activeTenants: number
  totalUsers: number
  totalListings: number
  totalOrders: number
  pendingTenantReviews: number
}

interface PurchaseOrderPendingListResponse {
  totalElements: number
}

// 快速管理入口已移到共用外殼的側欄（components/layout/sellerNav.ts，沿用原本的標籤與 data-testid）。

function StatCard({ label, value, href, testId }: { label: string; value: string; href?: string; testId?: string }) {
  const content = (
    <div className="bg-white rounded-lg p-5 border border-rs-hairline">
      <p className="text-sm text-rs-ink-muted">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-rs-ink">{value}</p>
    </div>
  )
  if (!href) return content
  return (
    <Link href={href} data-testid={testId} className="block hover:border-rs-primary transition-colors rounded-lg border border-rs-hairline">
      {content}
    </Link>
  )
}

export default function AdminOverviewPage() {
  const router = useRouter()
  const [stats, setStats] = useState<PlatformStatsResponse | null>(null)
  const [pendingPurchaseOrders, setPendingPurchaseOrders] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const currentUser = AuthService.getCurrentUser()
    if (!currentUser) {
      router.push(loginHref())
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const [statsRes, poRes] = await Promise.all([
          apiClient.get<ApiResponse<PlatformStatsResponse>>(API_ENDPOINTS.admin.stats),
          apiClient.get<ApiResponse<PurchaseOrderPendingListResponse>>(API_ENDPOINTS.admin.purchaseOrders.pending),
        ])
        if (cancelled) return
        setStats(statsRes.data.data)
        setPendingPurchaseOrders(poRes.data.data.totalElements)
      } catch {
        if (!cancelled) setError('無法載入平台統計資料，請稍後再試')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [router])

  if (loading) {
    return (
      <div className="min-h-[40vh] flex items-center justify-center">
        <div className="text-lg text-rs-ink-muted">Loading...</div>
      </div>
    )
  }

  return (
    <div>
      <main>
        <h2 className="text-lg font-medium text-rs-ink mb-4">平台總覽</h2>

        {error ? (
          <div className="mb-6 rounded-md bg-rs-error/10 border border-rs-error/30 p-4 text-sm text-rs-error" data-testid="admin-overview-error">
            {error}
          </div>
        ) : null}

        {/* 平台統計 */}
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <StatCard label="租戶總數" value={String(stats?.totalTenants ?? 0)} />
          <StatCard label="營運中租戶" value={String(stats?.activeTenants ?? 0)} />
          <StatCard label="使用者總數" value={String(stats?.totalUsers ?? 0)} />
          <StatCard label="上架中商品/房源" value={String(stats?.totalListings ?? 0)} />
          <StatCard label="訂單總數" value={String(stats?.totalOrders ?? 0)} />
        </section>

        {/* 待處理事項（可點擊導向對應審核頁） */}
        <section className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <StatCard
            label="待審核開店申請"
            value={String(stats?.pendingTenantReviews ?? 0)}
            href="/admin/tenants"
            testId="admin-pending-tenant-reviews"
          />
          <StatCard
            label="待審批採購單"
            value={String(pendingPurchaseOrders)}
            href="/admin/purchase-orders"
            testId="admin-pending-purchase-orders"
          />
        </section>
      </main>
    </div>
  )
}
