'use client'

import { useEffect, useState, useCallback } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import ReturnService, {
  type ReturnRequest,
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import OrderService, { type OrderItem } from '@/services/order'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

const CANCELLABLE_STATUSES = new Set<ReturnRequest['status']>(['REQUESTED', 'APPROVED'])

export default function ReturnDetailPage() {
  const params = useParams()
  const returnId = params.id as string

  const [returnRequest, setReturnRequest] = useState<ReturnRequest | null>(null)
  const [orderItems, setOrderItems] = useState<Record<string, OrderItem>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const detail = await ReturnService.getReturn(returnId)
      setReturnRequest(detail)
      // 退貨品項只帶 orderItemId／skuId，補上訂單品項資訊（品名/規格/圖片）供顯示
      const order = await OrderService.getOrder(detail.orderId).catch(() => null)
      if (order) {
        setOrderItems(Object.fromEntries(order.items.map((item) => [item.id, item])))
      }
    } catch (err) {
      setError(extractErrorMessage(err, '無法載入退貨申請詳情'))
    } finally {
      setLoading(false)
    }
  }, [returnId])

  useEffect(() => {
    load()
  }, [load])

  async function handleCancel() {
    setCancelling(true)
    setCancelError(null)
    try {
      const updated = await ReturnService.cancelReturn(returnId)
      setReturnRequest(updated)
    } catch (err) {
      setCancelError(extractErrorMessage(err, '撤回退貨申請失敗'))
    } finally {
      setCancelling(false)
    }
  }

  if (loading) {
    return (
      <StorefrontShell>
        <Skeleton className="h-8 w-52 mb-4" />
        <Card>
          <CardContent className="py-6 space-y-3">
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-2/3" />
          </CardContent>
        </Card>
      </StorefrontShell>
    )
  }

  if (error || !returnRequest) {
    return (
      <StorefrontShell>
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error || '找不到此退貨申請'}</AlertDescription>
        </Alert>
        <Link href="/returns">
          <Button variant="outline">返回退貨申請列表</Button>
        </Link>
      </StorefrontShell>
    )
  }

  const isReceived = returnRequest.status === 'RECEIVED'

  return (
    <StorefrontShell>
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <h1 className="text-2xl font-bold text-gray-900">{returnRequest.returnNumber}</h1>
            <Badge variant={returnStatusBadgeVariant(returnRequest.status)}>
              {RETURN_STATUS_LABELS[returnRequest.status]}
            </Badge>
          </div>
          <p className="text-sm text-gray-500">
            <Link href={`/orders/${returnRequest.orderId}`} className="hover:underline">
              訂單 #{returnRequest.orderId.slice(0, 8)}
            </Link>
            {' · '}
            申請於 {new Date(returnRequest.createdAt).toLocaleString('zh-TW')}
          </p>
        </div>
        {CANCELLABLE_STATUSES.has(returnRequest.status) && (
          <Button variant="outline" onClick={handleCancel} disabled={cancelling}>
            {cancelling ? '撤回中…' : '撤回申請'}
          </Button>
        )}
      </div>

      {cancelError && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{cancelError}</AlertDescription>
        </Alert>
      )}

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-base">退貨品項（{returnRequest.items.length}）</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {returnRequest.items.map((item) => {
            const orderItem = orderItems[item.orderItemId]
            return (
              <div key={item.id} className="flex items-center gap-4 border-b last:border-b-0 pb-4 last:pb-0">
                {orderItem && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={orderItem.coverImageUrl || '/placeholder.png'}
                    alt={orderItem.listingTitle}
                    className="h-14 w-14 rounded object-cover bg-gray-100 shrink-0"
                  />
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-gray-900 truncate">
                    {orderItem?.listingTitle ?? '商品品項'}
                  </p>
                  {orderItem?.specName && <p className="text-xs text-gray-500">{orderItem.specName}</p>}
                  <p className="text-xs text-gray-500">申請退貨 {item.requestedQty} 件</p>
                </div>
                {isReceived && (
                  <div className="text-right text-xs text-gray-600 shrink-0">
                    <p>可售 {item.sellableQty ?? 0}</p>
                    <p>不可售 {item.unsellableQty ?? 0}</p>
                  </div>
                )}
              </div>
            )
          })}
        </CardContent>
      </Card>

      {returnRequest.reason && (
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">退貨原因</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-700 whitespace-pre-wrap">
            {returnRequest.reason}
          </CardContent>
        </Card>
      )}

      {returnRequest.status === 'REJECTED' && returnRequest.rejectionReason && (
        <Card className="mb-6 border-red-200">
          <CardHeader>
            <CardTitle className="text-base">店家駁回原因</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-700 whitespace-pre-wrap">
            {returnRequest.rejectionReason}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">進度</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-gray-700 space-y-1">
          <p>已送出申請：{new Date(returnRequest.createdAt).toLocaleString('zh-TW')}</p>
          {returnRequest.reviewedAt && (
            <p>店家審核：{new Date(returnRequest.reviewedAt).toLocaleString('zh-TW')}</p>
          )}
          {returnRequest.receivedAt && (
            <p>店家收貨確認：{new Date(returnRequest.receivedAt).toLocaleString('zh-TW')}</p>
          )}
        </CardContent>
      </Card>
    </StorefrontShell>
  )
}
