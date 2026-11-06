'use client'

import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import BookingService, {
  BOOKING_STATUS_LABELS,
  bookingStatusBadgeVariant,
} from '@/services/booking'
import { useAsyncData } from '@/hooks/useAsyncData'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

export default function DashboardBookingsPage() {
  const { data, error, loading, reload } = useAsyncData(
    () => BookingService.listTenantBookings(),
    [],
    { errorMessage: '載入訂房列表失敗' }
  )
  const bookings = data?.content ?? []

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">{error}</Alert>
        <Button variant="outline" onClick={reload}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <PageTitle>訂房管理</PageTitle>
          <PageDescription>共 {bookings.length} 筆訂房</PageDescription>
        </div>
        <Button variant="outline" onClick={reload}>重新整理</Button>
      </div>

      {bookings.length === 0 ? (
        <Card>
          <EmptyState title="尚無訂房" className="h-48" />
        </Card>
      ) : (
        <div className="space-y-4">
          {bookings.map((item) => (
            <Link key={item.id} href={`/dashboard/bookings/${item.id}`}>
              <Card className="hover:border-primary transition-colors">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{item.roomTitle || `訂房 #${item.id.slice(0, 8)}`}</CardTitle>
                      <CardDescription>
                        {item.guestName ? `${item.guestName} · ` : ''}
                        {formatDate(item.checkInDate)} – {formatDate(item.checkOutDate)}（{item.nightsCount} 晚）
                      </CardDescription>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium text-rs-ink">
                        {formatPrice(item.totalAmount, item.currency)}
                      </span>
                      <Badge variant={bookingStatusBadgeVariant(item.status)}>
                        {BOOKING_STATUS_LABELS[item.status] ?? item.status}
                      </Badge>
                    </div>
                  </div>
                </CardHeader>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
