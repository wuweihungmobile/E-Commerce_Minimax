'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import { isOAuthProviderConfigured, startOAuthFlow, type OAuthProviderId } from '@/services/oauth'
import { landingPathForRole, safeRedirectTarget } from '@/lib/auth-redirect'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { apiErrorMessage } from '@/lib/api-error'

export default function LoginPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // Sprint 153（item 13）：既有 Google/Facebook/Apple 按鈕列從未接上任何 onClick（死碼，
  // 且 Facebook/Apple 後端 OAuthProvider 枚舉根本不存在對應值）。改為 Google/GitHub 兩個
  // 真正可用的按鈕，且只在對應 client-id 已設定時才顯示，避免出現一個必然失敗的按鈕。
  const oauthProviders: OAuthProviderId[] = (['google', 'github'] as const).filter(
    isOAuthProviderConfigured
  )

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const response = await AuthService.login({ email, password })
      AuthService.storeAuthData(response)
      // DEF-409：?redirect=（同源站內路徑）優先，否則依角色分流，不再一律進 /dashboard。
      // 在送出事件裡讀 window.location，不用 useSearchParams（它在預先渲染的路由需要 Suspense）。
      const redirect = new URLSearchParams(window.location.search).get('redirect')
      router.push(
        safeRedirectTarget(redirect, window.location.origin) ?? landingPathForRole(response.user.role)
      )
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        setError(apiErrorMessage(err, 'Login failed'))
      } else {
        setError('An unexpected error occurred')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <Card className="max-w-md w-full space-y-8 p-8">
        <div>
          <h2 className="text-center text-3xl font-semibold text-rs-ink">
            Sign in to NextKey
          </h2>
          <p className="mt-2 text-center text-sm text-rs-ink-muted">
            Or{' '}
            <Link href="/register" className="font-medium text-rs-primary hover:text-rs-primary">
              create a new account
            </Link>
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {error && (
            <Alert variant="destructive" data-testid="login-error">
              {error}
            </Alert>
          )}

          <div className="space-y-4">
            <div>
              <Label htmlFor="email" className="block">
                Email address
              </Label>
              <Input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block"
                placeholder="you@example.com"
                suppressHydrationWarning
              />
            </div>

            <div>
              <Label htmlFor="password" className="block">
                Password
              </Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 block"
                placeholder="••••••••"
                suppressHydrationWarning
              />
            </div>
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <Checkbox
                id="remember-me"
                name="remember-me"
              />
              <Label htmlFor="remember-me" className="ml-2 block">
                Remember me
              </Label>
            </div>

            <div className="text-sm">
              <Link href="/forgot-password" className="font-medium text-rs-primary hover:text-rs-primary">
                Forgot your password?
              </Link>
            </div>
          </div>

          <Button
            variant="default"
            type="submit"
            disabled={loading}
            className="w-full"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </Button>
        </form>

        {oauthProviders.length > 0 && (
          <div className="mt-6">
            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-rs-hairline" />
              </div>
              <div className="relative flex justify-center text-sm">
                <span className="px-2 bg-white text-rs-ink-muted">Or continue with</span>
              </div>
            </div>

            <div className="mt-6 grid grid-cols-2 gap-3">
              {oauthProviders.map((provider) => (
                <Button
                  variant="outline"
                  key={provider}
                  type="button"
                  data-testid={`oauth-login-${provider}`}
                  onClick={() => startOAuthFlow(provider, 'login')}
                  className="w-full"
                >
                  {provider === 'google' ? 'Google' : 'GitHub'}
                </Button>
              ))}
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}
