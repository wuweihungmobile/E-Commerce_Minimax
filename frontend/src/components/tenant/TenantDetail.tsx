'use client'

import { useSyncExternalStore } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  getAuthRoleSnapshot,
  getAuthServerSnapshot,
  getAuthTenantIdSnapshot,
  subscribeAuth,
} from '@/services/authStore'
import tenantService from '@/services/tenant'
import { useAsyncData } from '@/hooks/useAsyncData'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { StatusBadge } from '@/components/ui/status-badge'

interface TenantDetailProps {
  tenantId: string
}

export default function TenantDetail({ tenantId }: TenantDetailProps) {
  const { data: tenant, error, loading } = useAsyncData(
    (signal) => tenantService.getById(tenantId, { signal }),
    [tenantId],
    {
      enabled: Boolean(tenantId),
      errorMessage: ({ status, message }) => (status === 404 ? '找不到此店鋪' : message || '載入店鋪詳情失敗'),
    }
  )
  const currentTenantId = useSyncExternalStore(subscribeAuth, getAuthTenantIdSnapshot, getAuthServerSnapshot)
  const currentRole = useSyncExternalStore(subscribeAuth, getAuthRoleSnapshot, getAuthServerSnapshot)

  const getBusinessTypeLabel = (type: string) => {
    // Sprint 146：對齊後端 TenantApplicationRequest/TenantUpdateRequest 的 businessType 值域
    const typeLabels: Record<string, string> = {
      RETAIL_ONLY: '零售商城',
      BOOKING_ONLY: '民宿訂房',
      HYBRID: '複合式（零售＋訂房）',
    }
    return typeLabels[type] || type
  }

  const isOwner = currentTenantId === tenantId
  // DEF-438：編輯（PUT /v2/tenants/{id}）與功能開關頁都只放行店主與管理員，一般成員（STORE_STAFF）看得到詳情但操作不了
  // （按儲存只會得 403 E-1007），所以兩個入口共用同一條角色條件，避免兩邊各寫一份又漂移
  const canManageStore = isOwner && (currentRole === 'STORE_OWNER' || currentRole === 'ADMIN')

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">
          {error}
        </Alert>
        <Link href="/dashboard/tenants">
          <Button variant="outline">返回店鋪列表</Button>
        </Link>
      </div>
    )
  }

  if (!tenant) {
    return (
      <div className="space-y-4">
        <div className="text-rs-ink-muted">找不到店鋪資料</div>
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
          {tenant.status === 'ACTIVE' && (
            <Badge variant="success" className="text-sm px-3 py-1">
              已核准店鋪
            </Badge>
          )}
          {/* DEF-430：功能開關頁（features/page.tsx）只放行店主與管理員，其他成員進去會被導回店鋪列表，所以不給入口 */}
          {canManageStore && tenant.status === 'ACTIVE' && (
            <Link href={`/dashboard/tenants/${tenantId}/features`}>
              <Button variant="outline">功能開關</Button>
            </Link>
          )}
          {/* Sprint 315（DEF-432③）：FRD US-M17-003 邊界條件『店鋪狀態非 ACTIVE：允許更新』，所以編輯入口不看店鋪狀態；
              Sprint 316（DEF-438）：但要看角色——店員進得了表單，按儲存卻只會得 403 */}
          {canManageStore && (
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
                店鋪 ID: <span className="font-mono">{tenant.tenantId}</span>
              </CardDescription>
            </div>
            <div className="flex flex-col items-end gap-2">
              <StatusBadge domain="tenant" status={tenant.status} />
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
                <Input value={tenant.contactPhone || '未提供'} readOnly disabled />
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
            <Badge variant="destructive">已拒絕/已停權/已終止</Badge>
          </div>
        </CardFooter>
      </Card>
    </div>
  )
}
