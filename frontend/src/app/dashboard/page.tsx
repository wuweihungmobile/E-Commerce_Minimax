'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import AnalyticsService, { DashboardStats, OrderStats, RevenueStats } from '@/services/analytics'

const QUICK_LINKS = [
  { href: '/dashboard/products', label: '商品管理' },
  { href: '/dashboard/rooms', label: '房型管理' },
  { href: '/dashboard/pricing/rules', label: '定價規則', testId: 'dashboard-pricing-link' },
  { href: '/dashboard/returns', label: '退貨審核', testId: 'dashboard-returns-link' },
]

type CurrentUser = { email: string; fullName: string; role: string }

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('zh-TW', {
    style: 'currency',
    currency: 'TWD',
    maximumFractionDigits: 0,
  }).format(value ?? 0)
}

function StatCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="bg-white rounded-lg shadow p-5">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-gray-900">{value}</p>
      {hint ? <p className="mt-1 text-xs text-gray-400">{hint}</p> : null}
    </div>
  )
}

export default function DashboardPage() {
  const router = useRouter()
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [orderStats, setOrderStats] = useState<OrderStats | null>(null)
  const [revenue, setRevenue] = useState<RevenueStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const currentUser = AuthService.getCurrentUser()
    if (!currentUser) {
      router.push('/login')
      return
    }
    let cancelled = false
    ;(async () => {
      try {
        const [s, o, r] = await Promise.all([
          AnalyticsService.getDashboardStats(),
          AnalyticsService.getOrderStats(),
          AnalyticsService.getRevenueStats(),
        ])
        if (cancelled) return
        setStats(s)
        setOrderStats(o)
        setRevenue(r)
      } catch {
        if (!cancelled) setError('無法載入儀表板資料，請稍後再試')
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

  const growth = stats?.revenueGrowthPercent ?? 0

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <h1 className="text-xl font-bold text-gray-900">NextKey Dashboard</h1>
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
        <h2 className="text-lg font-medium text-gray-900 mb-4">營運總覽</h2>

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
          <div className="mb-6 rounded-md bg-red-50 border border-red-200 p-4 text-sm text-red-700">
            {error}
          </div>
        ) : null}

        {/* 營收統計 */}
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="今日營收" value={formatCurrency(stats?.todayRevenue ?? 0)} />
          <StatCard
            label="今日 vs 昨日"
            value={`${growth >= 0 ? '+' : ''}${growth.toFixed(1)}%`}
            hint={`昨日 ${formatCurrency(stats?.yesterdayRevenue ?? 0)}`}
          />
          <StatCard label="本月營收" value={formatCurrency(stats?.monthRevenue ?? 0)} />
          <StatCard label="本年營收" value={formatCurrency(stats?.yearRevenue ?? 0)} />
        </section>

        {/* 訂單統計 */}
        <section className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="今日訂單" value={String(stats?.todayOrders ?? 0)} />
          <StatCard label="本月訂單" value={String(stats?.monthOrders ?? 0)} />
          <StatCard label="待處理訂單" value={String(stats?.pendingOrders ?? 0)} />
          <StatCard
            label="上架中商品 / 房型"
            value={`${stats?.activeListings ?? 0} / ${stats?.totalRooms ?? 0}`}
          />
        </section>

        {/* 訂單狀態總覽 */}
        <section className="mt-6 bg-white rounded-lg shadow p-6">
          <h3 className="text-base font-medium text-gray-900 mb-4">訂單狀態總覽</h3>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
            {[
              { label: '待付款', value: orderStats?.pendingPayment ?? 0 },
              { label: '待出貨', value: orderStats?.pendingShipment ?? 0 },
              { label: '運送中', value: orderStats?.inTransit ?? 0 },
              { label: '已送達', value: orderStats?.delivered ?? 0 },
              { label: '已完成', value: orderStats?.completed ?? 0 },
              { label: '已取消', value: orderStats?.cancelled ?? 0 },
            ].map((item) => (
              <div key={item.label} className="text-center">
                <p className="text-2xl font-semibold text-gray-900">{item.value}</p>
                <p className="mt-1 text-xs text-gray-500">{item.label}</p>
              </div>
            ))}
          </div>
          <p className="mt-4 text-sm text-gray-500">
            訂單總數：<span className="font-medium text-gray-900">{orderStats?.totalOrders ?? 0}</span>
          </p>
        </section>

        {/* 營收趨勢（近 30 天） */}
        <section className="mt-6 bg-white rounded-lg shadow p-6">
          <div className="flex items-baseline justify-between mb-4">
            <h3 className="text-base font-medium text-gray-900">營收趨勢（近 30 天）</h3>
            <span className="text-sm text-gray-500">
              淨營收 {formatCurrency(revenue?.netRevenue ?? 0)}｜平均客單價 {formatCurrency(revenue?.averageOrderValue ?? 0)}
            </span>
          </div>
          {(() => {
            const dailyData = revenue?.dailyRevenue?.data ?? []
            const maxRevenue = Math.max(...dailyData.map((d) => d.revenue), 1)
            if (dailyData.length === 0) {
              return <p className="text-sm text-gray-400">尚無營收資料</p>
            }
            return (
              <div className="flex items-end gap-1 h-40" role="img" aria-label="每日營收長條圖">
                {dailyData.map((d) => (
                  <div
                    key={d.date}
                    className="flex-1 bg-blue-500 rounded-t hover:bg-blue-600 transition-colors"
                    style={{ height: `${Math.round((d.revenue / maxRevenue) * 100)}%` }}
                    title={`${d.date}：${formatCurrency(d.revenue)}（${d.orderCount} 筆）`}
                  />
                ))}
              </div>
            )
          })()}
        </section>
      </main>
    </div>
  )
}
