'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import BookingService, {
  type Booking,
  BOOKING_STATUS_LABELS,
  CANCELLATION_POLICY_SUMMARY,
  bookingStatusBadgeVariant,
  cancelResultMessage,
  isBookingCancellable,
} from '@/services/booking'
import AuthService from '@/services/auth'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export default function DashboardBookingDetailPage() {
  const params = useParams()
  const router = useRouter()
  const bookingId = params.id as string

  const [booking, setBooking] = useState<Booking | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [showCancel, setShowCancel] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)
  const [cancelNotice, setCancelNotice] = useState<string | null>(null)

  const fetchBooking = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const detail = await BookingService.getBooking(bookingId)
      setBooking(detail)
    } catch (err) {
      setError(extractErrorMessage(err, '載入訂房詳情失敗'))
    } finally {
      setLoading(false)
    }
  }, [bookingId])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchBooking()
  }, [router, fetchBooking])

  async function handleCancel() {
    setCancelling(true)
    setCancelError(null)
    try {
      const wasPaid = booking?.status === 'PAID' || booking?.status === 'CONFIRMED'
      const result = await BookingService.cancelBooking(bookingId, cancelReason.trim() || undefined)
      setCancelNotice(
        cancelResultMessage(result, wasPaid, (amount) => formatPrice(amount, booking?.currency ?? 'TWD'))
      )
      setShowCancel(false)
      setCancelReason('')
      await fetchBooking()
    } catch (err) {
      setCancelError(extractErrorMessage(err, '取消訂房失敗，此預訂目前狀態可能無法取消'))
    } finally {
      setCancelling(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error || !booking) {
    return (
      <div className="space-y-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">{error}</div>}
        <Link href="/dashboard/bookings">
          <Button variant="outline">返回訂房列表</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-2xl font-bold truncate">
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
            取消訂房
          </Button>
        )}
      </div>

      {showCancel && (
        <Card className="border-red-200">
          <CardHeader>
            <CardTitle className="text-base">取消訂房</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {cancelError && (
              <Alert variant="destructive">
                <AlertDescription>{cancelError}</AlertDescription>
              </Alert>
            )}
            <p className="text-sm text-gray-600">{CANCELLATION_POLICY_SUMMARY}</p>
            <div className="space-y-2">
              <Label htmlFor="cancelReason">取消原因（選填）</Label>
              <Input
                id="cancelReason"
                placeholder="例如：房源維修"
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

      {cancelNotice && (
        <Alert>
          <AlertDescription>{cancelNotice}</AlertDescription>
        </Alert>
      )}

      {booking.status === 'CANCELLED' && booking.refundStatus !== 'NONE' && booking.refundAmount != null && (
        <Card className="border-amber-200">
          <CardHeader>
            <CardTitle className="text-base">退款資訊</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-700 space-y-1">
            {booking.refundStatus === 'PENDING' ? (
              <p>退款處理中：將退款 {formatPrice(booking.refundAmount, booking.currency)}，系統會自動退回買家原付款方式。</p>
            ) : (
              <p>已退款 {formatPrice(booking.refundAmount, booking.currency)}，款項已退回買家原付款方式。</p>
            )}
          </CardContent>
        </Card>
      )}

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
  )
}
