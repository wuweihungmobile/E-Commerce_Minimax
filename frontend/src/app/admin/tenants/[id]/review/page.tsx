'use client'

import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import adminService from '@/services/admin'
import { useAsyncData } from '@/hooks/useAsyncData'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { StatusBadge } from '@/components/ui/status-badge'

export default function AdminTenantReviewPage() {
  const params = useParams()
  const router = useRouter()
  const tenantId = params.id as string

  const { data: tenant, error, loading } = useAsyncData(
    (signal) => adminService.getTenant(tenantId, { signal }),
    [tenantId],
    { errorMessage: '載入店鋪詳情失敗' }
  )

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error || !tenant) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">
          {error || '找不到店鋪'}
        </Alert>
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
          <PageTitle>店鋪詳情</PageTitle>
          <PageDescription>檢視既有店鋪資料</PageDescription>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => router.push(`/admin/tenants/${tenantId}/features`)}>
            功能開關
          </Button>
          <Button variant="outline" onClick={() => router.push('/admin/tenants')}>
            返回列表
          </Button>
        </div>
      </div>

      {/* Tenant Detail Card */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>{tenant.name}</CardTitle>
            <StatusBadge domain="tenant" status={tenant.status} />
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
            <Alert variant="destructive">
              <Label className="text-rs-error">駁回原因</Label>
              <p className="mt-1 text-rs-ink">{tenant.rejectReason}</p>
            </Alert>
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