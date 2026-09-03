'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import OrderService, {
  type Order,
  type OrderStateLog,
  ORDER_STATUS_LABELS,
  orderStatusBadgeVariant,
  isCancellable,
} from '@/services/order'
import OrderPaymentService, { type OrderPaymentState } from '@/services/payment'
import ReviewService from '@/services/review'
import ReviewForm, { type ReviewFormValue } from '@/components/reviews/ReviewForm'
import LogisticsService, {
  type TrackingDetail,
  LOGISTICS_STATUS_LABELS,
  LOGISTICS_PROVIDER_LABELS,
} from '@/services/logistics'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

function formatDateTime(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function statusLabel(status: string) {
  return ORDER_STATUS_LABELS[status as keyof typeof ORDER_STATUS_LABELS] ?? status
}

export default function OrderDetailPage() {
  const params = useParams()
  const orderId = params.id as string

  const [order, setOrder] = useState<Order | null>(null)
  const [logs, setLogs] = useState<OrderStateLog[]>([])
  const [payment, setPayment] = useState<OrderPaymentState | null>(null)
  const [tracking, setTracking] = useState<TrackingDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Cancel flow
  const [showCancel, setShowCancel] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)

  // Payment flow (Mock)
  const [paying, setPaying] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)

  // Review flow（已完成商品訂單，逐項）
  const [reviewOpenItemId, setReviewOpenItemId] = useState<string | null>(null)
  const [reviewedItemIds, setReviewedItemIds] = useState<Set<string>>(new Set())

  const load = useCallback(
    async (signal?: { cancelled: boolean }) => {
      setLoading(true)
      setError(null)
      try {
        const [detail, stateLogs, paymentState] = await Promise.all([
          OrderService.getOrder(orderId),
          OrderService.getOrderLogs(orderId).catch(() => [] as OrderStateLog[]),
          OrderPaymentService.getPaymentState(orderId).catch(() => null),
        ])
        if (signal?.cancelled) return
        setOrder(detail)
        setLogs(stateLogs)
        setPayment(paymentState)

        // 物流追蹤（僅商品訂單；無出貨回空陣列）
        if (detail.orderType === 'PRODUCT') {
          const shipments = await LogisticsService.getByOrder(orderId).catch(() => [])
          const trackingDetail =
            shipments.length > 0
              ? await LogisticsService.getTrackingDetail(shipments[0].logisticsId).catch(() => null)
              : null
          if (signal?.cancelled) return
          setTracking(trackingDetail)
        } else {
          setTracking(null)
        }
      } catch {
        if (signal?.cancelled) return
        setError('無法載入訂單詳情，請稍後再試')
      } finally {
        if (!signal?.cancelled) setLoading(false)
      }
    },
    [orderId]
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
      const updated = await OrderService.cancelOrder(orderId, cancelReason.trim() || undefined)
      setOrder(updated)
      setShowCancel(false)
      setCancelReason('')
      // Refresh logs to reflect the new CANCELLED state transition
      const stateLogs = await OrderService.getOrderLogs(orderId).catch(() => [] as OrderStateLog[])
      setLogs(stateLogs)
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setCancelError(errorResponse?.response?.data?.message ?? '取消訂單失敗，此訂單目前狀態可能無法取消')
    } finally {
      setCancelling(false)
    }
  }

  const handlePay = async () => {
    setPaying(true)
    setPayError(null)
    try {
      await OrderPaymentService.pay(orderId)
      await load()
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setPayError(errorResponse?.response?.data?.message ?? '付款失敗，請稍後再試')
    } finally {
      setPaying(false)
    }
  }

  const handlePayFail = async () => {
    setPaying(true)
    setPayError(null)
    try {
      await OrderPaymentService.payFail(orderId, '使用者模擬付款失敗')
      await load()
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setPayError(errorResponse?.response?.data?.message ?? '操作失敗，請稍後再試')
    } finally {
      setPaying(false)
    }
  }

  // 真實金流 Phase A（AI-2410）：建立 Stripe Checkout Session 並重導至託管付款頁
  const handleStripeCheckout = async () => {
    setPaying(true)
    setPayError(null)
    try {
      const session = await OrderPaymentService.createCheckoutSession(orderId)
      window.location.href = session.sessionUrl
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } }
      setPayError(errorResponse?.response?.data?.message ?? '無法前往付款頁，請稍後再試')
      setPaying(false)
    }
  }

  const submitReview = async (listingId: string, itemId: string, value: ReviewFormValue) => {
    await ReviewService.createReview({
      listingId,
      orderId,
      rating: value.rating,
      title: value.title,
      content: value.content,
      isAnonymous: value.isAnonymous,
    })
    setReviewedItemIds((prev) => new Set(prev).add(itemId))
    setReviewOpenItemId(null)
  }

  // 商品訂單送達/完成後可評價（後端仍以「一訂單一評價」為最終權威）
  const canReview =
    order != null && order.orderType === 'PRODUCT' && (order.status === 'DELIVERED' || order.status === 'COMPLETED')

  // 商品訂單送達/完成後可申請退貨（後端 ReturnRequestService.RETURNABLE_ORDER_STATUSES 為最終權威）
  const canReturn =
    order != null && order.orderType === 'PRODUCT' && (order.status === 'DELIVERED' || order.status === 'COMPLETED')

  return (
    <StorefrontShell>
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
        ) : order ? (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <h1 className="text-2xl font-bold text-gray-900">
                    訂單 #{order.id.slice(0, 8)}
                  </h1>
                  <Badge variant={orderStatusBadgeVariant(order.status)}>
                    {statusLabel(order.status)}
                  </Badge>
                  <Badge variant="outline">{order.orderType === 'ROOM' ? '訂房' : '商品'}</Badge>
                </div>
                <p className="text-sm text-gray-500">建立於 {formatDateTime(order.createdAt)}</p>
              </div>
              <div className="flex gap-2 shrink-0">
                {(payment?.canCancel ?? isCancellable(order.status)) && !showCancel && (
                  <Button variant="outline" onClick={() => setShowCancel(true)}>
                    取消訂單
                  </Button>
                )}
                {canReturn && (
                  <Link href={`/returns/new?orderId=${order.id}`}>
                    <Button variant="outline">申請退貨</Button>
                  </Link>
                )}
              </div>
            </div>

            {/* Cancel panel */}
            {showCancel && (
              <Card className="border-red-200">
                <CardHeader>
                  <CardTitle className="text-base">取消訂單</CardTitle>
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
                      placeholder="例如：改變心意、重複下單"
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

            {/* Payment (Mock) */}
            {payment?.canPay && (
              <Card className="border-primary/30">
                <CardHeader>
                  <CardTitle className="text-base">付款</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {payError && (
                    <Alert variant="destructive">
                      <AlertDescription>{payError}</AlertDescription>
                    </Alert>
                  )}
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-gray-600">應付金額</span>
                    <span className="text-xl font-bold text-gray-900">
                      {formatPrice(order.totalAmount, order.currency)}
                    </span>
                  </div>
                  {payment?.paymentProvider === 'stripe' ? (
                    <>
                      {/* 真實金流 Phase A（AI-2410）：重導至 Stripe 託管付款頁 */}
                      <p className="text-xs text-gray-500">將導向 Stripe 安全付款頁完成信用卡付款。</p>
                      <div className="flex flex-wrap gap-3">
                        <Button data-testid="order-pay-checkout" onClick={handleStripeCheckout} disabled={paying}>
                          {paying ? '前往付款中…' : '前往付款'}
                        </Button>
                      </div>
                    </>
                  ) : (
                    <>
                      <p className="text-xs text-gray-500">目前為模擬付款（Mock），不會實際扣款。</p>
                      <div className="flex flex-wrap gap-3">
                        <Button onClick={handlePay} disabled={paying}>
                          {paying ? '處理中…' : '確認付款（模擬）'}
                        </Button>
                        <Button variant="outline" onClick={handlePayFail} disabled={paying}>
                          模擬付款失敗
                        </Button>
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>
            )}

            {payment?.paymentStatus === 'SUCCESS' && (
              <Card className="border-green-200">
                <CardHeader>
                  <CardTitle className="text-base">付款資訊</CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-gray-700 space-y-1">
                  <p>
                    付款狀態：<Badge variant="success">付款成功</Badge>
                  </p>
                  {payment.transactionId && <p>交易編號：{payment.transactionId}</p>}
                  {payment.paidAt && <p>付款時間：{formatDateTime(payment.paidAt)}</p>}
                </CardContent>
              </Card>
            )}

            {/* Items */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">訂單項目（{order.items.length}）</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {order.items.map((item) => (
                  <div key={item.id} className="border-b last:border-b-0 pb-4 last:pb-0">
                    <div className="flex items-center gap-4">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={item.coverImageUrl || '/placeholder.png'}
                        alt={item.listingTitle}
                        className="h-16 w-16 rounded object-cover bg-gray-100 shrink-0"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-gray-900 truncate">{item.listingTitle}</p>
                        {item.specName && <p className="text-xs text-gray-500">{item.specName}</p>}
                        <p className="text-xs text-gray-500">
                          {formatPrice(item.unitPrice, order.currency)} × {item.quantity}
                        </p>
                      </div>
                      <div className="text-sm font-semibold text-gray-900 shrink-0">
                        {formatPrice(item.subtotal, order.currency)}
                      </div>
                    </div>

                    {/* 評價操作（商品訂單） */}
                    <div className="flex items-center gap-3 mt-2 pl-20">
                      <Link
                        href={`/reviews/product/${item.listingId}`}
                        className="text-xs text-primary hover:underline"
                      >
                        查看評價
                      </Link>
                      {canReview &&
                        (reviewedItemIds.has(item.id) ? (
                          <span className="text-xs text-green-600">已送出評價 ✓</span>
                        ) : reviewOpenItemId === item.id ? null : (
                          <button
                            type="button"
                            className="text-xs text-primary hover:underline"
                            onClick={() => setReviewOpenItemId(item.id)}
                          >
                            撰寫評價
                          </button>
                        ))}
                    </div>

                    {canReview && reviewOpenItemId === item.id && (
                      <div className="mt-3 pl-20">
                        <ReviewForm
                          onSubmit={(value) => submitReview(item.listingId, item.id, value)}
                          onCancel={() => setReviewOpenItemId(null)}
                        />
                      </div>
                    )}
                  </div>
                ))}

                <div className="border-t pt-4 space-y-1">
                  <div className="flex justify-between text-sm text-gray-600">
                    <span>運費</span>
                    <span>{formatPrice(order.shippingFee, order.currency)}</span>
                  </div>
                  {/* Sprint 100：買家在購物車看到折扣，訂單也須看得到用了哪張券、折了多少 */}
                  {order.discountAmount != null && order.discountAmount > 0 && (
                    <div className="flex justify-between text-sm text-green-700" data-testid="order-discount">
                      <span>優惠折扣{order.promoCode ? `（${order.promoCode}）` : ''}</span>
                      <span>-{formatPrice(order.discountAmount, order.currency)}</span>
                    </div>
                  )}
                  <div className="flex justify-between text-base font-bold text-gray-900">
                    <span>訂單總額</span>
                    <span>{formatPrice(order.totalAmount, order.currency)}</span>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Recipient / Guest info */}
            {order.orderType === 'ROOM' ? (
              (order.guestName || order.guestPhone || order.guestEmail) && (
                <Card>
                  <CardHeader>
                    <CardTitle className="text-base">訂房資料</CardTitle>
                  </CardHeader>
                  <CardContent className="text-sm text-gray-700 space-y-1">
                    {order.guestName && <p>入住人：{order.guestName}</p>}
                    {order.guestCount != null && <p>入住人數：{order.guestCount}</p>}
                    {order.guestPhone && <p>電話：{order.guestPhone}</p>}
                    {order.guestEmail && <p>Email：{order.guestEmail}</p>}
                  </CardContent>
                </Card>
              )
            ) : (
              (order.shippingRecipientName || order.shippingAddress || order.shippingPhone) && (
                <Card>
                  <CardHeader>
                    <CardTitle className="text-base">收件資訊</CardTitle>
                  </CardHeader>
                  <CardContent className="text-sm text-gray-700 space-y-1">
                    {order.shippingRecipientName && <p>收件人：{order.shippingRecipientName}</p>}
                    {order.shippingPhone && <p>電話：{order.shippingPhone}</p>}
                    {order.shippingAddress && <p>地址：{order.shippingAddress}</p>}
                  </CardContent>
                </Card>
              )
            )}

            {/* 物流追蹤（商品訂單） */}
            {order.orderType === 'PRODUCT' && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">物流追蹤</CardTitle>
                </CardHeader>
                <CardContent>
                  {tracking ? (
                    <div className="space-y-4">
                      <div className="text-sm text-gray-700 space-y-1">
                        <p>
                          物流商：{LOGISTICS_PROVIDER_LABELS[tracking.logisticsProvider] ?? tracking.logisticsProvider}
                        </p>
                        <p>物流單號：{tracking.trackingNumber}</p>
                        <p>
                          目前狀態：
                          <span className="font-medium">
                            {LOGISTICS_STATUS_LABELS[tracking.currentStatus] ?? tracking.currentStatus}
                          </span>
                        </p>
                      </div>
                      {tracking.events.length > 0 && (
                        <ol className="space-y-4 border-t pt-4">
                          {tracking.events.map((event, idx) => (
                            <li key={`${event.status}-${idx}`} className="flex gap-3">
                              <div className="flex flex-col items-center">
                                <div
                                  className={
                                    'h-2.5 w-2.5 rounded-full mt-1.5 ' +
                                    (idx === tracking.events.length - 1 ? 'bg-primary' : 'bg-gray-300')
                                  }
                                />
                              </div>
                              <div className="flex-1">
                                <p className="text-sm text-gray-900">
                                  {event.description || (LOGISTICS_STATUS_LABELS[event.status] ?? event.status)}
                                </p>
                                <p className="text-xs text-gray-500">
                                  {event.location ? event.location + ' · ' : ''}
                                  {formatDateTime(event.eventTime)}
                                </p>
                              </div>
                            </li>
                          ))}
                        </ol>
                      )}
                    </div>
                  ) : (
                    <p className="text-sm text-gray-500">尚未出貨，物流資訊將於出貨後顯示。</p>
                  )}
                </CardContent>
              </Card>
            )}

            {order.notes && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">備註</CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-gray-700">{order.notes}</CardContent>
              </Card>
            )}

            {/* State log timeline */}
            {logs.length > 0 && (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">訂單狀態紀錄</CardTitle>
                </CardHeader>
                <CardContent>
                  <ol className="space-y-4">
                    {logs.map((log) => (
                      <li key={log.id} className="flex gap-3">
                        <div className="flex flex-col items-center">
                          <div className="h-2.5 w-2.5 rounded-full bg-gray-400 mt-1.5" />
                        </div>
                        <div className="flex-1">
                          <p className="text-sm text-gray-900">
                            {log.fromStatus ? `${statusLabel(log.fromStatus)} → ` : ''}
                            <span className="font-medium">{statusLabel(log.toStatus)}</span>
                          </p>
                          <p className="text-xs text-gray-500">{formatDateTime(log.createdAt)}</p>
                          {log.reason && <p className="text-xs text-gray-500">原因：{log.reason}</p>}
                        </div>
                      </li>
                    ))}
                  </ol>
                </CardContent>
              </Card>
            )}
          </div>
        ) : (
          !error && (
            <Card>
              <CardContent className="flex flex-col items-center justify-center py-16">
                <p className="text-gray-500 mb-4">找不到此訂單</p>
                <Link href="/orders">
                  <Button variant="outline">返回訂單列表</Button>
                </Link>
              </CardContent>
            </Card>
          )
        )}
    </StorefrontShell>
  )
}
