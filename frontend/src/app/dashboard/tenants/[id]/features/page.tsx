'use client'

import { useEffect, useState, useSyncExternalStore } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import tenantService, { type FeatureToggle } from '@/services/tenant'
import { useAsyncData } from '@/hooks/useAsyncData'
import { getAuthRoleSnapshot, getAuthServerSnapshot, getAuthTenantIdSnapshot, subscribeAuth } from '@/services/authStore'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { StatusBadge } from '@/components/ui/status-badge'
import { featureStatusEntry } from '@/lib/status'

export default function FeatureTogglePage() {
  const router = useRouter()
  // 角色與店鋪一律從 auth store 讀（SSR／hydration 前是 null）；只有店主與管理員能開這一頁，其他角色回店鋪管理
  const role = useSyncExternalStore(subscribeAuth, getAuthRoleSnapshot, getAuthServerSnapshot)
  const tenantId = useSyncExternalStore(subscribeAuth, getAuthTenantIdSnapshot, getAuthServerSnapshot)
  const canManage = role === 'STORE_OWNER' || role === 'ADMIN'
  useEffect(() => {
    if (role !== null && !canManage) router.push('/dashboard/tenants')
  }, [role, canManage, router])

  // 原本載入失敗固定顯示這句；「更新開關」失敗也共用同一個 error。hook 只管載入，動作失敗另外記在 actionError
  const { data, error: loadError, loading, setData } = useAsyncData(
    (signal) => tenantService.getMyFeatures({ signal }),
    [],
    { enabled: canManage && Boolean(tenantId), errorMessage: () => '載入功能開關失敗，請稍後再試' }
  )
  const features = data?.features ?? []
  const quotas = data?.quotas ?? []
  const [updating, setUpdating] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const error = actionError ?? loadError
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const updateFeatureToggle = async (feature: string, enabled: boolean) => {
    if (!tenantId) return

    setUpdating(feature)
    setActionError(null)
    setSuccessMessage(null)

    try {
      const { newState, status } = await tenantService.updateMyFeature(feature, enabled)

      // DEF-428：用後端回應的結果更新畫面，不是用「我送出的要求」。需審核的功能送出啟用申請後，後端的 newState 仍是 false、
      // status 是 PENDING_APPROVAL（待平台審核）；先前直接把畫面改成已啟用並提示「已啟用」，重新整理後開關又變回關閉。
      const pendingApproval = enabled && status === 'PENDING_APPROVAL'
      setData(prev =>
        prev && {
          ...prev,
          features: prev.features.map(f =>
            f.featureKey === feature
              ? {
                  ...f,
                  isEnabled: newState,
                  status: pendingApproval ? ('PENDING' as const) : newState ? ('ACTIVE' as const) : ('INACTIVE' as const),
                }
              : f
          ),
        }
      )

      setSuccessMessage(
        pendingApproval
          ? `功能「${feature}」已送出啟用申請，待平台審核後生效`
          : `功能「${feature}」已${newState ? '啟用' : '停用'}`
      )
      setTimeout(() => setSuccessMessage(null), 3000)
    } catch (err: unknown) {
      console.error('Failed to update feature toggle:', err)
      setActionError('更新功能開關失敗，請稍後再試')
    } finally {
      setUpdating(null)
    }
  }

  const getFeatureCategoryLabel = (category: string) => {
    const labels: Record<string, string> = {
      listing: '商品管理',
      booking: '預訂管理',
      cms: '內容管理',
      erp: 'ERP 整合',
      promo: '促銷活動',
      pricing: '動態定價',
    }
    return labels[category] || category
  }

  // Group features by category
  const groupedFeatures = features.reduce((acc, feature) => {
    const category = feature.category || 'other'
    if (!acc[category]) {
      acc[category] = []
    }
    acc[category].push(feature)
    return acc
  }, {} as Record<string, FeatureToggle[]>)

  if (!canManage || !tenantId) {
    return (
      <div>
        <main>
          <div>
            <LoadingState />
          </div>
        </main>
      </div>
    )
  }

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            {/* Sprint 328：原本頁面自己的頂欄麵包屑（店鋪管理 / 店鋪詳情 / 功能開關）是從這裡回到店鋪詳情的唯一出口，
                頂欄改由共用外殼提供後，補回這個返回連結（側欄只有『店鋪管理』列表，要多點一層才回得到詳情）。 */}
            <Link href={`/dashboard/tenants/${tenantId}`} className="text-sm text-rs-primary hover:underline">
              ← 返回店鋪詳情
            </Link>
            <PageTitle className="mt-2">功能開關</PageTitle>
            <PageDescription>
              管理店鋪的功能開啟狀態。部分功能需要管理員審核後才會生效。
            </PageDescription>
          </div>

          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {successMessage && (
            <Alert variant="success" className="mb-4">
              <AlertDescription>{successMessage}</AlertDescription>
            </Alert>
          )}

          {loading ? (
            <div className="space-y-4">
              {[1, 2, 3].map(i => (
                <Card key={i}>
                  <CardHeader>
                    <Skeleton className="h-6 w-48" />
                    <Skeleton className="h-4 w-32 mt-2" />
                  </CardHeader>
                  <CardContent>
                    <Skeleton className="h-12 w-full" />
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : features.length === 0 ? (
            <Card>
              <EmptyState title="尚無功能開關設定" action={<Button variant="outline" onClick={() => router.push('/dashboard/tenants')}>
                  返回店鋪列表
                </Button>} />
            </Card>
          ) : (
            <div className="space-y-6">
              {quotas.length > 0 && (
                <Card>
                  <CardHeader>
                    <CardTitle>數量配額</CardTitle>
                    <CardDescription className="mt-1">店鋪目前用量 / 上限</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    {quotas.map((quota) => {
                      const ratio = quota.limit > 0 ? Math.min(quota.currentUsage / quota.limit, 1) : 0
                      const isNearLimit = quota.limit > 0 && quota.currentUsage / quota.limit >= 0.9
                      return (
                        <div key={quota.featureKey}>
                          <div className="flex items-center justify-between text-sm">
                            <span className="font-medium text-rs-ink">{quota.featureName}</span>
                            <span className={isNearLimit ? 'text-rs-ink font-medium' : 'text-rs-ink-muted'}>
                              {quota.currentUsage} / {quota.limit}
                            </span>
                          </div>
                          <div className="mt-1.5 h-2 w-full rounded-full bg-rs-disabled-bg">
                            <div
                              className={`h-2 rounded-full ${isNearLimit ? 'bg-rs-warning' : 'bg-primary'}`}
                              style={{ width: `${ratio * 100}%` }}
                            />
                          </div>
                        </div>
                      )
                    })}
                  </CardContent>
                </Card>
              )}

              {Object.entries(groupedFeatures).map(([category, categoryFeatures]) => (
                <Card key={category}>
                  <CardHeader>
                    <div className="flex justify-between items-center">
                      <div>
                        <CardTitle>{getFeatureCategoryLabel(category)}</CardTitle>
                        <CardDescription className="mt-1">
                          {categoryFeatures.length} 項功能
                        </CardDescription>
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    {categoryFeatures.map(feature => (
                      <div
                        key={feature.featureKey}
                        className="flex items-center justify-between p-4 border rounded-lg"
                      >
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <Label className="text-base font-medium">
                              {feature.featureName}
                            </Label>
                            <StatusBadge entry={featureStatusEntry(feature.status, feature.requiresAdminReview, feature.isEnabled)} />
                          </div>
                          <p className="text-sm text-rs-ink-muted mt-1">
                            {feature.description}
                          </p>
                          {feature.requiresAdminReview && (
                            <p className="text-xs text-rs-ink mt-1">
                              啟用後需要管理員審核才會生效
                            </p>
                          )}
                        </div>
                        <div className="flex items-center gap-3">
                          <Switch
                            id={feature.featureKey}
                            checked={feature.isEnabled}
                            onCheckedChange={(checked) => updateFeatureToggle(feature.featureKey, checked)}
                            disabled={updating === feature.featureKey}
                          />
                          {updating === feature.featureKey && (
                            <span className="text-sm text-rs-ink-muted">更新中...</span>
                          )}
                        </div>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              ))}

              <div className="bg-rs-tertiary border border-rs-secondary rounded-lg p-4">
                <h3 className="text-sm font-medium text-rs-primary">功能開關說明</h3>
                <ul className="mt-2 text-sm text-rs-primary space-y-1">
                  <li>• <strong>可直接啟用</strong>：立即生效，無需審核</li>
                  <li>• <strong>需要審核</strong>：啟用後狀態為「待審核」，需 Admin 確認後才會生效</li>
                  <li>• <strong>停用</strong>：功能立即停用</li>
                </ul>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}