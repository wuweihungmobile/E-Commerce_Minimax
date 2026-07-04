'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Pagination } from '@/components/ui/pagination'
import apiClient from '@/lib/axios'

interface AuditLog {
  id: string
  tenantId: string | null
  userId: string | null
  action: string
  entityType: string | null
  entityId: string | null
  oldValue: string | null
  newValue: string | null
  reason: string | null
  createdAt: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

interface AuditLogListResponse {
  logs: AuditLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

const PAGE_SIZE = 20

export default function AdminAuditLogsPage() {
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [actionFilter, setActionFilter] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const fetchAuditLogs = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)

    try {
      const params: Record<string, string | number> = {
        page: page - 1, // 後端 page 為 0-based
        size: PAGE_SIZE,
      }
      if (actionFilter) params.action = actionFilter
      if (startDate) params.startDate = startDate
      if (endDate) params.endDate = endDate

      const response = await apiClient.get<ApiResponse<AuditLogListResponse>>(
        '/v2/admin/audit-logs',
        { params }
      )
      const data = response.data.data
      setLogs(data?.logs || [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入稽核紀錄失敗')
      } else {
        setError('載入稽核紀錄失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [actionFilter, startDate, endDate])

  useEffect(() => {
    fetchAuditLogs(currentPage)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage])

  const handleSearch = () => {
    setCurrentPage(1)
    fetchAuditLogs(1)
  }

  const handleReset = () => {
    setActionFilter('')
    setStartDate('')
    setEndDate('')
    setCurrentPage(1)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">稽核紀錄</h1>
        <p className="text-muted-foreground">
          共 {totalElements} 筆平台管理操作紀錄
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">篩選條件</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div>
              <label className="text-sm text-muted-foreground mb-1 block">操作類型</label>
              <Input
                placeholder="如 TENANT_APPROVED"
                value={actionFilter}
                onChange={(e) => setActionFilter(e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground mb-1 block">開始日期</label>
              <Input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </div>
            <div>
              <label className="text-sm text-muted-foreground mb-1 block">結束日期</label>
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
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">載入中...</div>
        </div>
      ) : error ? (
        <div className="space-y-4">
          <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
            {error}
          </div>
          <Button variant="outline" onClick={() => fetchAuditLogs(currentPage)}>
            重試
          </Button>
        </div>
      ) : logs.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500">無符合條件的稽核紀錄</p>
          </CardContent>
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
