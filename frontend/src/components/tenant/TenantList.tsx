'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import AuthService from '@/services/auth'

// Tenant status enum
type TenantStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'

// Tenant interface
interface Tenant {
  id: string
  storeName: string
  businessType: string
  contactEmail: string
  contactPhone: string
  status: TenantStatus
  createdAt: string
  updatedAt: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export default function TenantList() {
  const router = useRouter()
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [currentTenantId, setCurrentTenantId] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')

  useEffect(() => {
    const user = AuthService.getCurrentUser()
    if (user?.tenantId) {
      setCurrentTenantId(user.tenantId)
    }
  }, [])

  useEffect(() => {
    fetchTenants()
  }, [])

  const fetchTenants = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<ApiResponse<Tenant[]>>(
        API_ENDPOINTS.tenants.list
      )
      setTenants(response.data.data || [])
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入店鋪失敗')
      } else {
        setError('載入店鋪失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleSwitchTenant = (tenantId: string) => {
    localStorage.setItem('tenantId', tenantId)
    setCurrentTenantId(tenantId)
    // 觸發頁面重新整理以反映變更
    router.refresh()
  }

  const getStatusBadge = (status: TenantStatus) => {
    const statusConfig: Record<TenantStatus, { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline'; label: string }> = {
      PENDING: { variant: 'secondary', label: '審核中' },
      APPROVED: { variant: 'success', label: '已核准' },
      REJECTED: { variant: 'destructive', label: '已拒絕' },
      SUSPENDED: { variant: 'destructive', label: '已停權' },
    }
    const config = statusConfig[status] || { variant: 'outline', label: status }
    return <Badge variant={config.variant}>{config.label}</Badge>
  }

  const getBusinessTypeLabel = (type: string) => {
    const typeLabels: Record<string, string> = {
      RETAIL: '零售',
      WHOLESALE: '批發',
      'F&B': '餐飲',
      SERVICE: '服務業',
      MANUFACTURING: '製造業',
      OTHER: '其他',
    }
    return typeLabels[type] || type
  }

  const filteredTenants = tenants.filter((tenant) =>
    tenant.storeName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    tenant.businessType.toLowerCase().includes(searchQuery.toLowerCase())
  )

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
        {error}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header Actions */}
      <div className="flex flex-col sm:flex-row justify-between gap-4">
        <div className="flex-1 max-w-md">
          <Input
            type="search"
            placeholder="搜尋店鋪..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full"
          />
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => fetchTenants()}>
            重新整理
          </Button>
          <Link href="/tenant/apply">
            <Button>申請新店鋪</Button>
          </Link>
        </div>
      </div>

      {/* Current Tenant Banner */}
      {currentTenantId && (
        <div className="bg-primary/5 border border-primary/20 rounded-lg p-4">
          <p className="text-sm text-muted-foreground">
            目前選定的店鋪 ID: <span className="font-mono font-medium">{currentTenantId}</span>
          </p>
        </div>
      )}

      {/* Tenant List */}
      {filteredTenants.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無店鋪資料</p>
            <Link href="/tenant/apply">
              <Button>申請第一個店鋪</Button>
            </Link>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredTenants.map((tenant) => (
            <Card key={tenant.id} className="relative">
              {currentTenantId === tenant.id && (
                <div className="absolute top-2 right-2">
                  <Badge variant="default">目前店鋪</Badge>
                </div>
              )}
              <CardHeader>
                <CardTitle className="text-lg">{tenant.storeName}</CardTitle>
                <CardDescription>
                  {getBusinessTypeLabel(tenant.businessType)}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="text-sm">
                  <span className="text-muted-foreground">狀態：</span>
                  {getStatusBadge(tenant.status)}
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">聯絡信箱：</span>
                  {tenant.contactEmail}
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">聯絡電話：</span>
                  {tenant.contactPhone}
                </div>
                <div className="text-xs text-muted-foreground">
                  建立時間：{new Date(tenant.createdAt).toLocaleDateString('zh-TW')}
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Link href={`/dashboard/tenants/${tenant.id}`} className="flex-1">
                  <Button variant="outline" className="w-full">
                    查看詳情
                  </Button>
                </Link>
                {tenant.status === 'APPROVED' && currentTenantId !== tenant.id && (
                  <Button
                    variant="secondary"
                    onClick={() => handleSwitchTenant(tenant.id)}
                  >
                    切換至此店鋪
                  </Button>
                )}
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
