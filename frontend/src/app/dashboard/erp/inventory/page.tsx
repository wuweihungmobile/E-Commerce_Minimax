'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import InventoryService, {
  InventoryLedgerDto,
  LowStockAlertDto,
} from '@/services/erp/inventory'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

export default function InventoryPage() {
  const router = useRouter()
  const [inventory, setInventory] = useState<InventoryLedgerDto[]>([])
  const [alerts, setAlerts] = useState<LowStockAlertDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [activeTab, setActiveTab] = useState<'ledger' | 'alerts'>('ledger')

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [ledgerResponse, alertData] = await Promise.all([
        InventoryService.getInventoryLedger(page, 50),
        InventoryService.getLowStockAlerts(),
      ])
      setInventory(ledgerResponse.content)
      setTotalPages(ledgerResponse.totalPages)
      setTotalElements(ledgerResponse.totalElements)
      setAlerts(alertData)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入庫存失敗')
      } else {
        setError('載入庫存失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
    fetchData()
  }, [router, fetchData])

  // Sprint 116（DEF-066）：門檻改用後端實際提供的 lowStockThreshold（product_inventory 只有這一個）。
  // 這個函式原本就存在，但「狀態」欄過去是寫死的 <Badge>正常</Badge>，從來沒有呼叫過它
  // ——庫存見底時畫面照樣顯示正常，與台帳讀空表是同一種「看起來有、其實沒有」的問題。
  const getStockStatus = (available: number, threshold: number) => {
    if (available <= threshold * 0.5) {
      return { variant: 'destructive' as const, label: '危險' }
    }
    if (available <= threshold) {
      return { variant: 'warning' as const, label: '低庫存' }
    }
    return { variant: 'success' as const, label: '正常' }
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
      <div className="flex justify-between items-center">
        <div className="flex gap-4">
          <Button
            variant={activeTab === 'ledger' ? 'default' : 'outline'}
            onClick={() => setActiveTab('ledger')}
          >
            庫存帳查 ({totalElements})
          </Button>
          <Button
            variant={activeTab === 'alerts' ? 'default' : 'outline'}
            onClick={() => setActiveTab('alerts')}
          >
            低庫存警告 ({alerts.length})
          </Button>
        </div>
        <Button variant="outline" onClick={fetchData}>重新整理</Button>
      </div>

      {activeTab === 'ledger' ? (
        <>
          <div className="text-sm text-rs-ink-muted">
            共 {totalElements} 筆資料，第 {page + 1} / {totalPages} 頁
          </div>

          {inventory.length === 0 ? (
            <Card>
              <EmptyState title="尚無庫存資料" className="h-48" />
            </Card>
          ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>SKU</TableHead>
                    <TableHead>品名</TableHead>
                    <TableHead className="text-right">庫存</TableHead>
                    <TableHead className="text-right">可用</TableHead>
                    <TableHead>狀態</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {inventory.map((item) => (
                    <TableRow key={item.skuId}>
                      <TableCell className="text-sm text-rs-ink">{item.skuCode}</TableCell>
                      <TableCell className="text-sm text-rs-ink">{item.productName}</TableCell>
                      <TableCell className="text-sm text-right">{item.quantity}</TableCell>
                      <TableCell className="text-sm text-right">{item.availableQuantity}</TableCell>
                      <TableCell className="text-sm">
                        {(() => {
                          const status = getStockStatus(item.availableQuantity, item.lowStockThreshold)
                          return <Badge variant={status.variant}>{status.label}</Badge>
                        })()}
                      </TableCell>
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
        </>
      ) : (
        <>
          {alerts.length === 0 ? (
            <Card>
              <EmptyState className="h-48" title="目前沒有低庫存警告" description="所有商品庫存都處於安全範圍" />
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {alerts.map((alert) => (
                <Card key={alert.skuId} className="border-rs-warning/40 bg-rs-warning/15">
                  <CardHeader>
                    <CardTitle className="text-lg">{alert.productName}</CardTitle>
                    <CardDescription>SKU: {alert.skuCode}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">目前庫存：</span>
                      <span className="font-bold text-rs-error">{alert.currentQuantity}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">低庫存門檻：</span>
                      <span>{alert.lowStockThreshold}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">嚴重度：</span>
                      <Badge variant={alert.severity === 'CRITICAL' ? 'destructive' : 'warning'}>
                        {alert.severity === 'CRITICAL' ? '危險' : '低庫存'}
                      </Badge>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}