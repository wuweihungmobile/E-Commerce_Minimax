'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import { notifyAuthChange } from '@/services/authStore'
import OAuthApiService, {
  type OAuthProviderId,
  consumeOAuthState,
  oauthRedirectUri,
} from '@/services/oauth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { Button } from '@/components/ui/button'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

const VALID_PROVIDERS: OAuthProviderId[] = ['google', 'github']

// Sprint 153（item 13）：provider 授權頁重導回此頁後（?code=&state=），依發起時記錄的 intent
// 呼叫後端登入或連結端點。state 驗證失敗（不存在/不符）一律視為潛在 CSRF，拒絕繼續。
export default function OAuthCallbackPage() {
  const params = useParams()
  const router = useRouter()
  const providerParam = params.provider as string

  const [status, setStatus] = useState<'processing' | 'error'>('processing')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    async function run() {
      if (!VALID_PROVIDERS.includes(providerParam as OAuthProviderId)) {
        setStatus('error')
        setErrorMessage('不支援的登入服務')
        return
      }
      const provider = providerParam as OAuthProviderId

      const search = new URLSearchParams(window.location.search)
      const code = search.get('code')
      const state = search.get('state')
      const providerError = search.get('error')

      if (providerError) {
        setStatus('error')
        setErrorMessage('已取消或拒絕授權')
        return
      }

      const { valid, intent } = consumeOAuthState(state)
      if (!valid || !code || !intent) {
        setStatus('error')
        setErrorMessage('登入驗證失敗，請重新嘗試')
        return
      }

      try {
        const redirectUri = oauthRedirectUri(provider)
        if (intent === 'login') {
          const authResponse = await OAuthApiService.login(provider, code, redirectUri)
          AuthService.storeAuthData(authResponse)
          notifyAuthChange()
          router.push('/')
        } else {
          await OAuthApiService.link(provider, code, redirectUri)
          router.push('/account?linked=' + provider)
        }
      } catch (err) {
        setStatus('error')
        setErrorMessage(extractErrorMessage(err, '處理失敗，請稍後再試'))
      }
    }
    run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [providerParam])

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <Card className="max-w-md w-full">
        <CardHeader>
          <CardTitle className="text-base">
            {status === 'processing' ? '登入處理中…' : '登入失敗'}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {status === 'processing' ? (
            <div className="space-y-3">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          ) : (
            <>
              <Alert variant="destructive">
                <AlertDescription>{errorMessage}</AlertDescription>
              </Alert>
              <div className="flex gap-3">
                <Link href="/login">
                  <Button variant="outline">返回登入頁</Button>
                </Link>
                <Link href="/account">
                  <Button variant="outline">返回帳戶設定</Button>
                </Link>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
