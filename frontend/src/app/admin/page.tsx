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

const QUICK_LINKS = [
  { href: '/admin/tenants', label: '租戶管理', testId: 'admin-tenants-link' },
  { href: '/admin/audit-logs', label: '稽核紀錄', testId: 'admin-audit-logs-link' },
  { href: '/admin/purchase-orders', label: '採購單審批', testId: 'admin-purchase-orders-link' },
  { href: '/admin/settlements/reversal', label: '結算沖正', testId: 'admin-settlements-link' },
  { href: '/admin/support/tickets', label: '客服工單', testId: 'admin-support-tickets-link' },
]

type CurrentUser = { email: string; role: string }

function StatCard({ label, value, href, testId }: { label: string; value: string; href?: string; testId?: string }) {
  const content = (
    <div className="bg-white rounded-lg shadow p-5">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-gray-900">{value}</p>
    </div>
  )
  if (!href) return content
  return (
    <Link href={href} data-testid={testId} className="block hover:shadow-md transition-shadow rounded-lg">
      {content}
    </Link>
  )
}

export default function AdminOverviewPage() {
  const router = useRouter()
  const [user, setUser] = useState<CurrentUser | null>(null)
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
        if (!cancelled) {
          setUser(currentUser as CurrentUser)
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [router])

  const handleLogout = async () => {
    await AuthService.logout()
    router.push('/login')
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-lg text-gray-600">Loading...</div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <h1 className="text-xl font-bold text-gray-900">平台管理台</h1>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {user?.email} ({user?.role})
              </span>
              <button
                onClick={handleLogout}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700"
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        <h2 className="text-lg font-medium text-gray-900 mb-4">平台總覽</h2>

        {/* 快速管理入口 */}
        <section className="mb-6 flex flex-wrap gap-3">
          {QUICK_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              data-testid={link.testId}
              className="inline-flex items-center rounded-md border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50"
            >
              {link.label}
            </Link>
          ))}
        </section>

        {error ? (
          <div className="mb-6 rounded-md bg-red-50 border border-red-200 p-4 text-sm text-red-700" data-testid="admin-overview-error">
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
