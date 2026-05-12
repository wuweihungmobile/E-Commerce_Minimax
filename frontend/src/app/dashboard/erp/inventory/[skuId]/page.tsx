'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import InventoryService, {
  InventoryDetailDto,
  StockMovementSummary,
} from '@/services/erp/inventory'
import AuthService from '@/services/auth'

export default function InventoryDetailPage() {
  const router = useRouter()
  const params = useParams()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [inventory, setInventory] = useState<InventoryDetailDto | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    const skuId = params.skuId as string
    if (skuId) {
      fetchInventoryDetail(skuId)
    }
  }, [router, params.skuId])

  const fetchInventoryDetail = async (skuId: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await InventoryService.getInventoryDetail(skuId)
      setInventory(data)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入庫存詳情失敗')
      } else {
        setError('載入庫存詳情失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const getStockStatus = (available: number, reorderPoint: number, safetyStock: number) => {
    if (available <= safetyStock) {
      return { variant: 'destructive' as const, label: '危險', color: 'text-red-600 bg-red-100' }
    }
    if (available <= reorderPoint) {
      return { variant: 'warning' as const, label: '低庫存', color: 'text-yellow-600 bg-yellow-100' }
    }
    return { variant: 'success' as const, label: '正常', color: 'text-green-600 bg-green-100' }
  }

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('zh-TW')
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

  if (!inventory) {
    return (
      <div className="bg-gray-50 border border-gray-200 px-4 py-3 rounded-md">
        無庫存資料
      </div>
    )
  }

  const status = getStockStatus(inventory.availableQuantity, inventory.reorderPoint, inventory.safetyStock)

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">{inventory.productName}</h2>
          <p className="text-gray-500">SKU: {inventory.skuCode}</p>
        </div>
        <div className="flex gap-2">
          <Link href="/dashboard/erp/inventory">
            <Button variant="outline">返回列表</Button>
          </Link>
          <Button onClick={() => router.push('/dashboard/erp/stock-movements/new')}>
            新增異動
          </Button>
        </div>
      </div>

      {/* 基本資訊 */}
      <Card>
        <CardHeader>
          <CardTitle>庫存資訊</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">總庫存</p>
              <p className="text-2xl font-bold">{inventory.quantity}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">保留數量</p>
              <p className="text-2xl font-bold">{inventory.reservedQuantity}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">可用數量</p>
              <p className="text-2xl font-bold">{inventory.availableQuantity}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">狀態</p>
              <Badge variant={status.variant}>{status.label}</Badge>
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mt-6">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">存放位置</p>
              <p className="font-medium">{inventory.location || '-'}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">補貨點</p>
              <p className="font-medium">{inventory.reorderPoint}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">安全庫存</p>
              <p className="font-medium">{inventory.safetyStock}</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mt-6">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">最後入庫</p>
              <p className="font-medium">{formatDate(inventory.lastInboundDate)}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">最後出庫</p>
              <p className="font-medium">{formatDate(inventory.lastOutboundDate)}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 異動記錄 */}
      <Card>
        <CardHeader>
          <CardTitle>近期異動記錄</CardTitle>
        </CardHeader>
        <CardContent>
          {inventory.movements.length === 0 ? (
            <p className="text-gray-500 text-center py-8">尚無異動記錄</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 border">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">時間</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">類型</th>
                    <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase">數量</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">參考單號</th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">備註</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {inventory.movements.map((movement) => (
                    <tr key={movement.id}>
                      <td className="px-4 py-2 text-sm text-gray-500">
                        {formatDate(movement.createdAt)}
                      </td>
                      <td className="px-4 py-2 text-sm">
                        <Badge variant="outline">{movement.movementType}</Badge>
                      </td>
                      <td className="px-4 py-2 text-sm text-right font-medium">
                        {movement.quantity > 0 ? '+' : ''}{movement.quantity}
                      </td>
                      <td className="px-4 py-2 text-sm text-gray-500">
                        {movement.referenceNumber || '-'}
                      </td>
                      <td className="px-4 py-2 text-sm text-gray-500">
                        {movement.notes || '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}