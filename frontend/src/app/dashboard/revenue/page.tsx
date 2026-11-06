'use client'

import { useEffect, useState, useCallback } from 'react'
import AnalyticsService, { RevenueStats } from '@/services/analytics'
import { toLocalDateString } from '@/lib/date'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Input } from '@/components/ui/input'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'

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
    <Card className="p-5">
      <p className="text-sm text-rs-ink-muted">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-rs-ink">{value}</p>
    </Card>
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
        <PageTitle>營收報表</PageTitle>
        <PageDescription>自訂日期範圍與統計粒度查看營收趨勢</PageDescription>
      </div>

      {/* Filters */}
      <Card className="p-4 mb-6 flex flex-wrap gap-4 items-end">
        <div>
          <Label className="block mb-1">開始日期</Label>
          <Input
            type="date"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            className="w-auto"
          />
        </div>
        <div>
          <Label className="block mb-1">結束日期</Label>
          <Input
            type="date"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            className="w-auto"
          />
        </div>
        <div>
          <Label className="block mb-1">統計粒度</Label>
          <NativeSelect
            value={granularity}
            onChange={(e) => setGranularity(e.target.value as Granularity)}
            className="w-auto"
          >
            <option value="DAY">依日</option>
            <option value="WEEK">依週</option>
            <option value="MONTH">依月</option>
          </NativeSelect>
        </div>
      </Card>

      {error && (
        <Alert variant="destructive" className="mb-6">
          {error}
        </Alert>
      )}

      {loading ? (
        <LoadingState className="h-auto py-12" />
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
          <Card className="p-6">
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
          </Card>
        </>
      )}
    </div>
  )
}
