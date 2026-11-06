'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import OrderPaymentService from '@/services/payment'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { PageTitle } from '@/components/ui/page-title'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { useAsyncData } from '@/hooks/useAsyncData'

/**
 * Stripe Checkout 回跳成功頁（真實金流 Phase A，AI-2410）。
 * 以 session_id（Stripe 於 success_url 帶回）呼叫後端 return 端點確認狀態並顯示結果。
 */
export default function PaymentSuccessPage() {
  const params = useParams()
  const orderId = params.id as string

  const { data: state, error, loading } = useAsyncData(
    async () => {
      // 於 client 端讀 session_id（避免 useSearchParams 的 Suspense 邊界需求）
      const sessionId = new URLSearchParams(window.location.search).get('session_id')
      if (!sessionId) throw new Error('no-session')
      return OrderPaymentService.confirmCheckoutReturn(orderId, sessionId)
    },
    [orderId],
    {
      errorMessage: (_info, err) =>
        err instanceof Error && err.message === 'no-session' ? '缺少付款工作階段資訊' : '確認付款狀態失敗，請至訂單查看',
    }
  )

  const paid = state?.paymentStatus === 'SUCCESS' || state?.orderStatus === 'PAID'

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="payment-success-card">
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
              <div className="space-y-2" data-testid="payment-success-paid">
                <p>
                  付款狀態：<Badge variant="success">付款成功</Badge>
                </p>
                <p className="text-sm text-rs-ink-muted">訂單編號：{orderId}</p>
              </div>
            )}
            {!loading && !error && !paid && (
              <div className="space-y-2" data-testid="payment-success-pending">
                <p>
                  付款狀態：<Badge variant="secondary">處理中</Badge>
                </p>
                <p className="text-sm text-rs-ink-muted">
                  尚未確認付款完成，請稍後至訂單查看，或重新付款。
                </p>
              </div>
            )}
            <div className="flex gap-3 pt-2">
              <Link href={`/orders/${orderId}`}>
                <Button data-testid="payment-success-view-order">查看訂單</Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </StorefrontShell>
  )
}
