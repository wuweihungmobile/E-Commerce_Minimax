'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Pagination } from '@/components/ui/pagination'
import apiClient from '@/lib/axios'

// Tenant interface - matches actual API response
interface Tenant {
  tenantId: string
  name: string
  status: 'PENDING_REVIEW' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'
  userCount?: number
  listingCount?: number
  createdAt: string
  updatedAt: string
  rejectReason?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

interface TenantListResponse {
  tenants: Tenant[]
  totalCount: number
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type FilterTab = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'

const FILTER_TO_STATUS: Record<FilterTab, Tenant['status'] | undefined> = {
  ALL: undefined,
  PENDING: 'PENDING_REVIEW',
  APPROVED: 'ACTIVE',
  REJECTED: 'TERMINATED',
}

const PAGE_SIZE = 20

export default function AdminTenantListPage() {
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<FilterTab>('ALL')
  const [keyword, setKeyword] = useState('')
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  // 各分頁籤的數量，伺服器端篩選下需分別查詢才能同時顯示
  const [tabCounts, setTabCounts] = useState<Record<FilterTab, number>>({
    ALL: 0,
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })

  const fetchTenants = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)

    try {
      const params: Record<string, string | number> = {
        page: page - 1, // 後端 page 為 0-based
        size: PAGE_SIZE,
      }
      const status = FILTER_TO_STATUS[filter]
      if (status) params.status = status
      if (keyword) params.keyword = keyword

      const response = await apiClient.get<ApiResponse<TenantListResponse>>('/v2/admin/tenants', { params })
      const data = response.data.data
      setTenants(data?.tenants || [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements ?? data?.totalCount ?? 0)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入店鋪列表失敗')
      } else {
        setError('載入店鋪列表失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [filter, keyword])

  const fetchTabCounts = useCallback(async () => {
    try {
      const tabs: FilterTab[] = ['ALL', 'PENDING', 'APPROVED', 'REJECTED']
      const results = await Promise.all(
        tabs.map((tab) => {
          const status = FILTER_TO_STATUS[tab]
          const params: Record<string, string | number> = { page: 0, size: 1 }
          if (status) params.status = status
          return apiClient.get<ApiResponse<TenantListResponse>>('/v2/admin/tenants', { params })
        })
      )
      const counts = {} as Record<FilterTab, number>
      tabs.forEach((tab, i) => {
        const data = results[i].data.data
        counts[tab] = data?.totalElements ?? data?.totalCount ?? 0
      })
      setTabCounts(counts)
    } catch (err) {
      console.error('Failed to load tab counts:', err)
    }
  }, [])

  useEffect(() => {
    fetchTabCounts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    fetchTenants(currentPage)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, filter, keyword])

  const handleFilterChange = (tab: FilterTab) => {
    setFilter(tab)
    setCurrentPage(1)
  }

  const refresh = () => {
    fetchTenants(currentPage)
    fetchTabCounts()
  }

  const getStatusBadge = (status: Tenant['status']) => {
    const statusConfig: Record<Tenant['status'], { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline'; label: string }> = {
      PENDING_REVIEW: { variant: 'secondary', label: '審核中' },
      ACTIVE: { variant: 'success', label: '已核准' },
      SUSPENDED: { variant: 'destructive', label: '已停權' },
      TERMINATED: { variant: 'destructive', label: '已終止' },
    }
    const config = statusConfig[status] || { variant: 'outline', label: status }
    return <Badge variant={config.variant}>{config.label}</Badge>
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
          {error}
        </div>
        <Button variant="outline" onClick={refresh}>
          重試
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">店鋪管理</h1>
          <p className="text-muted-foreground">
            {tabCounts.PENDING > 0 ? (
              <span className="text-warning">有待審核店鋪 {tabCounts.PENDING} 間</span>
            ) : (
              '目前無待審核店鋪'
            )}
          </p>
        </div>
        <Button variant="outline" onClick={refresh}>
          重新整理
        </Button>
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2">
        <Button
          variant={filter === 'ALL' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('ALL')}
        >
          全部 ({tabCounts.ALL})
        </Button>
        <Button
          variant={filter === 'PENDING' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('PENDING')}
        >
          審核中 ({tabCounts.PENDING})
        </Button>
        <Button
          variant={filter === 'APPROVED' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('APPROVED')}
        >
          已核准 ({tabCounts.APPROVED})
        </Button>
        <Button
          variant={filter === 'REJECTED' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('REJECTED')}
        >
          已拒絕 ({tabCounts.REJECTED})
        </Button>
      </div>

      {/* Keyword Search */}
      <div className="flex gap-2 items-end">
        <div className="flex-1 max-w-xs">
          <label className="text-sm text-muted-foreground mb-1 block">搜尋店鋪名稱</label>
          <Input
            placeholder="輸入店鋪名稱或 slug"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setCurrentPage(1)
            }}
          />
        </div>
      </div>

      {/* Tenant List */}
      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">載入中...</div>
        </div>
      ) : tenants.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">
              {filter === 'ALL' ? '尚無店鋪資料' : '無符合條件的店鋪'}
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {tenants.map((tenant) => (
            <Card key={tenant.tenantId} className={tenant.status === 'PENDING_REVIEW' ? 'border-warning' : ''}>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-lg">{tenant.name}</CardTitle>
                    <CardDescription>
                      申請時間：{new Date(tenant.createdAt).toLocaleDateString('zh-TW')}
                    </CardDescription>
                  </div>
                  {getStatusBadge(tenant.status)}
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                  <div>
                    <span className="text-muted-foreground">店鋪 ID：</span>
                    <span className="font-mono text-xs">{tenant.tenantId}</span>
                  </div>
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Link href={`/admin/tenants/${tenant.tenantId}/review`}>
                  <Button variant="outline" size="sm">
                    審核詳情
                  </Button>
                </Link>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      {totalElements > 0 && (
        <p className="text-sm text-muted-foreground text-center">共 {totalElements} 間店鋪</p>
      )}
      <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />
    </div>
  )
}
