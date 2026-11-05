'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import { isOAuthProviderConfigured, startOAuthFlow, type OAuthProviderId } from '@/services/oauth'
import { landingPathForRole, safeRedirectTarget } from '@/lib/auth-redirect'

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
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || 'Login failed')
      } else {
        setError('An unexpected error occurred')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg border border-rs-hairline">
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
            <div data-testid="login-error" className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded">
              {error}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-rs-ink">
                Email address
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block w-full px-3 py-2 border border-rs-hairline rounded-md focus:outline-none focus:ring-rs-primary focus:border-rs-primary placeholder:text-rs-ink-muted"
                placeholder="you@example.com"
                suppressHydrationWarning
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-rs-ink">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 block w-full px-3 py-2 border border-rs-hairline rounded-md focus:outline-none focus:ring-rs-primary focus:border-rs-primary placeholder:text-rs-ink-muted"
                placeholder="••••••••"
                suppressHydrationWarning
              />
            </div>
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <input
                id="remember-me"
                name="remember-me"
                type="checkbox"
                className="h-4 w-4 text-rs-primary focus:ring-rs-primary border-rs-hairline rounded"
              />
              <label htmlFor="remember-me" className="ml-2 block text-sm text-rs-ink">
                Remember me
              </label>
            </div>

            <div className="text-sm">
              <Link href="/forgot-password" className="font-medium text-rs-primary hover:text-rs-primary">
                Forgot your password?
              </Link>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md text-sm font-medium text-white bg-rs-primary hover:bg-rs-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-rs-primary disabled:opacity-50"
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
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
                <button
                  key={provider}
                  type="button"
                  data-testid={`oauth-login-${provider}`}
                  onClick={() => startOAuthFlow(provider, 'login')}
                  className="w-full flex justify-center py-2 px-4 border border-rs-hairline rounded-md text-sm font-medium text-rs-ink bg-white hover:bg-rs-bg-base"
                >
                  {provider === 'google' ? 'Google' : 'GitHub'}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
