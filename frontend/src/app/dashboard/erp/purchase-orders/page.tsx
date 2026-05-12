'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import PurchaseOrderService, {
  PurchaseOrderDto,
  POStatus,
} from '@/services/erp/purchaseOrder'
import AuthService from '@/services/auth'

export default function PurchaseOrdersPage() {
  const router = useRouter()
  const [orders, setOrders] = useState<PurchaseOrderDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchOrders()
  }, [router, statusFilter, page])

  const fetchOrders = async () => {
    setLoading(true)
    setError(null)
    try {
      const status = statusFilter ? (statusFilter as POStatus) : undefined
      const response = await PurchaseOrderService.listPurchaseOrders(status, page, 20)
      setOrders(response.content)
      setTotalPages(response.totalPages)
      setTotalElements(response.totalElements)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入採購訂單失敗')
      } else {
        setError('載入採購訂單失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const getStatusBadge = (status: POStatus) => {
    const config: Record<POStatus, { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline' | 'warning'; label: string }> = {
      DRAFT: { variant: 'outline', label: '草稿' },
      SUBMITTED: { variant: 'default', label: '已提交' },
      PARTIAL_RECEIVED: { variant: 'warning', label: '部分到貨' },
      RECEIVED: { variant: 'success', label: '已到貨' },
      CANCELLED: { variant: 'destructive', label: '已取消' },
    }
    const c = config[status] || { variant: 'outline', label: status }
    return <Badge variant={c.variant}>{c.label}</Badge>
  }

  const formatPrice = (price: number, currency: string) => {
    return new Intl.NumberFormat('zh-TW', {
      style: 'currency',
      currency: currency || 'TWD',
    }).format(price)
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
        {error}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between gap-4">
        <div className="flex-1 flex gap-4">
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            className="border rounded-md px-3 py-2"
          >
            <option value="">所有狀態</option>
            <option value="DRAFT">草稿</option>
            <option value="SUBMITTED">已提交</option>
            <option value="PARTIAL_RECEIVED">部分到貨</option>
            <option value="RECEIVED">已到貨</option>
            <option value="CANCELLED">已取消</option>
          </select>
          <Button variant="outline" onClick={fetchOrders}>重新整理</Button>
        </div>
        <Link href="/dashboard/erp/purchase-orders/new">
          <Button>新增採購訂單</Button>
        </Link>
      </div>

      <div className="text-sm text-gray-600">
        共 {totalElements} 筆資料，第 {page + 1} / {totalPages} 頁
      </div>

      {orders.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無採購訂單</p>
            <Link href="/dashboard/erp/purchase-orders/new">
              <Button>建立第一張採購訂單</Button>
            </Link>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {orders.map((order) => (
            <Card key={order.id}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <div>
                    <CardTitle className="text-lg">{order.orderNumber}</CardTitle>
                    <CardDescription>供應商：{order.supplierName}</CardDescription>
                  </div>
                  {getStatusBadge(order.status)}
                </div>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">預計到貨：</span>
                  <span>{order.expectedDeliveryDate ? new Date(order.expectedDeliveryDate).toLocaleDateString('zh-TW') : '-'}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">總金額：</span>
                  <span className="font-bold">{formatPrice(order.totalAmount, order.currency)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">品項數量：</span>
                  <span>{order.items?.length || 0}</span>
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Link href={`/dashboard/erp/purchase-orders/${order.id}`} className="flex-1">
                  <Button variant="outline" className="w-full">檢視</Button>
                </Link>
                {order.status === 'DRAFT' && (
                  <Link href={`/dashboard/erp/purchase-orders/${order.id}/edit`} className="flex-1">
                    <Button variant="outline" className="w-full">編輯</Button>
                  </Link>
                )}
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-6">
          <Button variant="outline" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
            上一頁
          </Button>
          <span className="flex items-center px-4">
            第 {page + 1} 頁，共 {totalPages} 頁
          </span>
          <Button variant="outline" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>
            下一頁
          </Button>
        </div>
      )}
    </div>
  )
}