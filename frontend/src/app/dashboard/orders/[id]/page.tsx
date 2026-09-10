'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import OrderService, {
  type Order,
  type OrderStatus,
  ORDER_STATUS_LABELS,
  orderStatusBadgeVariant,
} from '@/services/order'
import LogisticsService, {
  type LogisticsResponse,
  type LogisticsProvider,
  LOGISTICS_STATUS_LABELS,
  LOGISTICS_PROVIDER_LABELS,
} from '@/services/logistics'
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

function formatDateTime(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function DashboardOrderDetailPage() {
  const params = useParams()
  const router = useRouter()
  const orderId = params.id as string

  const [order, setOrder] = useState<Order | null>(null)
  const [shipments, setShipments] = useState<LogisticsResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  // 建立物流單表單（訂單 CONFIRMED 時顯示）
  const [showLogisticsForm, setShowLogisticsForm] = useState(false)
  const [provider, setProvider] = useState<LogisticsProvider>('HCT')
  const [receiverName, setReceiverName] = useState('')
  const [receiverPhone, setReceiverPhone] = useState('')
  const [shippingAddress, setShippingAddress] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const detail = await OrderService.getOrder(orderId)
      setOrder(detail)
      if (detail.orderType === 'PRODUCT') {
        const list = await LogisticsService.getByOrder(orderId).catch(() => [])
        setShipments(list)
      } else {
        setShipments([])
      }
    } catch (err) {
      setError(extractErrorMessage(err, '載入訂單詳情失敗'))
    } finally {
      setLoading(false)
    }
  }, [orderId])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    load()
  }, [router, load])

  useEffect(() => {
    if (order) {
      setReceiverName(order.shippingRecipientName ?? '')
      setReceiverPhone(order.shippingPhone ?? '')
      setShippingAddress(order.shippingAddress ?? '')
    }
  }, [order])

  async function transitionTo(targetStatus: OrderStatus, reason: string) {
    setActionLoading(true)
    setActionError(null)
    try {
      const updated = await OrderService.updateOrderStatus(orderId, targetStatus, reason)
      setOrder(updated)
    } catch (err) {
      setActionError(extractErrorMessage(err, '更新訂單狀態失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  async function handleCreateLogistics() {
    setActionLoading(true)
    setActionError(null)
    try {
      await LogisticsService.createLogistics({
        orderId,
        logisticsProvider: provider,
        receiverName: receiverName.trim() || undefined,
        receiverPhone: receiverPhone.trim() || undefined,
        shippingAddress: shippingAddress.trim() || undefined,
      })
      setShowLogisticsForm(false)
      await load()
    } catch (err) {
      setActionError(extractErrorMessage(err, '建立物流單失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error || !order) {
    return (
      <div className="space-y-4 p-6">
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
          {error || '找不到此訂單'}
        </div>
        <Link href="/dashboard/orders">
          <Button variant="outline">返回訂單列表</Button>
        </Link>
      </div>
    )
  }

  const canManageFulfillment = order.orderType === 'PRODUCT'

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <Link href="/dashboard" className="text-gray-600 hover:text-gray-900">
                Dashboard
              </Link>
              <span className="text-gray-400">/</span>
              <Link href="/dashboard/orders" className="text-gray-600 hover:text-gray-900">
                訂單管理
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">#{order.id.slice(0, 8)}</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">{AuthService.getCurrentUser()?.email}</span>
              <button
                onClick={async () => {
                  await AuthService.logout()
                  router.push('/login')
                }}
                className="px-3 py-1.5 text-sm text-white bg-red-500 rounded-md hover:bg-red-600"
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-5xl mx-auto py-6 sm:px-6 lg:px-8 space-y-6">
        <div className="flex items-start justify-between gap-4 px-4 sm:px-0">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 className="text-2xl font-bold text-gray-900">訂單 #{order.id.slice(0, 8)}</h1>
              <Badge variant={orderStatusBadgeVariant(order.status)}>{ORDER_STATUS_LABELS[order.status]}</Badge>
              <Badge variant="outline">{order.orderType === 'ROOM' ? '訂房' : '商品'}</Badge>
            </div>
            <p className="text-sm text-gray-500">建立於 {formatDateTime(order.createdAt)}</p>
          </div>
        </div>

        {actionError && (
          <div className="mx-4 sm:mx-0 bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
            {actionError}
          </div>
        )}

        {/* 出貨作業（僅商品訂單） */}
        {canManageFulfillment && (
          <Card className="mx-4 sm:mx-0 border-primary/30">
            <CardHeader>
              <CardTitle className="text-base">出貨作業</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {order.status === 'PAID' && (
                <div className="flex items-center justify-between">
                  <p className="text-sm text-gray-600">買家已付款，請確認訂單以進入備貨。</p>
                  <Button onClick={() => transitionTo('CONFIRMED', 'Seller confirmed order')} disabled={actionLoading}>
                    {actionLoading ? '處理中…' : '確認訂單'}
                  </Button>
                </div>
              )}

              {order.status === 'CONFIRMED' && !showLogisticsForm && (
                <div className="flex items-center justify-between">
                  <p className="text-sm text-gray-600">訂單已確認，請建立物流單以出貨。</p>
                  <Button onClick={() => setShowLogisticsForm(true)} disabled={actionLoading}>
                    建立物流單
                  </Button>
                </div>
              )}

              {order.status === 'CONFIRMED' && showLogisticsForm && (
                <div className="space-y-4 border-t pt-4">
                  <div className="space-y-2">
                    <Label htmlFor="logistics-provider">物流商</Label>
                    <Select value={provider} onValueChange={(v) => setProvider(v as LogisticsProvider)}>
                      <SelectTrigger id="logistics-provider" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {(Object.entries(LOGISTICS_PROVIDER_LABELS) as Array<[LogisticsProvider, string]>).map(
                          ([value, label]) => (
                            <SelectItem key={value} value={value}>
                              {label}
                            </SelectItem>
                          )
                        )}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="receiver-name">收件人</Label>
                    <Input id="receiver-name" value={receiverName} onChange={(e) => setReceiverName(e.target.value)} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="receiver-phone">收件人電話</Label>
                    <Input id="receiver-phone" value={receiverPhone} onChange={(e) => setReceiverPhone(e.target.value)} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="shipping-address">收件地址</Label>
                    <Input
                      id="shipping-address"
                      value={shippingAddress}
                      onChange={(e) => setShippingAddress(e.target.value)}
                    />
                  </div>
                  <div className="flex gap-2">
                    <Button onClick={handleCreateLogistics} disabled={actionLoading}>
                      {actionLoading ? '建立中…' : '確認建立物流單並出貨'}
                    </Button>
                    <Button variant="outline" onClick={() => setShowLogisticsForm(false)} disabled={actionLoading}>
                      取消
                    </Button>
                  </div>
                </div>
              )}

              {order.status === 'SHIPPING' && (
                <div className="flex items-center justify-between">
                  <p className="text-sm text-gray-600">貨物運送中，買家簽收後請確認送達。</p>
                  <Button onClick={() => transitionTo('DELIVERED', 'Seller marked as delivered')} disabled={actionLoading}>
                    {actionLoading ? '處理中…' : '確認送達'}
                  </Button>
                </div>
              )}

              {order.status === 'DELIVERED' && (
                <div className="flex items-center justify-between">
                  <p className="text-sm text-gray-600">貨物已送達，可標記訂單完成。</p>
                  <Button onClick={() => transitionTo('COMPLETED', 'Seller marked order as completed')} disabled={actionLoading}>
                    {actionLoading ? '處理中…' : '標記完成'}
                  </Button>
                </div>
              )}

              {!['PAID', 'CONFIRMED', 'SHIPPING', 'DELIVERED'].includes(order.status) && (
                <p className="text-sm text-gray-500">此訂單目前狀態無需出貨操作。</p>
              )}

              {shipments.length > 0 && (
                <div className="border-t pt-4 space-y-2">
                  <p className="text-xs font-medium text-gray-500">物流單</p>
                  {shipments.map((s) => (
                    <div key={s.logisticsId} className="text-sm text-gray-700 flex items-center justify-between">
                      <span>
                        {LOGISTICS_PROVIDER_LABELS[s.logisticsProvider]} · {s.trackingNumber}
                      </span>
                      <Badge variant="outline">{LOGISTICS_STATUS_LABELS[s.status]}</Badge>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* Items */}
        <Card className="mx-4 sm:mx-0">
          <CardHeader>
            <CardTitle className="text-base">訂單項目（{order.items.length}）</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {order.items.map((item) => (
              <div key={item.id} className="flex items-center gap-4 border-b last:border-b-0 pb-4 last:pb-0">
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
              {order.discountAmount != null && order.discountAmount > 0 && (
                <div className="flex justify-between text-sm text-green-700">
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

        {/* Recipient info */}
        {(order.shippingRecipientName || order.shippingAddress || order.shippingPhone) && (
          <Card className="mx-4 sm:mx-0">
            <CardHeader>
              <CardTitle className="text-base">收件資訊</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-gray-700 space-y-1">
              {order.shippingRecipientName && <p>收件人：{order.shippingRecipientName}</p>}
              {order.shippingPhone && <p>電話：{order.shippingPhone}</p>}
              {order.shippingAddress && <p>地址：{order.shippingAddress}</p>}
            </CardContent>
          </Card>
        )}

        {order.notes && (
          <Card className="mx-4 sm:mx-0">
            <CardHeader>
              <CardTitle className="text-base">備註</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-gray-700">{order.notes}</CardContent>
          </Card>
        )}
      </main>
    </div>
  )
}
