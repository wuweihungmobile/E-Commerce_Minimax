'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import InventoryService, {
  InventoryDetailDto,
} from '@/services/erp/inventory'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

export default function InventoryDetailPage() {
  const router = useRouter()
  const params = useParams()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [inventory, setInventory] = useState<InventoryDetailDto | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
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

  // Sprint 116（DEF-066）：product_inventory 只有單一低庫存門檻，沒有補貨點／安全庫存之分。
  // 判準與列表頁、後端的 severity 一致：低於門檻一半為危險，低於門檻為低庫存。
  const getStockStatus = (available: number, threshold: number) => {
    if (available <= threshold * 0.5) {
      return { variant: 'destructive' as const, label: '危險', color: 'text-rs-error bg-rs-error/10' }
    }
    if (available <= threshold) {
      return { variant: 'warning' as const, label: '低庫存', color: 'text-rs-ink bg-rs-warning/15' }
    }
    return { variant: 'success' as const, label: '正常', color: 'text-rs-success bg-rs-success/10' }
  }

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleDateString('zh-TW')
  }

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error) {
    return (
      <Alert variant="destructive">
        {error}
      </Alert>
    )
  }

  if (!inventory) {
    return (
      <div className="bg-rs-bg-base border border-rs-hairline px-4 py-3 rounded-md">
        無庫存資料
      </div>
    )
  }

  const status = getStockStatus(inventory.availableQuantity, inventory.lowStockThreshold)

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-semibold text-rs-ink">{inventory.productName}</h2>
          <p className="text-rs-ink-muted">SKU: {inventory.skuCode}</p>
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

          {/* Sprint 116（DEF-066）：資料來源改為 product_inventory 後只有單一低庫存門檻，
              沒有存放位置／補貨點／安全庫存——那三欄過去也一直是空的（來源表沒有任何資料）。 */}
          <div className="grid grid-cols-2 gap-4 mt-6">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">低庫存門檻</p>
              <p className="font-medium">{inventory.lowStockThreshold}</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mt-6">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">最後入庫</p>
              <p className="font-medium">{inventory.lastInboundDate ? formatDate(inventory.lastInboundDate) : '-'}</p>
            </div>
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">最後出庫</p>
              <p className="font-medium">{inventory.lastOutboundDate ? formatDate(inventory.lastOutboundDate) : '-'}</p>
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
            <p className="text-rs-ink-muted text-center py-8">尚無異動記錄</p>
          ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>時間</TableHead>
                    <TableHead>類型</TableHead>
                    <TableHead className="text-right">數量</TableHead>
                    <TableHead>參考單號</TableHead>
                    <TableHead>備註</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {inventory.movements.map((movement) => (
                    <TableRow key={movement.id}>
                      <TableCell className="text-sm text-rs-ink-muted">
                        {formatDate(movement.createdAt)}
                      </TableCell>
                      <TableCell className="text-sm">
                        <Badge variant="outline">{movement.movementType}</Badge>
                      </TableCell>
                      <TableCell className="text-sm text-right font-medium">
                        {movement.quantity > 0 ? '+' : ''}{movement.quantity}
                      </TableCell>
                      <TableCell className="text-sm text-rs-ink-muted">
                        {movement.referenceNumber || '-'}
                      </TableCell>
                      <TableCell className="text-sm text-rs-ink-muted">
                        {movement.notes || '-'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}