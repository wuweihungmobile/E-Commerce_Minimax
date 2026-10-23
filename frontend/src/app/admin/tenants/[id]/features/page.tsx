'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import AuthService from '@/services/auth'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { loginHref } from '@/lib/auth-redirect'

// DEF-321(e)：PRD §10.1.3 列為 /admin/tenants/[id]/features，先前後端端點
// （GET/PUT /v2/admin/tenants/{id}/features）已存在但零前端呼叫點，本頁補上。
// 欄位形狀比照店主自助頁 dashboard/tenants/[id]/features/page.tsx（同一套
// TenantService.FEATURE_DEFINITIONS 目錄+中繼資料），差異只在：
// (1) 目標租戶來自路由參數而非呼叫者自己的租戶；(2) Admin 按下開關即直接生效，
// 不會進入「待審核」——那是店主自助申請才有的限制，Admin 本身就是審核者。
interface FeatureToggle {
  featureKey: string
  category: string
  featureName: string
  description: string
  isEnabled: boolean
  status: 'ACTIVE' | 'PENDING' | 'INACTIVE'
  requiresAdminReview: boolean
}

interface QuotaInfo {
  featureKey: string
  featureName: string
  limit: number
  currentUsage: number
}

interface FeatureToggleResponse {
  features: FeatureToggle[]
  quotas: QuotaInfo[]
}

export default function AdminTenantFeaturesPage() {
  const params = useParams()
  const router = useRouter()
  const tenantId = params.id as string

  const [features, setFeatures] = useState<FeatureToggle[]>([])
  const [quotas, setQuotas] = useState<QuotaInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [updating, setUpdating] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  useEffect(() => {
    if (tenantId) {
      fetchFeatures()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId])

  const fetchFeatures = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<{ data: FeatureToggleResponse }>(
        API_ENDPOINTS.admin.tenants.features(tenantId)
      )
      setFeatures(response.data.data.features)
      setQuotas(response.data.data.quotas ?? [])
    } catch (err: unknown) {
      console.error('Failed to fetch tenant features:', err)
      setError('載入功能開關失敗，請稍後再試')
    } finally {
      setLoading(false)
    }
  }

  const updateFeatureToggle = async (feature: string, enabled: boolean) => {
    setUpdating(feature)
    setError(null)
    setSuccessMessage(null)

    try {
      await apiClient.put(API_ENDPOINTS.admin.tenants.updateFeature(tenantId, feature), { enabled })

      // Admin 的設定即為最終決定，直接套用為 ACTIVE/INACTIVE（不會進入待審核）
      setFeatures(prev =>
        prev.map(f =>
          f.featureKey === feature ? { ...f, isEnabled: enabled, status: enabled ? 'ACTIVE' : 'INACTIVE' } : f
        )
      )

      setSuccessMessage(`功能「${feature}」已${enabled ? '啟用' : '停用'}`)
      setTimeout(() => setSuccessMessage(null), 3000)
    } catch (err: unknown) {
      console.error('Failed to update tenant feature toggle:', err)
      setError('更新功能開關失敗，請稍後再試')
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

  const getStatusBadge = (status: string, requiresAdminReview: boolean, enabled: boolean) => {
    // 待審核必須先判斷：PENDING 時 isEnabled 本身就是 false（DEF-247 既有設計），
    // 若先判斷 !enabled 會讓「待審核」被誤標成「已停用」，看起來已直接被駁回。
    if (requiresAdminReview && status === 'PENDING') {
      return <Badge variant="warning">待審核</Badge>
    }
    if (!enabled) {
      return <Badge variant="secondary">已停用</Badge>
    }
    if (status === 'ACTIVE') {
      return <Badge variant="success">已啟用</Badge>
    }
    return <Badge variant="secondary">{status}</Badge>
  }

  const pendingFeatures = features.filter(f => f.requiresAdminReview && f.status === 'PENDING')

  const groupedFeatures = features.reduce((acc, feature) => {
    const category = feature.category || 'other'
    if (!acc[category]) {
      acc[category] = []
    }
    acc[category].push(feature)
    return acc
  }, {} as Record<string, FeatureToggle[]>)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">功能開關管理</h1>
          <p className="text-muted-foreground">檢視並設定此店鋪的功能開關</p>
        </div>
        <Button variant="outline" onClick={() => router.push(`/admin/tenants/${tenantId}/review`)}>
          返回店鋪詳情
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {successMessage && (
        <Alert variant="success">
          <AlertDescription>{successMessage}</AlertDescription>
        </Alert>
      )}

      {pendingFeatures.length > 0 && (
        <Alert variant="warning">
          <AlertDescription>
            {pendingFeatures.length} 項功能待審核：
            {pendingFeatures.map(f => f.featureName).join('、')}
          </AlertDescription>
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
          <CardContent className="flex flex-col items-center justify-center py-12">
            <p className="text-gray-500">尚無功能開關設定</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          {quotas.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>數量配額</CardTitle>
                <CardDescription>店鋪目前用量 / 上限</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {quotas.map((quota) => {
                  const ratio = quota.limit > 0 ? Math.min(quota.currentUsage / quota.limit, 1) : 0
                  const isNearLimit = quota.limit > 0 && quota.currentUsage / quota.limit >= 0.9
                  return (
                    <div key={quota.featureKey}>
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-medium text-gray-900">{quota.featureName}</span>
                        <span className={isNearLimit ? 'text-amber-600 font-medium' : 'text-gray-600'}>
                          {quota.currentUsage} / {quota.limit}
                        </span>
                      </div>
                      <div className="mt-1.5 h-2 w-full rounded-full bg-gray-100">
                        <div
                          className={`h-2 rounded-full ${isNearLimit ? 'bg-amber-500' : 'bg-primary'}`}
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
                <CardTitle>{getFeatureCategoryLabel(category)}</CardTitle>
                <CardDescription>{categoryFeatures.length} 項功能</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {categoryFeatures.map(feature => (
                  <div key={feature.featureKey} className="flex items-center justify-between p-4 border rounded-lg">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <Label className="text-base font-medium">{feature.featureName}</Label>
                        {getStatusBadge(feature.status, feature.requiresAdminReview, feature.isEnabled)}
                      </div>
                      <p className="text-sm text-gray-500 mt-1">{feature.description}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <Switch
                        id={feature.featureKey}
                        checked={feature.isEnabled}
                        onCheckedChange={(checked) => updateFeatureToggle(feature.featureKey, checked)}
                        disabled={updating === feature.featureKey}
                      />
                      {updating === feature.featureKey && (
                        <span className="text-sm text-gray-500">更新中...</span>
                      )}
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
