'use client'

import { useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
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
  const [actionLoading, setActionLoading] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [showRejectDialog, setShowRejectDialog] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

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

  const handleApprove = async () => {
    setActionLoading(true)
    try {
      await apiClient.post<ApiResponse<unknown>>(
        `/v2/admin/tenants/${tenantId}/approve`
      )
      setSuccessMessage('店鋪已核准')
      setTimeout(() => router.push('/admin/tenants'), 2000)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '核准失敗')
      } else {
        alert('核准失敗')
      }
    } finally {
      setActionLoading(false)
    }
  }

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      alert('請填寫駁回原因')
      return
    }

    setActionLoading(true)
    try {
      await apiClient.post(
        `/v2/admin/tenants/${tenantId}/reject`,
        { reason: rejectReason }
      )
      setSuccessMessage('店鋪已駁回')
      setTimeout(() => router.push('/admin/tenants'), 2000)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '駁回失敗')
      } else {
        alert('駁回失敗')
      }
    } finally {
      setActionLoading(false)
    }
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
      {/* Success Message */}
      {successMessage && (
        <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded">
          {successMessage}
        </div>
      )}

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">店鋪審核</h1>
          <p className="text-muted-foreground">審核店鋪申請</p>
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
        <CardFooter className="flex gap-4">
          {tenant.status === 'PENDING_REVIEW' && (
            <>
              <Button
                variant="default"
                onClick={handleApprove}
                disabled={actionLoading}
              >
                {actionLoading ? '處理中...' : '核准'}
              </Button>
              <Button
                variant="destructive"
                onClick={() => setShowRejectDialog(true)}
                disabled={actionLoading}
              >
                駁回
              </Button>
            </>
          )}
        </CardFooter>
      </Card>

      {/* Reject Dialog */}
      {showRejectDialog && (
        <Card className="border-destructive">
          <CardHeader>
            <CardTitle>駁回店鋪申請</CardTitle>
            <CardDescription>請填寫駁回原因</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label htmlFor="rejectReason">駁回原因 *</Label>
              <Input
                id="rejectReason"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="請輸入駁回原因..."
                className="mt-1"
              />
            </div>
          </CardContent>
          <CardFooter className="flex gap-4">
            <Button
              variant="destructive"
              onClick={handleReject}
              disabled={actionLoading || !rejectReason.trim()}
            >
              {actionLoading ? '處理中...' : '確認駁回'}
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setShowRejectDialog(false)
                setRejectReason('')
              }}
              disabled={actionLoading}
            >
              取消
            </Button>
          </CardFooter>
        </Card>
      )}
    </div>
  )
}