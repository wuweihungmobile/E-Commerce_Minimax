'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import StockMovementService, {
  INBOUND_MOVEMENT_TYPES,
  StockMovementDto,
  StockMovementType,
} from '@/services/erp/stockMovement'
import AuthService from '@/services/auth'

export default function StockMovementsPage() {
  const router = useRouter()
  const [movements, setMovements] = useState<StockMovementDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchMovements()
  }, [router, page])

  const fetchMovements = async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await StockMovementService.getStockMovements(page, 50)
      setMovements(response.content)
      setTotalPages(response.totalPages)
      setTotalElements(response.totalElements)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入庫存異動失敗')
      } else {
        setError('載入庫存異動失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  // 涵蓋 PRD §6.7.4 全部型別：列表除了手動異動，也會顯示採購收貨與訂單流程產生的流水帳，
  // 少一型就會 fallback 成裸英文代碼（DEF-063 之前 INBOUND 以外的系統型別都是這樣顯示的）。
  const getMovementBadge = (type: StockMovementType) => {
    const config: Record<StockMovementType, { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline' | 'warning'; label: string }> = {
      INBOUND: { variant: 'success', label: '採購入庫' },
      OUTBOUND: { variant: 'destructive', label: '訂單出貨' },
      RESERVE: { variant: 'outline', label: '訂單預留' },
      RELEASE: { variant: 'outline', label: '取消釋放' },
      ADJUST_PLUS: { variant: 'default', label: '盤盈調整' },
      ADJUST_MINUS: { variant: 'warning', label: '盤虧調整' },
      TRANSFER_IN: { variant: 'success', label: '調撥入庫' },
      TRANSFER_OUT: { variant: 'destructive', label: '調撥出庫' },
      SCRAP: { variant: 'secondary', label: '報廢出庫' },
      RETURN: { variant: 'outline', label: '退貨' },
    }
    const c = config[type] || { variant: 'outline' as const, label: type }
    return <Badge variant={c.variant}>{c.label}</Badge>
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
      <div className="flex justify-between items-center">
        <div className="text-sm text-gray-600">
          共 {totalElements} 筆異動記錄
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={fetchMovements}>重新整理</Button>
          <Button onClick={() => router.push('/dashboard/erp/stock-movements/new')}>
            新增異動
          </Button>
        </div>
      </div>

      {movements.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無庫存異動記錄</p>
            <Button onClick={() => router.push('/dashboard/erp/stock-movements/new')}>
              建立第一筆記錄
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 border">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">時間</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">類型</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">SKU</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">品名</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">數量</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">參考單號</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">備註</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {movements.map((movement) => (
                <tr key={movement.id}>
                  <td className="px-4 py-3 text-sm text-gray-500">
                    {new Date(movement.createdAt).toLocaleString('zh-TW')}
                  </td>
                  <td className="px-4 py-3 text-sm">
                    {getMovementBadge(movement.movementType)}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-900">{movement.skuCode}</td>
                  <td className="px-4 py-3 text-sm text-gray-900">{movement.productName}</td>
                  <td className="px-4 py-3 text-sm text-right font-medium">
                    {/* 依 PRD §6.7.4 的方向表判定，不用字串比對：'OUTBOUND'.endsWith('OUT') 為 false，
                        原本的寫法會把訂單出貨顯示成綠色 +N */}
                    {INBOUND_MOVEMENT_TYPES.includes(movement.movementType)
                      ? <span className="text-green-600">+{movement.quantity}</span>
                      : <span className="text-red-600">-{movement.quantity}</span>
                    }
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">{movement.referenceNumber || '-'}</td>
                  <td className="px-4 py-3 text-sm text-gray-500">{movement.notes || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
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