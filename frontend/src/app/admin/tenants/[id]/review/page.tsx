'use client'

import { useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import apiClient from '@/lib/axios'
import AuthService from '@/services/auth'

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

export default function AdminTenantReviewPage() {
  const params = useParams()
  const router = useRouter()
  const tenantId = params.id as string

  const [tenant, setTenant] = useState<Tenant | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchTenantDetail = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<ApiResponse<Tenant>>(
        `/v2/admin/tenants/${tenantId}`
      )
      setTenant(response.data.data)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入店鋪詳情失敗')
      } else {
        setError('載入店鋪詳情失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTenantDetail()
  }, [tenantId])

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

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error || !tenant) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
          {error || '找不到店鋪'}
        </div>
        <Button variant="outline" onClick={() => router.push('/admin/tenants')}>
          返回列表
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">店鋪詳情</h1>
          <p className="text-muted-foreground">檢視既有店鋪資料</p>
        </div>
        <Button variant="outline" onClick={() => router.push('/admin/tenants')}>
          返回列表
        </Button>
      </div>

      {/* Tenant Detail Card */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>{tenant.name}</CardTitle>
            {getStatusBadge(tenant.status)}
          </div>
          <CardDescription>
            申請時間：{new Date(tenant.createdAt).toLocaleDateString('zh-TW', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Reject Reason */}
          {tenant.status === 'TERMINATED' && tenant.rejectReason && (
            <div className="bg-destructive/10 border border-destructive/20 rounded-md p-4">
              <Label className="text-destructive">駁回原因</Label>
              <p className="mt-1">{tenant.rejectReason}</p>
            </div>
          )}
        </CardContent>
        {/*
          🔴 此頁**刻意不提供核准／駁回**（Sprint 109，DEF-060）。
          開店審核的對象是「開店申請」（tenant_applications），不是既有的 Tenant：
          網友申請後 Admin 於 /admin/tenants 的「審核中」分頁核准，後端才建立 Tenant（直接 ACTIVE）。
          生產環境沒有任何路徑會讓 Tenant 進入 PENDING_REVIEW，因此原先掛在這裡、打向
          POST /v2/admin/tenants/{id}/approve 的按鈕**永遠會失敗**（後端拋 E_2005），
          卻讓人誤以為審核入口在此——那正是 DEF-060 的缺陷本體。
          請勿「順手」把審核按鈕加回這一頁。
        */}
      </Card>

    </div>
  )
}