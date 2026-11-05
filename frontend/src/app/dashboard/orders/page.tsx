'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Pagination } from '@/components/ui/pagination'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import OrderService, {
  type OrderListItem,
  type OrderStatus,
  ORDER_STATUS_LABELS,
  orderStatusBadgeVariant,
} from '@/services/order'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

const PAGE_SIZE = 20

// Radix Select 不接受空字串當 value，故以 ALL 代表「全部狀態」（呼叫 API 時轉為不帶 status 參數）
const STATUS_FILTER_OPTIONS: Array<{ value: 'ALL' | OrderStatus; label: string }> = [
  { value: 'ALL', label: '全部狀態' },
  ...(Object.entries(ORDER_STATUS_LABELS) as Array<[OrderStatus, string]>).map(([value, label]) => ({
    value,
    label,
  })),
]

function formatPrice(amount: number, currency: string) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
}

export default function DashboardOrdersPage() {
  const router = useRouter()

  const [orders, setOrders] = useState<OrderListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // 初始值 ALL；掛載後於 client 端讀 status query string 覆蓋（避免 useSearchParams 的
  // Suspense 邊界需求，比照 (auth)/returns/new 讀 orderId 的既有慣例）
  const [status, setStatus] = useState<'ALL' | OrderStatus>('ALL')
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  const fetchOrders = useCallback(async (page: number, statusFilter: 'ALL' | OrderStatus) => {
    setLoading(true)
    setError(null)
    try {
      const result = await OrderService.getTenantOrders({
        page: page - 1,
        size: PAGE_SIZE,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
      })
      setOrders(result.content)
      setTotalPages(result.totalPages || 1)
      setTotalElements(result.totalElements || 0)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr?.response?.data?.message || '載入訂單列表失敗')
    } finally {
      setLoading(false)
    }
  }, [])

  // 掛載時讀 ?status= 帶入初始篩選（例如儀表板「待出貨」統計連結過來）
  useEffect(() => {
    const fromQuery = new URLSearchParams(window.location.search).get('status') as OrderStatus | null
    if (fromQuery && fromQuery in ORDER_STATUS_LABELS) {
      setStatus(fromQuery)
    }
  }, [])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
    fetchOrders(currentPage, status)
  }, [router, fetchOrders, currentPage, status])

  return (
    <div>
      <main>
        <div className="space-y-6">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div>
              <h1 className="text-2xl font-semibold text-rs-ink">訂單管理</h1>
              <p className="mt-1 text-sm text-rs-ink-muted">
                {totalElements > 0 ? `共 ${totalElements} 筆訂單` : '目前尚無訂單'}
              </p>
            </div>
            <Select
              value={status}
              onValueChange={(value) => {
                setStatus(value as 'ALL' | OrderStatus)
                setCurrentPage(1)
              }}
            >
              <SelectTrigger className="w-40">
                <SelectValue placeholder="全部狀態" />
              </SelectTrigger>
              <SelectContent>
                {STATUS_FILTER_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {error && (
            <div className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded-md">{error}</div>
          )}

          {loading ? (
            <div className="flex items-center justify-center h-64">
              <div className="text-rs-ink-muted">載入中...</div>
            </div>
          ) : orders.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center justify-center h-48">
                <p className="text-rs-ink-muted">
                  {status === 'ALL' ? '尚無訂單' : `尚無「${ORDER_STATUS_LABELS[status]}」狀態的訂單`}
                </p>
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-3">
              {orders.map((order) => (
                <Link key={order.id} href={`/dashboard/orders/${order.id}`}>
                  <Card className="hover:border-primary transition-colors">
                    <CardContent className="py-4 flex items-center justify-between gap-4">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="font-mono text-sm text-rs-ink">
                            #{order.id.slice(0, 8)}
                          </span>
                          <Badge variant={orderStatusBadgeVariant(order.status)}>
                            {ORDER_STATUS_LABELS[order.status]}
                          </Badge>
                        </div>
                        <p className="text-xs text-rs-ink-muted">
                          {order.shippingRecipientName ? `收件人：${order.shippingRecipientName} · ` : ''}
                          {order.itemCount} 項商品 · {new Date(order.createdAt).toLocaleString('zh-TW')}
                        </p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-sm font-semibold text-rs-ink">
                          {formatPrice(order.totalAmount, order.currency)}
                        </p>
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
          )}

          {totalPages > 1 && <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />}
        </div>
      </main>
    </div>
  )
}
