'use client'

import { useState } from 'react'
import Link from 'next/link'
import AuthService, { getApiErrorInfo } from '@/services/auth'

// Sprint 204（FRD US-M03-006，DEF-253）：/login 頁的「Forgot your password?」原本指向不存在的此頁（死連結）。
// 不論 Email 是否已註冊，成功畫面一律相同——後端刻意不揭露帳號是否存在，前端也不可自己洩漏。
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      await AuthService.requestPasswordReset(email)
      setSubmitted(true)
    } catch (err: unknown) {
      const { status } = getApiErrorInfo(err)
      if (status === 429) {
        setError('Too many requests. Please wait a moment and try again.')
      } else if (status === 400) {
        setError('Please enter a valid email address.')
      } else {
        setError('Something went wrong. Please try again later.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg border border-rs-hairline">
        <div>
          <h2 className="text-center text-3xl font-semibold text-rs-ink">Reset your password</h2>
          <p className="mt-2 text-center text-sm text-rs-ink-muted">
            Enter the email you registered with and we&apos;ll send you a reset link.
          </p>
        </div>

        {submitted ? (
          <div className="space-y-6" data-testid="forgot-password-submitted">
            <div className="bg-rs-success/10 border border-rs-success/30 text-rs-success px-4 py-3 rounded">
              If that email is registered, we&apos;ve sent a link to reset your password. The link expires in 30
              minutes and can only be used once.
            </div>
            <Link href="/login" className="block text-center font-medium text-rs-primary hover:text-rs-primary">
              Back to sign in
            </Link>
          </div>
        ) : (
          <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
            {error && (
              <div className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded">{error}</div>
            )}

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

            <button
              type="submit"
              disabled={loading}
              className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md text-sm font-medium text-white bg-rs-primary hover:bg-rs-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-rs-primary disabled:opacity-50"
            >
              {loading ? 'Sending...' : 'Send reset link'}
            </button>

            <p className="text-center text-sm text-rs-ink-muted">
              <Link href="/login" className="font-medium text-rs-primary hover:text-rs-primary">
                Back to sign in
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}
