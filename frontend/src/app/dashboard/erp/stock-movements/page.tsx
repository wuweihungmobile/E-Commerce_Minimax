'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import StockMovementService, {
  INBOUND_MOVEMENT_TYPES,
  StockMovementDto,
  StockMovementType,
} from '@/services/erp/stockMovement'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { PageTitle } from '@/components/ui/page-title'

export default function StockMovementsPage() {
  const router = useRouter()
  const [page, setPage] = useState(0)

  const { data, error, loading, reload } = useAsyncData(
    () => StockMovementService.getStockMovements(page, 50),
    [page],
    { errorMessage: '載入庫存異動失敗' }
  )
  const movements = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

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

  // 來源單據的前綴由 referenceType 決定，讓「PO-2026-0042」與訂單的八碼縮寫不會混淆。
  // 後端只回單據識別、不回中文標籤——顯示用語留在前端。
  const sourceDocumentLabel = (movement: StockMovementDto) => {
    if (movement.referenceType === 'PURCHASE_ORDER') return `採購單 ${movement.sourceDocument}`
    if (movement.referenceType === 'ORDER') return `訂單 #${movement.sourceDocument}`
    return movement.sourceDocument
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

  return (
    <div className="space-y-6">
      <PageTitle className="sr-only">庫存異動</PageTitle>
      <div className="flex justify-between items-center">
        <div className="text-sm text-rs-ink-muted">
          共 {totalElements} 筆異動記錄
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={reload}>重新整理</Button>
          <Button onClick={() => router.push('/dashboard/erp/stock-movements/new')}>
            新增異動
          </Button>
        </div>
      </div>

      {movements.length === 0 ? (
        <Card>
          <EmptyState title="尚無庫存異動記錄" action={<Button onClick={() => router.push('/dashboard/erp/stock-movements/new')}>
              建立第一筆記錄
            </Button>} className="h-48" />
        </Card>
      ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>時間</TableHead>
                <TableHead>類型</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead>品名</TableHead>
                <TableHead className="text-right">數量</TableHead>
                <TableHead>來源單據</TableHead>
                <TableHead>參考單號</TableHead>
                <TableHead>備註</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {movements.map((movement) => (
                <TableRow key={movement.id}>
                  <TableCell className="text-sm text-rs-ink-muted">
                    {new Date(movement.createdAt).toLocaleString('zh-TW')}
                  </TableCell>
                  <TableCell className="text-sm">
                    {getMovementBadge(movement.movementType)}
                  </TableCell>
                  <TableCell className="text-sm text-rs-ink">{movement.skuCode}</TableCell>
                  <TableCell className="text-sm text-rs-ink">{movement.productName}</TableCell>
                  <TableCell className="text-sm text-right font-medium">
                    {/* 依 PRD §6.7.4 的方向表判定，不用字串比對：'OUTBOUND'.endsWith('OUT') 為 false，
                        原本的寫法會把訂單出貨顯示成綠色 +N */}
                    {INBOUND_MOVEMENT_TYPES.includes(movement.movementType)
                      ? <span className="text-rs-success">+{movement.quantity}</span>
                      : <span className="text-rs-error">-{movement.quantity}</span>
                    }
                  </TableCell>
                  <TableCell className="text-sm text-rs-ink-muted">
                    {movement.sourceDocument
                      ? <span className="font-mono text-xs">{sourceDocumentLabel(movement)}</span>
                      : '-'}
                  </TableCell>
                  <TableCell className="text-sm text-rs-ink-muted">{movement.referenceNumber || '-'}</TableCell>
                  <TableCell className="text-sm text-rs-ink-muted">{movement.notes || '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
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