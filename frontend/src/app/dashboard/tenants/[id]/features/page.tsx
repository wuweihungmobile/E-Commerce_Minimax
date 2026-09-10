'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
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

// 欄位名必須與後端 FeatureToggleResponse.FeatureInfo 完全一致（DEF-168）。
// 本介面原本用 feature/displayName/enabled，與後端的 featureKey/featureName/isEnabled 全數對不上，
// 使整個頁面只有 description 顯示得出來——名稱空白、Switch 恆為 unchecked、
// 點擊時把 undefined 當 featureKey 送給後端。專案未配置任何 Jackson 命名策略，不會自動轉換。
interface FeatureToggle {
  featureKey: string
  category: string
  featureName: string
  description: string
  isEnabled: boolean
  status: 'ACTIVE' | 'PENDING' | 'INACTIVE'
  requiresAdminReview: boolean
}

// Sprint 153（S147 §6 範圍外項目）：數值配額用量，與 features 分開回傳
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

interface UpdateRequest {
  enabled: boolean
}

export default function FeatureTogglePage() {
  const router = useRouter()
  const [tenantId, setTenantId] = useState<string | null>(null)
  const [user, setUser] = useState<{ email: string; role: string; tenantId: string | null } | null>(null)
  const [features, setFeatures] = useState<FeatureToggle[]>([])
  const [quotas, setQuotas] = useState<QuotaInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [updating, setUpdating] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    const currentUser = AuthService.getCurrentUser()
    if (currentUser?.role !== 'STORE_OWNER' && currentUser?.role !== 'ADMIN') {
      router.push('/dashboard/tenants')
      return
    }

    setUser(currentUser)
    if (currentUser?.tenantId) {
      setTenantId(currentUser.tenantId)
    }
  }, [router])

  useEffect(() => {
    if (tenantId) {
      fetchFeatures()
    }
  }, [tenantId])

  const fetchFeatures = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<{ data: FeatureToggleResponse }>(
        API_ENDPOINTS.dashboard.tenants.features
      )
      setFeatures(response.data.data.features)
      setQuotas(response.data.data.quotas ?? [])
    } catch (err: unknown) {
      console.error('Failed to fetch features:', err)
      setError('載入功能開關失敗，請稍後再試')
    } finally {
      setLoading(false)
    }
  }

  const updateFeatureToggle = async (feature: string, enabled: boolean) => {
    if (!tenantId) return

    setUpdating(feature)
    setError(null)
    setSuccessMessage(null)

    try {
      // DEF-168：型別需與後端 FeatureToggleUpdateResponse 一致。原宣告為
      // { feature, enabled, status }，三欄有兩欄對不上；因回傳值未被使用故無執行後果，
      // 但錯誤的宣告會誤導未來的取用者，一併更正。
      await apiClient.put<{ data: { featureKey: string; previousState: boolean; newState: boolean; status: string; statusDescription: string } }>(
        API_ENDPOINTS.dashboard.tenants.updateFeature(feature),
        { enabled } as UpdateRequest
      )

      // Update local state
      setFeatures(prev =>
        prev.map(f =>
          f.featureKey === feature
            ? {
                ...f,
                isEnabled: enabled,
                status: f.requiresAdminReview && enabled ? 'PENDING' : enabled ? 'ACTIVE' : 'INACTIVE'
              }
            : f
        )
      )

      setSuccessMessage(`功能「${feature}」已${enabled ? '啟用' : '停用'}`)
      setTimeout(() => setSuccessMessage(null), 3000)
    } catch (err: unknown) {
      console.error('Failed to update feature toggle:', err)
      setError(`更新功能開關失敗，請稍後再試`)
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
    if (!enabled) {
      return <Badge variant="secondary">已停用</Badge>
    }
    if (requiresAdminReview && status === 'PENDING') {
      return <Badge variant="warning">待審核</Badge>
    }
    if (status === 'ACTIVE') {
      return <Badge variant="success">已啟用</Badge>
    }
    return <Badge variant="secondary">{status}</Badge>
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

  if (!user || !tenantId) {
    return (
      <div className="min-h-screen bg-gray-50">
        <nav className="bg-white shadow-sm">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between h-16">
              <div className="flex items-center">
                <h1 className="text-xl font-bold text-gray-900">NextKey</h1>
              </div>
            </div>
          </div>
        </nav>
        <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
          <div className="px-4 py-6 sm:px-0">
            <div className="flex items-center justify-center h-64">
              <div className="text-gray-500">載入中...</div>
            </div>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <button
                onClick={() => router.push('/dashboard/tenants')}
                className="text-gray-600 hover:text-gray-900"
              >
                店鋪管理
              </button>
              <span className="text-gray-400">/</span>
              <button
                onClick={() => router.push(`/dashboard/tenants/${tenantId}`)}
                className="text-gray-600 hover:text-gray-900"
              >
                店鋪詳情
              </button>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">功能開關</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">{user.email}</span>
              <button
                onClick={async () => {
                  await AuthService.logout()
                  router.push('/login')
                }}
                className="px-3 py-1.5 text-sm text-white bg-red-500 rounded-md hover:bg-red-600"
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">功能開關</h1>
            <p className="mt-1 text-sm text-gray-600">
              管理店鋪的功能開啟狀態。部分功能需要管理員審核後才會生效。
            </p>
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
              <CardContent className="flex flex-col items-center justify-center py-12">
                <p className="text-gray-500 mb-4">尚無功能開關設定</p>
                <Button variant="outline" onClick={() => router.push('/dashboard/tenants')}>
                  返回店鋪列表
                </Button>
              </CardContent>
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
                            {getStatusBadge(feature.status, feature.requiresAdminReview, feature.isEnabled)}
                          </div>
                          <p className="text-sm text-gray-500 mt-1">
                            {feature.description}
                          </p>
                          {feature.requiresAdminReview && (
                            <p className="text-xs text-amber-600 mt-1">
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
                            <span className="text-sm text-gray-500">更新中...</span>
                          )}
                        </div>
                      </div>
                    ))}
                  </CardContent>
                </Card>
              ))}

              <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                <h3 className="text-sm font-medium text-blue-800">功能開關說明</h3>
                <ul className="mt-2 text-sm text-blue-700 space-y-1">
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