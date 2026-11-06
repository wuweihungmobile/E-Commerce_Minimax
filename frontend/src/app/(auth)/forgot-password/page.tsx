'use client'

import { useState } from 'react'
import Link from 'next/link'
import AuthService, { getApiErrorInfo } from '@/services/auth'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

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
      <Card className="max-w-md w-full space-y-8 p-8">
        <div>
          <h2 className="text-center text-3xl font-semibold text-rs-ink">Reset your password</h2>
          <p className="mt-2 text-center text-sm text-rs-ink-muted">
            Enter the email you registered with and we&apos;ll send you a reset link.
          </p>
        </div>

        {submitted ? (
          <div className="space-y-6" data-testid="forgot-password-submitted">
            <Alert variant="success">
              If that email is registered, we&apos;ve sent a link to reset your password. The link expires in 30
              minutes and can only be used once.
            </Alert>
            <Link href="/login" className="block text-center font-medium text-rs-primary hover:text-rs-primary">
              Back to sign in
            </Link>
          </div>
        ) : (
          <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
            {error && (
              <Alert variant="destructive">{error}</Alert>
            )}

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

            <Button
              variant="default"
              type="submit"
              disabled={loading}
              className="w-full"
            >
              {loading ? 'Sending...' : 'Send reset link'}
            </Button>

            <p className="text-center text-sm text-rs-ink-muted">
              <Link href="/login" className="font-medium text-rs-primary hover:text-rs-primary">
                Back to sign in
              </Link>
            </p>
          </form>
        )}
      </Card>
    </div>
  )
}
