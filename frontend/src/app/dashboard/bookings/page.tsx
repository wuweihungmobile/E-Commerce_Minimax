'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import BookingService, {
  type BookingListItem,
  BOOKING_STATUS_LABELS,
  bookingStatusBadgeVariant,
} from '@/services/booking'
import AuthService from '@/services/auth'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

export default function DashboardBookingsPage() {
  const router = useRouter()
  const [bookings, setBookings] = useState<BookingListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchBookings = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await BookingService.listTenantBookings()
      setBookings(result.content)
    } catch (err) {
      setError(extractErrorMessage(err, '載入訂房列表失敗'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchBookings()
  }, [router, fetchBookings])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">{error}</div>
        <Button variant="outline" onClick={fetchBookings}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">訂房管理</h1>
          <p className="text-muted-foreground">共 {bookings.length} 筆訂房</p>
        </div>
        <Button variant="outline" onClick={fetchBookings}>重新整理</Button>
      </div>

      {bookings.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500">尚無訂房</p>
          </CardContent>
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
                      <span className="text-sm font-medium text-gray-900">
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
