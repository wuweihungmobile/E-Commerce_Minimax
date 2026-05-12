'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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

interface TenantDetailProps {
  tenantId: string
}

export default function TenantDetail({ tenantId }: TenantDetailProps) {
  const router = useRouter()
  const [tenant, setTenant] = useState<Tenant | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [currentTenantId, setCurrentTenantId] = useState<string | null>(null)

  useEffect(() => {
    const user = AuthService.getCurrentUser()
    if (user?.tenantId) {
      setCurrentTenantId(user.tenantId)
    }
  }, [])

  useEffect(() => {
    if (tenantId) {
      fetchTenantDetail()
    }
  }, [tenantId])

  const fetchTenantDetail = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<ApiResponse<Tenant>>(
        API_ENDPOINTS.tenants.detail(tenantId)
      )
      setTenant(response.data.data)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { status?: number; data?: { message?: string } } }
        if (axiosErr.response?.status === 404) {
          setError('找不到此店鋪')
        } else {
          setError(axiosErr.response?.data?.message || '載入店鋪詳情失敗')
        }
      } else {
        setError('載入店鋪詳情失敗')
      }
    } finally {
      setLoading(false)
    }
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

  const isOwner = currentTenantId === tenantId

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
        <Link href="/dashboard/tenants">
          <Button variant="outline">返回店鋪列表</Button>
        </Link>
      </div>
    )
  }

  if (!tenant) {
    return (
      <div className="space-y-4">
        <div className="text-gray-500">找不到店鋪資料</div>
        <Link href="/dashboard/tenants">
          <Button variant="outline">返回店鋪列表</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Action Buttons */}
      <div className="flex justify-between items-center">
        <Link href="/dashboard/tenants">
          <Button variant="outline">返回列表</Button>
        </Link>
        <div className="flex gap-2">
          {tenant.status === 'APPROVED' && (
            <Badge variant="success" className="text-sm px-3 py-1">
              已核准店鋪
            </Badge>
          )}
          {isOwner && tenant.status === 'APPROVED' && (
            <Link href={`/dashboard/tenants/${tenantId}/edit`}>
              <Button>編輯店鋪</Button>
            </Link>
          )}
        </div>
      </div>

      {/* Tenant Info Card */}
      <Card>
        <CardHeader>
          <div className="flex justify-between items-start">
            <div>
              <CardTitle className="text-2xl">{tenant.storeName}</CardTitle>
              <CardDescription className="mt-2">
                店鋪 ID: <span className="font-mono">{tenant.id}</span>
              </CardDescription>
            </div>
            <div className="flex flex-col items-end gap-2">
              {getStatusBadge(tenant.status)}
              {isOwner && (
                <Badge variant="outline">目前店鋪</Badge>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Basic Info Section */}
          <div>
            <h3 className="text-lg font-semibold mb-4">基本資訊</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1">
                <Label className="text-muted-foreground">營業類型</Label>
                <Input value={getBusinessTypeLabel(tenant.businessType)} readOnly disabled />
              </div>
              <div className="space-y-1">
                <Label className="text-muted-foreground">創建日期</Label>
                <Input value={new Date(tenant.createdAt).toLocaleDateString('zh-TW')} readOnly disabled />
              </div>
            </div>
          </div>

          {/* Contact Info Section */}
          <div>
            <h3 className="text-lg font-semibold mb-4">聯絡資訊</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1">
                <Label className="text-muted-foreground">聯絡信箱</Label>
                <Input value={tenant.contactEmail} readOnly disabled />
              </div>
              <div className="space-y-1">
                <Label className="text-muted-foreground">聯絡電話</Label>
                <Input value={tenant.contactPhone} readOnly disabled />
              </div>
            </div>
          </div>

          {/* Timestamps */}
          <div className="pt-4 border-t">
            <p className="text-xs text-muted-foreground">
              最後更新: {new Date(tenant.updatedAt).toLocaleString('zh-TW')}
            </p>
          </div>
        </CardContent>
        <CardFooter className="flex justify-between border-t pt-6">
          <div className="text-sm text-muted-foreground">
            店鋪狀態說明：
          </div>
          <div className="flex gap-2 text-sm">
            <Badge variant="secondary">審核中</Badge>
            <span className="text-muted-foreground">-</span>
            <Badge variant="success">已核准</Badge>
            <span className="text-muted-foreground">-</span>
            <Badge variant="destructive">已拒絕/已停權</Badge>
          </div>
        </CardFooter>
      </Card>
    </div>
  )
}
