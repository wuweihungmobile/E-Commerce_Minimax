'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
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
}

export default function AdminTenantListPage() {
  const router = useRouter()
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL')

  const fetchTenants = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<ApiResponse<TenantListResponse>>(
        '/v2/admin/tenants?page=0&size=50'
      )
      setTenants(response.data.data?.tenants || [])
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
  }

  useEffect(() => {
    fetchTenants()
  }, [])

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

  const filteredTenants = tenants.filter((tenant) => {
    if (filter === 'ALL') return true
    if (filter === 'PENDING') return tenant.status === 'PENDING_REVIEW'
    if (filter === 'APPROVED') return tenant.status === 'ACTIVE'
    if (filter === 'REJECTED') return tenant.status === 'TERMINATED'
    return true
  })

  const pendingCount = tenants.filter(t => t.status === 'PENDING_REVIEW').length

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
          {error}
        </div>
        <Button variant="outline" onClick={fetchTenants}>
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
            {pendingCount > 0 ? (
              <span className="text-warning">有待審核店鋪 {pendingCount} 間</span>
            ) : (
              '目前無待審核店鋪'
            )}
          </p>
        </div>
        <Button variant="outline" onClick={fetchTenants}>
          重新整理
        </Button>
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2">
        <Button
          variant={filter === 'ALL' ? 'default' : 'outline'}
          size="sm"
          onClick={() => setFilter('ALL')}
        >
          全部 ({tenants.length})
        </Button>
        <Button
          variant={filter === 'PENDING' ? 'default' : 'outline'}
          size="sm"
          onClick={() => setFilter('PENDING')}
        >
          審核中 ({pendingCount})
        </Button>
        <Button
          variant={filter === 'APPROVED' ? 'default' : 'outline'}
          size="sm"
          onClick={() => setFilter('APPROVED')}
        >
          已核准 ({tenants.filter(t => t.status === 'ACTIVE').length})
        </Button>
        <Button
          variant={filter === 'REJECTED' ? 'default' : 'outline'}
          size="sm"
          onClick={() => setFilter('REJECTED')}
        >
          已拒絕 ({tenants.filter(t => t.status === 'TERMINATED').length})
        </Button>
      </div>

      {/* Tenant List */}
      {filteredTenants.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">
              {filter === 'ALL' ? '尚無店鋪資料' : '無符合條件的店鋪'}
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {filteredTenants.map((tenant) => (
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
    </div>
  )
}