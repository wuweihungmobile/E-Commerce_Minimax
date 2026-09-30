'use client'

import { useEffect, useState } from 'react'
import BookingPaymentService from '@/services/bookingPayment'
import { PAYMENT_STATUS_LABELS, type OrderPaymentState } from '@/services/payment'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'

interface BookingPaymentCardProps {
  bookingId: string
  /** 應付金額（訂房總額）；付款狀態端點不回金額，由呼叫端帶入 */
  totalAmount: number
  currency: string
  /** 付款成功後通知呼叫端（例如重新載入訂房詳情以更新狀態標籤） */
  onPaid?: () => void
}

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 訂房付款區塊（Sprint 222，DEF-303 (1)）：訂房詳情頁、結帳完成畫面共用。
 * 依後端回報的付款提供者決定畫面：mock → 模擬付款按鈕；stripe → 重導至 Stripe 託管付款頁。
 * 已付款顯示付款資訊；沒有可付款也沒有付款紀錄（例如已取消）時不顯示任何東西。
 */
export default function BookingPaymentCard({ bookingId, totalAmount, currency, onPaid }: BookingPaymentCardProps) {
  const [state, setState] = useState<OrderPaymentState | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [paying, setPaying] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    // 全程走 async callback 更新狀態，不在 effect 內同步 setState（遵循 React 19 嚴格 hooks）。
    BookingPaymentService.getPaymentState(bookingId)
      .then((s) => {
        if (!cancelled) setState(s)
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [bookingId])

  const handlePay = async () => {
    setPaying(true)
    setPayError(null)
    try {
      setState(await BookingPaymentService.pay(bookingId))
      onPaid?.()
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setPayError(errorResponse?.response?.data?.message ?? '付款失敗，請稍後再試')
    } finally {
      setPaying(false)
    }
  }

  // 真實金流：建立 Stripe Checkout Session 並重導至託管付款頁
  const handleStripeCheckout = async () => {
    setPaying(true)
    setPayError(null)
    try {
      const session = await BookingPaymentService.createCheckoutSession(bookingId)
      window.location.href = session.sessionUrl
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setPayError(errorResponse?.response?.data?.message ?? '無法前往付款頁，請稍後再試')
      setPaying(false)
    }
  }

  if (loading) {
    return <Skeleton className="h-24 w-full" data-testid="booking-payment-loading" />
  }

  if (loadFailed || !state) {
    return (
      <Card data-testid="booking-payment-unavailable">
        <CardContent className="py-4 text-sm text-gray-600">
          暫時無法取得付款資訊。您可以稍後到「我的預訂」查看並完成付款。
        </CardContent>
      </Card>
    )
  }

  if (state.canPay) {
    return (
      <Card className="border-primary/30" data-testid="booking-payment-card">
        <CardHeader>
          <CardTitle className="text-base">付款</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {payError && (
            <Alert variant="destructive" data-testid="booking-pay-error">
              <AlertDescription>{payError}</AlertDescription>
            </Alert>
          )}
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-600">應付金額</span>
            <span className="text-xl font-bold text-gray-900">{formatPrice(totalAmount, currency)}</span>
          </div>
          {state.paymentProvider === 'stripe' ? (
            <>
              <p className="text-xs text-gray-500">將導向 Stripe 安全付款頁完成信用卡付款。</p>
              <div className="flex flex-wrap gap-3">
                <Button data-testid="booking-pay-checkout" onClick={handleStripeCheckout} disabled={paying}>
                  {paying ? '前往付款中…' : '前往付款'}
                </Button>
              </div>
            </>
          ) : (
            <>
              <p className="text-xs text-gray-500">目前為模擬付款（Mock），不會實際扣款。</p>
              <div className="flex flex-wrap gap-3">
                <Button data-testid="booking-pay-mock" onClick={handlePay} disabled={paying}>
                  {paying ? '處理中…' : '確認付款（模擬）'}
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    )
  }

  if (state.paymentStatus === 'SUCCESS' || state.paymentStatus === 'REFUNDED') {
    return (
      <Card className="border-green-200" data-testid="booking-payment-paid">
        <CardHeader>
          <CardTitle className="text-base">付款資訊</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-gray-700 space-y-1">
          <p>
            付款狀態：
            <Badge variant={state.paymentStatus === 'SUCCESS' ? 'success' : 'secondary'}>
              {PAYMENT_STATUS_LABELS[state.paymentStatus] ?? state.paymentStatus}
            </Badge>
          </p>
          {state.transactionId && <p>交易編號：{state.transactionId}</p>}
          {state.paidAt && <p>付款時間：{formatDateTime(state.paidAt)}</p>}
        </CardContent>
      </Card>
    )
  }

  return null
}
