'use client'

import { useState, useSyncExternalStore } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { getAuthServerSnapshot, getAuthTenantIdSnapshot, subscribeAuth } from '@/services/authStore'
import tenantService from '@/services/tenant'
import { useAsyncData } from '@/hooks/useAsyncData'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { StatusBadge } from '@/components/ui/status-badge'

export default function TenantList() {
  const router = useRouter()
  const { data, error, loading, reload } = useAsyncData(
    (signal) => tenantService.listMine({ signal }),
    [],
    { errorMessage: '載入店鋪失敗' }
  )
  const tenants = data ?? []
  const storedTenantId = useSyncExternalStore(subscribeAuth, getAuthTenantIdSnapshot, getAuthServerSnapshot)
  // 「切換至此店鋪」只在本頁生效（寫入 localStorage 並在畫面上標示），登入者資料裡的店鋪 id 不變
  const [switchedTenantId, setSwitchedTenantId] = useState<string | null>(null)
  const currentTenantId = switchedTenantId ?? storedTenantId
  const [searchQuery, setSearchQuery] = useState('')

  const handleSwitchTenant = (tenantId: string) => {
    localStorage.setItem('tenantId', tenantId)
    setSwitchedTenantId(tenantId)
    // 觸發頁面重新整理以反映變更
    router.refresh()
  }

  const getBusinessTypeLabel = (type: string) => {
    // Sprint 146：對齊後端 TenantApplicationRequest/TenantUpdateRequest 的 businessType 值域
    const typeLabels: Record<string, string> = {
      RETAIL_ONLY: '零售商城',
      BOOKING_ONLY: '民宿訂房',
      HYBRID: '複合式（零售＋訂房）',
    }
    return typeLabels[type] || type
  }

  const getRoleLabel = (role: string) => {
    const roleLabels: Record<string, string> = {
      STORE_OWNER: '店主',
      STORE_MANAGER: '管理員',
      STORE_STAFF: '員工',
    }
    return roleLabels[role] || role
  }

  const filteredTenants = tenants.filter((tenant) =>
    tenant.storeName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    tenant.businessType.toLowerCase().includes(searchQuery.toLowerCase())
  )

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error) {
    return (
      <Alert variant="destructive">
        {error}
      </Alert>
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
          <Button variant="outline" onClick={reload}>
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
          <EmptyState title="尚無店鋪資料" action={<Link href="/tenant/apply">
              <Button>申請第一個店鋪</Button>
            </Link>} className="h-48" />
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredTenants.map((tenant) => (
            <Card key={tenant.tenantId} className="relative">
              {currentTenantId === tenant.tenantId && (
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
                  <StatusBadge domain="tenant" status={tenant.status} />
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">我的角色：</span>
                  {getRoleLabel(tenant.role)}
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">成員數：</span>
                  {tenant.memberCount}
                </div>
                <div className="text-xs text-muted-foreground">
                  建立時間：{new Date(tenant.createdAt).toLocaleDateString('zh-TW')}
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Link href={`/dashboard/tenants/${tenant.tenantId}`} className="flex-1">
                  <Button variant="outline" className="w-full">
                    查看詳情
                  </Button>
                </Link>
                {tenant.status === 'ACTIVE' && currentTenantId !== tenant.tenantId && (
                  <Button
                    variant="secondary"
                    onClick={() => handleSwitchTenant(tenant.tenantId)}
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
