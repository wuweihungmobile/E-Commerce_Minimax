'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import BookingPaymentService from '@/services/bookingPayment'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { PageTitle } from '@/components/ui/page-title'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { useAsyncData } from '@/hooks/useAsyncData'

/**
 * 訂房 Stripe Checkout 回跳成功頁（Sprint 222，DEF-303 (1)）。
 * 以 session_id（Stripe 於 success_url 帶回）呼叫後端 return 端點確認狀態並顯示結果。
 */
export default function BookingPaymentSuccessPage() {
  const params = useParams()
  const bookingId = params.id as string

  const { data: state, error, loading } = useAsyncData(
    async () => {
      // 於 client 端讀 session_id（避免 useSearchParams 的 Suspense 邊界需求）
      const sessionId = new URLSearchParams(window.location.search).get('session_id')
      if (!sessionId) throw new Error('no-session')
      return BookingPaymentService.confirmCheckoutReturn(bookingId, sessionId)
    },
    [bookingId],
    {
      errorMessage: (_info, err) =>
        err instanceof Error && err.message === 'no-session' ? '缺少付款工作階段資訊' : '確認付款狀態失敗，請至預訂查看',
    }
  )

  const paid = state?.paymentStatus === 'SUCCESS' || state?.orderStatus === 'PAID'

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="booking-payment-success-card">
          <CardHeader>
            <PageTitle className="text-base leading-none tracking-tight">付款結果</PageTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {loading && <Skeleton className="h-20 w-full" />}
            {!loading && error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            {!loading && !error && paid && (
              <div className="space-y-2" data-testid="booking-payment-success-paid">
                <p>
                  付款狀態：<Badge variant="success">付款成功</Badge>
                </p>
                <p className="text-sm text-rs-ink-muted">預訂編號：{bookingId}</p>
              </div>
            )}
            {!loading && !error && !paid && (
              <div className="space-y-2" data-testid="booking-payment-success-pending">
                <p>
                  付款狀態：<Badge variant="secondary">處理中</Badge>
                </p>
                <p className="text-sm text-rs-ink-muted">尚未確認付款完成，請稍後至預訂查看，或重新付款。</p>
              </div>
            )}
            <div className="flex gap-3 pt-2">
              <Link href={`/bookings/${bookingId}`}>
                <Button data-testid="booking-payment-success-view">查看預訂</Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </StorefrontShell>
  )
}
