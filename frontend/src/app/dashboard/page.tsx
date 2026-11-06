'use client'

import Link from 'next/link'
import AnalyticsService from '@/services/analytics'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { LoadingState } from '@/components/ui/states'
import { PageTitle } from '@/components/ui/page-title'

// 快速管理入口已移到共用賣家外殼的側欄（components/layout/sellerNav.ts，沿用原本的標籤與 data-testid），
// 本頁只留營運數據。

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('zh-TW', {
    style: 'currency',
    currency: 'TWD',
    maximumFractionDigits: 0,
  }).format(value ?? 0)
}

function StatCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card className="p-5">
      <p className="text-sm text-rs-ink-muted">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-rs-ink">{value}</p>
      {hint ? <p className="mt-1 text-xs text-rs-disabled-text">{hint}</p> : null}
    </Card>
  )
}

export default function DashboardPage() {
  // 原本的頁面不顯示後端訊息，固定顯示這句（所以 errorMessage 用函式形式忽略伺服器訊息）
  const { data, error, loading } = useAsyncData(
    async () => {
      const [stats, orderStats, revenue] = await Promise.all([
        AnalyticsService.getDashboardStats(),
        AnalyticsService.getOrderStats(),
        AnalyticsService.getRevenueStats(),
      ])
      return { stats, orderStats, revenue }
    },
    [],
    { errorMessage: () => '無法載入儀表板資料，請稍後再試' }
  )
  const stats = data?.stats ?? null
  const orderStats = data?.orderStats ?? null
  const revenue = data?.revenue ?? null

  if (loading) {
    return (
      <LoadingState label="Loading..." className="h-auto min-h-[40vh]" />
    )
  }

  const growth = stats?.revenueGrowthPercent ?? 0

  return (
    <div>
      <main>
        <PageTitle className="mb-4 text-lg font-medium">營運總覽</PageTitle>

        {error ? (
          <Alert variant="destructive" className="mb-6">
            {error}
          </Alert>
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

        {/* 訂單狀態總覽（Sprint 151，DEF-188：各狀態數字連結至 /dashboard/orders 對應篩選，
            補上先前「有統計數字、無管理入口」的缺口） */}
        <Card asChild className="mt-6 p-6">
<section>
          <h3 className="text-base font-medium text-rs-ink mb-4">訂單狀態總覽</h3>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
            {[
              { label: '待付款', value: orderStats?.pendingPayment ?? 0, status: 'CREATED' },
              { label: '待出貨', value: orderStats?.pendingShipment ?? 0, status: 'PAID' },
              { label: '運送中', value: orderStats?.inTransit ?? 0, status: 'SHIPPING' },
              { label: '已送達', value: orderStats?.delivered ?? 0, status: 'DELIVERED' },
              { label: '已完成', value: orderStats?.completed ?? 0, status: 'COMPLETED' },
              { label: '已取消', value: orderStats?.cancelled ?? 0, status: 'CANCELLED' },
            ].map((item) => (
              <Link
                key={item.label}
                href={`/dashboard/orders?status=${item.status}`}
                className="text-center rounded-md py-1 hover:bg-rs-bg-base"
              >
                <p className="text-2xl font-semibold text-rs-ink">{item.value}</p>
                <p className="mt-1 text-xs text-rs-ink-muted">{item.label}</p>
              </Link>
            ))}
          </div>
          <p className="mt-4 text-sm text-rs-ink-muted">
            訂單總數：<span className="font-medium text-rs-ink">{orderStats?.totalOrders ?? 0}</span>
          </p>
        </section>
</Card>

        {/* 營收趨勢（近 30 天） */}
        <Card asChild className="mt-6 p-6">
<section>
          <div className="flex items-baseline justify-between mb-4">
            <h3 className="text-base font-medium text-rs-ink">營收趨勢（近 30 天）</h3>
            <span className="text-sm text-rs-ink-muted">
              淨營收 {formatCurrency(revenue?.netRevenue ?? 0)}｜平均客單價 {formatCurrency(revenue?.averageOrderValue ?? 0)}
            </span>
          </div>
          {(() => {
            const dailyData = revenue?.dailyRevenue?.data ?? []
            const maxRevenue = Math.max(...dailyData.map((d) => d.revenue), 1)
            if (dailyData.length === 0) {
              return <p className="text-sm text-rs-disabled-text">尚無營收資料</p>
            }
            return (
              <div className="flex items-end gap-1 h-40" role="img" aria-label="每日營收長條圖">
                {dailyData.map((d) => (
                  <div
                    key={d.date}
                    className="flex-1 bg-rs-primary rounded-t hover:bg-rs-primary-hover transition-colors"
                    style={{ height: `${Math.round((d.revenue / maxRevenue) * 100)}%` }}
                    title={`${d.date}：${formatCurrency(d.revenue)}（${d.orderCount} 筆）`}
                  />
                ))}
              </div>
            )
          })()}
        </section>
</Card>
      </main>
    </div>
  )
}
