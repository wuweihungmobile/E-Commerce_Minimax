'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Pagination } from '@/components/ui/pagination'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

interface PurchaseOrderSummary {
  id: string
  tenantId: string
  poNumber: string
  status: string
  totalAmount: number
  currency: string
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  rejectionReason?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

interface PurchaseOrderPendingListResponse {
  purchaseOrders: PurchaseOrderSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

const PAGE_SIZE = 20

export default function AdminPurchaseOrdersPage() {
  const [orders, setOrders] = useState<PurchaseOrderSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  const fetchOrders = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)
    try {
      const response = await apiClient.get<ApiResponse<PurchaseOrderPendingListResponse>>(
        API_ENDPOINTS.admin.purchaseOrders.pending,
        { params: { page: page - 1, size: PAGE_SIZE } }
      )
      const data = response.data.data
      setOrders(data?.purchaseOrders || [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入待審批採購單失敗')
      } else {
        setError('載入待審批採購單失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchOrders(currentPage)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage])

  const handleApprove = async (poId: string) => {
    setActionLoadingId(poId)
    try {
      await apiClient.post(API_ENDPOINTS.admin.purchaseOrders.approve(poId))
      fetchOrders(currentPage)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '核准失敗')
      } else {
        alert('核准失敗')
      }
    } finally {
      setActionLoadingId(null)
    }
  }

  const handleReject = async (poId: string) => {
    if (!rejectReason.trim()) {
      alert('請填寫駁回原因')
      return
    }
    setActionLoadingId(poId)
    try {
      await apiClient.post(API_ENDPOINTS.admin.purchaseOrders.reject(poId), { reason: rejectReason })
      setRejectingId(null)
      setRejectReason('')
      fetchOrders(currentPage)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '駁回失敗')
      } else {
        alert('駁回失敗')
      }
    } finally {
      setActionLoadingId(null)
    }
  }

  const formatAmount = (amount: number, currency: string) => {
    return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">{error}</div>
        <Button variant="outline" onClick={() => fetchOrders(currentPage)}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">採購單審批</h1>
        <p className="text-muted-foreground">
          {totalElements > 0 ? <span className="text-warning">待審批採購單 {totalElements} 筆</span> : '目前無待審批採購單'}
        </p>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">載入中...</div>
        </div>
      ) : orders.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500">目前無待審批採購單</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {orders.map((order) => (
            <Card key={order.id} className="border-warning">
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-lg">{order.poNumber}</CardTitle>
                    <CardDescription>
                      租戶 ID：<span className="font-mono text-xs">{order.tenantId}</span>
                    </CardDescription>
                  </div>
                  <Badge variant="warning">待審批</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">金額：</span>
                  <span className="font-bold">{formatAmount(order.totalAmount, order.currency)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">提交時間：</span>
                  <span>{order.submittedAt ? new Date(order.submittedAt).toLocaleString('zh-TW') : '-'}</span>
                </div>
              </CardContent>
              <CardFooter className="flex flex-col gap-4 items-stretch">
                <div className="flex gap-2">
                  <Button
                    variant="default"
                    onClick={() => handleApprove(order.id)}
                    disabled={actionLoadingId === order.id}
                  >
                    {actionLoadingId === order.id ? '處理中...' : '核准'}
                  </Button>
                  <Button
                    variant="destructive"
                    onClick={() => { setRejectingId(order.id); setRejectReason('') }}
                    disabled={actionLoadingId === order.id}
                  >
                    駁回
                  </Button>
                </div>

                {rejectingId === order.id && (
                  <div className="space-y-2 border-t pt-4">
                    <Label htmlFor={`rejectReason-${order.id}`}>駁回原因 *</Label>
                    <Input
                      id={`rejectReason-${order.id}`}
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      placeholder="請輸入駁回原因..."
                    />
                    <div className="flex gap-2">
                      <Button
                        variant="destructive"
                        size="sm"
                        onClick={() => handleReject(order.id)}
                        disabled={actionLoadingId === order.id || !rejectReason.trim()}
                      >
                        確認駁回
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => { setRejectingId(null); setRejectReason('') }}
                        disabled={actionLoadingId === order.id}
                      >
                        取消
                      </Button>
                    </div>
                  </div>
                )}
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      {totalPages > 1 && <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />}
    </div>
  )
}
