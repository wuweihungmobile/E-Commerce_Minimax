'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import OrderService, { type Order } from '@/services/order'
import ReturnService from '@/services/returns'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Label } from '@/components/ui/label'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { apiErrorMessage } from '@/lib/api-error'
import { useAsyncData } from '@/hooks/useAsyncData'

const RETURNABLE_STATUSES = new Set<Order['status']>(['DELIVERED', 'COMPLETED'])

/**
 * 買家提出退貨申請（Sprint 119，DEF-044 前端）。
 * 從訂單詳情頁的「申請退貨」帶入 orderId（query string）。
 */
export default function NewReturnPage() {
  const router = useRouter()

  const [quantities, setQuantities] = useState<Record<string, number>>({})
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const { data: order, error: loadError, loading } = useAsyncData(
    async () => {
      // 於 client 端讀 orderId（避免 useSearchParams 的 Suspense 邊界需求，比照付款回跳頁）
      const orderId = new URLSearchParams(window.location.search).get('orderId')
      if (!orderId) throw new Error('no-order-id')
      return OrderService.getOrder(orderId)
    },
    [],
    {
      errorMessage: ({ message }, err) =>
        err instanceof Error && err.message === 'no-order-id'
          ? '缺少訂單資訊，請由訂單詳情頁提出退貨申請'
          : message || '無法載入訂單詳情，請稍後再試',
    }
  )

  function setQuantity(itemId: string, max: number, value: number) {
    const clamped = Math.max(0, Math.min(max, Math.floor(value) || 0))
    setQuantities((prev) => ({ ...prev, [itemId]: clamped }))
  }

  const selectedItems = order
    ? order.items
        .map((item) => ({ orderItemId: item.id, quantity: quantities[item.id] || 0 }))
        .filter((line) => line.quantity > 0)
    : []

  async function handleSubmit() {
    if (!order || selectedItems.length === 0) {
      setSubmitError('請至少選擇一項商品並填寫退貨數量')
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      const created = await ReturnService.createReturn({
        orderId: order.id,
        reason: reason.trim() || undefined,
        items: selectedItems,
      })
      router.push(`/returns/${created.id}`)
    } catch (err) {
      setSubmitError(apiErrorMessage(err, '提交退貨申請失敗'))
    } finally {
      setSubmitting(false)
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

  if (loadError || !order) {
    return (
      <StorefrontShell>
        <PageTitle className="sr-only">申請退貨</PageTitle>
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{loadError || '找不到此訂單'}</AlertDescription>
        </Alert>
        <Link href="/orders">
          <Button variant="outline">返回我的訂單</Button>
        </Link>
      </StorefrontShell>
    )
  }

  if (!RETURNABLE_STATUSES.has(order.status)) {
    return (
      <StorefrontShell>
        <PageTitle className="sr-only">申請退貨</PageTitle>
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>此訂單目前狀態不允許申請退貨（須為已送達或已完成）</AlertDescription>
        </Alert>
        <Link href={`/orders/${order.id}`}>
          <Button variant="outline">返回訂單詳情</Button>
        </Link>
      </StorefrontShell>
    )
  }

  return (
    <StorefrontShell>
      <div className="mb-6">
        <PageTitle>申請退貨</PageTitle>
        <PageDescription>訂單 #{order.id.slice(0, 8)}</PageDescription>
      </div>

      {submitError && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{submitError}</AlertDescription>
        </Alert>
      )}

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-base">選擇要退貨的商品與數量</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {order.items.map((item) => (
            <div key={item.id} className="flex items-center gap-4 border-b last:border-b-0 pb-4 last:pb-0">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={item.coverImageUrl || '/placeholder.svg'}
                alt={item.listingTitle}
                className="h-14 w-14 rounded object-cover bg-rs-disabled-bg shrink-0"
              />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-rs-ink truncate">{item.listingTitle}</p>
                {item.specName && <p className="text-xs text-rs-ink-muted">{item.specName}</p>}
                <p className="text-xs text-rs-ink-muted">已購買 {item.quantity} 件</p>
              </div>
              <div className="shrink-0 flex items-center gap-2">
                <Label htmlFor={`qty-${item.id}`} className="text-xs text-rs-ink-muted">
                  退貨數量
                </Label>
                <Input
                  id={`qty-${item.id}`}
                  type="number"
                  min={0}
                  max={item.quantity}
                  value={quantities[item.id] || 0}
                  onChange={(e) => setQuantity(item.id, item.quantity, Number(e.target.value))}
                  className="w-16 text-center"
                />
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-base">退貨原因（選填）</CardTitle>
        </CardHeader>
        <CardContent>
          <Textarea
            placeholder="請說明退貨原因，方便店家審核"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={1000}
          />
        </CardContent>
      </Card>

      <div className="flex gap-3">
        <Button onClick={handleSubmit} disabled={submitting || selectedItems.length === 0}>
          {submitting ? '送出中…' : '送出退貨申請'}
        </Button>
        <Link href={`/orders/${order.id}`}>
          <Button variant="outline" disabled={submitting}>
            取消
          </Button>
        </Link>
      </div>
    </StorefrontShell>
  )
}
