'use client'

import { useEffect, useState, useCallback } from 'react'
import AnalyticsService, { RevenueStats } from '@/services/analytics'
import { toLocalDateString } from '@/lib/date'

type Granularity = 'DAY' | 'WEEK' | 'MONTH'

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('zh-TW', {
    style: 'currency',
    currency: 'TWD',
    maximumFractionDigits: 0,
  }).format(value ?? 0)
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-white rounded-lg p-5 border border-rs-hairline">
      <p className="text-sm text-rs-ink-muted">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-rs-ink">{value}</p>
    </div>
  )
}

export default function RevenueReportPage() {
  const today = new Date()
  const thirtyDaysAgo = new Date(today)
  thirtyDaysAgo.setDate(today.getDate() - 30)

  const [startDate, setStartDate] = useState(toLocalDateString(thirtyDaysAgo))
  const [endDate, setEndDate] = useState(toLocalDateString(today))
  const [granularity, setGranularity] = useState<Granularity>('DAY')
  const [revenue, setRevenue] = useState<RevenueStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadRevenue = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await AnalyticsService.getRevenueStats(startDate, endDate, granularity)
      setRevenue(data)
    } catch (err) {
      console.error('Failed to load revenue stats:', err)
      setError('載入營收報表失敗，請稍後再試')
    } finally {
      setLoading(false)
    }
  }, [startDate, endDate, granularity])

  useEffect(() => {
    loadRevenue()
  }, [loadRevenue])

  const dailyData = revenue?.dailyRevenue?.data ?? []
  const maxRevenue = Math.max(...dailyData.map((d) => d.revenue), 1)

  const granularityLabel: Record<Granularity, string> = {
    DAY: '依日',
    WEEK: '依週',
    MONTH: '依月',
  }

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">營收報表</h1>
        <p className="text-muted-foreground text-sm mt-1">自訂日期範圍與統計粒度查看營收趨勢</p>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg p-4 mb-6 flex flex-wrap gap-4 items-end border border-rs-hairline">
        <div>
          <label className="block text-sm font-medium text-rs-ink mb-1">開始日期</label>
          <input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="border rounded px-3 py-1.5 text-sm"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-rs-ink mb-1">結束日期</label>
          <input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="border rounded px-3 py-1.5 text-sm"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-rs-ink mb-1">統計粒度</label>
          <select
            value={granularity}
            onChange={(e) => setGranularity(e.target.value as Granularity)}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="DAY">依日</option>
            <option value="WEEK">依週</option>
            <option value="MONTH">依月</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded-md mb-6">
          {error}
        </div>
      )}

      {loading ? (
        <div className="text-center py-12 text-rs-ink-muted">載入中...</div>
      ) : (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
            <StatCard label="總營收" value={formatCurrency(revenue?.totalRevenue ?? 0)} />
            <StatCard label="淨營收" value={formatCurrency(revenue?.netRevenue ?? 0)} />
            <StatCard label="平均客單價" value={formatCurrency(revenue?.averageOrderValue ?? 0)} />
            <StatCard label="總退款" value={formatCurrency(revenue?.totalRefunds ?? 0)} />
          </div>

          {/* Revenue Chart */}
          <div className="bg-white rounded-lg p-6 border border-rs-hairline">
            <div className="flex items-baseline justify-between mb-4">
              <h3 className="text-base font-medium text-rs-ink">
                營收趨勢（{granularityLabel[granularity]}）
              </h3>
              <span className="text-sm text-rs-ink-muted">
                訂單總數 {revenue?.totalOrders ?? 0} 筆
              </span>
            </div>
            {dailyData.length === 0 ? (
              <p className="text-sm text-rs-disabled-text">此區間尚無營收資料</p>
            ) : (
              <div className="flex items-end gap-1 h-48" role="img" aria-label="營收趨勢長條圖">
                {dailyData.map((d) => (
                  <div
                    key={d.date}
                    className="flex-1 bg-rs-primary rounded-t hover:bg-rs-primary-hover transition-colors"
                    style={{ height: `${Math.round((d.revenue / maxRevenue) * 100)}%` }}
                    title={`${d.date}：${formatCurrency(d.revenue)}（${d.orderCount} 筆）`}
                  />
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
