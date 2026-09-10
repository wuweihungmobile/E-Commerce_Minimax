'use client'

import { useEffect, useState, useSyncExternalStore } from 'react'
import { useRouter } from 'next/navigation'
import AuthService from '@/services/auth'
import { subscribeAuth, getAuthRoleSnapshot, getAuthServerSnapshot, notifyAuthChange } from '@/services/authStore'
import { isOAuthProviderConfigured, startOAuthFlow, type OAuthProviderId } from '@/services/oauth'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

// PRD §1.5.1 會員資料權利（Sprint 153：Sprint 149 §7 範圍外項目，補前端入口——後端功能
// 已於 Sprint 94（AI-2428）完成，此前全站零呼叫點）
export default function AccountPage() {
  const router = useRouter()
  // SSR/hydration 回 null（比照 StorefrontHeader 既有模式），避免在 render 期直接讀
  // localStorage 造成 hydration mismatch；client 端切換為實際角色後才顯示刪除帳戶卡片
  const role = useSyncExternalStore(subscribeAuth, getAuthRoleSnapshot, getAuthServerSnapshot)

  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  // callback 頁連結成功後導回本頁帶 ?linked=<provider>（見 oauth/callback/[provider]/page.tsx）
  const [linkedProvider, setLinkedProvider] = useState<string | null>(null)
  useEffect(() => {
    const provider = new URLSearchParams(window.location.search).get('linked')
    if (provider) setLinkedProvider(provider)
  }, [])

  const oauthProviders: OAuthProviderId[] = (['google', 'github'] as const).filter(
    isOAuthProviderConfigured
  )

  const handleExport = async () => {
    setExporting(true)
    setExportError(null)
    try {
      const data = await AuthService.exportMyData()
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `my-data-export-${new Date().toISOString().slice(0, 10)}.json`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    } catch (err) {
      setExportError(extractErrorMessage(err, '資料匯出失敗，請稍後再試'))
    } finally {
      setExporting(false)
    }
  }

  const handleDelete = async () => {
    setDeleting(true)
    setDeleteError(null)
    try {
      await AuthService.deleteMyAccount()
      // 後端已將此使用者全部 refresh token 加入黑名單（見 UserPrivacyService.deleteMyAccount），
      // 此處只需清除本機資料並導向登入頁，不需再呼叫 /v2/auth/logout
      AuthService.clearAuthData()
      notifyAuthChange()
      router.push('/login')
    } catch (err) {
      setDeleteError(extractErrorMessage(err, '帳戶刪除失敗，請稍後再試'))
      setDeleting(false)
    }
  }

  // 後端 UserPrivacyService.deleteMyAccount 僅允許 BUYER 角色自助刪除（E-1009），
  // 賣家/管理員帳號不提供此操作入口，避免顯示一個必然失敗的按鈕
  const canDeleteAccount = role === 'BUYER'

  return (
    <StorefrontShell>
      <div className="max-w-2xl space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">帳戶設定</h1>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">匯出我的資料</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-gray-600">
              下載您在本平台的個人資料副本，包含基本資料、訂單、訂房、評價、地址簿、通知與客服工單摘要（JSON
              格式）。聊天訊息與客服工單訊息串的完整內容不包含在匯出範圍內，請至對應頁面查看。
            </p>
            {exportError && (
              <Alert variant="destructive">
                <AlertDescription>{exportError}</AlertDescription>
              </Alert>
            )}
            <Button onClick={handleExport} disabled={exporting} data-testid="account-export-data">
              {exporting ? '匯出中…' : '匯出我的資料（JSON）'}
            </Button>
          </CardContent>
        </Card>

        {oauthProviders.length > 0 && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">連結第三方帳號</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-gray-600">連結後可使用第三方帳號快速登入。</p>
              {linkedProvider && (
                <Alert variant="success">
                  <AlertDescription>已成功連結 {linkedProvider === 'google' ? 'Google' : 'GitHub'} 帳號</AlertDescription>
                </Alert>
              )}
              <div className="flex gap-3">
                {oauthProviders.map((provider) => (
                  <Button
                    key={provider}
                    variant="outline"
                    data-testid={`oauth-link-${provider}`}
                    onClick={() => startOAuthFlow(provider, 'link')}
                  >
                    連結 {provider === 'google' ? 'Google' : 'GitHub'}
                  </Button>
                ))}
              </div>
            </CardContent>
          </Card>
        )}

        {canDeleteAccount && (
          <Card className="border-red-200">
            <CardHeader>
              <CardTitle className="text-base text-red-700">刪除帳戶</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-gray-600">
                刪除帳戶將匿名化您的個人資料且無法復原。若尚有未完成的訂單或訂房，將無法刪除。
              </p>
              {deleteError && (
                <Alert variant="destructive">
                  <AlertDescription>{deleteError}</AlertDescription>
                </Alert>
              )}
              {!showDeleteConfirm ? (
                <Button
                  variant="outline"
                  className="border-red-300 text-red-700 hover:bg-red-50"
                  onClick={() => setShowDeleteConfirm(true)}
                  data-testid="account-delete-open"
                >
                  刪除我的帳戶
                </Button>
              ) : (
                <div className="space-y-3 border-t pt-4">
                  <p className="text-sm font-medium text-red-700">確定要刪除帳戶嗎？此操作無法復原。</p>
                  <div className="flex gap-3">
                    <Button
                      variant="destructive"
                      onClick={handleDelete}
                      disabled={deleting}
                      data-testid="account-delete-confirm"
                    >
                      {deleting ? '刪除中…' : '確認刪除'}
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => {
                        setShowDeleteConfirm(false)
                        setDeleteError(null)
                      }}
                      disabled={deleting}
                    >
                      取消
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        )}
      </div>
    </StorefrontShell>
  )
}
