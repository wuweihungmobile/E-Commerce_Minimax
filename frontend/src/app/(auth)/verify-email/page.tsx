'use client'

import { Suspense, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import Link from 'next/link'
import AuthService, { getApiErrorInfo } from '@/services/auth'

type Status = 'verifying' | 'success' | 'invalid' | 'error'

// Sprint 204（FRD US-M03-007，DEF-252）：驗證連結一開啟就自動驗證，不需登入（連結常在另一個瀏覽器開啟）。
// 連結是一次性的：React 開發模式會把 effect 跑兩次，第二次會讓連結失效並把成功畫面蓋成錯誤，
// 所以用 ref 保證整個頁面生命週期只送一次。
function VerifyEmailContent() {
  const searchParams = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'invalid')
  const [resendMessage, setResendMessage] = useState('')
  const submitted = useRef(false)

  useEffect(() => {
    if (!token || submitted.current) return
    submitted.current = true

    async function run() {
      try {
        await AuthService.verifyEmail(token)
        setStatus('success')
      } catch (err: unknown) {
        setStatus(getApiErrorInfo(err).code === 'E-1011' ? 'invalid' : 'error')
      }
    }
    void run()
  }, [token])

  const handleResend = async () => {
    setResendMessage('')
    try {
      await AuthService.resendEmailVerification()
      setResendMessage('If your email is not verified yet, a new verification link has been sent.')
    } catch {
      setResendMessage('Could not send a new link. Please try again later.')
    }
  }

  if (status === 'verifying') {
    return <p className="text-center text-rs-ink-muted">Verifying your email...</p>
  }

  if (status === 'success') {
    return (
      <div className="space-y-6" data-testid="verify-email-success">
        <div className="bg-rs-success/10 border border-rs-success/30 text-rs-success px-4 py-3 rounded">
          Your email has been verified.
        </div>
        <Link href="/dashboard" className="block text-center font-medium text-rs-primary hover:text-rs-primary">
          Continue
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6" data-testid="verify-email-failed">
      <div className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded">
        {status === 'invalid'
          ? 'This verification link is invalid or has expired. Links can only be used once and expire after 24 hours.'
          : 'We could not verify your email right now. Please try again later.'}
      </div>
      {AuthService.isAuthenticated() ? (
        <div className="space-y-2 text-center">
          <button
            type="button"
            onClick={handleResend}
            className="font-medium text-rs-primary hover:text-rs-primary"
          >
            Send me a new verification email
          </button>
          {resendMessage && <p className="text-sm text-rs-ink-muted">{resendMessage}</p>}
        </div>
      ) : (
        <Link href="/login" className="block text-center font-medium text-rs-primary hover:text-rs-primary">
          Sign in to request a new link
        </Link>
      )}
    </div>
  )
}

export default function VerifyEmailPage() {
  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg border border-rs-hairline">
        <h2 className="text-center text-3xl font-semibold text-rs-ink">Verify your email</h2>
        <Suspense fallback={null}>
          <VerifyEmailContent />
        </Suspense>
      </div>
    </div>
  )
}
