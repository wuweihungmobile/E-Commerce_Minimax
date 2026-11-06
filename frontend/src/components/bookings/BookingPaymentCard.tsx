'use client'

import { useState } from 'react'
import BookingPaymentService from '@/services/bookingPayment'
import { PAYMENT_STATUS_LABELS } from '@/services/payment'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { apiErrorMessage } from '@/lib/api-error'
import { useAsyncData } from '@/hooks/useAsyncData'

interface BookingPaymentCardProps {
  bookingId: string
  /** 應付金額（訂房總額）；付款狀態端點不回金額，由呼叫端帶入 */
  totalAmount: number
  currency: string
  /** 付款期限（Sprint 225，DEF-311）；有值才顯示「請於…前完成付款」。歷史訂房沒有期限（null／未帶）。 */
  paymentDueAt?: string | null
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
export default function BookingPaymentCard({
  bookingId,
  totalAmount,
  currency,
  paymentDueAt,
  onPaid,
}: BookingPaymentCardProps) {
  const { data: state, error: loadError, loading, setData: setState } = useAsyncData(
    () => BookingPaymentService.getPaymentState(bookingId),
    [bookingId],
    { errorMessage: '載入付款資訊失敗' }
  )
  const loadFailed = loadError !== null
  const [paying, setPaying] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)

  const handlePay = async () => {
    setPaying(true)
    setPayError(null)
    try {
      setState(await BookingPaymentService.pay(bookingId))
      onPaid?.()
    } catch (err: unknown) {
      setPayError(apiErrorMessage(err, '付款失敗，請稍後再試'))
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
      setPayError(apiErrorMessage(err, '無法前往付款頁，請稍後再試'))
      setPaying(false)
    }
  }

  if (loading) {
    return <Skeleton className="h-24 w-full" data-testid="booking-payment-loading" />
  }

  if (loadFailed || !state) {
    return (
      <Card data-testid="booking-payment-unavailable">
        <CardContent className="py-4 text-sm text-rs-ink-muted">
          暫時無法取得付款資訊。您可以稍後到「我的預訂」查看並完成付款。
        </CardContent>
      </Card>
    )
  }

  // Sprint 242：店鋪暫停營業時不能付款（後端回 E-2010）；仍可取消預訂
  if (state.canPay && state.storeOpen === false) {
    return (
      <Card className="border-rs-warning/40" data-testid="booking-store-closed">
        <CardHeader>
          <CardTitle className="text-base">店鋪暫停營業</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-rs-ink">
          <p>此店鋪目前暫停營業，暫時無法付款。</p>
          <p>您可以取消這筆預訂；若店鋪恢復營業，這裡會再出現付款按鈕。</p>
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
            <span className="text-sm text-rs-ink-muted">應付金額</span>
            <span className="text-xl font-bold text-rs-ink">{formatPrice(totalAmount, currency)}</span>
          </div>
          {paymentDueAt && (
            <p className="text-sm text-rs-ink" data-testid="booking-payment-due">
              請於 {formatDateTime(paymentDueAt)} 前完成付款，逾時預訂將自動取消並釋出日期。
            </p>
          )}
          {state.paymentProvider === 'stripe' ? (
            <>
              <p className="text-xs text-rs-ink-muted">將導向 Stripe 安全付款頁完成信用卡付款。</p>
              <div className="flex flex-wrap gap-3">
                <Button data-testid="booking-pay-checkout" onClick={handleStripeCheckout} disabled={paying}>
                  {paying ? '前往付款中…' : '前往付款'}
                </Button>
              </div>
            </>
          ) : (
            <>
              <p className="text-xs text-rs-ink-muted">目前為模擬付款（Mock），不會實際扣款。</p>
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
      <Card className="border-rs-success/30" data-testid="booking-payment-paid">
        <CardHeader>
          <CardTitle className="text-base">付款資訊</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-rs-ink space-y-1">
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
