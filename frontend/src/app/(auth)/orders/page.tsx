'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import AuthService from '@/services/auth'
import OrderService, {
  type OrderListItem,
  ORDER_STATUS_LABELS,
  orderStatusBadgeVariant,
} from '@/services/order'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'

const PAGE_SIZE = 10

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

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderListItem[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const result = await OrderService.getOrders({ page, size: PAGE_SIZE, sortBy: 'createdAt', sortDir: 'DESC' })
        if (cancelled) return
        setOrders(result.content)
        setTotalPages(result.totalPages)
        setTotalElements(result.totalElements)
      } catch {
        if (cancelled) return
        setError('無法載入訂單，請稍後再試')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [page])

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <Link href="/" className="text-xl font-bold text-gray-900">
                NextKey
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">我的訂單</span>
            </div>
            <div className="flex items-center">
              <span className="text-sm text-gray-600">
                {AuthService.getCurrentUser()?.email}
              </span>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-5xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">我的訂單</h1>
          <p className="mt-1 text-sm text-gray-600">
            {loading ? '載入中…' : `共 ${totalElements} 筆訂單`}
          </p>
        </div>

        {error && (
          <Alert variant="destructive" className="mb-4">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {loading ? (
          <div className="space-y-4">
            {[0, 1, 2].map((i) => (
              <Card key={i}>
                <CardContent className="py-6">
                  <Skeleton className="h-5 w-40 mb-3" />
                  <Skeleton className="h-4 w-24" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : orders.length === 0 && !error ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <div className="text-5xl mb-4">🧾</div>
              <h2 className="text-lg font-semibold text-gray-900 mb-1">尚無訂單</h2>
              <p className="text-sm text-gray-500 mb-6">您目前還沒有任何訂單</p>
              <Link href="/">
                <Button>去逛逛</Button>
              </Link>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {orders.map((order) => (
              <Link key={order.id} href={`/orders/${order.id}`} className="block">
                <Card className="transition-shadow hover:shadow-md">
                  <CardContent className="py-5">
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm font-medium text-gray-900">
                            訂單 #{order.id.slice(0, 8)}
                          </span>
                          <Badge variant={orderStatusBadgeVariant(order.status)}>
                            {ORDER_STATUS_LABELS[order.status] ?? order.status}
                          </Badge>
                          <Badge variant="outline">
                            {order.orderType === 'ROOM' ? '訂房' : '商品'}
                          </Badge>
                        </div>
                        <p className="text-sm text-gray-500">
                          {formatDateTime(order.createdAt)} · {order.itemCount} 件
                        </p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-lg font-bold text-gray-900">
                          {formatPrice(order.totalAmount, order.currency)}
                        </p>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 mt-6">
            <Button
              variant="outline"
              disabled={page <= 0 || loading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              上一頁
            </Button>
            <span className="text-sm text-gray-600">
              第 {page + 1} / {totalPages} 頁
            </span>
            <Button
              variant="outline"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((p) => p + 1)}
            >
              下一頁
            </Button>
          </div>
        )}
      </main>
    </div>
  )
}
