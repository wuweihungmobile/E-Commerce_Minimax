'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import OrderPaymentService, { type OrderPaymentState } from '@/services/payment'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

/**
 * Stripe Checkout 回跳成功頁（真實金流 Phase A，AI-2410）。
 * 以 session_id（Stripe 於 success_url 帶回）呼叫後端 return 端點確認狀態並顯示結果。
 */
export default function PaymentSuccessPage() {
  const params = useParams()
  const orderId = params.id as string

  const [state, setState] = useState<OrderPaymentState | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // 於 client 端讀 session_id（避免 useSearchParams 的 Suspense 邊界需求）。
    // 全程走 async callback 更新狀態，不在 effect 內同步 setState（遵循 React 19 嚴格 hooks）。
    const sessionId = new URLSearchParams(window.location.search).get('session_id')
    const run = sessionId
      ? OrderPaymentService.confirmCheckoutReturn(orderId, sessionId)
      : Promise.reject(new Error('no-session'))
    run
      .then((s) => {
        if (!cancelled) setState(s)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        setError(e instanceof Error && e.message === 'no-session'
          ? '缺少付款工作階段資訊'
          : '確認付款狀態失敗，請至訂單查看')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [orderId])

  const paid = state?.paymentStatus === 'SUCCESS' || state?.orderStatus === 'PAID'

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="payment-success-card">
          <CardHeader>
            <CardTitle>付款結果</CardTitle>
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
                <p className="text-sm text-gray-600">訂單編號：{orderId}</p>
              </div>
            )}
            {!loading && !error && !paid && (
              <div className="space-y-2" data-testid="payment-success-pending">
                <p>
                  付款狀態：<Badge variant="secondary">處理中</Badge>
                </p>
                <p className="text-sm text-gray-600">
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
