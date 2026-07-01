'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import BookingService, {
  type Booking,
  BOOKING_STATUS_LABELS,
  bookingStatusBadgeVariant,
  isBookingCancellable,
} from '@/services/booking'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export default function BookingDetailPage() {
  const params = useParams()
  const bookingId = params.id as string

  const [booking, setBooking] = useState<Booking | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [showCancel, setShowCancel] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)

  const load = useCallback(
    async (signal?: { cancelled: boolean }) => {
      setLoading(true)
      setError(null)
      try {
        const detail = await BookingService.getBooking(bookingId)
        if (signal?.cancelled) return
        setBooking(detail)
      } catch {
        if (signal?.cancelled) return
        setError('無法載入預訂詳情，請稍後再試')
      } finally {
        if (!signal?.cancelled) setLoading(false)
      }
    },
    [bookingId]
  )

  useEffect(() => {
    const signal = { cancelled: false }
    load(signal)
    return () => {
      signal.cancelled = true
    }
  }, [load])

  const handleCancel = async () => {
    setCancelling(true)
    setCancelError(null)
    try {
      await BookingService.cancelBooking(bookingId, cancelReason.trim() || undefined)
      setShowCancel(false)
      setCancelReason('')
      await load()
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setCancelError(errorResponse?.response?.data?.message ?? '取消預訂失敗，此預訂目前狀態可能無法取消')
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center gap-4">
              <Link href="/" className="text-xl font-bold text-gray-900">
                NextKey
              </Link>
              <span className="text-gray-400">/</span>
              <Link href="/bookings" className="text-gray-600 hover:text-gray-900">
                我的預訂
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">預訂詳情</span>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-3xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        {error && (
          <Alert variant="destructive" className="mb-4">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {loading ? (
          <div className="space-y-4">
            <Skeleton className="h-8 w-52" />
            <Card>
              <CardContent className="py-6 space-y-3">
                <Skeleton className="h-5 w-full" />
                <Skeleton className="h-5 w-2/3" />
              </CardContent>
            </Card>
          </div>
        ) : booking ? (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <h1 className="text-2xl font-bold text-gray-900 truncate">
                    {booking.roomTitle || `訂房 #${booking.id.slice(0, 8)}`}
                  </h1>
                  <Badge variant={bookingStatusBadgeVariant(booking.status)}>
                    {BOOKING_STATUS_LABELS[booking.status] ?? booking.status}
                  </Badge>
                </div>
                <p className="text-sm text-gray-500">建立於 {formatDate(booking.createdAt)}</p>
              </div>
              {isBookingCancellable(booking.status) && !showCancel && (
                <Button variant="outline" onClick={() => setShowCancel(true)}>
                  取消預訂
                </Button>
              )}
            </div>

            {/* Cancel panel */}
            {showCancel && (
              <Card className="border-red-200">
                <CardHeader>
                  <CardTitle className="text-base">取消預訂</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {cancelError && (
                    <Alert variant="destructive">
                      <AlertDescription>{cancelError}</AlertDescription>
                    </Alert>
                  )}
                  <div className="space-y-2">
                    <Label htmlFor="cancelReason">取消原因（選填）</Label>
                    <Input
                      id="cancelReason"
                      placeholder="例如：行程變更"
                      value={cancelReason}
                      onChange={(e) => setCancelReason(e.target.value)}
                    />
                  </div>
                  <div className="flex gap-3">
                    <Button variant="destructive" onClick={handleCancel} disabled={cancelling}>
                      {cancelling ? '取消中…' : '確認取消'}
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => {
                        setShowCancel(false)
                        setCancelError(null)
                      }}
                      disabled={cancelling}
                    >
                      返回
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Stay info */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">住宿資訊</CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-gray-700 space-y-2">
                <div className="flex justify-between">
                  <span className="text-gray-500">入住</span>
                  <span>
                    {formatDate(booking.checkInDate)}
                    {booking.checkInTime ? `（${booking.checkInTime} 後）` : ''}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">退房</span>
                  <span>
                    {formatDate(booking.checkOutDate)}
                    {booking.checkOutTime ? `（${booking.checkOutTime} 前）` : ''}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">住宿天數</span>
                  <span>{booking.nightsCount} 晚</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">入住人數</span>
                  <span>{booking.guestCount} 人</span>
                </div>
                <div className="flex justify-between border-t pt-2 text-base font-bold text-gray-900">
                  <span>總金額</span>
                  <span>{formatPrice(booking.totalAmount, booking.currency)}</span>
                </div>
              </CardContent>
            </Card>

            {/* Guest info */}
            {(booking.guestName || booking.guestPhone || booking.guestEmail || booking.specialRequests) && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">訂房人資料</CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-gray-700 space-y-1">
                  {booking.guestName && <p>姓名：{booking.guestName}</p>}
                  {booking.guestPhone && <p>電話：{booking.guestPhone}</p>}
                  {booking.guestEmail && <p>Email：{booking.guestEmail}</p>}
                  {booking.specialRequests && <p>特殊要求：{booking.specialRequests}</p>}
                </CardContent>
              </Card>
            )}
          </div>
        ) : (
          !error && (
            <Card>
              <CardContent className="flex flex-col items-center justify-center py-16">
                <p className="text-gray-500 mb-4">找不到此預訂</p>
                <Link href="/bookings">
                  <Button variant="outline">返回預訂列表</Button>
                </Link>
              </CardContent>
            </Card>
          )
        )}
      </main>
    </div>
  )
}
