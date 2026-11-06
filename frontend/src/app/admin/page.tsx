'use client'

import Link from 'next/link'
import adminService from '@/services/admin'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { LoadingState } from '@/components/ui/states'

// PRD §10.1.3 `/admin`（DEF-358(d)，Sprint 258）：平台管理台統一入口首頁。
// 各功能頁（/admin/tenants 等）皆無前端角色守門，純依賴後端 403；本頁沿用同一慣例，
// 不額外新增角色導向邏輯（範圍精準對應缺口本身：補入口頁，不是重做既有頁面的存取控制）。

// 快速管理入口已移到共用外殼的側欄（components/layout/sellerNav.ts，沿用原本的標籤與 data-testid）。

function StatCard({ label, value, href, testId }: { label: string; value: string; href?: string; testId?: string }) {
  const content = (
    <Card className="p-5">
      <p className="text-sm text-rs-ink-muted">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-rs-ink">{value}</p>
    </Card>
  )
  if (!href) return content
  return (
    <Link href={href} data-testid={testId} className="block hover:border-rs-primary transition-colors rounded-lg border border-rs-hairline">
      {content}
    </Link>
  )
}

export default function AdminOverviewPage() {
  // 原本的頁面不顯示後端訊息，固定顯示這句（所以 errorMessage 用函式形式忽略伺服器訊息）
  const { data, error, loading } = useAsyncData(
    async () => {
      const [stats, pendingPurchaseOrderList] = await Promise.all([
        adminService.getPlatformStats(),
        adminService.listPendingPurchaseOrders(),
      ])
      return { stats, pendingPurchaseOrders: pendingPurchaseOrderList.totalElements }
    },
    [],
    { errorMessage: () => '無法載入平台統計資料，請稍後再試' }
  )
  const stats = data?.stats ?? null
  const pendingPurchaseOrders = data?.pendingPurchaseOrders ?? 0

  if (loading) {
    return (
      <LoadingState label="Loading..." className="h-auto min-h-[40vh]" />
    )
  }

  return (
    <div>
      <main>
        <h2 className="text-lg font-medium text-rs-ink mb-4">平台總覽</h2>

        {error ? (
          <Alert variant="destructive" className="mb-6" data-testid="admin-overview-error">
            {error}
          </Alert>
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
