'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Pagination } from '@/components/ui/pagination'
import adminService, { type AuditLogQuery } from '@/services/admin'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Label } from '@/components/ui/label'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

const PAGE_SIZE = 20

interface AppliedFilters {
  action: string
  startDate: string
  endDate: string
}

export default function AdminAuditLogsPage() {
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用

  // 輸入框的內容與「已套用的篩選條件」分開：輸入時不重抓，按「查詢」或「清除」才套用（每次套用都是新物件，即使內容相同也會重抓）
  const [actionFilter, setActionFilter] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [applied, setApplied] = useState<AppliedFilters>({ action: '', startDate: '', endDate: '' })

  const { data, error, loading, reload } = useAsyncData(
    (signal) => {
      const query: AuditLogQuery = {
        page: currentPage - 1, // 後端 page 為 0-based
        size: PAGE_SIZE,
      }
      if (applied.action) query.action = applied.action
      if (applied.startDate) query.startDate = applied.startDate
      if (applied.endDate) query.endDate = applied.endDate
      return adminService.listAuditLogs(query, { signal })
    },
    [currentPage, applied],
    { errorMessage: '載入稽核紀錄失敗' }
  )
  const logs = data?.logs || []
  const totalPages = data?.totalPages || 1
  const totalElements = data?.totalElements || 0

  const handleSearch = () => {
    setApplied({ action: actionFilter, startDate, endDate })
    setCurrentPage(1)
  }

  const handleReset = () => {
    setActionFilter('')
    setStartDate('')
    setEndDate('')
    setApplied({ action: '', startDate: '', endDate: '' })
    setCurrentPage(1)
  }

  return (
    <div className="space-y-6">
      <div>
        <PageTitle>稽核紀錄</PageTitle>
        <PageDescription>
          共 {totalElements} 筆平台管理操作紀錄
        </PageDescription>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">篩選條件</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div>
              <Label className="text-muted-foreground mb-1 block">操作類型</Label>
              <Input
                placeholder="如 TENANT_APPROVED"
                value={actionFilter}
                onChange={(e) => setActionFilter(e.target.value)}
              />
            </div>
            <div>
              <Label className="text-muted-foreground mb-1 block">開始日期</Label>
              <Input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>
            <div>
              <Label className="text-muted-foreground mb-1 block">結束日期</Label>
              <Input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={handleSearch}>查詢</Button>
              <Button variant="outline" onClick={handleReset}>
                清除
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {loading ? (
        <LoadingState />
      ) : error ? (
        <div className="space-y-4">
          <Alert variant="destructive">
            {error}
          </Alert>
          <Button variant="outline" onClick={reload}>
            重試
          </Button>
        </div>
      ) : logs.length === 0 ? (
        <Card>
          <EmptyState title="無符合條件的稽核紀錄" className="h-48" />
        </Card>
      ) : (
        <div className="space-y-3">
          {logs.map((log) => (
            <Card key={log.id}>
              <CardContent className="py-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-semibold">{log.action}</span>
                  <span className="text-sm text-muted-foreground">
                    {new Date(log.createdAt).toLocaleString('zh-TW')}
                  </span>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm text-muted-foreground">
                  {log.entityType && (
                    <div>
                      對象：{log.entityType}
                      {log.entityId ? ` (${log.entityId})` : ''}
                    </div>
                  )}
                  {log.tenantId && <div>租戶 ID：{log.tenantId}</div>}
                  {log.userId && <div>操作者 ID：{log.userId}</div>}
                  {log.reason && <div>原因：{log.reason}</div>}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />
    </div>
  )
}
