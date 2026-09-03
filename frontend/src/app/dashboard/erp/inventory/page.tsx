'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import InventoryService, {
  InventoryLedgerDto,
  LowStockAlertDto,
} from '@/services/erp/inventory'
import AuthService from '@/services/auth'

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

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchData()
  }, [router, page])

  const fetchData = async () => {
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
  }

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
          <div className="text-sm text-gray-600">
            共 {totalElements} 筆資料，第 {page + 1} / {totalPages} 頁
          </div>

          {inventory.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center justify-center h-48">
                <p className="text-gray-500">尚無庫存資料</p>
              </CardContent>
            </Card>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 border">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">SKU</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">品名</th>
                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">庫存</th>
                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">可用</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">狀態</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {inventory.map((item) => (
                    <tr key={item.skuId}>
                      <td className="px-4 py-3 text-sm text-gray-900">{item.skuCode}</td>
                      <td className="px-4 py-3 text-sm text-gray-900">{item.productName}</td>
                      <td className="px-4 py-3 text-sm text-right">{item.quantity}</td>
                      <td className="px-4 py-3 text-sm text-right">{item.availableQuantity}</td>
                      <td className="px-4 py-3 text-sm">
                        {(() => {
                          const status = getStockStatus(item.availableQuantity, item.lowStockThreshold)
                          return <Badge variant={status.variant}>{status.label}</Badge>
                        })()}
                      </td>
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
        </>
      ) : (
        <>
          {alerts.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center justify-center h-48">
                <p className="text-gray-500 mb-2">目前沒有低庫存警告</p>
                <p className="text-sm text-gray-400">所有商品庫存都處於安全範圍</p>
              </CardContent>
            </Card>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {alerts.map((alert) => (
                <Card key={alert.skuId} className="border-yellow-200 bg-yellow-50">
                  <CardHeader>
                    <CardTitle className="text-lg">{alert.productName}</CardTitle>
                    <CardDescription>SKU: {alert.skuCode}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">目前庫存：</span>
                      <span className="font-bold text-red-600">{alert.currentQuantity}</span>
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