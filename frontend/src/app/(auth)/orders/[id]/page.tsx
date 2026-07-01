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
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Cancel flow
  const [showCancel, setShowCancel] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)

  const load = useCallback(
    async (signal?: { cancelled: boolean }) => {
      setLoading(true)
      setError(null)
      try {
        const [detail, stateLogs] = await Promise.all([
          OrderService.getOrder(orderId),
          OrderService.getOrderLogs(orderId).catch(() => [] as OrderStateLog[]),
        ])
        if (signal?.cancelled) return
        setOrder(detail)
        setLogs(stateLogs)
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

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center gap-4">
              <Link href="/" className="text-xl font-bold text-gray-900">
                NextKey
              </Link>
              <span className="text-gray-400">/</span>
              <Link href="/orders" className="text-gray-600 hover:text-gray-900">
                我的訂單
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">訂單詳情</span>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-4xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
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
              {isCancellable(order.status) && !showCancel && (
                <Button variant="outline" onClick={() => setShowCancel(true)}>
                  取消訂單
                </Button>
              )}
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

            {/* Items */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">訂單項目（{order.items.length}）</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {order.items.map((item) => (
                  <div key={item.id} className="flex items-center gap-4">
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
                ))}

                <div className="border-t pt-4 space-y-1">
                  <div className="flex justify-between text-sm text-gray-600">
                    <span>運費</span>
                    <span>{formatPrice(order.shippingFee, order.currency)}</span>
                  </div>
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
      </main>
    </div>
  )
}
